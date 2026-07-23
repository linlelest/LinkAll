// 控制页逻辑
// 协调连接表单 + 控制画布
// connect_to_device 成功后切换到画布，disconnect_peer 后回到表单

import { initConnectForm, resetConnectForm } from '../control/connect-form.js';
import { initControlCanvas, showCanvas, hideCanvas } from '../control/canvas.js';
import { subscribeAll } from '../lib/api.js';
import { t } from '../lib/i18n.js';
import { toast, toastWarning } from '../lib/toast.js';

/** 初始化控制页 */
export function initControlView() {
    // 初始化连接表单
    initConnectForm({
        onConnected: () => {
            showCanvas();
        },
        onDisconnected: () => {
            hideCanvas();
            resetConnectForm();
        },
    });

    // 初始化控制画布（含虚拟工具层）
    initControlCanvas();

    // 监听后端断开事件
    subscribeAll([
        'peer-disconnected', 'control-disconnected', 'control-error',
    ], (evt, payload) => {
        if (evt === 'control-error') {
            toast(`${evt}: ${payload?.message || ''}`, 'error');
        } else {
            toastWarning(t('control.disconnected'));
        }
        hideCanvas();
        resetConnectForm();
    });
}

/** 切换到控制页时调用（可选的页面激活钩子） */
export function activateControlView() {
    // 控制页激活时不做特殊处理，连接状态由 WebRTC 自管
}

/** 离开控制页时调用（可选） */
export function deactivateControlView() {
    // 不主动断开连接，仅隐藏子面板
    // 实际断开由用户点击「断开」按钮触发
}
