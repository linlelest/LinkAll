// Tauri 命令模块
// 定义前端通过 invoke() 调用的命令，桥接设置窗口 UI 与后端守护进程。
// 命令列表：登录/登出、设备信息、配置读写、服务控制、开机自启、设备重置。
use serde::Serialize;
use tauri::State;

use crate::daemon::{DaemonEvent, DaemonState};
use crate::webrtc::control::SettingsSnapshot;

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
