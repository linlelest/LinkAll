// 系统托盘模块
// 创建托盘图标与右键菜单：显示设置 / 暂停服务 / 退出。
// 点击托盘图标时切换设置窗口的显示/隐藏。
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Emitter, Manager};

/// 托盘菜单项 ID
const SHOW_SETTINGS: &str = "show_settings";
const PAUSE_SERVICE: &str = "pause_service";
const QUIT: &str = "quit";

/// 初始化系统托盘
pub fn init_tray(app: &AppHandle) -> tauri::Result<()> {
    let show = MenuItem::with_id(app, SHOW_SETTINGS, "显示设置", true, None::<&str>)?;
    let pause = MenuItem::with_id(app, PAUSE_SERVICE, "暂停服务", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, QUIT, "退出", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show, &pause, &quit])?;

    // 加载托盘图标：优先使用应用默认图标
    let icon = load_tray_icon(app);

    TrayIconBuilder::with_id("main-tray")
        .icon(icon)
        .tooltip("LinkALL 桌面被控端")
        .menu(&menu)
        .on_menu_event(|app, event| {
            match event.id.as_ref() {
                SHOW_SETTINGS => {
                    show_settings_window(app);
                }
                PAUSE_SERVICE => {
                    log::info!("用户请求暂停服务");
                    let _ = app.emit("tray-action", "pause");
                }
                QUIT => {
                    log::info!("用户请求退出应用");
                    app.exit(0);
                }
                _ => {}
            }
        })
        .on_tray_icon_event(|tray, event| {
            // 左键点击切换设置窗口
            if let tauri::tray::TrayIconEvent::Click {
                button: tauri::tray::MouseButton::Left,
                button_state: tauri::tray::MouseButtonState::Up,
                ..
            } = event
            {
                let app = tray.app_handle();
                show_settings_window(app);
            }
        })
        .build(app)?;
    log::info!("系统托盘已初始化");
    Ok(())
}

/// 显示/切换设置窗口
fn show_settings_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("settings") {
        if window.is_visible().unwrap_or(false) {
            let _ = window.hide();
        } else {
            let _ = window.show();
            let _ = window.set_focus();
        }
    }
}

/// 加载托盘图标（从内嵌资源，'static 生命周期）
fn load_tray_icon(_app: &AppHandle) -> tauri::image::Image<'static> {
    // 从内嵌 PNG 文件加载，确保返回 'static 生命周期的图像
    let bytes = include_bytes!("../icons/icon.png");
    tauri::image::Image::from_bytes(bytes.as_slice())
        .unwrap_or_else(|_| tauri::image::Image::new(&[], 0, 0))
}
