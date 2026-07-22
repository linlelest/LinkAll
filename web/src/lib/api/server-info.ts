// 服务器信息 API。
import { request } from './client';

export interface ServerInfo {
  cpu: { percent: number; cores: number };
  memory: {
    alloc: number;
    totalAlloc: number;
    sys: number;
    heapInuse: number;
    stackInuse: number;
    numGC: number;
    percent: number;
  };
  bandwidth: { sent: number; recv: number };
  onlineDevices: number;
  activeSessions: number;
  signalingLatencyMs: number;
  uptime: number;
  goVersion: string;
  numGoroutines: number;
  hostname: string;
}

export function getServerInfo(): Promise<ServerInfo> {
  return request<ServerInfo>('/api/admin/server-info');
}

export function healthCheck(): Promise<{ status: string; uptime: number; version: string }> {
  return request<{ status: string; uptime: number; version: string }>('/api/health', {
    noAuth: true,
  });
}
