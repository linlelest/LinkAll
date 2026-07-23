// 设置页逻辑
// 账户（登录/登出）+ 服务器地址 + 视频编码 + 系统（自启/语言/日志/退出）

import { api } from '../lib/api.js';
import { t, getLocale, setLocale, onLocaleChange } from '../lib/i18n.js';
import { toast, toastError, toastSuccess, toastWarning } from '../lib/toast.js';

const $ = (id) => document.getElementById(id);

/** 初始化设置页 */
export function initSettingsView() {
    loadAuthStatus();
    loadConfig();
    loadAutostart();
    initLanguageSelect();
    bindEvents();
}

// ============ 数据加载 ============

async function loadAuthStatus() {
    try {
        const loggedIn = await api.getAuthStatus();
        if (loggedIn) {
            const user = await api.getUserInfo();
            if (user) {
                $('display-username').textContent = user.username || '--';
                $('display-role').textContent = user.role || '--';
            }
            $('account-login')?.classList.add('hidden');
            $('account-info')?.classList.remove('hidden');
        } else {
            $('account-login')?.classList.remove('hidden');
            $('account-info')?.classList.add('hidden');
        }
    } catch (e) {
        console.error('加载认证状态失败:', e);
    }
}

async function loadConfig() {
    try {
        const cfg = await api.getConfig();
        if ($('server-url')) $('server-url').value = cfg.server_url;
        if ($('codec-video')) $('codec-video').value = cfg.codec_video;
        if ($('target-fps')) $('target-fps').value = cfg.target_fps;
        if ($('max-bitrate')) $('max-bitrate').value = Math.round(cfg.max_bitrate / 1_000_000);
        if ($('scale')) $('scale').value = cfg.scale;
        if ($('scale-value')) $('scale-value').textContent = cfg.scale.toFixed(1) + 'x';
    } catch (e) {
        console.error('加载配置失败:', e);
    }
}

async function loadAutostart() {
    try {
        const enabled = await api.getAutostart();
        if ($('autostart-toggle')) $('autostart-toggle').checked = enabled;
    } catch (e) {
        console.error('加载自启状态失败:', e);
    }
}

// ============ 语言切换 ============

function initLanguageSelect() {
    const select = $('language-select');
    if (!select) return;
    select.value = getLocale();
    select.addEventListener('change', (e) => {
        setLocale(e.target.value);
        toastSuccess(e.target.value);
    });
    // 语言切换后重新刷新页面文案（i18n.js 内部已自动处理 DOM）
    onLocaleChange(() => {
        // 重新加载会动态变化的统计/状态显示
        // 大部分文案由 applyLocaleToDom() 自动更新，无需额外动作
    });
}

// ============ 事件绑定 ============

function bindEvents() {
    // 登录
    $('btn-login')?.addEventListener('click', async () => {
        const username = $('username')?.value.trim();
        const password = $('password')?.value;
        if (!username || !password) {
            toastWarning(t('settings.inputRequired'));
            return;
        }
        const btn = $('btn-login');
        btn.disabled = true;
        try {
            await api.login(username, password);
            toastSuccess(t('settings.loginSuccess'));
            await loadAuthStatus();
        } catch (e) {
            toastError(`${t('settings.loginFailed')}: ${e}`);
            console.error('登录失败:', e);
        } finally {
            btn.disabled = false;
        }
    });

    // 登出
    $('btn-logout')?.addEventListener('click', async () => {
        try {
            await api.logout();
            toastSuccess(t('settings.logoutSuccess'));
            await loadAuthStatus();
        } catch (e) {
            toastError(`${t('settings.logoutFailed')}: ${e}`);
        }
    });

    // 保存服务器地址
    $('btn-save-server')?.addEventListener('click', async () => {
        const url = $('server-url').value.trim();
        if (!url) return;
        try {
            await api.updateConfig('server_url', url);
            toastSuccess(t('settings.serverSaved'));
        } catch (e) {
            toastError(`${t('settings.saveFailed')}: ${e}`);
        }
    });

    // 保存视频设置
    $('btn-save-video')?.addEventListener('click', async () => {
        const codec = $('codec-video').value;
        const fps = parseInt($('target-fps').value);
        const bitrate = parseInt($('max-bitrate').value) * 1_000_000;
        const scale = parseFloat($('scale').value);
        try {
            await api.updateConfig('codec_video', codec);
            await api.updateConfig('target_fps', fps);
            await api.updateConfig('max_bitrate', bitrate);
            await api.updateConfig('scale', scale);
            toastSuccess(t('settings.videoSaved'));
        } catch (e) {
            toastError(`${t('settings.saveFailed')}: ${e}`);
        }
    });

    // 缩放滑块实时显示
    $('scale')?.addEventListener('input', (e) => {
        if ($('scale-value')) {
            $('scale-value').textContent = parseFloat(e.target.value).toFixed(1) + 'x';
        }
    });

    // 开机自启开关
    $('autostart-toggle')?.addEventListener('change', async (e) => {
        const enabled = e.target.checked;
        try {
            await api.setAutostart(enabled);
            toastSuccess(enabled ? t('settings.autostartEnabled') : t('settings.autostartDisabled'));
        } catch (err) {
            toastError(`${t('settings.saveFailed')}: ${err}`);
            e.target.checked = !enabled;
        }
    });

    // 导出日志
    $('btn-export-logs')?.addEventListener('click', async () => {
        try {
            const content = await api.exportLogs();
            // 通过浏览器下载（Tauri webview 支持 Blob URL）
            const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `linkall-${new Date().toISOString().slice(0, 10)}.log`;
            a.click();
            URL.revokeObjectURL(url);
            toastSuccess(t('settings.logsExported'));
        } catch (e) {
            toastError(`${t('settings.exportLogsFailed')}: ${e}`);
        }
    });

    // 退出软件
    $('btn-exit-app')?.addEventListener('click', () => {
        if (!confirm(t('settings.exitConfirm'))) return;
        // Tauri 2 退出 API
        if (window.__TAURI__?.core) {
            window.__TAURI__.core.exit(0);
        } else {
            window.close();
        }
    });
}
