package com.linkall.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WebSocket 信令消息类型（与 shared/messages.json SignalingType 一致）
 */
@Serializable
enum class SignalingType {
    @SerialName("connect") CONNECT,
    @SerialName("connect_ack") CONNECT_ACK,
    @SerialName("sdp_offer") SDP_OFFER,
    @SerialName("sdp_answer") SDP_ANSWER,
    @SerialName("ice_candidate") ICE_CANDIDATE,
    @SerialName("ice_complete") ICE_COMPLETE,
    @SerialName("bye") BYE,
    @SerialName("ping") PING,
    @SerialName("pong") PONG,
    @SerialName("error") ERROR
}

/**
 * 信令通道消息信封
 */
@Serializable
data class SignalingEnvelope(
    val type: SignalingType,
    val ts: Long,
    val sessionId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val payload: kotlinx.serialization.json.JsonElement? = null
)

/**
 * connect 消息 payload
 */
@Serializable
data class SignalingConnect(
    val deviceId: String,
    val mode: ConnectionMode,
    val token: String? = null,
    val deviceCode: String? = null
)

/**
 * connect_ack 消息 payload
 */
@Serializable
data class SignalingConnectAck(
    val ok: Boolean,
    val code: String = "ERR_OK",
    val sessionId: String = "",
    val requireConfirm: Boolean = false
)

/**
 * SDP 交换 payload
 */
@Serializable
data class SignalingSdp(
    val sdp: String,
    val type: String
)

/**
 * ICE candidate payload
 */
@Serializable
data class SignalingIceCandidate(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?
)

/**
 * 错误消息 payload
 */
@Serializable
data class SignalingError(
    val code: String,
    val message: String = ""
)
