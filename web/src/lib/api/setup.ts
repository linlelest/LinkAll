// 首次启动初始化 API：检查初始化状态 + 创建首个超级管理员。
// 公开路由，无需鉴权。
import { request } from './client';

export interface SetupStatus {
  needsSetup: boolean;
}

export interface SetupInitResult {
  created: boolean;
  userId: number;
  username: string;
  autoLogin: boolean;
  token?: string;
  expiresIn?: number;
  message?: string;
}

// GET /api/setup/status — 检查是否需要首次初始化
export function getSetupStatus(): Promise<SetupStatus> {
  return request<SetupStatus>('/api/setup/status', {
    method: 'GET',
    noAuth: true,
  });
}

// POST /api/setup/init — 创建首个超级管理员，成功后返回 JWT 自动登录
export function initSetup(username: string, password: string): Promise<SetupInitResult> {
  return request<SetupInitResult>('/api/setup/init', {
    method: 'POST',
    noAuth: true,
    body: { username, password },
  });
}
