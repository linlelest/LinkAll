package com.linkall.android.webrtc

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * WebRTC 初始化器：单例持有一个 PeerConnectionFactory
 * 负责加载原生库、初始化编解码器
 */
object WebRtcInitializer {

    @Volatile private var initialized = false
    @Volatile private var eglBase: EglBase? = null

    /** 获取共享的 EglBase */
    fun getEglBase(): EglBase {
        return eglBase ?: synchronized(this) {
            eglBase ?: EglBase.create().also { eglBase = it }
        }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            // 加载 libwebrtc 原生库
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            initialized = true
        }
    }

    /**
     * 创建 PeerConnectionFactory：硬件编码优先，软件解码兜底
     */
    fun createFactory(context: Context): PeerConnectionFactory {
        init(context)
        val egl = getEglBase()
        val audioDevice = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
}
