// 邀请码 API：生成 / 列表 / 吊销 / 导出 CSV。
import { requestWithMeta, request, rawFetch, type ApiMeta } from './client';

export interface InviteCode {
  id: number;
  code: string;
  createdBy: number;
  createdAt: number;
  expiresAt: number;
  used: boolean;
  revoked: boolean;
  usedBy?: number;
  usedAt?: number;
  note?: string;
}

export function generateInvites(
  count = 1,
  ttlHours = 168,
  note = '',
): Promise<{ codes: string[]; count: number }> {
  return request<{ codes: string[]; count: number }>('/api/admin/invites', {
    method: 'POST',
    body: { count, ttlHours, note },
  });
}

export function listInvites(
  limit = 50,
  offset = 0,
): Promise<{ data: InviteCode[]; meta?: ApiMeta }> {
  return requestWithMeta<InviteCode[]>('/api/admin/invites', { query: { limit, offset } });
}

export function revokeInvite(idOrCode: string | number): Promise<{ revoked: string | number }> {
  return request<{ revoked: string | number }>(
    `/api/admin/invites/${encodeURIComponent(String(idOrCode))}/revoke`,
    { method: 'POST' },
  );
}

export async function exportInvites(): Promise<string> {
  const res = await rawFetch('/api/admin/invites/export', { method: 'GET' });
  return await res.text();
}
