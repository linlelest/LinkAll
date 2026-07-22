// Windows 键鼠注入：Win32 SendInput API
// 键盘走 KEYBDINPUT + 扫描码（wVk=0，KEYEVENTF_SCANCODE），兼容键盘布局无关输入；
// 鼠标走 MOUSEINPUT，绝对坐标归一化到 0..65535（MOUSEEVENTF_ABSOLUTE）。
use anyhow::Result;
use windows::Win32::UI::Input::KeyboardAndMouse::{
    KEYBD_EVENT_FLAGS, KEYBDINPUT, KEYEVENTF_EXTENDEDKEY, KEYEVENTF_KEYUP, KEYEVENTF_SCANCODE,
    MOUSE_DATA, MOUSE_EVENT_FLAGS, MOUSEINPUT, MOUSEEVENTF_ABSOLUTE, MOUSEEVENTF_HWHEEL,
    MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP,
    MOUSEEVENTF_MOVE, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_WHEEL,
    MOUSEEVENTF_XDOWN, MOUSEEVENTF_XUP, INPUT, INPUT_0, INPUT_KEYBOARD, INPUT_MOUSE,
    SendInput, VIRTUAL_KEY, XBUTTON1, XBUTTON2,
};
use windows::Win32::UI::WindowsAndMessaging::{GetSystemMetrics, SM_CXSCREEN, SM_CYSCREEN};

use super::{InputInjector, MouseButton, lookup_key};

/// Win32 SendInput 注入器
pub struct Win32Input {
    screen_w: i32,
    screen_h: i32,
}

impl Win32Input {
    pub fn new() -> Result<Self> {
        let (w, h) = unsafe {
            (GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN))
        };
        log::info!("Win32 注入器就绪，主屏 {}x{}", w, h);
        Ok(Self {
            screen_w: w.max(1),
            screen_h: h.max(1),
        })
    }

    /// 发送一次键盘事件（扫描码）
    fn send_key(&mut self, scan: u16, extended: bool, down: bool) {
        // 用原始 u32 运算，避免依赖 newtype 的 BitOr 派生
        let mut flags: u32 = KEYEVENTF_SCANCODE.0;
        if extended {
            flags |= KEYEVENTF_EXTENDEDKEY.0;
        }
        if !down {
            flags |= KEYEVENTF_KEYUP.0;
        }
        let input = INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: VIRTUAL_KEY(0),
                    wScan: scan,
                    dwFlags: KEYBD_EVENT_FLAGS(flags),
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        };
        unsafe {
            SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        }
    }

    /// 发送一次鼠标事件
    fn send_mouse(&mut self, flags: u32, dx: i32, dy: i32, data: u32) {
        let input = INPUT {
            r#type: INPUT_MOUSE,
            Anonymous: INPUT_0 {
                mi: MOUSEINPUT {
                    dx,
                    dy,
                    mouseData: MOUSE_DATA(data),
                    dwFlags: MOUSE_EVENT_FLAGS(flags),
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        };
        unsafe {
            SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        }
    }
}

impl InputInjector for Win32Input {
    fn key_event(&mut self, key: &str, down: bool) {
        if let Some(pk) = lookup_key(key) {
            self.send_key(pk.windows, pk.extended, down);
        } else {
            log::warn!("未知键码：{}", key);
        }
    }

    fn mouse_move_abs(&mut self, x: i32, y: i32) {
        // 归一化到 0..65535（覆盖主屏）
        let nx = (x.clamp(0, self.screen_w - 1) * 65535) / (self.screen_w - 1).max(1);
        let ny = (y.clamp(0, self.screen_h - 1) * 65535) / (self.screen_h - 1).max(1);
        self.send_mouse(
            MOUSEEVENTF_MOVE.0 | MOUSEEVENTF_ABSOLUTE.0,
            nx,
            ny,
            0,
        );
    }

    fn mouse_move_rel(&mut self, dx: i32, dy: i32) {
        self.send_mouse(MOUSEEVENTF_MOVE.0, dx, dy, 0);
    }

    fn mouse_button(&mut self, button: MouseButton, down: bool) {
        // XBUTTON1/XBUTTON2 在 windows-rs 中为 u32 常量，直接取值
        const X1: u32 = 0x0001;
        const X2: u32 = 0x0002;
        let (down_flag, up_flag, data): (u32, u32, u32) = match button {
            MouseButton::Left => (MOUSEEVENTF_LEFTDOWN.0, MOUSEEVENTF_LEFTUP.0, 0),
            MouseButton::Right => (MOUSEEVENTF_RIGHTDOWN.0, MOUSEEVENTF_RIGHTUP.0, 0),
            MouseButton::Middle => (MOUSEEVENTF_MIDDLEDOWN.0, MOUSEEVENTF_MIDDLEUP.0, 0),
            MouseButton::Back => (MOUSEEVENTF_XDOWN.0, MOUSEEVENTF_XUP.0, X1),
            MouseButton::Forward => (MOUSEEVENTF_XDOWN.0, MOUSEEVENTF_XUP.0, X2),
        };
        let flag = if down { down_flag } else { up_flag };
        self.send_mouse(flag, 0, 0, data);
    }

    fn mouse_wheel(&mut self, delta_x: i32, delta_y: i32) {
        // WHEEL_DELTA = 120
        if delta_y != 0 {
            self.send_mouse(MOUSEEVENTF_WHEEL.0, 0, 0, (delta_y * 120) as u32);
        }
        if delta_x != 0 {
            self.send_mouse(MOUSEEVENTF_HWHEEL.0, 0, 0, (delta_x * 120) as u32);
        }
    }
}
