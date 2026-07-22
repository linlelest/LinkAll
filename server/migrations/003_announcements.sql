-- 003_announcements.sql
-- 公告表：Markdown 富文本、置顶、平台/版本过滤、数字签名、阅读状态追踪

CREATE TABLE IF NOT EXISTS announcements (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    title           TEXT    NOT NULL,
    content_md      TEXT    NOT NULL DEFAULT '',                 -- Markdown 正文
    pinned          INTEGER NOT NULL DEFAULT 0,                  -- 0=普通 1=置顶（支持多条置顶）
    platform        TEXT    NOT NULL DEFAULT 'all',             -- all / windows / linux / android / web
    version_filter  TEXT    NOT NULL DEFAULT '',                 -- 版本过滤表达式（如 ">=1.0.0" 或 "1.x"），空表示不过滤
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    updated_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    signature       TEXT    NOT NULL DEFAULT '',                 -- Ed25519 数字签名（Hex 编码）
    author_id       INTEGER,                                     -- 创建者 user_id
    status          TEXT    NOT NULL DEFAULT 'published',        -- draft / published / archived
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_announcements_pinned   ON announcements(pinned);
CREATE INDEX IF NOT EXISTS idx_announcements_platform ON announcements(platform);
CREATE INDEX IF NOT EXISTS idx_announcements_status   ON announcements(status);

-- 公告阅读状态表：追踪每个用户对每条公告的阅读情况
CREATE TABLE IF NOT EXISTS announcement_reads (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    announcement_id INTEGER NOT NULL,
    user_id         INTEGER NOT NULL,
    read_at         INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE (announcement_id, user_id),
    FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reads_user ON announcement_reads(user_id);
CREATE INDEX IF NOT EXISTS idx_reads_ann  ON announcement_reads(announcement_id);
