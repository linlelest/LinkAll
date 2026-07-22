// Package db 封装 modernc.org/sqlite 纯 Go 驱动的连接池与生命周期管理。
// 不使用 CGO，编译产物可直接跨平台。
package db

import (
	"context"
	"database/sql"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"sync"
	"time"

	_ "modernc.org/sqlite" // 注册 "sqlite" 驱动（纯 Go，无 CGO）
)

var (
	globalDB *sql.DB
	once    sync.Once
)

// Open 打开 SQLite 数据库连接并配置连接池。
// dsn 形如 "file:./data/linkall.db?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)"
func Open(dbPath string) (*sql.DB, error) {
	// 确保父目录存在
	if dir := filepath.Dir(dbPath); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return nil, fmt.Errorf("创建数据库目录失败: %w", err)
		}
	}

	dsn := buildDSN(dbPath)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("打开 SQLite 失败: %w", err)
	}

	// modernc.org/sqlite 使用连接池：设置单写多读，避免 SQLITE_BUSY
	// 单写：SetMaxOpenConns(1) 可彻底避免锁冲突，但牺牲并发；
	// 此处取折中：开启 WAL + busy_timeout，连接数适度
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(0) // SQLite 长连接
	db.SetConnMaxIdleTime(30 * time.Minute)

	// 启用 PRAGMA
	pragmas := []string{
		"PRAGMA journal_mode=WAL;",       // WAL 模式，读写并发
		"PRAGMA synchronous=NORMAL;",      // 性能折中
		"PRAGMA foreign_keys=ON;",         // 启用外键约束
		"PRAGMA busy_timeout=5000;",      // 锁等待 5 秒
		"PRAGMA cache_size=-20000;",      // 20MB 缓存
		"PRAGMA temp_store=MEMORY;",      // 临时表走内存
	}
	for _, p := range pragmas {
		if _, err := db.Exec(p); err != nil {
			db.Close()
			return nil, fmt.Errorf("执行 PRAGMA 失败 [%s]: %w", p, err)
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, fmt.Errorf("数据库 ping 失败: %w", err)
	}

	return db, nil
}

// Global 返回全局单例 DB（首次调用时 Open）。
func Global(dbPath string) (*sql.DB, error) {
	var err error
	once.Do(func() {
		globalDB, err = Open(dbPath)
	})
	return globalDB, err
}

// Close 关闭全局 DB 连接。
func Close() error {
	if globalDB != nil {
		return globalDB.Close()
	}
	return nil
}

// buildDSN 构造 modernc.org/sqlite 接受的 DSN。
// 关键参数：busy_timeout / journal_mode=WAL / foreign_keys=ON。
func buildDSN(dbPath string) string {
	// 路径转 file: URL
	q := url.Values{}
	q.Add("_pragma", "busy_timeout(5000)")
	q.Add("_pragma", "journal_mode(WAL)")
	q.Add("_pragma", "foreign_keys(1)")
	// 路径若含特殊字符需转义，此处保持简单（路径一般不含特殊字符）
	return "file:" + dbPath + "?" + q.Encode()
}
