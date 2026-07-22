// Linux 防窥屏：通过 xset 禁用屏保/DPMS 阻止系统休眠；
// 覆盖窗口通过动态加载 libX11 创建顶层黑色窗口（dlopen 模式，避免编译期依赖）。
use std::ffi::{c_char, c_int, c_long, c_uint, c_ulong, CString};
use std::ptr;

use anyhow::{anyhow, Context, Result};

use super::PrivacyScreen;

/// X11 常量
const BLACK_PIXEL: c_ulong = 0;
const INPUT_OUTPUT: c_int = 1;
const CW_BACK_PIXEL: c_uint = 1;
const CW_OVERRIDE_REDIRECT: c_uint = 1;
const EXPOSURE_MASK: c_long = 1 << 15;

/// dlopen/libX11 函数签名
type XOpenDisplayFn = unsafe extern "C" fn(display: *const c_char) -> *mut XDisplay;
type XDefaultRootWindowFn = unsafe extern "C" fn(disp: *mut XDisplay) -> c_ulong;
type XCreateSimpleWindowFn = unsafe extern "C" fn(
    disp: *mut XDisplay,
    parent: c_ulong,
    x: c_int,
    y: c_int,
    width: c_uint,
    height: c_uint,
    border_width: c_uint,
    border: c_ulong,
    background: c_ulong,
) -> c_ulong;
type XMapWindowFn = unsafe extern "C" fn(disp: *mut XDisplay, win: c_ulong) -> c_int;
type XUnmapWindowFn = unsafe extern "C" fn(disp: *mut XDisplay, win: c_ulong) -> c_int;
type XDestroyWindowFn = unsafe extern "C" fn(disp: *mut XDisplay, win: c_ulong) -> c_int;
type XFlushFn = unsafe extern "C" fn(disp: *mut XDisplay) -> c_int;
type XCloseDisplayFn = unsafe extern "C" fn(disp: *mut XDisplay) -> c_int;
type XDisplayWidthFn = unsafe extern "C" fn(disp: *mut XDisplay, screen: c_int) -> c_int;
type XDisplayHeightFn = unsafe extern "C" fn(disp: *mut XDisplay, screen: c_int) -> c_int;
type XDefaultScreenFn = unsafe extern "C" fn(disp: *mut XDisplay) -> c_int;

/// XDisplay 不透明指针
#[repr(C)]
struct XDisplay {
    _opaque: [u8; 0],
}

/// libX11 动态加载的函数集合
struct X11Functions {
    handle: *mut libc::c_void,
    open_display: XOpenDisplayFn,
    default_root_window: XDefaultRootWindowFn,
    create_simple_window: XCreateSimpleWindowFn,
    map_window: XMapWindowFn,
    unmap_window: XUnmapWindowFn,
    destroy_window: XDestroyWindowFn,
    flush: XFlushFn,
    close_display: XCloseDisplayFn,
    display_width: XDisplayWidthFn,
    display_height: XDisplayHeightFn,
    default_screen: XDefaultScreenFn,
}

impl X11Functions {
    /// 动态加载 libX11.so.6
    unsafe fn load() -> Result<Self> {
        let name = CString::new("libX11.so.6").unwrap();
        let handle = libc::dlopen(name.as_ptr(), libc::RTLD_LAZY);
        if handle.is_null() {
            return Err(anyhow!("无法加载 libX11.so.6：{}", dlerror()));
        }
        macro_rules! load_sym {
            ($name:expr, $type:ty) => {{
                let sym = CString::new($name).unwrap();
                let ptr = libc::dlsym(handle, sym.as_ptr());
                if ptr.is_null() {
                    return Err(anyhow!("无法加载 X11 符号：{}", $name));
                }
                std::mem::transmute::<*mut libc::c_void, $type>(ptr)
            }};
        }
        Ok(Self {
            handle,
            open_display: load_sym!("XOpenDisplay", XOpenDisplayFn),
            default_root_window: load_sym!("XDefaultRootWindow", XDefaultRootWindowFn),
            create_simple_window: load_sym!("XCreateSimpleWindow", XCreateSimpleWindowFn),
            map_window: load_sym!("XMapWindow", XMapWindowFn),
            unmap_window: load_sym!("XUnmapWindow", XUnmapWindowFn),
            destroy_window: load_sym!("XDestroyWindow", XDestroyWindowFn),
            flush: load_sym!("XFlush", XFlushFn),
            close_display: load_sym!("XCloseDisplay", XCloseDisplayFn),
            display_width: load_sym!("XDisplayWidth", XDisplayWidthFn),
            display_height: load_sym!("XDisplayHeight", XDisplayHeightFn),
            default_screen: load_sym!("XDefaultScreen", XDefaultScreenFn),
        })
    }
}

impl Drop for X11Functions {
    fn drop(&mut self) {
        if !self.handle.is_null() {
            unsafe {
                libc::dlclose(self.handle);
            }
        }
    }
}

