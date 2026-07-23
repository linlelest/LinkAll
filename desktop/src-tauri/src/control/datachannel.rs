// 控制端 DataChannel 控制指令发送器
// 与被控端 control.rs 的 ControlDispatcher 相反：此处是发送方，封装各种控制指令为
// JSON 信封并通过 DataChannel 推送给远端被控设备。
// 消息协议与 shared/protocol.json Envelope 对齐。
use std::sync::Arc;

use anyhow::Result;
use tokio::sync::Mutex;
use webrtc::data_channel::RTCDataChannel;

/// 控制指令信封（与被控端 ControlEnvelope 对齐）
#[derive(Debug, Clone, serde::Serialize)]
pub struct ControlEnvelope<T: serde::Serialize> {
    #[serde(rename = "type")]
    pub kind: String,
    pub ts: i64,
    #[serde(default)]
    pub seq: i64,
    #[serde(default, rename = "sessionId", skip_serializing_if = "Option::is_none")]
    pub session_id: Option<String>,
    pub payload: T,
}

/// 键鼠事件载荷构造器
pub struct ControlSender {
    /// DataChannel 引用
    dc: Arc<RTCDataChannel>,
    /// 序号计数器
    seq: Mutex<i64>,
    /// 会话 ID
    session_id: Mutex<Option<String>>,
}

impl ControlSender {
    pub fn new(dc: Arc<RTCDataChannel>) -> Self {
        Self {
            dc,
            seq: Mutex::new(0),
            session_id: Mutex::new(None),
        }
    }

    /// 设置会话 ID（收到 connect_ack 后调用）
    pub async fn set_session_id(&self, id: Option<String>) {
        *self.session_id.lock().await = id;
    }

    /// 发送任意控制指令（payload 为任意可序列化对象）
    pub async fn send<T: serde::Serialize>(&self, kind: &str, payload: T) -> Result<()> {
        let seq = {
            let mut s = self.seq.lock().await;
            *s += 1;
            *s
        };
        let session_id = self.session_id.lock().await.clone();
        let env = ControlEnvelope {
            kind: kind.to_string(),
            ts: chrono::Utc::now().timestamp_millis(),
            seq,
            session_id,
            payload,
        };
        let text = serde_json::to_string(&env)?;
        self.dc.send_text(text).await
            .map_err(|e| anyhow::anyhow!("DataChannel 发送失败：{}", e))?;
        Ok(())
    }

    /// 发送键鼠事件（直接传入已构造的 JSON 字符串，由前端组装）
    /// 这样前端可以自由扩展协议字段，无需在 Rust 端逐一映射。
    pub async fn send_raw_json(&self, json: &str) -> Result<()> {
        self.dc.send_text(json.to_string()).await
            .map_err(|e| anyhow::anyhow!("DataChannel 发送失败：{}", e))?;
        Ok(())
    }
}

// === 常用控制指令的便捷构造器 ===

/// 鼠标事件 payload
#[derive(Debug, Clone, serde::Serialize)]
pub struct MousePayload {
    pub action: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub button: Option<String>,
    #[serde(default)]
    pub x: i32,
    #[serde(default)]
    pub y: i32,
    #[serde(default)]
    pub dx: i32,
    #[serde(default)]
    pub dy: i32,
}

/// 键盘事件 payload
#[derive(Debug, Clone, serde::Serialize)]
pub struct KeyboardPayload {
    pub key: String,
    pub action: String,
    #[serde(default)]
    pub modifiers: Vec<String>,
}

/// 滚轮事件 payload
#[derive(Debug, Clone, serde::Serialize)]
pub struct WheelPayload {
    #[serde(default, rename = "deltaX")]
    pub delta_x: i32,
    #[serde(rename = "deltaY")]
    pub delta_y: i32,
}

/// 防窥屏指令 payload
#[derive(Debug, Clone, serde::Serialize)]
pub struct PrivacyScreenPayload {
    pub enabled: bool,
}

/// 设置同步 payload（按需填入 screen / codec / control 子结构）
#[derive(Debug, Clone, Default, serde::Serialize)]
pub struct SettingsSyncPayload {
    #[serde(default)]
    pub category: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub screen: Option<ScreenSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub codec: Option<CodecSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub control: Option<ControlSettings>,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct ScreenSettings {
    #[serde(default)]
    pub scale: f64,
    #[serde(default)]
    pub fps: u32,
    #[serde(default, rename = "maxBitrate")]
    pub max_bitrate: u64,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct CodecSettings {
    #[serde(default)]
    pub video: String,
    #[serde(default)]
    pub audio: String,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct ControlSettings {
    #[serde(default, rename = "privacyScreen")]
    pub privacy_screen: bool,
    #[serde(default, rename = "clipboardSync")]
    pub clipboard_sync: bool,
}
