package com.linkall.android.data.signaling

import com.linkall.android.data.model.SignalingConnect
import com.linkall.android.data.model.SignalingConnectAck
import com.linkall.android.data.model.SignalingEnvelope
import com.linkall.android.data.model.SignalingError
import com.linkall.android.data.model.SignalingIceCandidate
import com.linkall.android.data.model.SignalingSdp
import com.linkall.android.data.model.SignalingType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket 信令客户端：连接服务端 /ws/signaling 通道
 * 负责建立连接、收发 SDP/ICE 消息、心跳保活
 */
class SignalingClient(
    private val json: kotlinx.serialization.json.Json
) {

    private var socket: WebSocket? = null

    /** 构造信令通道 URL */
    fun buildUrl(baseUrl: String, token: String, deviceId: String? = null): String {
        val url = if (baseUrl.endsWith("/")) baseUrl.trimEnd('/') else baseUrl
        val sb = StringBuilder("$url/ws/signaling?token=$token")
        if (!deviceId.isNullOrBlank()) sb.append("&deviceId=$deviceId")
        return sb.toString()
    }

    /**
     * 建立连接并以 Flow 形式推送接收到的信令消息
     */
    fun connect(serverUrl: String, token: String, deviceId: String? = null): Flow<SignalingEnvelope> = callbackFlow {
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 永不超时
            .build()

        val req = Request.Builder().url(buildUrl(serverUrl, token, deviceId)).build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 发送 connect 握手
                val connect = SignalingEnvelope(
                    type = SignalingType.CONNECT,
                    ts = System.currentTimeMillis(),
                    payload = json.encodeToJsonElement(
                        SignalingConnect.serializer(),
                        SignalingConnect(
                            deviceId = deviceId ?: "",
                            mode = com.linkall.android.data.model.ConnectionMode.SAME_ACCOUNT,
                            token = token
                        )
                    )
                )
                webSocket.send(json.encodeToString(connect))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString<SignalingEnvelope>(text) }
                    .onSuccess { trySend(it) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                channel.close(t)
            }
        })
        socket = ws

        awaitClose {
            ws.close(1000, null)
            socket = null
        }
    }

    /**
     * 发送信令消息
     */
    fun send(message: SignalingEnvelope): Boolean {
        return socket?.send(json.encodeToString(message)) ?: false
    }

    /** 发送 ping 心跳 */
    fun sendPing(): Boolean = send(SignalingEnvelope(SignalingType.PING, System.currentTimeMillis()))

    /** 发送 bye 断开 */
    fun sendBye(sessionId: String?): Boolean = send(
        SignalingEnvelope(SignalingType.BYE, System.currentTimeMillis(), sessionId)
    )

    /** 主动关闭连接 */
    fun close() {
        socket?.close(1000, null)
        socket = null
    }
}

/**
 * 解析信令 payload 为具体类型（便捷扩展）
 */
inline fun <reified T> SignalingEnvelope.payloadAs(json: kotlinx.serialization.json.Json): T? {
    val el = payload ?: return null
    return runCatching { json.decodeFromJsonElement<T>(el) }.getOrNull()
}
