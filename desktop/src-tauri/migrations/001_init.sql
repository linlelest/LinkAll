-- LinkALL 桌面被控端本地 SQLite 初始化脚本
-- 存储：JWT 凭据、设备身份（编号/设备码）、运行时设置
-- 文件位置：由 config.rs 决定，默认 <app_data>/linkall.db

PRAGMA journal_mode = WAL;     -- 写前日志，降低锁竞争
PRAGMA foreign_keys = ON;       -- 开启外键约束

-- 凭据表：仅一行（当前登录账号的 JWT）
CREATE TABLE IF NOT EXISTS credentials (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    token       TEXT NOT NULL,           -- JWT
    user_id     INTEGER NOT NULL,
    username    TEXT NOT NULL,
    role        TEXT NOT NULL DEFAULT 'user',
    expires_at  INTEGER NOT NULL,        -- Unix 秒
    updated_at  INTEGER NOT NULL
);

-- 设备身份表：仅一行（本机设备身份）
CREATE TABLE IF NOT EXISTS device_identity (
    id              INTEGER PRIMARY KEY CHECK (id = 1),
    device_id       TEXT NOT NULL,        -- 12 位设备编号
    device_code     TEXT NOT NULL,        -- 设备码（明文，本地存储，0600 权限保护）
    owner_user_id   INTEGER,              -- 所属用户 ID（登录后绑定）
    created_at      INTEGER NOT NULL
);

-- 运行时设置表：键值存储
CREATE TABLE IF NOT EXISTS settings (
    key     TEXT PRIMARY KEY,
    value   TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 初始默认设置
INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES
    ('server_url',          'http://localhost:8080', strftime('%s','now')),
    ('allow_anonymous',      '0',                     strftime('%s','now')),
    ('allow_device_code',   '1',                     strftime('%s','now')),
    ('allow_remote_control','1',                     strftime('%s','now')),
    ('autostart',           '0',                     strftime('%s','now')),
    ('performance_monitor', '0',                     strftime('%s','now')),
    ('log_level',           'info',                  strftime('%s','now')),
    -- 默认编码参数
    ('codec_video',         'H264',                  strftime('%s','now')),
    ('target_fps',          '30',                    strftime('%s','now')),
    ('max_bitrate',         '8000000',               strftime('%s','now')),
    ('scale',               '1.0',                   strftime('%s','now'));
