// LinkALL 桌面被控端 - Tauri 2 应用库
// 声明所有模块并提供 run() 入口函数。
// 同时承载被控端（DaemonState）与控制端（ControlState）能力。
mod auth;
mod autostart;
mod capture;
mod commands;
mod config;
mod control;
mod daemon;
mod device;
mod encoder;
mod input;
mod privacy;
mod tray;
mod webrtc;

use tauri::{Emitter, Manager};
use tokio::sync::mpsc;

use control::state::{ControlEvent, ControlState};
use daemon::{DaemonEvent, DaemonState};

/// 应用入口：初始化日志、数据库、守护进程、托盘，启动 Tauri 事件循环。
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // 初始化日志（写入文件 + 控制台）
    init_logger();

    // 安装 panic hook：捕获崩溃 backtrace 写入日志文件
    install_panic_hook();

    log::info!("LinkALL 桌面被控端启动中...");

    // 创建守护进程事件通道
    let (event_tx, mut event_rx) = mpsc::unbounded_channel::<DaemonEvent>();
    // 创建控制端事件通道
    let (control_tx, mut control_rx) = mpsc::unbounded_channel::<ControlEvent>();

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

            // 初始化控制端状态（与 DaemonState 平级但独立）
            // 同一进程可同时作为被控端与控制端
            let control_state = ControlState::new(control_tx);
            app.manage(control_state);

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

            // 启动控制端事件转发任务（→ Tauri 前端事件）
            // 将 ControlEvent 转为前端可订阅的事件名与 payload
            let control_app_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                while let Some(evt) = control_rx.recv().await {
                    let (name, payload) = match evt {
                        ControlEvent::Connected => {
                            ("control-connected", serde_json::Value::Null)
                        }
                        ControlEvent::Disconnected => {
                            ("control-disconnected", serde_json::Value::Null)
                        }
                        ControlEvent::Failed(msg) => {
                            ("control-error", serde_json::json!({ "message": msg }))
                        }
                        ControlEvent::Stats(stats) => {
                            ("peer-stats", serde_json::to_value(&stats).unwrap_or(serde_json::Value::Null))
                        }
                        ControlEvent::ConnectionRequest(req) => {
                            ("connection-request", serde_json::to_value(&req).unwrap_or(serde_json::Value::Null))
                        }
                        ControlEvent::SignalingOpen => {
                            ("signaling-open", serde_json::Value::Null)
                        }
                    };
                    let _ = control_app_handle.emit(name, payload);
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
            commands::export_logs,
            // 控制端命令
            commands::connect_to_device,
            commands::send_control_event,
            commands::get_peer_stats,
            commands::disconnect_peer,
            commands::discover_devices,
            commands::get_connection_requests,
            commands::respond_connection_request,
        ])
        .run(tauri::generate_context!())
        .expect("运行 Tauri 应用时出错");
}

/// 初始化日志：同时输出到文件和控制台
fn init_logger() {
    use env_logger::{Builder, Target};
    use std::fs;

    // 日志目录：<data_dir>/LinkALL/logs/
    let log_dir = dirs::data_dir()
        .map(|d| d.join("LinkALL").join("logs"))
        .unwrap_or_else(|| std::path::PathBuf::from("logs"));
    let _ = fs::create_dir_all(&log_dir);
    let log_file = log_dir.join("linkall.log");

    let mut builder = Builder::from_env(env_logger::Env::default().default_filter_or("info"));
    builder.format_timestamp_secs();

    // 尝试打开日志文件作为额外输出目标
    match fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_file)
    {
        Ok(file) => {
            let target = Target::Pipe(Box::new(file));
            builder.target(target);
        }
        Err(e) => {
            eprintln!("无法打开日志文件 {:?}：{}", log_file, e);
        }
    }

    let _ = builder.try_init();
}

/// 安装 panic hook：捕获崩溃信息并写入日志文件
fn install_panic_hook() {
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        // 获取 backtrace
        let backtrace = std::backtrace::Backtrace::force_capture();
        let payload = info.payload();
        let payload_str = if let Some(s) = payload.downcast_ref::<&str>() {
            *s
        } else if let Some(s) = payload.downcast_ref::<String>() {
            s.as_str()
        } else {
            "<未知 panic 载荷>"
        };
        let location = info
            .location()
            .map(|l| format!("{}:{}:{}", l.file(), l.line(), l.column()))
            .unwrap_or_else(|| "<未知位置>".to_string());

        // 写入日志文件
        let crash_msg = format!(
            "=== PANIC ===\n位置: {}\n消息: {}\nBacktrace:\n{}\n=============",
            location, payload_str, backtrace
        );
        log::error!("{}", crash_msg);

        // 同时写入崩溃日志文件
        if let Some(data_dir) = dirs::data_dir() {
            let crash_path = data_dir.join("LinkALL").join("logs").join("crash.log");
            let _ = std::fs::write(&crash_path, &format!(
                "{}\n{}\n",
                chrono::Local::now().format("%Y-%m-%d %H:%M:%S"),
                crash_msg
            ));
        }

        // 调用默认 hook（打印到 stderr）
        default_hook(info);
    }));
}
