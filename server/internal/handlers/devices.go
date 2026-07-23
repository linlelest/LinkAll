package handlers

// 同账号设备自动发现 API：登录用户可查询自己名下的所有在线设备，
// 实现免输入设备编号的便捷连接。首次配对需确认，后续可免密直连。
// 设计：
//   - GET /api/devices/discover：返回当前用户的所有设备（在线优先）
//   - POST /api/devices/pair：首次配对确认（设备端发起，记录 pairing 状态）
//   - GET /api/devices/pairings：查询当前用户的配对设备列表
//   - DELETE /api/devices/pairings/:deviceId：撤销配对（取消免密直连）

import (
	"database/sql"
	"errors"
	"strings"

	"github.com/gofiber/fiber/v2"

	"github.com/linlelest/LinkALL/server/internal/auth"
)

// DiscoveredDevice 同账号设备发现响应项。
type DiscoveredDevice struct {
	DeviceID     string `json:"deviceId"`
	DeviceName   string `json:"deviceName"`
	Platform     string `json:"platform"`
	Version      string `json:"version"`
	OnlineStatus string `json:"onlineStatus"`
	LastSeen     int64  `json:"lastSeen"`
	Paired       bool   `json:"paired"` // 是否已完成首次配对（免密直连）
}

// DiscoverDevices 同账号设备自动发现。
// GET /api/devices/discover?online=true
func (d *Deps) DiscoverDevices(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	if uid == 0 {
		return failUnauthorized(c, "需要登录")
	}
	onlineOnly := c.Query("online") == "true"

	var (
		rows *sql.Rows
		err  error
	)
	if onlineOnly {
		rows, err = d.DB.Query(
			`SELECT device_id, COALESCE(device_name,''), COALESCE(platform,''), COALESCE(version,''),
			        online_status, last_seen
			 FROM devices WHERE owner_user_id = ? AND online_status = 'online'
			 ORDER BY last_seen DESC`,
			uid,
		)
	} else {
		rows, err = d.DB.Query(
			`SELECT device_id, COALESCE(device_name,''), COALESCE(platform,''), COALESCE(version,''),
			        online_status, last_seen
			 FROM devices WHERE owner_user_id = ?
			 ORDER BY online_status = 'online' DESC, last_seen DESC`,
			uid,
		)
	}
	if err != nil {
		return failInternal(c, "查询设备失败")
	}
	defer rows.Close()

	// 查询当前用户已配对的设备集合
	paired := make(map[string]bool)
	pairRows, err := d.DB.Query(
		`SELECT device_id FROM device_pairings WHERE controller_user_id = ? AND status = 'confirmed'`,
		uid,
	)
	if err == nil {
		for pairRows.Next() {
			var did string
			_ = pairRows.Scan(&did)
			paired[did] = true
		}
		pairRows.Close()
	}

	devices := make([]DiscoveredDevice, 0)
	for rows.Next() {
		var dev DiscoveredDevice
		if err := rows.Scan(
			&dev.DeviceID, &dev.DeviceName, &dev.Platform, &dev.Version,
			&dev.OnlineStatus, &dev.LastSeen,
		); err != nil {
			return failInternal(c, "扫描设备行失败")
		}
		dev.Paired = paired[dev.DeviceID]
		devices = append(devices, dev)
	}
	if len(devices) == 0 {
		return fail(c, fiber.StatusNotFound, "ERR_DEVICE_DISCOVER_FAILED", "未发现同账号在线设备")
	}
	return ok(c, fiber.Map{
		"devices": devices,
		"total":   len(devices),
	})
}

// PairDeviceRequest 配对请求体。
type PairDeviceRequest struct {
	DeviceID   string `json:"deviceId"`
	DeviceCode string `json:"deviceCode"` // 被控端设备码（认证）
}

