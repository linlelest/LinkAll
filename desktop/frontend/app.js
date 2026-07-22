// LinkALL 桌面端设置窗口逻辑
// 通过 Tauri invoke 调用后端命令，监听后端事件更新 UI。
// 纯原生 JS，无框架依赖。

// Tauri 2 全局 API（withGlobalTauri: true）
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

// ==================== DOM 元素 ====================
const $ = (id) => document.getElementById(id);

// ==================== 初始化 ====================
async function init() {
    await loadDeviceInfo();
    await loadConfig();
    await loadAuthStatus();
    await loadServiceStatus();
    await loadAutostart();
    bindEvents();
    bindBackendEvents();
}

// ==================== 加载数据 ====================

async function loadDeviceInfo() {
    try {
        const info = await invoke('get_device_info');
        $('device-id').textContent = info.device_id;
        $('device-code').textContent = info.device_code;
    } catch (e) {
        console.error('加载设备信息失败:', e);
    }
}

async function loadConfig() {
    try {
        const cfg = await invoke('get_config');
        $('server-url').value = cfg.server_url;
        $('codec-video').value = cfg.codec_video;
        $('target-fps').value = cfg.target_fps;
        $('max-bitrate').value = Math.round(cfg.max_bitrate / 1_000_000);
        $('scale').value = cfg.scale;
        $('scale-value').textContent = cfg.scale.toFixed(1) + 'x';
    } catch (e) {
        console.error('加载配置失败:', e);
    }
}

async function loadAuthStatus() {
    try {
        const loggedIn = await invoke('get_auth_status');
        if (loggedIn) {
            const user = await invoke('get_user_info');
            if (user) {
                $('display-username').textContent = user.username;
                $('display-role').textContent = user.role;
            }
            $('account-login').classList.add('hidden');
            $('account-info').classList.remove('hidden');
        } else {
            $('account-login').classList.remove('hidden');
            $('account-info').classList.add('hidden');
        }
    } catch (e) {
        console.error('加载认证状态失败:', e);
    }
}

async function loadServiceStatus() {
    try {
        const status = await invoke('get_service_status');
        updateServiceUI(status.running, status.paused);
    } catch (e) {
        console.error('加载服务状态失败:', e);
    }
}

async function loadAutostart() {
    try {
        const enabled = await invoke('get_autostart');
        $('autostart-toggle').checked = enabled;
    } catch (e) {
        console.error('加载自启状态失败:', e);
    }
}

// ==================== UI 更新 ====================

function updateServiceUI(running, paused) {
    const dot = $('status-dot');
    const text = $('status-text');
    const btnStart = $('btn-start');
    const btnPause = $('btn-pause');
    const btnStop = $('btn-stop');

    dot.className = 'status-dot';
    if (running && !paused) {
        dot.classList.add('running');
        text.textContent = '运行中';
        btnStart.disabled = true;
        btnPause.disabled = false;
        btnStop.disabled = false;
    } else if (running && paused) {
        dot.classList.add('paused');
        text.textContent = '已暂停';
        btnStart.disabled = true;
        btnPause.disabled = true;
        btnStop.disabled = false;
    } else {
        text.textContent = '未运行';
        btnStart.disabled = false;
        btnPause.disabled = true;
        btnStop.disabled = true;
    }
}

function setFooterStatus(text) {
    $('footer-status').textContent = text;
}

// ==================== 事件绑定 ====================

