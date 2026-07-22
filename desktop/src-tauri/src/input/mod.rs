// 键鼠注入模块
// mod.rs 定义跨平台 trait 与 HID 键码映射；windows.rs 走 Win32 SendInput，linux.rs 走 uinput。
// 协议键码采用 JS KeyboardEvent.key 命名（与 shared/protocol.json KeyCode 一致）。
use anyhow::Result;

#[cfg(target_os = "windows")]
pub mod windows;
#[cfg(target_os = "linux")]
pub mod linux;

/// 平台键码：Windows 扫描码（Set 1 Make Code）+ Linux input-event-codes（KEY_*）
#[derive(Debug, Clone, Copy)]
pub struct PlatformKey {
    pub windows: u16,
    pub linux: u16,
    /// 是否为扩展键（Windows 需 KEYEVENTF_EXTENDEDKEY）
    pub extended: bool,
}

/// 鼠标按键
#[derive(Debug, Clone, Copy)]
pub enum MouseButton {
    Left,
    Right,
    Middle,
    Back,
    Forward,
}

impl MouseButton {
    pub fn parse(s: &str) -> Option<Self> {
        Some(match s {
            "left" => MouseButton::Left,
            "right" => MouseButton::Right,
            "middle" => MouseButton::Middle,
            "back" => MouseButton::Back,
            "forward" => MouseButton::Forward,
            _ => return None,
        })
    }
}

/// 键鼠注入器 trait
pub trait InputInjector: Send {
    /// 按下（down=true）/松开（down=false）指定键
    fn key_event(&mut self, key: &str, down: bool);
    /// 鼠标移动到绝对坐标（屏幕像素）
    fn mouse_move_abs(&mut self, x: i32, y: i32);
    /// 鼠标相对移动（像素偏移）
    fn mouse_move_rel(&mut self, dx: i32, dy: i32);
    /// 按下/松开鼠标按键
    fn mouse_button(&mut self, button: MouseButton, down: bool);
    /// 滚轮：deltaY 正=向下，负=向上；deltaX 水平滚动
    fn mouse_wheel(&mut self, delta_x: i32, delta_y: i32);
}

