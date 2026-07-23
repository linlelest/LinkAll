// 控制端 WebRTC 客户端
// 浏览器原生 RTCPeerConnection + WebSocket 信令通道
// 用于接收远端视频流并通过 DataChannel 下发控制指令

import { tauriListen } from './api.js';

// 信令消息类型（与 shared/messages.json 对齐）
export const SigType = {
    CONNECT: 'connect',
    CONNECT_ACK: 'connect_ack',
    SDP_OFFER: 'sdp_offer',
    SDP_ANSWER: 'sdp_answer',
    ICE_CANDIDATE: 'ice_candidate',
    ICE_COMPLETE: 'ice_complete',
    BYE: 'bye',
    PING: 'ping',
    PONG: 'pong',
    ERROR: 'error',
};

// 心跳与重连参数
const HEARTBEAT_INTERVAL_MS = 15_000;
const RECONNECT_INITIAL_MS = 1_000;
const RECONNECT_MAX_MS = 30_000;
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

// 默认 STUN 服务器
const DEFAULT_ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }];

// 控制指令 DataChannel 标签
const CONTROL_CHANNEL_LABEL = 'control';

/**
 * 控制端 WebRTC 客户端
 * 职责：
 *  1. 通过 Tauri 后端转发信令（WebSocket 由 Rust 端维护）
 *  2. 接收 SDP Answer / ICE Candidate 并应用
 *  3. 在 PeerConnection 上接收远端视频流（渲染到 <video>）
 *  4. 创建 DataChannel 用于发送键鼠/滚轮/手势/设置同步指令
 */
export class WebRtcClient {
    constructor() {
        this.pc = null;
        this.dc = null;
        this.remoteStream = null;
        this.videoEl = null;
        this.iceServers = DEFAULT_ICE_SERVERS;
        this.handlers = {};
        this.statsTimer = null;
        this.durationStart = 0;
        this.durationTimer = null;
        this._unlistens = [];
    }

    /** 注册事件回调 */
    setHandlers(h) {
        this.handlers = h;
    }

    /** 设置远端视频渲染元素 */
    setVideoElement(el) {
        this.videoEl = el;
    }

    /** 创建 PeerConnection 与控制 DataChannel，监听远端轨道 */
    create() {
        this.close();
        this.pc = new RTCPeerConnection({ iceServers: this.iceServers });
        this.remoteStream = new MediaStream();

        // 仅接收视频/音频
        this.pc.addTransceiver('video', { direction: 'recvonly' });
        this.pc.addTransceiver('audio', { direction: 'recvonly' });

        // 控制指令 DataChannel（控制端创建）
        this.dc = this.pc.createDataChannel(CONTROL_CHANNEL_LABEL, { ordered: true });
        this._bindDataChannel();

        // 远端轨道到达
        this.pc.ontrack = (ev) => {
            if (ev.streams && ev.streams.length > 0) {
                this.remoteStream = ev.streams[0];
            } else if (ev.track) {
                this.remoteStream.addTrack(ev.track);
            }
            if (this.videoEl) {
                this.videoEl.srcObject = this.remoteStream;
            }
            this.handlers.onTrack?.(ev.track);
        };

        this.pc.onconnectionstatechange = () => {
            this.handlers.onConnectionStateChange?.(this.pc?.connectionState ?? 'closed');
        };

        // 本地 ICE 候选通过 Tauri 后端发送
        this.pc.onicecandidate = (ev) => {
            if (ev.candidate) {
                this.handlers.onIceCandidate?.(ev.candidate.toJSON());
            } else {
                this.handlers.onIceComplete?.();
            }
        };

        // 订阅 Tauri 后端推送的信令事件
        this._subscribeBackendEvents();
    }

    /** 绑定 DataChannel 事件 */
    _bindDataChannel() {
        if (!this.dc) return;
        this.dc.onopen = () => this.handlers.onDataChannelOpen?.();
        this.dc.onclose = () => this.handlers.onDataChannelClose?.();
        this.dc.onmessage = (ev) => this.handlers.onDataChannelMessage?.(ev.data);
        this.dc.onerror = () => { /* 错误透传到 close */ };
    }

