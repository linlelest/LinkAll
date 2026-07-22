// WebSocket 信令客户端：connect/SDP/ICE 交换/心跳 15s/指数退避重连/会话超时 30min。
// 协议见 shared/messages.json。
import { authStore } from '$lib/stores/auth';
import { getBaseUrl } from '$lib/api/client';

export type SignalingMsgType =
  | 'connect'
  | 'connect_ack'
  | 'sdp_offer'
  | 'sdp_answer'
  | 'ice_candidate'
  | 'ice_complete'
  | 'bye'
  | 'ping'
  | 'pong'
  | 'error';

export interface SignalingEnvelope {
  type: SignalingMsgType;
  ts: number;
  sessionId?: string;
  from?: string;
  to?: string;
  // 各类型 payload 字段
  deviceId?: string;
  mode?: 'anonymous' | 'same_account' | 'device_code';
  token?: string;
  deviceCode?: string;
  ok?: boolean;
  code?: string;
  message?: string;
  requireConfirm?: boolean;
  sdp?: { type: 'offer' | 'answer'; sdp: string };
  candidate?: RTCIceCandidateInit;
  reason?: string;
  clientTs?: number;
  serverTs?: number;
  rtt?: number;
}

export interface SignalingHandlers {
  onOpen?: () => void;
  onClose?: (ev: CloseEvent) => void;
  onError?: (e: unknown) => void;
  onMessage?: (msg: SignalingEnvelope) => void;
  // 便捷回调
  onConnectAck?: (ok: boolean, sessionId?: string, requireConfirm?: boolean, code?: string) => void;
  onSdpAnswer?: (sdp: RTCSessionDescriptionInit) => void;
  onIceCandidate?: (candidate: RTCIceCandidateInit) => void;
  onIceComplete?: () => void;
  onBye?: (reason?: string) => void;
  onPong?: (rtt: number) => void;
  onErrorMsg?: (code: string, message?: string) => void;
}

// 心跳与重连参数（与 shared/messages.json 对齐）
const HEARTBEAT_INTERVAL_MS = 15_000;
const RECONNECT_INITIAL_MS = 1_000;
const RECONNECT_MULTIPLIER = 2;
const RECONNECT_MAX_MS = 30_000;
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

export class SignalingClient {
  private ws: WebSocket | null = null;
  private url = '';
  private handlers: SignalingHandlers = {};
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private sessionTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectDelay = RECONNECT_INITIAL_MS;
  private manuallyClosed = false;
  private lastPingTs = 0;

  setHandlers(h: SignalingHandlers) {
    this.handlers = h;
  }

  get isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  // 连接信令服务器
  connect(deviceId?: string): void {
    this.manuallyClosed = false;
    this.url = this.buildUrl(deviceId);
    this.open();
  }

  private buildUrl(deviceId?: string): string {
    const base = getBaseUrl();
    const loc = typeof window !== 'undefined' ? window.location : null;
    let wsBase: string;
    if (base) {
      // 自定义服务器地址：http(s):// -> ws(s)://
      wsBase = base.replace(/^http/, 'ws');
    } else if (loc) {
      const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
      wsBase = `${proto}//${loc.host}`;
    } else {
      wsBase = 'ws://localhost:8080';
    }
    const params = new URLSearchParams();
    if (authStore.token) params.set('token', authStore.token);
    if (deviceId) params.set('deviceId', deviceId);
    return `${wsBase}/ws/signaling?${params.toString()}`;
  }

  private open() {
    try {
      this.ws = new WebSocket(this.url);
    } catch (e) {
      this.handlers.onError?.(e);
      this.scheduleReconnect();
      return;
    }
    this.ws.onopen = () => {
      this.reconnectDelay = RECONNECT_INITIAL_MS;
      this.startHeartbeat();
      this.resetSessionTimeout();
      this.handlers.onOpen?.();
    };
    this.ws.onmessage = (ev) => this.handleMessage(ev);
    this.ws.onclose = (ev) => {
      this.stopHeartbeat();
      this.stopSessionTimeout();
      this.handlers.onClose?.(ev);
      if (!this.manuallyClosed) this.scheduleReconnect();
    };
    this.ws.onerror = (e) => {
      this.handlers.onError?.(e);
    };
  }

  private handleMessage(ev: MessageEvent) {
    let msg: SignalingEnvelope;
    try {
      msg = JSON.parse(ev.data as string) as SignalingEnvelope;
    } catch {
      return;
    }
    this.resetSessionTimeout();
    this.handlers.onMessage?.(msg);
    switch (msg.type) {
      case 'connect_ack':
        this.handlers.onConnectAck?.(!!msg.ok, msg.sessionId, msg.requireConfirm, msg.code);
        break;
      case 'sdp_answer':
        if (msg.sdp) this.handlers.onSdpAnswer?.(msg.sdp as RTCSessionDescriptionInit);
        break;
      case 'ice_candidate':
        if (msg.candidate) this.handlers.onIceCandidate?.(msg.candidate);
        break;
      case 'ice_complete':
        this.handlers.onIceComplete?.();
        break;
      case 'bye':
        this.handlers.onBye?.(msg.reason);
        break;
      case 'pong':
        if (this.lastPingTs > 0) {
          const rtt = Date.now() - this.lastPingTs;
          this.handlers.onPong?.(msg.rtt ?? rtt);
        }
        break;
      case 'error':
        this.handlers.onErrorMsg?.(msg.code ?? 'ERR_INTERNAL_ERROR', msg.message);
        break;
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: 'ping', ts: Date.now(), clientTs: Date.now() });
      this.lastPingTs = Date.now();
    }, HEARTBEAT_INTERVAL_MS);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  // 会话超时：30 分钟无消息自动休眠
  private resetSessionTimeout() {
    this.stopSessionTimeout();
    this.sessionTimeoutTimer = setTimeout(() => {
      this.handlers.onBye?.('session_timeout');
      this.close();
    }, SESSION_TIMEOUT_MS);
  }

  private stopSessionTimeout() {
    if (this.sessionTimeoutTimer) {
      clearTimeout(this.sessionTimeoutTimer);
      this.sessionTimeoutTimer = null;
    }
  }

  // 指数退避重连：1s→2s→4s→…→max 30s
  private scheduleReconnect() {
    if (this.manuallyClosed) return;
    const delay = this.reconnectDelay;
    this.reconnectDelay = Math.min(this.reconnectDelay * RECONNECT_MULTIPLIER, RECONNECT_MAX_MS);
    this.reconnectTimer = setTimeout(() => this.open(), delay);
  }

  // 发送消息
  send(msg: Partial<SignalingEnvelope>): boolean {
    if (!this.isConnected || !this.ws) return false;
    const full: SignalingEnvelope = {
      type: msg.type ?? 'ping',
      ts: msg.ts ?? Date.now(),
      ...msg,
    };
    try {
      this.ws.send(JSON.stringify(full));
      return true;
    } catch {
      return false;
    }
  }

  // 主动关闭，不再重连
  close() {
    this.manuallyClosed = true;
    this.stopHeartbeat();
    this.stopSessionTimeout();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // ignore
      }
      this.ws = null;
    }
  }

  // 发送 bye
  sendBye(reason?: string) {
    this.send({ type: 'bye', reason });
  }
}

export const signalingClient = new SignalingClient();
