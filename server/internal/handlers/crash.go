package handlers

// 客户端崩溃上报与管理端查询：
//   1. ReportCrash: POST /api/crash（公开接口，客户端崩溃时可能已无 JWT）
//      接收客户端上报的崩溃堆栈、日志摘录、设备信息，持久化到 crash_reports 表
//   2. ListCrashReports: GET /api/admin/crash-reports（管理员）
//      分页查询崩溃报告，支持按平台/客户端 ID 过滤

import (
	"log/slog"
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// CrashReport 崩溃报告结构。
type CrashReport struct {
	ID          int64  `json:"id"`
	ClientID    string `json:"clientId"`
	Platform    string `json:"platform"`
	AppVersion  string `json:"appVersion"`
	CrashType   string `json:"crashType"`
	StackTrace  string `json:"stackTrace"`
	LogExcerpt  string `json:"logExcerpt"`
	DeviceInfo  string `json:"deviceInfo"`
	IP          string `json:"ip"`
	CreatedAt   int64  `json:"createdAt"`
}

// ReportCrashRequest 客户端崩溃上报请求体。
type ReportCrashRequest struct {
	ClientID   string `json:"clientId"`
	Platform   string `json:"platform"`
	AppVersion string `json:"appVersion"`
	CrashType  string `json:"crashType"`
	StackTrace string `json:"stackTrace"`
	LogExcerpt string `json:"logExcerpt"`
	DeviceInfo string `json:"deviceInfo"`
}

// ReportCrash 接收客户端崩溃上报。
// POST /api/crash
// 公开接口（无需 JWT）：客户端崩溃时可能尚未登录或 JWT 已失效。
// 对 stackTrace/logExcerpt 做长度限制（防止超大 payload）。
func (d *Deps) ReportCrash(c *fiber.Ctx) error {
	var req ReportCrashRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	// 基本校验：crashType 或 stackTrace 至少有一个非空
	if strings.TrimSpace(req.StackTrace) == "" && strings.TrimSpace(req.CrashType) == "" {
		return fail(c, fiber.StatusBadRequest, ErrCrashReportInvalid, "崩溃类型与堆栈不能同时为空")
	}
	// 限制单字段最大长度（64KB），防止超大日志撑爆数据库
	maxFieldLen := 64 * 1024
	if len(req.StackTrace) > maxFieldLen {
		req.StackTrace = req.StackTrace[:maxFieldLen] + "\n...[truncated]"
	}
	if len(req.LogExcerpt) > maxFieldLen {
		req.LogExcerpt = req.LogExcerpt[:maxFieldLen] + "\n...[truncated]"
	}
	if len(req.DeviceInfo) > 16*1024 {
		req.DeviceInfo = req.DeviceInfo[:16*1024] + "...[truncated]"
	}
	// 默认平台
	if req.Platform == "" {
		req.Platform = "unknown"
	}
	if req.CrashType == "" {
		req.CrashType = "panic"
	}

	// 写入数据库
	ip := c.IP()
	res, err := d.DB.Exec(
		`INSERT INTO crash_reports (client_id, platform, app_version, crash_type, stack_trace, log_excerpt, device_info, ip)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		req.ClientID, req.Platform, req.AppVersion, req.CrashType,
		req.StackTrace, req.LogExcerpt, req.DeviceInfo, ip,
	)
	if err != nil {
		slog.Error("[crash] 写入崩溃报告失败", "error", err, "client", req.ClientID, "platform", req.Platform)
		return failInternal(c, "保存崩溃报告失败")
	}
	id, _ := res.LastInsertId()
	slog.Warn("[crash] 收到客户端崩溃报告",
		"id", id, "client", req.ClientID, "platform", req.Platform,
		"crashType", req.CrashType, "appVersion", req.AppVersion, "ip", ip,
	)
	return ok(c, fiber.Map{"id": id, "received": true})
}

// ListCrashReports 查询崩溃报告列表（管理员）。
// GET /api/admin/crash-reports?limit=50&offset=0&platform=android&clientId=xxx
func (d *Deps) ListCrashReports(c *fiber.Ctx) error {
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
	platform := strings.TrimSpace(c.Query("platform"))
	clientID := strings.TrimSpace(c.Query("clientId"))

	// 构造查询
	query := `SELECT id, COALESCE(client_id,''), platform, COALESCE(app_version,''),
	                 crash_type, COALESCE(stack_trace,''), COALESCE(log_excerpt,''),
	                 COALESCE(device_info,''), COALESCE(ip,''), created_at
	          FROM crash_reports`
	countQuery := `SELECT COUNT(*) FROM crash_reports`
	where := []string{}
	args := []interface{}{}
	if platform != "" && platform != "all" {
		where = append(where, "platform = ?")
		args = append(args, platform)
	}
	if clientID != "" {
		where = append(where, "client_id LIKE ?")
		args = append(args, "%"+clientID+"%")
	}
	if len(where) > 0 {
		clause := " WHERE " + strings.Join(where, " AND ")
		query += clause
		countQuery += clause
	}
	query += " ORDER BY created_at DESC LIMIT ? OFFSET ?"
	argsWith := append(args, limit, offset)

	var total int
	_ = d.DB.QueryRow(countQuery, args...).Scan(&total)

	rows, err := d.DB.Query(query, argsWith...)
	if err != nil {
		return failInternal(c, "查询崩溃报告失败")
	}
	defer rows.Close()

	list := make([]CrashReport, 0)
	for rows.Next() {
		var r CrashReport
		if err := rows.Scan(
			&r.ID, &r.ClientID, &r.Platform, &r.AppVersion, &r.CrashType,
			&r.StackTrace, &r.LogExcerpt, &r.DeviceInfo, &r.IP, &r.CreatedAt,
		); err != nil {
			return failInternal(c, "扫描崩溃报告行失败")
		}
		list = append(list, r)
	}
	return okWithMeta(c, list, total, limit, offset)
}
