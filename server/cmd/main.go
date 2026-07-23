// Package main 是 LinkALL 服务端的入口。
// 支持子命令：
//   init   交互式初始化（创建超级管理员、生成 .env 与 data/ 目录）
//   (无)   启动 HTTP + WebSocket 信令服务
package main

import (
	"bufio"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	fiberlogger "github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"golang.org/x/term"

	"github.com/linlelest/LinkALL/server/internal/auth"
	"github.com/linlelest/LinkALL/server/internal/config"
	"github.com/linlelest/LinkALL/server/internal/db"
	"github.com/linlelest/LinkALL/server/internal/handlers"
	applogger "github.com/linlelest/LinkALL/server/internal/logger"
	"github.com/linlelest/LinkALL/server/internal/i18n"
	"github.com/linlelest/LinkALL/server/internal/static"
	"github.com/linlelest/LinkALL/server/internal/webrtc"
)

const (
	envFileName = ".env"
)

// appVersion 使用 var 而非 const，便于 CI 通过 ldflags="-X main.appVersion=xxx" 注入版本号。
var appVersion = "0.1.0"

func main() {
	// 解析子命令
	args := os.Args[1:]
	if len(args) > 0 {
		switch args[0] {
		case "init":
			runInit(args[1:])
			return
		case "version", "-v", "--version":
			fmt.Printf("linkall-server v%s (Phase 1)\n", appVersion)
			return
		case "help", "-h", "--help":
			printHelp()
			return
		}
	}

	// 默认：启动服务
	runServer(args)
}

// printHelp 打印帮助信息。
func printHelp() {
	fmt.Println(`LinkALL 服务端 v` + appVersion + `

用法:
  remote-server [flags]        启动服务
  remote-server init [flags]   交互式初始化（创建超级管理员）
  remote-server version        显示版本

Flags:
  -port string   HTTP 监听端口（覆盖 .env SERVER_PORT）
  -db string     SQLite 数据库路径（覆盖 .env DB_PATH）
  -env-path string  .env 文件路径（默认 ./.env）
  -env string    运行环境 dev/prod`)
}

// === init 子命令 ===

// runInit 执行交互式初始化：
// 1. 检查是否已有超级管理员
// 2. 提示输入用户名/密码/确认密码（密码隐藏输入）
// 3. 生成 .env（含随机 JWT_SECRET）
// 4. 创建 data/ 目录
// 5. 创建超级管理员账户
func runInit(flags []string) {
	fmt.Println()
	fmt.Println("╔══════════════════════════════════════════════════╗")
	fmt.Println("║       LinkALL 服务端初始化程序 (Phase 1)         ║")
	fmt.Println("╚══════════════════════════════════════════════════╝")
	fmt.Println()

	// 加载已有 .env（若存在），用于读取 DB 路径等配置
	cfg, err := config.Load(flags)
	if err != nil {
		log.Fatalf("[init] 加载配置失败: %v", err)
	}

	// 生成 .env（若不存在）
	if _, err := os.Stat(cfg.MigrationsDir); os.IsNotExist(err) {
		_ = os.MkdirAll(cfg.MigrationsDir, 0o755)
	}
	// 尝试在当前工作目录生成 .env
	if _, err := os.Stat(envFileName); os.IsNotExist(err) {
		fmt.Println("[init] 正在生成 .env 配置文件...")
		if err := generateEnvFile(envFileName, cfg); err != nil {
			log.Fatalf("[init] 生成 .env 失败: %v", err)
		}
		fmt.Printf("[init] 已生成 %s\n", envFileName)
		// 重新加载配置以应用 .env
		cfg, _ = config.Load(flags)
	} else {
		fmt.Printf("[init] %s 已存在，跳过生成\n", envFileName)
	}

	// 创建 data/ 目录
	fmt.Println("[init] 正在创建 data/ 数据目录...")
	if err := cfg.EnsureDirs(); err != nil {
		log.Fatalf("[init] 创建目录失败: %v", err)
	}
	fmt.Printf("[init] 已创建 %s/\n", cfg.DataDir)

	// 打开数据库并执行迁移
	fmt.Println("[init] 正在打开数据库并执行迁移...")
	database, err := db.Open(cfg.DBPath)
	if err != nil {
		log.Fatalf("[init] 打开数据库失败: %v", err)
	}
	defer database.Close()

	if err := db.RunMigrations(database, cfg.MigrationsDir); err != nil {
		log.Fatalf("[init] 执行迁移失败: %v", err)
	}
	fmt.Println("[init] 数据库迁移完成")

	// 初始化 i18n
	if err := i18n.InitGlobal(cfg.LocalesDir); err != nil {
		log.Printf("[init] 警告: 加载语言包失败: %v", err)
	}

	// 创建超级管理员
	hasher := auth.NewPasswordHasher(nil)

	// 检查是否已有超级管理员
	var adminCount int
	_ = database.QueryRow(`SELECT COUNT(*) FROM users WHERE role = 'superadmin'`).Scan(&adminCount)
	if adminCount > 0 {
		fmt.Printf("\n[init] 检测到已有 %d 个超级管理员账户。\n", adminCount)
		fmt.Print("[init] 是否继续创建新的超级管理员？(y/N): ")
		reader := bufio.NewReader(os.Stdin)
		answer, _ := reader.ReadString('\n')
		answer = strings.TrimSpace(strings.ToLower(answer))
		if answer != "y" && answer != "yes" {
			fmt.Println("[init] 已取消。直接运行 ./remote-server 启动服务。")
			return
		}
	}

	// 交互式输入
	fmt.Println()
	fmt.Println("=== 创建超级管理员账户 ===")
	username := promptUsername()
	password := promptPassword()

	// 哈希密码
	fmt.Println("\n[init] 正在哈希密码（Argon2id）...")
	hash, err := hasher.Hash(password)
	if err != nil {
		log.Fatalf("[init] 密码哈希失败: %v", err)
	}

	// 写入数据库
	result, err := database.Exec(
		`INSERT INTO users (username, password_hash, role, status) VALUES (?, ?, 'superadmin', 'active')`,
		username, hash,
	)
	if err != nil {
		log.Fatalf("[init] 创建超级管理员失败: %v", err)
	}
	uid, _ := result.LastInsertId()

	fmt.Println()
	fmt.Println("╔══════════════════════════════════════════════════╗")
	fmt.Println("║                  初始化完成！                     ║")
	fmt.Println("╠══════════════════════════════════════════════════╣")
	fmt.Printf("║  超级管理员: %-36s║\n", truncate(username, 36))
	fmt.Printf("║  用户 ID: %-38d║\n", uid)
	fmt.Printf("║  .env 路径: %-37s║\n", truncate(envFileName, 37))
	fmt.Printf("║  数据库: %-39s║\n", truncate(cfg.DBPath, 39))
	fmt.Println("║                                                  ║")
	fmt.Println("║  请运行 ./remote-server 启动服务                  ║")
	fmt.Println("╚══════════════════════════════════════════════════╝")
}

