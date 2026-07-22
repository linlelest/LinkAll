// WebRTC PeerConnection 管理
// 职责：创建 RTCPeerConnection，附加 H.264 视频轨道（TrackLocalStaticSample），
//       创建用于控制指令的 DataChannel，处理 SDP/ICE 交换，
//       将编码后的视频帧写入轨道，并将信令事件路由到控制模块。
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
use tokio::sync::{mpsc, oneshot, Mutex};
use webrtc::api::interceptor_registry::register_default_interceptors;
use webrtc::api::media_engine::{MediaEngine, MIME_TYPE_H264};
use webrtc::api::APIBuilder;
use webrtc::ice_transport::ice_candidate::RTCIceCandidateInit;
use webrtc::interceptor::registry::Registry;
use webrtc::media::Sample;
use webrtc::peer_connection::configuration::RTCConfiguration;
use webrtc::peer_connection::sdp::session_description::RTCSessionDescription;
use webrtc::peer_connection::RTCPeerConnection;
use webrtc::rtp_transceiver::rtp_codec::RTCRtpCodecCapability;
use webrtc::track::track_local::track_local_static_sample::TrackLocalStaticSample;
use webrtc::track::track_local::TrackLocal;

use super::control::{ControlDispatcher, SettingsSnapshot};
use super::signaling::{
    SdpPayload, SignalingClient, SignalingConfig, SignalingEnvelope, SignalingEvent, SignalingType,
};
use crate::encoder::EncodedFrame;

/// Peer 事件（向上层 UI/日志派发）
#[derive(Debug, Clone)]
pub enum PeerEvent {
    Connected,
    Disconnected,
    Failed(String),
    IceConnected,
    IceDisconnected,
    Stats { rtt_ms: i32 },
}

/// Peer 会话：封装一个被控会话的所有资源
pub struct PeerSession {
    /// 信令客户端
    signaling: Arc<SignalingClient>,
    /// RTCPeerConnection
    pc: Arc<RTCPeerConnection>,
    /// 视频轨道（H.264）
    video_track: Arc<TrackLocalStaticSample>,
    /// 控制通道（DataChannel）
    control: Arc<ControlDispatcher>,
    /// 接收编码帧的入口（编码线程 → 此处 → 写入轨道）
    frame_tx: mpsc::Sender<EncodedFrame>,
    /// 本地 ICE 候选出口（PC 回调 → run 循环 → 信令发送）
    ice_tx: mpsc::UnboundedSender<RTCIceCandidateInit>,
    /// 关闭信号
    shutdown: Mutex<Option<oneshot::Sender<()>>>,
}

impl PeerSession {
    /// 创建新会话：建立 PeerConnection、视频轨道、DataChannel 与信令客户端
    pub async fn new(
        cfg: SignalingConfig,
        settings: SettingsSnapshot,
    ) -> Result<(Arc<Self>, mpsc::UnboundedReceiver<PeerEvent>)> {
        // 1) MediaEngine + 拦截器
        let mut m = MediaEngine::default();
        m.register_default_codecs()?;
        let mut registry = Registry::new();
        register_default_interceptors(&mut registry, &mut m)?;
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

        // 3) 视频轨道（H.264）
        let codec_cap = RTCRtpCodecCapability {
            mime_type: MIME_TYPE_H264.to_owned(),
            clock_rate: 90_000,
            channels: 0,
            sdp_fmtp_line: String::new(),
            ..Default::default()
        };
        let video_track = Arc::new(TrackLocalStaticSample::new(
            codec_cap,
            "video".to_string(),
            "linkall-desktop".to_string(),
        ));
        let _ = pc
            .add_track(video_track.clone() as Arc<dyn TrackLocal + Send + Sync>)
            .await
            .context("添加视频轨道失败")?;

        // 4) DataChannel（控制指令）
        let dc = pc
            .create_data_channel("control", None)
            .await
            .context("创建 DataChannel 失败")?;
        let dc_arc = Arc::new(dc);

        // 5) 控制分发器
        let (frame_tx, frame_rx) = mpsc::channel::<EncodedFrame>(8);
        let control = Arc::new(ControlDispatcher::new(dc_arc.clone(), settings, frame_rx));

        // 6) ICE 候选通道
        let (ice_tx, ice_rx) = mpsc::unbounded_channel::<RTCIceCandidateInit>();

        // 7) 事件出口
        let (event_tx, event_rx) = mpsc::unbounded_channel::<PeerEvent>();

        // 8) 信令客户端
        let (sig_tx, sig_rx) = mpsc::unbounded_channel::<SignalingEvent>();
        let signaling = Arc::new(SignalingClient::new(cfg, sig_tx));

        let session = Arc::new(Self {
            signaling: signaling.clone(),
            pc: pc.clone(),
            video_track: video_track.clone(),
            control,
            frame_tx,
            ice_tx,
            shutdown: Mutex::new(None),
        });

        // 绑定 PeerConnection 状态回调
        bind_pc_handlers(pc.clone(), event_tx.clone(), session.ice_tx.clone());

        // 启动后台主循环
        let (stx, srx) = oneshot::channel::<()>();
        *session.shutdown.lock().await = Some(stx);
        tokio::spawn(run_loop(
            session.clone(),
            sig_rx,
            ice_rx,
            frame_rx,
            event_tx,
            signaling,
            srx,
        ));

        Ok((session, event_rx))
    }

