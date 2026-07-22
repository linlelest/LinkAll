package handlers

import (
	"encoding/json"
	"strconv"

	"github.com/gofiber/fiber/v2"
)

// SecuritySettings 全局安全设置响应。
type SecuritySettings struct {
	ForceHTTPS            bool     `json:"forceHttps"`
	AllowAnonymous        bool     `json:"allowAnonymous"`
	AllowDeviceCode       bool     `json:"allowDeviceCode"`
	AllowRemoteControl    bool     `json:"allowRemoteControl"`
	AnonymousWhitelist    []string `json:"anonymousWhitelist"`
	ConnectionPasswordSet bool     `json:"connectionPasswordSet"`
	MaxConcurrentSessions int      `json:"maxConcurrentSessions"`
	DataRetentionDays     int      `json:"dataRetentionDays"`
	UpdatedAt             int64    `json:"updatedAt"`
}

// GetSecurity 获取全局安全设置（管理员）。
// GET /api/admin/security
func (d *Deps) GetSecurity(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	var (
		forceHTTPS            int
		allowAnon             int
		allowDevCode          int
		allowRemote           int
		anonWhitelistJSON     string
		connPwHash            string
		maxSessions           int
		retentionDays         int
		updatedAt             int64
	)
	err := d.DB.QueryRow(
		`SELECT force_https, allow_anonymous, allow_device_code, allow_remote_control,
		        anonymous_whitelist, connection_password_hash,
		        max_concurrent_sessions, data_retention_days, updated_at
		 FROM security_settings WHERE id = 1`,
	).Scan(
		&forceHTTPS, &allowAnon, &allowDevCode, &allowRemote,
		&anonWhitelistJSON, &connPwHash,
		&maxSessions, &retentionDays, &updatedAt,
	)
	if err != nil {
		return failInternal(c, "查询安全设置失败")
	}
	var whitelist []string
	if anonWhitelistJSON != "" && anonWhitelistJSON != "[]" {
		_ = json.Unmarshal([]byte(anonWhitelistJSON), &whitelist)
	}
	if whitelist == nil {
		whitelist = []string{}
	}
	return ok(c, SecuritySettings{
		ForceHTTPS:            forceHTTPS == 1,
		AllowAnonymous:       allowAnon == 1,
		AllowDeviceCode:       allowDevCode == 1,
		AllowRemoteControl:   allowRemote == 1,
		AnonymousWhitelist:    whitelist,
		ConnectionPasswordSet: connPwHash != "",
		MaxConcurrentSessions: maxSessions,
		DataRetentionDays:     retentionDays,
		UpdatedAt:             updatedAt,
	})
}

// UpdateSecurityRequest 更新安全设置请求体。
type UpdateSecurityRequest struct {
	ForceHTTPS            *bool     `json:"forceHttps,omitempty"`
	AllowAnonymous        *bool     `json:"allowAnonymous,omitempty"`
	AllowDeviceCode       *bool     `json:"allowDeviceCode,omitempty"`
	AllowRemoteControl    *bool     `json:"allowRemoteControl,omitempty"`
	AnonymousWhitelist    *[]string `json:"anonymousWhitelist,omitempty"`
	ConnectionPassword    *string   `json:"connectionPassword,omitempty"` // 明文，服务端哈希后存储
	MaxConcurrentSessions *int      `json:"maxConcurrentSessions,omitempty"`
	DataRetentionDays     *int      `json:"dataRetentionDays,omitempty"`
}