// promptUsername 提示输入用户名。
func promptUsername() string {
	reader := bufio.NewReader(os.Stdin)
	for {
		fmt.Print("请输入超级管理员用户名（3-32 字符）: ")
		input, _ := reader.ReadString('\n')
		username := strings.TrimSpace(input)
		if len(username) < 3 || len(username) > 32 {
			fmt.Println("  ✗ 用户名长度需 3-32 字符，请重试")
			continue
		}
		// 检查是否已存在
		// 注：此处简化，实际数据库检查在写入时由 UNIQUE 约束保证
		return username
	}
}

// promptPassword 提示输入密码（隐藏输入）并二次确认。
func promptPassword() string {
	for {
		fmt.Print("请输入密码（至少 8 位）: ")
		pw1, err := readHiddenPassword()
		if err != nil {
			// 回退到明文输入（非终端环境）
			reader := bufio.NewReader(os.Stdin)
			pw1, _ = reader.ReadString('\n')
			pw1 = strings.TrimSpace(pw1)
		}
		fmt.Println()
		if len(pw1) < 8 {
			fmt.Println("  ✗ 密码长度不足 8 位，请重试")
			continue
		}

		fmt.Print("请再次输入密码以确认: ")
		pw2, err := readHiddenPassword()
		if err != nil {
			reader := bufio.NewReader(os.Stdin)
			pw2, _ = reader.ReadString('\n')
			pw2 = strings.TrimSpace(pw2)
		}
		fmt.Println()

		if pw1 != pw2 {
			fmt.Println("  ✗ 两次输入的密码不一致，请重试")
			continue
		}
		return pw1
	}
}

