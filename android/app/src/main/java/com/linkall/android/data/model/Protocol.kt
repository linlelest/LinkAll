package com.linkall.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 消息类型枚举（与 shared/protocol.json MessageType 一致）
 */
@Serializable
enum class MessageType {
    @SerialName("keyboard") KEYBOARD,
    @SerialName("mouse") MOUSE,
    @SerialName("wheel") WHEEL,
    @SerialName("file_meta") FILE_META,
    @SerialName("file_chunk") FILE_CHUNK,
    @SerialName("file_ack") FILE_ACK,
    @SerialName("file_complete") FILE_COMPLETE,
    @SerialName("settings_sync") SETTINGS_SYNC,
    @SerialName("heartbeat") HEARTBEAT,
    @SerialName("heartbeat_ack") HEARTBEAT_ACK,
    @SerialName("status") STATUS,
    @SerialName("error") ERROR,
    @SerialName("auth") AUTH,
    @SerialName("auth_ack") AUTH_ACK,
    @SerialName("screen_config") SCREEN_CONFIG,
    @SerialName("privacy_screen") PRIVACY_SCREEN,
    @SerialName("clipboard") CLIPBOARD
}

/**
 * 连接模式
 */
@Serializable
enum class ConnectionMode {
    @SerialName("anonymous") ANONYMOUS,
    @SerialName("same_account") SAME_ACCOUNT,
    @SerialName("device_code") DEVICE_CODE
}

/**
 * 鼠标按键
 */
@Serializable
enum class MouseButton {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
    @SerialName("middle") MIDDLE,
    @SerialName("back") BACK,
    @SerialName("forward") FORWARD
}

/**
 * 鼠标动作
 */
@Serializable
enum class MouseAction {
    @SerialName("move") MOVE,
    @SerialName("down") DOWN,
    @SerialName("up") UP,
    @SerialName("click") CLICK,
    @SerialName("double_click") DOUBLE_CLICK
}

/**
 * 设备在线状态
 */
@Serializable
enum class DeviceStatus {
    @SerialName("offline") OFFLINE,
    @SerialName("online") ONLINE,
    @SerialName("busy") BUSY,
    @SerialName("sleeping") SLEEPING
}

/**
 * 消息信封：所有 DataChannel / WebSocket 控制消息的外层包裹
 */
@Serializable
data class Envelope(
    val type: MessageType,
    val ts: Long,
    val seq: Long = 0,
    val sessionId: String? = null,
    val payload: kotlinx.serialization.json.JsonElement? = null
)

/**
 * 键盘事件 payload
 */
@Serializable
data class KeyboardPayload(
    val key: String,
    val action: String,
    val modifiers: List<String> = emptyList()
)

/**
 * 鼠标事件 payload
 */
@Serializable
data class MousePayload(
    val action: MouseAction,
    val button: MouseButton? = null,
    val x: Int? = null,
    val y: Int? = null,
    val dx: Int? = null,
    val dy: Int? = null
)

/**
 * 滚轮事件 payload
 */
@Serializable
data class WheelPayload(
    val deltaX: Int = 0,
    val deltaY: Int
)

/**
 * 文件元数据 payload
 */
@Serializable
data class FileMetaPayload(
    val transferId: String,
    val name: String,
    val size: Long,
    val hash: String,
    val direction: String = "upload",
    val remotePath: String = "",
    val chunkSize: Int = 262144
)

/**
 * 文件分片 payload
 */
@Serializable
data class FileChunkPayload(
    val transferId: String,
    val chunkId: Int,
    val offset: Long,
    val data: String
)

/**
 * 文件分片确认 payload
 */
@Serializable
data class FileAckPayload(
    val transferId: String,
    val chunkId: Int,
    val ok: Boolean
)

/**
 * 文件传输完成 payload
 */
@Serializable
data class FileCompletePayload(
    val transferId: String,
    val ok: Boolean,
    val hash: String = ""
)

/**
 * 设置同步 payload（屏幕/编解码/控制）
 */
@Serializable
data class SettingsSyncPayload(
    val category: String,
    val screen: ScreenConfig? = null,
    val codec: CodecConfig? = null,
    val control: ControlConfig? = null
)

@Serializable
data class ScreenConfig(
    val scale: Float = 1.0f,
    val fps: Int = 30,
    val maxBitrate: Long = 8_000_000
)

@Serializable
data class CodecConfig(
    val video: String = "H264",
    val audio: String = "Opus"
)

@Serializable
data class ControlConfig(
    val privacyScreen: Boolean = false,
    val clipboardSync: Boolean = false
)

/**
 * 心跳 payload
 */
@Serializable
data class HeartbeatPayload(val clientTs: Long)

@Serializable
data class HeartbeatAckPayload(val serverTs: Long, val rtt: Long)

/**
 * 状态上报 payload
 */
@Serializable
data class StatusPayload(
    val rtt: Long,
    val packetLoss: Float = 0f,
    val bitrate: Long = 0,
    val fps: Int = 0,
    val codec: String = "",
    val duration: Long = 0
)

/**
 * 错误 payload
 */
@Serializable
data class ErrorPayload(
    val code: String,
    val message: String = "",
    val details: kotlinx.serialization.json.JsonElement? = null
)

/**
 * 鉴权握手 payload
 */
@Serializable
data class AuthPayload(
    val deviceId: String,
    val mode: ConnectionMode,
    val deviceCode: String? = null,
    val token: String? = null,
    val clientTs: Long = System.currentTimeMillis()
)

@Serializable
data class AuthAckPayload(
    val ok: Boolean,
    val code: String = "ERR_OK",
    val sessionId: String = ""
)

/**
 * 屏幕配置 payload
 */
@Serializable
data class ScreenConfigPayload(
    val width: Int,
    val height: Int,
    val scale: Float = 1.0f,
    val primary: Boolean = true
)

/**
 * 防窥屏 payload
 */
@Serializable
data class PrivacyScreenPayload(val enabled: Boolean)

/**
 * 剪贴板同步 payload
 */
@Serializable
data class ClipboardPayload(val text: String)