/// Linux 防窥屏控制器
pub struct LinuxPrivacy {
    /// X11 函数集（懒加载）
    x11: Option<X11Functions>,
    /// X11 Display 连接
    display: *mut XDisplay,
    /// 覆盖窗口 ID
    window: c_ulong,
    /// 是否已开启
    enabled: bool,
    /// xset 前的屏保状态（用于恢复）
    /// 注：xset 无查询命令的简单实现，恢复时直接重设默认值
    was_screensaver_on: bool,
}

// X11 Display 是线程局部资源，但此处仅用于覆盖窗口线程，Send 安全
unsafe impl Send for LinuxPrivacy {}

impl LinuxPrivacy {
    pub fn new() -> Result<Self> {
        Ok(Self {
            x11: None,
            display: ptr::null_mut(),
            window: 0,
            enabled: false,
            was_screensaver_on: true,
        })
    }

    /// 禁用屏保与 DPMS（通过 xset 子进程）
    fn disable_screensaver(&mut self) -> Result<()> {
        // 保存当前状态（简化：假设默认开启）
        self.was_screensaver_on = true;
        for cmd in &["s", "off", "s", "noblank", "-dpms"] {
            let _ = std::process::Command::new("xset").arg(cmd).output();
        }
        log::info!("已通过 xset 禁用屏保与 DPMS");
        Ok(())
    }

    /// 恢复屏保与 DPMS
    fn restore_screensaver(&mut self) -> Result<()> {
        if self.was_screensaver_on {
            for cmd in &["s", "on", "+dpms"] {
                let _ = std::process::Command::new("xset").arg(cmd).output();
            }
            log::info!("已恢复屏保与 DPMS");
        }
        Ok(())
    }

    /// 通过动态加载 libX11 创建顶层覆盖窗口
    fn create_x11_overlay(&mut self) -> Result<()> {
        unsafe {
            let x11 = match &self.x11 {
                Some(x) => x,
                None => {
                    let x = X11Functions::load().context("加载 libX11 失败")?;
                    self.x11 = Some(x);
                    self.x11.as_ref().unwrap()
                }
            };

            let display_name = ptr::null();
            let disp = (x11.open_display)(display_name);
            if disp.is_null() {
                return Err(anyhow!("XOpenDisplay 失败：无法连接 X 服务器"));
            }
            self.display = disp;

            let screen = (x11.default_screen)(disp);
            let width = (x11.display_width)(disp, screen) as c_uint;
            let height = (x11.display_height)(disp, screen) as c_uint;
            let root = (x11.default_root_window)(disp);

            // 创建全屏黑色窗口
            let win = (x11.create_simple_window)(
                disp,
                root,
                0,
                0,
                width,
                height,
                0,
                BLACK_PIXEL,
                BLACK_PIXEL,
            );
            if win == 0 {
                (x11.close_display)(disp);
                self.display = ptr::null_mut();
                return Err(anyhow!("XCreateSimpleWindow 失败"));
            }
            self.window = win;

            // 映射窗口并刷新
            (x11.map_window)(disp, win);
            (x11.flush)(disp);
        }
        Ok(())
    }

    /// 销毁 X11 覆盖窗口
    fn destroy_x11_overlay(&mut self) {
        if let Some(x11) = &self.x11 {
            if !self.display.is_null() && self.window != 0 {
                unsafe {
                    (x11.unmap_window)(self.display, self.window);
                    (x11.destroy_window)(self.display, self.window);
                    (x11.flush)(self.display);
                    (x11.close_display)(self.display);
                }
                self.display = ptr::null_mut();
                self.window = 0;
            }
        }
    }
}

impl PrivacyScreen for LinuxPrivacy {
    fn enable(&mut self) -> Result<()> {
        if self.enabled {
            return Ok(());
        }
        // 1) 禁用屏保与 DPMS
        self.disable_screensaver()?;

        // 2) 尝试创建 X11 覆盖窗口
        match self.create_x11_overlay() {
            Ok(_) => log::info!("X11 覆盖窗口已创建"),
            Err(e) => {
                log::warn!("创建 X11 覆盖窗口失败：{:?}（仅禁用屏保）", e);
            }
        }
        self.enabled = true;
        log::info!("防窥屏已开启");
        Ok(())
    }

    fn disable(&mut self) -> Result<()> {
        if !self.enabled {
            return Ok(());
        }
        // 1) 销毁覆盖窗口
        self.destroy_x11_overlay();

        // 2) 恢复屏保与 DPMS
        self.restore_screensaver()?;
        self.enabled = false;
        log::info!("防窥屏已关闭");
        Ok(())
    }

    fn is_enabled(&self) -> bool {
        self.enabled
    }
}

impl Drop for LinuxPrivacy {
    fn drop(&mut self) {
        if self.enabled {
            let _ = self.disable();
        }
    }
}

/// 获取 dlerror 字符串
fn dlerror() -> String {
    unsafe {
        let ptr = libc::dlerror();
        if ptr.is_null() {
            "未知错误".to_string()
        } else {
            std::ffi::CStr::from_ptr(ptr as *const c_char)
                .to_string_lossy()
                .into_owned()
        }
    }
}
