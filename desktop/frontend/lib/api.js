// Tauri invoke 封装 + 服务端 HTTP API 调用
// 统一桥接前端 → Rust 命令 → 服务端 HTTP/WebSocket

const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

// ============ 本地 Tauri 命令封装 ============

/** 调用 Tauri 命令并返回结果 */
export function tauriInvoke(cmd, args = {}) {
    return invoke(cmd, args);
}

/** 监听 Tauri 事件 */
export function tauriListen(event, handler) {
    return listen(event, handler);
}

// ============ 设备 / 配置 / 认证 ============

export const api = {
    // 设备信息
    getDeviceInfo: () => tauriInvoke('get_device_info'),
    resetDeviceId: () => tauriInvoke('reset_device_id'),
    resetDeviceCode: () => tauriInvoke('reset_device_code'),

    // 配置
    getConfig: () => tauriInvoke('get_config'),
    updateConfig: (key, value) => tauriInvoke('update_config', { params: { key, value: String(value) } }),

    // 认证
    login: (username, password) => tauriInvoke('login', { params: { username, password } }),
    logout: () => tauriInvoke('logout'),
    getAuthStatus: () => tauriInvoke('get_auth_status'),
    getUserInfo: () => tauriInvoke('get_user_info'),

    // 被控服务
    startService: () => tauriInvoke('start_service'),
    stopService: () => tauriInvoke('stop_service'),
    pauseService: () => tauriInvoke('pause_service'),
    resumeService: () => tauriInvoke('resume_service'),
    getServiceStatus: () => tauriInvoke('get_service_status'),

    // 系统
    setAutostart: (enabled) => tauriInvoke('set_autostart', { enabled }),
    getAutostart: () => tauriInvoke('get_autostart'),
    exportLogs: () => tauriInvoke('export_logs'),

    // 控制端命令
    connectToDevice: (deviceId, deviceCode, mode) =>
        tauriInvoke('connect_to_device', { deviceId, deviceCode, mode }),
    sendControlEvent: (eventJson) =>
        tauriInvoke('send_control_event', { eventJson }),
    getPeerStats: () => tauriInvoke('get_peer_stats'),
    disconnectPeer: () => tauriInvoke('disconnect_peer'),
    discoverDevices: () => tauriInvoke('discover_devices'),
    getConnectionRequests: () => tauriInvoke('get_connection_requests'),
    respondConnectionRequest: (requestId, action) =>
        tauriInvoke('respond_connection_request', { requestId, action }),
};

// ============ 后端事件订阅工具 ============

/** 订阅后端事件，返回反订阅函数 */
export function subscribe(event, handler) {
    let unlisten = null;
    listen(event, (e) => handler(e.payload, e)).then((fn) => { unlisten = fn; });
    return () => { if (unlisten) unlisten(); };
}

/** 订阅多个事件，返回统一反订阅函数 */
export function subscribeAll(events, handler) {
    const unsubs = events.map((evt) => subscribe(evt, (p) => handler(evt, p)));
    return () => unsubs.forEach((fn) => fn());
}
