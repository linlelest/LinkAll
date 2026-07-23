// Tauri 命令模块
// 定义前端通过 invoke() 调用的命令，桥接设置窗口 UI 与后端守护进程。
// 命令列表：登录/登出、设备信息、配置读写、服务控制、开机自启、设备重置。
// 控制端命令：发起连接、发送控制指令、获取统计、断开会话、设备发现、连接请求管理。
use serde::Serialize;
use tauri::State;

use crate::control::state::{
    discover_devices as fetch_discovered_devices,
    respond_connection_request as submit_connection_response, ConnectionRequest, ControlState,
    DeviceSummary, PeerStats,
};
use crate::daemon::DaemonState;

/// 设备信息响应
#[derive(Debug, Serialize)]
pub struct DeviceInfoResponse {
    pub device_id: String,
    pub device_code: String,
    pub is_bound: bool,
}

/// 服务状态响应
#[derive(Debug, Serialize)]
pub struct ServiceStatus {
    pub running: bool,
    pub paused: bool,
}

/// 配置响应
#[derive(Debug, Serialize)]
pub struct ConfigResponse {
    pub server_url: String,
    pub allow_anonymous: bool,
    pub allow_device_code: bool,
    pub allow_remote_control: bool,
    pub autostart: bool,
    pub performance_monitor: bool,
    pub log_level: String,
    pub codec_video: String,
    pub target_fps: u32,
    pub max_bitrate: u64,
    pub scale: f64,
}

/// 登录请求参数
#[derive(Debug, serde::Deserialize)]
pub struct LoginParams {
    pub username: String,
    pub password: String,
}

/// 配置更新参数
#[derive(Debug, serde::Deserialize)]
pub struct UpdateConfigParams {
    pub key: String,
    pub value: String,
}

// ==================== 命令定义 ====================

/// 获取设备信息
#[tauri::command]
pub async fn get_device_info(state: State<'_, DaemonState>) -> Result<DeviceInfoResponse, String> {
    let dev = state.device_info().await;
    Ok(DeviceInfoResponse {
        device_id: dev.device_id,
        device_code: dev.device_code,
        is_bound: dev.owner_user_id.is_some(),
    })
}

/// 获取配置
#[tauri::command]
pub async fn get_config(state: State<'_, DaemonState>) -> Result<ConfigResponse, String> {
    let cfg = state.config().await;
    Ok(ConfigResponse {
        server_url: cfg.server_url,
        allow_anonymous: cfg.allow_anonymous,
        allow_device_code: cfg.allow_device_code,
        allow_remote_control: cfg.allow_remote_control,
        autostart: cfg.autostart,
        performance_monitor: cfg.performance_monitor,
        log_level: cfg.log_level,
        codec_video: cfg.codec_video,
        target_fps: cfg.target_fps,
        max_bitrate: cfg.max_bitrate,
        scale: cfg.scale,
    })
}

/// 更新配置
#[tauri::command]
pub async fn update_config(
    state: State<'_, DaemonState>,
    params: UpdateConfigParams,
) -> Result<(), String> {
    state
        .update_config(&params.key, &params.value)
        .await
        .map_err(|e| e.to_string())
}

/// 登录
#[tauri::command]
pub async fn login(state: State<'_, DaemonState>, params: LoginParams) -> Result<(), String> {
    state
        .login(&params.username, &params.password)
        .await
        .map_err(|e| e.to_string())
}

/// 登出
#[tauri::command]
pub async fn logout(state: State<'_, DaemonState>) -> Result<(), String> {
    state.logout().await.map_err(|e| e.to_string())
}

/// 启动服务
#[tauri::command]
pub async fn start_service(state: State<'_, DaemonState>) -> Result<(), String> {
    state.start().await.map_err(|e| e.to_string())
}

/// 停止服务
#[tauri::command]
pub async fn stop_service(state: State<'_, DaemonState>) -> Result<(), String> {
    state.stop().await.map_err(|e| e.to_string())
}

/// 暂停服务
#[tauri::command]
pub async fn pause_service(state: State<'_, DaemonState>) -> Result<(), String> {
    state.pause().await.map_err(|e| e.to_string())
}

/// 恢复服务
#[tauri::command]
pub async fn resume_service(state: State<'_, DaemonState>) -> Result<(), String> {
    state.resume().await.map_err(|e| e.to_string())
}

/// 获取服务状态
#[tauri::command]
pub async fn get_service_status(state: State<'_, DaemonState>) -> Result<ServiceStatus, String> {
    Ok(ServiceStatus {
        running: state.is_running(),
        paused: state.is_paused(),
    })
}

/// 重置设备编号
#[tauri::command]
pub async fn reset_device_id(state: State<'_, DaemonState>) -> Result<String, String> {
    state.reset_device_id().await.map_err(|e| e.to_string())
}

