// 用户管理 API：列表 / 封禁 / 解封 / 重置密码 / 改角色 / 删除。
import { requestWithMeta, request, type ApiMeta } from './client';

export interface UserItem {
  id: number;
  username: string;
  role: string;
  status: string;
  banned: boolean;
  deviceCount: number;
  traffic: number;
  createdAt: number;
  lastLoginIp: string;
}

export function listUsers(
  limit = 50,
  offset = 0,
): Promise<{ data: UserItem[]; meta?: ApiMeta }> {
  return requestWithMeta<UserItem[]>('/api/admin/users', { query: { limit, offset } });
}

export function banUser(id: number): Promise<{ banned: boolean }> {
  return request<{ banned: boolean }>(`/api/admin/users/${id}/ban`, { method: 'POST' });
}

export function unbanUser(id: number): Promise<{ banned: boolean }> {
  return request<{ banned: boolean }>(`/api/admin/users/${id}/unban`, { method: 'POST' });
}

export function resetPassword(id: number, newPassword: string): Promise<{ reset: boolean }> {
  return request<{ reset: boolean }>(`/api/admin/users/${id}/reset-password`, {
    method: 'POST',
    body: { newPassword },
  });
}

export function updateRole(id: number, role: string): Promise<{ role: string }> {
  return request<{ role: string }>(`/api/admin/users/${id}/role`, {
    method: 'POST',
    body: { role },
  });
}

export function deleteUser(id: number): Promise<{ deleted: boolean }> {
  return request<{ deleted: boolean }>(`/api/admin/users/${id}`, { method: 'DELETE' });
}
