// 被控页逻辑
// 服务状态卡片 + 设备身份卡片 + 防窥屏开关 + 连接请求列表 + 连接请求确认弹窗

import { api, subscribeAll } from '../lib/api.js';
import { t } from '../lib/i18n.js';
import { toast, toastError, toastSuccess, toastWarning } from '../lib/toast.js';

const $ = (id) => document.getElementById(id);

let pendingRequest = null; // 当前等待确认的请求

/** 初始化被控页 */
export function initControlledView() {
    bindEvents();
    bindBackendEvents();
    loadDeviceInfo();
    loadServiceStatus();
    loadConnectionRequests();
}

// ============ 数据加载 ============

async function loadDeviceInfo() {
    try {
        const info = await api.getDeviceInfo();
        if ($('device-id')) $('device-id').textContent = info.device_id;
        if ($('device-code')) $('device-code').textContent = info.device_code;
    } catch (e) {
        console.error('加载设备信息失败:', e);
    }
}

async function loadServiceStatus() {
    try {
        const status = await api.getServiceStatus();
        updateServiceUI(status.running, status.paused);
    } catch (e) {
        console.error('加载服务状态失败:', e);
    }
}

async function loadConnectionRequests() {
    try {
        const requests = await api.getConnectionRequests();
        renderConnectionRequests(Array.isArray(requests) ? requests : []);
    } catch (e) {
        // 命令可能未实现或无服务，静默处理
        renderConnectionRequests([]);
    }
}

// ============ UI 更新 ============

function updateServiceUI(running, paused) {
    const dot = $('status-dot');
    const text = $('status-text');
    const btnStart = $('btn-start');
    const btnPause = $('btn-pause');
    const btnStop = $('btn-stop');

    if (!dot || !text) return;
    dot.className = 'status-dot';
    if (running && !paused) {
        dot.classList.add('running');
        text.textContent = t('controlled.running');
        if (btnStart) btnStart.disabled = true;
        if (btnPause) btnPause.disabled = false;
        if (btnStop) btnStop.disabled = false;
    } else if (running && paused) {
        dot.classList.add('paused');
        text.textContent = t('controlled.paused');
        if (btnStart) btnStart.disabled = true;
        if (btnPause) btnPause.disabled = true;
        if (btnStop) btnStop.disabled = false;
    } else {
        text.textContent = t('controlled.stopped');
        if (btnStart) btnStart.disabled = false;
        if (btnPause) btnPause.disabled = true;
        if (btnStop) btnStop.disabled = true;
    }
}

function renderConnectionRequests(requests) {
    const container = $('connection-requests');
    if (!container) return;
    if (!requests || requests.length === 0) {
        container.innerHTML = `<div class="empty-hint">${t('controlled.noRequests')}</div>`;
        return;
    }
    container.innerHTML = '';
    requests.forEach((req) => {
        const item = document.createElement('div');
        item.className = 'request-item';
        item.innerHTML = `
            <div class="req-info">${t('controlled.anonymousRequestFrom', { ip: req.requesterIp || req.ip || '?' })}</div>
            <div class="req-actions">
                <button class="btn btn-danger btn-small" data-action="deny">${t('controlled.deny')}</button>
                <button class="btn btn-secondary btn-small" data-action="allow_once">${t('controlled.allowOnce')}</button>
                <button class="btn btn-primary btn-small" data-action="allow_always">${t('controlled.allowAlways')}</button>
            </div>
        `;
        item.querySelectorAll('button').forEach((btn) => {
            btn.addEventListener('click', () => respondRequest(req.requestId || req.sessionId, btn.dataset.action));
        });
        container.appendChild(item);
    });
}

// ============ 事件绑定 ============