/// 创建平台原生注入器（Windows: SendInput；Linux: uinput）
pub fn new_injector() -> Result<Box<dyn InputInjector>> {
    #[cfg(target_os = "windows")]
    {
        Ok(Box::new(windows::Win32Input::new()?))
    }
    #[cfg(target_os = "linux")]
    {
        Ok(Box::new(linux::UinputInjector::new()?))
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    {
        anyhow::bail!("当前平台不支持键鼠注入")
    }
}

/// 查询键码映射（JS key name → 平台键码）
pub fn lookup_key(name: &str) -> Option<PlatformKey> {
    // 字母键 KeyA-KeyZ
    if name.len() == 4 && name.starts_with("Key") {
        let c = name.as_bytes()[3];
        if (b'A'..=b'Z').contains(&c) {
            let idx = (c - b'A') as usize;
            let (win, lin) = LETTER_MAP[idx];
            return Some(PlatformKey { windows: win, linux: lin, extended: false });
        }
    }
    // 数字键 Digit0-Digit9
    if name.len() == 6 && name.starts_with("Digit") {
        let c = name.as_bytes()[5];
        if (b'0'..=b'9').contains(&c) {
            let idx = (c - b'0') as usize;
            let (win, lin) = DIGIT_MAP[idx];
            return Some(PlatformKey { windows: win, linux: lin, extended: false });
        }
    }
    // 功能键 F1-F12
    if name.len() <= 3 && name.starts_with('F') {
        if let Ok(n) = name[1..].parse::<usize>() {
            if (1..=12).contains(&n) {
                let (win, lin) = FUNC_MAP[n - 1];
                return Some(PlatformKey { windows: win, linux: lin, extended: false });
            }
        }
    }
    // 特殊键
    SPECIAL_MAP.iter().find(|(k, _)| *k == name).map(|(_, v)| *v)
}

// 字母扫描码表（A-Z）
const LETTER_MAP: [(u16, u16); 26] = [
    (0x1E, 30),  (0x30, 48),  (0x2E, 46),  (0x20, 32),  (0x12, 18),  // A-E
    (0x21, 33),  (0x22, 34),  (0x23, 35),  (0x17, 23),  (0x24, 36),  // F-J
    (0x25, 37),  (0x26, 38),  (0x32, 50),  (0x31, 49),  (0x18, 24),  // K-O
    (0x19, 25),  (0x10, 16),  (0x13, 19),  (0x1F, 31),  (0x14, 20),  // P-T
    (0x16, 22),  (0x2F, 47),  (0x11, 17),  (0x2D, 45),  (0x15, 21),  // U-Y
    (0x2C, 44),                                                                  // Z
];

// 数字键扫描码表（0-9，主键盘）
const DIGIT_MAP: [(u16, u16); 10] = [
    (0x0B, 11), (0x02, 2),  (0x03, 3),  (0x04, 4),  (0x05, 5),   // 0-4
    (0x06, 6),  (0x07, 7),  (0x08, 8),  (0x09, 9),  (0x0A, 10),  // 5-9
];

// 功能键扫描码表（F1-F12）
const FUNC_MAP: [(u16, u16); 12] = [
    (0x3B, 59),  (0x3C, 60),  (0x3D, 61),  (0x3E, 62),  (0x3F, 63),  (0x40, 64),
    (0x41, 65),  (0x42, 66),  (0x43, 67),  (0x44, 68),  (0x57, 87),  (0x58, 88),
];

// 特殊键映射表（key name → PlatformKey）
// 扩展键（右侧修饰/方向键/Insert/Delete/Home/End/PageUp/PageDown/NumLock/Enter小键盘）
static SPECIAL_MAP: &[(&str, PlatformKey)] = &[
    ("Enter",       PlatformKey { windows: 0x1C, linux: 28,  extended: false }),
    ("Escape",      PlatformKey { windows: 0x01, linux: 1,   extended: false }),
    ("Backspace",   PlatformKey { windows: 0x0E, linux: 14,  extended: false }),
    ("Tab",         PlatformKey { windows: 0x0F, linux: 15,  extended: false }),
    ("Space",       PlatformKey { windows: 0x39, linux: 57,  extended: false }),
    ("CapsLock",    PlatformKey { windows: 0x3A, linux: 58,  extended: false }),
    ("ShiftLeft",   PlatformKey { windows: 0x2A, linux: 42,  extended: false }),
    ("ShiftRight",  PlatformKey { windows: 0x36, linux: 54,  extended: false }),
    ("ControlLeft", PlatformKey { windows: 0x1D, linux: 29,  extended: false }),
    ("ControlRight",PlatformKey { windows: 0x1D, linux: 97,  extended: true  }),
    ("AltLeft",     PlatformKey { windows: 0x38, linux: 56,  extended: false }),
    ("AltRight",    PlatformKey { windows: 0x38, linux: 100, extended: true  }),
    ("MetaLeft",    PlatformKey { windows: 0x5B, linux: 125, extended: true  }),
    ("MetaRight",   PlatformKey { windows: 0x5C, linux: 126, extended: true  }),
    // 方向键
    ("ArrowUp",     PlatformKey { windows: 0x48, linux: 103, extended: true  }),
    ("ArrowDown",   PlatformKey { windows: 0x50, linux: 108, extended: true  }),
    ("ArrowLeft",   PlatformKey { windows: 0x4B, linux: 105, extended: true  }),
    ("ArrowRight",  PlatformKey { windows: 0x4D, linux: 106, extended: true  }),
    // 编辑键
    ("Insert",      PlatformKey { windows: 0x52, linux: 110, extended: true  }),
    ("Delete",      PlatformKey { windows: 0x53, linux: 111, extended: true  }),
    ("Home",        PlatformKey { windows: 0x47, linux: 102, extended: true  }),
    ("End",         PlatformKey { windows: 0x4F, linux: 107, extended: true  }),
    ("PageUp",      PlatformKey { windows: 0x49, linux: 104, extended: true  }),
    ("PageDown",    PlatformKey { windows: 0x51, linux: 109, extended: true  }),
    ("NumLock",     PlatformKey { windows: 0x45, linux: 69,  extended: true  }),
    ("ScrollLock",  PlatformKey { windows: 0x46, linux: 70,  extended: false }),
    ("Pause",       PlatformKey { windows: 0x46, linux: 119, extended: false }),
    // 符号键
    ("Minus",       PlatformKey { windows: 0x0C, linux: 12,  extended: false }),
    ("Equal",       PlatformKey { windows: 0x0D, linux: 13,  extended: false }),
    ("BracketLeft", PlatformKey { windows: 0x1A, linux: 26,  extended: false }),
    ("BracketRight",PlatformKey { windows: 0x1B, linux: 27,  extended: false }),
    ("Backslash",   PlatformKey { windows: 0x2B, linux: 43,  extended: false }),
    ("Semicolon",   PlatformKey { windows: 0x27, linux: 39,  extended: false }),
    ("Quote",       PlatformKey { windows: 0x28, linux: 40,  extended: false }),
    ("Backquote",   PlatformKey { windows: 0x29, linux: 41,  extended: false }),
    ("Comma",       PlatformKey { windows: 0x33, linux: 51,  extended: false }),
    ("Period",      PlatformKey { windows: 0x34, linux: 52,  extended: false }),
    ("Slash",       PlatformKey { windows: 0x35, linux: 53,  extended: false }),
];
