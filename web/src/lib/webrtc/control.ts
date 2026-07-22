// 控制指令发送器：键盘 / 鼠标 / 滚轮 / 设置同步 / 防窥屏 / 心跳。
// 协议见 shared/protocol.json：所有指令包裹在 Envelope {type, ts, seq, sessionId, payload} 中。
import { peerConnection } from './peer';
import { uuid } from '$lib/utils/format';

export type ConnectionMode = 'anonymous' | 'same_account' | 'device_code';

export type MessageType =
  | 'keyboard'
  | 'mouse'
  | 'wheel'
  | 'file_meta'
  | 'file_chunk'
  | 'file_ack'
  | 'file_complete'
  | 'settings_sync'
  | 'heartbeat'
  | 'heartbeat_ack'
  | 'status'
  | 'error'
  | 'auth'
  | 'auth_ack'
  | 'screen_config'
  | 'privacy_screen'
  | 'clipboard';

export type KeyCode = string;

export interface Envelope {
  type: MessageType;
  ts: number;
  seq: number;
  sessionId?: string;
  payload?: unknown;
}

// 全局序列号
let seqCounter = 0;
function nextSeq(): number {
  seqCounter = (seqCounter + 1) % 0xffff_ffff;
  return seqCounter;
}

let currentSessionId = '';

export function setSessionId(sid: string) {
  currentSessionId = sid;
}

// 发送封装：通过 DataChannel 发送 JSON 信封
function send(type: MessageType, payload?: unknown): boolean {
  const env: Envelope = {
    type,
    ts: Date.now(),
    seq: nextSeq(),
    sessionId: currentSessionId || undefined,
    payload,
  };
  return peerConnection.sendByDataChannel(JSON.stringify(env));
}

// === 键盘 ===
export function sendKeyboard(
  key: KeyCode,
  action: 'down' | 'up' | 'press',
  modifiers: KeyCode[] = [],
): boolean {
  return send('keyboard', { key, action, modifiers });
}

// === 鼠标 ===
export function sendMouseMove(dx: number, dy: number): boolean {
  return send('mouse', { action: 'move', dx, dy });
}

export function sendMouseAbsolute(x: number, y: number): boolean {
  return send('mouse', { action: 'move', x, y });
}

export function sendMouseDown(button: 'left' | 'right' | 'middle' | 'back' | 'forward' = 'left'): boolean {
  return send('mouse', { action: 'down', button });
}

export function sendMouseUp(button: 'left' | 'right' | 'middle' | 'back' | 'forward' = 'left'): boolean {
  return send('mouse', { action: 'up', button });
}

export function sendMouseClick(button: 'left' | 'right' | 'middle' = 'left'): boolean {
  return send('mouse', { action: 'click', button });
}

export function sendMouseDoubleClick(button: 'left' | 'right' | 'middle' = 'left'): boolean {
  return send('mouse', { action: 'double_click', button });
}

// === 滚轮 ===
export function sendWheel(deltaX: number, deltaY: number): boolean {
  return send('wheel', { deltaX, deltaY });
}

// === 设置同步 ===
export interface ScreenSettings {
  scale?: number;
  fps?: number;
  maxBitrate?: number;
}
export interface CodecSettings {
  video?: 'H264' | 'VP8' | 'VP9' | 'AV1';
  audio?: 'Opus' | 'AAC';
}
export interface ControlSettings {
  privacyScreen?: boolean;
  clipboardSync?: boolean;
}

export function sendScreenSettings(s: ScreenSettings): boolean {
  return send('settings_sync', { category: 'screen', ...s });
}

export function sendCodecSettings(s: CodecSettings): boolean {
  return send('settings_sync', { category: 'codec', ...s });
}

export function sendControlSettings(s: ControlSettings): boolean {
  return send('settings_sync', { category: 'control', ...s });
}

// === 防窥屏 ===
export function sendPrivacyScreen(enabled: boolean): boolean {
  return send('privacy_screen', { enabled });
}

// === 心跳 ===
export function sendHeartbeat(): boolean {
  return send('heartbeat', { clientTs: Date.now() });
}

// === 剪贴板 ===
export function sendClipboard(text: string): boolean {
  return send('clipboard', { text });
}

// === 鉴权握手（DataChannel 建立后发送）===
export function sendAuth(
  deviceId: string,
  mode: ConnectionMode,
  deviceCode?: string,
  token?: string,
): boolean {
  return send('auth', { deviceId, mode, deviceCode, token, clientTs: Date.now() });
}

// === 文件传输 ===
export interface FileMeta {
  transferId: string;
  name: string;
  size: number;
  hash: string;
  direction: 'upload' | 'download';
  remotePath?: string;
  chunkSize?: number;
}

export function sendFileMeta(meta: FileMeta): boolean {
  return send('file_meta', meta);
}

export function sendFileChunk(
  transferId: string,
  chunkId: number,
  offset: number,
  data: string,
): boolean {
  return send('file_chunk', { transferId, chunkId, offset, data });
}

export function sendFileAck(transferId: string, chunkId: number, ok: boolean): boolean {
  return send('file_ack', { transferId, chunkId, ok });
}

export function sendFileComplete(transferId: string, ok: boolean, hash?: string): boolean {
  return send('file_complete', { transferId, ok, hash });
}

export function newTransferId(): string {
  return uuid();
}

// SHA-256 哈希（用于文件校验）
export async function sha256(buffer: ArrayBuffer): Promise<string> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  // 降级：返回空，由调用方处理
  return '';
}
