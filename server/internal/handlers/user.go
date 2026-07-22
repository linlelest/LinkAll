package handlers

import (
	"database/sql"
	"errors"
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// UserListItem 用户列表项。
type UserListItem struct {
	ID          int64  `json:"id"`
	Username    string `json:"username"`
	Role        string `json:"role"`
	Status      string `json:"status"`
	Banned      bool   `json:"banned"`
	DeviceCount int    `json:"deviceCount"`
	Traffic     int64  `json:"traffic"`
	CreatedAt   int64  `json:"createdAt"`
	LastLoginIP string `json:"lastLoginIp"`
}

// ListUsers 列出用户（分页，仅管理员）。
// GET /api/admin/users?limit=50&offset=0
func (d *Deps) ListUsers(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	limit, _ := strconv.Atoi(c.Query("limit", "50"))
	offset, _ := strconv.Atoi(c.Query("offset", "0"))
	if limit <= 0 || limit > 500 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	var total int
	if err := d.DB.QueryRow(`SELECT COUNT(*) FROM users`).Scan(&total); err != nil {
		return failInternal(c, "查询用户总数失败")
	}

	rows, err := d.DB.Query(
		`SELECT id, username, role, status, banned, device_count, traffic, created_at, COALESCE(last_login_ip,'')
		 FROM users ORDER BY id DESC LIMIT ? OFFSET ?`,
		limit, offset,
	)
	if err != nil {
		return failInternal(c, "查询用户列表失败")
	}
	defer rows.Close()

	users := make([]UserListItem, 0)
	for rows.Next() {
		var u UserListItem
		var banned int
		if err := rows.Scan(
			&u.ID, &u.Username, &u.Role, &u.Status, &banned,
			&u.DeviceCount, &u.Traffic, &u.CreatedAt, &u.LastLoginIP,
		); err != nil {
			return failInternal(c, "扫描用户行失败")
		}
		u.Banned = banned == 1
		users = append(users, u)
	}
	return okWithMeta(c, users, total, limit, offset)
}

// BanUser 封禁用户。
// POST /api/admin/users/:id/ban
func (d *Deps) BanUser(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的用户 ID")
	}
	// 不允许封禁超级管理员
	if role := getUserRole(d.DB, id); role == "superadmin" {
		return failForbidden(c, "不能封禁超级管理员")
	}
	if _, err := d.DB.Exec(`UPDATE users SET banned = 1, status = 'disabled' WHERE id = ?`, id); err != nil {
		return failInternal(c, "封禁用户失败")
	}
	return ok(c, fiber.Map{"banned": true})
}

// UnbanUser 解封用户。
// POST /api/admin/users/:id/unban
func (d *Deps) UnbanUser(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的用户 ID")
	}
	if _, err := d.DB.Exec(`UPDATE users SET banned = 0, status = 'active' WHERE id = ?`, id); err != nil {
		return failInternal(c, "解封用户失败")
	}
	return ok(c, fiber.Map{"banned": false})
}

// ResetPassword 重置用户密码（管理员）。
// POST /api/admin/users/:id/reset-password
func (d *Deps) ResetPassword(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的用户 ID")
	}
	var req struct {
		NewPassword string `json:"newPassword"`
	}
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	req.NewPassword = strings.TrimSpace(req.NewPassword)
	if len(req.NewPassword) < 8 {
		return failBadRequest(c, "新密码长度至少 8 位")
	}
	hash, err := d.Hasher.Hash(req.NewPassword)
	if err != nil {
		return failInternal(c, "密码哈希失败")
	}
	if _, err := d.DB.Exec(`UPDATE users SET password_hash = ? WHERE id = ?`, hash, id); err != nil {
		return failInternal(c, "重置密码失败")
	}
	return ok(c, fiber.Map{"reset": true})
}

// UpdateRole 更新用户角色（仅超级管理员）。
// POST /api/admin/users/:id/role
func (d *Deps) UpdateRole(c *fiber.Ctx) error {
	if auth.CurrentRole(c) != "superadmin" {
		return failForbidden(c, "需要超级管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的用户 ID")
	}
	var req struct {
		Role string `json:"role"`
	}
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	role := strings.TrimSpace(req.Role)
	if role != "user" && role != "admin" && role != "superadmin" {
		return failBadRequest(c, "无效的角色")
	}
	// 不能修改自己的角色（防止唯一超管降级）
	if id == auth.CurrentUserID(c) {
		return failForbidden(c, "不能修改自己的角色")
	}
	if _, err := d.DB.Exec(`UPDATE users SET role = ? WHERE id = ?`, role, id); err != nil {
		return failInternal(c, "更新角色失败")
	}
	return ok(c, fiber.Map{"role": role})
}

