// DataChannel 控制指令处理
// 接收 Web 控制端通过 DataChannel 下发的键鼠/滚轮/设置同步/防窥屏指令，
// 解析 JSON 信封后路由到 InputInjector 或配置应用器。
// 消息协议与 shared/protocol.json 对齐。
use std::sync::Arc;

use serde::Deserialize;
use serde_json::Value;
use tokio::sync::Mutex;
use webrtc::data_channel::RTCDataChannel;

use crate::input::{self, InputInjector, MouseButton};

/// 设置快照：用于在创建会话时传递初始配置，并接收 settings_sync 更新
#[derive(Debug, Clone)]
pub struct SettingsSnapshot {
    pub scale: f64,
    pub fps: u32,
    pub max_bitrate: u64,
    pub codec: String,
    pub privacy_screen: bool,
}

impl Default for SettingsSnapshot {
    fn default() -> Self {
        Self {
            scale: 1.0,
            fps: 30,
            max_bitrate: 8_000_000,
            codec: "H264".into(),
            privacy_screen: false,
        }
    }
}

/// DataChannel 控制消息外层信封（与 shared/protocol.json Envelope 对齐）
#[derive(Debug, Deserialize)]
struct ControlEnvelope {
    #[serde(rename = "type")]
    kind: String,
    #[serde(default)]
    ts: i64,
    #[serde(default)]
    seq: i64,
    #[serde(default, rename = "sessionId")]
    session_id: Option<String>,
    payload: Value,
}

/// 键盘事件 payload
#[derive(Debug, Deserialize)]
struct KeyboardPayload {
    key: String,
    action: String, // down / up / press
    #[serde(default)]
    modifiers: Vec<String>,
}

/// 鼠标事件 payload
#[derive(Debug, Deserialize)]
struct MousePayload {
    action: String, // move / down / up / click / double_click
    #[serde(default)]
    button: Option<String>,
    #[serde(default)]
    x: i32,
    #[serde(default)]
    y: i32,
    #[serde(default)]
    dx: i32,
    #[serde(default)]
    dy: i32,
}

/// 滚轮事件 payload
#[derive(Debug, Deserialize)]
struct WheelPayload {
    #[serde(default, rename = "deltaX")]
    delta_x: i32,
    #[serde(rename = "deltaY")]
    delta_y: i32,
}

/// 设置同步 payload
#[derive(Debug, Deserialize)]
struct SettingsSyncPayload {
    #[serde(default)]
    category: String,
    #[serde(default)]
    screen: Option<ScreenSettings>,
    #[serde(default)]
    codec: Option<CodecSettings>,
    #[serde(default)]
    control: Option<ControlSettings>,
}

