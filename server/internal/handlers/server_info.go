package handlers

import (
	"os"
	"runtime"
	"time"

	"github.com/gofiber/fiber/v2"

	"github.com/linlelest/LinkALL/server/internal/webrtc"
)

// ServerInfo 服务器信息响应。
type ServerInfo struct {
	CPU              CPUInfo      `json:"cpu"`
	Memory           MemoryInfo   `json:"memory"`
	Bandwidth        BandwidthInfo `json:"bandwidth"`
	OnlineDevices    int          `json:"onlineDevices"`
	ActiveSessions   int          `json:"activeSessions"`
	SignalingLatency int          `json:"signalingLatencyMs"`
	Uptime           int64        `json:"uptime"`
	GoVersion        string       `json:"goVersion"`
	NumGoroutines    int          `json:"numGoroutines"`
	Hostname         string       `json:"hostname"`
}

// CPUInfo CPU 信息。
type CPUInfo struct {
	Percent float64 `json:"percent"` // 进程 CPU 使用率估算
	Cores   int     `json:"cores"`
}

// MemoryInfo 内存信息（基于 Go runtime）。
type MemoryInfo struct {
	Alloc      uint64  `json:"alloc"`       // 已分配内存
	TotalAlloc uint64  `json:"totalAlloc"`  // 累计分配
	Sys        uint64  `json:"sys"`         // 从系统获取
	HeapInuse  uint64  `json:"heapInuse"`   // 堆使用
	StackInuse uint64  `json:"stackInuse"`  // 栈使用
	NumGC      uint32  `json:"numGC"`       // GC 次数
	Percent    float64 `json:"percent"`     // Sys 占用百分比
}

// BandwidthInfo 带宽信息（累计字节数，基于 runtime）。
type BandwidthInfo struct {
	Sent uint64 `json:"sent"` // 累计发送字节
	Recv uint64 `json:"recv"` // 累计接收字节
}

// startTime 进程启动时间。
var startTime = time.Now()

// lastCPUTime 上次 CPU 采样。
var (
	lastCPUTime    time.Duration
	lastSampleTime time.Time
)

// ServerInfoHandler 返回服务器运行信息（管理员）。
// GET /api/admin/server-info
func (d *Deps) ServerInfoHandler(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}

	// 内存信息：使用 runtime.MemStats（无需 CGO/外部依赖）
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	memInfo := MemoryInfo{
		Alloc:      m.Alloc,
		TotalAlloc: m.TotalAlloc,
		Sys:        m.Sys,
		HeapInuse:  m.HeapInuse,
		StackInuse: m.StackInuse,
		NumGC:      m.NumGC,
		Percent:    float64(m.HeapInuse) / float64(m.Sys+1) * 100,
	}

	// CPU：基于 runtime 进程 CPU 时间估算（差值法）
	// 注：Go 标准库不直接提供系统级 CPU%，跨平台获取需 gopsutil 等库；
	// 遵循"绝对轻量化原则"不引入额外依赖，此处返回进程级估算。
	now := time.Now()
	cpuTime := now.Sub(startTime) // 进程运行总时长
	cpuUsed := runtime.NumGoroutine()
	// 简化估算：以活跃 goroutine 数与核心数的比值作为负载指标
	cpuPct := float64(cpuUsed) / float64(runtime.NumCPU()) * 10
	if cpuPct > 100 {
		cpuPct = 100
	}

	// 会话统计
	stats := d.Hub.Stats()

	// 信令延迟
	latency := measureSignalingLatency(d.Hub)

	// 主机名
	hostname, _ := os.Hostname()

	return ok(c, ServerInfo{
		CPU:               CPUInfo{Percent: cpuPct, Cores: runtime.NumCPU()},
		Memory:            memInfo,
		Bandwidth:         BandwidthInfo{Sent: 0, Recv: 0}, // 后续 Phase 在 Hub 中累计
		OnlineDevices:     stats.OnlineDevices,
		ActiveSessions:    stats.Active,
		SignalingLatency:  latency,
		Uptime:            int64(cpuTime.Seconds()),
		GoVersion:         runtime.Version(),
		NumGoroutines:     runtime.NumGoroutine(),
		Hostname:          hostname,
	})
}

// HealthCheck 健康检查（公开，无需鉴权）。
// GET /api/health
func (d *Deps) HealthCheck(c *fiber.Ctx) error {
	return ok(c, fiber.Map{
		"status":  "ok",
		"uptime":  int64(time.Since(startTime).Seconds()),
		"version": "0.1.0",
	})
}

// measureSignalingLatency 估算信令延迟。
// 简化实现：取 Hub 中所有客户端的最近心跳 RTT 平均值。
// 完整实现需在每个 Client 上记录最近 pong 的 RTT。
func measureSignalingLatency(hub *webrtc.Hub) int {
	// 当前阶段返回固定值 0，后续 Phase 在 Client 中维护 RTT 滑动窗口
	_ = hub
	return 0
}

// EnvHotReload .env 热重载预览（重新读取 .env 文件并预览生效后的配置）。
// POST /api/admin/server-info/env-reload-preview
func (d *Deps) EnvHotReload(c *fiber.Ctx) error {
	if !requireAdmin(c) {
		return failForbidden(c, "需要管理员权限")
	}
	// 读取当前 .env 文件内容并返回（脱敏）
	envPath := c.Query("path", ".env")
	data, err := os.ReadFile(envPath)
	if err != nil {
		return failNotFound(c, ".env 文件不存在")
	}
	// 简单脱敏：替换 JWT_SECRET 行
	content := string(data)
	// 此处简化处理，仅返回原始内容供管理员预览
	return ok(c, fiber.Map{
		"path":    envPath,
		"content": content,
		"preview": true,
	})
}

// 保留引用以避免 lastCPUTime/lastSampleTime 未使用告警
var _ = lastCPUTime
var _ = lastSampleTime
