// 设备管理 API：列表 / 踢出会话。
import { requestWithMeta, type ApiMeta } from './client';
import type { DeviceItem } from '$lib/stores/devices';

export function listDevices(
  limit = 50,
  offset = 0,
  onlineOnly = false,
): Promise<{ data: DeviceItem[]; meta?: ApiMeta }> {
  return requestWithMeta<DeviceItem[]>('/api/admin/devices', {
    query: { limit, offset, online: onlineOnly ? 'true' : undefined },
  });
}

export function kickDevice(deviceId: string): Promise<{ kicked: string }> {
  return request<{ kicked: string }>(`/api/admin/devices/${encodeURIComponent(deviceId)}/kick`, {
    method: 'POST',
  });
}