// UpdateSecurity 更新全局安全设置（管理员）。
// PUT /api/admin/security
func (d *Deps) UpdateSecurity(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	var req UpdateSecurityRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}

	// 动态构造 UPDATE 语句
	sets := []string{}
	args := []interface{}{}

	if req.ForceHTTPS != nil {
		sets = append(sets, "force_https = ?")
		args = append(args, boolToInt(*req.ForceHTTPS))
	}
	if req.AllowAnonymous != nil {
		sets = append(sets, "allow_anonymous = ?")
		args = append(args, boolToInt(*req.AllowAnonymous))
	}
	if req.AllowDeviceCode != nil {
		sets = append(sets, "allow_device_code = ?")
		args = append(args, boolToInt(*req.AllowDeviceCode))
	}
	if req.AllowRemoteControl != nil {
		sets = append(sets, "allow_remote_control = ?")
		args = append(args, boolToInt(*req.AllowRemoteControl))
	}
	if req.AnonymousWhitelist != nil {
		raw, _ := json.Marshal(*req.AnonymousWhitelist)
		sets = append(sets, "anonymous_whitelist = ?")
		args = append(args, string(raw))
	}
	if req.ConnectionPassword != nil {
		pw := *req.ConnectionPassword
		if pw == "" {
			// 清空连接密码
			sets = append(sets, "connection_password_hash = ?")
			args = append(args, "")
		} else {
			if len(pw) < 6 {
				return failBadRequest(c, "连接密码至少 6 位")
			}
			hash, err := d.Hasher.Hash(pw)
			if err != nil {
				return failInternal(c, "密码哈希失败")
			}
			sets = append(sets, "connection_password_hash = ?")
			args = append(args, hash)
		}
	}
	if req.MaxConcurrentSessions != nil {
		if *req.MaxConcurrentSessions < 1 || *req.MaxConcurrentSessions > 10000 {
			return failBadRequest(c, "最大并发会话数需在 1-10000")
		}
		sets = append(sets, "max_concurrent_sessions = ?")
		args = append(args, *req.MaxConcurrentSessions)
	}
	if req.DataRetentionDays != nil {
		if *req.DataRetentionDays < 1 || *req.DataRetentionDays > 3650 {
			return failBadRequest(c, "数据保留天数需在 1-3650")
		}
		sets = append(sets, "data_retention_days = ?")
		args = append(args, *req.DataRetentionDays)
	}

	if len(sets) == 0 {
		return failBadRequest(c, "未提供任何更新字段")
	}

	sets = append(sets, "updated_at = strftime('%s','now')")
	args = append(args, 1) // WHERE id = ?

	query := "UPDATE security_settings SET " + joinStrings(sets, ", ") + " WHERE id = ?"
	res, err := d.DB.Exec(query, args...)
	if err != nil {
		return failInternal(c, "更新安全设置失败")
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		// 兜底：插入默认行后重试
		_, _ = d.DB.Exec(`INSERT OR IGNORE INTO security_settings (id) VALUES (1)`)
		_, err = d.DB.Exec(query, args...)
		if err != nil {
			return failInternal(c, "更新安全设置失败")
		}
	}
	return ok(c, fiber.Map{"updated": true})
}

// EnvPreview .env 热重载预览（管理员）。
// GET /api/admin/security/env-preview
func (d *Deps) EnvPreview(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	// 脱敏：JWT_SECRET 仅显示前 8 字符
	secret := d.Config.JWTSecret
	masked := ""
	if len(secret) > 8 {
		masked = secret[:8] + "..."
	} else {
		masked = "***"
	}
	return ok(c, fiber.Map{
		"serverPort":           d.Config.ServerPort,
		"env":                  d.Config.Env,
		"officialServer":       d.Config.OfficialServer,
		"dbPath":               d.Config.DBPath,
		"jwtScheme":            d.Config.JWTScheme,
		"jwtSecret":            masked,
		"jwtExpiry":            d.Config.JWTExpiry.String(),
		"stunServers":          d.Config.STUNServers,
		"turnServers":          d.Config.TURNServers,
		"turnUsername":         d.Config.TURNUsername,
		"forceHttps":           d.Config.ForceHTTPS,
		"maxConcurrentSessions": d.Config.MaxConcurrentSessions,
		"dataRetentionDays":     d.Config.DataRetentionDays,
	})
}

// --- 辅助 ---

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

// joinStrings 简单字符串拼接（避免引入 strings 包仅用一个函数）。
func joinStrings(parts []string, sep string) string {
	out := ""
	for i, p := range parts {
		if i > 0 {
			out += sep
		}
		out += p
	}
	return out
}

// intToStr 整型转字符串。
func intToStr(i int) string { return strconv.Itoa(i) }
