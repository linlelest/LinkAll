package handlers

import (
	"strconv"
	"time"

	"github.com/gofiber/fiber/v2"

	"github.com/linlelest/LinkALL/server/internal/auth"
)

// GenerateInviteCodesRequest 生成邀请码请求。
type GenerateInviteCodesRequest struct {
	Count int    `json:"count" xml:"count" form:"count"`
	TTLHours int `json:"ttlHours" xml:"ttlHours" form:"ttlHours"`
	Note   string `json:"note" xml:"note" form:"note"`
}

// GenerateInviteCodes 生成邀请码（管理员）。
// POST /api/admin/invites
func (d *Deps) GenerateInviteCodes(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	var req GenerateInviteCodesRequest
	if err := c.BodyParser(&req); err != nil {
		// 兼容 form 表单
		req.Count, _ = strconv.Atoi(c.FormValue("count"))
		req.TTLHours, _ = strconv.Atoi(c.FormValue("ttlHours"))
		req.Note = c.FormValue("note")
	}
	if req.Count <= 0 {
		req.Count = 1
	}
	if req.Count > 1000 {
		return failBadRequest(c, "单次生成数量不能超过 1000")
	}
	if req.TTLHours <= 0 {
		req.TTLHours = 24 * 7 // 默认 7 天
	}
	uid := getUserIDFromContext(c)
	if uid == 0 {
		// 兜底：使用 1（superadmin）
		uid = 1
	}

	res, err := d.Invites.Generate(auth.GenerateOptions{
		Count:      req.Count,
		TTL:        time.Duration(req.TTLHours) * time.Hour,
		CreatedBy:  uid,
		Note:       req.Note,
	})
	if err != nil {
		return failInternal(c, "生成邀请码失败: "+err.Error())
	}
	return ok(c, fiber.Map{
		"codes": res.Codes,
		"count": len(res.Codes),
	})
}

// ListInvites 列出邀请码（管理员）。
// GET /api/admin/invites?limit=50&offset=0
func (d *Deps) ListInvites(c *fiber.Ctx) error {
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
	codes, total, err := d.Invites.List(limit, offset)
	if err != nil {
		return failInternal(c, "查询邀请码失败")
	}
	return okWithMeta(c, codes, total, limit, offset)
}

// RevokeInvite 吊销邀请码（管理员）。
// POST /api/admin/invites/:id/revoke
func (d *Deps) RevokeInvite(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	id, err := strconv.ParseInt(c.Params("id"), 10, 64)
	if err != nil || id <= 0 {
		// 也允许按 code 吊销
		code := c.Params("id")
		if code == "" {
			return failBadRequest(c, "无效的邀请码 ID")
		}
		if err := d.Invites.Revoke(code); err != nil {
			return failBadRequest(c, err.Error())
		}
		return ok(c, fiber.Map{"revoked": code})
	}
	if err := d.Invites.RevokeByID(id); err != nil {
		return failBadRequest(c, err.Error())
	}
	return ok(c, fiber.Map{"revoked": id})
}

// ExportInvites 批量导出邀请码为 CSV（管理员）。
// GET /api/admin/invites/export
func (d *Deps) ExportInvites(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	data, err := d.Invites.Export()
	if err != nil {
		return failInternal(c, "导出邀请码失败")
	}
	c.Set("Content-Type", "text/csv; charset=utf-8")
	c.Set("Content-Disposition", "attachment; filename=invite-codes.csv")
	return c.Send(data)
}