    /// 推送一帧编码视频到轨道（异步写入由 run_loop 完成）
    pub async fn send_frame(&self, frame: EncodedFrame) -> Result<()> {
        self.frame_tx.send(frame).await.context("视频帧通道已关闭")?;
        Ok(())
    }

    /// 启动信令客户端
    pub async fn start_signaling(&self) -> Result<()> {
        self.signaling.start().await
    }

    /// 主动结束会话
    pub async fn close(&self) {
        log::info!("关闭 Peer 会话");
        if let Some(tx) = self.shutdown.lock().await.take() {
            let _ = tx.send(());
        }
        let _ = self.signaling.close().await;
        let _ = self.pc.close().await;
    }

    /// 应用动态设置（settings_sync 消息触发）
    pub async fn apply_settings(&self, settings: SettingsSnapshot) {
        self.control.apply_settings(settings).await;
    }
}

/// 主运行循环：消费信令事件、ICE 候选、编码帧
async fn run_loop(
    session: Arc<PeerSession>,
    mut sig_rx: mpsc::UnboundedReceiver<SignalingEvent>,
    mut ice_rx: mpsc::UnboundedReceiver<RTCIceCandidateInit>,
    mut frame_rx: mpsc::Receiver<EncodedFrame>,
    event_tx: mpsc::UnboundedSender<PeerEvent>,
    signaling: Arc<SignalingClient>,
    mut shutdown: oneshot::Receiver<()>,
) {
    loop {
        tokio::select! {
            _ = &mut shutdown => {
                log::info!("PeerSession 收到关闭信号");
                break;
            }
            evt = sig_rx.recv() => {
                let evt = match evt {
                    Some(e) => e,
                    None => break,
                };
                if let Err(e) = handle_signaling_event(&session, &signaling, evt, &event_tx).await {
                    log::warn!("处理信令事件失败：{:?}", e);
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
                        candidate: Some(val),
                        ..Default::default()
                    };
                    if let Err(e) = signaling.send(env).await {
                        log::warn!("发送 ICE 候选失败：{:?}", e);
                    }
                }
            }
            frame = frame_rx.recv() => {
                let frame = match frame {
                    Some(f) => Some(f),
                    None => None,
                };
                if let Some(f) = frame {
                    let sample = Sample {
                        data: bytes::Bytes::from(f.data),
                        duration: Duration::from_secs_f64(1.0 / 30.0),
                        ..Default::default()
                    };
                    if let Err(e) = session.video_track.write_sample(&sample).await {
                        log::warn!("写入视频轨道失败：{}", e);
                    }
                }
            }
        }
    }
}

