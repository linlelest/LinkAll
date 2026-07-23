// 控制画布：协调 video / 状态栏 / 虚拟工具层 / 断开按钮
// 当 connect_to_device 命令成功后，连接表单隐藏，控制画布显示

import { webrtcClient } from '../lib/webrtc.js';
import { api } from '../lib/api.js';
import { t } from '../lib/i18n.js';
import { toast, toastError } from '../lib/toast.js';

import { initVirtualMouse } from './virtual-mouse.js';
import { initVirtualKeyboard, hide as hideKeyboard } from './virtual-keyboard.js';
import { initWheelSlider, hide as hideWheel } from './wheel-slider.js';
import { initSettingsDrawer, hide as hideDrawer } from './settings-drawer.js';

let canvasEl = null;
let connectFormCard = null;
let btnDisconnect = null;

/** 初始化控制画布 */
export function initControlCanvas() {
    canvasEl = document.getElementById('control-canvas');
    connectFormCard = document.getElementById('connect-form-card');
    btnDisconnect = document.getElementById('btn-disconnect');

    if (!canvasEl) return;

    // 绑定 WebRTC 客户端到 <video> 元素
    const videoEl = document.getElementById('remote-video');
    webrtcClient.setVideoElement(videoEl);

    // 设置事件回调
    webrtcClient.setHandlers({
        onTrack: (track) => {
            console.log('远端轨道到达:', track.kind);
        },
        onConnectionStateChange: (state) => {
            console.log('PeerConnection 状态:', state);
            if (state === 'connected') {
                toast(t('control.connectSuccess'), 'success');
            } else if (state === 'disconnected' || state === 'failed' || state === 'closed') {
                onPeerClosed();
            }
        },
        onDataChannelOpen: () => {
            console.log('DataChannel 已开启');
            webrtcClient.startStatsTimer();
        },
        onDataChannelClose: () => {
            console.log('DataChannel 已关闭');
        },
        onConnectAck: (ok, sessionId, requireConfirm) => {
            if (!ok) {
                toastError(t('control.connectFailed'));
                onPeerClosed();
            } else if (requireConfirm) {
                toast(t('control.waitingConfirm'), 'warning');
            }
        },
        onBye: (reason) => {
            toast(`${t('control.disconnected')}: ${reason || ''}`, 'warning');
            onPeerClosed();
        },
        onStats: (stats) => {
            updateStatsUI(stats);
        },
    });

    // 创建 PeerConnection 与 DataChannel
    webrtcClient.create();

    // 初始化各虚拟工具模块
    initVirtualMouse();
    initVirtualKeyboard();
    initWheelSlider();
    initSettingsDrawer();

    // 文件传输入口（占位）
    document.getElementById('tool-files')?.addEventListener('click', () => {
        document.getElementById('file-panel')?.classList.remove('hidden');
    });
    document.getElementById('file-close')?.addEventListener('click', () => {
        document.getElementById('file-panel')?.classList.add('hidden');
    });

    // 断开按钮
    btnDisconnect?.addEventListener('click', async () => {
        try {
            await api.disconnectPeer();
        } catch (e) {
            console.warn('disconnect_peer 失败:', e);
        }
        onPeerClosed();
    });
}

/** 进入控制画布（连接成功后调用） */
export function showCanvas() {
    if (!canvasEl) return;
    connectFormCard?.classList.add('hidden');
    canvasEl.classList.remove('hidden');
    // 隐藏各工具子面板
    hideKeyboard();
    hideWheel();
    hideDrawer();
}

/** 退出控制画布（回到连接表单） */
export function hideCanvas() {
    if (!canvasEl) return;
    canvasEl.classList.add('hidden');
    connectFormCard?.classList.remove('hidden');
    // 关闭 WebRTC 资源
    webrtcClient.close();
    // 重置统计 UI
    updateStatsUI({ rttMs: 0, packetLoss: 0, fps: 0, duration: 0 });
    // 隐藏所有弹层
    hideKeyboard();
    hideWheel();
    hideDrawer();
    document.getElementById('file-panel')?.classList.add('hidden');
}

/** Peer 连接关闭时的清理 */
function onPeerClosed() {
    hideCanvas();
}

/** 更新顶部状态栏统计 */
function updateStatsUI(stats) {
    if (!stats) return;
    const rttEl = document.getElementById('stat-rtt');
    const lossEl = document.getElementById('stat-loss');
    const fpsEl = document.getElementById('stat-fps');
    const durationEl = document.getElementById('stat-duration');
    if (rttEl) rttEl.textContent = stats.rttMs ?? '--';
    if (lossEl) lossEl.textContent = stats.packetLoss ?? '--';
    if (fpsEl) fpsEl.textContent = stats.fps ?? '--';
    if (durationEl) {
        const sec = stats.duration ?? 0;
        const m = Math.floor(sec / 60).toString().padStart(2, '0');
        const s = (sec % 60).toString().padStart(2, '0');
        durationEl.textContent = `${m}:${s}`;
    }
}
