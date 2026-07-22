// 认证状态：token / 用户信息，Svelte 5 runes 模式。
// 持久化到 localStorage，启动时自动恢复。
import { browserStorage } from '$lib/utils/storage';

export interface UserInfo {
  id: number;
  username: string;
  role: string;
  status: string;
  banned: boolean;
  deviceCount: number;
  createdAt: number;
  lastLoginIp: string;
}

interface AuthState {
  token: string;
  expiresIn: number;
  user: UserInfo | null;
  ready: boolean;
}

const TOKEN_KEY = 'linkall.token';
const USER_KEY = 'linkall.user';

class AuthStore {
  token = $state<string>('');
  expiresIn = $state<number>(0);
  user = $state<UserInfo | null>(null);
  ready = $state<boolean>(false);

  constructor() {
    // 启动时从 localStorage 恢复
    this.token = browserStorage.get(TOKEN_KEY, '');
    const userJson = browserStorage.get(USER_KEY, '');
    if (userJson) {
      try {
        this.user = JSON.parse(userJson);
      } catch {
        this.user = null;
      }
    }
    this.ready = true;
  }

  get isLoggedIn(): boolean {
    return !!this.token && !!this.user;
  }

  get isAdmin(): boolean {
    return this.user?.role === 'admin' || this.user?.role === 'superadmin';
  }

  get isSuperadmin(): boolean {
    return this.user?.role === 'superadmin';
  }

  setSession(token: string, expiresIn: number, user: UserInfo | null) {
    this.token = token;
    this.expiresIn = expiresIn;
    this.user = user;
    browserStorage.set(TOKEN_KEY, token);
    if (user) {
      browserStorage.set(USER_KEY, JSON.stringify(user));
    } else {
      browserStorage.remove(USER_KEY);
    }
  }

  setUser(user: UserInfo | null) {
    this.user = user;
    if (user) {
      browserStorage.set(USER_KEY, JSON.stringify(user));
    } else {
      browserStorage.remove(USER_KEY);
    }
  }

  logout() {
    this.token = '';
    this.expiresIn = 0;
    this.user = null;
    browserStorage.remove(TOKEN_KEY);
    browserStorage.remove(USER_KEY);
  }
}

export const authStore = new AuthStore();
