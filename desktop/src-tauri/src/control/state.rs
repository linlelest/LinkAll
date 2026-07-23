// 控制端会话状态
// 管理一次"作为控制端"的远程控制会话：远端设备信息、PeerSession、统计、连接请求缓存。
// 与 DaemonState 平级但独立：同一进程可同时作为被控端（DaemonState）与控制端（ControlState）。
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use anyhow::Result;
use serde::Serialize;
use tokio::sync::{mpsc, Mutex};

use crate::daemon::DaemonState;
use crate::webrtc::control::SettingsSnapshot;

use super::peer::{ControlPeer, ControlPeerEvent};

/// 控制会话统计
#[derive(Debug, Clone, Serialize)]
pub struct PeerStats {
    /// RTT（毫秒）
    pub rtt_ms: i32,
    /// 丢包率（百分比，0-100）
    pub packet_loss: f64,
    /// 接收帧率
    pub fps: u32,
    /// 已连接时长（秒）
    pub duration_secs: u64,
}

/// 同账号设备发现响应项
#[derive(Debug, Clone, Serialize)]
pub struct DeviceSummary {
    pub device_id: String,
    pub device_name: String,
    pub platform: String,
    pub version: String,
    pub online_status: String,
    pub last_seen: i64,
    pub paired: bool,
}

/// 待确认的连接请求
#[derive(Debug, Clone, Serialize)]
pub struct ConnectionRequest {
    pub request_id: String,
    pub session_id: String,
    pub requester_ip: String,
    pub mode: String,
    pub created_at: i64,
}

/// 控制端会话状态
pub struct ControlState {
    /// 当前控制 Peer 会话
    peer: Mutex<Option<Arc<ControlPeer>>>,
    /// 连接中标志
    connecting: AtomicBool,
    /// 已连接标志
    connected: AtomicBool,
    /// 连接开始时间戳（Unix 秒）
    started_at: Arc<Mutex<u64>>,
    /// 最新统计
    stats: Arc<Mutex<PeerStats>>,
    /// 事件出口（向前端派发 control-* 事件）
    event_tx: mpsc::UnboundedSender<ControlEvent>,
    /// 待确认的连接请求缓存（被控端收到的匿名请求）
    pending_requests: Mutex<Vec<ConnectionRequest>>,
}

/// 控制端事件（向上层 UI 派发）
#[derive(Debug, Clone)]
pub enum ControlEvent {
    /// 连接已建立
    Connected,
    /// 连接已断开
    Disconnected,
    /// 连接失败
    Failed(String),
    /// 统计更新
    Stats(PeerStats),
    /// 收到连接请求（被控端视角）
    ConnectionRequest(ConnectionRequest),
    /// 信令通道就绪
    SignalingOpen,
}

impl ControlState {
    /// 创建控制端状态（事件出口由调用方提供，用于转发到 Tauri 前端）
    pub fn new(event_tx: mpsc::UnboundedSender<ControlEvent>) -> Self {
        Self {
            peer: Mutex::new(None),
            connecting: AtomicBool::new(false),
            connected: AtomicBool::new(false),
            started_at: Arc::new(Mutex::new(0)),
            stats: Arc::new(Mutex::new(PeerStats {
                rtt_ms: 0,
                packet_loss: 0.0,
                fps: 0,
                duration_secs: 0,
            })),
            event_tx,
            pending_requests: Mutex::new(Vec::new()),
        }
    }

