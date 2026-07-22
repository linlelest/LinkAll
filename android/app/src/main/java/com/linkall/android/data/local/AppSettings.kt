package com.linkall.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用偏好设置：使用 DataStore 存储非敏感配置（语言、主题、服务器地址、连接设置等）
 */
private val Context.appDataStore by preferencesDataStore(name = "linkall_settings")

class AppSettings(context: Context) {

    private val dataStore = context.appDataStore

    /** 语言代码（zh-CN / en-US / system） */
    val language: Flow<String> = dataStore.data.map { it[KEY_LANGUAGE] ?: "system" }

    /** 自定义服务器地址 */
    val serverAddress: Flow<String> = dataStore.data.map { it[KEY_SERVER] ?: "" }

    /** 连接超时（秒） */
    val connectionTimeout: Flow<Int> = dataStore.data.map { it[KEY_TIMEOUT] ?: 15 }

    /** 日志级别（debug/info/warn/error） */
    val logLevel: Flow<String> = dataStore.data.map { it[KEY_LOG_LEVEL] ?: "info" }

    /** 被控端：是否允许匿名连接 */
    val allowAnonymous: Flow<Boolean> = dataStore.data.map { it[KEY_ALLOW_ANON] ?: false }

    /** 被控端：是否允许设备码连接 */
    val allowDeviceCode: Flow<Boolean> = dataStore.data.map { it[KEY_ALLOW_CODE] ?: true }

    /** 被控端：远程控制总开关 */
    val allowRemoteControl: Flow<Boolean> = dataStore.data.map { it[KEY_ALLOW_REMOTE] ?: true }

    suspend fun setLanguage(value: String) = dataStore.edit { it[KEY_LANGUAGE] = value }
    suspend fun setServerAddress(value: String) = dataStore.edit { it[KEY_SERVER] = value }
    suspend fun setConnectionTimeout(value: Int) = dataStore.edit { it[KEY_TIMEOUT] = value }
    suspend fun setLogLevel(value: String) = dataStore.edit { it[KEY_LOG_LEVEL] = value }
    suspend fun setAllowAnonymous(value: Boolean) = dataStore.edit { it[KEY_ALLOW_ANON] = value }
    suspend fun setAllowDeviceCode(value: Boolean) = dataStore.edit { it[KEY_ALLOW_CODE] = value }
    suspend fun setAllowRemoteControl(value: Boolean) = dataStore.edit { it[KEY_ALLOW_REMOTE] = value }

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_SERVER = stringPreferencesKey("server_address")
        private val KEY_TIMEOUT = intPreferencesKey("connection_timeout")
        private val KEY_LOG_LEVEL = stringPreferencesKey("log_level")
        private val KEY_ALLOW_ANON = booleanPreferencesKey("allow_anonymous")
        private val KEY_ALLOW_CODE = booleanPreferencesKey("allow_device_code")
        private val KEY_ALLOW_REMOTE = booleanPreferencesKey("allow_remote_control")
    }
}
