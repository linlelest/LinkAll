package com.linkall.android.data.api

import com.linkall.android.data.model.AnnouncementList
import com.linkall.android.data.model.AnnouncementListResponse
import com.linkall.android.data.model.ApiError
import com.linkall.android.data.model.AuthResponse
import com.linkall.android.data.model.ChangePasswordRequest
import com.linkall.android.data.model.DeviceList
import com.linkall.android.data.model.GenerateInviteRequest
import com.linkall.android.data.model.InviteList
import com.linkall.android.data.model.LoginRequest
import com.linkall.android.data.model.OtaRelease
import com.linkall.android.data.model.RegisterRequest
import com.linkall.android.data.model.SecuritySettings
import com.linkall.android.data.model.ServerInfo
import com.linkall.android.data.model.UserInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * LinkALL 服务端 REST API 接口定义
 * 对接服务端 /api 路由
 */
interface LinkAllApi {

    // ===== 认证 =====

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun getMe(@Header("Authorization") auth: String): UserInfo

    @POST("api/auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") auth: String,
        @Body body: ChangePasswordRequest
    ): ApiError

    // ===== 公告 =====

    @GET("api/announcements")
    suspend fun getAnnouncements(@Header("Authorization") auth: String): AnnouncementListResponse

    @POST("api/announcements/{id}/read")
    suspend fun markAnnouncementRead(
        @Header("Authorization") auth: String,
        @Path("id") id: Long
    ): ApiError

    // ===== 管理端：设备管理 =====

    @GET("api/admin/devices")
    suspend fun getDevices(
        @Header("Authorization") auth: String,
        @Query("online") online: Boolean? = null
    ): DeviceList

    @POST("api/admin/devices/{deviceId}/kick")
    suspend fun kickDevice(
        @Header("Authorization") auth: String,
        @Path("deviceId") deviceId: String
    ): ApiError

    // ===== 管理端：邀请码 =====

    @POST("api/admin/invites")
    suspend fun generateInvites(
        @Header("Authorization") auth: String,
        @Body body: GenerateInviteRequest
    ): InviteList

    @GET("api/admin/invites")
    suspend fun getInvites(@Header("Authorization") auth: String): InviteList

    @GET("api/admin/invites/export")
    suspend fun exportInvites(@Header("Authorization") auth: String): String

    // ===== 管理端：安全设置 =====

    @GET("api/admin/security")
    suspend fun getSecurity(@Header("Authorization") auth: String): SecuritySettings

    @PUT("api/admin/security")
    suspend fun updateSecurity(
        @Header("Authorization") auth: String,
        @Body body: SecuritySettings
    ): ApiError

    // ===== 管理端：服务器信息 =====

    @GET("api/admin/server-info")
    suspend fun getServerInfo(@Header("Authorization") auth: String): ServerInfo

    // ===== OTA =====

    @GET("api/ota/latest")
    suspend fun getLatestRelease(
        @Query("platform") platform: String = "android",
        @Query("version") currentVersion: String = "1.0.0"
    ): OtaRelease
}
