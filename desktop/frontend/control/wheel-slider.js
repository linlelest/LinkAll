// 滚轮侧边条
// 提供简单的滚轮控制：上下按钮单步滚动 + 中部拖拽连续滚动
// 通过 DataChannel 发送 wheel 事件

import { webrtcClient } from '../lib/webrtc.js';

let sliderEl = null;
let isVisible = false;

/** 初始化滚轮侧边条 */
export function initWheelSlider() {
    sliderEl = document.getElementById('wheel-slider');
    if (!sliderEl) return;

    // 上下按钮：单步滚动
    sliderEl.querySelectorAll('.wheel-btn').forEach((btn) => {
        btn.addEventListener('click', () => {
            const delta = parseInt(btn.dataset.delta || '0', 10);
            sendWheel(0, delta * 120); // 标准 wheel delta ≈ 120
        });
    });

    // 中部拖拽：模拟连续滚动
    const thumb = document.getElementById('wheel-thumb');
    let dragStartY = 0;
    let lastDeltaY = 0;
    thumb?.addEventListener('pointerdown', (e) => {
        dragStartY = e.clientY;
        lastDeltaY = 0;
        sliderEl.setPointerCapture?.(e.pointerId);
    });
    thumb?.addEventListener('pointermove', (e) => {
        if (dragStartY === 0) return;
        const dy = e.clientY - dragStartY;
        const step = dy - lastDeltaY;
        if (Math.abs(step) >= 8) {
            sendWheel(0, -step * 5); // 向上拖动 → 滚动向上
            lastDeltaY = dy;
        }
    });
    thumb?.addEventListener('pointerup', () => { dragStartY = 0; });
    thumb?.addEventListener('pointercancel', () => { dragStartY = 0; });

    // 工具条「滚轮」按钮切换显隐
    document.getElementById('tool-wheel')?.addEventListener('click', () => toggle());
}

/** 显示滚轮侧边条 */
export function show() {
    sliderEl?.classList.remove('hidden');
    isVisible = true;
    document.getElementById('tool-wheel')?.classList.add('active');
}

/** 隐藏滚轮侧边条 */
export function hide() {
    sliderEl?.classList.add('hidden');
    isVisible = false;
    document.getElementById('tool-wheel')?.classList.remove('active');
}

/** 切换显隐 */
export function toggle() {
    if (isVisible) hide();
    else show();
}

/** 发送滚轮事件 */
function sendWheel(deltaX, deltaY) {
    webrtcClient.sendControl('wheel', { deltaX, deltaY });
}
