// 守护进程模块
// 管理被控服务的生命周期：启动（截屏→编码→WebRTC 管线）、停止、暂停。
// 在后台 tokio 任务中运行，通过 AppState 与前端通信。
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use anyhow::Result;
use rusqlite::Connection;
use tokio::sync::{mpsc, Mutex};
use tokio::time::Duration;

use crate::auth;
use crate::capture::{self, Capturer};
use crate::config::{self, AppConfig};
use crate::device::{self, DeviceInfo};
use crate::encoder::{self, VideoCodec};
use crate::webrtc::peer::{PeerEvent, PeerSession};
use crate::webrtc::signaling::SignalingConfig;
use crate::webrtc::control::SettingsSnapshot;

/// 守护进程状态
pub struct DaemonState {
    /// SQLite 数据库连接
    db: Mutex<Connection>,
    /// 应用配置
    config: Mutex<AppConfig>,
    /// 设备身份
    device: Mutex<DeviceInfo>,
    /// 当前 Peer 会话
    peer: Mutex<Option<Arc<PeerSession>>>,
    /// 截屏循环停止标志
    capture_stop: Arc<AtomicBool>,
    /// 编码循环停止标志
    encode_stop: Arc<AtomicBool>,
    /// 服务是否运行中
    running: AtomicBool,
    /// 服务是否暂停
    paused: AtomicBool,
    /// 事件出口（向上层 UI 派发）
    event_tx: mpsc::UnboundedSender<DaemonEvent>,
}

/// 守护进程事件
#[derive(Debug, Clone)]
pub enum DaemonEvent {
    /// 服务已启动
    Started,
    /// 服务已停止
    Stopped,
    /// 服务已暂停
    Paused,
    /// 服务已恢复
    Resumed,
    /// Peer 连接状态
    PeerConnected,
    PeerDisconnected,
    /// 错误
    Error(String),
    /// 统计信息
    Stats { rtt_ms: i32, fps: u32 },
}

impl DaemonState {
    /// 初始化守护进程状态：打开数据库、加载配置、确保设备身份
    pub fn new(event_tx: mpsc::UnboundedSender<DaemonEvent>) -> Result<Self> {
        let conn = config::open_db()?;
        let cfg = config::load_config(&conn)?;
        let dev = device::ensure_device(&conn)?;
        log::info!(
            "守护进程初始化完成：设备ID={} 服务器={}",
            dev.device_id,
            cfg.server_url
        );
        Ok(Self {
            db: Mutex::new(conn),
            config: Mutex::new(cfg),
            device: Mutex::new(dev),
            peer: Mutex::new(None),
            capture_stop: Arc::new(AtomicBool::new(false)),
            encode_stop: Arc::new(AtomicBool::new(false)),
            running: AtomicBool::new(false),
            paused: AtomicBool::new(false),
            event_tx,
        })
    }

    /// 启动被控服务
    pub async fn start(&self) -> Result<()> {
        if self.running.load(Ordering::Relaxed) {
            log::warn!("服务已在运行");
            return Ok(());
        }

        let cfg = self.config.lock().await.clone();
        let dev = self.device.lock().await.clone();

        // 获取 JWT 令牌
        let token = {
            let conn = self.db.lock().await;
            auth::current_token(&conn)?
                .ok_or_else(|| anyhow::anyhow!("未登录，请先在设置中登录"))?
        };

        // 创建信令配置
        let sig_cfg = SignalingConfig {
            server_url: cfg.server_url.clone(),
            token,
            device_id: dev.device_id.clone(),
            ..Default::default()
        };

        // 创建设置快照
        let settings = SettingsSnapshot {
            scale: cfg.scale,
            fps: cfg.target_fps,
            max_bitrate: cfg.max_bitrate,
            codec: cfg.codec_video.clone(),
            privacy_screen: false,
        };

        // 创建 Peer 会话（PeerSession::new 已返回 Arc<PeerSession>）
        let (peer, mut event_rx) = PeerSession::new(sig_cfg, settings).await?;

        // 启动信令客户端
        peer.start_signaling().await?;

        // 启动截屏 → 编码 → 推流管线
        self.start_pipeline(peer.clone(), &cfg).await?;

        // 保存 peer 引用
        *self.peer.lock().await = Some(peer);

        self.running.store(true, Ordering::Relaxed);
        self.paused.store(false, Ordering::Relaxed);

        // 事件监控任务
        let event_tx = self.event_tx.clone();
        let peer_clone = self.peer.lock().await.clone();
        tokio::spawn(async move {
            let _ = peer_clone;
            while let Some(evt) = event_rx.recv().await {
                match evt {
                    PeerEvent::Connected => {
                        let _ = event_tx.send(DaemonEvent::PeerConnected);
                    }
                    PeerEvent::Disconnected => {
                        let _ = event_tx.send(DaemonEvent::PeerDisconnected);
                    }
                    PeerEvent::Failed(msg) => {
                        let _ = event_tx.send(DaemonEvent::Error(msg));
                    }
                    PeerEvent::Stats { rtt_ms } => {
                        let _ = event_tx.send(DaemonEvent::Stats { rtt_ms, fps: 0 });
                    }
                    _ => {}
                }
            }
        });

        let _ = self.event_tx.send(DaemonEvent::Started);
        log::info!("被控服务已启动");
        Ok(())
    }

