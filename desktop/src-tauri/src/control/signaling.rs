// 控制端 WebSocket 信令客户端
// 连接服务端 /ws/signaling?token=<JWT>，发送 connect 消息（含目标设备编号与连接模式），
// 处理 connect_ack / sdp_answer / ice_candidate / ice_complete / bye / pong / error。
// 内置心跳 15s、指数退避重连（1s→2s→4s→…→max 30s）、会话超时 30min。
// 与被控端 signaling.rs 的 SignalingClient 类似，但消息流方向相反（控制端发 Offer、收 Answer）。
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tokio::sync::{mpsc, oneshot, Mutex};
use tokio::time::{interval_at, Instant};
use tokio_tungstenite::tungstenite::Message;
use url::Url;

/// 控制端连接模式
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ControlSignalingMode {
    /// 匿名连接（需被控端确认）
    Anonymous,
    /// 同账号连接（需 JWT）
    SameAccount,
    /// 设备码连接
    DeviceCode,
}

impl ControlSignalingMode {
    /// 转为信令消息中的 mode 字段值
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Anonymous => "anonymous",
            Self::SameAccount => "same_account",
            Self::DeviceCode => "device_code",
        }
    }
}

/// 信令消息类型
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum SignalingType {
    Connect,
    #[serde(rename = "connect_ack")]
    ConnectAck,
    #[serde(rename = "sdp_offer")]
    SdpOffer,
    #[serde(rename = "sdp_answer")]
    SdpAnswer,
    #[serde(rename = "ice_candidate")]
    IceCandidate,
    #[serde(rename = "ice_complete")]
    IceComplete,
    Bye,
    Ping,
    Pong,
    Error,
}

/// 信令外层消息（与 shared/messages.json SignalEnvelope 对齐）
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignalingEnvelope {
    #[serde(rename = "type")]
    pub kind: SignalingType,
    pub ts: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub session_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub from: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub to: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub device_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub mode: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub token: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub device_code: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ok: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub require_confirm: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sdp: Option<SdpPayload>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub candidate: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub client_ts: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub server_ts: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub rtt: Option<i32>,
}

/// SDP 载荷
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SdpPayload {
    #[serde(rename = "type")]
    pub kind: String,
    pub sdp: String,
}

/// 信令事件：向 ControlPeer 派发
#[derive(Debug, Clone)]
pub enum ControlSignalingEvent {
    /// WebSocket 已开启并已发送 connect
    Open,
    /// 收到 connect_ack（ok / sessionId / requireConfirm）
    ConnectAck { ok: bool, session_id: Option<String>, require_confirm: Option<bool> },
    /// 收到 SDP Answer
    SdpAnswer(SdpPayload),
    /// 收到 ICE Candidate
    IceCandidate(Value),
    /// ICE 收集完成
    IceComplete,
    /// 对端 bye
    Bye(String),
    /// 心跳响应（rtt 毫秒）
    Pong(i32),
    /// 错误
    Error(String, Option<String>),
    /// 连接关闭
    Closed,
}

/// 控制端信令配置
#[derive(Debug, Clone)]
pub struct ControlSignalingConfig {
    /// 服务端 http(s) URL
    pub server_url: String,
    /// JWT 令牌（同账号模式必填）
    pub token: String,
    /// 目标设备 ID（12 位编号）
    pub target_device_id: String,
    /// 目标设备码
    pub target_device_code: String,
    /// 连接模式
    pub mode: ControlSignalingMode,
    /// 心跳间隔（秒）
    pub heartbeat_interval: u64,
    /// 初始重连延迟（毫秒）
    pub reconnect_initial_ms: u64,
    /// 最大重连延迟（毫秒）
    pub reconnect_max_ms: u64,
    /// 会话超时（秒）
    pub session_timeout: u64,
}

impl Default for ControlSignalingConfig {
    fn default() -> Self {
        Self {
            server_url: "http://localhost:8080".into(),
            token: String::new(),
            target_device_id: String::new(),
            target_device_code: String::new(),
            mode: ControlSignalingMode::DeviceCode,
            heartbeat_interval: 15,
            reconnect_initial_ms: 1_000,
            reconnect_max_ms: 30_000,
            session_timeout: 30 * 60,
        }
    }
}

/// 信令客户端内部状态
struct State {
    /// 发送队列：待发往 WebSocket 的 JSON 字符串
    tx_out: Option<mpsc::UnboundedSender<String>>,
    /// 关闭标志（用户主动 close）
    manually_closed: bool,
    /// 当前重连延迟
    reconnect_delay: u64,
}

impl State {
    fn new() -> Self {
        Self {
            tx_out: None,
            manually_closed: false,
            reconnect_delay: 1_000,
        }
    }
}

/// 控制端 WebSocket 信令客户端
pub struct ControlSignalingClient {
    cfg: ControlSignalingConfig,
    state: Arc<Mutex<State>>,
    event_tx: mpsc::UnboundedSender<ControlSignalingEvent>,
    shutdown_tx: Mutex<Option<oneshot::Sender<()>>>,
}

