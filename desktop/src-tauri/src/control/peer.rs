// 控制端 WebRTC PeerSession
// 职责：
//   1. 创建 RTCPeerConnection，添加 video/audio recvonly 收发器
//   2. 创建控制 DataChannel（control），由控制端发起
//   3. 创建 SDP Offer，通过信令通道发送给被控端
//   4. 接收被控端的 SDP Answer 并应用
//   5. 处理远端 ICE Candidate
//   6. 通过 DataChannel 发送键鼠/滚轮/手势/设置同步指令
//   7. 通过事件通道向 ControlState 派发连接状态与统计
//
// 与被控端 webrtc/peer.rs 相反：被控端响应 Offer 并推送视频；
// 控制端创建 Offer 并接收视频。
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
use tokio::sync::{mpsc, oneshot, Mutex};
use webrtc::api::interceptor_registry::register_default_interceptors;
use webrtc::api::media_engine::MediaEngine;
use webrtc::api::APIBuilder;
use webrtc::ice_transport::ice_candidate::RTCIceCandidateInit;
use webrtc::ice_transport::ice_connection_state::RTCIceConnectionState;
use webrtc::interceptor::registry::Registry;
use webrtc::peer_connection::configuration::RTCConfiguration;
use webrtc::peer_connection::peer_connection_state::RTCPeerConnectionState;
use webrtc::peer_connection::sdp::session_description::RTCSessionDescription;
use webrtc::peer_connection::RTCPeerConnection;
use webrtc::rtp_transceiver::rtp_receiver::RTCRtpReceiver;
use webrtc::track::track_remote::TrackRemote;

use super::datachannel::ControlSender;
use super::signaling::{
    ControlSignalingClient, ControlSignalingConfig, ControlSignalingEvent, SignalingEnvelope,
    SignalingType, SdpPayload,
};
use crate::webrtc::control::SettingsSnapshot;

/// 控制 Peer 事件（向上层 ControlState 派发）
#[derive(Debug, Clone)]
pub enum ControlPeerEvent {
    /// 连接已建立
    Connected,
    /// 连接已断开
    Disconnected,
    /// 连接失败
    Failed(String),
    /// 统计信息
    Stats { rtt_ms: i32, fps: u32, packet_loss: f64 },
    /// 信令通道就绪
    SignalingOpen,
}

/// 控制端 PeerSession：封装一次"作为控制端"的 WebRTC 会话
pub struct ControlPeer {
    /// 信令客户端
    signaling: Arc<ControlSignalingClient>,
    /// RTCPeerConnection
    pc: Arc<RTCPeerConnection>,
    /// DataChannel 控制指令发送器
    sender: Arc<ControlSender>,
    /// 本地 ICE 候选出口
    ice_tx: mpsc::UnboundedSender<RTCIceCandidateInit>,
    /// 关闭信号
    shutdown: Mutex<Option<oneshot::Sender<()>>>,
}

impl ControlPeer {
    /// 创建新会话：建立 PeerConnection、视频/音频 recvonly 收发器、控制 DataChannel、信令客户端
    pub async fn new(
        cfg: ControlSignalingConfig,
        _settings: SettingsSnapshot,
    ) -> Result<(Arc<Self>, mpsc::UnboundedReceiver<ControlPeerEvent>)> {
        // 1) MediaEngine + 拦截器
        let mut m = MediaEngine::default();
        m.register_default_codecs()?;
        let registry = Registry::new();
        let registry = register_default_interceptors(registry, &mut m)?;
        let api = APIBuilder::new()
            .with_media_engine(m)
            .with_interceptor_registry(registry)
            .build();

        // 2) PeerConnection（STUN/TURN 由服务端 ICE relay 或本地配置）
        let pc_config = RTCConfiguration {
            ice_servers: vec![],
            ..Default::default()
        };
        let pc = Arc::new(api.new_peer_connection(pc_config).await?);

        // 3) 视频/音频 recvonly 收发器（控制端只接收）
        pc.add_transceiver_from_kind(
            webrtc::rtp_transceiver::rtp_codec::RTPCodecType::Video,
            Some(webrtc::rtp_transceiver::rtp_transceiver_direction::RTCRtpTransceiverDirection::Recvonly),
        ).await?;
        pc.add_transceiver_from_kind(
            webrtc::rtp_transceiver::rtp_codec::RTPCodecType::Audio,
            Some(webrtc::rtp_transceiver::rtp_transceiver_direction::RTCRtpTransceiverDirection::Recvonly),
        ).await?;

        // 4) 控制指令 DataChannel（控制端创建，被控端接收）
        let dc = pc
            .create_data_channel("control", None)
            .await
            .context("创建控制 DataChannel 失败")?;

        // 5) DataChannel 控制发送器
        let sender = Arc::new(ControlSender::new(dc.clone()));

        // 6) ICE 候选通道
        let (ice_tx, ice_rx) = mpsc::unbounded_channel::<RTCIceCandidateInit>();

        // 7) 事件出口
        let (event_tx, event_rx) = mpsc::unbounded_channel::<ControlPeerEvent>();

        // 8) 信令客户端
        let (sig_tx, sig_rx) = mpsc::unbounded_channel::<ControlSignalingEvent>();
        let signaling = Arc::new(ControlSignalingClient::new(cfg, sig_tx));

        let session = Arc::new(Self {
            signaling: signaling.clone(),
            pc: pc.clone(),
            sender: sender.clone(),
            ice_tx,
            shutdown: Mutex::new(None),
        });

        // 绑定 PeerConnection 状态回调
        bind_pc_handlers(pc.clone(), event_tx.clone(), session.ice_tx.clone());

        // 处理远端轨道到达（仅日志，实际渲染在前端 <video> 元素由 stream 完成）
        pc.on_track(Box::new({
            let tx = event_tx.clone();
            move |track: Arc<TrackRemote>, _receiver: Arc<RTCRtpReceiver>| {
                log::info!("控制端收到远端轨道：kind={}", track.kind());
                let _ = tx;
                Box::pin(async move {
                    // 实际渲染由前端处理（通过 stats 查询帧率）
                })
            }
        }));

        // 启动后台主循环
        let (stx, srx) = oneshot::channel::<()>();
        *session.shutdown.lock().await = Some(stx);
        tokio::spawn(run_loop(
            pc.clone(),
            sig_rx,
            ice_rx,
            event_tx,
            signaling,
            srx,
        ));

        Ok((session, event_rx))
    }

