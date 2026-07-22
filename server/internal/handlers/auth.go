package handlers

import (
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"

	"github.com/linlelest/LinkALL/server/internal/auth"
)

// LoginRequest 登录请求体。
type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// LoginResponse 登录响应。
type LoginResponse struct {
	Token     string `json:"token"`
	ExpiresIn int    `json:"expiresIn"` // 秒
	User      UserInfo `json:"user"`
}

// RegisterRequest 注册请求体（需邀请码）。
type RegisterRequest struct {
	Username   string `json:"username"`
	Password   string `json:"password"`
	InviteCode string `json:"inviteCode"`
}

// RefreshResponse 令牌刷新响应。
type RefreshResponse struct {
	Token     string `json:"token"`
	ExpiresIn int    `json:"expiresIn"`
}

// UserInfo 用户基本信息（脱敏）。
type UserInfo struct {
	ID           int64  `json:"id"`
	Username     string `json:"username"`
	Role         string `json:"role"`
	Status       string `json:"status"`
	Banned       bool   `json:"banned"`
	DeviceCount  int    `json:"deviceCount"`
	CreatedAt    int64  `json:"createdAt"`
	LastLoginIP  string `json:"lastLoginIp"`
}

// Login 处理用户登录。
// POST /api/auth/login
func (d *Deps) Login(c *fiber.Ctx) error {
	var req LoginRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	req.Username = strings.TrimSpace(req.Username)
	if req.Username == "" || req.Password == "" {
		return failBadRequest(c, "用户名或密码不能为空")
	}

	var (
		id           int64
		passwordHash string
		role         string
		status       string
		banned       int
		deviceCount  int
		createdAt    int64
	)
	err := d.DB.QueryRow(
		`SELECT id, password_hash, role, status, banned, device_count, created_at
		 FROM users WHERE username = ?`,
		req.Username,
	).Scan(&id, &passwordHash, &role, &status, &banned, &deviceCount, &createdAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return fail(c, fiber.StatusUnauthorized, CodeAuthFailed, "用户名或密码错误")
		}
		return failInternal(c, "查询用户失败")
	}

	if banned == 1 {
		return fail(c, fiber.StatusForbidden, CodeUserBanned, "账号已被封禁")
	}
	if status != "active" {
		return fail(c, fiber.StatusForbidden, CodeUserBanned, "账号已禁用")
	}

	// 校验密码
	match, err := d.Hasher.Verify(req.Password, passwordHash)
	if err != nil || !match {
		return fail(c, fiber.StatusUnauthorized, CodeAuthFailed, "用户名或密码错误")
	}

	// 签发 JWT
	token, err := d.JWT.Sign(id, req.Username, role)
	if err != nil {
		return failInternal(c, "签发令牌失败")
	}

	// 更新最后登录 IP
	ip := c.IP()
	if _, err := d.DB.Exec(
		`UPDATE users SET last_login_ip = ? WHERE id = ?`,
		ip, id,
	); err != nil {
		// 登录本身已成功，IP 更新失败仅记录日志
		_ = err
	}

	return c.JSON(APIResponse{
		Code:    CodeOK,
		Message: "登录成功",
		Data: LoginResponse{
			Token:     token,
			ExpiresIn: int(d.JWT.Expiry().Seconds()),
			User: UserInfo{
				ID:          id,
				Username:    req.Username,
				Role:        role,
				Status:      status,
				Banned:      false,
				DeviceCount: deviceCount,
				CreatedAt:   createdAt,
				LastLoginIP: ip,
			},
		},
	})
}