    /** 订阅 Rust 后端转发的信令事件 */
    _subscribeBackendEvents() {
        const un1 = tauriListen('signaling-sdp-answer', (payload) => {
            const sdp = payload?.sdp;
            if (sdp) this.setRemoteAnswer({ type: 'answer', sdp });
        });
        const un2 = tauriListen('signaling-ice-candidate', (payload) => {
            if (payload?.candidate) this.addIceCandidate(payload.candidate);
        });
        const un3 = tauriListen('signaling-connect-ack', (payload) => {
            this.handlers.onConnectAck?.(!!payload?.ok, payload?.sessionId, payload?.requireConfirm);
        });
        const un4 = tauriListen('signaling-bye', (payload) => {
            this.handlers.onBye?.(payload?.reason);
        });
        const un5 = tauriListen('peer-stats', (payload) => {
            this.handlers.onStats?.(payload);
        });
        this._unlistens = [un1, un2, un3, un4, un5];
    }

    /** 应用远端 SDP Answer */
    async setRemoteAnswer(sdp) {
        if (!this.pc) return false;
        try {
            await this.pc.setRemoteDescription(sdp);
            return true;
        } catch (e) {
            console.error('setRemoteAnswer failed', e);
            return false;
        }
    }

    /** 应用远端 ICE Candidate */
    async addIceCandidate(candidate) {
        if (!this.pc) return false;
        try {
            await this.pc.addIceCandidate(candidate);
            return true;
        } catch (e) {
            console.warn('addIceCandidate failed', e);
            return false;
        }
    }

    /** 通过 DataChannel 发送字符串消息 */
    sendByDataChannel(text) {
        if (!this.dc || this.dc.readyState !== 'open') return false;
        try {
            this.dc.send(text);
            return true;
        } catch {
            return false;
        }
    }

    /** 发送键鼠/滚轮/手势控制指令（包装为信封） */
    sendControl(type, payload) {
        const env = {
            type,
            ts: Date.now(),
            seq: 0,
            payload,
        };
        return this.sendByDataChannel(JSON.stringify(env));
    }

    /** 启动统计与时长定时器 */
    startStatsTimer() {
        this.durationStart = Date.now();
        this.statsTimer = setInterval(() => {
            this._collectStats();
        }, 1000);
    }

    /** 采集 PeerConnection 统计信息（RTT/丢包/帧率） */
    async _collectStats() {
        if (!this.pc) return;
        try {
            const stats = await this.pc.getStats();
            let rttMs = 0, packetsLost = 0, packetsReceived = 0, fps = 0;
            stats.forEach((report) => {
                if (report.type === 'candidate-pair' && report.nominated) {
                    rttMs = Math.round(report.currentRoundTripTime * 1000) || rttMs;
                }
                if (report.type === 'inbound-rtp' && report.kind === 'video') {
                    packetsLost = report.packetsLost || 0;
                    packetsReceived = report.packetsReceived || 0;
                    fps = Math.round(report.framesPerSecond || 0);
                }
            });
            const lossRate = packetsReceived > 0 ? (packetsLost / (packetsLost + packetsReceived) * 100) : 0;
            const duration = Math.floor((Date.now() - this.durationStart) / 1000);
            this.handlers.onStats?.({
                rttMs, packetLoss: Math.round(lossRate * 10) / 10, fps, duration,
            });
        } catch {
            // ignore
        }
    }

    /** 关闭 PeerConnection */
    close() {
        if (this.statsTimer) {
            clearInterval(this.statsTimer);
            this.statsTimer = null;
        }
        this._unlistens.forEach((fn) => { try { fn(); } catch { /* ignore */ } });
        this._unlistens = [];
        if (this.dc) {
            try { this.dc.close(); } catch { /* ignore */ }
            this.dc = null;
        }
        if (this.pc) {
            try {
                this.pc.getSenders().forEach((s) => {
                    try { s.track?.stop(); } catch { /* ignore */ }
                });
                this.pc.close();
            } catch { /* ignore */ }
            this.pc = null;
        }
        if (this.remoteStream) {
            this.remoteStream.getTracks().forEach((t) => {
                try { t.stop(); } catch { /* ignore */ }
            });
            this.remoteStream = null;
        }
        if (this.videoEl) {
            try { this.videoEl.srcObject = null; } catch { /* ignore */ }
        }
    }
}

/** 单例客户端 */
export const webrtcClient = new WebRtcClient();
