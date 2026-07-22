-- 004_ota_releases.sql
-- OTA 发布表：版本管理、文件哈希、强制更新标志、下载量统计

CREATE TABLE IF NOT EXISTS ota_releases (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    platform        TEXT    NOT NULL,                            -- windows / linux / android / web
    version         TEXT    NOT NULL,                            -- 语义化版本号（如 1.2.3）
    file_path       TEXT    NOT NULL,                            -- 相对 /ota 目录的文件路径
    file_hash       TEXT    NOT NULL,                            -- SHA-256 文件哈希（Hex）
    file_size       INTEGER NOT NULL DEFAULT 0,                  -- 文件字节数
    release_notes   TEXT    NOT NULL DEFAULT '',                 -- 更新日志（Markdown）
    force_update    INTEGER NOT NULL DEFAULT 0,                 -- 0=可选 1=强制更新
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    download_count  INTEGER NOT NULL DEFAULT 0,                 -- 下载量统计
    signature       TEXT    NOT NULL DEFAULT '',                -- Ed25519 签名（Hex）
    status          TEXT    NOT NULL DEFAULT 'active',           -- active / withdrawn / deprecated
    UNIQUE (platform, version)
);

CREATE INDEX IF NOT EXISTS idx_ota_platform   ON ota_releases(platform);
CREATE INDEX IF NOT EXISTS idx_ota_version    ON ota_releases(version);
CREATE INDEX IF NOT EXISTS idx_ota_status     ON ota_releases(status);

-- OTA 下载日志表：记录每次下载（用于审计与统计）
CREATE TABLE IF NOT EXISTS ota_download_logs (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    release_id      INTEGER NOT NULL,
    device_id       TEXT,                                        -- 下载设备编号
    user_id         INTEGER,                                    -- 下载用户（匿名时 NULL）
    ip              TEXT    NOT NULL DEFAULT '',
    user_agent      TEXT    NOT NULL DEFAULT '',
    downloaded_at   INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    FOREIGN KEY (release_id) REFERENCES ota_releases(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)     REFERENCES users(id)        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_ota_logs_release ON ota_download_logs(release_id);