/// 处理单个信令事件
async fn handle_signaling_event(
    session: &PeerSession,
    signaling: &SignalingClient,
    evt: SignalingEvent,
    event_tx: &mpsc::UnboundedSender<PeerEvent>,
) -> Result<()> {
    match evt {
        SignalingEvent::Open => {
            log::info!("信令通道已开启");
        }
        SignalingEvent::ConnectAck { ok, session_id, require_confirm } => {
            log::info!(
                "connect_ack ok={} sessionId={:?} requireConfirm={:?}",
                ok, session_id, require_confirm
            );
            if !ok {
                let _ = event_tx.send(PeerEvent::Failed("connect_ack 拒绝".into()));
            }
        }
        SignalingEvent::SdpOffer(sdp) => {
            log::info!("收到 SDP Offer，开始应答");
            handle_sdp_offer(&session.pc, signaling, sdp).await?;
        }
        SignalingEvent::SdpAnswer(sdp) => {
            log::info!("收到 SDP Answer");
            let desc = RTCSessionDescription::answer(sdp.sdp)?;
            session.pc.set_remote_description(desc).await?;
        }
        SignalingEvent::IceCandidate(candidate) => {
            // candidate 为 serde_json::Value，反序列化为 RTCIceCandidateInit 后直接添加
            if let Ok(init) = serde_json::from_value::<RTCIceCandidateInit>(candidate) {
                let _ = session.pc.add_ice_candidate(init).await;
            }
        }
        SignalingEvent::IceComplete => {
            log::info!("ICE 收集完成");
        }
        SignalingEvent::Bye(reason) => {
            log::info!("收到 bye：{}", reason);
            let _ = event_tx.send(PeerEvent::Disconnected);
        }
        SignalingEvent::Pong(rtt) => {
            let _ = event_tx.send(PeerEvent::Stats { rtt_ms: rtt });
        }
        SignalingEvent::Error(code, msg) => {
            log::warn!("信令错误：{} {:?}", code, msg);
            let _ = event_tx.send(PeerEvent::Failed(format!("{}: {}", code, msg.unwrap_or_default())));
        }
        SignalingEvent::Closed => {
            let _ = event_tx.send(PeerEvent::Disconnected);
        }
    }
    Ok(())
}

/// 处理 SDP Offer：setRemoteDescription → createAnswer → setLocalDescription → 回送
async fn handle_sdp_offer(
    pc: &Arc<RTCPeerConnection>,
    signaling: &SignalingClient,
    offer: SdpPayload,
) -> Result<()> {
    let desc = RTCSessionDescription::offer(offer.sdp)?;
    pc.set_remote_description(desc).await?;

    let answer = pc.create_answer(None).await?;
    pc.set_local_description(answer).await?;

    if let Some(local) = pc.local_description().await {
        let env = SignalingEnvelope {
            kind: SignalingType::SdpAnswer,
            ts: chrono::Utc::now().timestamp_millis(),
            sdp: Some(SdpPayload {
                kind: "answer".into(),
                sdp: local.sdp,
            }),
            ..Default::default()
        };
        signaling.send(env).await?;
        log::info!("已回送 SDP Answer");
    }
    Ok(())
}

/// 绑定 PeerConnection 状态回调
fn bind_pc_handlers(
    pc: Arc<RTCPeerConnection>,
    event_tx: mpsc::UnboundedSender<PeerEvent>,
    ice_tx: mpsc::UnboundedSender<RTCIceCandidateInit>,
) {
    let tx1 = event_tx.clone();
    pc.on_peer_connection_state_change(Box::new(move |s| {
        log::info!("PeerConnection 状态：{}", s);
        let evt = match s.as_str() {
            "connected" => Some(PeerEvent::Connected),
            "disconnected" => Some(PeerEvent::Disconnected),
            "failed" => Some(PeerEvent::Failed("peer connection failed".into())),
            _ => None,
        };
        if let Some(evt) = evt {
            let _ = tx1.send(evt);
        }
    }));

    let tx2 = event_tx.clone();
    pc.on_ice_connection_state_change(Box::new(move |s| {
        log::info!("ICE 状态：{}", s);
        let evt = match s.as_str() {
            "connected" | "completed" => Some(PeerEvent::IceConnected),
            "disconnected" | "failed" => Some(PeerEvent::IceDisconnected),
            _ => None,
        };
        if let Some(evt) = evt {
            let _ = tx2.send(evt);
        }
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