/// 重置设备码
#[tauri::command]
pub async fn reset_device_code(state: State<'_, DaemonState>) -> Result<String, String> {
    state.reset_device_code().await.map_err(|e| e.to_string())
}

/// 设置开机自启
#[tauri::command]
pub async fn set_autostart(enabled: bool) -> Result<(), String> {
    crate::autostart::set_autostart(enabled).map_err(|e| e.to_string())
}

/// 获取开机自启状态
#[tauri::command]
pub async fn get_autostart() -> Result<bool, String> {
    Ok(crate::autostart::is_autostart_enabled())
}

/// 获取登录状态（是否已登录且 JWT 有效）
#[tauri::command]
pub async fn get_auth_status(state: State<'_, DaemonState>) -> Result<bool, String> {
    state.is_logged_in().await.map_err(|e| e.to_string())
}

/// 获取当前用户信息
#[tauri::command]
pub async fn get_user_info(
    state: State<'_, DaemonState>,
) -> Result<Option<serde_json::Value>, String> {
    state
        .current_user()
        .await
        .map_err(|e| e.to_string())
        .and_then(|opt| {
            opt
                .map(|u| serde_json::to_value(u).map_err(|e| e.to_string()))
                .transpose()
        })
}

/// 导出日志（一键打包最近日志内容返回给前端）
#[tauri::command]
pub async fn export_logs() -> Result<String, String> {
    // 日志目录：<data_dir>/LinkALL/logs/linkall.log
    let log_dir = dirs::data_dir()
        .ok_or_else(|| "无法确定数据目录".to_string())?
        .join("LinkALL")
        .join("logs");
    let log_file = log_dir.join("linkall.log");
    std::fs::read_to_string(&log_file).map_err(|e| format!("读取日志失败：{}", e))
}

// ==================== 控制端命令 ====================

/// 作为控制端发起连接
/// 通过 ControlState 创建 ControlPeer，建立信令通道并发起 SDP Offer。
/// - device_id: 目标设备 12 位编号
/// - device_code: 目标设备 10 位设备码（匿名模式可为空）
/// - mode: 连接模式 anonymous / same_account / device_code
#[tauri::command]
pub async fn connect_to_device(
    daemon: State<'_, DaemonState>,
    control: State<'_, ControlState>,
    device_id: String,
    device_code: String,
    mode: String,
) -> Result<(), String> {
    control
        .connect(&daemon, &device_id, &device_code, &mode)
        .await
        .map_err(|e| e.to_string())
}

/// 通过 DataChannel 发送键鼠/滚轮/手势/设置同步指令（JSON 字符串）
/// 由前端组装完整的协议信封后透传给被控端。
#[tauri::command]
pub async fn send_control_event(
    control: State<'_, ControlState>,
    event_json: String,
) -> Result<(), String> {
    control
        .send_control_event(event_json)
        .await
        .map_err(|e| e.to_string())
}

/// 获取当前控制会话的统计（RTT / 丢包率 / 帧率 / 已连接时长）
#[tauri::command]
pub async fn get_peer_stats(control: State<'_, ControlState>) -> Result<PeerStats, String> {
    Ok(control.stats().await)
}

/// 断开控制会话（关闭 PeerConnection 与信令通道）
#[tauri::command]
pub async fn disconnect_peer(control: State<'_, ControlState>) -> Result<(), String> {
    control.disconnect().await.map_err(|e| e.to_string())
}

/// 同账号设备发现：调用服务端 GET /api/devices/discover 返回当前用户在线设备列表
#[tauri::command]
pub async fn discover_devices(
    daemon: State<'_, DaemonState>,
) -> Result<Vec<DeviceSummary>, String> {
    fetch_discovered_devices(&daemon)
        .await
        .map_err(|e| e.to_string())
}

/// 获取待确认的连接请求列表（被控端视角，匿名控制请求由信令通道推送并缓存）
#[tauri::command]
pub async fn get_connection_requests(
    control: State<'_, ControlState>,
) -> Result<Vec<ConnectionRequest>, String> {
    Ok(control.connection_requests().await)
}

/// 响应连接请求：调用服务端 POST /api/connect/anonymous/confirm
/// action: allow_once / allow_always / deny / device_code
#[tauri::command]
pub async fn respond_connection_request(
    daemon: State<'_, DaemonState>,
    control: State<'_, ControlState>,
    request_id: String,
    action: String,
) -> Result<(), String> {
    // 调用服务端确认接口
    submit_connection_response(&daemon, request_id.clone(), action.clone())
        .await
        .map_err(|e| e.to_string())?;
    // 本地缓存移除该请求
    control.remove_connection_request(&request_id).await;
    Ok(())
}
