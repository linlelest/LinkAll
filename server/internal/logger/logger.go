// Package logger 提供结构化 JSON 日志工具。
// 基于 log/slog（标准库），输出 JSON 格式，便于日志聚合与查询。
// 启动时调用 Init 初始化全局 logger，后续直接使用 slog.Info/Warn/Error。
package logger

import (
	"log"
	"log/slog"
	"os"
	"strings"
)

// Init 初始化全局 slog 日志器。
// level: "debug" / "info" / "warn" / "error"
// 输出到 stdout，JSON 格式。
func Init(level string) {
	var lvl slog.Level
	switch strings.ToLower(level) {
	case "debug":
		lvl = slog.LevelDebug
	case "warn":
		lvl = slog.LevelWarn
	case "error":
		lvl = slog.LevelError
	default:
		lvl = slog.LevelInfo
	}
	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level:     lvl,
		AddSource: false,
	})
	slog.SetDefault(slog.New(handler))
	// 同时将标准 log 包的输出重定向到 slog（避免双重日志）
	log.SetFlags(0)
	log.SetOutput(os.Stderr)
}

// Info 记录 INFO 级别日志（含 key-value 属性）。
func Info(msg string, args ...any) {
	slog.Info(msg, args...)
}

// Warn 记录 WARN 级别日志。
func Warn(msg string, args ...any) {
	slog.Warn(msg, args...)
}

// Error 记录 ERROR 级别日志。
func Error(msg string, args ...any) {
	slog.Error(msg, args...)
}

// Debug 记录 DEBUG 级别日志。
func Debug(msg string, args ...any) {
	slog.Debug(msg, args...)
}
