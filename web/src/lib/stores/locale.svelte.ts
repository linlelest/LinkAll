// 语言状态：Svelte 5 runes 模式，持久化到 localStorage，默认跟随系统语言。
// 本模块自包含 Locale 类型与 detectLocale 函数，避免与 i18n/index 形成循环依赖。
import { browserStorage } from '$lib/utils/storage';

// 支持的语言列表
export type Locale = 'zh-CN' | 'en-US';

export const SUPPORTED_LOCALES: Locale[] = ['zh-CN', 'en-US'];

// 从 navigator.language 检测系统语言
export function detectLocale(): Locale {
  if (typeof navigator === 'undefined') return 'zh-CN';
  const lang = navigator.language || 'zh-CN';
  if (lang.startsWith('zh')) return 'zh-CN';
  if (lang.startsWith('en')) return 'en-US';
  return 'zh-CN';
}

const LOCALE_KEY = 'linkall.locale';

class LocaleStore {
  locale = $state<Locale>('zh-CN');
  ready = $state<boolean>(false);

  constructor() {
    const saved = browserStorage.get(LOCALE_KEY, '');
    if (saved === 'zh-CN' || saved === 'en-US') {
      this.locale = saved;
    } else {
      this.locale = detectLocale();
    }
    this.ready = true;
  }

  setLocale(l: Locale) {
    this.locale = l;
    browserStorage.set(LOCALE_KEY, l);
    if (typeof document !== 'undefined') {
      document.documentElement.lang = l;
    }
  }
}

export const localeStore = new LocaleStore();
