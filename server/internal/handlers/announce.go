package handlers

// 公告系统 stub：完整实现在 Phase 5（Markdown 解析、富文本编辑、签名、阅读追踪）。
// Phase 1 仅提供基础 CRUD 占位与返回结构定义。

import (
	"strconv"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
)

// Announcement 公告结构。
type Announcement struct {
	ID            int64  `json:"id"`
	Title         string `json:"title"`
	ContentMD     string `json:"contentMd"`
	Pinned        bool   `json:"pinned"`
	Platform      string `json:"platform"`
	VersionFilter string `json:"versionFilter"`
	CreatedAt     int64  `json:"createdAt"`
	UpdatedAt     int64  `json:"updatedAt"`
	Signature     string `json:"signature,omitempty"`
	AuthorID      int64  `json:"authorId,omitempty"`
	Status        string `json:"status"`
}

// CreateAnnouncementRequest 创建公告请求。
type CreateAnnouncementRequest struct {
	Title         string `json:"title"`
	ContentMD     string `json:"contentMd"`
	Pinned        bool   `json:"pinned"`
	Platform      string `json:"platform"`
	VersionFilter string `json:"versionFilter"`
}

// ListAnnouncements 列出公告（管理员/普通用户均可，普通用户仅看 published）。
// GET /api/announcements?limit=20&offset=0&platform=windows
func (d *Deps) ListAnnouncements(c *fiber.Ctx) error {
	limit, _ := strconv.Atoi(c.Query("limit", "20"))
	offset, _ := strconv.Atoi(c.Query("offset", "0"))
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	if offset < 0 {
		offset = 0
	}
	platform := strings.TrimSpace(c.Query("platform"))
	isAdmin := requireAdmin(c)

	// 构造查询
	query := `SELECT id, title, COALESCE(content_md,''), pinned, platform, COALESCE(version_filter,''),
	                 created_at, updated_at, COALESCE(signature,''), COALESCE(author_id,0), status
	          FROM announcements`
	countQuery := `SELECT COUNT(*) FROM announcements`
	where := []string{}
	args := []interface{}{}
	if !isAdmin {
		where = append(where, "status = 'published'")
	}
	if platform != "" && platform != "all" {
		where = append(where, "(platform = ? OR platform = 'all')")
		args = append(args, platform)
	}
	if len(where) > 0 {
		clause := " WHERE " + strings.Join(where, " AND ")
		query += clause
		countQuery += clause
	}
	query += " ORDER BY pinned DESC, created_at DESC LIMIT ? OFFSET ?"
	argsWith := append(args, limit, offset)

	var total int
	_ = d.DB.QueryRow(countQuery, args...).Scan(&total)

	rows, err := d.DB.Query(query, argsWith...)
	if err != nil {
		return failInternal(c, "查询公告失败")
	}
	defer rows.Close()

	list := make([]Announcement, 0)
	for rows.Next() {
		var ann Announcement
		var pinned int
		if err := rows.Scan(
			&ann.ID, &ann.Title, &ann.ContentMD, &pinned, &ann.Platform,
			&ann.VersionFilter, &ann.CreatedAt, &ann.UpdatedAt, &ann.Signature,
			&ann.AuthorID, &ann.Status,
		); err != nil {
			return failInternal(c, "扫描公告行失败")
		}
		ann.Pinned = pinned == 1
		list = append(list, ann)
	}
	return okWithMeta(c, list, total, limit, offset)
}

