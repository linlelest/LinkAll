package com.linkall.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.linkall.android.MainActivity
import com.linkall.android.R
import org.koin.core.context.GlobalContext

/**
 * 被控端前台保活服务：
 * - 持有 MediaProjection 截屏流，推送到 WebRTC PeerConnection
 * - 通知栏常驻快捷开关（断开连接/防窥屏/文件接收）
 * - Android 14+ 适配 foregroundServiceType
 *
 * 启动方式：通过 startForegroundService(Intent(extra RESULT_CODE, INTENT))
 */
class MediaProjectionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPrivacyScreen = false

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_DISCONNECT = "com.linkall.android.DISCONNECT"
        const val ACTION_PRIVACY = "com.linkall.android.PRIVACY"
        const val ACTION_FILE = "com.linkall.android.FILE"
        private const val CHANNEL_ID = "linkall_service"
        private const val NOTIF_ID = 1001

        /** 是否运行中（静态标志，供 UI 查询） */
        @Volatile var isRunning = false
            private set

        /** 防窥屏状态（静态标志） */
        @Volatile var privacyScreenEnabled = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> { stopSelfSafely(); return START_NOT_STICKY }
            ACTION_PRIVACY -> togglePrivacyScreen()
            ACTION_FILE -> { /* 文件接收开关由 UI 处理 */ }
        }

        // 启动前台通知
        startForegroundCompat()

        // 初始化 MediaProjection（若携带了授权数据）
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != 0 && data != null) {
            startMediaProjection(resultCode, data)
        }

        isRunning = true
        return START_STICKY // 被杀后尝试重建
    }

    /**
     * 启动 MediaProjection 截屏
     */
    private fun startMediaProjection(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)?.also { projection ->
            // 注册回调：捕获停止时关闭服务
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelfSafely()
                }
            }, null)
            // TODO: 将 projection 接入 WebRTC ScreenCapturerAndroid 推流
        }
    }

    /**
     * 切换防窥屏
     */
    private fun togglePrivacyScreen() {
        isPrivacyScreen = !isPrivacyScreen
        privacyScreenEnabled = isPrivacyScreen
        // 更新通知文案
        startForegroundCompat()
        // TODO: 发送 privacy_screen 信令给控制端
    }

    /**
     * 创建并显示前台通知（兼容 Android 14+ foregroundServiceType）
     */
    private fun startForegroundCompat() {
        createNotificationChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    /**
     * 构造通知栏（含 3 个快捷操作按钮）
     */
    private fun buildNotification(): Notification {
        val deviceId = runCatching {
            GlobalContext.get().getOrNull<com.linkall.android.data.local.SecureStorage>()
                ?.getDeviceId().orEmpty()
        }.getOrDefault("")

        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = actionIntent(ACTION_DISCONNECT, R.string.notif_action_disconnect)
        val privacyIntent = actionIntent(ACTION_PRIVACY, R.string.notif_action_privacy)
        val fileIntent = actionIntent(ACTION_FILE, R.string.notif_action_file)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(getString(R.string.notif_title_running))
            .setContentText(getString(R.string.notif_text_running, deviceId))
            .setContentIntent(tapIntent)
            .addAction(R.drawable.icon, getString(R.string.notif_action_disconnect), disconnectIntent)
            .addAction(R.drawable.icon, getString(R.string.notif_action_privacy), privacyIntent)
            .addAction(R.drawable.icon, getString(R.string.notif_action_file), fileIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun actionIntent(action: String, titleRes: Int): PendingIntent {
        val intent = Intent(this, MediaProjectionService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_service), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.notif_channel_service_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    /**
     * 获取唤醒锁：保活期间防止 CPU 休眠
     */
    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LinkALL::MediaProjection")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(10 * 60 * 1000L) // 10 分钟，超时后自动释放（避免泄漏）
    }

    /**
     * 安全停止服务
     */
    fun stopSelfSafely() {
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { wakeLock?.release() }
        wakeLock = null
        isRunning = false
        privacyScreenEnabled = false
        cancelNotification()
        stopSelf()
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { mediaProjection?.stop() }
        runCatching { wakeLock?.release() }
        isRunning = false
        privacyScreenEnabled = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 任务移除时不停止，保持保活（Android 14+ 限制下可能被杀）
    }
}
