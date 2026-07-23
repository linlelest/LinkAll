// Package handlers 实现管理后台与认证相关的 HTTP API 路由处理器。
// 遵循轻量化原则：直接使用 sql.DB，不引入 ORM；统一 JSON 响应格式。
package handlers

import (
	"database/sql"

	"github.com/gofiber/fiber/v2"

	"github.com/linlelest/LinkALL/server/internal/auth"
	"github.com/linlelest/LinkALL/server/internal/config"
	"github.com/linlelest/LinkALL/server/internal/i18n"
	"github.com/linlelest/LinkALL/server/internal/webrtc"
)

// Deps 注入到所有 handler 的共享依赖。
type Deps struct {
	DB          *sql.DB
	JWT         *auth.JWTManager
	Hasher      *auth.PasswordHasher
	Invites     *auth.InviteManager
	Signaling   *webrtc.SignalingServer
	Hub         *webrtc.Hub
	Config      *config.Config
	I18N        *i18n.Loader
}

// NewDeps 构造依赖集合。
func NewDeps(
	db *sql.DB,
	jwtMgr *auth.JWTManager,
	hasher *auth.PasswordHasher,
	invites *auth.InviteManager,
	sig *webrtc.SignalingServer,
	hub *webrtc.Hub,
	cfg *config.Config,
	i18nLoader *i18n.Loader,
) *Deps {
	return &Deps{
		DB:        db,
		JWT:       jwtMgr,
		Hasher:    hasher,
		Invites:   invites,
		Signaling: sig,
		Hub:       hub,
		Config:    cfg,
		I18N:      i18nLoader,
	}
}

// === 统一响应封装 ===

// APIResponse 统一 API 响应结构。
type APIResponse struct {
	Code      ErrorCode     `json:"code"`
	Message   string        `json:"message"`
	Data      interface{}   `json:"data,omitempty"`
	Meta      *ResponseMeta `json:"meta,omitempty"`
}

// ResponseMeta 分页元数据。
type ResponseMeta struct {
	Total  int `json:"total"`
	Limit  int `json:"limit"`
	Offset int `json:"offset"`
}

// ErrorCode 与 protocol.ErrorCode 等价，独立定义避免循环引用。
type ErrorCode string

const (
	CodeOK               ErrorCode = "ERR_OK"
	CodeAuthFailed       ErrorCode = "ERR_AUTH_FAILED"
	CodeAuthExpired      ErrorCode = "ERR_AUTH_EXPIRED"
	CodePermissionDenied ErrorCode = "ERR_PERMISSION_DENIED"
	CodeDeviceNotFound   ErrorCode = "ERR_DEVICE_NOT_FOUND"
	CodeInviteInvalid    ErrorCode = "ERR_INVITE_CODE_INVALID"
	CodeInviteUsed       ErrorCode = "ERR_INVITE_CODE_USED"
	CodeInviteExpired    ErrorCode = "ERR_INVITE_CODE_EXPIRED"
	CodeUserBanned       ErrorCode = "ERR_USER_BANNED"
	CodeMaxSessions      ErrorCode = "ERR_MAX_SESSIONS_REACHED"
	CodeRateLimited      ErrorCode = "ERR_RATE_LIMITED"
	CodeInvalidPayload   ErrorCode = "ERR_INVALID_PAYLOAD"
	CodeInternalError    ErrorCode = "ERR_INTERNAL_ERROR"
	CodeSetupAlreadyDone ErrorCode = "ERR_SETUP_ALREADY_DONE"
)

// ok 成功响应。
func ok(c *fiber.Ctx, data interface{}) error {
	return c.JSON(APIResponse{
		Code:    CodeOK,
		Message: "OK",
		Data:    data,
	})
}

// okWithMeta 成功响应（带分页元数据）。
func okWithMeta(c *fiber.Ctx, data interface{}, total, limit, offset int) error {
	return c.JSON(APIResponse{
		Code:    CodeOK,
		Message: "OK",
		Data:    data,
		Meta: &ResponseMeta{
			Total:  total,
			Limit:  limit,
			Offset: offset,
		},
	})
}

// fail 失败响应。
func fail(c *fiber.Ctx, status int, code ErrorCode, msg string) error {
	return c.Status(status).JSON(APIResponse{
		Code:    code,
		Message: msg,
	})
}

// failBadRequest 400。
func failBadRequest(c *fiber.Ctx, msg string) error {
	return fail(c, fiber.StatusBadRequest, CodeInvalidPayload, msg)
}

// failUnauthorized 401。
func failUnauthorized(c *fiber.Ctx, msg string) error {
	return fail(c, fiber.StatusUnauthorized, CodeAuthExpired, msg)
}

// failForbidden 403。
func failForbidden(c *fiber.Ctx, msg string) error {
	return fail(c, fiber.StatusForbidden, CodePermissionDenied, msg)
}

// failNotFound 404。
func failNotFound(c *fiber.Ctx, msg string) error {
	return fail(c, fiber.StatusNotFound, CodeDeviceNotFound, msg)
}

// failInternal 500。
func failInternal(c *fiber.Ctx, msg string) error {
	return fail(c, fiber.StatusInternalServerError, CodeInternalError, msg)
}

// lang 从请求 Accept-Language 或 query 参数提取语言。
func lang(c *fiber.Ctx) string {
	if l := c.Query("lang"); l != "" {
		return l
	}
	if al := c.Get("Accept-Language"); al != "" {
		// 简单解析，取第一个
		for i := 0; i < len(al); i++ {
			if al[i] == ',' || al[i] == ';' || al[i] == ' ' {
				return al[:i]
			}
		}
		return al
	}
	return i18n.DefaultLang
}

// requireAdmin 校验当前用户是否为管理员。
func requireAdmin(c *fiber.Ctx) bool {
	r := auth.CurrentRole(c)
	return r == "admin" || r == "superadmin"
}