    /// 启动截屏→编码→推流管线
    async fn start_pipeline(&self, peer: Arc<PeerSession>, cfg: &AppConfig) -> Result<()> {
        // 创建截屏器
        let capturer = capture::new_capturer()?;
        let (w, h) = capturer.size();

        // 创建编码器
        let codec = VideoCodec::parse(&cfg.codec_video);
        let bitrate = cfg.max_bitrate;
        let mut video_encoder = encoder::new_encoder(codec, w, h, bitrate)?;

        // 截屏 → 编码 → peer.send_frame() 管线
        let (frame_tx, mut frame_rx) = mpsc::channel(8);
        let capture_stop = self.capture_stop.clone();
        let fps = cfg.target_fps;

        // 截屏线程
        std::thread::spawn(move || {
            capture::capture_loop(capturer, fps, frame_tx, capture_stop);
        });

        // 编码→推流任务
        let encode_stop = self.encode_stop.clone();
        let peer_frame = peer.clone();
        tokio::spawn(async move {
            log::info!("编码→推流管线启动");
            loop {
                if encode_stop.load(Ordering::Relaxed) {
                    log::info!("编码循环收到停止信号");
                    break;
                }
                match tokio::time::timeout(Duration::from_millis(100), frame_rx.recv()).await {
                    Ok(Some(frame)) => {
                        match video_encoder.encode(&frame.data, frame.width, frame.height) {
                            Ok(encoded) => {
                                if let Err(e) = peer_frame.send_frame(encoded).await {
                                    log::warn!("推送视频帧失败：{:?}", e);
                                }
                            }
                            Err(e) => {
                                log::warn!("编码失败：{:?}", e);
                            }
                        }
                    }
                    Ok(None) => {
                        log::info!("截屏通道已关闭，退出编码循环");
                        break;
                    }
                    Err(_) => {
                        // 超时：继续循环检查停止标志
                    }
                }
            }
            log::info!("编码→推流管线已停止");
        });

        Ok(())
    }

    /// 停止被控服务
    pub async fn stop(&self) -> Result<()> {
        if !self.running.load(Ordering::Relaxed) {
            return Ok(());
        }
        // 停止截屏与编码
        self.capture_stop.store(true, Ordering::Relaxed);
        self.encode_stop.store(true, Ordering::Relaxed);

        // 关闭 Peer 会话
        if let Some(peer) = self.peer.lock().await.take() {
            peer.close().await;
        }

        // 重置停止标志以备下次启动
        self.capture_stop = Arc::new(AtomicBool::new(false));
        self.encode_stop = Arc::new(AtomicBool::new(false));

        self.running.store(false, Ordering::Relaxed);
        self.paused.store(false, Ordering::Relaxed);
        let _ = self.event_tx.send(DaemonEvent::Stopped);
        log::info!("被控服务已停止");
        Ok(())
    }

