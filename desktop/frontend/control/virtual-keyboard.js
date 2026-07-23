// 虚拟键盘
// 监听 .kb-key 按钮的 pointerdown / pointerup，发送 keyboard:down / keyboard:up
// 也支持物理键盘事件转发

import { webrtcClient } from '../lib/webrtc.js';

let keyboardEl = null;
let isVisible = false;

/** 初始化虚拟键盘 */
export function initVirtualKeyboard() {
    keyboardEl = document.getElementById('virtual-keyboard');
    if (!keyboardEl) return;

    // 绑定每个虚拟按键
    keyboardEl.querySelectorAll('.kb-key').forEach((btn) => {
        const key = btn.dataset.key;
        if (!key) return;
        // 关闭按钮单独处理
        if (btn.id === 'kb-close') {
            btn.addEventListener('click', () => hide());
            return;
        }
        btn.addEventListener('pointerdown', (e) => {
            e.preventDefault();
            sendKeyEvent(key, 'down');
        });
        btn.addEventListener('pointerup', () => sendKeyEvent(key, 'up'));
        btn.addEventListener('pointerleave', () => sendKeyEvent(key, 'up'));
    });

    // 工具条「键盘」按钮切换显隐
    document.getElementById('tool-keyboard')?.addEventListener('click', () => {
        toggle();
    });

    // 物理键盘事件转发（仅当控制画布可见时）
    document.addEventListener('keydown', (e) => {
        if (!isVisible) return;
        // 阻止默认行为，避免页面滚动
        if (e.code && e.code !== 'F5') e.preventDefault();
        sendKeyEvent(e.code, 'down');
    });
    document.addEventListener('keyup', (e) => {
        if (!isVisible) return;
        e.preventDefault();
        sendKeyEvent(e.code, 'up');
    });
}

/** 显示虚拟键盘 */
export function show() {
    keyboardEl?.classList.remove('hidden');
    isVisible = true;
    document.getElementById('tool-keyboard')?.classList.add('active');
}

/** 隐藏虚拟键盘 */
export function hide() {
    keyboardEl?.classList.add('hidden');
    isVisible = false;
    document.getElementById('tool-keyboard')?.classList.remove('active');
}

/** 切换显隐 */
export function toggle() {
    if (isVisible) hide();
    else show();
}

/** 发送键盘事件 */
function sendKeyEvent(key, action) {
    webrtcClient.sendControl('keyboard', { key, action, modifiers: [] });
}
