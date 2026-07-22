package com.linkall.android.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密本地存储：使用 EncryptedSharedPreferences 保护 Token、设备码等敏感数据
 */
class SecureStorage(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "linkall_secure.xml",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 保存 JWT Token */
    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()

    /** 读取 JWT Token */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** 清除 Token */
    fun clearToken() = prefs.edit().remove(KEY_TOKEN).apply()

    /** 保存设备编号（12 位） */
    fun saveDeviceId(id: String) = prefs.edit().putString(KEY_DEVICE_ID, id).apply()

    /** 读取设备编号 */
    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    /** 保存设备码（密码） */
    fun saveDeviceCode(code: String) = prefs.edit().putString(KEY_DEVICE_CODE, code).apply()

    /** 读取设备码 */
    fun getDeviceCode(): String? = prefs.getString(KEY_DEVICE_CODE, null)

    /** 保存用户名 */
    fun saveUsername(name: String) = prefs.edit().putString(KEY_USERNAME, name).apply()

    /** 读取用户名 */
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    /** 清除所有凭证（退出登录） */
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_USERNAME = "username"
    }
}
