// Package config 负责加载 .env 配置并解析命令行参数。
// 遵循轻量化原则：仅用 godotenv + flag，不引入 viper 等重型库。
package config

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

// Config 持有服务端运行时配置。
// 所有字段均可由 .env 覆盖，部分字段（端口/数据库路径）可由命令行 flag 进一步覆盖。
type Config struct {
	// 服务器
	ServerPort  string
	Env         string // dev / prod
	OfficialServer string

	// 数据库
	DBPath string

	// JWT
	JWTScheme string // HS256 / HS384 / HS512
	JWTSecret string
	JWTExpiry time.Duration

	// WebRTC / STUN / TURN
	STUNServers   []string
	TURNServers   []string
	TURNUsername  string
	TURNCredential string

	// 安全
	ForceHTTPS            bool
	MaxConcurrentSessions int
	DataRetentionDays     int

	// 心跳 / 重连 / 会话
	HeartbeatInterval time.Duration
	SessionTimeout    time.Duration

	// 路径
	DataDir       string
	MigrationsDir string
	OTADir        string
	LocalesDir    string
}

// Default 返回一份带默认值的配置，便于首次 init 时生成 .env。
func Default() *Config {
	return &Config{
		ServerPort:           "8080",
		Env:                  "dev",
		OfficialServer:       "",
		DBPath:               "./data/linkall.db",
		JWTScheme:            "HS256",
		JWTSecret:            "",
		JWTExpiry:            24 * time.Hour,
		STUNServers:          []string{"stun:stun.l.google.com:19302"},
		TURNServers:          nil,
		TURNUsername:         "",
		TURNCredential:       "",
		ForceHTTPS:           false,
		MaxConcurrentSessions: 100,
		DataRetentionDays:     90,
		HeartbeatInterval:    15 * time.Second,
		SessionTimeout:       30 * time.Minute,
		DataDir:              "./data",
		MigrationsDir:        "./migrations",
		OTADir:               "./ota",
		LocalesDir:           "./internal/i18n/locales",
	}
}

// Load 从 .env 与命令行 flag 加载配置。
// envPath 为空时按顺序尝试 ./.env / ./server/.env。
func Load(args []string) (*Config, error) {
	cfg := Default()

	fs := flag.NewFlagSet("linkall-server", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)

	var (
		port     string
		dbPath   string
		envPath  string
		envFile  string
		showVer  bool
	)
	fs.StringVar(&port, "port", "", "HTTP 监听端口（覆盖 .env SERVER_PORT）")
	fs.StringVar(&dbPath, "db", "", "SQLite 数据库路径（覆盖 .env DB_PATH）")
	fs.StringVar(&envPath, "env-path", ".env", ".env 文件路径")
	fs.StringVar(&envFile, "env", "", "运行环境 dev/prod（覆盖 .env ENV）")
	fs.BoolVar(&showVer, "version", false, "显示版本信息")

	if err := fs.Parse(args); err != nil {
		return nil, err
	}
	if showVer {
		fmt.Println("linkall-server v0.1.0 (Phase 1)")
		os.Exit(0)
	}

	// 加载 .env（不存在时忽略，允许纯 flag 启动）
	if _, err := os.Stat(envPath); err == nil {
		if err := godotenv.Load(envPath); err != nil {
			return nil, fmt.Errorf("加载 .env 失败: %w", err)
		}
	}

	// 从环境变量读取（.env 已注入 os.Environ）
	cfg.ServerPort = getenvStr("SERVER_PORT", cfg.ServerPort)
	cfg.Env = getenvStr("ENV", cfg.Env)
	cfg.OfficialServer = getenvStr("OFFICIAL_SERVER", cfg.OfficialServer)
	cfg.DBPath = getenvStr("DB_PATH", cfg.DBPath)
	cfg.JWTSecret = getenvStr("JWT_SECRET", cfg.JWTSecret)
	cfg.JWTScheme = getenvStr("JWT_SCHEME", cfg.JWTScheme)
	cfg.TURNUsername = getenvStr("TURN_USERNAME", cfg.TURNUsername)
	cfg.TURNCredential = getenvStr("TURN_CREDENTIAL", cfg.TURNCredential)
	cfg.DataDir = getenvStr("DATA_DIR", cfg.DataDir)
	cfg.MigrationsDir = getenvStr("MIGRATIONS_DIR", cfg.MigrationsDir)
	cfg.OTADir = getenvStr("OTA_DIR", cfg.OTADir)
	cfg.LocalesDir = getenvStr("LOCALES_DIR", cfg.LocalesDir)

	// JWT 过期时间
	if v := os.Getenv("JWT_EXPIRY"); v != "" {
		d, err := time.ParseDuration(v)
		if err == nil {
			cfg.JWTExpiry = d
		}
	}

	// STUN / TURN 列表（逗号分隔）
	cfg.STUNServers = splitCSV(os.Getenv("STUN_SERVERS"), cfg.STUNServers)
	cfg.TURNServers = splitCSV(os.Getenv("TURN_SERVERS"), nil)

	// 布尔/整型
	cfg.ForceHTTPS = getenvBool("FORCE_HTTPS", cfg.ForceHTTPS)
	cfg.MaxConcurrentSessions = getenvInt("MAX_CONCURRENT_SESSIONS", cfg.MaxConcurrentSessions)
	cfg.DataRetentionDays = getenvInt("DATA_RETENTION_DAYS", cfg.DataRetentionDays)

	// 命令行 flag 优先级最高
	if port != "" {
		cfg.ServerPort = port
	}
	if dbPath != "" {
		cfg.DBPath = dbPath
	}
	if envFile != "" {
		cfg.Env = envFile
	}

	// 兜底：JWT_SECRET 为空时生成随机值并写回环境（避免生产环境空密钥）
	if cfg.JWTSecret == "" {
		cfg.JWTSecret = generateRandomSecret(32)
		_ = os.Setenv("JWT_SECRET", cfg.JWTSecret)
	}

	// 规范化路径（相对路径基于工作目录）
	cfg.DBPath = filepath.Clean(cfg.DBPath)
	cfg.DataDir = filepath.Clean(cfg.DataDir)
	cfg.MigrationsDir = filepath.Clean(cfg.MigrationsDir)
	cfg.OTADir = filepath.Clean(cfg.OTADir)
	cfg.LocalesDir = filepath.Clean(cfg.LocalesDir)

	return cfg, nil
}

// EnsureDirs 创建运行时所需目录（data/、ota/）。
func (c *Config) EnsureDirs() error {
	for _, d := range []string{c.DataDir, c.OTADir} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			return fmt.Errorf("创建目录 %s 失败: %w", d, err)
		}
	}
	return nil
}

// --- 辅助函数 ---

func getenvStr(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

func getenvBool(key string, def bool) bool {
	if v, ok := os.LookupEnv(key); ok {
		if b, err := strconv.ParseBool(strings.TrimSpace(v)); err == nil {
			return b
		}
	}
	return def
}

func getenvInt(key string, def int) int {
	if v, ok := os.LookupEnv(key); ok {
		if n, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {
			return n
		}
	}
	return def
}

// splitCSV 将逗号分隔的字符串拆分为切片，空字符串时返回 def。
func splitCSV(s string, def []string) []string {
	s = strings.TrimSpace(s)
	if s == "" {
		return def
	}
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if t := strings.TrimSpace(p); t != "" {
			out = append(out, t)
		}
	}
	if len(out) == 0 {
		return def
	}
	return out
}
