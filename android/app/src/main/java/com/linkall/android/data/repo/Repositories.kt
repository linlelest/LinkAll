package com.linkall.android.data.repo

import com.linkall.android.data.api.LinkAllApi
import com.linkall.android.data.local.AppSettings
import com.linkall.android.data.local.SecureStorage
import com.linkall.android.data.model.Announcement
import com.linkall.android.data.model.AnnouncementList
import com.linkall.android.data.model.ChangePasswordRequest
import com.linkall.android.data.model.DeviceInfo
import com.linkall.android.data.model.DeviceList
import com.linkall.android.data.model.GenerateInviteRequest
import com.linkall.android.data.model.InviteCode
import com.linkall.android.data.model.InviteList
import com.linkall.android.data.model.LoginRequest
import com.linkall.android.data.model.RegisterRequest
import com.linkall.android.data.model.SecuritySettings
import com.linkall.android.data.model.ServerInfo
import com.linkall.android.data.model.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 认证仓库：登录/注册/Token 管理/用户信息
 */
class AuthRepository(
    private val api: LinkAllApi,
    private val storage: SecureStorage
) {

    private fun authHeader(): String = "Bearer ${storage.getToken().orEmpty()}"

    /** 登录并持久化 Token */
    suspend fun login(username: String, password: String): Result<UserInfo> = runCatching {
        val resp = api.login(LoginRequest(username, password))
        storage.saveToken(resp.token)
        storage.saveUsername(resp.user.username)
        resp.user
    }

    /** 注册并自动登录 */
    suspend fun register(username: String, password: String, inviteCode: String): Result<UserInfo> = runCatching {
        val resp = api.register(RegisterRequest(username, password, inviteCode))
        storage.saveToken(resp.token)
        storage.saveUsername(resp.user.username)
        resp.user
    }

    /** 获取当前用户信息 */
    suspend fun getMe(): Result<UserInfo> = runCatching { api.getMe(authHeader()) }

    /** 修改密码 */
    suspend fun changePassword(old: String, new: String): Result<Unit> = runCatching {
        api.changePassword(authHeader(), ChangePasswordRequest(old, new))
    }

    /** 退出登录：清除凭证 */
    fun logout() = storage.clearToken()

    /** 是否已登录 */
    fun isLoggedIn(): Boolean = !storage.getToken().isNullOrBlank()

    /** 当前用户名 */
    fun username(): String? = storage.getUsername()
}

/**
 * 公告仓库
 */
class AnnouncementRepository(private val api: LinkAllApi, private val storage: SecureStorage) {
    private fun authHeader(): String = "Bearer ${storage.getToken().orEmpty()}"

    suspend fun list(): Result<AnnouncementList> = runCatching { api.getAnnouncements(authHeader()) }
    suspend fun markRead(id: Long): Result<Unit> = runCatching { api.markAnnouncementRead(authHeader(), id) }
}

/**
 * 设备管理仓库（管理员）
 */
class DeviceRepository(private val api: LinkAllApi, private val storage: SecureStorage) {
    private fun authHeader(): String = "Bearer ${storage.getToken().orEmpty()}"

    suspend fun list(onlineOnly: Boolean = false): Result<DeviceList> = runCatching {
        api.getDevices(authHeader(), if (onlineOnly) true else null)
    }
    suspend fun kick(deviceId: String): Result<Unit> = runCatching { api.kickDevice(authHeader(), deviceId) }
}

/**
 * 邀请码仓库
 */
class InviteRepository(private val api: LinkAllApi, private val storage: SecureStorage) {
    private fun authHeader(): String = "Bearer ${storage.getToken().orEmpty()}"

    suspend fun list(): Result<InviteList> = runCatching { api.getInvites(authHeader()) }
    suspend fun generate(count: Int, ttlHours: Int, note: String): Result<InviteList> = runCatching {
        api.generateInvites(authHeader(), GenerateInviteRequest(count, ttlHours, note))
    }
    suspend fun exportCsv(): Result<String> = runCatching { api.exportInvites(authHeader()) }
}

/**
 * 安全设置仓库
 */
class SecurityRepository(
    private val api: LinkAllApi,
    private val storage: SecureStorage,
    private val settings: AppSettings
) {
    private fun authHeader(): String = "Bearer ${storage.getToken().orEmpty()}"

    suspend fun get(): Result<SecuritySettings> = runCatching { api.getSecurity(authHeader()) }
    suspend fun update(s: SecuritySettings): Result<Unit> = runCatching { api.updateSecurity(authHeader(), s) }

    /** 本地被控安全设置流 */
    fun localAllowAnonymous(): Flow<Boolean> = settings.allowAnonymous
    fun localAllowDeviceCode(): Flow<Boolean> = settings.allowDeviceCode
    fun localAllowRemoteControl(): Flow<Boolean> = settings.allowRemoteControl

    suspend fun setLocalAllowAnonymous(v: Boolean) = settings.setAllowAnonymous(v)
    suspend fun setLocalAllowDeviceCode(v: Boolean) = settings.setAllowDeviceCode(v)
    suspend fun setLocalAllowRemoteControl(v: Boolean) = settings.setAllowRemoteControl(v)
}

/**
 * 服务器信息仓库
 */
class ServerRepository(private val api: LinkAllApi, private val storage: SecureStorage) {
    private fun authHeader(): String = "Bearer ${storage.getToken().orEmpty()}"
    suspend fun getInfo(): Result<ServerInfo> = runCatching { api.getServerInfo(authHeader()) }
}
