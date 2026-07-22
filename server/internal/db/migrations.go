package db

import (
	"context"
	"database/sql"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// migrationsTableName 记录已执行迁移的元表名。
const migrationsTableName = "schema_migrations"

// ensureMigrationsTable 创建记录迁移执行情况的元表。
func ensureMigrationsTable(db *sql.DB) error {
	const ddl = `
CREATE TABLE IF NOT EXISTS schema_migrations (
    version     TEXT PRIMARY KEY,
    applied_at  INTEGER NOT NULL,
    checksum    TEXT NOT NULL DEFAULT ''
);`
	_, err := db.Exec(ddl)
	return err
}

// RunMigrations 执行 migrationsDir 下所有 .sql 文件（按文件名升序）。
// 每个文件作为一个事务，失败则回滚。已执行的不再重复执行。
func RunMigrations(db *sql.DB, migrationsDir string) error {
	if err := ensureMigrationsTable(db); err != nil {
		return fmt.Errorf("创建迁移元表失败: %w", err)
	}

	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("读取迁移目录 %s 失败: %w", migrationsDir, err)
	}

	// 收集所有 .sql 文件，按文件名升序
	var files []fs.DirEntry
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			files = append(files, e)
		}
	}
	sort.Slice(files, func(i, j int) bool {
		return files[i].Name() < files[j].Name()
	})

	applied, err := listApplied(db)
	if err != nil {
		return fmt.Errorf("查询已应用迁移失败: %w", err)
	}

	for _, f := range files {
		version := strings.TrimSuffix(f.Name(), ".sql")
		if _, ok := applied[version]; ok {
			continue
		}
		if err := applyMigration(db, migrationsDir, f.Name(), version); err != nil {
			return fmt.Errorf("执行迁移 %s 失败: %w", f.Name(), err)
		}
	}
	return nil
}

// listApplied 返回已执行的迁移版本集合。
func listApplied(db *sql.DB) (map[string]bool, error) {
	rows, err := db.Query(`SELECT version FROM schema_migrations`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make(map[string]bool)
	for rows.Next() {
		var v string
		if err := rows.Scan(&v); err != nil {
			return nil, err
		}
		out[v] = true
	}
	return out, rows.Err()
}

// applyMigration 读取单个 .sql 文件并按 ";" 切分执行，整体包在一个事务里。
func applyMigration(db *sql.DB, dir, filename, version string) error {
	path := filepath.Join(dir, filename)
	content, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("读取迁移文件失败: %w", err)
	}
	statements := splitSQLStatements(string(content))

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("开启事务失败: %w", err)
	}
	defer tx.Rollback() // 安全回滚，Commit 后无效

	for _, stmt := range statements {
		stmt = strings.TrimSpace(stmt)
		if stmt == "" {
			continue
		}
		if _, err := tx.ExecContext(ctx, stmt); err != nil {
			return fmt.Errorf("执行语句失败 [%s]: %w", truncate(stmt, 80), err)
		}
	}

	if _, err := tx.Exec(
		`INSERT INTO schema_migrations (version, applied_at, checksum) VALUES (?, ?, ?)`,
		version, time.Now().Unix(), "",
	); err != nil {
		return fmt.Errorf("记录迁移版本失败: %w", err)
	}

	return tx.Commit()
}

// splitSQLStatements 将多语句 SQL 文本按 ";" 切分为单条语句。
// 简单实现：忽略字符串字面量与行注释（--）。满足迁移脚本场景。
func splitSQLStatements(content string) []string {
	var (
		out      []string
		cur      strings.Builder
		inStr    bool
		strDelim byte
	)
	flush := func() {
		s := strings.TrimSpace(cur.String())
		if s != "" {
			out = append(out, s)
		}
		cur.Reset()
	}

	for i := 0; i < len(content); i++ {
		c := content[i]
		// 行注释：-- ... \n
		if c == '-' && i+1 < len(content) && content[i+1] == '-' && !inStr {
			// 跳过到行尾
			for i < len(content) && content[i] != '\n' {
				i++
			}
			continue
		}
		// 字符串字面量
		if (c == '\'' || c == '"') && !inStr {
			inStr = true
			strDelim = c
			cur.WriteByte(c)
			continue
		}
		if inStr && c == strDelim {
			inStr = false
			cur.WriteByte(c)
			continue
		}
		if c == ';' && !inStr {
			flush()
			continue
		}
		cur.WriteByte(c)
	}
	flush()
	return out
}

// truncate 截断字符串用于错误日志。
func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}