// CreateAnnouncement 创建公告（管理员）。
// POST /api/admin/announcements
func (d *Deps) CreateAnnouncement(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	var req CreateAnnouncementRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	if strings.TrimSpace(req.Title) == "" {
		return failBadRequest(c, "标题不能为空")
	}
	if req.Platform == "" {
		req.Platform = "all"
	}
	authorID := getUserIDFromContext(c)
	createdAt := time.Now().Unix()
	// Ed25519 数字签名：对 title + contentMd + createdAt 签名
	signature := SignAnnouncement(req.Title, req.ContentMD, createdAt)
	res, err := d.DB.Exec(
		`INSERT INTO announcements (title, content_md, pinned, platform, version_filter, author_id, status, signature, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, 'published', ?, ?, ?)`,
		req.Title, req.ContentMD, boolToInt(req.Pinned), req.Platform, req.VersionFilter, authorID, signature, createdAt, createdAt,
	)
	if err != nil {
		return failInternal(c, "创建公告失败")
	}
	id, _ := res.LastInsertId()
	return ok(c, Announcement{
		ID:        id,
		Title:     req.Title,
		ContentMD: req.ContentMD,
		Pinned:    req.Pinned,
		Platform:  req.Platform,
		VersionFilter: req.VersionFilter,
		AuthorID:  authorID,
		Status:    "published",
		Signature: signature,
		CreatedAt: createdAt,
		UpdatedAt: createdAt,
	})
}

// UpdateAnnouncement 更新公告（管理员）。
// PUT /api/admin/announcements/:id
// 更新后重新生成 Ed25519 签名（基于新内容 + 原 createdAt 保持签名稳定）
func (d *Deps) UpdateAnnouncement(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的公告 ID")
	}
	var req CreateAnnouncementRequest
	if err := c.BodyParser(&req); err != nil {
		return failBadRequest(c, "请求体格式错误")
	}
	if req.Platform == "" {
		req.Platform = "all"
	}
	// 读取原 createdAt（签名内容包含 createdAt，更新内容需基于原 createdAt 重新签名）
	var createdAt int64
	_ = d.DB.QueryRow(`SELECT created_at FROM announcements WHERE id = ?`, id).Scan(&createdAt)
	if createdAt == 0 {
		createdAt = time.Now().Unix()
	}
	// Ed25519 重新签名：对 title + contentMd + createdAt 签名
	signature := SignAnnouncement(req.Title, req.ContentMD, createdAt)
	updatedAt := time.Now().Unix()
	if _, err := d.DB.Exec(
		`UPDATE announcements
		 SET title = ?, content_md = ?, pinned = ?, platform = ?, version_filter = ?, signature = ?, updated_at = ?
		 WHERE id = ?`,
		req.Title, req.ContentMD, boolToInt(req.Pinned), req.Platform, req.VersionFilter, signature, updatedAt, id,
	); err != nil {
		return failInternal(c, "更新公告失败")
	}
	return ok(c, fiber.Map{"updated": id, "signature": signature})
}

// DeleteAnnouncement 删除公告（管理员，软删除：标记 archived）。
// DELETE /api/admin/announcements/:id
func (d *Deps) DeleteAnnouncement(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的公告 ID")
	}
	res, err := d.DB.Exec(
		`UPDATE announcements SET status = 'archived', updated_at = strftime('%s','now') WHERE id = ?`,
		id,
	)
	if err != nil {
		return failInternal(c, "删除公告失败")
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return failNotFound(c, "公告不存在")
	}
	return ok(c, fiber.Map{"deleted": id})
}

// MarkAnnouncementRead 标记公告已读。
// POST /api/announcements/:id/read
func (d *Deps) MarkAnnouncementRead(c *fiber.Ctx) error {
	uid := getUserIDFromContext(c)
	if uid == 0 {
		return failUnauthorized(c, "需要登录")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		return failBadRequest(c, "无效的公告 ID")
	}
	if _, err := d.DB.Exec(
		`INSERT OR IGNORE INTO announcement_reads (announcement_id, user_id) VALUES (?, ?)`,
		id, uid,
	); err != nil {
		return failInternal(c, "标记已读失败")
	}
	return ok(c, fiber.Map{"read": id})
}

// getUserIDFromContext 从上下文提取用户 ID（辅助）。
func getUserIDFromContext(c *fiber.Ctx) int64 {
	if v := c.Locals("uid"); v != nil {
		if id, ok := v.(int64); ok {
			return id
		}
	}
	return 0
}
