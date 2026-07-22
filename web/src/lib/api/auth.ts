// 认证 API：登录 / 注册 / 刷新 / 当前用户 / 修改密码。
import { request } from './client';
import type { UserInfo } from '$lib/stores/auth';

export interface LoginResult {
  token: string;
  expiresIn: number;
  user: UserInfo;
}

export function login(username: string, password: string): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/login', {
    method: 'POST',
    noAuth: true,
    body: { username, password },
  });
}

export function register(
  username: string,
  password: string,
  inviteCode: string,
): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/register', {
    method: 'POST',
    noAuth: true,
    body: { username, password, inviteCode },
  });
}

export function refresh(): Promise<{ token: string; expiresIn: number }> {
  return request<{ token: string; expiresIn: number }>('/api/auth/refresh', {
    method: 'POST',
  });
}

export function me(): Promise<UserInfo> {
  return request<UserInfo>('/api/auth/me');
}

export function changePassword(oldPassword: string, newPassword: string): Promise<{ updated: boolean }> {
  return request<{ updated: boolean }>('/api/auth/change-password', {
    method: 'POST',
    body: { oldPassword, newPassword },
  });
}