    /// 暂停服务（断开 Peer 但保持登录状态）
    pub async fn pause(&self) -> Result<()> {
        if !self.running.load(Ordering::Relaxed) || self.paused.load(Ordering::Relaxed) {
            return Ok(());
        }
        // 停止截屏与编码
        self.capture_stop.store(true, Ordering::Relaxed);
        self.encode_stop.store(true, Ordering::Relaxed);

        // 暂停时不关闭信令，仅停止推流
        self.paused.store(true, Ordering::Relaxed);
        let _ = self.event_tx.send(DaemonEvent::Paused);
        log::info!("被控服务已暂停");
        Ok(())
    }

    /// 恢复服务
    pub async fn resume(&self) -> Result<()> {
        if !self.paused.load(Ordering::Relaxed) {
            return Ok(());
        }
        // 重置停止标志并重启管线
        self.capture_stop.store(false, Ordering::Relaxed);
        self.encode_stop.store(false, Ordering::Relaxed);

        let cfg = self.config.lock().await.clone();
        if let Some(peer) = self.peer.lock().await.as_ref() {
            self.start_pipeline(peer.clone(), &cfg).await?;
        }

        self.paused.store(false, Ordering::Relaxed);
        let _ = self.event_tx.send(DaemonEvent::Resumed);
        log::info!("被控服务已恢复");
        Ok(())
    }

    /// 检查服务是否运行中
    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::Relaxed)
    }

    /// 检查服务是否暂停
    pub fn is_paused(&self) -> bool {
        self.paused.load(Ordering::Relaxed)
    }

    /// 获取设备信息
    pub async fn device_info(&self) -> DeviceInfo {
        self.device.lock().await.clone()
    }

    /// 获取配置
    pub async fn config(&self) -> AppConfig {
        self.config.lock().await.clone()
    }

    /// 更新配置
    pub async fn update_config(&self, key: &str, value: &str) -> Result<()> {
        let conn = self.db.lock().await;
        config::save_setting(&conn, key, value)?;
        // 重新加载配置
        let new_cfg = config::load_config(&conn)?;
        drop(conn);
        *self.config.lock().await = new_cfg;
        log::info!("配置已更新：{} = {}", key, value);
        Ok(())
    }

    /// 登录
    pub async fn login(&self, username: &str, password: &str) -> Result<()> {
        let cfg = self.config.lock().await;
        let server_url = cfg.server_url.clone();
        drop(cfg);

        let login_data = auth::login(&server_url, username, password).await?;
        let conn = self.db.lock().await;
        auth::save_credentials(&conn, &login_data)?;
        // 绑定设备归属用户
        device::bind_owner(&conn, login_data.user.id)?;
        Ok(())
    }

    /// 登出
    pub async fn logout(&self) -> Result<()> {
        let conn = self.db.lock().await;
        auth::logout(&conn)?;
        Ok(())
    }

    /// 重置设备码
    pub async fn reset_device_code(&self) -> Result<String> {
        let conn = self.db.lock().await;
        let new_code = device::reset_device_code(&conn)?;
        let mut dev = self.device.lock().await;
        dev.device_code = new_code.clone();
        Ok(new_code)
    }

    /// 重置设备编号
    pub async fn reset_device_id(&self) -> Result<String> {
        let conn = self.db.lock().await;
        let new_id = device::reset_device_id(&conn)?;
        let mut dev = self.device.lock().await;
        dev.device_id = new_id.clone();
        Ok(new_id)
    }

    /// 检查是否已登录且 JWT 有效
    pub async fn is_logged_in(&self) -> Result<bool> {
        let conn = self.db.lock().await;
        match auth::load_credentials(&conn)? {
            Some(c) => Ok(auth::is_token_valid(&c)),
            None => Ok(false),
        }
    }

    /// 获取当前用户信息（从本地凭据读取）
    pub async fn current_user(&self) -> Result<Option<auth::UserInfo>> {
        let conn = self.db.lock().await;
        match auth::load_credentials(&conn)? {
            Some(c) if auth::is_token_valid(&c) => {
                Ok(Some(auth::UserInfo {
                    id: c.user_id,
                    username: c.username,
                    role: c.role,
                    status: "active".into(),
                    banned: false,
                    device_count: 0,
                    created_at: 0,
                    last_login_ip: String::new(),
                }))
            }
            _ => Ok(None),
        }
    }
}
