// 认证模块
// 负责与服务端交互登录/登出，本地 JWT 令牌的存储与读取。
use anyhow::{anyhow, Context, Result};
use reqwest::Client;
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};

/// 登录请求体（对应服务端 /api/auth/login）
#[derive(Debug, Serialize)]
struct LoginRequest<'a> {
    username: &'a str,
    password: &'a str,
}

/// 服务端统一响应外层
// 注意：服务端的 code 字段是字符串类型（如 "ERR_OK" / "ERR_AUTH_FAILED"），
// 而非整数。早期版本误将 code 声明为 i32，导致登录成功响应（HTTP 200 +
// body code="ERR_OK"）反序列化失败，进而被前端当作错误弹出。这里改为
// String 并在下文按 "ERR_OK" 判断成功。
#[derive(Debug, Deserialize)]
struct ApiResponse<T> {
    code: String,
    message: Option<String>,
    data: Option<T>,
}

/// 登录响应数据
#[derive(Debug, Deserialize)]
pub struct LoginData {
    pub token: String,
    #[serde(rename = "expiresIn")]
    pub expires_in: i64,
    pub user: UserInfo,
}

/// 用户信息
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserInfo {
    pub id: i64,
    pub username: String,
    pub role: String,
    pub status: String,
    pub banned: bool,
    #[serde(rename = "deviceCount")]
    pub device_count: i64,
    #[serde(rename = "createdAt")]
    pub created_at: i64,
    #[serde(rename = "lastLoginIp", default)]
    pub last_login_ip: String,
}

/// 本地存储的凭据
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Credentials {
    pub token: String,
    pub user_id: i64,
    pub username: String,
    pub role: String,
    pub expires_at: i64, // Unix 秒
}

/// 登录：调用服务端 /api/auth/login 并返回完整登录数据（含 JWT 令牌）
pub async fn login(
    server_url: &str,
    username: &str,
    password: &str,
) -> Result<LoginData> {
    let client = Client::builder()
        .timeout(std::time::Duration::from_secs(10))
        .build()?;
    let url = format!("{}/api/auth/login", server_url.trim_end_matches('/'));
    log::info!("发起登录请求：{}", url);
    let resp = client
        .post(&url)
        .json(&LoginRequest { username, password })
        .send()
        .await
        .context("登录请求失败，请检查服务器地址与网络")?;
    let status = resp.status();
    let body = resp.text().await.unwrap_or_default();
    let api: ApiResponse<LoginData> = serde_json::from_str(&body)
        .with_context(|| format!("解析登录响应失败 (HTTP {}): {}", status, body))?;
    // 服务端约定 code == "ERR_OK" 表示业务成功；其余均视为失败（即使 HTTP 200）
    if api.code != "ERR_OK" {
        return Err(anyhow!("{}", api.message.unwrap_or_else(|| "登录失败".into())));
    }
    let data = api.data.ok_or_else(|| anyhow!("登录响应缺少数据"))?;
    log::info!("登录成功：用户={} 角色={}", data.user.username, data.user.role);
    Ok(data)
}

/// 登录成功后保存凭据到本地
pub fn save_credentials(conn: &Connection, login: &LoginData) -> Result<()> {
    let now = chrono::Utc::now().timestamp();
    let expires_at = now + login.expires_in;
    conn.execute(
        "INSERT INTO credentials (id, token, user_id, username, role, expires_at, updated_at)
         VALUES (1, ?1, ?2, ?3, ?4, ?5, ?6)
         ON CONFLICT(id) DO UPDATE SET
            token = ?1, user_id = ?2, username = ?3, role = ?4, expires_at = ?5, updated_at = ?6",
        params![
            login.token,
            login.user.id,
            login.user.username,
            login.user.role,
            expires_at,
            now
        ],
    )?;
    log::info!("凭据已保存，用户={} 角色={}", login.user.username, login.user.role);
    Ok(())
}

/// 读取本地凭据
pub fn load_credentials(conn: &Connection) -> Result<Option<Credentials>> {
    let mut stmt = conn.prepare(
        "SELECT token, user_id, username, role, expires_at FROM credentials WHERE id = 1",
    )?;
    let mut rows = stmt.query_map([], |row| {
        Ok(Credentials {
            token: row.get(0)?,
            user_id: row.get(1)?,
            username: row.get(2)?,
            role: row.get(3)?,
            expires_at: row.get(4)?,
        })
    })?;
    if let Some(row) = rows.next() {
        Ok(Some(row?))
    } else {
        Ok(None)
    }
}

/// 当前 JWT 是否有效（未过期）
pub fn is_token_valid(creds: &Credentials) -> bool {
    let now = chrono::Utc::now().timestamp();
    creds.expires_at > now
}

/// 登出：清除本地凭据
pub fn logout(conn: &Connection) -> Result<()> {
    conn.execute("DELETE FROM credentials WHERE id = 1", [])?;
    log::info!("已登出，凭据已清除");
    Ok(())
}

/// 获取当前 JWT（若有效）
pub fn current_token(conn: &Connection) -> Result<Option<String>> {
    if let Some(c) = load_credentials(conn)? {
        if is_token_valid(&c) {
            return Ok(Some(c.token));
        }
        log::warn!("JWT 已过期，需重新登录");
    }
    Ok(None)
}
