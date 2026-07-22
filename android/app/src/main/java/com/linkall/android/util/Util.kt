package com.linkall.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 轻量级工具：Toast、剪贴板、设备编号生成
 */
object Util {

    /** 生成 12 位随机设备编号（数字+大写字母） */
    fun generateDeviceId(): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..12).map { chars.random() }.joinToString("")
    }

    /** 验证设备编号格式（12 位数字+大写字母） */
    fun isValidDeviceId(id: String): Boolean {
        return id.length == 12 && id.all { it.isDigit() || (it in 'A'..'Z') }
    }

    /** 生成随机设备码（密码，8 位） */
    fun generateDeviceCode(): String {
        val chars = "0123456789ABCDEFGHJKMNPQRSTUVWXYZ"
        return (1..8).map { chars.random() }.joinToString("")
    }

    /** 复制文本到剪贴板 */
    fun copyToClipboard(context: Context, text: String, label: String = "LinkALL") {
        val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mgr.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** 显示 Toast（主线程） */
    fun toast(context: Context, @StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, resId, duration).show()
    }

    /** 显示 Toast（主线程，文本） */
    fun toast(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, text, duration).show()
    }

    /** 格式化字节为人类可读 */
    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> "%.2f GB".format(bytes / gb)
            bytes >= mb -> "%.2f MB".format(bytes / mb)
            bytes >= kb -> "%.1f KB".format(bytes / kb)
            else -> "$bytes B"
        }
    }

    /** 格式化时长（秒 → "1h 2m 3s"） */
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            append("${s}s")
        }
    }
}