function bindEvents() {
    // 服务控制
    $('btn-start')?.addEventListener('click', async () => {
        setFooter(t('controlled.serviceStarted'));
        try {
            await api.startService();
            toastSuccess(t('controlled.serviceStarted'));
        } catch (e) {
            toastError(`${t('controlled.startFailed')}: ${e}`);
        }
    });

    $('btn-pause')?.addEventListener('click', async () => {
        try {
            await api.pauseService();
            toastWarning(t('controlled.servicePaused'));
        } catch (e) {
            toastError(`${t('controlled.pauseFailed')}: ${e}`);
        }
    });

    $('btn-stop')?.addEventListener('click', async () => {
        try {
            await api.stopService();
            toastWarning(t('controlled.serviceStopped'));
        } catch (e) {
            toastError(`${t('controlled.stopFailed')}: ${e}`);
        }
    });

    // 重置设备编号
    $('btn-reset-device-id')?.addEventListener('click', async () => {
        if (!confirm(t('controlled.resetDeviceIdConfirm'))) return;
        try {
            const newId = await api.resetDeviceId();
            $('device-id').textContent = newId;
            toastSuccess(t('controlled.deviceIdReset'));
        } catch (e) {
            toastError(`${t('controlled.resetFailed')}: ${e}`);
        }
    });

    // 重置设备码
    $('btn-reset-device-code')?.addEventListener('click', async () => {
        if (!confirm(t('controlled.resetDeviceCodeConfirm'))) return;
        try {
            const newCode = await api.resetDeviceCode();
            $('device-code').textContent = newCode;
            toastSuccess(t('controlled.deviceCodeReset'));
        } catch (e) {
            toastError(`${t('controlled.resetFailed')}: ${e}`);
        }
    });

    // 防窥屏开关
    $('privacy-toggle')?.addEventListener('change', async (e) => {
        // 防窥屏通过 settings_sync 同步，本地无独立命令，仅做开关记录
        toast(e.target.checked ? 'Privacy ON' : 'Privacy OFF', 'info');
    });

    // Modal 确认弹窗按钮
    $('confirm-deny')?.addEventListener('click', () => respondModal('deny'));
    $('confirm-once')?.addEventListener('click', () => respondModal('allow_once'));
    $('confirm-always')?.addEventListener('click', () => respondModal('allow_always'));
}

/** 响应列表中的请求 */
async function respondRequest(requestId, action) {
    try {
        await api.respondConnectionRequest(requestId, action);
        toastSuccess(action === 'deny' ? t('controlled.deny') : t('controlled.allowOnce'));
        await loadConnectionRequests();
    } catch (e) {
        toastError(`Failed: ${e}`);
    }
}

/** 响应 Modal 中的请求 */
async function respondModal(action) {
    if (!pendingRequest) return;
    const requestId = pendingRequest.requestId || pendingRequest.sessionId;
    pendingRequest = null;
    $('confirm-modal')?.classList.add('hidden');
    await respondRequest(requestId, action);
}

// ============ 后端事件 ============

function bindBackendEvents() {
    subscribeAll([
        'service-started', 'service-stopped', 'service-paused', 'service-resumed',
        'peer-connected', 'peer-disconnected', 'service-error',
    ], (evt) => {
        switch (evt) {
            case 'service-started':
                setFooter(t('controlled.serviceStarted'));
                toastSuccess(t('controlled.serviceStarted'));
                break;
            case 'service-stopped':
                setFooter(t('controlled.serviceStopped'));
                toastWarning(t('controlled.serviceStopped'));
                break;
            case 'service-paused':
                setFooter(t('controlled.servicePaused'));
                break;
            case 'service-resumed':
                setFooter(t('controlled.serviceResumed'));
                break;
            case 'peer-connected':
                toastSuccess(t('controlled.peerConnected'));
                break;
            case 'peer-disconnected':
                toastWarning(t('controlled.peerDisconnected'));
                break;
        }
        loadServiceStatus();
    });

    // 连接请求推送
    subscribeAll(['connection-request', 'anonymous-connection-request'], (evt, payload) => {
        pendingRequest = payload;
        showConfirmModal(payload);
    });
}

/** 显示连接请求确认弹窗 */
function showConfirmModal(req) {
    const modal = $('confirm-modal');
    const body = $('confirm-modal-body');
    if (!modal) return;
    if (body && req) {
        body.textContent = t('controlled.anonymousRequestFrom', { ip: req.requesterIp || req.ip || '?' });
    }
    modal.classList.remove('hidden');
}

function setFooter(text) {
    const el = $('footer-status');
    if (el) el.textContent = text;
}
