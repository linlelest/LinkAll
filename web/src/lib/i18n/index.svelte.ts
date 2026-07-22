// i18n 扁平 JSON 语言包加载器，支持热切换，默认跟随系统语言。
// 遵循设计：字符串全部外置，杜绝硬编码；日期/数字格式化跟随 Locale。
// Locale 类型与 detectLocale 定义在 locale.svelte.ts 中，此处再导出以保持 $lib/i18n 入口不变。
import { localeStore } from '$lib/stores/locale';
import type { Locale } from '$lib/stores/locale';
import zhCN from './zh-CN.json';
import enUS from './en-US.json';

// 再导出，使现有 `import { ... } from '$lib/i18n'` 调用保持兼容
export type { Locale };
export { detectLocale, SUPPORTED_LOCALES } from '$lib/stores/locale';

// 语言包注册表
const LOCALES: Record<string, Record<string, string>> = {
  'zh-CN': zhCN as Record<string, string>,
  'en-US': enUS as Record<string, string>,
};

// 当前激活语言包（响应式）
let bundles = $state<Record<string, string>>(LOCALES[localeStore.locale] ?? zhCN);

// 同步 locale 切换
$effect.root(() => {
  $effect(() => {
    const l = localeStore.locale;
    bundles = LOCALES[l] ?? zhCN;
    if (typeof document !== 'undefined') {
      document.documentElement.lang = l;
    }
  });
});

// 翻译函数：t('key', { name: value }) 支持插值
export function t(key: string, params?: Record<string, string | number>): string {
  let s = bundles[key] ?? key;
  if (params) {
    for (const k of Object.keys(params)) {
      s = s.replace(new RegExp(`\\{${k}\\}`, 'g'), String(params[k]));
    }
  }
  return s;
}

// 切换语言（热更新）
export function setLocale(l: Locale) {
  localeStore.setLocale(l);
}

// 获取当前语言
export function getLocale(): Locale {
  return localeStore.locale;
}
