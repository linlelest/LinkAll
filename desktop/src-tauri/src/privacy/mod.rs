// 防窥屏模块
// 当远程控制会话激活时，阻止系统进入休眠/屏保状态，并覆盖本地屏幕
// 以防止旁观者窥视被控画面。
// Windows：SetThreadExecutionState + 顶层黑色覆盖窗口（CreateWindowExW）
// Linux：xset 禁用屏保 + 顶层 X11 覆盖窗口（动态加载 libX11）
use anyhow::Result;

#[cfg(target_os = "windows")]
pub mod windows;
#[cfg(target_os = "linux")]
pub mod linux;

/// 防窥屏控制器 trait
pub trait PrivacyScreen: Send {
    /// 开启防窥屏：阻止休眠 + 显示覆盖窗口
    fn enable(&mut self) -> Result<()>;
    /// 关闭防窥屏：恢复休眠策略 + 隐藏覆盖窗口
    fn disable(&mut self) -> Result<()>;
    /// 当前是否已开启
    fn is_enabled(&self) -> bool;
}

/// 创建平台原生防窥屏控制器
pub fn new_privacy_screen() -> Result<Box<dyn PrivacyScreen>> {
    #[cfg(target_os = "windows")]
    {
        Ok(Box::new(windows::WindowsPrivacy::new()?))
    }
    #[cfg(target_os = "linux")]
    {
        Ok(Box::new(linux::LinuxPrivacy::new()?))
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    {
        anyhow::bail!("当前平台不支持防窥屏")
    }
}
