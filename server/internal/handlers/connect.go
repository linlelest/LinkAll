package handlers

// 匿名连接确认 API：被控端通过此接口提交对匿名控制请求的确认结果。
// 流程：
//   1. 控制端通过 WebSocket 信令发起 connect(mode=anonymous)
//   2. 服务端转发给被控端，被控端 UI 弹出确认框
//   3. 被控端用户选择 [允许一次] [永久允许] [拒绝] [输入设备码放行]
//   4. 被控端调用 POST /api/connect/anonymous/confirm 提交结果
//   5. 服务端更新白名单（永久允许时），并通过 WebSocket 通知控制端
// 同时提供策略预检接口供控制端在发起信令前查询。

import (
	"database/sql"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
)

// AnonymousPolicy 匿名连接策略响应。
type AnonymousPolicy struct {
	AllowAnonymous  bool     `json:"allowAnonymous"`
	AllowDeviceCode bool     `json:"allowDeviceCode"`
	RequireConfirm  bool     `json:"requireConfirm"`
	Whitelist       []string `json:"whitelist"`
	DeviceOnline    bool     `json:"deviceOnline"`
}

// GetAnonymousPolicy 查询设备的匿名连接策略（控制端预检）。
// GET /api/connect/anonymous/policy?deviceId=xxx&requesterIp=xxx
func (d *Deps) GetAnonymousPolicy(c *fiber.Ctx) error {
	deviceID := strings.TrimSpace(c.Query("deviceId"))
	if deviceID == "" {
		return failBadRequest(c, "deviceId 不能为空")
	}
	requesterIP := c.Query("requesterIp")
	if requesterIP == "" {
		requesterIP = c.IP()
	}

	// 读取全局安全设置
	var (
		allowAnon    int
		allowDevCode int
		anonWLJSON   string
	)
	_ = d.DB.QueryRow(
		`SELECT allow_anonymous, allow_device_code, anonymous_whitelist FROM security_settings WHERE id = 1`,
	).Scan(&allowAnon, &allowDevCode, &anonWLJSON)

	// 设备是否在线
	deviceOnline := false
	if client := d.Hub.FindDevice(deviceID); client != nil {
		deviceOnline = true
	}

	// 解析白名单
	var whitelist []string
	if anonWLJSON != "" && anonWLJSON != "[]" {
		_ = json.Unmarshal([]byte(anonWLJSON), &whitelist)
	}
	if whitelist == nil {
		whitelist = []string{}
	}

	// 若请求者在白名单中，则不需要确认
	requireConfirm := true
	for _, ip := range whitelist {
		if ip == requesterIP {
			requireConfirm = false
			break
		}
	}

	return ok(c, AnonymousPolicy{
		AllowAnonymous:  allowAnon == 1,
		AllowDeviceCode: allowDevCode == 1,
		RequireConfirm:  requireConfirm,
		Whitelist:       whitelist,
		DeviceOnline:    deviceOnline,
	})
}

// ConfirmAnonymousRequest 匿名连接确认请求体。
type ConfirmAnonymousRequest struct {
	DeviceID            string `json:"deviceId"`
	DeviceCode          string `json:"deviceCode"`           // 被控端设备码（认证）
	SessionID           string `json:"sessionId"`            // 待确认的会话 ID
	Action              string `json:"action"`               // allow_once / allow_always / deny / device_code
	RequesterIP         string `json:"requesterIp"`           // 控制端 IP（allow_always 时加入白名单）
	DeviceCodeForController string `json:"deviceCodeForController,omitempty"` // action=device_code 时返回给控制端的设备码
}