    /// 启动信令客户端
    pub async fn start_signaling(&self) -> Result<()> {
        self.signaling.start().await
    }

    /// 创建 SDP Offer 并通过信令通道发送给被控端
    pub async fn create_offer(&self) -> Result<()> {
        log::info!("控制端创建 SDP Offer");
        let offer = self.pc.create_offer(None).await?;
        self.pc.set_local_description(offer).await?;

        if let Some(local) = self.pc.local_description().await {
            let env = SignalingEnvelope {
                kind: SignalingType::SdpOffer,
                ts: chrono::Utc::now().timestamp_millis(),
                from: Some("controller".into()),
                sdp: Some(SdpPayload {
                    kind: "offer".into(),
                    sdp: local.sdp,
                }),
                ..Default::default()
            };
            self.signaling.send(env).await?;
            log::info!("控制端已发送 SDP Offer");
        } else {
            anyhow::bail!("无法获取本地 SDP");
        }
        Ok(())
    }

    /// 通过 DataChannel 发送控制指令（JSON 字符串）
    pub async fn send_data_channel(&self, json: String) -> Result<()> {
        self.sender.send_raw_json(&json).await
    }

    /// 主动结束会话
    pub async fn close(&self) {
        log::info!("关闭控制端 Peer 会话");
        if let Some(tx) = self.shutdown.lock().await.take() {
            let _ = tx.send(());
        }
        let _ = self.signaling.close().await;
        let _ = self.pc.close().await;
    }
}

/// 主运行循环：消费信令事件、ICE 候选
async fn run_loop(
    pc: Arc<RTCPeerConnection>,
    mut sig_rx: mpsc::UnboundedReceiver<ControlSignalingEvent>,
    mut ice_rx: mpsc::UnboundedReceiver<RTCIceCandidateInit>,
    event_tx: mpsc::UnboundedSender<ControlPeerEvent>,
    signaling: Arc<ControlSignalingClient>,
    mut shutdown: oneshot::Receiver<()>,
) {
    // 周期性发送 PING 用于 RTT 统计
    let mut stats_ticker = tokio::time::interval(Duration::from_secs(2));
    loop {
        tokio::select! {
            _ = &mut shutdown => {
                log::info!("ControlPeer 收到关闭信号");
                break;
            }
            evt = sig_rx.recv() => {
                let evt = match evt {
                    Some(e) => e,
                    None => break,
                };
                if let Err(e) = handle_signaling_event(&pc, evt, &event_tx).await {
                    log::warn!("处理控制端信令事件失败：{:?}", e);
                }
            }
            ice = ice_rx.recv() => {
                if let Some(init) = ice {
                    let val = match serde_json::to_value(&init) {
                        Ok(v) => v,
                        Err(_) => continue,
                    };
                    let env = SignalingEnvelope {
                        kind: SignalingType::IceCandidate,
                        ts: chrono::Utc::now().timestamp_millis(),
                        from: Some("controller".into()),
                        candidate: Some(val),
                        ..Default::default()
                    };
                    if let Err(e) = signaling.send(env).await {
                        log::warn!("控制端发送 ICE 候选失败：{:?}", e);
                    }
                }
            }
            _ = stats_ticker.tick() => {
                // 仅在已连接时统计；具体 RTT 由信令 Pong 推送
                // 这里保持空操作，避免无意义负载
            }
        }
    }
    let _ = event_tx.send(ControlPeerEvent::Disconnected);
}

