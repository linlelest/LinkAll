// 首次启动初始化模块
// 当数据库无 superadmin 时，开放 /api/setup/* 路由供网页端创建首个管理员。
// 创建完成后路由立即禁用，防止二次创建。
package handlers

import (
	"database/sql"
	"strings"
	"sync/atomic"
	"time"

	"github.com/gofiber/fiber/v2"
)

// setupDone 标记初始化是否已完成（进程级原子标志，防止重启前二次创建）。
// 即使数据库为空，只要本次进程已完成 init，也拒绝再次调用。
var setupDone atomic.Bool

// SetupStatusResponse 初始化状态响应。
type SetupStatusResponse struct {
	NeedsSetup bool `json:"needsSetup"`
}

// SetupInitRequest 初始化请求体。
type SetupInitRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// GetSetupStatus 检查是否需要首次初始化。
// GET /api/setup/status
// 公开路由，无需鉴权。
func (d *Deps) GetSetupStatus(c *fiber.Ctx) error {
	needs := d.NeedsSetup()
	return ok(c, SetupStatusResponse{NeedsSetup: needs})
}

// InitSetup 创建首个超级管理员账户。
// POST /api/setup/init
// 仅在 needsSetup=true 时可用，成功后设置原子标志。
func (d *Deps) InitSetup(c *fiber.Ctx) error {
	// 二次检查：已初始化则拒绝
	if !d.NeedsSetup() {
		return fail(c, fiber.StatusForbidden, CodeSetupAlreadyDone, "系统已完成初始化")
	}

	var req SetupInitRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}

	req.Username = strings.TrimSpace(req.Username)
	if len(req.Username) < 3 || len(req.Username) > 32 {
		return failBadRequest(c, "用户名长度需为 3-32 个字符")
	}
	if len(req.Password) < 8 || len(req.Password) > 128 {
		return failBadRequest(c, "密码长度需为 8-128 个字符")
	}

	// 哈希密码（Argon2id）
	hash, err := d.Hasher.Hash(req.Password)
	if err != nil {
		return failInternal(c, "密码哈希失败")
	}

	// 写入数据库
	result, err := d.DB.Exec(
		`INSERT INTO users (username, password_hash, role, status, banned, created_at, last_login_at)
		 VALUES (?, ?, 'superadmin', 'active', 0, ?, 0)`,
		req.Username, hash, time.Now().Unix(),
	)
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") || strings.Contains(err.Error(), "unique") {
			return fail(c, fiber.StatusConflict, CodeInvalidPayload, "用户名已存在")
		}
		return failInternal(c, "创建管理员失败")
	}
	uid, _ := result.LastInsertId()

	// 设置原子标志，防止本进程再次创建
	setupDone.Store(true)

	// 签发 JWT，让前端无需再次登录
	token, err := d.JWT.Sign(uid, req.Username, "superadmin")
	if err != nil {
		// 管理员已创建，仅签发失败，返回提示
		return ok(c, fiber.Map{
			"created":   true,
			"userId":    uid,
			"username":  req.Username,
			"autoLogin": false,
			"message":   "管理员创建成功，但自动登录失败，请手动登录",
		})
	}

	return ok(c, fiber.Map{
		"created":   true,
		"userId":    uid,
		"username":  req.Username,
		"autoLogin": true,
		"token":     token,
		"expiresIn": int((24 * time.Hour).Seconds()),
	})
}

// NeedsSetup 判断是否需要首次初始化。
// 满足以下任一条件即返回 false（不需要）：
//  1. 本进程已完成 init（setupDone=true）
//  2. 数据库已存在 superadmin 账户
func (d *Deps) NeedsSetup() bool {
	if setupDone.Load() {
		return false
	}
	var count int
	err := d.DB.QueryRow(
		`SELECT COUNT(*) FROM users WHERE role = 'superadmin'`,
	).Scan(&count)
	if err != nil {
		// 数据库错误时保守处理：返回 true 让前端显示 setup 页面
		// （避免因 DB 异常导致系统无法初始化）
		if err == sql.ErrNoRows {
			return true
		}
		return true
	}
	return count == 0
}
