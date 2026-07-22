package com.linkall.android.webrtc

import com.linkall.android.data.model.Envelope
import com.linkall.android.data.model.FileAckPayload
import com.linkall.android.data.model.FileChunkPayload
import com.linkall.android.data.model.FileCompletePayload
import com.linkall.android.data.model.FileMetaPayload
import com.linkall.android.data.model.HeartbeatPayload
import com.linkall.android.data.model.KeyboardPayload
import com.linkall.android.data.model.MessageType
import com.linkall.android.data.model.MouseAction
import com.linkall.android.data.model.MouseButton
import com.linkall.android.data.model.MousePayload
import com.linkall.android.data.model.PrivacyScreenPayload
import com.linkall.android.data.model.ScreenConfig
import com.linkall.android.data.model.SettingsSyncPayload
import com.linkall.android.data.model.WheelPayload
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/**
 * 控制指令构建器：按协议信封格式构造各类消息
 * 供控制端构造发往被控端的指令
 */
class ControlMessageBuilder(private val json: Json) {

    private val seq = AtomicLong(0)
    private fun nextSeq(): Long = seq.incrementAndGet()
    private fun now(): Long = System.currentTimeMillis()

    /** 键盘事件 */
    fun keyboard(key: String, action: String, modifiers: List<String> = emptyList()): Envelope = Envelope(
        type = MessageType.KEYBOARD,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            KeyboardPayload.serializer(),
            KeyboardPayload(key, action, modifiers)
        )
    )

    /** 鼠标移动 */
    fun mouseMove(dx: Int, dy: Int): Envelope = Envelope(
        type = MessageType.MOUSE,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            MousePayload.serializer(),
            MousePayload(MouseAction.MOVE, dx = dx, dy = dy)
        )
    )

    /** 鼠标点击 */
    fun mouseClick(button: MouseButton = MouseButton.LEFT, x: Int? = null, y: Int? = null): Envelope = Envelope(
        type = MessageType.MOUSE,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            MousePayload.serializer(),
            MousePayload(MouseAction.CLICK, button, x, y)
        )
    )

    /** 鼠标按下 */
    fun mouseDown(button: MouseButton, x: Int? = null, y: Int? = null): Envelope = Envelope(
        type = MessageType.MOUSE,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            MousePayload.serializer(),
            MousePayload(MouseAction.DOWN, button, x, y)
        )
    )

    /** 鼠标抬起 */
    fun mouseUp(button: MouseButton): Envelope = Envelope(
        type = MessageType.MOUSE,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            MousePayload.serializer(),
            MousePayload(MouseAction.UP, button)
        )
    )

    /** 双击 */
    fun mouseDoubleClick(button: MouseButton = MouseButton.LEFT, x: Int? = null, y: Int? = null): Envelope = Envelope(
        type = MessageType.MOUSE,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            MousePayload.serializer(),
            MousePayload(MouseAction.DOUBLE_CLICK, button, x, y)
        )
    )

    /** 滚轮（正=向下） */
    fun wheel(deltaY: Int, deltaX: Int = 0): Envelope = Envelope(
        type = MessageType.WHEEL,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            WheelPayload.serializer(),
            WheelPayload(deltaX, deltaY)
        )
    )

    /** 文件元数据 */
    fun fileMeta(meta: FileMetaPayload): Envelope = Envelope(
        type = MessageType.FILE_META,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(FileMetaPayload.serializer(), meta)
    )

    /** 文件分片 */
    fun fileChunk(chunk: FileChunkPayload): Envelope = Envelope(
        type = MessageType.FILE_CHUNK,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(FileChunkPayload.serializer(), chunk)
    )

    /** 文件分片确认 */
    fun fileAck(ack: FileAckPayload): Envelope = Envelope(
        type = MessageType.FILE_ACK,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(FileAckPayload.serializer(), ack)
    )

    /** 文件传输完成 */
    fun fileComplete(complete: FileCompletePayload): Envelope = Envelope(
        type = MessageType.FILE_COMPLETE,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(FileCompletePayload.serializer(), complete)
    )

    /** 设置同步 */
    fun settingsSync(category: String, screen: ScreenConfig? = null): Envelope = Envelope(
        type = MessageType.SETTINGS_SYNC,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            SettingsSyncPayload.serializer(),
            SettingsSyncPayload(category = category, screen = screen)
        )
    )

    /** 防窥屏开关 */
    fun privacyScreen(enabled: Boolean): Envelope = Envelope(
        type = MessageType.PRIVACY_SCREEN,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            PrivacyScreenPayload.serializer(),
            PrivacyScreenPayload(enabled)
        )
    )

    /** 心跳 */
    fun heartbeat(): Envelope = Envelope(
        type = MessageType.HEARTBEAT,
        ts = now(),
        seq = nextSeq(),
        payload = json.encodeToJsonElement(
            HeartbeatPayload.serializer(),
            HeartbeatPayload(now())
        )
    )
}