impl ControlSignalingClient {
    pub fn new(
        cfg: ControlSignalingConfig,
        event_tx: mpsc::UnboundedSender<ControlSignalingEvent>,
    ) -> Self {
        Self {
            cfg,
            state: Arc::new(Mutex::new(State::new())),
            event_tx,
            shutdown_tx: Mutex::new(None),
        }
    }

    /// 启动信令客户端
    pub async fn start(&self) -> Result<()> {
        let cfg = self.cfg.clone();
        let state = self.state.clone();
        let event_tx = self.event_tx.clone();
        let (shutdown_tx, shutdown_rx) = oneshot::channel::<()>();
        *self.shutdown_tx.lock().await = Some(shutdown_tx);

        tokio::spawn(async move {
            if let Err(e) = run_loop(cfg, state, event_tx.clone(), shutdown_rx).await {
                log::error!("控制端信令客户端运行循环异常退出：{:?}", e);
                let _ = event_tx.send(ControlSignalingEvent::Closed);
            }
        });
        Ok(())
    }

    /// 发送一条信令消息
    pub async fn send(&self, env: SignalingEnvelope) -> Result<()> {
        let tx = {
            let s = self.state.lock().await;
            match s.tx_out.clone() {
                Some(t) => t,
                None => anyhow::bail!("信令通道未连接"),
            }
        };
        let text = serde_json::to_string(&env)?;
        tx.send(text).context("信令发送队列已关闭")?;
        Ok(())
    }

    /// 主动关闭，不再重连
    pub async fn close(&self) {
        {
            let mut s = self.state.lock().await;
            s.manually_closed = true;
            s.tx_out = None;
        }
        if let Some(tx) = self.shutdown_tx.lock().await.take() {
            let _ = tx.send(());
        }
    }
}

/// 主运行循环：连接 → 收发 → 断开 → 退避重连
async fn run_loop(
    cfg: ControlSignalingConfig,
    state: Arc<Mutex<State>>,
    event_tx: mpsc::UnboundedSender<ControlSignalingEvent>,
    mut shutdown_rx: oneshot::Receiver<()>,
) -> Result<()> {
    loop {
        if state.lock().await.manually_closed {
            break;
        }
        let url = build_ws_url(&cfg.server_url, &cfg.token);
        log::info!("控制端信令客户端连接：{}", url);
        let (out_tx, mut out_rx) = mpsc::unbounded_channel::<String>();
        state.lock().await.tx_out = Some(out_tx);

        match connect_and_serve(&cfg, &state, &event_tx, &mut out_rx, &mut shutdown_rx).await {
            Ok(_) => log::info!("控制端信令会话结束"),
            Err(e) => log::warn!("控制端信令会话错误：{:?}", e),
        }
        state.lock().await.tx_out = None;
        let _ = event_tx.send(ControlSignalingEvent::Closed);

        if state.lock().await.manually_closed {
            break;
        }
        let delay_ms = {
            let mut s = state.lock().await;
            let d = s.reconnect_delay;
            s.reconnect_delay = (s.reconnect_delay * 2).min(cfg.reconnect_max_ms);
            d
        };
        log::info!("{}ms 后重连控制端信令服务器", delay_ms);
        tokio::select! {
            _ = tokio::time::sleep(Duration::from_millis(delay_ms)) => {}
            _ = &mut shutdown_rx => break,
        }
    }
    Ok(())
}

