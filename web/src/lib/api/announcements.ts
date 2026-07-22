// 公告 API：列表 / 标记已读 / 创建 / 更新 / 删除（管理员写）。
import { requestWithMeta, request, type ApiMeta } from './client';

export interface Announcement {
  id: number;
  title: string;
  contentMd: string;
  pinned: boolean;
  platform: string;
  versionFilter: string;
  createdAt: number;
  updatedAt: number;
  signature?: string;
  authorId?: number;
  status: string;
}

export function listAnnouncements(
  limit = 20,
  offset = 0,
  platform?: string,
): Promise<{ data: Announcement[]; meta?: ApiMeta }> {
  return requestWithMeta<Announcement[]>('/api/announcements', {
    query: { limit, offset, platform },
  });
}

export function markRead(id: number): Promise<{ read: number }> {
  return request<{ read: number }>(`/api/announcements/${id}/read`, { method: 'POST' });
}

export function createAnnouncement(input: {
  title: string;
  contentMd: string;
  pinned?: boolean;
  platform?: string;
  versionFilter?: string;
}): Promise<Announcement> {
  return request<Announcement>('/api/admin/announcements', { method: 'POST', body: input });
}

export function updateAnnouncement(
  id: number,
  input: {
    title: string;
    contentMd: string;
    pinned?: boolean;
    platform?: string;
    versionFilter?: string;
  },
): Promise<{ updated: number }> {
  return request<{ updated: number }>(`/api/admin/announcements/${id}`, {
    method: 'PUT',
    body: input,
  });
}

export function deleteAnnouncement(id: number): Promise<{ deleted: number }> {
  return request<{ deleted: number }>(`/api/admin/announcements/${id}`, { method: 'DELETE' });
}
