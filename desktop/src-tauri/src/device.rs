// 设备身份模块
// 负责生成 12 位设备编号、设备码（密码），以及本地持久化。
use anyhow::Result;
use rand::Rng;
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};

/// 不易混淆的字符集（剔除 0/O、1/I/l），12 位设备编号从此生成
const DEVICE_ID_CHARSET: &[u8] = b"ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
/// 设备码字符集（含大小写字母与数字）
const DEVICE_CODE_CHARSET: &[u8] = b"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

/// 设备身份信息
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo {
    /// 12 位设备编号
    pub device_id: String,
    /// 设备码（明文，本地存储）
    pub device_code: String,
    /// 所属用户 ID（登录后绑定）
    pub owner_user_id: Option<i64>,
}

/// 加载或创建本机设备身份（数据库仅一行，id=1）
pub fn ensure_device(conn: &Connection) -> Result<DeviceInfo> {
    // 尝试读取已有身份
    if let Some(info) = load_device(conn)? {
        return Ok(info);
    }
    // 首次启动：生成新身份
    let info = DeviceInfo {
        device_id: generate_device_id(),
        device_code: generate_device_code(),
        owner_user_id: None,
    };
    let now = chrono::Utc::now().timestamp();
    conn.execute(
        "INSERT INTO device_identity (id, device_id, device_code, owner_user_id, created_at)
         VALUES (1, ?1, ?2, NULL, ?3)",
        params![info.device_id, info.device_code, now],
    )?;
    log::info!("已生成新设备身份：{}", info.device_id);
    Ok(info)
}

/// 从数据库读取设备身份
pub fn load_device(conn: &Connection) -> Result<Option<DeviceInfo>> {
    let mut stmt = conn.prepare(
        "SELECT device_id, device_code, owner_user_id FROM device_identity WHERE id = 1",
    )?;
    let mut rows = stmt.query_map([], |row| {
        Ok(DeviceInfo {
            device_id: row.get(0)?,
            device_code: row.get(1)?,
            owner_user_id: row.get(2)?,
        })
    })?;
    if let Some(row) = rows.next() {
        Ok(Some(row?))
    } else {
        Ok(None)
    }
}

/// 生成 12 位设备编号（大写字母+数字，剔除易混淆字符）
pub fn generate_device_id() -> String {
    let mut rng = rand::thread_rng();
    (0..12)
        .map(|_| {
            let idx = rng.gen_range(0..DEVICE_ID_CHARSET.len());
            DEVICE_ID_CHARSET[idx] as char
        })
        .collect()
}

/// 生成 10 位设备码（密码）
pub fn generate_device_code() -> String {
    let mut rng = rand::thread_rng();
    (0..10)
        .map(|_| {
            let idx = rng.gen_range(0..DEVICE_CODE_CHARSET.len());
            DEVICE_CODE_CHARSET[idx] as char
        })
        .collect()
}

/// 重置设备编号（生成新 12 位编号并持久化）
pub fn reset_device_id(conn: &Connection) -> Result<String> {
    let new_id = generate_device_id();
    conn.execute(
        "UPDATE device_identity SET device_id = ?1 WHERE id = 1",
        params![new_id],
    )?;
    log::info!("设备编号已重置为：{}", new_id);
    Ok(new_id)
}

/// 重置设备码（生成新密码并持久化）
pub fn reset_device_code(conn: &Connection) -> Result<String> {
    let new_code = generate_device_code();
    conn.execute(
        "UPDATE device_identity SET device_code = ?1 WHERE id = 1",
        params![new_code],
    )?;
    log::info!("设备码已重置");
    Ok(new_code)
}

/// 绑定设备归属用户（登录成功后调用）
pub fn bind_owner(conn: &Connection, user_id: i64) -> Result<()> {
    conn.execute(
        "UPDATE device_identity SET owner_user_id = ?1 WHERE id = 1",
        params![user_id],
    )?;
    Ok(())
}
