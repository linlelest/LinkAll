// LinkALL 桌面被控端 - 程序入口
// 防止 Windows 发布构建时弹出控制台窗口
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    linkall_desktop_lib::run()
}
