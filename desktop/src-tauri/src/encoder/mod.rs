// 视频编码模块
// 定义编码器 trait 与已编码帧结构，h264.rs 提供 openh264 软件编码实现。
// 默认 H.264 Baseline（兼容性最优），预留 VP9/AV1 切换接口。
use anyhow::Result;
use serde::{Deserialize, Serialize};

pub mod h264;

/// 一帧已编码的视频数据
#[derive(Debug, Clone)]
pub struct EncodedFrame {
    /// 编码后的 NAL 单元（Annex B 格式，含 0x00000001 起始码）
    pub data: Vec<u8>,
    pub is_keyframe: bool,
    pub width: u32,
    pub height: u32,
    /// 捕获时间戳（微秒），用于 RTP 时间戳与抖动缓冲
    pub timestamp_us: u64,
}

/// 视频编码器 trait：平台/编解码无关
pub trait VideoEncoder: Send {
    /// 编码一帧 BGRA 像素
    fn encode(&mut self, bgra: &[u8], width: u32, height: u32) -> Result<EncodedFrame>;
    /// 动态调整码率上限（GCC 算法滑块阈值联动）
    fn set_bitrate(&mut self, bps: u64) -> Result<()>;
    /// 请求下一帧强制为关键帧（IDR）
    fn request_keyframe(&mut self);
    /// 当前编解码器名称
    fn codec(&self) -> &'static str;
}

/// 支持的视频编解码器
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum VideoCodec {
    H264,
    #[allow(dead_code)]
    Vp8,
    #[allow(dead_code)]
    Vp9,
    #[allow(dead_code)]
    Av1,
}

impl VideoCodec {
    pub fn parse(s: &str) -> Self {
        match s.to_ascii_uppercase().as_str() {
            "VP8" => VideoCodec::Vp8,
            "VP9" => VideoCodec::Vp9,
            "AV1" => VideoCodec::Av1,
            _ => VideoCodec::H264,
        }
    }
    /// WebRTC RTP mime type
    pub fn mime(&self) -> &'static str {
        match self {
            VideoCodec::H264 => "video/h264",
            VideoCodec::Vp8 => "video/vp8",
            VideoCodec::Vp9 => "video/vp9",
            VideoCodec::Av1 => "video/av1",
        }
    }
}

/// 工厂：按编解码器创建编码器
pub fn new_encoder(codec: VideoCodec, width: u32, height: u32, bitrate_bps: u64) -> Result<Box<dyn VideoEncoder>> {
    match codec {
        VideoCodec::H264 => Ok(Box::new(h264::H264Encoder::new(width, height, bitrate_bps)?)),
        // VP9/AV1 当前预留：回退到 H264（保证可用性）
        _ => Ok(Box::new(h264::H264Encoder::new(width, height, bitrate_bps)?)),
    }
}
