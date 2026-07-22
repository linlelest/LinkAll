package com.linkall.android.webrtc

import android.content.Context
import com.linkall.android.data.model.Envelope
import com.linkall.android.data.model.MessageType
import com.linkall.android.data.model.ScreenConfig
import com.linkall.android.data.model.ScreenConfigPayload
import com.linkall.android.data.model.SettingsSyncPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.IceServer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * WebRTC PeerConnection 管理器：
 * - 被控端：接收 ICE offer，回 SDP answer，发送本地视频流（MediaProjection 截屏）
 * - 控制端：发起 SDP offer，接收并渲染远端视频流
 * - 维护 DataChannel 用于传输控制指令（键盘/鼠标/滚轮/文件）
 *
 * 与信令客户端配合完成 SDP/ICE 交换。
 */
class PeerConnectionManager(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val json: Json
) {

    /** STUN/TURN 服务器列表（公共 STUN，生产建议加 TURN） */
    private val iceServers = listOf(
        IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null

    /** 接收到的 DataChannel 消息流（控制指令信封） */
    private val _incomingMessages = MutableSharedFlow<Envelope>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Envelope> = _incomingMessages

    /** ICE 连接状态变化 */
    private val _iceState = MutableSharedFlow<PeerConnection.IceConnectionState>(extraBufferCapacity = 16)
    val iceState: SharedFlow<PeerConnection.IceConnectionState> = _iceState

    /** 远端视频轨道（控制端渲染用） */
    private val _remoteVideoTrack = MutableSharedFlow<VideoTrack>(extraBufferCapacity = 1)
    val remoteVideoTrack: SharedFlow<VideoTrack> = _remoteVideoTrack

    /**
     * 创建 PeerConnection
     * @param isOffer true=控制端（发起 offer），false=被控端（接收 offer）
     */
    fun createConnection(isOffer: Boolean): PeerConnection {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = factory.createPeerConnection(config, observer) ?: error("创建 PeerConnection 失败")

        // 控制端：创建 DataChannel
        if (isOffer) {
            val dcInit = DataChannel.Init().apply {
                id = 1
                negotiated = false
            }
            dataChannel = pc.createDataChannel("control", dcInit)
            dataChannel?.registerObserver(dcObserver)
        }
        peerConnection = pc
        return pc
    }

    /**
     * 被控端：绑定本地视频源（来自 MediaProjection）
     */
    fun bindLocalVideo(capturer: VideoCapturer) {
        videoCapturer = capturer
        videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("ARDAMS", videoSource)
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(
                listOf(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
            )
        )
        localVideoTrack = track
        peerConnection?.addTrack(track, listOf("stream1"))
    }

    /**
     * 控制端：创建 SDP offer 并设置 local description
     */
    fun createOffer(onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val pc = peerConnection ?: return onFailure("PeerConnection 未初始化")
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { onSuccess(sdp.description) }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) { onFailure(p0 ?: "setLocalDescription 失败") }
                }, sdp)
            }
            override fun onCreateFailure(error: String?) { onFailure(error ?: "createOffer 失败") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        })
    }

    /**
     * 被控端：接收远端 offer 并生成 answer
     */
    fun createAnswer(remoteSdp: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val pc = peerConnection ?: return onFailure("PeerConnection 未初始化")
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() {
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() { onSuccess(sdp.description) }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) { onFailure(p0 ?: "setLocalDescription 失败") }
                        }, sdp)
                    }
                    override fun onCreateFailure(error: String?) { onFailure(error ?: "createAnswer 失败") }
                    override fun onSetSuccess() {}
                    override fun onSetFailure(p0: String?) {}
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String?) { onFailure(error ?: "setRemoteDescription 失败") }
        }, SessionDescription(SessionDescription.Type.OFFER, remoteSdp))
    }

    /** 设置远端 SDP answer */
    fun setRemoteAnswer(sdp: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val pc = peerConnection ?: return onFailure("PeerConnection 未初始化")
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() { onSuccess() }
            override fun onSetFailure(error: String?) { onFailure(error ?: "setRemoteDescription 失败") }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    /** 添加远端 ICE candidate */
    fun addRemoteIce(candidate: IceCandidate, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val pc = peerConnection ?: return onFailure("PeerConnection 未初始化")
        if (pc.addIceCandidate(candidate)) onSuccess() else onFailure("addIceCandidate 失败")
    }

    /** 发送控制指令信封到 DataChannel */
    fun sendEnvelope(env: Envelope): Boolean {
        val dc = dataChannel ?: return false
        val text = json.encodeToString(env)
        return dc.send(DataChannel.Buffer(text.toByteArray(Charsets.UTF_8).asByteBuffer(), false))
    }

    /** 发送屏幕配置同步 */
    fun sendScreenConfig(config: ScreenConfig): Boolean {
        return sendEnvelope(
            Envelope(
                type = MessageType.SETTINGS_SYNC,
                ts = System.currentTimeMillis(),
                seq = nextSeq(),
                payload = json.encodeToJsonElement(
                    SettingsSyncPayload.serializer(),
                    SettingsSyncPayload(category = "screen", screen = config)
                )
            )
        )
    }

    private val seqCounter = java.util.concurrent.atomic.AtomicLong(0)
    private fun nextSeq(): Long = seqCounter.incrementAndGet()

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            _iceState.tryEmit(state)
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            // 通过信令通道转发 ICE candidate（由调用方监听处理）
            _iceCandidate.tryEmit(candidate)
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: org.webrtc.MediaStream?) {}
        override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
        override fun onDataChannel(dc: DataChannel) {
            // 被控端：接收控制端创建的 DataChannel
            dataChannel = dc
            dc.registerObserver(dcObserver)
        }
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out org.webrtc.MediaStream>?) {
            val track = receiver.track()
            if (track is VideoTrack) _remoteVideoTrack.tryEmit(track)
        }
        override fun onTrack(transceiver: RtpTransceiver) {}
    }

    /** 本地生成的 ICE candidate 流 */
    private val _iceCandidate = MutableSharedFlow<IceCandidate>(extraBufferCapacity = 64)
    val localIceCandidate: SharedFlow<IceCandidate> = _iceCandidate

    private val dcObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}
        override fun onStateChange() {}
        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            val text = String(bytes, Charsets.UTF_8)
            runCatching { json.decodeFromString<Envelope>(text) }
                .onSuccess { _incomingMessages.tryEmit(it) }
        }
    }

    /**
     * 释放所有资源
     */
    fun dispose() {
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer = null
        videoSource?.dispose()
        videoSource = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        dataChannel?.close()
        dataChannel?.dispose()
        dataChannel = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }
}
