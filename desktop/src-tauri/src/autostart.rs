// 开机自启模块
// Windows：通过 reg.exe 操作注册表 HKCU\Software\Microsoft\Windows\CurrentVersion\Run
// Linux：创建/删除 ~/.config/autostart/linkall.desktop
use anyhow::Result;

/// 注册表 Run 键路径
#[cfg(target_os = "windows")]
const REG_RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
/// 注册表值名
const APP_NAME: &str = "LinkALL";

/// 设置开机自启状态
pub fn set_autostart(enabled: bool) -> Result<()> {
    #[cfg(target_os = "windows")]
    {
        set_autostart_windows(enabled)
    }
    #[cfg(target_os = "linux")]
    {
        set_autostart_linux(enabled)
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    {
        log::warn!("当前平台不支持开机自启");
        Ok(())
    }
}

/// 查询当前开机自启状态
pub fn is_autostart_enabled() -> bool {
    #[cfg(target_os = "windows")]
    {
        is_autostart_enabled_windows()
    }
    #[cfg(target_os = "linux")]
    {
        is_autostart_enabled_linux()
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    {
        false
    }
}

// ==================== Windows 实现（reg.exe） ====================

#[cfg(target_os = "windows")]
fn set_autostart_windows(enabled: bool) -> Result<()> {
    let exe_path = std::env::current_exe()
        .map_err(|e| anyhow::anyhow!("获取可执行文件路径失败：{}", e))?;
    let exe_path = exe_path.to_string_lossy().to_string();

    if enabled {
        // 写入注册表：reg add "HKCU\...\Run" /v LinkALL /t REG_SZ /d "<path>" /f
        let output = std::process::Command::new("reg")
            .args([
                "add",
                REG_RUN_KEY,
                "/v",
                APP_NAME,
                "/t",
                "REG_SZ",
                "/d",
                &exe_path,
                "/f",
            ])
            .output()
            .map_err(|e| anyhow::anyhow!("执行 reg add 失败：{}", e))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            anyhow::bail!("写入注册表失败：{}", stderr);
        }
        log::info!("已启用开机自启（注册表）：{}", exe_path);
    } else {
        // 删除注册表值：reg delete "HKCU\...\Run" /v LinkALL /f
        let output = std::process::Command::new("reg")
            .args(["delete", REG_RUN_KEY, "/v", APP_NAME, "/f"])
            .output()
            .map_err(|e| anyhow::anyhow!("执行 reg delete 失败：{}", e))?;

        // reg delete 在值不存在时返回非零退出码，但视为成功
        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            if !stderr.contains("找不到") && !stderr.contains("Unable to find") {
                log::debug!("删除注册表值（可能不存在）：{}", stderr);
            }
        }
        log::info!("已禁用开机自启");
    }
    Ok(())
}

#[cfg(target_os = "windows")]
fn is_autostart_enabled_windows() -> bool {
    // 查询注册表：reg query "HKCU\...\Run" /v LinkALL
    let output = std::process::Command::new("reg")
        .args(["query", REG_RUN_KEY, "/v", APP_NAME])
        .output();

    match output {
        Ok(output) => output.status.success(),
        Err(_) => false,
    }
}

// ==================== Linux 实现（.desktop 文件） ====================

#[cfg(target_os = "linux")]
fn set_autostart_linux(enabled: bool) -> Result<()> {
    let autostart_dir = dirs::config_dir()
        .map(|d| d.join("autostart"))
        .ok_or_else(|| anyhow::anyhow!("无法确定 autostart 目录"))?;

    if !autostart_dir.exists() {
        std::fs::create_dir_all(&autostart_dir)?;
    }

    let desktop_path = autostart_dir.join("linkall.desktop");

    if enabled {
        let exe_path = std::env::current_exe()
            .map_err(|e| anyhow::anyhow!("获取可执行文件路径失败：{}", e))?;
        let exe_path = exe_path.to_string_lossy();

        let content = format!(
            "[Desktop Entry]\n\
             Type=Application\n\
             Name=LinkALL\n\
             Comment=LinkALL 桌面被控端\n\
             Exec={}\n\
             Icon=linkall\n\
             Terminal=false\n\
             X-GNOME-Autostart-enabled=true\n\
             Categories=Network;\n",
            exe_path
        );
        std::fs::write(&desktop_path, content)?;
        log::info!("已启用开机自启（.desktop：{:?}）", desktop_path);
    } else {
        if desktop_path.exists() {
            std::fs::remove_file(&desktop_path)?;
        }
        log::info!("已禁用开机自启");
    }
    Ok(())
}

#[cfg(target_os = "linux")]
fn is_autostart_enabled_linux() -> bool {
    dirs::config_dir()
        .map(|d| d.join("autostart").join("linkall.desktop").exists())
        .unwrap_or(false)
}
