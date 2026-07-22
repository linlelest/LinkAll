// Toast 全局状态：Svelte 5 runes 模式。
export type ToastType = 'info' | 'success' | 'error' | 'warn';

export interface ToastItem {
  id: number;
  type: ToastType;
  message: string;
}

let counter = 0;

class ToastStore {
  items = $state<ToastItem[]>([]);

  show(message: string, type: ToastType = 'info', ttl = 3000): number {
    const id = ++counter;
    this.items = [...this.items, { id, type, message }];
    if (ttl > 0) {
      setTimeout(() => this.remove(id), ttl);
    }
    return id;
  }

  info(message: string, ttl?: number) {
    return this.show(message, 'info', ttl);
  }
  success(message: string, ttl?: number) {
    return this.show(message, 'success', ttl);
  }
  error(message: string, ttl?: number) {
    return this.show(message, 'error', ttl ?? 5000);
  }
  warn(message: string, ttl?: number) {
    return this.show(message, 'warn', ttl);
  }

  remove(id: number) {
    this.items = this.items.filter((x) => x.id !== id);
  }

  clear() {
    this.items = [];
  }
}

export const toastStore = new ToastStore();

// 便捷全局函数
export const toast = {
  info: (m: string, ttl?: number) => toastStore.info(m, ttl),
  success: (m: string, ttl?: number) => toastStore.success(m, ttl),
  error: (m: string, ttl?: number) => toastStore.error(m, ttl),
  warn: (m: string, ttl?: number) => toastStore.warn(m, ttl),
};
