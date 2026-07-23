// 状态化路由：无 router 库，使用简单状态切换（遵循"绝对轻量化原则"）。
// 路由用 hash 片段同步，便于刷新保持。
import { authStore } from './auth';

export type RouteName =
  | 'setup'
  | 'login'
  | 'dashboard'
  | 'devices'
  | 'control'
  | 'announcements'
  | 'settings'
  | 'ota';

class RouterStore {
  current = $state<RouteName>('dashboard');

  constructor() {
    this.syncFromHash();
    if (typeof window !== 'undefined') {
      window.addEventListener('hashchange', () => this.syncFromHash());
    }
  }

  private syncFromHash() {
    const h = (typeof window !== 'undefined' ? window.location.hash : '').replace(/^#\/?/, '');
    const valid: RouteName[] = [
      'setup',
      'login',
      'dashboard',
      'devices',
      'control',
      'announcements',
      'settings',
      'ota',
    ];
    if (h && valid.includes(h as RouteName)) {
      this.current = h as RouteName;
    } else {
      // 默认路由：未登录 -> login，已登录 -> dashboard
      this.current = authStore.isLoggedIn ? 'dashboard' : 'login';
    }
  }

  go(route: RouteName) {
    this.current = route;
    if (typeof window !== 'undefined') {
      window.location.hash = `/${route}`;
    }
  }

  // 登录后默认跳转
  goAfterLogin() {
    this.go('dashboard');
  }
}

export const routerStore = new RouterStore();
