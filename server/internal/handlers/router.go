package handlers

import (
	"github.com/gofiber/fiber/v2"
)

// RegisterRoutes 注册所有 HTTP API 路由。
// 公开路由无需鉴权；/api/auth/* 中除 me/refresh/change-password 外也公开；
// /api/admin/* 需要管理员 JWT；/api/announcements 公开读取。
func RegisterRoutes(app *fiber.App, d *Deps) {
	api := app.Group("/api")

	// --- 公开路由 ---
	api.Get("/health", d.HealthCheck)

	// --- 认证路由 ---
	authGroup := api.Group("/auth")
	authGroup.Post("/login", d.Login)
	authGroup.Post("/register", d.Register)
	// 需登录
	authGroup.Use(d.JWT.Middleware())
	authGroup.Post("/refresh", d.Refresh)
	authGroup.Get("/me", d.Me)
	authGroup.Post("/change-password", d.ChangePassword)

	// --- 公告路由（读取公开，写操作需管理员）---
	annGroup := api.Group("/announcements")
	annGroup.Get("", d.ListAnnouncements)
	annGroup.Get("/:id/read", d.MarkAnnouncementRead) // 简化：GET 即标记已读
	annGroup.Post("/:id/read", d.MarkAnnouncementRead)

	// --- 管理员路由 ---
	admin := api.Group("/admin")
	admin.Use(d.JWT.Middleware("admin", "superadmin"))

	// 用户管理
	admin.Get("/users", d.ListUsers)
	admin.Post("/users/:id/ban", d.BanUser)
	admin.Post("/users/:id/unban", d.UnbanUser)
	admin.Post("/users/:id/reset-password", d.ResetPassword)
	admin.Post("/users/:id/role", d.UpdateRole)
	admin.Delete("/users/:id", d.DeleteUser)

	// 设备管理
	admin.Get("/devices", d.ListDevices)
	admin.Post("/devices/:deviceId/kick", d.KickDevice)

	// 邀请码管理
	admin.Post("/invites", d.GenerateInviteCodes)
	admin.Get("/invites", d.ListInvites)
	admin.Post("/invites/:id/revoke", d.RevokeInvite)
	admin.Get("/invites/export", d.ExportInvites)

	// 全局安全设置
	admin.Get("/security", d.GetSecurity)
	admin.Put("/security", d.UpdateSecurity)
	admin.Get("/security/env-preview", d.EnvPreview)

	// 服务器信息
	admin.Get("/server-info", d.ServerInfoHandler)
	admin.Post("/server-info/env-reload-preview", d.EnvHotReload)

	// 公告管理（管理员写操作）
	admin.Post("/announcements", d.CreateAnnouncement)
	admin.Put("/announcements/:id", d.UpdateAnnouncement)
	admin.Delete("/announcements/:id", d.DeleteAnnouncement)

	// --- WebSocket 信令通道 ---
	// /ws/signaling?token=<JWT>&deviceId=<可选>
	app.Get("/ws/signaling", d.Signaling.HandleFiber())
}
