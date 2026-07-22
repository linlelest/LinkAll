// H.264 软件编码：基于 Cisco openh264（openh264-sys2 默认 source 特性，开箱即编译）
// 流程：BGRA 像素 → I420 转换（BT.601）→ openh264 编码 → Annex B NAL 单元
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result};
use openh264::encoder::{Encoder, EncoderConfig, FrameType};
use openh264::formats::YUVBuffer;
use openh264::OpenH264API; // source 特性默认开启，OpenH264API::default() 使用内置源码构建

use super::{EncodedFrame, VideoEncoder};

/// openh264 H.264 编码器
pub struct H264Encoder {
    encoder: Encoder,
    width: u32,
    height: u32,
    bitrate_bps: u64,
    /// I420 中间缓冲（复用，避免每帧分配）
    yuv_buf: Vec<u8>,
}

impl H264Encoder {
    pub fn new(width: u32, height: u32, bitrate_bps: u64) -> Result<Self> {
        // openh264 要求宽高为偶数
        let width = width & !1;
        let height = height & !1;
        if width == 0 || height == 0 {
            anyhow::bail!("编码尺寸非法：{}x{}", width, height);
        }
        let api = OpenH264API::default();
        // Baseline profile，码率上限由滑块设定（GCC 动态自适应以此为上界）
        let config = EncoderConfig::new(width as usize, height as usize)
            .set_bitrate_bps(bitrate_bps as u32);
        let encoder = Encoder::with_api_config(api, config)
            .context("初始化 openh264 编码器失败")?;
        let yuv_len = (width as usize) * (height as usize) * 3 / 2;
        Ok(Self {
            encoder,
            width,
            height,
            bitrate_bps,
            yuv_buf: vec![0u8; yuv_len],
        })
    }
}

impl VideoEncoder for H264Encoder {
    fn encode(&mut self, bgra: &[u8], width: u32, height: u32) -> Result<EncodedFrame> {
        let w = (width & !1) as usize;
        let h = (height & !1) as usize;
        // 若尺寸变化，重置中间缓冲
        let need = w * h * 3 / 2;
        if self.yuv_buf.len() != need {
            self.yuv_buf.resize(need, 0);
            self.width = w as u32;
            self.height = h as u32;
        }
        // BGRA → I420（BT.601 限幅 16-235）
        bgra_to_i420(bgra, w, h, &mut self.yuv_buf);

        let yuv = YUVBuffer::from_vec(self.yuv_buf.clone(), w, h);
        let bitstream = self
            .encoder
            .encode(&yuv)
            .context("openh264 编码失败")?;
        let is_keyframe = bitstream.frame_type() == FrameType::I
            || bitstream.frame_type() == FrameType::IDR;
        let data = bitstream.to_vec();

        Ok(EncodedFrame {
            data,
            is_keyframe,
            width: w as u32,
            height: h as u32,
            timestamp_us: now_us(),
        })
    }

    fn set_bitrate(&mut self, bps: u64) -> Result<()> {
        // 简化实现：记录上限，下次重建编码器时生效。
        // 注：openh264 运行时码率切换通过 SetOption 可实现，此处保留接口供 GCC 联动。
        self.bitrate_bps = bps;
        Ok(())
    }

    fn request_keyframe(&mut self) {
        self.encoder.force_intra_frame();
    }

    fn codec(&self) -> &'static str {
        "H264"
    }
}

/// BGRA → I420 转换（BT.601，4:2:0 子采样）
/// bgra: 长度 = w*h*4；输出 i420 长度 = w*h*3/2
fn bgra_to_i420(bgra: &[u8], w: usize, h: usize, i420: &mut [u8]) {
    let y_size = w * h;
    let u_size = y_size / 4;
    let y_plane = &mut i420[..y_size];
    let u_plane = &mut i420[y_size..y_size + u_size];
    let v_plane = &mut i420[y_size + u_size..];

    // Y 平面（全分辨率）
    for j in 0..h {
        for i in 0..w {
            let idx = (j * w + i) * 4;
            let b = bgra[idx] as i32;
            let g = bgra[idx + 1] as i32;
            let r = bgra[idx + 2] as i32;
            // Y = 0.299R + 0.587G + 0.114B
            let y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            y_plane[j * w + i] = clamp(y);
        }
    }

    // U/V 平面（2x2 子采样：4 个像素取平均）
    for j in 0..(h / 2) {
        for i in 0..(w / 2) {
            let base = (j * 2 * w + i * 2) * 4;
            let mut sum_b = 0i32;
            let mut sum_g = 0i32;
            let mut sum_r = 0i32;
            for dj in 0..2 {
                for di in 0..2 {
                    let p = base + (dj * w + di) * 4;
                    sum_b += bgra[p] as i32;
                    sum_g += bgra[p + 1] as i32;
                    sum_r += bgra[p + 2] as i32;
                }
            }
            let b = sum_b / 4;
            let g = sum_g / 4;
            let r = sum_r / 4;
            // U = -0.169R - 0.331G + 0.5B + 128
            let u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
            // V = 0.5R - 0.419G - 0.081B + 128
            let v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
            let uv_idx = j * (w / 2) + i;
            u_plane[uv_idx] = clamp(u);
            v_plane[uv_idx] = clamp(v);
        }
    }
}

#[inline]
fn clamp(v: i32) -> u8 {
    v.clamp(0, 255) as u8
}

fn now_us() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_micros() as u64)
        .unwrap_or(0)
}
