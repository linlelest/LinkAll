package com.linkall.android.data.repo

import com.linkall.android.data.api.LinkAllApi
import com.linkall.android.data.local.SecureStorage
import com.linkall.android.data.model.OtaRelease
import java.security.MessageDigest

/**
 * OTA 更新仓库：检查版本、下载 APK、引导安装
 */
class OtaRepository(
    private val api: LinkAllApi,
    private val storage: SecureStorage
) {

    /**
     * 检查最新版本
     * @param currentVersion 当前 App 版本名（如 1.0.0）
     */
    suspend fun checkUpdate(currentVersion: String): Result<OtaRelease> = runCatching {
        api.getLatestRelease(platform = "android", currentVersion = currentVersion)
    }

    /**
     * 比较版本号：返回 true 表示 remote 更新
     */
    fun isNewer(current: String, remote: String): Boolean {
        val a = current.split(".").map { it.toIntOrNull() ?: 0 }
        val b = remote.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: 0
            val y = b.getOrNull(i) ?: 0
            if (x != y) return y > x
        }
        return false
    }

    /**
     * 计算 SHA-256 文件哈希（Hex）
     */
    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }
}