// DeleteUser 删除用户（仅超级管理员，软删除：标记 status=deleted）。
// DELETE /api/admin/users/:id
func (d *Deps) DeleteUser(c *fiber.Ctx) error {
	if auth.CurrentRole(c) != "superadmin" {
		return failForbidden(c, "需要超级管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的用户 ID")
	}
	if id == auth.CurrentUserID(c) {
		return failForbidden(c, "不能删除自己")
	}
	if role := getUserRole(d.DB, id); role == "superadmin" {
		return failForbidden(c, "不能删除超级管理员")
	}
	if _, err := d.DB.Exec(
		`UPDATE users SET status = 'deleted', banned = 1 WHERE id = ?`,
		id,
	); err != nil {
		return failInternal(c, "删除用户失败")
	}
	return ok(c, fiber.Map{"deleted": true})
}

// getUserRole 查询用户角色（辅助）。
func getUserRole(db *sql.DB, id int64) string {
	var role string
	err := db.QueryRow(`SELECT role FROM users WHERE id = ?`, id).Scan(&role)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ""
		}
		return ""
	}
	return role
}

// ListDevices 列出所有设备（管理员）。
// GET /api/admin/devices?limit=50&offset=0&online=true
func (d *Deps) ListDevices(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	limit, _ := strconv.Atoi(c.Query("limit", "50"))
	offset, _ := strconv.Atoi(c.Query("offset", "0"))
	if limit <= 0 || limit > 500 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}
	onlineOnly := c.Query("online") == "true"

	var (
		rows *sql.Rows
		err  error
		total int
	)
	if onlineOnly {
		_ = d.DB.QueryRow(`SELECT COUNT(*) FROM devices WHERE online_status = 'online'`).Scan(&total)
		rows, err = d.DB.Query(
			`SELECT device_id, COALESCE(owner_user_id,0), online_status, last_seen, platform, version, device_name, created_at
			 FROM devices WHERE online_status = 'online' ORDER BY last_seen DESC LIMIT ? OFFSET ?`,
			limit, offset,
		)
	} else {
		_ = d.DB.QueryRow(`SELECT COUNT(*) FROM devices`).Scan(&total)
		rows, err = d.DB.Query(
			`SELECT device_id, COALESCE(owner_user_id,0), online_status, last_seen, platform, version, device_name, created_at
			 FROM devices ORDER BY last_seen DESC LIMIT ? OFFSET ?`,
			limit, offset,
		)
	}
	if err != nil {
		return failInternal(c, "查询设备列表失败")
	}
	defer rows.Close()

	type DeviceItem struct {
		DeviceID     string `json:"deviceId"`
		OwnerUserID  int64  `json:"ownerUserId"`
		OnlineStatus string `json:"onlineStatus"`
		LastSeen     int64  `json:"lastSeen"`
		Platform     string `json:"platform"`
		Version      string `json:"version"`
		DeviceName   string `json:"deviceName"`
		CreatedAt    int64  `json:"createdAt"`
	}
	devices := make([]DeviceItem, 0)
	for rows.Next() {
		var dev DeviceItem
		if err := rows.Scan(
			&dev.DeviceID, &dev.OwnerUserID, &dev.OnlineStatus, &dev.LastSeen,
			&dev.Platform, &dev.Version, &dev.DeviceName, &dev.CreatedAt,
		); err != nil {
			return failInternal(c, "扫描设备行失败")
		}
		devices = append(devices, dev)
	}
	return okWithMeta(c, devices, total, limit, offset)
}

// KickDevice 踢出指定设备的所有会话（管理员）。
// POST /api/admin/devices/:deviceId/kick
func (d *Deps) KickDevice(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	deviceID := c.Params("deviceId")
	if deviceID == "" {
		return failBadRequest(c, "设备编号不能为空")
	}
	// 通过 Hub 关闭该设备的连接
	client := d.Hub.FindDevice(deviceID)
	if client == nil {
		return failNotFound(c, "设备不在线")
	}
	client.Close()
	return ok(c, fiber.Map{"kicked": deviceID})
}
