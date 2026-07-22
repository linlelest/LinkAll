package auth

import (
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/golang-jwt/jwt/v5"
)

// Claims 自定义 JWT Claims。
type Claims struct {
	UserID   int64  `json:"uid"`
	Username string `json:"usr"`
	Role     string `json:"rol"`
	jwt.RegisteredClaims
}

// JWTManager 负责签发与校验 JWT。
type JWTManager struct {
	secret []byte
	expiry time.Duration
	method jwt.SigningMethod
}

// NewJWTManager 创建 JWT 管理器。scheme 取 HS256/HS384/HS512（默认 HS256）。
func NewJWTManager(secret string, expiry time.Duration, scheme string) *JWTManager {
	if secret == "" {
		panic("JWT_SECRET 不能为空")
	}
	m := jwt.SigningMethodHS256
	switch strings.ToUpper(scheme) {
	case "HS384":
		m = jwt.SigningMethodHS384
	case "HS512":
		m = jwt.SigningMethodHS512
	}
	return &JWTManager{
		secret: []byte(secret),
		expiry: expiry,
		method: m,
	}
}

// Sign 签发 JWT。
func (m *JWTManager) Sign(userID int64, username, role string) (string, error) {
	now := time.Now()
	claims := Claims{
		UserID:   userID,
		Username: username,
		Role:     role,
		RegisteredClaims: jwt.RegisteredClaims{
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(m.expiry)),
			NotBefore: jwt.NewNumericDate(now),
			Issuer:    "linkall-server",
			Subject:   username,
			ID:        fmt.Sprintf("%d-%d", userID, now.UnixNano()),
		},
	}
	token := jwt.NewWithClaims(m.method, claims)
	return token.SignedString(m.secret)
}

// Parse 解析并校验 JWT，返回 Claims。
func (m *JWTManager) Parse(tokenStr string) (*Claims, error) {
	// 去掉 Bearer 前缀
	tokenStr = strings.TrimSpace(strings.TrimPrefix(tokenStr, "Bearer"))
	tokenStr = strings.TrimSpace(tokenStr)
	if tokenStr == "" {
		return nil, errors.New("token 为空")
	}

	var claims Claims
	token, err := jwt.ParseWithClaims(tokenStr, &claims, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return m.secret, nil
	})
	if err != nil {
		return nil, err
	}
	if !token.Valid {
		return nil, errors.New("token 无效")
	}
	return &claims, nil
}

// Expiry 返回过期时长。
func (m *JWTManager) Expiry() time.Duration { return m.expiry }

// === Fiber 中间件 ===

// contextKey 用于 fiber.Locals 存储。
const (
	LocalsUserID   = "uid"
	LocalsUsername = "username"
	LocalsRole     = "role"
)

// Middleware 返回 Fiber JWT 校验中间件。
// 从 Authorization 头读取 Bearer token，校验后将用户信息注入 Locals。
// skipRoles 非空时，仅允许这些角色通过（用于管理员保护路由）。
// 若需跳过某些路径，请使用 fiber.Group 显式挂载。
func (m *JWTManager) Middleware(requireRoles ...string) fiber.Handler {
	roleSet := make(map[string]bool, len(requireRoles))
	for _, r := range requireRoles {
		roleSet[r] = true
	}
	return func(c *fiber.Ctx) error {
		auth := c.Get("Authorization")
		if auth == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"code":    "ERR_AUTH_EXPIRED",
				"message": "缺少 Authorization 头",
			})
		}
		claims, err := m.Parse(auth)
		if err != nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"code":    "ERR_AUTH_EXPIRED",
				"message": "token 无效或已过期",
			})
		}
		// 角色校验
		if len(roleSet) > 0 && !roleSet[claims.Role] {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"code":    "ERR_PERMISSION_DENIED",
				"message": "权限不足",
			})
		}
		c.Locals(LocalsUserID, claims.UserID)
		c.Locals(LocalsUsername, claims.Username)
		c.Locals(LocalsRole, claims.Role)
		return c.Next()
	}
}

// CurrentUserID 从 fiber.Ctx 提取当前用户 ID。
func CurrentUserID(c *fiber.Ctx) int64 {
	if v := c.Locals(LocalsUserID); v != nil {
		if id, ok := v.(int64); ok {
			return id
		}
	}
	return 0
}

// CurrentUsername 从 fiber.Ctx 提取当前用户名。
func CurrentUsername(c *fiber.Ctx) string {
	if v := c.Locals(LocalsUsername); v != nil {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

// CurrentRole 从 fiber.Ctx 提取当前角色。
func CurrentRole(c *fiber.Ctx) string {
	if v := c.Locals(LocalsRole); v != nil {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

// IsAdmin 判断当前用户是否为管理员或超级管理员。
func IsAdmin(c *fiber.Ctx) bool {
	r := CurrentRole(c)
	return r == "admin" || r == "superadmin"
}

// EnsureDBUserExists 校验 token 对应的用户在 DB 中是否仍有效（可选辅助）。
// 在 handlers 层使用更合适，此处提供以便 auth 包内聚。
func (m *JWTManager) EnsureDBUserExists(db *sql.DB, userID int64) error {
	var banned int
	err := db.QueryRow(`SELECT banned FROM users WHERE id = ? AND status = 'active'`, userID).Scan(&banned)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return errors.New("用户不存在或已禁用")
		}
		return err
	}
	if banned == 1 {
		return errors.New("账号已被封禁")
	}
	return nil
}
