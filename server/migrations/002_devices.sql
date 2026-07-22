-- 002_devices.sql
-- 设备表：被控端注册设备信息、在线状态、最后心跳

CREATE TABLE IF NOT EXISTS devices (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id       TEXT    NOT NULL UNIQUE,                     -- 12 位设备编号（明文展示，索引唯一）
    device_code_hash TEXT   NOT NULL,                            -- 设备码 Argon2id 哈希（密码）
    owner_user_id   INTEGER,                                      -- 所属用户 user_id（匿名注册时为 NULL）
    online_status   TEXT    NOT NULL DEFAULT 'offline',          -- offline / online / busy / sleeping
    last_seen       INTEGER NOT NULL DEFAULT 0,                   -- 最后心跳时间戳（秒）
    platform        TEXT    NOT NULL DEFAULT '',                  -- windows / linux / android / web
    version         TEXT    NOT NULL DEFAULT '',                 -- 客户端版本号
    device_name     TEXT    NOT NULL DEFAULT '',                 -- 设备显示名
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_devices_device_id   ON devices(device_id);
CREATE INDEX IF NOT EXISTS idx_devices_owner       ON devices(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_devices_online      ON devices(online_status);

-- 设备会话表：记录每次控制会话（用于审计与并发限制）
CREATE TABLE IF NOT EXISTS device_sessions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT    NOT NULL UNIQUE,                     -- UUIDv4 会话 ID
    device_id       TEXT    NOT NULL,                            -- 被控设备编号
    controller_user_id INTEGER,                                 -- 控制端 user_id（匿名时为 NULL）
    controller_ip   TEXT    NOT NULL DEFAULT '',                 -- 控制端 IP
    mode            TEXT    NOT NULL DEFAULT 'anonymous',         -- anonymous / same_account / device_code
    status          TEXT    NOT NULL DEFAULT 'active',            -- active / sleeping / closed / timeout
    started_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    last_active_at  INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    closed_at       INTEGER,
    bytes_sent      INTEGER NOT NULL DEFAULT 0,                  -- 上行字节
    bytes_received  INTEGER NOT NULL DEFAULT 0,                  -- 下行字节
    FOREIGN KEY (device_id)          REFERENCES devices(device_id) ON DELETE CASCADE,
    FOREIGN KEY (controller_user_id) REFERENCES users(id)        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_device   ON device_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_sessions_user     ON device_sessions(controller_user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_status  ON device_sessions(status);
