// 控制端连接表单
// 收集目标设备编号 / 设备码 / 连接模式，调用 Tauri connect_to_device 命令
// 同时提供「发现设备」入口（同账号设备列表）

import { api } from '../lib/api.js';
import { t } from '../lib/i18n.js';
import { toast, toastError, toastInfo } from '../lib/toast.js';

/**
 * 初始化连接表单
 * @param {Object} hooks 回调
 * @param {Function} hooks.onConnected 连接成功后调用，切到控制画布
 * @param {Function} hooks.onDisconnected 断开后调用，回到连接表单
 */
export function initConnectForm(hooks) {
    const deviceIdInput = document.getElementById('target-device-id');
    const deviceCodeInput = document.getElementById('target-device-code');
    const modeSelect = document.getElementById('connect-mode');
    const btnConnect = document.getElementById('btn-connect');
    const btnDiscover = document.getElementById('btn-discover');
    const discoverHint = document.getElementById('discover-hint');

    // 连接模式切换：匿名模式时设备码可留空
    modeSelect.addEventListener('change', () => {
        const mode = modeSelect.value;
        if (mode === 'anonymous') {
            deviceCodeInput.value = '';
            deviceCodeInput.placeholder = '';
            deviceCodeInput.disabled = true;
        } else {
            deviceCodeInput.disabled = false;
            deviceCodeInput.placeholder = '------';
        }
    });

    // 触发一次初始化
    modeSelect.dispatchEvent(new Event('change'));

    // 连接按钮
    btnConnect.addEventListener('click', async () => {
        const deviceId = deviceIdInput.value.trim().toUpperCase();
        const deviceCode = deviceCodeInput.value.trim();
        const mode = modeSelect.value;

        if (!deviceId) {
            toastError(t('control.connectFailed') + ': deviceId');
            return;
        }
        if (mode !== 'anonymous' && !deviceCode) {
            toastError(t('control.connectFailed') + ': deviceCode');
            return;
        }

        btnConnect.disabled = true;
        btnConnect.textContent = t('control.connecting');
        try {
            await api.connectToDevice(deviceId, deviceCode, mode);
            toast(t('control.connectSuccess'), 'success');
            hooks.onConnected?.({ deviceId, deviceCode, mode });
        } catch (e) {
            toastError(`${t('control.connectFailed')}: ${e}`);
            console.error('连接失败:', e);
        } finally {
            btnConnect.disabled = false;
            btnConnect.textContent = t('control.connect');
        }
    });

    // 发现设备按钮
    btnDiscover.addEventListener('click', async () => {
        try {
            const result = await api.discoverDevices();
            const devices = Array.isArray(result) ? result : (result?.devices || []);
            if (devices.length === 0) {
                discoverHint.classList.remove('hidden');
                toastInfo(t('control.noDevices'));
                return;
            }
            discoverHint.classList.add('hidden');
            // 取第一个在线设备填入表单
            const first = devices[0];
            if (first.deviceId) {
                deviceIdInput.value = first.deviceId;
            }
            toast(`${devices.length} devices`, 'success');
        } catch (e) {
            toastError(`${t('control.noDevices')}: ${e}`);
        }
    });

    // 回车键触发连接
    [deviceIdInput, deviceCodeInput].forEach((input) => {
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') btnConnect.click();
        });
    });
}

/** 重置表单状态（断开后调用） */
export function resetConnectForm() {
    const deviceIdInput = document.getElementById('target-device-id');
    const deviceCodeInput = document.getElementById('target-device-code');
    if (deviceIdInput) deviceIdInput.value = '';
    if (deviceCodeInput) deviceCodeInput.value = '';
}
