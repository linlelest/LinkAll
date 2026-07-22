// 日期 / 数字 / 文件大小格式化工具，跟随 Locale。
import { getLocale } from '$lib/i18n';

// 格式化 Unix 时间戳（秒）为本地日期时间字符串
export function formatDateTime(ts: number): string {
  if (!ts || ts <= 0) return '-';
  const d = new Date(ts * 1000);
  if (isNaN(d.getTime())) return '-';
  const locale = getLocale().replace('-', '_');
  const y = d.getFullYear();
  const mo = pad(d.getMonth() + 1);
  const da = pad(d.getDate());
  const h = pad(d.getHours());
  const mi = pad(d.getMinutes());
  if (locale.startsWith('en')) {
    return `${y}-${mo}-${da} ${h}:${mi}`;
  }
  return `${y}-${mo}-${da} ${h}:${mi}`;
}

// 相对时间（如 "3 分钟前"）
export function formatRelative(ts: number): string {
  if (!ts || ts <= 0) return '-';
  const now = Math.floor(Date.now() / 1000);
  const diff = now - ts;
  if (diff < 0) return formatDateTime(ts);
  const locale = getLocale();
  const isZh = locale.startsWith('zh');
  if (diff < 60) return isZh ? '刚刚' : 'just now';
  if (diff < 3600) {
    const m = Math.floor(diff / 60);
    return isZh ? `${m} 分钟前` : `${m}m ago`;
  }
  if (diff < 86400) {
    const h = Math.floor(diff / 3600);
    return isZh ? `${h} 小时前` : `${h}h ago`;
  }
  const d = Math.floor(diff / 86400);
  return isZh ? `${d} 天前` : `${d}d ago`;
}

// 格式化时长（秒 -> 1d 2h 3m 4s）
export function formatDuration(seconds: number): string {
  if (!seconds || seconds < 0) return '00:00';
  const s = Math.floor(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) {
    return `${pad(h)}:${pad(m)}:${pad(sec)}`;
  }
  return `${pad(m)}:${pad(sec)}`;
}

// 格式化字节大小
export function formatBytes(bytes: number): string {
  if (!bytes || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let v = bytes;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v < 10 && i > 0 ? 1 : 0)} ${units[i]}`;
}

// 格式化比特率（bps -> Kbps/Mbps/Gbps）
export function formatBitrate(bps: number): string {
  if (!bps || bps < 0) return '0 bps';
  if (bps < 1000) return `${bps} bps`;
  if (bps < 1_000_000) return `${(bps / 1000).toFixed(1)} Kbps`;
  if (bps < 1_000_000_000) return `${(bps / 1_000_000).toFixed(1)} Mbps`;
  return `${(bps / 1_000_000_000).toFixed(2)} Gbps`;
}

// 格式化百分比（0.0~1.0 -> "12.3%"）
export function formatPercent(ratio: number): string {
  if (!ratio || ratio < 0) return '0%';
  return `${(ratio * 100).toFixed(ratio < 0.1 ? 1 : 0)}%`;
}

// 对数刻度转换：value 在 [min,max] 范围内，转为 0~1 的进度
export function logScaleToProgress(value: number, min: number, max: number): number {
  if (value <= min) return 0;
  if (value >= max) return 1;
  return Math.log(value / min) / Math.log(max / min);
}

// 进度（0~1）转对数刻度实际值
export function progressToLogScale(progress: number, min: number, max: number): number {
  const p = Math.max(0, Math.min(1, progress));
  return Math.round(min * Math.pow(max / min, p));
}

// 防抖
export function debounce<T extends (...args: any[]) => void>(fn: T, wait: number): T {
  let timer: ReturnType<typeof setTimeout> | null = null;
  return ((...args: any[]) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  }) as T;
}

// 节流
export function throttle<T extends (...args: any[]) => void>(fn: T, wait: number): T {
  let last = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  return ((...args: any[]) => {
    const now = Date.now();
    const remaining = wait - (now - last);
    if (remaining <= 0) {
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      last = now;
      fn(...args);
    } else if (!timer) {
      timer = setTimeout(() => {
        last = Date.now();
        timer = null;
        fn(...args);
      }, remaining);
    }
  }) as T;
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

// 生成 UUIDv4
export function uuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