    /// 作为控制端发起连接
    /// 1) 从 DaemonState 读取服务器地址与 JWT
    /// 2) 创建 ControlSignalingConfig
    /// 3) 启动 ControlPeer（创建 Offer → 信令发送 → 等待 Answer）
    pub async fn connect(
        &self,
        daemon: &DaemonState,
        device_id: &str,
        device_code: &str,
        mode: &str,
    ) -> Result<()> {
        if self.connecting.load(Ordering::Relaxed) {
            anyhow::bail!("已在连接中，请先断开当前会话");
        }
        if self.connected.load(Ordering::Relaxed) {
            anyhow::bail!("已存在活动控制会话，请先断开");
        }

        // 读取配置（服务器地址、视频设置）
        let cfg = daemon.config().await;
        // 读取 JWT（同账号模式必须，匿名模式可为空）
        let token = daemon.current_token_optional().await.unwrap_or_default();

        // 解析连接模式
        let sig_mode = match mode {
            "anonymous" => super::signaling::ControlSignalingMode::Anonymous,
            "same_account" => {
                if token.is_empty() {
                    anyhow::bail!("同账号模式需先登录");
                }
                super::signaling::ControlSignalingMode::SameAccount
            }
            "device_code" => super::signaling::ControlSignalingMode::DeviceCode,
            other => anyhow::bail!("未知连接模式：{}", other),
        };

        // 构造信令配置
        let sig_cfg = super::signaling::ControlSignalingConfig {
            server_url: cfg.server_url.clone(),
            token: token.clone(),
            target_device_id: device_id.to_string(),
            target_device_code: device_code.to_string(),
            mode: sig_mode,
            ..Default::default()
        };

        // 构造初始设置快照（来自本地视频配置）
        let settings = SettingsSnapshot {
            scale: cfg.scale,
            fps: cfg.target_fps,
            max_bitrate: cfg.max_bitrate,
            codec: cfg.codec_video.clone(),
            privacy_screen: false,
        };

        self.connecting.store(true, Ordering::Relaxed);
        log::info!(
            "控制端发起连接：device={} mode={} server={}",
            device_id, mode, cfg.server_url
        );

        // 创建 Peer 会话
        let (peer, mut event_rx) = ControlPeer::new(sig_cfg, settings).await?;
        peer.start_signaling().await?;
        peer.create_offer().await?;

        // 保存 peer 引用
        *self.peer.lock().await = Some(peer.clone());

        // 启动事件监控任务
        let event_tx = self.event_tx.clone();
        let stats_tx = self.event_tx.clone();
        let stats_lock = self.stats.clone();
        let started_at = self.started_at.clone();
        let connected = AtomicBool::new(false);
        let connected_ref = std::sync::Arc::new(AtomicBool::new(false));
        let connected_for_task = connected_ref.clone();
        tokio::spawn(async move {
            let start_ts = chrono::Utc::now().timestamp() as u64;
            *started_at.lock().await = start_ts;
            while let Some(evt) = event_rx.recv().await {
                match evt {
                    ControlPeerEvent::Connected => {
                        connected_for_task.store(true, Ordering::Relaxed);
                        let _ = event_tx.send(ControlEvent::Connected);
                    }
                    ControlPeerEvent::Disconnected => {
                        let _ = event_tx.send(ControlEvent::Disconnected);
                        break;
                    }
                    ControlPeerEvent::Failed(msg) => {
                        let _ = event_tx.send(ControlEvent::Failed(msg));
                        break;
                    }
                    ControlPeerEvent::Stats { rtt_ms, fps, packet_loss } => {
                        let stats = PeerStats {
                            rtt_ms,
                            packet_loss,
                            fps,
                            duration_secs: (chrono::Utc::now().timestamp() as u64).saturating_sub(start_ts),
                        };
                        *stats_lock.lock().await = stats.clone();
                        let _ = stats_tx.send(ControlEvent::Stats(stats));
                    }
                    ControlPeerEvent::SignalingOpen => {
                        let _ = event_tx.send(ControlEvent::SignalingOpen);
                    }
                }
            }
            connected_for_task.store(false, Ordering::Relaxed);
            let _ = stats_tx.send(ControlEvent::Disconnected);
        });

        self.connecting.store(false, Ordering::Relaxed);
        let _ = connected;
        let _ = connected_ref;
        Ok(())
    }

    /// 通过 DataChannel 发送控制指令（JSON 字符串）
    pub async fn send_control_event(&self, event_json: String) -> Result<()> {
        let peer = self.peer.lock().await;
        let peer = peer.as_ref().ok_or_else(|| anyhow::anyhow!("无活动控制会话"))?;
        peer.send_data_channel(event_json).await
    }

    /// 获取最新统计
    pub async fn stats(&self) -> PeerStats {
        self.stats.lock().await.clone()
    }

    /// 断开控制会话
    pub async fn disconnect(&self) -> Result<()> {
        if let Some(peer) = self.peer.lock().await.take() {
            peer.close().await;
        }
        self.connecting.store(false, Ordering::Relaxed);
        self.connected.store(false, Ordering::Relaxed);
        log::info!("控制会话已断开");
        Ok(())
    }

