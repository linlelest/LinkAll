// Windows 防窥屏：SetThreadExecutionState 阻止休眠 + CreateWindowExW 顶层覆盖窗口
// 覆盖窗口使用 WS_EX_TOPMOST，黑色画刷（BLACK_BRUSH），铺满屏幕。
use anyhow::{Context, Result};
use windows::core::w;
use windows::Win32::Foundation::{HWND, LPARAM, LRESULT, WPARAM};
use windows::Win32::Graphics::Gdi::{
    BeginPaint, EndPaint, FillRect, GetStockObject, BLACK_BRUSH, HBRUSH, PAINTSTRUCT,
};
use windows::Win32::System::Power::{
    SetThreadExecutionState, ES_CONTINUOUS, ES_DISPLAY_REQUIRED, ES_SYSTEM_REQUIRED,
};
use windows::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DestroyWindow, GetSystemMetrics, RegisterClassExW,
    SetWindowPos, ShowWindow, CS_HREDRAW, CS_VREDRAW, HWND_TOPMOST, SM_CXSCREEN, SM_CYSCREEN,
    SWP_NOACTIVATE, SWP_NOMOVE, SWP_NOSIZE, SWP_SHOWWINDOW, SW_SHOWNORMAL, WNDCLASSEXW,
    WM_ERASEBKGND, WM_PAINT,
    WINDOW_EX_STYLE, WINDOW_STYLE, WS_EX_TOPMOST, WS_POPUP, WS_VISIBLE,
};

use super::PrivacyScreen;

/// Windows 防窥屏控制器
pub struct WindowsPrivacy {
    /// 覆盖窗口句柄
    hwnd: Option<HWND>,
    /// 是否已开启
    enabled: bool,
}

// HWND 为原始指针包装，需手动声明 Send 以满足 PrivacyScreen trait 约束
unsafe impl Send for WindowsPrivacy {}

impl WindowsPrivacy {
    pub fn new() -> Result<Self> {
        Ok(Self {
            hwnd: None,
            enabled: false,
        })
    }

    /// 注册窗口类并创建覆盖窗口
    fn create_overlay(&mut self) -> Result<HWND> {
        let class_name = w!("LinkAllPrivacyOverlay");

        // 注册窗口类（使用黑色画刷填充背景）
        let wc = WNDCLASSEXW {
            cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
            style: CS_HREDRAW | CS_VREDRAW,
            lpfnWndProc: Some(def_window_proc),
            hInstance: Default::default(),
            hCursor: Default::default(),
            hbrBackground: HBRUSH(unsafe { GetStockObject(BLACK_BRUSH) }.0),
            lpszClassName: class_name,
            ..Default::default()
        };

        let atom = unsafe { RegisterClassExW(&wc) };
        if atom == 0 {
            log::debug!("窗口类注册返回 0（可能已注册）");
        }

        let (w, h) = unsafe {
            (GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN))
        };

        let hwnd = unsafe {
            CreateWindowExW(
                WINDOW_EX_STYLE(WS_EX_TOPMOST.0),
                class_name,
                w!("LinkALL"),
                WINDOW_STYLE(WS_POPUP | WS_VISIBLE),
                0,
                0,
                w,
                h,
                None,
                None,
                Default::default(),
                None,
            )
        }
        .context("创建防窥屏覆盖窗口失败")?;

        // 置顶并显示
        unsafe {
            SetWindowPos(
                hwnd,
                Some(HWND_TOPMOST),
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW,
            );
            ShowWindow(hwnd, SW_SHOWNORMAL);
        }

        Ok(hwnd)
    }
}

impl PrivacyScreen for WindowsPrivacy {
    fn enable(&mut self) -> Result<()> {
        if self.enabled {
            return Ok(());
        }
        // 1) 阻止系统休眠
        unsafe {
            SetThreadExecutionState(
                ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED,
            );
        }
        log::info!("已阻止系统休眠");

        // 2) 创建并显示覆盖窗口
        let hwnd = self.create_overlay()?;
        self.hwnd = Some(hwnd);
        self.enabled = true;
        log::info!("防窥屏已开启");
        Ok(())
    }

    fn disable(&mut self) -> Result<()> {
        if !self.enabled {
            return Ok(());
        }
        // 1) 恢复休眠策略
        unsafe {
            SetThreadExecutionState(ES_CONTINUOUS);
        }

        // 2) 销毁覆盖窗口
        if let Some(hwnd) = self.hwnd.take() {
            unsafe {
                let _ = DestroyWindow(hwnd);
            }
        }
        self.enabled = false;
        log::info!("防窥屏已关闭");
        Ok(())
    }

    fn is_enabled(&self) -> bool {
        self.enabled
    }
}

impl Drop for WindowsPrivacy {
    fn drop(&mut self) {
        if self.enabled {
            let _ = self.disable();
        }
    }
}

/// 默认窗口过程：黑色背景由窗口类画刷自动填充
fn def_window_proc(hwnd: HWND, msg: u32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    match msg {
        WM_PAINT => {
            // 仅调用 BeginPaint/EndPaint 验证绘制区域，背景由 hbrBackground 填充
            unsafe {
                let mut ps: PAINTSTRUCT = std::mem::zeroed();
                let _ = BeginPaint(hwnd, &mut ps);
                EndPaint(hwnd, &ps);
            }
            LRESULT(0)
        }
        WM_ERASEBKGND => LRESULT(1),
        _ => unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) },
    }
}
