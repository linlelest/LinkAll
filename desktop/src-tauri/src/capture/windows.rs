// Windows 截屏：基于 scrap 的 DXGI Desktop Duplication 后端
// scrap 在 Windows 上默认使用 DXGI 1.x，零拷贝 GPU 纹理 → 系统内存 BGRA。
use anyhow::Result;
use scrap::{Capturer as ScrapCapturer, Display};

use super::{Capturer, Frame};

/// Windows DXGI 截屏器
pub struct WindowsCapturer {
    cap: ScrapCapturer,
    width: u32,
    height: u32,
}

impl WindowsCapturer {
    pub fn new() -> Result<Self> {
        let display = Display::primary()
            .map_err(|e| anyhow::anyhow!("获取主显示器失败：{}", e))?;
        let cap = ScrapCapturer::new(display)
            .map_err(|e| anyhow::anyhow!("初始化 DXGI 截屏器失败：{}", e))?;
        let width = cap.width() as u32;
        let height = cap.height() as u32;
        Ok(Self { cap, width, height })
    }
}

impl Capturer for WindowsCapturer {
    fn size(&self) -> (u32, u32) {
        (self.width, self.height)
    }

    fn capture(&mut self) -> Result<Option<Frame>> {
        match self.cap.frame() {
            Ok(buf) => {
                // scrap 返回的缓冲区由 capturer 持有，需拷贝以独立生命周期
                Ok(Some(Frame {
                    data: buf.to_vec(),
                    width: self.width,
                    height: self.height,
                }))
            }
            Err(e) => {
                // WouldBlock：桌面尚无新帧（Windows DXGI 在帧无变化时返回此错误）
                if e.kind() == std::io::ErrorKind::WouldBlock {
                    Ok(None)
                } else {
                    Err(anyhow::Error::from(e))
                }
            }
        }
    }
}
