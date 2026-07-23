-- 邀请码系统升级：支持使用次数上限 + 全局开关 + 自动删除
-- 006_invite_upgrade.sql

-- 邀请码表：增加最大使用次数和已使用次数（替代原 used 布尔字段，兼容旧逻辑）
ALTER TABLE invite_codes ADD COLUMN max_uses   INTEGER NOT NULL DEFAULT 1;
ALTER TABLE invite_codes ADD COLUMN used_count  INTEGER NOT NULL DEFAULT 0;

-- 已使用的旧邀请码迁移：used=1 的标记 used_count=max_uses（视为已用尽）
UPDATE invite_codes SET used_count = max_uses WHERE used = 1;

-- 安全设置表：增加邀请码系统总开关（1=启用注册需邀请码，0=禁用注册免邀请码）
ALTER TABLE security_settings ADD COLUMN invite_enabled INTEGER NOT NULL DEFAULT 1;

-- 清理已过期且未用完的邀请码（启动时一次性清理）
DELETE FROM invite_codes WHERE expires_at < strftime('%s','now') AND used_count < max_uses;
