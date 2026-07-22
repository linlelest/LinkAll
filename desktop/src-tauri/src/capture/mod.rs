// 屏幕截屏模块
// 跨平台抽象：mod.rs 定义类型与采集循环，windows.rs / linux.rs 分别用 scrap 实现
// （Windows 走 DXGI Desktop Duplication，Linux 走 X11/Wayland）。
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use anyhow::Result;
use tokio::sync::mpsc;

pub mod windows;
pub mod linux;

/// 一帧屏幕原始像素（BGRA 32bpp，scrap 默认输出格式）
pub struct Frame {
    /// BGRA 像素数据，长度 = width * height * 4
    pub data: Vec<u8>,
    pub width: u32,
    pub height: u32,
}

impl Frame {
    /// 长度（像素数）
    pub fn len(&self) -> usize {
        self.data.len() / 4
    }
    pub fn is_empty(&self) -> bool {
        self.data.is_empty()
    }
}

/// 截屏器 trait：平台无关接口
pub trait Capturer {
    /// 屏幕宽高
    fn size(&self) -> (u32, u32);
    /// 捕获一帧；返回 Ok(None) 表示暂无新帧（WouldBlock），Ok(Some) 为新帧
    fn capture(&mut self) -> Result<Option<Frame>>;
}

/// 创建平台截屏器
pub fn new_capturer() -> Result<Box<dyn Capturer + Send>> {
    #[cfg(target_os = "windows")]
    {
        Ok(Box::new(windows::WindowsCapturer::new()?))
    }
    #[cfg(target_os = "linux")]
    {
        Ok(Box::new(linux::LinuxCapturer::new()?))
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    {
        anyhow::bail!("当前平台不支持截屏")
    }
}

/// 返回主屏分辨率（用于鼠标绝对坐标归一化）
pub fn screen_size() -> (u32, u32) {
    new_capturer().map(|c| c.size()).unwrap_or((1920, 1080))
}

/// 采集循环：在专用线程中按目标 FPS 截屏，通过 mpsc 推送给编码/发送端。
/// stop 置 true 时退出。
pub fn capture_loop(
    mut capturer: Box<dyn Capturer + Send>,
    fps: u32,
    tx: mpsc::Sender<Frame>,
    stop: Arc<AtomicBool>,
) {
    let fps = fps.max(1).min(144);
    let interval = Duration::from_secs_f64(1.0 / fps as f64);
    log::info!("截屏循环启动：{}x{} @ {}FPS", capturer.size().0, capturer.size().1, fps);
    loop {
        if stop.load(Ordering::Relaxed) {
            log::info!("截屏循环收到停止信号，退出");
            break;
        }
        let frame_start = Instant::now();
        match capturer.capture() {
            Ok(Some(frame)) => {
                if tx.blocking_send(frame).is_err() {
                    // 接收端已关闭（WebRTC 断开），退出
                    log::info!("截屏接收端已关闭，退出采集循环");
                    break;
                }
            }
            Ok(None) => {
                // WouldBlock：暂无新帧，短暂让出 CPU
                std::thread::sleep(Duration::from_millis(1));
            }
            Err(e) => {
                log::warn!("截屏错误：{:?}，10ms 后重试", e);
                std::thread::sleep(Duration::from_millis(10));
            }
        }
        // 帧率节流
        let elapsed = frame_start.elapsed();
        if elapsed < interval {
            std::thread::sleep(interval - elapsed);
        }
    }
}
