// LinkALL 桌面被控端 - Tauri 2 应用库
// 声明所有模块并提供 run() 入口函数。
mod auth;
mod autostart;
mod capture;
mod commands;
mod config;
mod daemon;
mod device;
mod encoder;
mod input;
mod privacy;
mod tray;
mod webrtc;

use tauri::{Emitter, Manager};
use tokio::sync::mpsc;

use daemon::{DaemonEvent, DaemonState};

/// 应用入口：初始化日志、数据库、守护进程、托盘，启动 Tauri 事件循环。
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // 初始化日志
    let _ = env_logger::Builder::from_env(
        env_logger::Env::default().default_filter_or("info")
    )
    .format_timestamp_secs()
    .try_init();

    log::info!("LinkALL 桌面被控端启动中...");

    // 创建守护进程事件通道
    let (event_tx, mut event_rx) = mpsc::unbounded_channel::<DaemonEvent>();

    // 创建 Tauri 应用
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .setup(move |app| {
            // 初始化守护进程状态
            // new() 即使初始化失败也返回可用的 DaemonState（init_error 记录错误）
            // 确保 Tauri 的 app.manage() 总能注册成功，命令调用时返回友好错误
            // 注意：必须 manage(DaemonState) 而非 manage(Arc<DaemonState>)，
            // 因为命令参数是 State<'_, DaemonState>
            let daemon_state = DaemonState::new(event_tx);

            // 注册 Tauri 托管状态（Tauri 内部会用 RwLock 包裹）
            app.manage(daemon_state);

            // 初始化系统托盘
            if let Err(e) = tray::init_tray(app.handle()) {
                log::warn!("初始化系统托盘失败：{:?}", e);
            }

            // 启动守护进程事件转发任务（→ Tauri 前端事件）
            let app_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                while let Some(evt) = event_rx.recv().await {
                    let (name, payload) = match evt {
                        DaemonEvent::Started => ("service-started", serde_json::Value::Null),
                        DaemonEvent::Stopped => ("service-stopped", serde_json::Value::Null),
                        DaemonEvent::Paused => ("service-paused", serde_json::Value::Null),
                        DaemonEvent::Resumed => ("service-resumed", serde_json::Value::Null),
                        DaemonEvent::PeerConnected => ("peer-connected", serde_json::Value::Null),
                        DaemonEvent::PeerDisconnected => ("peer-disconnected", serde_json::Value::Null),
                        DaemonEvent::Error(msg) => ("service-error", serde_json::json!({ "message": msg })),
                        DaemonEvent::Stats { rtt_ms, fps } => {
                            ("service-stats", serde_json::json!({ "rttMs": rtt_ms, "fps": fps }))
                        }
                    };
                    let _ = app_handle.emit(name, payload);
                }
            });

            log::info!("LinkALL 桌面被控端已就绪");
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_device_info,
            commands::get_config,
            commands::update_config,
            commands::login,
            commands::logout,
            commands::start_service,
            commands::stop_service,
            commands::pause_service,
            commands::resume_service,
            commands::get_service_status,
            commands::reset_device_id,
            commands::reset_device_code,
            commands::set_autostart,
            commands::get_autostart,
            commands::get_auth_status,
            commands::get_user_info,
        ])
        .run(tauri::generate_context!())
        .expect("运行 Tauri 应用时出错");
}
