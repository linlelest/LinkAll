// 配置与本地存储模块
// 负责应用数据目录、SQLite 数据库初始化、键值设置读写。
// 遵循轻量化：rusqlite bundled，无外部数据库进程。
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use anyhow::{Context, Result};
use rusqlite::Connection;
use serde::{Deserialize, Serialize};

/// 应用运行时配置（来自本地 SQLite settings 表，可在设置窗口热修改）
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    /// 信令/HTTP 服务器地址（如 http://localhost:8080）
    pub server_url: String,
    /// 允许匿名连接（无需设备码）
    pub allow_anonymous: bool,
    /// 允许设备码连接
    pub allow_device_code: bool,
    /// 远程控制总开关
    pub allow_remote_control: bool,
    /// 开机自启
    pub autostart: bool,
    /// 性能监控开关
    pub performance_monitor: bool,
    /// 日志级别
    pub log_level: String,
    /// 视频编码（H264 / VP8 / VP9 / AV1）
    pub codec_video: String,
    /// 目标帧率
    pub target_fps: u32,
    /// 码率上限（bps）
    pub max_bitrate: u64,
    /// 屏幕缩放
    pub scale: f64,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            server_url: "http://localhost:8080".into(),
            allow_anonymous: false,
            allow_device_code: true,
            allow_remote_control: true,
            autostart: false,
            performance_monitor: false,
            log_level: "info".into(),
            codec_video: "H264".into(),
            target_fps: 30,
            max_bitrate: 8_000_000,
            scale: 1.0,
        }
    }
}

/// 数据库句柄（受互斥锁保护，可跨线程共享）
pub type Db = Mutex<Connection>;

/// 返回应用数据目录：<系统数据目录>/LinkALL
/// Windows: %APPDATA%/LinkALL ; Linux: ~/.local/share/LinkALL
pub fn app_data_dir() -> Result<PathBuf> {
    let base = dirs::data_dir()
        .context("无法确定系统数据目录")?;
    Ok(base.join("LinkALL"))
}

/// 返回本地数据库路径
pub fn db_path() -> Result<PathBuf> {
    Ok(app_data_dir()?.join("linkall.db"))
}

/// 打开 SQLite 数据库连接并执行迁移脚本。
/// migration_sql 为内嵌的 001_init.sql 内容。
pub fn open_db() -> Result<Connection> {
    let dir = app_data_dir()?;
    fs::create_dir_all(&dir)
        .with_context(|| format!("创建数据目录失败: {:?}", dir))?;
    let path = db_path()?;
    // 设置文件权限 0600（仅 Unix 有效，Windows 忽略）
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        if path.exists() {
            let perm = fs::Permissions::from_mode(0o600);
            let _ = fs::set_permissions(&path, perm);
        }
    }
    let conn = Connection::open(&path)
        .with_context(|| format!("打开数据库失败: {:?}", path))?;
    conn.busy_timeout(std::time::Duration::from_millis(2000))?;
    // 执行迁移
    run_migrations(&conn)?;
    log::info!("本地数据库已就绪: {:?}", path);
    Ok(conn)
}

/// 执行迁移脚本（幂等）
fn run_migrations(conn: &Connection) -> Result<()> {
    conn.execute_batch(include_str!("../migrations/001_init.sql"))
        .context("执行迁移脚本 001_init.sql 失败")?;
    Ok(())
}

/// 从 settings 表加载配置
pub fn load_config(conn: &Connection) -> Result<AppConfig> {
    let mut cfg = AppConfig::default();
    let mut stmt = conn.prepare("SELECT key, value FROM settings")?;
    let rows = stmt.query_map([], |row| {
        let k: String = row.get(0)?;
        let v: String = row.get(1)?;
        Ok((k, v))
    })?;
    for row in rows {
        let (k, v) = row?;
        apply_setting(&mut cfg, &k, &v);
    }
    Ok(cfg)
}

/// 将单个键值应用到配置
fn apply_setting(cfg: &mut AppConfig, key: &str, value: &str) {
    match key {
        "server_url" => cfg.server_url = value.to_string(),
        "allow_anonymous" => cfg.allow_anonymous = parse_bool(value),
        "allow_device_code" => cfg.allow_device_code = parse_bool(value),
        "allow_remote_control" => cfg.allow_remote_control = parse_bool(value),
        "autostart" => cfg.autostart = parse_bool(value),
        "performance_monitor" => cfg.performance_monitor = parse_bool(value),
        "log_level" => cfg.log_level = value.to_string(),
        "codec_video" => cfg.codec_video = value.to_string(),
        "target_fps" => cfg.target_fps = value.parse().unwrap_or(30),
        "max_bitrate" => cfg.max_bitrate = value.parse().unwrap_or(8_000_000),
        "scale" => cfg.scale = value.parse().unwrap_or(1.0),
        _ => {}
    }
}

/// 保存单个设置项到数据库
pub fn save_setting(conn: &Connection, key: &str, value: &str) -> Result<()> {
    let now = chrono::Utc::now().timestamp();
    conn.execute(
        "INSERT INTO settings (key, value, updated_at) VALUES (?1, ?2, ?3)
         ON CONFLICT(key) DO UPDATE SET value = ?2, updated_at = ?3",
        rusqlite::params![key, value, now],
    )?;
    Ok(())
}

/// 批量保存多个设置项
pub fn save_settings(conn: &Connection, items: &[(&str, &str)]) -> Result<()> {
    let now = chrono::Utc::now().timestamp();
    let tx = conn.unchecked_transaction()?;
    {
        let mut stmt = tx.prepare(
            "INSERT INTO settings (key, value, updated_at) VALUES (?1, ?2, ?3)
             ON CONFLICT(key) DO UPDATE SET value = ?2, updated_at = ?3",
        )?;
        for (k, v) in items {
            stmt.execute(rusqlite::params![k, v, now])?;
        }
    }
    tx.commit()?;
    Ok(())
}

/// 内嵌迁移脚本路径检查（供资源加载校验）
pub fn migration_resource_path() -> &'static Path {
    Path::new("migrations/001_init.sql")
}

fn parse_bool(s: &str) -> bool {
    matches!(s.trim().to_ascii_lowercase().as_str(), "1" | "true" | "yes" | "on")
}
