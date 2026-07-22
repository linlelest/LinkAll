package com.linkall.android.webrtc

import android.content.Intent
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer

/**
 * 屏幕捕获器：基于 MediaProjection 创建 WebRTC 视频源
 * 被控端使用，将屏幕画面编码为视频流推送到控制端
 */
class ScreenCapturer(
    private val resultCode: Int,
    private val data: Intent
) {

    private var capturer: ScreenCapturerAndroid? = null

    /**
     * 创建 WebRTC 视频采集器
     * ScreenCapturerAndroid 会自动使用传入 Intent 创建 MediaProjection
     */
    fun createCapturer(): VideoCapturer {
        val c = ScreenCapturerAndroid(resultCode, data) { mediaProjection ->
            // MediaProjection 权限/停止回调
        }
        capturer = c
        return c
    }

    fun dispose() {
        capturer = null
    }
}