    /// 是否已连接
    pub fn is_connected(&self) -> bool {
        self.connected.load(Ordering::Relaxed)
    }

    /// 添加待确认的连接请求（由被控端信令事件回调调用）
    pub async fn add_connection_request(&self, req: ConnectionRequest) {
        self.pending_requests.lock().await.push(req);
    }

    /// 获取所有待确认的连接请求
    pub async fn connection_requests(&self) -> Vec<ConnectionRequest> {
        self.pending_requests.lock().await.clone()
    }

    /// 移除指定的连接请求
    pub async fn remove_connection_request(&self, request_id: &str) {
        self.pending_requests.lock().await.retain(|r| r.request_id != request_id);
    }
}

/// 同账号设备发现：调用服务端 GET /api/devices/discover
pub async fn discover_devices(daemon: &DaemonState) -> Result<Vec<DeviceSummary>> {
    let cfg = daemon.config().await;
    let token = daemon.current_token_optional().await
        .ok_or_else(|| anyhow::anyhow!("同账号设备发现需先登录"))?;

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(10))
        .build()?;
    let url = format!("{}/api/devices/discover", cfg.server_url.trim_end_matches('/'));
    let resp = client
        .get(&url)
        .query(&[("online", "true")])
        .header("Authorization", format!("Bearer {}", token))
        .send()
        .await
        .map_err(|e| anyhow::anyhow!("设备发现请求失败：{}", e))?;

    let status = resp.status();
    let body = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        anyhow::bail!("设备发现失败 (HTTP {}): {}", status, body);
    }

    #[derive(serde::Deserialize)]
    struct ApiResponse {
        code: i32,
        message: Option<String>,
        data: Option<DevicesData>,
    }
    #[derive(serde::Deserialize)]
    struct DevicesData {
        #[serde(default)]
        devices: Vec<DeviceRaw>,
        #[serde(default)]
        total: i32,
    }
    #[derive(serde::Deserialize)]
    struct DeviceRaw {
        #[serde(rename = "deviceId")]
        device_id: String,
        #[serde(rename = "deviceName", default)]
        device_name: String,
        #[serde(default)]
        platform: String,
        #[serde(default)]
        version: String,
        #[serde(rename = "onlineStatus", default)]
        online_status: String,
        #[serde(rename = "lastSeen", default)]
        last_seen: i64,
        #[serde(default)]
        paired: bool,
    }

    let api: ApiResponse = serde_json::from_str(&body)
        .map_err(|e| anyhow::anyhow!("解析设备发现响应失败：{} body={}", e, body))?;
    if api.code != 0 {
        anyhow::bail!("{}", api.message.unwrap_or_else(|| "设备发现失败".into()));
    }
    let data = api.data.ok_or_else(|| anyhow::anyhow!("响应缺少 data 字段"))?;
    let result = data.devices.into_iter().map(|d| DeviceSummary {
        device_id: d.device_id,
        device_name: d.device_name,
        platform: d.platform,
        version: d.version,
        online_status: d.online_status,
        last_seen: d.last_seen,
        paired: d.paired,
    }).collect();
    Ok(result)
}

/// 响应连接请求：调用服务端 POST /api/connect/anonymous/confirm
pub async fn respond_connection_request(
    daemon: &DaemonState,
    request_id: String,
    action: String,
) -> Result<()> {
    let cfg = daemon.config().await;
    let dev = daemon.device_info().await;

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(10))
        .build()?;
    let url = format!("{}/api/connect/anonymous/confirm", cfg.server_url.trim_end_matches('/'));

    #[derive(serde::Serialize)]
    struct ConfirmReq {
        #[serde(rename = "deviceId")]
        device_id: String,
        #[serde(rename = "deviceCode")]
        device_code: String,
        #[serde(rename = "sessionId")]
        session_id: String,
        action: String,
    }
    let req = ConfirmReq {
        device_id: dev.device_id,
        device_code: dev.device_code,
        session_id: request_id,
        action,
    };
    let resp = client
        .post(&url)
        .json(&req)
        .send()
        .await
        .map_err(|e| anyhow::anyhow!("响应连接请求失败：{}", e))?;
    let status = resp.status();
    let body = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        anyhow::bail!("响应连接请求失败 (HTTP {}): {}", status, body);
    }
    Ok(())
}
