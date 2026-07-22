-- 001_users.sql
-- 用户表：存储账号、角色、邀请关系、登录信息、流量统计
-- 注意：所有时间字段采用 INTEGER 存储 Unix 时间戳（秒），避免时区问题

CREATE TABLE IF NOT EXISTS users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT    NOT NULL UNIQUE,
    password_hash   TEXT    NOT NULL,
    role            TEXT    NOT NULL DEFAULT 'user',            -- 角色: superadmin / admin / user
    invite_code_id  INTEGER,                                     -- 关联邀请码 id（注册时使用）
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    last_login_ip   TEXT    DEFAULT '',
    device_count    INTEGER NOT NULL DEFAULT 0,
    traffic         INTEGER NOT NULL DEFAULT 0,                  -- 累计流量（字节）
    banned          INTEGER NOT NULL DEFAULT 0,                   -- 0=正常 1=封禁
    status          TEXT    NOT NULL DEFAULT 'active',           -- active / disabled / pending
    FOREIGN KEY (invite_code_id) REFERENCES invite_codes(id) ON DELETE SET NULL
);

-- 索引：按用户名/角色/状态查询
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_role     ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status   ON users(status);

-- 邀请码表：单次使用 + 时效 + 创建者追踪
CREATE TABLE IF NOT EXISTS invite_codes (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    code          TEXT    NOT NULL UNIQUE,                       -- 邀请码明文（8-12 位）
    code_hash     TEXT    NOT NULL,                              -- 邀请码哈希（用于安全比对，可选）
    created_by    INTEGER NOT NULL,                             -- 创建者 user_id
    used_by       INTEGER,                                      -- 使用者 user_id（使用后填充）
    expires_at    INTEGER NOT NULL,                              -- 过期时间戳（秒）
    used_at       INTEGER,                                      -- 使用时间戳
    revoked       INTEGER NOT NULL DEFAULT 0,                    -- 0=有效 1=已吊销
    used          INTEGER NOT NULL DEFAULT 0,                    -- 0=未使用 1=已使用
    created_at    INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    note          TEXT    DEFAULT '',                            -- 备注（导出时可选填充）
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (used_by)    REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_invite_codes_code     ON invite_codes(code);
CREATE INDEX IF NOT EXISTS idx_invite_codes_used     ON invite_codes(used);
CREATE INDEX IF NOT EXISTS idx_invite_codes_revoked  ON invite_codes(revoked);

-- 安全设置表（全局，单行记录 id=1）
CREATE TABLE IF NOT EXISTS security_settings (
    id                      INTEGER PRIMARY KEY CHECK (id = 1),
    force_https             INTEGER NOT NULL DEFAULT 0,          -- 强制 HTTPS
    anonymous_whitelist     TEXT    NOT NULL DEFAULT '[]',        -- 匿名连接白名单 JSON 数组
    connection_password     TEXT    NOT NULL DEFAULT '',          -- 全局连接密码策略（哈希后存储）
    connection_password_hash TEXT   NOT NULL DEFAULT '',         -- 全局连接密码 Argon2id 哈希
    max_concurrent_sessions INTEGER NOT NULL DEFAULT 100,        -- 最大并发会话数
    data_retention_days     INTEGER NOT NULL DEFAULT 90,         -- 数据保留天数
    allow_anonymous         INTEGER NOT NULL DEFAULT 0,           -- 总开关：是否允许匿名连接
    allow_device_code       INTEGER NOT NULL DEFAULT 1,           -- 总开关：是否允许设备码连接
    allow_remote_control    INTEGER NOT NULL DEFAULT 1,          -- 总开关：是否允许远程控制
    updated_at              INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);

-- 初始化单行默认配置
INSERT OR IGNORE INTO security_settings (id) VALUES (1);
