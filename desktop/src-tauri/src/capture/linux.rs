// Linux 截屏：基于 scrap 的 X11 / Wayland 后端
// scrap 在 Linux 上默认使用 XGetImage/XShmGetXImage，Wayland 下需 pipewire（scrap 受限）。
use anyhow::Result;
use scrap::{Capturer as ScrapCapturer, Display};

use super::{Capturer, Frame};

/// Linux X11 截屏器
pub struct LinuxCapturer {
    cap: ScrapCapturer,
    width: u32,
    height: u32,
}

// scrap::Capturer 内部含原始指针（非 Send），但仅在专用截屏线程使用，标记 Send 安全
unsafe impl Send for LinuxCapturer {}

impl LinuxCapturer {
    pub fn new() -> Result<Self> {
        let display = Display::primary()
            .map_err(|e| anyhow::anyhow!("获取主显示器失败（确认 DISPLAY 环境变量）：{}", e))?;
        let cap = ScrapCapturer::new(display)
            .map_err(|e| anyhow::anyhow!("初始化 X11 截屏器失败：{}", e))?;
        let width = cap.width() as u32;
        let height = cap.height() as u32;
        Ok(Self { cap, width, height })
    }
}

impl Capturer for LinuxCapturer {
    fn size(&self) -> (u32, u32) {
        (self.width, self.height)
    }

    fn capture(&mut self) -> Result<Option<Frame>> {
        match self.cap.frame() {
            Ok(buf) => Ok(Some(Frame {
                data: buf.to_vec(),
                width: self.width,
                height: self.height,
            })),
            Err(e) => {
                if e.kind() == std::io::ErrorKind::WouldBlock {
                    Ok(None)
                } else {
                    Err(anyhow::Error::from(e))
                }
            }
        }
    }
}
