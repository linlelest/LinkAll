package com.linkall.android

import android.app.Application
import com.linkall.android.di.appModule
import com.linkall.android.webrtc.WebRtcInitializer
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * LinkALL Application 入口：
 * - 初始化 Koin 依赖注入
 * - 初始化 WebRTC 原生库
 */
class LinkALLApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化 Koin
        startKoin {
            androidContext(this@LinkALLApp)
            modules(appModule)
        }
        // 初始化 WebRTC（加载原生库）
        runCatching { WebRtcInitializer.init(this) }
    }
}
