// 连接编排：将信令、PeerConnection、控制指令串联起来，供控制页各组件共享。
import { signalingClient, type SignalingHandlers } from './signaling';
import { peerConnection, type PeerHandlers } from './peer';
import {
  sendAuth,
  sendScreenSettings,
  sendCodecSettings,
  sendControlSettings,
  sendPrivacyScreen,
  sendHeartbeat,
  setSessionId,
  type ConnectionMode,
} from './control';
import { connectionStore } from '$lib/stores/connection';
import { authStore } from '$lib/stores/auth';
import { toast } from '$lib/stores/toast';
import { t } from '$lib/i18n';

let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let statsTimer: ReturnType<typeof setInterval> | null = null;
let durationStart = 0;

// 启动连接流程
export async function startConnection(deviceId: string, deviceCode: string, mode: ConnectionMode): Promise<void> {
  connectionStore.deviceId = deviceId;
  connectionStore.deviceCode = deviceCode;
  connectionStore.mode = mode;
  connectionStore.phase = 'connecting';
  connectionStore.errorMsg = '';

  // 1. 建立信令通道
  signalingClient.setHandlers(buildSignalingHandlers());
  signalingClient.connect(deviceId);

  // 2. 创建 PeerConnection（等待信令 onOpen 后发 connect + createOffer）
}

// 信令 -> 控制端处理
function buildSignalingHandlers(): SignalingHandlers {
  return {
    onOpen: () => {
      // 信令通道就绪，发送 connect 请求
      signalingClient.send({
        type: 'connect',
        deviceId: connectionStore.deviceId,
        mode: connectionStore.mode,
        token: connectionStore.mode === 'same_account' ? authStore.token : undefined,
        deviceCode: connectionStore.mode === 'device_code' ? connectionStore.deviceCode : undefined,
      });
    },
    onConnectAck: (ok, sessionId, requireConfirm, code) => {
      if (!ok) {
        connectionStore.phase = 'failed';
        connectionStore.errorMsg = code ? t(`error.${codeToKey(code)}`) : t('control.connection_failed', { reason: '' });
        toast.error(connectionStore.errorMsg);
        return;
      }
      if (sessionId) {
        connectionStore.sessionId = sessionId;
        setSessionId(sessionId);
      }
      if (requireConfirm) {
        // 匿名模式：等待被控端用户手动确认
        connectionStore.phase = 'waiting_confirm';
        return;
      }
      // 通过鉴权，开始建立 WebRTC
      connectionStore.phase = 'establishing';
      void establishWebRTC();
    },
    onSdpAnswer: (sdp) => {
      void peerConnection.setRemoteAnswer(sdp);
    },
    onIceCandidate: (candidate) => {
      void peerConnection.addIceCandidate(candidate);
    },
    onBye: (reason) => {
      if (reason === 'session_timeout') {
        toast.warn(t('error.session_timeout'));
      }
      cleanup();
    },
    onErrorMsg: (code, message) => {
      connectionStore.phase = 'failed';
      connectionStore.errorMsg = message || t(`error.${codeToKey(code)}`);
      toast.error(connectionStore.errorMsg);
    },
    onPong: (rtt) => {
      connectionStore.setStats({ rtt });
    },
    onClose: () => {
      if (connectionStore.phase !== 'idle' && connectionStore.phase !== 'disconnected') {
        // 信令断开，标记断连（信令客户端会自动重连）
        if (connectionStore.phase === 'connected') {
          connectionStore.phase = 'disconnected';
        }
      }
    },
  };
}

// 建立 WebRTC 连接
async function establishWebRTC() {
  peerConnection.setHandlers(buildPeerHandlers());
  peerConnection.create();
  await peerConnection.createOffer();
}

function buildPeerHandlers(): PeerHandlers {
  return {
    onRemoteStream: (stream) => {
      // 由 ControlCanvas 组件接收并渲染
      window.dispatchEvent(new CustomEvent('linkall:remotestream', { detail: stream }));
    },
    onDataChannelOpen: () => {
      connectionStore.phase = 'connected';
      durationStart = Date.now();
      startHeartbeat();
      startStatsTimer();
      // 发送鉴权握手 + 当前设置同步
      sendAuth(connectionStore.deviceId, connectionStore.mode, connectionStore.deviceCode, authStore.token);
      syncAllSettings();
      toast.success(t('control.connected'));
    },
    onDataChannelClose: () => {
      cleanup();
    },
    onConnectionStateChange: (state) => {
      if (state === 'failed' || state === 'closed' || state === 'disconnected') {
        if (state === 'failed') {
          connectionStore.phase = 'failed';
          connectionStore.errorMsg = t('error.webrtc_connection_failed');
        }
      } else if (state === 'connected') {
        connectionStore.phase = 'connected';
      }
    },
  };
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = setInterval(() => {
    // DataChannel 心跳（信令层心跳由 signalingClient 自动维护）
    sendHeartbeat();
  }, 15_000);
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function startStatsTimer() {
  stopStatsTimer();
  statsTimer = setInterval(() => {
    if (durationStart > 0) {
      const duration = Math.floor((Date.now() - durationStart) / 1000);
      connectionStore.setStats({ duration });
    }
    // 从 RTCPeerConnection 读取统计
    readPeerStats();
  }, 1000);
}

function stopStatsTimer() {
  if (statsTimer) {
    clearInterval(statsTimer);
    statsTimer = null;
  }
}

async function readPeerStats() {
  // peerConnection 内部 pc 未导出，这里通过事件桥接简化
  // 完整实现需读取 getStats()，此处维持已有 stats 值
}

// 同步当前所有设置到被控端
export function syncAllSettings() {
  sendScreenSettings({
    scale: connectionStore.scale / 100,
    fps: connectionStore.fps,
    maxBitrate: connectionStore.maxBitrate,
  });
  sendCodecSettings({ video: connectionStore.codec });
  sendControlSettings({ privacyScreen: connectionStore.privacyScreen });
}

// 设置变化时同步
export function syncScale() {
  sendScreenSettings({ scale: connectionStore.scale / 100 });
}

export function syncBitrate() {
  sendScreenSettings({ maxBitrate: connectionStore.maxBitrate });
  void peerConnection.setMaxBitrate(connectionStore.maxBitrate);
}

export function syncFps() {
  sendScreenSettings({ fps: connectionStore.fps });
}

export function syncCodec() {
  sendCodecSettings({ video: connectionStore.codec });
}

export function syncPrivacyScreen() {
  sendPrivacyScreen(connectionStore.privacyScreen);
}

// 断开连接
export function disconnect() {
  signalingClient.sendBye('user_disconnect');
  cleanup();
}

function cleanup() {
  stopHeartbeat();
  stopStatsTimer();
  durationStart = 0;
  signalingClient.close();
  peerConnection.close();
  const wasConnected = connectionStore.phase === 'connected';
  connectionStore.phase = 'disconnected';
  if (!wasConnected && connectionStore.phase === 'disconnected') {
    // 已断开
  }
}

function codeToKey(code: string): string {
  // ERR_AUTH_FAILED -> auth_failed
  return code.replace(/^ERR_/, '').toLowerCase();
}

// 重置 store
export function resetConnection() {
  connectionStore.reset();
}

// 暴露 peer 实例供 ControlCanvas 使用
export { peerConnection };