/// 处理单个信令事件
async fn handle_signaling_event(
    pc: &Arc<RTCPeerConnection>,
    evt: ControlSignalingEvent,
    event_tx: &mpsc::UnboundedSender<ControlPeerEvent>,
) -> Result<()> {
    match evt {
        ControlSignalingEvent::Open => {
            log::info!("控制端信令通道已开启");
            let _ = event_tx.send(ControlPeerEvent::SignalingOpen);
        }
        ControlSignalingEvent::ConnectAck { ok, session_id, require_confirm } => {
            log::info!(
                "控制端 connect_ack ok={} sessionId={:?} requireConfirm={:?}",
                ok, session_id, require_confirm
            );
            if !ok {
                let _ = event_tx.send(ControlPeerEvent::Failed("connect_ack 拒绝".into()));
            }
        }
        ControlSignalingEvent::SdpAnswer(sdp) => {
            log::info!("控制端收到 SDP Answer");
            // 应用远端 Answer：将 SdpPayload 转为 RTCSessionDescription
            let desc = RTCSessionDescription::answer(sdp.sdp.clone())
                .with_context(|| format!("构造 Answer 失败：{}", sdp.sdp))?;
            pc.set_remote_description(desc).await
                .context("控制端 set_remote_description 失败")?;
            log::info!("控制端已应用远端 SDP Answer");
        }
        ControlSignalingEvent::IceCandidate(candidate) => {
            if let Ok(init) = serde_json::from_value::<RTCIceCandidateInit>(candidate) {
                // 应用远端 ICE 候选
                if let Err(e) = pc.add_ice_candidate(init).await {
                    log::warn!("控制端 add_ice_candidate 失败：{:?}", e);
                }
            }
        }
        ControlSignalingEvent::IceComplete => {
            log::info!("控制端 ICE 收集完成");
        }
        ControlSignalingEvent::Bye(reason) => {
            log::info!("控制端收到 bye：{}", reason);
            let _ = event_tx.send(ControlPeerEvent::Disconnected);
        }
        ControlSignalingEvent::Pong(rtt) => {
            let _ = event_tx.send(ControlPeerEvent::Stats {
                rtt_ms: rtt,
                fps: 0,
                packet_loss: 0.0,
            });
        }
        ControlSignalingEvent::Error(code, msg) => {
            log::warn!("控制端信令错误：{} {:?}", code, msg);
            let _ = event_tx.send(ControlPeerEvent::Failed(format!("{}: {}", code, msg.unwrap_or_default())));
        }
        ControlSignalingEvent::Closed => {
            let _ = event_tx.send(ControlPeerEvent::Disconnected);
        }
    }
    Ok(())
}

/// 绑定 PeerConnection 状态回调
fn bind_pc_handlers(
    pc: Arc<RTCPeerConnection>,
    event_tx: mpsc::UnboundedSender<ControlPeerEvent>,
    ice_tx: mpsc::UnboundedSender<RTCIceCandidateInit>,
) {
    let tx1 = event_tx.clone();
    pc.on_peer_connection_state_change(Box::new(move |s| {
        log::info!("控制端 PeerConnection 状态：{:?}", s);
        let evt = match s {
            RTCPeerConnectionState::Connected => Some(ControlPeerEvent::Connected),
            RTCPeerConnectionState::Disconnected => Some(ControlPeerEvent::Disconnected),
            RTCPeerConnectionState::Failed => Some(ControlPeerEvent::Failed("peer connection failed".into())),
            _ => None,
        };
        let tx = tx1.clone();
        Box::pin(async move {
            if let Some(evt) = evt {
                let _ = tx.send(evt);
            }
        })
    }));

    let tx2 = event_tx.clone();
    pc.on_ice_connection_state_change(Box::new(move |s| {
        log::info!("控制端 ICE 状态：{:?}", s);
        let evt = match s {
            RTCIceConnectionState::Connected | RTCIceConnectionState::Completed => Some(ControlPeerEvent::Connected),
            RTCIceConnectionState::Disconnected | RTCIceConnectionState::Failed => Some(ControlPeerEvent::Disconnected),
            _ => None,
        };
        let tx = tx2.clone();
        Box::pin(async move {
            if let Some(evt) = evt {
                let _ = tx.send(evt);
            }
        })
    }));

    // 本地 ICE 候选：通过通道传递给 run_loop，由其转发到信令服务器
    pc.on_ice_candidate(Box::new(move |c| {
        let ice_tx = ice_tx.clone();
        Box::pin(async move {
            if let Some(c) = c {
                if let Ok(init) = c.to_json() {
                    let _ = ice_tx.send(init);
                }
            }
        })
    }));
}

/// 应用远端 SDP Answer
/// （独立函数，供外部在收到 SdpAnswer 时调用）
pub async fn set_remote_answer(pc: &Arc<RTCPeerConnection>, sdp: SdpPayload) -> Result<()> {
    let desc = RTCSessionDescription::answer(sdp.sdp)?;
    pc.set_remote_description(desc).await?;
    Ok(())
}