// ConfirmAnonymousConnection 被控端提交匿名连接确认结果。
// POST /api/connect/anonymous/confirm
func (d *Deps) ConfirmAnonymousConnection(c *fiber.Ctx) error {
	var req ConfirmAnonymousRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	req.DeviceID = strings.TrimSpace(req.DeviceID)
	req.DeviceCode = strings.TrimSpace(req.DeviceCode)
	req.SessionID = strings.TrimSpace(req.SessionID)
	req.Action = strings.TrimSpace(req.Action)

	if req.DeviceID == "" || req.DeviceCode == "" || req.SessionID == "" {
		return failBadRequest(c, "deviceId / deviceCode / sessionId 不能为空")
	}
	switch req.Action {
	case "allow_once", "allow_always", "deny", "device_code":
		// 合法
	default:
		return failBadRequest(c, "action 必须为 allow_once / allow_always / deny / device_code")
	}

	// 校验设备码
	var codeHash string
	err := d.DB.QueryRow(`SELECT device_code_hash FROM devices WHERE device_id = ?`, req.DeviceID).Scan(&codeHash)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return failNotFound(c, "设备不存在")
		}
		return failInternal(c, "查询设备失败")
	}
	match, err := d.Hasher.Verify(req.DeviceCode, codeHash)
	if err != nil || !match {
		return failUnauthorized(c, "设备码错误")
	}

	// 处理永久允许：加入白名单
	if req.Action == "allow_always" && req.RequesterIP != "" {
		if err := d.addToAnonymousWhitelist(req.RequesterIP); err != nil {
			return failInternal(c, "更新白名单失败")
		}
	}

	// 通过 WebSocket 信令通知控制端
	sess := d.Hub.FindSession(req.SessionID)
	if sess != nil && sess.Controller != nil {
		// 构造确认结果信令（复用 connect_ack）
		allowed := req.Action == "allow_once" || req.Action == "allow_always" || req.Action == "device_code"
		// 使用 SignalEnvelope 直接发送
		ackPayload := map[string]interface{}{
			"ok":              allowed,
			"sessionId":       req.SessionID,
			"requireConfirm":  false,
			"anonymousAction":  req.Action,
		}
		if req.Action == "device_code" && req.DeviceCodeForController != "" {
			ackPayload["deviceCode"] = req.DeviceCodeForController
		}
		ackJSON, _ := json.Marshal(ackPayload)
		// 直接通过 WebSocket 发送 connect_ack
		msg := map[string]interface{}{
			"type":      "connect_ack",
			"ts":        time.Now().UnixMilli(),
			"sessionId": req.SessionID,
			"payload":   json.RawMessage(ackJSON),
		}
		_ = sess.Controller.SendJSON(msg)
	}

	// 记录到 device_pairings（如果是 device_code 放行，便于审计）
	if req.Action == "device_code" {
		_, _ = d.DB.Exec(
			`INSERT OR REPLACE INTO device_pairings (device_id, controller_user_id, status, pair_token, confirmed_at)
			 VALUES (?, NULL, 'confirmed', ?, ?)`,
			req.DeviceID, req.DeviceCodeForController, time.Now().Unix(),
		)
	}

	return ok(c, fiber.Map{
		"confirmed": true,
		"action":   req.Action,
	})
}

// addToAnonymousWhitelist 将 IP 加入匿名白名单。
func (d *Deps) addToAnonymousWhitelist(ip string) error {
	// 读取当前白名单
	var wlJSON string
	_ = d.DB.QueryRow(`SELECT anonymous_whitelist FROM security_settings WHERE id = 1`).Scan(&wlJSON)
	var whitelist []string
	if wlJSON != "" && wlJSON != "[]" {
		_ = json.Unmarshal([]byte(wlJSON), &whitelist)
	}
	// 去重添加
	exists := false
	for _, e := range whitelist {
		if e == ip {
			exists = true
			break
		}
	}
	if !exists {
		whitelist = append(whitelist, ip)
	}
	raw, _ := json.Marshal(whitelist)
	_, err := d.DB.Exec(
		`UPDATE security_settings SET anonymous_whitelist = ?, updated_at = strftime('%s','now') WHERE id = 1`,
		string(raw),
	)
	return err
}

// ListAnonymousWhitelist 列出匿名白名单（管理员）。
// GET /api/admin/connect/anonymous/whitelist
func (d *Deps) ListAnonymousWhitelist(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	var wlJSON string
	_ = d.DB.QueryRow(`SELECT anonymous_whitelist FROM security_settings WHERE id = 1`).Scan(&wlJSON)
	var whitelist []string
	if wlJSON != "" && wlJSON != "[]" {
		_ = json.Unmarshal([]byte(wlJSON), &whitelist)
	}
	if whitelist == nil {
		whitelist = []string{}
	}
	return ok(c, fiber.Map{"whitelist": whitelist})
}

// RemoveAnonymousWhitelist 移除匿名白名单条目（管理员）。
// DELETE /api/admin/connect/anonymous/whitelist/:ip
func (d *Deps) RemoveAnonymousWhitelist(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	ip := c.Params("ip")
	if ip == "" {
		return failBadRequest(c, "ip 不能为空")
	}
	var wlJSON string
	_ = d.DB.QueryRow(`SELECT anonymous_whitelist FROM security_settings WHERE id = 1`).Scan(&wlJSON)
	var whitelist []string
	if wlJSON != "" && wlJSON != "[]" {
		_ = json.Unmarshal([]byte(wlJSON), &whitelist)
	}
	filtered := make([]string, 0, len(whitelist))
	for _, e := range whitelist {
		if e != ip {
			filtered = append(filtered, e)
		}
	}
	raw, _ := json.Marshal(filtered)
	_, err := d.DB.Exec(
		`UPDATE security_settings SET anonymous_whitelist = ?, updated_at = strftime('%s','now') WHERE id = 1`,
		string(raw),
	)
	if err != nil {
		return failInternal(c, "更新白名单失败")
	}
	return ok(c, fiber.Map{"removed": ip})
}
