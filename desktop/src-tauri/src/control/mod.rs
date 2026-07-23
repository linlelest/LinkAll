// 控制端模块
// 实现"作为控制端"的能力：连接到远端被控设备、接收远端视频流、
// 通过 DataChannel 下发键鼠/滚轮/手势/设置同步指令。
// 与 daemon（被控端）平级但独立：同一进程内可同时作为被控端与控制端。
//
// 模块组成：
//   - signaling：WebSocket 信令客户端（连接 /ws/signaling，SDP/ICE 交换）
//   - peer：客户端 PeerSession（创建 Offer → 接收 Answer → 接收远端视频流）
//   - datachannel：DataChannel 控制指令发送（键鼠/滚轮/设置同步）
//   - state：ControlState（控制会话状态，与 DaemonState 平级）
pub mod signaling;
pub mod peer;
pub mod datachannel;
pub mod state;