#[derive(Debug, Deserialize)]
struct ScreenSettings {
    #[serde(default)]
    scale: Option<f64>,
    #[serde(default)]
    fps: Option<u32>,
    #[serde(default, rename = "maxBitrate")]
    max_bitrate: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct CodecSettings {
    #[serde(default)]
    video: Option<String>,
    #[serde(default)]
    audio: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ControlSettings {
    #[serde(default, rename = "privacyScreen")]
    privacy_screen: Option<bool>,
    #[serde(default, rename = "clipboardSync")]
    clipboard_sync: Option<bool>,
}

/// 防窥屏指令 payload
#[derive(Debug, Deserialize)]
struct PrivacyScreenPayload {
    enabled: bool,
}

/// 共享状态：键鼠注入器（DataChannel 回调与 ControlDispatcher 均可访问）
type SharedInjector = Arc<Mutex<Box<dyn InputInjector>>>;
/// 共享设置快照
type SharedSettings = Arc<Mutex<SettingsSnapshot>>;

/// 控制分发器：绑定 DataChannel，解析并派发控制指令
pub struct ControlDispatcher {
    /// DataChannel 引用
    dc: Arc<RTCDataChannel>,
    /// 当前设置（运行时可被 settings_sync 更新）
    settings: SharedSettings,
    /// 键鼠注入器
    injector: SharedInjector,
}

impl ControlDispatcher {
    pub fn new(
        dc: Arc<RTCDataChannel>,
        settings: SettingsSnapshot,
    ) -> Self {
        let injector: SharedInjector = Arc::new(Mutex::new(
            input::new_injector().unwrap_or_else(|e| {
                log::error!("创建键鼠注入器失败：{:?}", e);
                Box::new(NullInjector)
            }),
        ));
        let settings = Arc::new(Mutex::new(settings));
        let dispatcher = Self {
            dc: dc.clone(),
            settings: settings.clone(),
            injector: injector.clone(),
        };
        dispatcher.bind_data_channel(dc, injector, settings);
        dispatcher
    }

    /// 绑定 DataChannel 消息回调
    fn bind_data_channel(
        &self,
        dc: Arc<RTCDataChannel>,
        injector: SharedInjector,
        settings: SharedSettings,
    ) {
        dc.on_message(Box::new(move |msg: webrtc::data_channel::data_channel_message::DataChannelMessage| {
            let data = msg.data.clone();
            let injector = injector.clone();
            let settings = settings.clone();
            Box::pin(async move {
                let text = String::from_utf8(data.to_vec()).unwrap_or_default();
                if text.is_empty() {
                    return;
                }
                if let Err(e) = handle_control_message(&text, &injector, &settings).await {
                    log::warn!("处理 DataChannel 控制指令失败：{:?}", e);
                }
            })
        }));
    }

    /// 应用动态设置（settings_sync 触发）
    pub async fn apply_settings(&self, new_settings: SettingsSnapshot) {
        let mut guard = self.settings.lock().await;
        *guard = new_settings;
    }
}

/// 处理单条 DataChannel 控制消息
async fn handle_control_message(
    data: &str,
    injector: &SharedInjector,
    settings: &SharedSettings,
) -> anyhow::Result<()> {
    let env: ControlEnvelope = serde_json::from_str(data)
        .map_err(|e| anyhow::anyhow!("解析控制消息失败：{} 原文：{}", e, data))?;
    match env.kind.as_str() {
        "keyboard" => {
            let p: KeyboardPayload = serde_json::from_value(env.payload)?;
            handle_keyboard(injector, &p).await;
        }
        "mouse" => {
            let p: MousePayload = serde_json::from_value(env.payload)?;
            handle_mouse(injector, &p).await;
        }
        "wheel" => {
            let p: WheelPayload = serde_json::from_value(env.payload)?;
            let mut inj = injector.lock().await;
            inj.mouse_wheel(p.delta_x, p.delta_y);
        }
        "settings_sync" => {
            let p: SettingsSyncPayload = serde_json::from_value(env.payload)?;
            handle_settings_sync(settings, p).await;
        }
        "privacy_screen" => {
            let p: PrivacyScreenPayload = serde_json::from_value(env.payload)?;
            let mut s = settings.lock().await;
            s.privacy_screen = p.enabled;
            log::info!("防窥屏已{}", if p.enabled { "开启" } else { "关闭" });
        }
        "heartbeat" | "heartbeat_ack" | "status" | "error" => {
            // 这些消息由上层处理，此处忽略
        }
        other => {
            log::debug!("忽略未知控制指令：{}", other);
        }
    }
    Ok(())
}

/// 处理键盘事件
async fn handle_keyboard(injector: &SharedInjector, p: &KeyboardPayload) {
    let mut inj = injector.lock().await;
    match p.action.as_str() {
        "down" => inj.key_event(&p.key, true),
        "up" => inj.key_event(&p.key, false),
        "press" => {
            inj.key_event(&p.key, true);
            inj.key_event(&p.key, false);
        }
        _ => {
            log::warn!("未知键盘动作：{}", p.action);
        }
    }
}

/// 处理鼠标事件
async fn handle_mouse(injector: &SharedInjector, p: &MousePayload) {
    let mut inj = injector.lock().await;
    match p.action.as_str() {
        "move" => {
            if p.dx != 0 || p.dy != 0 {
                inj.mouse_move_rel(p.dx, p.dy);
            } else {
                inj.mouse_move_abs(p.x, p.y);
            }
        }
        "down" => {
            if let Some(b) = p.button.as_deref().and_then(MouseButton::parse) {
                inj.mouse_button(b, true);
            }
        }
        "up" => {
            if let Some(b) = p.button.as_deref().and_then(MouseButton::parse) {
                inj.mouse_button(b, false);
            }
        }
        "click" => {
            if let Some(b) = p.button.as_deref().and_then(MouseButton::parse) {
                inj.mouse_button(b, true);
                inj.mouse_button(b, false);
            }
        }
        "double_click" => {
            if let Some(b) = p.button.as_deref().and_then(MouseButton::parse) {
                inj.mouse_button(b, true);
                inj.mouse_button(b, false);
                inj.mouse_button(b, true);
                inj.mouse_button(b, false);
            }
        }
        _ => {
            log::warn!("未知鼠标动作：{}", p.action);
        }
    }
}

/// 处理设置同步
async fn handle_settings_sync(settings: &SharedSettings, p: SettingsSyncPayload) {
    let mut s = settings.lock().await;
    if let Some(screen) = p.screen {
        if let Some(scale) = screen.scale {
            s.scale = scale;
        }
        if let Some(fps) = screen.fps {
            s.fps = fps;
        }
        if let Some(bitrate) = screen.max_bitrate {
            s.max_bitrate = bitrate;
        }
    }
    if let Some(codec) = p.codec {
        if let Some(video) = codec.video {
            s.codec = video;
        }
    }
    if let Some(control) = p.control {
        if let Some(privacy) = control.privacy_screen {
            s.privacy_screen = privacy;
        }
    }
    log::info!(
        "设置已同步：fps={} bitrate={} scale={} codec={} privacy={}",
        s.fps, s.max_bitrate, s.scale, s.codec, s.privacy_screen
    );
}

/// 空注入器：当平台注入器创建失败时的回退实现（仅日志，无实际注入）
struct NullInjector;

impl InputInjector for NullInjector {
    fn key_event(&mut self, key: &str, down: bool) {
        log::warn!("NullInjector: 键 {} {}（注入器未可用）", key, if down { "按下" } else { "松开" });
    }
    fn mouse_move_abs(&mut self, x: i32, y: i32) {
        log::warn!("NullInjector: 鼠标移动到 ({},{})", x, y);
    }
    fn mouse_move_rel(&mut self, dx: i32, dy: i32) {
        log::warn!("NullInjector: 鼠标相对移动 ({},{})", dx, dy);
    }
    fn mouse_button(&mut self, button: MouseButton, down: bool) {
        log::warn!("NullInjector: 鼠标按键 {:?} {}", button, if down { "按下" } else { "松开" });
    }
    fn mouse_wheel(&mut self, dx: i32, dy: i32) {
        log::warn!("NullInjector: 滚轮 ({},{})", dx, dy);
    }
}
