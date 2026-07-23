package handlers

import (
	"net/http"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/filesystem"

	"github.com/linlelest/LinkALL/server/internal/static"
)

// RegisterRoutes 注册所有 HTTP API 路由。
// 公开路由无需鉴权；/api/auth/* 中除 me/refresh/change-password 外也公开；
// /api/admin/* 需要管理员 JWT；/api/announcements 公开读取。
func RegisterRoutes(app *fiber.App, d *Deps) {
	api := app.Group("/api")

	// --- 公开路由 ---
	api.Get("/health", d.HealthCheck)
	api.Get("/security/public-key", d.GetRSAPublicKey) // RSA 公钥（供客户端加密设备码）

	// --- 首次启动初始化路由（公开：仅在无 superadmin 时可用）---
	api.Get("/setup/status", d.GetSetupStatus)
	api.Post("/setup/init", d.InitSetup)

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
	annGroup.Get("/public-key", d.GetAnnouncementPublicKey) // Ed25519 公钥（供客户端验签）
	annGroup.Get("/:id/read", d.MarkAnnouncementRead)        // 简化：GET 即标记已读
	annGroup.Post("/:id/read", d.MarkAnnouncementRead)

	// --- 匿名连接确认路由（公开：被控端用设备码认证）---
	connectGroup := api.Group("/connect/anonymous")
	connectGroup.Get("/policy", d.GetAnonymousPolicy)
	// confirm 路由应用时间戳防重放中间件
	connectGroup.Post("/confirm", d.ReplayProtection, d.ConfirmAnonymousConnection)

	// --- 崩溃上报路由（公开：客户端崩溃时可能已无 JWT）---
	api.Post("/crash", d.ReportCrash)

	// --- 设备路由（需登录，同账号设备发现与配对）---
	deviceGroup := api.Group("/devices")
	deviceGroup.Use(d.JWT.Middleware())
	deviceGroup.Get("/discover", d.DiscoverDevices)
	deviceGroup.Post("/pair", d.PairDevice)
	deviceGroup.Get("/pairings", d.ListPairings)
	deviceGroup.Delete("/pairings/:deviceId", d.RevokePairing)

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

	// 匿名连接白名单管理（管理员）
	admin.Get("/connect/anonymous/whitelist", d.ListAnonymousWhitelist)
	admin.Delete("/connect/anonymous/whitelist/:ip", d.RemoveAnonymousWhitelist)

	// 崩溃报告查询（管理员）
	admin.Get("/crash-reports", d.ListCrashReports)

	// --- WebSocket 信令通道 ---
	// /ws/signaling?token=<JWT>&deviceId=<可选>
	app.Get("/ws/signaling", d.Signaling.HandleFiber())

	// --- 网页端静态文件服务（嵌入在二进制中）---
	// 启动后端即自动提供网页前端，无需额外部署静态文件服务器。
	// 使用 fiber/filesystem 中间件 + embed.FS 提供嵌入式静态文件服务。
	// Next 回调确保 /api/* 和 /ws/* 路由不被拦截，交给已注册的路由处理器处理。
	app.Use("/", filesystem.New(filesystem.Config{
		Root: http.FS(static.FS()),
		Index: "index.html",
		Next: func(c *fiber.Ctx) bool {
			path := c.Path()
			// /api/* 和 /ws/* 路由跳过静态文件中间件
			if len(path) >= 4 && path[:4] == "/api" {
				return true
			}
			if len(path) >= 3 && path[:3] == "/ws" {
				return true
			}
			return false
		},
	}))
}
