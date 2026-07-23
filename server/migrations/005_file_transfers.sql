-- 005_file_transfers.sql
-- 文件传输记录表：断点续传进度持久化、传输队列管理、审计统计
-- 记录每次文件传输任务的状态，支持中断后从断点恢复

CREATE TABLE IF NOT EXISTS file_transfers (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    transfer_id     TEXT    NOT NULL UNIQUE,                     -- 传输任务 ID（UUIDv4）
    session_id      TEXT    NOT NULL DEFAULT '',                 -- 关联会话 ID
    device_id       TEXT    NOT NULL DEFAULT '',                 -- 被控设备编号
    controller_user_id INTEGER,                                 -- 控制端用户 ID（匿名时为 NULL）
    name            TEXT    NOT NULL DEFAULT '',                 -- 文件名
    size            INTEGER NOT NULL DEFAULT 0,                  -- 文件总字节数
    hash            TEXT    NOT NULL DEFAULT '',                 -- SHA-256 哈希（Hex）
    direction       TEXT    NOT NULL DEFAULT 'upload',           -- upload / download
    remote_path     TEXT    NOT NULL DEFAULT '',                 -- 目标路径
    chunk_size      INTEGER NOT NULL DEFAULT 262144,             -- 分片大小
    transferred     INTEGER NOT NULL DEFAULT 0,                  -- 已传输字节数
    received_chunks TEXT    NOT NULL DEFAULT '{}',               -- 已接收分片 JSON {chunkId: offset}
    status          TEXT    NOT NULL DEFAULT 'pending',          -- pending/transferring/paused/done/failed/cancelled
    error_msg       TEXT    NOT NULL DEFAULT '',                 -- 失败原因
    started_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    completed_at    INTEGER,
    updated_at      INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);

CREATE INDEX IF NOT EXISTS idx_ft_transfer   ON file_transfers(transfer_id);
CREATE INDEX IF NOT EXISTS idx_ft_session    ON file_transfers(session_id);
CREATE INDEX IF NOT EXISTS idx_ft_device     ON file_transfers(device_id);
CREATE INDEX IF NOT EXISTS idx_ft_status     ON file_transfers(status);

-- 崩溃报告表：客户端崩溃上报记录
CREATE TABLE IF NOT EXISTS crash_reports (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id       TEXT    NOT NULL DEFAULT '',                 -- 客户端标识（设备编号或用户名）
    platform        TEXT    NOT NULL DEFAULT '',                 -- windows / linux / android / web
    app_version     TEXT    NOT NULL DEFAULT '',                 -- 客户端版本
    crash_type      TEXT    NOT NULL DEFAULT '',                 -- panic / native / anr / oom
    stack_trace     TEXT    NOT NULL DEFAULT '',                 -- 堆栈跟踪（文本）
    log_excerpt     TEXT    NOT NULL DEFAULT '',                 -- 日志摘录
    device_info     TEXT    NOT NULL DEFAULT '',                 -- 设备信息 JSON
    ip              TEXT    NOT NULL DEFAULT '',                 -- 上报端 IP
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);

CREATE INDEX IF NOT EXISTS idx_crash_client   ON crash_reports(client_id);
CREATE INDEX IF NOT EXISTS idx_crash_platform ON crash_reports(platform);
CREATE INDEX IF NOT EXISTS idx_crash_created  ON crash_reports(created_at);

-- 设备配对表：同账号设备首次配对确认记录
CREATE TABLE IF NOT EXISTS device_pairings (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id       TEXT    NOT NULL,                            -- 被控设备编号
    controller_user_id INTEGER NOT NULL,                         -- 控制端用户 ID
    status          TEXT    NOT NULL DEFAULT 'pending',          -- pending / confirmed / denied
    pair_token      TEXT    NOT NULL DEFAULT '',                 -- 配对令牌（确认后免密直连）
    confirmed_at    INTEGER,
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE (device_id, controller_user_id)
);

CREATE INDEX IF NOT EXISTS idx_pair_device ON device_pairings(device_id);
CREATE INDEX IF NOT EXISTS idx_pair_user   ON device_pairings(controller_user_id);
