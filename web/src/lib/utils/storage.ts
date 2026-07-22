// localStorage 安全封装（带 JSON 与异常防护）。
const memory: Record<string, string> = {};

export const browserStorage = {
  get(key: string, def: string): string {
    try {
      if (typeof localStorage === 'undefined') return memory[key] ?? def;
      return localStorage.getItem(key) ?? def;
    } catch {
      return memory[key] ?? def;
    }
  },
  set(key: string, value: string): void {
    try {
      if (typeof localStorage === 'undefined') {
        memory[key] = value;
        return;
      }
      localStorage.setItem(key, value);
    } catch {
      memory[key] = value;
    }
  },
  remove(key: string): void {
    try {
      if (typeof localStorage === 'undefined') {
        delete memory[key];
        return;
      }
      localStorage.removeItem(key);
    } catch {
      delete memory[key];
    }
  },
  getJSON<T>(key: string, def: T): T {
    const raw = this.get(key, '');
    if (!raw) return def;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return def;
    }
  },
  setJSON<T>(key: string, value: T): void {
    try {
      this.set(key, JSON.stringify(value));
    } catch {
      // ignore
    }
  },
};