function bindEvents() {
    // 登录
    $('btn-login').addEventListener('click', async () => {
        const username = $('username').value.trim();
        const password = $('password').value;
        if (!username || !password) {
            setFooterStatus('请输入用户名和密码');
            return;
        }
        $('btn-login').disabled = true;
        setFooterStatus('登录中...');
        try {
            await invoke('login', { params: { username, password } });
            setFooterStatus('登录成功');
            await loadAuthStatus();
        } catch (e) {
            setFooterStatus('登录失败: ' + e);
            console.error('登录失败:', e);
        }
        $('btn-login').disabled = false;
    });

    // 登出
    $('btn-logout').addEventListener('click', async () => {
        try {
            await invoke('logout');
            setFooterStatus('已登出');
            await loadAuthStatus();
        } catch (e) {
            setFooterStatus('登出失败: ' + e);
        }
    });

    // 保存服务器地址
    $('btn-save-server').addEventListener('click', async () => {
        const url = $('server-url').value.trim();
        if (!url) return;
        try {
            await invoke('update_config', { params: { key: 'server_url', value: url } });
            setFooterStatus('服务器地址已保存');
        } catch (e) {
            setFooterStatus('保存失败: ' + e);
        }
    });

    // 服务控制
    $('btn-start').addEventListener('click', async () => {
        setFooterStatus('启动服务中...');
        try {
            await invoke('start_service');
            setFooterStatus('服务已启动');
        } catch (e) {
            setFooterStatus('启动失败: ' + e);
            console.error('启动失败:', e);
        }
    });

    $('btn-pause').addEventListener('click', async () => {
        try {
            await invoke('pause_service');
            setFooterStatus('服务已暂停');
        } catch (e) {
            setFooterStatus('暂停失败: ' + e);
        }
    });

    $('btn-stop').addEventListener('click', async () => {
        try {
            await invoke('stop_service');
            setFooterStatus('服务已停止');
        } catch (e) {
            setFooterStatus('停止失败: ' + e);
        }
    });

    // 重置设备编号
    $('btn-reset-device-id').addEventListener('click', async () => {
        if (!confirm('确定重置设备编号？重置后原编号将失效。')) return;
        try {
            const newId = await invoke('reset_device_id');
            $('device-id').textContent = newId;
            setFooterStatus('设备编号已重置');
        } catch (e) {
            setFooterStatus('重置失败: ' + e);
        }
    });

    // 重置设备码
    $('btn-reset-device-code').addEventListener('click', async () => {
        if (!confirm('确定重置设备码？重置后原设备码将失效。')) return;
        try {
            const newCode = await invoke('reset_device_code');
            $('device-code').textContent = newCode;
            setFooterStatus('设备码已重置');
        } catch (e) {
            setFooterStatus('重置失败: ' + e);
        }
    });

    // 保存视频设置
    $('btn-save-video').addEventListener('click', async () => {
        const codec = $('codec-video').value;
        const fps = parseInt($('target-fps').value);
        const bitrate = parseInt($('max-bitrate').value) * 1_000_000;
        const scale = parseFloat($('scale').value);
        try {
            await invoke('update_config', { params: { key: 'codec_video', value: codec } });
            await invoke('update_config', { params: { key: 'target_fps', value: String(fps) } });
            await invoke('update_config', { params: { key: 'max_bitrate', value: String(bitrate) } });
            await invoke('update_config', { params: { key: 'scale', value: String(scale) } });
            setFooterStatus('视频设置已保存');
        } catch (e) {
            setFooterStatus('保存失败: ' + e);
        }
    });

    // 缩放滑块实时显示
    $('scale').addEventListener('input', (e) => {
        $('scale-value').textContent = parseFloat(e.target.value).toFixed(1) + 'x';
    });

    // 开机自启开关
    $('autostart-toggle').addEventListener('change', async (e) => {
        const enabled = e.target.checked;
        try {
            await invoke('set_autostart', { enabled });
            setFooterStatus(enabled ? '已启用开机自启' : '已禁用开机自启');
        } catch (e) {
            setFooterStatus('设置失败: ' + e);
            e.target.checked = !enabled;
        }
    });
}

// ==================== 后端事件监听 ====================

function bindBackendEvents() {
    listen('service-started', () => {
        setFooterStatus('服务已启动');
        loadServiceStatus();
    });

    listen('service-stopped', () => {
        setFooterStatus('服务已停止');
        loadServiceStatus();
    });

    listen('service-paused', () => {
        setFooterStatus('服务已暂停');
        loadServiceStatus();
    });

    listen('service-resumed', () => {
        setFooterStatus('服务已恢复');
        loadServiceStatus();
    });

    listen('peer-connected', () => {
        setFooterStatus('远程连接已建立');
    });

    listen('peer-disconnected', () => {
        setFooterStatus('远程连接已断开');
    });

    listen('service-error', (event) => {
        setFooterStatus('错误: ' + (event.payload?.message || '未知'));
    });

    listen('service-stats', (event) => {
        const { rttMs, fps } = event.payload || {};
        setFooterStatus(`RTT: ${rttMs}ms | FPS: ${fps}`);
    });

    listen('tray-action', (event) => {
        if (event.payload === 'pause') {
            invoke('pause_service').catch(console.error);
        }
    });
}

// ==================== 启动 ====================
init();
