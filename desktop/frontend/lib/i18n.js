// 国际化运行时
// 支持 zh-CN / en-US 双语切换，运行时热加载

import zhCN from '../i18n/zh-CN.json' with { type: 'json' };
import enUS from '../i18n/en-US.json' with { type: 'json' };

const LOCALES = {
    'zh-CN': zhCN,
    'en-US': enUS,
};

const STORAGE_KEY = 'linkall.locale';

let currentLocale = 'zh-CN';
const listeners = new Set();

/** 初始化语言（从本地存储读取，默认 zh-CN） */
export function initI18n() {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved && LOCALES[saved]) {
            currentLocale = saved;
        }
    } catch { /* localStorage 不可用时忽略 */ }
    applyLocaleToDom();
}

/** 获取当前语言 */
export function getLocale() {
    return currentLocale;
}

/** 设置语言（运行时切换） */
export function setLocale(locale) {
    if (!LOCALES[locale] || locale === currentLocale) return;
    currentLocale = locale;
    try { localStorage.setItem(STORAGE_KEY, locale); } catch { /* ignore */ }
    applyLocaleToDom();
    listeners.forEach((fn) => fn(locale));
}

/** 监听语言变化 */
export function onLocaleChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
}

/** 按点路径取值，例如 t('controlled.start') */
export function t(key, vars = {}) {
    const dict = LOCALES[currentLocale] || {};
    const parts = key.split('.');
    let val = dict;
    for (const p of parts) {
        if (val && typeof val === 'object' && p in val) {
            val = val[p];
        } else {
            return key; // 找不到时返回 key 本身
        }
    }
    if (typeof val === 'string') {
        // 简单模板替换：{name}
        return val.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? '');
    }
    return key;
}

/** 将 data-i18n / data-i18n-title / data-i18n-ph 属性应用到 DOM */
function applyLocaleToDom() {
    document.documentElement.lang = currentLocale;
    document.querySelectorAll('[data-i18n]').forEach((el) => {
        const key = el.getAttribute('data-i18n');
        const text = t(key);
        if (text) el.textContent = text;
    });
    document.querySelectorAll('[data-i18n-title]').forEach((el) => {
        const key = el.getAttribute('data-i18n-title');
        const text = t(key);
        if (text) el.title = text;
    });
    document.querySelectorAll('[data-i18n-ph]').forEach((el) => {
        const key = el.getAttribute('data-i18n-ph');
        const text = t(key);
        if (text) el.placeholder = text;
    });
}
