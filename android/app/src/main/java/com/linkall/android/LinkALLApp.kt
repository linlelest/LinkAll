package com.linkall.android

import android.app.Application
import android.util.Log
import com.linkall.android.di.appModule
import com.linkall.android.webrtc.WebRtcInitializer
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * LinkALL Application 入口：
 * - 初始化 Koin 依赖注入
 * - 初始化 WebRTC 原生库
 * - 安装全局未捕获异常处理器（轻量 Crashlytics）
 */
class LinkALLApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 安装全局未捕获异常处理器
        installCrashHandler()
        // 初始化 Koin
        startKoin {
            androidContext(this@LinkALLApp)
            modules(appModule)
        }
        // 初始化 WebRTC（加载原生库）
        runCatching { WebRtcInitializer.init(this) }
    }

    /**
     * 轻量崩溃捕获：捕获未处理异常，写入本地崩溃日志文件。
     * 崩溃信息保存在 <filesDir>/crash/ 目录下，供后续上报或导出。
     */
    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashDir = filesDir.resolve("crash").also { it.mkdirs() }
                val timestamp = System.currentTimeMillis()
                val crashFile = crashDir.resolve("crash_$timestamp.log")
                val stackTrace = android.util.Log.getStackTraceString(throwable)
                val content = buildString {
                    appendLine("=== LinkALL Crash Report ===")
                    appendLine("时间: $timestamp")
                    appendLine("线程: ${thread.name}")
                    appendLine("异常: ${throwable::class.java.name}")
                    appendLine("消息: ${throwable.message}")
                    appendLine("堆栈:")
                    appendLine(stackTrace)
                    appendLine("=============================")
                }
                crashFile.writeText(content)
                Log.e("LinkALLCrash", "崩溃已记录: ${crashFile.absolutePath}", throwable)
            } catch (_: Exception) {
                // 崩溃处理器本身出错时静默失败，避免无限循环
            }
            // 交给前一个处理器继续处理（通常终止进程）
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
