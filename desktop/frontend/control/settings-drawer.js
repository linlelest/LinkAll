// 底部参数抽屉
// 提供：缩放 / 码率 / 帧率 滑块 + 防窥屏开关
// 通过 DataChannel 发送 settings_sync 指令，实时同步到被控端

import { webrtcClient } from '../lib/webrtc.js';

let drawerEl = null;
let isVisible = false;

/** 初始化参数抽屉 */
export function initSettingsDrawer() {
    drawerEl = document.getElementById('settings-drawer');
    if (!drawerEl) return;

    const scaleSlider = document.getElementById('drawer-scale');
    const scaleValue = document.getElementById('drawer-scale-value');
    const bitrateSlider = document.getElementById('drawer-bitrate');
    const bitrateValue = document.getElementById('drawer-bitrate-value');
    const fpsSlider = document.getElementById('drawer-fps');
    const fpsValue = document.getElementById('drawer-fps-value');
    const privacyToggle = document.getElementById('drawer-privacy');
    const btnClose = document.getElementById('drawer-close');

    // 实时显示数值
    scaleSlider?.addEventListener('input', (e) => {
        scaleValue.textContent = parseFloat(e.target.value).toFixed(1) + 'x';
    });
    bitrateSlider?.addEventListener('input', (e) => {
        bitrateValue.textContent = e.target.value + 'M';
    });
    fpsSlider?.addEventListener('input', (e) => {
        fpsValue.textContent = e.target.value;
    });

    // 应用按钮：滑块拖完发送 settings_sync
    scaleSlider?.addEventListener('change', () => sendSettings());
    bitrateSlider?.addEventListener('change', () => sendSettings());
    fpsSlider?.addEventListener('change', () => sendSettings());

    // 防窥屏开关：即时发送 privacy_screen 指令
    privacyToggle?.addEventListener('change', (e) => {
        webrtcClient.sendControl('privacy_screen', { enabled: e.target.checked });
    });

    btnClose?.addEventListener('click', () => hide());

    // 工具条「设置」按钮切换显隐
    document.getElementById('tool-settings')?.addEventListener('click', () => toggle());
}

/** 显示抽屉 */
export function show() {
    drawerEl?.classList.remove('hidden');
    isVisible = true;
    document.getElementById('tool-settings')?.classList.add('active');
}

/** 隐藏抽屉 */
export function hide() {
    drawerEl?.classList.add('hidden');
    isVisible = false;
    document.getElementById('tool-settings')?.classList.remove('active');
}

/** 切换显隐 */
export function toggle() {
    if (isVisible) hide();
    else show();
}

/** 发送 settings_sync 指令 */
function sendSettings() {
    const scale = parseFloat(document.getElementById('drawer-scale')?.value || '1.0');
    const bitrate = parseInt(document.getElementById('drawer-bitrate')?.value || '8', 10) * 1_000_000;
    const fps = parseInt(document.getElementById('drawer-fps')?.value || '30', 10);
    webrtcClient.sendControl('settings_sync', {
        category: 'screen',
        screen: { scale, fps, maxBitrate: bitrate },
    });
}
