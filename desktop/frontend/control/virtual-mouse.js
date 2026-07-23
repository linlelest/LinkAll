// 虚拟鼠标透明触控层
// 接收鼠标 / 触摸事件，转换为坐标百分比，通过 WebRTC DataChannel 下发
// 工具条上的「左键」「右键」按钮决定下次点击的按键

import { webrtcClient } from '../lib/webrtc.js';

let activeButton = 'left'; // 当前选中的按键：left / right
let isPointerDown = false;
let lastX = 0;
let lastY = 0;
let layerEl = null;

/** 初始化虚拟鼠标触控层 */
export function initVirtualMouse() {
    layerEl = document.getElementById('virtual-mouse-layer');
    if (!layerEl) return;

    layerEl.addEventListener('pointerdown', onPointerDown);
    layerEl.addEventListener('pointermove', onPointerMove);
    layerEl.addEventListener('pointerup', onPointerUp);
    layerEl.addEventListener('pointercancel', onPointerUp);
    layerEl.addEventListener('pointerleave', onPointerUp);

    // 工具条按键切换
    const btnLeft = document.getElementById('tool-left');
    const btnRight = document.getElementById('tool-right');
    btnLeft?.addEventListener('click', () => setActiveButton('left'));
    btnRight?.addEventListener('click', () => setActiveButton('right'));
}

/** 设置当前激活的鼠标按键 */
function setActiveButton(button) {
    activeButton = button;
    document.getElementById('tool-left')?.classList.toggle('active', button === 'left');
    document.getElementById('tool-right')?.classList.toggle('active', button === 'right');
}

/** 处理 pointerdown：发送 mouse:down */
function onPointerDown(e) {
    if (!webrtcClient.dc || webrtcClient.dc.readyState !== 'open') return;
    isPointerDown = true;
    layerEl.setPointerCapture?.(e.pointerId);
    const { x, y } = toRelative(e);
    lastX = x;
    lastY = y;
    sendMouseEvent('down', { button: activeButton, x, y });
}

/** 处理 pointermove：发送 mouse:move（绝对坐标） */
function onPointerMove(e) {
    if (!webrtcClient.dc || webrtcClient.dc.readyState !== 'open') return;
    const { x, y } = toRelative(e);
    // 移动距离阈值，避免发送过多事件
    if (Math.abs(x - lastX) < 1 && Math.abs(y - lastY) < 1 && !isPointerDown) return;
    if (isPointerDown) {
        sendMouseEvent('move', { button: activeButton, x, y });
    } else {
        sendMouseEvent('move', { x, y });
    }
    lastX = x;
    lastY = y;
}

/** 处理 pointerup：发送 mouse:up（如果之前按下） */
function onPointerUp(e) {
    if (!webrtcClient.dc || webrtcClient.dc.readyState !== 'open') return;
    if (isPointerDown) {
        const { x, y } = toRelative(e);
        sendMouseEvent('up', { button: activeButton, x, y });
    }
    isPointerDown = false;
    try { layerEl.releasePointerCapture?.(e.pointerId); } catch { /* ignore */ }
}

/** 计算相对坐标百分比（0-1000 整数，便于传输） */
function toRelative(e) {
    const rect = layerEl.getBoundingClientRect();
    const x = Math.max(0, Math.min(1000, Math.round((e.clientX - rect.left) / rect.width * 1000)));
    const y = Math.max(0, Math.min(1000, Math.round((e.clientY - rect.top) / rect.height * 1000)));
    return { x, y };
}

/** 发送鼠标事件（通过 DataChannel） */
function sendMouseEvent(action, payload) {
    webrtcClient.sendControl('mouse', { action, ...payload });
}

/** 切换左/右键模式（外部调用） */
export function setMouseButton(button) {
    setActiveButton(button);
}