// PairDevice 控制端发起首次配对确认（需要设备码认证）。
// POST /api/devices/pair
func (d *Deps) PairDevice(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	if uid == 0 {
		return failUnauthorized(c, "需要登录")
	}
	var req PairDeviceRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	req.DeviceID = strings.TrimSpace(req.DeviceID)
	req.DeviceCode = strings.TrimSpace(req.DeviceCode)
	if req.DeviceID == "" || req.DeviceCode == "" {
		return failBadRequest(c, "deviceId / deviceCode 不能为空")
	}

	// 校验设备码
	var codeHash string
	var ownerUserID sql.NullInt64
	err := d.DB.QueryRow(
		`SELECT device_code_hash, owner_user_id FROM devices WHERE device_id = ?`,
		req.DeviceID,
	).Scan(&codeHash, &ownerUserID)
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

	// 校验设备归属：必须属于当前用户
	if !ownerUserID.Valid || ownerUserID.Int64 != uid {
		return failForbidden(c, "该设备不属于当前账号")
	}

	// 写入配对记录
	_, err = d.DB.Exec(
		`INSERT OR REPLACE INTO device_pairings (device_id, controller_user_id, status, confirmed_at)
		 VALUES (?, ?, 'confirmed', NULL)`,
		req.DeviceID, uid,
	)
	if err != nil {
		// SQLite 不支持 strftime 作为 NULL，回退
		_, err = d.DB.Exec(
			`INSERT OR REPLACE INTO device_pairings (device_id, controller_user_id, status)
		 VALUES (?, ?, 'confirmed')`,
			req.DeviceID, uid,
		)
		if err != nil {
			return failInternal(c, "记录配对失败")
		}
	}
	// 单独更新 confirmed_at
	_, _ = d.DB.Exec(
		`UPDATE device_pairings SET confirmed_at = strftime('%s','now') WHERE device_id = ? AND controller_user_id = ?`,
		req.DeviceID, uid,
	)

	return ok(c, fiber.Map{
		"paired":    true,
		"deviceId":  req.DeviceID,
	})
}

// ListPairings 查询当前用户的配对设备列表。
// GET /api/devices/pairings
func (d *Deps) ListPairings(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	if uid == 0 {
		return failUnauthorized(c, "需要登录")
	}
	rows, err := d.DB.Query(
		`SELECT dp.device_id, COALESCE(dev.device_name,''), COALESCE(dev.online_status,'offline'),
		        COALESCE(dev.platform,''), dp.confirmed_at, dp.created_at
		 FROM device_pairings dp
		 LEFT JOIN devices dev ON dev.device_id = dp.device_id
		 WHERE dp.controller_user_id = ? AND dp.status = 'confirmed'
		 ORDER BY dp.confirmed_at DESC`,
		uid,
	)
	if err != nil {
		return failInternal(c, "查询配对列表失败")
	}
	defer rows.Close()

	type PairingItem struct {
		DeviceID     string `json:"deviceId"`
		DeviceName   string `json:"deviceName"`
		OnlineStatus string `json:"onlineStatus"`
		Platform     string `json:"platform"`
		ConfirmedAt  int64  `json:"confirmedAt"`
		CreatedAt    int64  `json:"createdAt"`
	}
	list := make([]PairingItem, 0)
	for rows.Next() {
		var item PairingItem
		var confirmedAt sql.NullInt64
		if err := rows.Scan(
			&item.DeviceID, &item.DeviceName, &item.OnlineStatus,
			&item.Platform, &confirmedAt, &item.CreatedAt,
		); err != nil {
			return failInternal(c, "扫描配对行失败")
		}
		if confirmedAt.Valid {
			item.ConfirmedAt = confirmedAt.Int64
		}
		list = append(list, item)
	}
	return ok(c, fiber.Map{"pairings": list, "total": len(list)})
}

// RevokePairing 撤销配对（取消免密直连）。
// DELETE /api/devices/pairings/:deviceId
func (d *Deps) RevokePairing(c *fiber.Ctx) error {
	uid := auth.CurrentUserID(c)
	if uid == 0 {
		return failUnauthorized(c, "需要登录")
	}
	deviceID := c.Params("deviceId")
	if deviceID == "" {
		return failBadRequest(c, "deviceId 不能为空")
	}
	res, err := d.DB.Exec(
		`UPDATE device_pairings SET status = 'denied' WHERE device_id = ? AND controller_user_id = ?`,
		deviceID, uid,
	)
	if err != nil {
		return failInternal(c, "撤销配对失败")
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return failNotFound(c, "配对记录不存在")
	}
	return ok(c, fiber.Map{"revoked": deviceID})
}