/// 单次连接的服务循环
async fn connect_and_serve(
    cfg: &ControlSignalingConfig,
    state: &Arc<Mutex<State>>,
    event_tx: &mpsc::UnboundedSender<ControlSignalingEvent>,
    out_rx: &mut mpsc::UnboundedReceiver<String>,
    shutdown_rx: &mut oneshot::Receiver<()>,
) -> Result<()> {
    let (ws_stream, _resp) = tokio_tungstenite::connect_async(build_ws_url(&cfg.server_url, &cfg.token))
        .await
        .context("控制端 WebSocket 连接失败")?;
    log::info!("控制端信令 WebSocket 已连接");
    let _ = event_tx.send(ControlSignalingEvent::Open);
    state.lock().await.reconnect_delay = cfg.reconnect_initial_ms;

    let (mut ws_sink, mut ws_stream) = ws_stream.split();

    // 发送 connect 消息（控制端发起）
    let connect_msg = SignalingEnvelope {
        kind: SignalingType::Connect,
        ts: now_ms(),
        from: Some("controller".into()),
        to: Some(cfg.target_device_id.clone()),
        device_id: Some(cfg.target_device_id.clone()),
        mode: Some(cfg.mode.as_str().into()),
        token: if cfg.token.is_empty() { None } else { Some(cfg.token.clone()) },
        device_code: if cfg.target_device_code.is_empty() { None } else { Some(cfg.target_device_code.clone()) },
        ..Default::default()
    };
    ws_sink.send(Message::Text(serde_json::to_string(&connect_msg)?)).await?;

    // 心跳定时器
    let mut heartbeat = interval_at(
        Instant::now() + Duration::from_secs(cfg.heartbeat_interval),
        Duration::from_secs(cfg.heartbeat_interval),
    );
    let mut session_deadline = Instant::now() + Duration::from_secs(cfg.session_timeout);

    loop {
        tokio::select! {
            _ = &mut *shutdown_rx => {
                log::info!("收到关闭信号，断开控制端信令");
                let _ = ws_sink.send(Message::Close(None)).await;
                break;
            }
            _ = heartbeat.tick() => {
                let ping = SignalingEnvelope {
                    kind: SignalingType::Ping,
                    ts: now_ms(),
                    client_ts: Some(now_ms()),
                    ..Default::default()
                };
                if let Ok(text) = serde_json::to_string(&ping) {
                    if ws_sink.send(Message::Text(text)).await.is_err() {
                        break;
                    }
                }
            }
            msg = ws_stream.next() => {
                let msg = match msg {
                    Some(Ok(m)) => m,
                    Some(Err(e)) => {
                        log::warn!("控制端信令 WebSocket 错误：{}", e);
                        break;
                    }
                    None => break,
                };
                match msg {
                    Message::Text(text) => {
                        if let Err(e) = handle_text(text, event_tx, &mut session_deadline, cfg) {
                            log::warn!("处理控制端信令消息错误：{:?}", e);
                        }
                    }
                    Message::Binary(b) => {
                        if let Ok(s) = String::from_utf8(b.to_vec()) {
                            let _ = handle_text(s, event_tx, &mut session_deadline, cfg);
                        }
                    }
                    Message::Ping(p) => { let _ = ws_sink.send(Message::Pong(p)).await; }
                    Message::Pong(_) => {}
                    Message::Close(_) => {
                        log::info!("控制端信令对端关闭连接");
                        break;
                    }
                    Message::Frame(_) => {}
                }
                if Instant::now() > session_deadline {
                    log::warn!("控制端信令会话超时，断开");
                    let _ = event_tx.send(ControlSignalingEvent::Bye("session_timeout".into()));
                    break;
                }
            }
            out = out_rx.recv() => {
                match out {
                    Some(text) => {
                        if ws_sink.send(Message::Text(text)).await.is_err() {
                            break;
                        }
                    }
                    None => break,
                }
            }
        }
    }
    Ok(())
}

/// 处理单条文本信令
fn handle_text(
    text: String,
    event_tx: &mpsc::UnboundedSender<ControlSignalingEvent>,
    session_deadline: &mut Instant,
    cfg: &ControlSignalingConfig,
) -> Result<()> {
    let env: SignalingEnvelope = serde_json::from_str(&text)
        .with_context(|| format!("解析控制端信令消息失败：{}", text))?;
    *session_deadline = Instant::now() + Duration::from_secs(cfg.session_timeout);
    match env.kind {
        SignalingType::ConnectAck => {
            let _ = event_tx.send(ControlSignalingEvent::ConnectAck {
                ok: env.ok.unwrap_or(false),
                session_id: env.session_id,
                require_confirm: env.require_confirm,
            });
        }
        SignalingType::SdpAnswer => {
            if let Some(sdp) = env.sdp {
                let _ = event_tx.send(ControlSignalingEvent::SdpAnswer(sdp));
            }
        }
        SignalingType::IceCandidate => {
            if let Some(c) = env.candidate {
                let _ = event_tx.send(ControlSignalingEvent::IceCandidate(c));
            }
        }
        SignalingType::IceComplete => {
            let _ = event_tx.send(ControlSignalingEvent::IceComplete);
        }
        SignalingType::Bye => {
            let _ = event_tx.send(ControlSignalingEvent::Bye(env.reason.unwrap_or_default()));
        }
        SignalingType::Pong => {
            let rtt = env.rtt.unwrap_or(0);
            let _ = event_tx.send(ControlSignalingEvent::Pong(rtt));
        }
        SignalingType::Error => {
            let code = env.code.unwrap_or_else(|| "ERR_INTERNAL_ERROR".into());
            let _ = event_tx.send(ControlSignalingEvent::Error(code, env.message));
        }
        _ => {}
    }
    Ok(())
}

/// 将 http(s)://host 转为 ws(s)://host/ws/signaling?token=...
fn build_ws_url(server_url: &str, token: &str) -> String {
    let ws_base = if server_url.starts_with("https://") {
        format!("wss://{}", &server_url[8..])
    } else if server_url.starts_with("http://") {
        format!("ws://{}", &server_url[7..])
    } else {
        format!("ws://{}", server_url)
    };
    let mut url = Url::parse(&format!("{}/ws/signaling", ws_base.trim_end_matches('/')))
        .unwrap_or_else(|_| Url::parse("ws://localhost:8080/ws/signaling").unwrap());
    if !token.is_empty() {
        url.query_pairs_mut().append_pair("token", token);
    }
    url.to_string()
}

fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

impl Default for SignalingEnvelope {
    fn default() -> Self {
        Self {
            kind: SignalingType::Ping,
            ts: now_ms(),
            session_id: None,
            from: None,
            to: None,
            device_id: None,
            mode: None,
            token: None,
            device_code: None,
            ok: None,
            code: None,
            message: None,
            require_confirm: None,
            sdp: None,
            candidate: None,
            reason: None,
            client_ts: None,
            server_ts: None,
            rtt: None,
        }
    }
}