// Register 处理用户注册（需邀请码）。
// POST /api/auth/register
func (d *Deps) Register(c *fiber.Ctx) error {
	var req RegisterRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	req.Username = strings.TrimSpace(req.Username)
	req.InviteCode = strings.TrimSpace(req.InviteCode)
	if len(req.Username) < 3 || len(req.Username) > 32 {
		return failBadRequest(c, "用户名长度需 3-32 字符")
	}
	if len(req.Password) < 8 {
		return failBadRequest(c, "密码长度至少 8 位")
	}
	if req.InviteCode == "" {
		return failBadRequest(c, "邀请码不能为空")
	}

	// 检查用户名是否已存在
	var existing int
	err := d.DB.QueryRow(`SELECT COUNT(*) FROM users WHERE username = ?`, req.Username).Scan(&existing)
	if err != nil {
		return failInternal(c, "查询用户失败")
	}
	if existing > 0 {
		return fail(c, fiber.StatusConflict, CodeAuthFailed, "用户名已存在")
	}

	// 哈希密码
	hash, err := d.Hasher.Hash(req.Password)
	if err != nil {
		return failInternal(c, "密码哈希失败")
	}

	// 在事务中消费邀请码 + 创建用户
	tx, err := d.DB.Begin()
	if err != nil {
		return failInternal(c, "开启事务失败")
	}
	defer tx.Rollback()

	// 校验邀请码（行级锁）
	var (
		inviteID   int64
		expiresAt  int64
		used       int
		revoked    int
	)
	err = tx.QueryRow(
		`SELECT id, expires_at, used, revoked FROM invite_codes WHERE code = ?`,
		req.InviteCode,
	).Scan(&inviteID, &expiresAt, &used, &revoked)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return fail(c, fiber.StatusBadRequest, CodeInviteInvalid, "邀请码无效")
		}
		return failInternal(c, "查询邀请码失败")
	}
	if revoked == 1 {
		return fail(c, fiber.StatusBadRequest, CodeInviteInvalid, "邀请码已被吊销")
	}
	if used == 1 {
		return fail(c, fiber.StatusBadRequest, CodeInviteUsed, "邀请码已被使用")
	}
	if time.Now().Unix() > expiresAt {
		return fail(c, fiber.StatusBadRequest, CodeInviteExpired, "邀请码已过期")
	}

	// 创建用户
	res, err := tx.Exec(
		`INSERT INTO users (username, password_hash, role, invite_code_id) VALUES (?, ?, 'user', ?)`,
		req.Username, hash, inviteID,
	)
	if err != nil {
		return fail(c, fiber.StatusConflict, CodeAuthFailed, "用户名已存在或创建失败")
	}
	userID, _ := res.LastInsertId()

	// 标记邀请码已使用
	_, err = tx.Exec(
		`UPDATE invite_codes SET used = 1, used_by = ?, used_at = ? WHERE id = ? AND used = 0`,
		userID, time.Now().Unix(), inviteID,
	)
	if err != nil {
		return failInternal(c, "更新邀请码状态失败")
	}

	if err := tx.Commit(); err != nil {
		return failInternal(c, "提交事务失败")
	}

	// 注册成功，自动签发 JWT
	token, err := d.JWT.Sign(userID, req.Username, "user")
	if err != nil {
		// 注册已成功，令牌失败仅返回不带 token
		return c.JSON(APIResponse{
			Code:    CodeOK,
			Message: "注册成功",
		})
	}

	return c.JSON(APIResponse{
		Code:    CodeOK,
		Message: "注册成功",
		Data: LoginResponse{
			Token:     token,
			ExpiresIn: int(d.JWT.Expiry().Seconds()),
			User: UserInfo{
				ID:        userID,
				Username:  req.Username,
				Role:      "user",
				Status:    "active",
				Banned:    false,
				CreatedAt: time.Now().Unix(),
			},
		},
	})
}

// Refresh 刷新 JWT 令牌（需当前 token 有效）。
// POST /api/auth/refresh
func (d *Deps) Refresh(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	username := auth.CurrentUsername(c)
	role := auth.CurrentRole(c)
	if uid == 0 {
		return failUnauthorized(c, "令牌无效")
	}
	token, err := d.JWT.Sign(uid, username, role)
	if err != nil {
		return failInternal(c, "签发令牌失败")
	}
	return c.JSON(APIResponse{
		Code:    CodeOK,
		Message: "令牌已刷新",
		Data: RefreshResponse{
			Token:     token,
			ExpiresIn: int(d.JWT.Expiry().Seconds()),
		},
	})
}

// Me 获取当前登录用户信息。
// GET /api/auth/me
func (d *Deps) Me(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	if uid == 0 {
		return failUnauthorized(c, "令牌无效")
	}
	var (
		username    string
		role        string
		status      string
		banned      int
		deviceCount int
		createdAt   int64
		lastLoginIP string
	)
	err := d.DB.QueryRow(
		`SELECT username, role, status, banned, device_count, created_at, COALESCE(last_login_ip,'')
		 FROM users WHERE id = ?`,
		uid,
	).Scan(&username, &role, &status, &banned, &deviceCount, &createdAt, &lastLoginIP)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return failNotFound(c, "用户不存在")
		}
		return failInternal(c, "查询用户失败")
	}
	return ok(c, UserInfo{
		ID:          uid,
		Username:    username,
		Role:        role,
		Status:      status,
		Banned:      banned == 1,
		DeviceCount: deviceCount,
		CreatedAt:   createdAt,
		LastLoginIP: lastLoginIP,
	})
}

// ChangePassword 修改当前用户密码。
// POST /api/auth/change-password
func (d *Deps) ChangePassword(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	if uid == 0 {
		return failUnauthorized(c, "令牌无效")
	}
	var req struct {
		OldPassword string `json:"oldPassword"`
		NewPassword string `json:"newPassword"`
	}
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	if len(req.NewPassword) < 8 {
		return failBadRequest(c, "新密码长度至少 8 位")
	}

	var hash string
	err := d.DB.QueryRow(`SELECT password_hash FROM users WHERE id = ?`, uid).Scan(&hash)
	if err != nil {
		return failInternal(c, "查询用户失败")
	}
	match, err := d.Hasher.Verify(req.OldPassword, hash)
	if err != nil || !match {
		return failUnauthorized(c, "原密码错误")
	}
	newHash, err := d.Hasher.Hash(req.NewPassword)
	if err != nil {
		return failInternal(c, "密码哈希失败")
	}
	if _, err := d.DB.Exec(`UPDATE users SET password_hash = ? WHERE id = ?`, newHash, uid); err != nil {
		return failInternal(c, "更新密码失败")
	}
	return ok(c, fiber.Map{"updated": true})
}
