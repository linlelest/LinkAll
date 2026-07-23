// 首次启动引导界面
// 三步骤：1. 选择语言 / 2. 自定义服务器地址 / 3. 登录账号
// 完成登录后通过 onComplete 回调通知调用方进入主界面。
// 引导完成状态持久化在 localStorage 的 linkall.desktop.setupDone 标记中：
//   - 未完成 → 完整走完三步
//   - 已完成但未登录 → 直接跳到第 3 步登录
//   - 已完成且已登录 → 不显示引导

import { api } from '../lib/api.js';
import { t, getLocale, setLocale } from '../lib/i18n.js';
import { toastError, toastSuccess, toastWarning } from '../lib/toast.js';

const SETUP_DONE_KEY = 'linkall.desktop.setupDone';

const $ = (id) => document.getElementById(id);

let currentStep = 1;
let onCompleteCb = null;
let eventsBound = false; // 事件只需绑定一次

/**
 * 启动引导界面
 * @param {Object} opts
 * @param {number} opts.startStep 从第几步开始（1/2/3），默认 1
 * @param {Function} opts.onComplete 完成登录后调用
 */
export function startSetup(opts = {}) {
    const { startStep = 1, onComplete } = opts;
    onCompleteCb = onComplete || null;
    const overlay = $('setup-overlay');
    if (!overlay) return;
    overlay.classList.remove('hidden');

    // 初始化各步骤默认值
    initLanguageStep();
    initServerStep();

    // 事件只需绑定一次
    if (!eventsBound) {
        bindStepNavigation();
        bindLogin();
        eventsBound = true;
    }

    // 跳到指定步骤
    goToStep(startStep);
}

/** 标记引导完成（持久化到 localStorage） */
export function markSetupDone() {
    try {
        localStorage.setItem(SETUP_DONE_KEY, '1');
    } catch { /* localStorage 不可用时忽略 */ }
}

/** 引导是否已完成 */
export function isSetupDone() {
    try {
        return localStorage.getItem(SETUP_DONE_KEY) === '1';
    } catch {
        return false;
    }
}

// ============ 步骤切换 ============

function goToStep(step) {
    currentStep = step;
    // 切换页面显隐
    [1, 2, 3].forEach((n) => {
        const page = $(`setup-step-${n}`);
        if (page) page.classList.toggle('hidden', n !== step);
    });
    // 更新步骤指示器
    document.querySelectorAll('.setup-step-dot').forEach((dot) => {
        const n = parseInt(dot.dataset.step, 10);
        dot.classList.toggle('active', n === step);
        dot.classList.toggle('done', n < step);
    });
}

// ============ 步骤 1：选择语言 ============

function initLanguageStep() {
    const select = $('setup-language');
    if (!select) return;
    select.value = getLocale();
    // change 事件只绑一次，重复绑定会触发多次
    if (!select.dataset.bound) {
        select.addEventListener('change', (e) => {
            // 实时切换语言：i18n.js 会自动刷新所有 data-i18n 元素
            setLocale(e.target.value);
        });
        select.dataset.bound = '1';
    }
}

// ============ 步骤 2：服务器地址 ============

function initServerStep() {
    const input = $('setup-server-url');
    if (!input) return;
    // 仅在输入框为空时尝试从后端配置预填，避免覆盖用户已输入内容
    if (input.value) return;
    api.getConfig()
        .then((cfg) => {
            if (cfg && cfg.server_url) input.value = cfg.server_url;
        })
        .catch(() => { /* 加载失败保持空值，走默认相对路径 */ });
}

// ============ 事件绑定 ============

function bindStepNavigation() {
    // 步骤 1 → 2
    $('setup-next-1')?.addEventListener('click', () => goToStep(2));
    // 步骤 2 ← 1
    $('setup-prev-2')?.addEventListener('click', () => goToStep(1));
    // 步骤 2 → 3：先保存服务器地址
    $('setup-next-2')?.addEventListener('click', async () => {
        const url = $('setup-server-url')?.value.trim() || '';
        if (url) {
            try {
                await api.updateConfig('server_url', url);
            } catch (e) {
                toastError(`${t('settings.saveFailed')}: ${e}`);
                return;
            }
        }
        goToStep(3);
    });
    // 步骤 3 ← 2
    $('setup-prev-3')?.addEventListener('click', () => goToStep(2));
}

function bindLogin() {
    const btn = $('setup-login-btn');
    if (!btn) return;
    btn.addEventListener('click', async () => {
        const username = $('setup-username')?.value.trim();
        const password = $('setup-password')?.value;
        if (!username || !password) {
            toastWarning(t('setup.inputRequired'));
            return;
        }
        btn.disabled = true;
        // 暂存原始文案（i18n 可能因语言切换而刷新），完成后恢复
        const originalText = btn.textContent;
        btn.textContent = t('setup.loggingIn');
        try {
            await api.login(username, password);
            toastSuccess(t('setup.loginSuccess'));
            // 标记引导完成
            markSetupDone();
            // 隐藏覆盖层
            $('setup-overlay')?.classList.add('hidden');
            // 通知调用方进入主界面
            if (onCompleteCb) onCompleteCb();
        } catch (e) {
            toastError(`${t('setup.loginFailed')}: ${e}`);
            console.error('引导登录失败:', e);
        } finally {
            btn.disabled = false;
            btn.textContent = originalText;
        }
    });

    // 密码框回车触发登录
    $('setup-password')?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') btn.click();
    });
    // 用户名框回车跳到密码框
    $('setup-username')?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') $('setup-password')?.focus();
    });
}
