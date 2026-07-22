// WebRTC 模块
// 负责：与 Go 服务端 /ws/signaling 建立 WebSocket 信令通道；协商 RTCPeerConnection；
//       将 H.264 编码后的屏幕帧通过 TrackLocalStaticSample 推送；
//       通过 DataChannel 接收并处理键鼠/滚轮/设置同步/防窥屏等控制指令。
// 协议：shared/messages.json（信令）+ shared/protocol.json（DataChannel）
pub mod signaling;
pub mod peer;
pub mod control;

pub use peer::PeerSession;
pub use signaling::{SignalingClient, SignalingConfig, SignalingEvent};
