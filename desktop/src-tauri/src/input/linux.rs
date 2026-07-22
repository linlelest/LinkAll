// Linux 键鼠注入：uinput 虚拟设备（/dev/uinput）
// 创建虚拟键盘+鼠标设备，写入 input_event 事件。
// 需要写权限：用户加入 input 组，或以 root 运行。
use std::ffi::CString;
use std::os::raw::c_int;

use anyhow::Result;
use libc::{c_ulong, close, ioctl, open, write, O_NONBLOCK, O_WRONLY};

use super::{InputInjector, MouseButton, lookup_key};

// === input event 类型与码 ===
const EV_SYN: u16 = 0x00;
const EV_KEY: u16 = 0x01;
const EV_REL: u16 = 0x02;
const SYN_REPORT: u16 = 0x00;

// 鼠标按键码（BTN_*）
const BTN_LEFT: u16 = 0x110;
const BTN_RIGHT: u16 = 0x111;
const BTN_MIDDLE: u16 = 0x112;
const BTN_BACK: u16 = 0x116;
const BTN_FORWARD: u16 = 0x115;

// 相对轴码（REL_*）
const REL_X: u16 = 0x00;
const REL_Y: u16 = 0x01;
const REL_WHEEL: u16 = 0x08;
const REL_HWHEEL: u16 = 0x06;

// === uinput ioctl 编码 ===
// _IOW(type, nr, size): (1<<30) | (size<<16) | (type<<8) | nr
const fn _iow(ty: u32, nr: u32, size: u32) -> u32 {
    (1u32 << 30) | ((size & 0x3fff) << 16) | ((ty & 0xff) << 8) | (nr & 0xff)
}
const fn _io(ty: u32, nr: u32) -> u32 {
    ((ty & 0xff) << 8) | (nr & 0xff)
}

const UINPUT_IOCTL_TYPE: u32 = 0x55;
const UI_SET_EVBIT: u32 = _iow(UINPUT_IOCTL_TYPE, 100, 4);
const UI_SET_KEYBIT: u32 = _iow(UINPUT_IOCTL_TYPE, 101, 4);
const UI_SET_RELBIT: u32 = _iow(UINPUT_IOCTL_TYPE, 102, 4);
const UI_DEV_CREATE: u32 = _io(UINPUT_IOCTL_TYPE, 1);
const UI_DEV_DESTROY: u32 = _io(UINPUT_IOCTL_TYPE, 2);

/// uinput 设备标识
#[repr(C)]
struct InputId {
    bus_type: u16,
    vendor: u16,
    product: u16,
    version: u16,
}

/// UI_DEV_SETUP 参数（结构体大小编码进 ioctl 编号）
#[repr(C)]
struct UinputSetup {
    id: InputId,
    name: [u8; 80],
    ff_effects_max: u32,
}

/// input_event（与内核 ABI 一致）
#[repr(C)]
struct InputEvent {
    time: libc::timeval,
    kind: u16,
    code: u16,
    value: i32,
}

/// uinput 注入器
pub struct UinputInjector {
    fd: c_int,
}

impl UinputInjector {
    pub fn new() -> Result<Self> {
        let path = CString::new("/dev/uinput").unwrap();
        let fd = unsafe { open(path.as_ptr(), O_WRONLY | O_NONBLOCK) };
        if fd < 0 {
            return Err(anyhow::anyhow!(
                "打开 /dev/uinput 失败：{}（需将用户加入 input 组或以 root 运行）",
                std::io::Error::last_os_error()
            ));
        }
        let inj = Self { fd };
        inj.setup_device()?;
        log::info!("uinput 虚拟设备已创建");
        Ok(inj)
    }

    /// 配置设备能力并创建
    fn setup_device(&self) -> Result<()> {
        unsafe {
            // 注册事件类型
            ioctl_evbit(self.fd, UI_SET_EVBIT, EV_KEY as i32);
            ioctl_evbit(self.fd, UI_SET_EVBIT, EV_REL as i32);
            // 注册所有支持的键码
            for code in supported_key_codes() {
                ioctl_evbit(self.fd, UI_SET_KEYBIT, code as i32);
            }
            // 注册鼠标按键
            for code in [BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_BACK, BTN_FORWARD] {
                ioctl_evbit(self.fd, UI_SET_KEYBIT, code as i32);
            }
            // 注册相对轴
            for code in [REL_X, REL_Y, REL_WHEEL, REL_HWHEEL] {
                ioctl_evbit(self.fd, UI_SET_RELBIT, code as i32);
            }
            // 配置设备并创建
            let mut setup = UinputSetup {
                id: InputId { bus_type: 0x03, vendor: 0x1234, product: 0x5678, version: 1 },
                name: [0u8; 80],
                ff_effects_max: 0,
            };
            let dev_name = b"LinkALL Virtual Input";
            let n = dev_name.len().min(79);
            setup.name[..n].copy_from_slice(&dev_name[..n]);
            let dev_setup = _iow(UINPUT_IOCTL_TYPE, 3, std::mem::size_of::<UinputSetup>() as u32);
            if ioctl(self.fd, dev_setup as c_ulong, &setup as *const _) < 0 {
                return Err(anyhow::anyhow!("UI_DEV_SETUP 失败：{}", std::io::Error::last_os_error()));
            }
            if ioctl(self.fd, UI_DEV_CREATE as c_ulong, 0) < 0 {
                return Err(anyhow::anyhow!("UI_DEV_CREATE 失败：{}", std::io::Error::last_os_error()));
            }
        }
        // 等待设备节点就绪
        std::thread::sleep(std::time::Duration::from_millis(50));
        Ok(())
    }

