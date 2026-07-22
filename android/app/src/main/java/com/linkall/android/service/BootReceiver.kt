package com.linkall.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 开机自启接收器：开机后启动被控端保活服务（需用户授权开机自启）
 * Android 10+ 后台启动服务受限，仅拉起 MainActivity 让用户感知
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 启动 MainActivity 让用户感知（Android 10+ 无法直接后台 startService）
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 需 Activity 启动，直接拉起
                    context.startActivity(launchIntent)
                } else {
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
