// 轻量 Toast 通知组件

const TOAST_DURATION = 3000;

/**
 * 显示一个 Toast
 * @param {string} message 消息内容
 * @param {'info'|'success'|'error'|'warning'} type 类型
 */
export function toast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.textContent = message;
    container.appendChild(el);
    setTimeout(() => {
        try {
            el.remove();
        } catch { /* ignore */ }
    }, TOAST_DURATION);
}

export const toastSuccess = (msg) => toast(msg, 'success');
export const toastError = (msg) => toast(msg, 'error');
export const toastWarning = (msg) => toast(msg, 'warning');
export const toastInfo = (msg) => toast(msg, 'info');