// readHiddenPassword 从终端读取密码（不回显）。
// 非终端环境（如管道）返回错误。
func readHiddenPassword() (string, error) {
	fd := int(os.Stdin.Fd())
	if !term.IsTerminal(fd) {
		return "", fmt.Errorf("stdin 不是终端")
	}
	bytes, err := term.ReadPassword(fd)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// generateEnvFile 生成默认 .env 文件。
func generateEnvFile(path string, cfg *config.Config) error {
	// 生成随机 JWT_SECRET
	secret := cfg.JWTSecret
	if secret == "" {
		secret = generateRandomSecret(32)
	}

	content := fmt.Sprintf(`# LinkALL 服务端配置文件
# 由 init 命令生成于 %s

# === 服务器 ===
SERVER_PORT=%s
ENV=dev
OFFICIAL_SERVER=%s

# === 数据库 ===
DB_PATH=%s

# === JWT 认证 ===
JWT_SECRET=%s
JWT_EXPIRY=24h
JWT_SCHEME=HS256

# === WebRTC / STUN / TURN ===
STUN_SERVERS=stun:stun.l.google.com:19302
TURN_SERVERS=
TURN_USERNAME=
TURN_CREDENTIAL=

# === 安全 ===
FORCE_HTTPS=false
MAX_CONCURRENT_SESSIONS=100
DATA_RETENTION_DAYS=90

# === 路径 ===
DATA_DIR=./data
MIGRATIONS_DIR=./migrations
OTA_DIR=./ota
LOCALES_DIR=./internal/i18n/locales
`, time.Now().Format(time.RFC3339),
		cfg.ServerPort,
		cfg.OfficialServer,
		cfg.DBPath,
		secret,
	)

	// 写入文件，权限 0600（仅所有者可读写）
	return os.WriteFile(path, []byte(content), 0o600)
}

// === 启动服务 ===

// runServer 启动 HTTP + WebSocket 信令服务。
func runServer(flags []string) {
	// 加载配置
	cfg, err := config.Load(flags)
	if err != nil {
		log.Fatalf("[server] 加载配置失败: %v", err)
	}

	// 初始化结构化 JSON 日志（log/slog）
	applogger.Init(cfg.Env)
	defer func() {
		// 确保日志刷出
		_ = os.Stdout.Sync()
	}()
	slog.Info("[server] 结构化日志已初始化", "env", cfg.Env)

	// 创建必要目录
	if err := cfg.EnsureDirs(); err != nil {
		log.Fatalf("[server] 创建目录失败: %v", err)
	}

	// 打开数据库
	database, err := db.Open(cfg.DBPath)
	if err != nil {
		log.Fatalf("[server] 打开数据库失败: %v", err)
	}
	defer database.Close()

	// 执行迁移
	if err := db.RunMigrations(database, cfg.MigrationsDir); err != nil {
		log.Fatalf("[server] 执行迁移失败: %v", err)
	}
	log.Println("[server] 数据库迁移完成")

	// 检查是否有超级管理员，若无则提示访问网页端完成首次初始化
	var adminCount int
	_ = database.QueryRow(`SELECT COUNT(*) FROM users WHERE role = 'superadmin'`).Scan(&adminCount)
	if adminCount == 0 {
		log.Println("[server] 提示: 尚未创建超级管理员，请访问网页端完成首次初始化（/setup）")
	}

	// 初始化 i18n
	if err := i18n.InitGlobal(cfg.LocalesDir); err != nil {
		log.Printf("[server] 警告: 加载语言包失败: %v", err)
	} else {
		i18nLoader := i18n.Global()
		i18nLoader.Watch(5 * time.Second) // 5 秒检查一次热重载
		defer i18nLoader.StopWatch()
	}

	// 构造依赖
	jwtMgr := auth.NewJWTManager(cfg.JWTSecret, cfg.JWTExpiry, cfg.JWTScheme)
	hasher := auth.NewPasswordHasher(nil)
	inviteMgr := auth.NewInviteManager(database)

	// 创建 WebRTC 信令服务器
	hub := webrtc.NewHub(database)
	iceConfig := &webrtc.ICEConfig{
		STUNServers:    cfg.STUNServers,
		TURNServers:    cfg.TURNServers,
		TURNUsername:   cfg.TURNUsername,
		TURNCredential: cfg.TURNCredential,
	}
	signaling := webrtc.NewSignalingServer(hub, cfg.JWTSecret, iceConfig, database)

	// 初始化 RSA 密钥对（用于设备码非对称加密传输），并确保文件权限 0600
	if err := handlers.EnsureRSAKeyPair(cfg.DataDir); err != nil {
		log.Printf("[server] 警告: RSA 密钥初始化失败: %v", err)
	}
	// 初始化 Ed25519 密钥对（用于公告数字签名），并确保文件权限 0600
	if err := handlers.EnsureEd25519KeyPair(cfg.DataDir); err != nil {
		log.Printf("[server] 警告: Ed25519 密钥初始化失败: %v", err)
	}
	handlers.EnsureSecureFilePermissions(envFileName, cfg.DataDir)

	// 后台 goroutine：定期清理空闲会话（30 分钟超时）
	go func() {
		ticker := time.NewTicker(1 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			n := hub.SweepIdleSessions(30 * time.Minute)
			if n > 0 {
				log.Printf("[server] 已清理 %d 个空闲会话", n)
			}
		}
	}()

	// 构造 handlers 依赖
	deps := handlers.NewDeps(database, jwtMgr, hasher, inviteMgr, signaling, hub, cfg, i18n.Global())

	// 创建 Fiber 应用
	app := fiber.New(fiber.Config{
		AppName:      "LinkALL Server v" + appVersion,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
		BodyLimit:    50 * 1024 * 1024, // 50MB（文件上传预留）
	})

	// 中间件
	app.Use(recover.New())
	app.Use(fiberlogger.New(fiberlogger.Config{
		Format:     `{"time":"${time}","status":${status},"method":"${method}","path":"${path}","latency":"${latency}","ip":"${ip}","ua":"${ua}"}` + "\n",
		TimeFormat: "2006-01-02T15:04:05.000Z07:00",
		TimeZone:   "Local",
	}))
	app.Use(cors.New(cors.Config{
		AllowOrigins:     "*",
		AllowMethods:     "GET,POST,PUT,DELETE,OPTIONS",
		AllowHeaders:     "Origin,Content-Type,Accept,Authorization,X-Requested-With",
		AllowCredentials: false,
		MaxAge:           300,
	}))

	// 注册路由
	handlers.RegisterRoutes(app, deps)

	// 检查网页端是否已嵌入
	frontendStatus := "未嵌入"
	if static.HasFrontend() {
		frontendStatus = "已嵌入"
	}
	// 检查是否需要首次初始化（无 superadmin）
	setupStatus := "已完成"
	if deps.NeedsSetup() {
		setupStatus = "待初始化"
	}

	// 输出启动信息
	fmt.Println()
	fmt.Println("╔══════════════════════════════════════════════════╗")
	fmt.Println("║         LinkALL 服务端 v" + appVersion + " (Phase 6)          ║")
	fmt.Println("╠══════════════════════════════════════════════════╣")
	fmt.Printf("║  监听端口: %-37s║\n", ":"+cfg.ServerPort)
	fmt.Printf("║  数据库: %-39s║\n", truncate(cfg.DBPath, 39))
	fmt.Printf("║  ICE 配置: %-38s║\n", truncate(iceConfig.Describe(), 38))
	fmt.Printf("║  会话超时: %-38s║\n", "30min")
	fmt.Printf("║  心跳间隔: %-38s║\n", "15s")
	fmt.Printf("║  网页前端: %-38s║\n", frontendStatus)
	fmt.Printf("║  初始化: %-40s║\n", setupStatus)
	fmt.Println("║                                                  ║")
	fmt.Println("║  API 文档:                                        ║")
	fmt.Println("║    GET  /api/health                               ║")
	fmt.Println("║    POST /api/auth/login                           ║")
	fmt.Println("║    POST /api/auth/register                        ║")
	fmt.Println("║    GET  /ws/signaling (WebSocket)                ║")
	if frontendStatus == "已嵌入" {
		fmt.Println("║  网页前端: http://<服务器地址>:" + cfg.ServerPort + "/            ║")
	}
	fmt.Println("╚══════════════════════════════════════════════════╝")
	if setupStatus == "待初始化" {
		fmt.Println("[server] 请访问网页端 http://<服务器地址>:" + cfg.ServerPort + "/#/setup 创建管理员账户")
	}
	fmt.Println()

	// 优雅关闭
	go func() {
		quit := make(chan os.Signal, 1)
		signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
		<-quit
		fmt.Println("\n[server] 正在关闭服务...")
		_ = app.Shutdown()
	}()

	// 启动监听
	addr := ":" + strings.TrimPrefix(cfg.ServerPort, ":")
	if err := app.Listen(addr); err != nil {
		log.Fatalf("[server] 启动失败: %v", err)
	}

	fmt.Println("[server] 服务已停止")
}

// === 辅助函数 ===

// truncate 截断字符串到指定长度。
func truncate(s string, maxLen int) string {
	// 处理中文字符宽度：简化实现，按 rune 计数
	runes := []rune(s)
	if len(runes) <= maxLen {
		// 补齐空格
		pad := maxLen - len(runes)
		return s + strings.Repeat(" ", pad)
	}
	return string(runes[:maxLen-3]) + "..."
}

// generateRandomSecret 生成随机密钥（与 config 包中同名函数保持一致）。
// 使用 crypto/rand 确保密钥安全性。
func generateRandomSecret(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		// 兜底：使用时间戳（不应发生在正常环境）
		return fmt.Sprintf("linkall-fallback-%x", time.Now().UnixNano())
	}
	return hex.EncodeToString(b)
}
