package com.linkall.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 登录请求
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 注册请求
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    @SerialName("invite_code") val inviteCode: String
)

/**
 * 认证响应（含 JWT）
 */
@Serializable
data class AuthResponse(
    val token: String,
    val user: UserInfo
)

/**
 * 用户信息
 */
@Serializable
data class UserInfo(
    val id: Long,
    val username: String,
    val role: String = "user",
    val banned: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * 修改密码请求
 */
@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String
)

/**
 * 标准错误响应
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String = ""
)

/**
 * 公告
 */
@Serializable
data class Announcement(
    val id: Long,
    val title: String,
    @SerialName("contentMd") val contentMd: String = "",
    val pinned: Boolean = false,
    val platform: String = "all",
    @SerialName("versionFilter") val versionFilter: String = "",
    @SerialName("createdAt") val createdAt: Long = 0,
    @SerialName("updatedAt") val updatedAt: Long = 0,
    val signature: String = "",
    @SerialName("authorId") val authorId: Long = 0,
    val status: String = "published"
)

@Serializable
data class AnnouncementList(
    val items: List<Announcement> = emptyList(),
    val total: Int = 0
)

/**
 * 服务端统一响应包装
 * 格式: {code, message, data, meta}
 */
@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    val meta: ResponseMeta? = null
)

@Serializable
data class ResponseMeta(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

/**
 * 公告列表响应（服务端包装格式 {code, message, data: [...], meta}）
 */
@Serializable
data class AnnouncementListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<Announcement> = emptyList(),
    val meta: ResponseMeta? = null
)

/**
 * 设备信息（管理端）
 */
@Serializable
data class DeviceInfo(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String = "",
    val platform: String = "android",
    val version: String = "",
    val status: DeviceStatus = DeviceStatus.OFFLINE,
    @SerialName("last_seen") val lastSeen: String = "",
    val owner: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class DeviceList(
    val items: List<DeviceInfo> = emptyList(),
    val total: Int = 0
)

/**
 * 邀请码
 */
@Serializable
data class InviteCode(
    val code: String,
    val status: String = "unused",
    @SerialName("used_by") val usedBy: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    val note: String = ""
)

@Serializable
data class InviteList(
    val items: List<InviteCode> = emptyList(),
    val total: Int = 0
)

@Serializable
data class GenerateInviteRequest(
    val count: Int = 1,
    @SerialName("ttl_hours") val ttlHours: Int = 24,
    val note: String = ""
)

/**
 * 全局安全设置
 */
@Serializable
data class SecuritySettings(
    @SerialName("force_https") val forceHttps: Boolean = true,
    @SerialName("allow_anonymous") val allowAnonymous: Boolean = false,
    @SerialName("allow_device_code") val allowDeviceCode: Boolean = true,
    @SerialName("allow_remote_control") val allowRemoteControl: Boolean = true,
    @SerialName("max_sessions") val maxSessions: Int = 10,
    @SerialName("retention_days") val retentionDays: Int = 30
)

/**
 * 服务器信息
 */
@Serializable
data class ServerInfo(
    val hostname: String = "",
    val version: String = "",
    @SerialName("go_version") val goVersion: String = "",
    val uptime: Long = 0,
    val cpu: Float = 0f,
    val memory: Float = 0f,
    val goroutines: Int = 0,
    @SerialName("online_devices") val onlineDevices: Int = 0,
    @SerialName("active_sessions") val activeSessions: Int = 0,
    @SerialName("signaling_latency") val signalingLatency: Long = 0
)

/**
 * OTA 版本信息
 */
@Serializable
data class OtaRelease(
    val version: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = false,
    val size: Long = 0,
    @SerialName("sha256") val sha256: String = ""
)
