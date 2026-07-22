// 连接 / 会话状态：Svelte 5 runes 模式。
import type { ConnectionMode } from '$lib/webrtc/control';

export type ConnPhase =
  | 'idle'
  | 'connecting'
  | 'waiting_confirm'
  | 'establishing'
  | 'connected'
  | 'disconnected'
  | 'failed';

export interface ConnStats {
  rtt: number; // 毫秒
  packetLoss: number; // 0.0~1.0
  bitrate: number; // bps
  fps: number;
  codec: string;
  duration: number; // 秒
}

class ConnectionStore {
  phase = $state<ConnPhase>('idle');
  deviceId = $state<string>('');
  deviceCode = $state<string>('');
  mode = $state<ConnectionMode>('anonymous');
  sessionId = $state<string>('');
  errorMsg = $state<string>('');
  stats = $state<ConnStats>({
    rtt: 0,
    packetLoss: 0,
    bitrate: 0,
    fps: 0,
    codec: '',
    duration: 0,
  });

  // 控制设置（实时同步给被控端）
  scale = $state<number>(100); // 10~300
  maxBitrate = $state<number>(4_000_000); // 512K~200M
  fps = $state<number>(60); // 离散步进
  privacyScreen = $state<boolean>(false);
  codec = $state<'H264' | 'VP8' | 'VP9' | 'AV1'>('H264');

  // 触屏辅助
  keyboardVisible = $state<boolean>(false);
  mouseOpacity = $state<number>(0.6);
  wheelSensitivity = $state<number>(1);

  get isConnected(): boolean {
    return this.phase === 'connected';
  }

  get isConnecting(): boolean {
    return (
      this.phase === 'connecting' ||
      this.phase === 'waiting_confirm' ||
      this.phase === 'establishing'
    );
  }

  reset() {
    this.phase = 'idle';
    this.deviceId = '';
    this.deviceCode = '';
    this.mode = 'anonymous';
    this.sessionId = '';
    this.errorMsg = '';
    this.stats = {
      rtt: 0,
      packetLoss: 0,
      bitrate: 0,
      fps: 0,
      codec: '',
      duration: 0,
    };
    this.privacyScreen = false;
    this.keyboardVisible = false;
  }

  setStats(s: Partial<ConnStats>) {
    this.stats = { ...this.stats, ...s };
  }
}

export const connectionStore = new ConnectionStore();