    /// 写入一个事件
    fn write_event(&self, kind: u16, code: u16, value: i32) {
        let ev = InputEvent {
            time: libc::timeval { tv_sec: 0, tv_usec: 0 },
            kind,
            code,
            value,
        };
        let size = std::mem::size_of::<InputEvent>();
        unsafe {
            write(
                self.fd,
                &ev as *const _ as *const libc::c_void,
                size,
            );
        }
    }

    /// 同步事件（刷新事件队列）
    fn sync(&self) {
        self.write_event(EV_SYN, SYN_REPORT, 0);
    }
}

impl InputInjector for UinputInjector {
    fn key_event(&mut self, key: &str, down: bool) {
        if let Some(pk) = lookup_key(key) {
            let v = if down { 1 } else { 0 };
            self.write_event(EV_KEY, pk.linux, v);
            self.sync();
        } else {
            log::warn!("未知键码：{}", key);
        }
    }

    fn mouse_move_abs(&mut self, x: i32, y: i32) {
        // uinput 无绝对坐标概念，转为相对移动（简化：直接归一化移动量）
        // 注：绝对定位需先注册 EV_ABS + ABS_X/ABS_Y。此处采用相对移动近似。
        self.mouse_move_rel(x, y);
    }

    fn mouse_move_rel(&mut self, dx: i32, dy: i32) {
        if dx != 0 {
            self.write_event(EV_REL, REL_X, dx);
        }
        if dy != 0 {
            self.write_event(EV_REL, REL_Y, dy);
        }
        self.sync();
    }

    fn mouse_button(&mut self, button: MouseButton, down: bool) {
        let code = match button {
            MouseButton::Left => BTN_LEFT,
            MouseButton::Right => BTN_RIGHT,
            MouseButton::Middle => BTN_MIDDLE,
            MouseButton::Back => BTN_BACK,
            MouseButton::Forward => BTN_FORWARD,
        };
        let v = if down { 1 } else { 0 };
        self.write_event(EV_KEY, code, v);
        self.sync();
    }

    fn mouse_wheel(&mut self, delta_x: i32, delta_y: i32) {
        if delta_y != 0 {
            self.write_event(EV_REL, REL_WHEEL, delta_y);
        }
        if delta_x != 0 {
            self.write_event(EV_REL, REL_HWHEEL, delta_x);
        }
        self.sync();
    }
}

impl Drop for UinputInjector {
    fn drop(&mut self) {
        unsafe {
            ioctl(self.fd, UI_DEV_DESTROY as c_ulong, 0);
            close(self.fd);
        }
        log::info!("uinput 虚拟设备已销毁");
    }
}

/// ioctl 设置位
unsafe fn ioctl_evbit(fd: c_int, request: u32, value: c_int) {
    ioctl(fd, request as c_ulong, value);
}

/// 返回所有支持的键码（Linux KEY_*）
fn supported_key_codes() -> Vec<u16> {
    let mut codes = Vec::new();
    // 字母 + 数字 + 功能键（通过空名查询）
    for c in b'A'..=b'Z' {
        let name = format!("Key{}", c as char);
        if let Some(pk) = lookup_key(&name) {
            codes.push(pk.linux);
        }
    }
    for c in b'0'..=b'9' {
        let name = format!("Digit{}", c as char);
        if let Some(pk) = lookup_key(&name) {
            codes.push(pk.linux);
        }
    }
    for n in 1..=12u8 {
        let name = format!("F{}", n);
        if let Some(pk) = lookup_key(&name) {
            codes.push(pk.linux);
        }
    }
    for name in [
        "Enter", "Escape", "Backspace", "Tab", "Space", "CapsLock", "ShiftLeft", "ShiftRight",
        "ControlLeft", "ControlRight", "AltLeft", "AltRight", "MetaLeft", "MetaRight",
        "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Insert", "Delete", "Home", "End",
        "PageUp", "PageDown", "NumLock", "ScrollLock", "Minus", "Equal", "BracketLeft",
        "BracketRight", "Backslash", "Semicolon", "Quote", "Backquote", "Comma", "Period", "Slash",
    ] {
        if let Some(pk) = lookup_key(name) {
            codes.push(pk.linux);
        }
    }
    codes
}
