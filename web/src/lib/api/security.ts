// 全局安全设置 API：获取 / 更新 / .env 预览。
import { request, rawFetch } from './client';

export interface SecuritySettings {
  forceHttps: boolean;
  allowAnonymous: boolean;
  allowDeviceCode: boolean;
  allowRemoteControl: boolean;
  anonymousWhitelist: string[];
  connectionPasswordSet: boolean;
  maxConcurrentSessions: number;
  dataRetentionDays: number;
  updatedAt: number;
}

export interface UpdateSecurityInput {
  forceHttps?: boolean;
  allowAnonymous?: boolean;
  allowDeviceCode?: boolean;
  allowRemoteControl?: boolean;
  anonymousWhitelist?: string[];
  connectionPassword?: string;
  maxConcurrentSessions?: number;
  dataRetentionDays?: number;
}

export function getSecurity(): Promise<SecuritySettings> {
  return request<SecuritySettings>('/api/admin/security');
}

export function updateSecurity(input: UpdateSecurityInput): Promise<{ updated: boolean }> {
  return request<{ updated: boolean }>('/api/admin/security', {
    method: 'PUT',
    body: input,
  });
}

export interface EnvPreview {
  serverPort: string;
  env: string;
  officialServer: string;
  dbPath: string;
  jwtScheme: string;
  jwtSecret: string;
  jwtExpiry: string;
  stunServers: string[];
  turnServers: string[];
  turnUsername: string;
  forceHttps: boolean;
  maxConcurrentSessions: number;
  dataRetentionDays: number;
}

export function envPreview(): Promise<EnvPreview> {
  return request<EnvPreview>('/api/admin/security/env-preview');
}

export async function envReloadPreview(path = '.env'): Promise<{ path: string; content: string; preview: boolean }> {
  const res = await rawFetch(`/api/admin/server-info/env-reload-preview?path=${encodeURIComponent(path)}`, {
    method: 'POST',
  });
  const text = await res.text();
  try {
    const body = JSON.parse(text);
    if (body.code === 'ERR_OK') return body.data;
    throw new Error(body.message || 'env preview failed');
  } catch {
    throw new Error('env preview failed');
  }
}
