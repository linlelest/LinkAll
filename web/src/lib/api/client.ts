// HTTP fetch 封装：统一响应处理、JWT 注入、错误码映射、自动刷新令牌。
// 服务端统一响应：{ code, message, data, meta? }
import { authStore } from '$lib/stores/auth';
import { browserStorage } from '$lib/utils/storage';

export interface ApiMeta {
  total: number;
  limit: number;
  offset: number;
}

export interface ApiResponse<T = unknown> {
  code: string;
  message: string;
  data: T;
  meta?: ApiMeta;
}

export class ApiError extends Error {
  code: string;
  httpStatus: number;
  constructor(code: string, message: string, httpStatus = 0) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

// 自定义服务器地址（高级设置覆盖），优先级：localStorage > 默认相对路径
export function getBaseUrl(): string {
  const custom = browserStorage.get('linkall.serverAddress', '');
  if (custom) {
    // 移除尾部斜杠
    return custom.replace(/\/+$/, '');
  }
  return ''; // 相对路径，走同源 / Vite 代理
}

export function setServerAddress(addr: string) {
  browserStorage.set('linkall.serverAddress', addr);
}

// 刷新锁，避免并发刷新
let refreshing: Promise<boolean> | null = null;

async function tryRefreshToken(): Promise<boolean> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      const res = await fetch(`${getBaseUrl()}/api/auth/refresh`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authStore.token}`,
        },
      });
      if (!res.ok) return false;
      const body: ApiResponse<{ token: string; expiresIn: number }> = await res.json();
      if (body.code !== 'ERR_OK' || !body.data?.token) return false;
      authStore.setSession(body.data.token, body.data.expiresIn, authStore.user);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body?: unknown;
  // 跳过 JWT 注入（如登录/注册）
  noAuth?: boolean;
  // 失败时不抛出，由调用方处理
  silent?: boolean;
  // 响应为非 JSON（如 CSV 文件下载）
  raw?: boolean;
  query?: Record<string, string | number | boolean | undefined>;
}

// 核心请求函数
export async function request<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, noAuth = false, raw = false, query } = opts;
  let url = `${getBaseUrl()}${path}`;
  if (query) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
    }
    const s = qs.toString();
    if (s) url += `?${s}`;
  }

  const headers: Record<string, string> = {};
  if (!raw) headers['Content-Type'] = 'application/json';
  if (!noAuth && authStore.token) {
    headers['Authorization'] = `Bearer ${authStore.token}`;
  }

  const init: RequestInit = { method, headers };
  if (body !== undefined) {
    init.body = body instanceof FormData ? body : JSON.stringify(body);
    if (body instanceof FormData) delete headers['Content-Type'];
  }

  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    throw new ApiError('ERR_NETWORK', (e as Error).message || 'network error', 0);
  }

  // 401 -> 尝试刷新令牌后重试一次
  if (res.status === 401 && !noAuth && authStore.token) {
    const ok = await tryRefreshToken();
    if (ok) {
      headers['Authorization'] = `Bearer ${authStore.token}`;
      res = await fetch(url, { method, headers, body: init.body });
    }
  }

  if (res.status === 401 && !noAuth) {
    authStore.logout();
  }

  if (raw) {
    return (await res.arrayBuffer()) as unknown as T;
  }

  const text = await res.text();
  let payload: ApiResponse<T>;
  try {
    payload = JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new ApiError('ERR_INVALID_PAYLOAD', text || 'invalid response', res.status);
  }

  if (payload.code !== 'ERR_OK') {
    throw new ApiError(payload.code, payload.message || payload.code, res.status);
  }
  return payload.data;
}

// 带 meta 的请求（分页列表）
export async function requestWithMeta<T = unknown>(
  path: string,
  opts: RequestOptions = {},
): Promise<{ data: T; meta?: ApiMeta }> {
  const { method = 'GET', body, noAuth = false, query } = opts;
  let url = `${getBaseUrl()}${path}`;
  if (query) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
    }
    const s = qs.toString();
    if (s) url += `?${s}`;
  }
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (!noAuth && authStore.token) headers['Authorization'] = `Bearer ${authStore.token}`;
  const init: RequestInit = { method, headers };
  if (body !== undefined) init.body = JSON.stringify(body);

  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    throw new ApiError('ERR_NETWORK', (e as Error).message || 'network error', 0);
  }
  if (res.status === 401 && !noAuth && authStore.token) {
    const ok = await tryRefreshToken();
    if (ok) {
      headers['Authorization'] = `Bearer ${authStore.token}`;
      res = await fetch(url, { method, headers, body: init.body });
    }
  }
  if (res.status === 401 && !noAuth) authStore.logout();

  const text = await res.text();
  let payload: ApiResponse<T>;
  try {
    payload = JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new ApiError('ERR_INVALID_PAYLOAD', text || 'invalid response', res.status);
  }
  if (payload.code !== 'ERR_OK') {
    throw new ApiError(payload.code, payload.message || payload.code, res.status);
  }
  return { data: payload.data, meta: payload.meta };
}

// 原始 fetch（用于文件下载等）
export async function rawFetch(path: string, opts: RequestOptions = {}): Promise<Response> {
  const { method = 'GET', body, noAuth = false } = opts;
  const headers: Record<string, string> = {};
  if (!noAuth && authStore.token) headers['Authorization'] = `Bearer ${authStore.token}`;
  const init: RequestInit = { method, headers };
  if (body !== undefined) {
    init.body = body instanceof FormData ? body : JSON.stringify(body);
    if (!(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  }
  return fetch(`${getBaseUrl()}${path}`, init);
}

export { getBaseUrl as baseUrl };
