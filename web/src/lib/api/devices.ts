// 设备 API：同账号设备发现（所有用户）+ 管理员设备管理。
import { requestWithMeta, request, type ApiMeta } from './client';
import type { DeviceItem } from '$lib/stores/devices';

// 同账号设备发现：返回当前用户名下的设备（按 owner_user_id 隔离，超管也只能看到自己的设备）
export async function discoverDevices(
  onlineOnly = false,
): Promise<{ data: DeviceItem[]; total: number }> {
  try {
    const res = await request<{ devices: DeviceItem[]; total: number }>('/api/devices/discover', {
      query: { online: onlineOnly ? 'true' : 'false' },
    });
    return { data: res.devices || [], total: res.total ?? 0 };
  } catch {
    // 无设备时后端返回 404，此处视为空列表
    return { data: [], total: 0 };
  }
}

// 管理员：列出所有设备（仅管理员）
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
