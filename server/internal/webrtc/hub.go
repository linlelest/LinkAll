package webrtc

import (
	"sync"
	"time"

	"github.com/google/uuid"
)

// Session 表示一个控制会话：一个控制端连接到一个被控端设备。
// 信令服务器仅做 SDP/ICE 转发，不持有 PeerConnection 本身（P2P 直连由两端建立）。
type Session struct {
	ID         string    // 会话 ID（UUIDv4）
	DeviceID   string    // 被控设备编号
	Controller *Client   // 控制端连接（发起方）
	Device     *Client   // 被控端连接（接收方）
	Mode       string    // anonymous / same_account / device_code
	Status     string    // active / sleeping / closed / timeout
	StartedAt  time.Time
	LastActive time.Time
	mu         sync.RWMutex
}

// Touch 更新最近活跃时间。
func (s *Session) Touch() {
	s.mu.Lock()
	s.LastActive = time.Now()
	s.mu.Unlock()
}

// IsIdle 判断是否空闲超过阈值。
func (s *Session) IsIdle(threshold time.Duration) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return time.Since(s.LastActive) > threshold
}

// SetStatus 线程安全地更新状态。
func (s *Session) SetStatus(status string) {
	s.mu.Lock()
	s.Status = status
	s.mu.Unlock()
}

// Hub 管理所有 WebSocket 客户端与会话的映射。
// 设计：单进程内存索引，goroutine 并发安全。
type Hub struct {
	mu       sync.RWMutex
	clients  map[string]*Client              // clientID -> Client
	devices  map[string]*Client              // deviceID -> 被控端 Client
	sessions map[string]*Session             // sessionID -> Session
	// 反向索引：控制端 clientID -> 其发起的会话列表（一个控制端可发起多个会话）
	ctrlSessions map[string]map[string]bool
}

// NewHub 创建 Hub。
func NewHub() *Hub {
	return &Hub{
		clients:      make(map[string]*Client),
		devices:      make(map[string]*Client),
		sessions:     make(map[string]*Session),
		ctrlSessions: make(map[string]map[string]bool),
	}
}

// RegisterClient 注册客户端连接。
func (h *Hub) RegisterClient(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.clients[c.ID] = c
}

// UnregisterClient 注销客户端并清理会话。
func (h *Hub) UnregisterClient(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	delete(h.clients, c.ID)

	// 若为被控端，清理 devices 映射
	if c.DeviceID != "" {
		if cur, ok := h.devices[c.DeviceID]; ok && cur == c {
			delete(h.devices, c.DeviceID)
		}
	}

	// 关闭该客户端参与的所有会话
	for sid, sess := range h.sessions {
		if sess.Controller == c || sess.Device == c {
			sess.SetStatus("closed")
			delete(h.sessions, sid)
		}
	}
	// 清理反向索引
	delete(h.ctrlSessions, c.ID)
}

// RegisterDevice 注册被控端连接（绑定设备编号）。
// 若同一设备已有连接，旧连接会被强制踢出。
func (h *Hub) RegisterDevice(c *Client, deviceID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	c.DeviceID = deviceID
	if old, ok := h.devices[deviceID]; ok && old != c {
		// 踢出旧连接
		old.Close()
	}
	h.devices[deviceID] = c
}

// FindDevice 查找设备编号对应的被控端连接。
func (h *Hub) FindDevice(deviceID string) *Client {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return h.devices[deviceID]
}

// CreateSession 创建并注册会话。
func (h *Hub) CreateSession(deviceID string, controller *Client, mode string) *Session {
	h.mu.Lock()
	defer h.mu.Unlock()

	sess := &Session{
		ID:         uuid.NewString(),
		DeviceID:   deviceID,
		Controller: controller,
		Device:     h.devices[deviceID],
		Mode:       mode,
		Status:     "active",
		StartedAt:  time.Now(),
		LastActive: time.Now(),
	}
	h.sessions[sess.ID] = sess
	if controller != nil {
		if _, ok := h.ctrlSessions[controller.ID]; !ok {
			h.ctrlSessions[controller.ID] = make(map[string]bool)
		}
		h.ctrlSessions[controller.ID][sess.ID] = true
	}
	return sess
}

// FindSession 按 ID 查找会话。
func (h *Hub) FindSession(id string) *Session {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return h.sessions[id]
}

// CloseSession 关闭并删除会话。
func (h *Hub) CloseSession(id string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if sess, ok := h.sessions[id]; ok {
		sess.SetStatus("closed")
		delete(h.sessions, id)
		if sess.Controller != nil {
			if m, ok := h.ctrlSessions[sess.Controller.ID]; ok {
				delete(m, id)
			}
		}
	}
}

// ActiveSessionCount 返回活跃会话数（用于并发限制校验）。
func (h *Hub) ActiveSessionCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.sessions)
}

// OnlineDeviceCount 返回在线被控端数量。
func (h *Hub) OnlineDeviceCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.devices)
}

// SessionStats 用于 server_info 接口。
type SessionStats struct {
	Total         int `json:"total"`
	Active        int `json:"active"`
	OnlineDevices int `json:"onlineDevices"`
}

// Stats 返回统计信息。
func (h *Hub) Stats() SessionStats {
	h.mu.RLock()
	defer h.mu.RUnlock()
	active := 0
	for _, s := range h.sessions {
		if s.Status == "active" {
			active++
		}
	}
	return SessionStats{
		Total:         len(h.sessions),
		Active:        active,
		OnlineDevices: len(h.devices),
	}
}

// SweepIdleSessions 清理超过阈值的空闲会话，标记为 timeout。
// 由后台 goroutine 定期调用。
func (h *Hub) SweepIdleSessions(threshold time.Duration) int {
	h.mu.Lock()
	defer h.mu.Unlock()
	closed := 0
	for id, s := range h.sessions {
		if s.Status == "active" && s.IsIdle(threshold) {
			s.SetStatus("timeout")
			delete(h.sessions, id)
			closed++
		}
	}
	return closed
}

// AllDevices 返回所有在线被控端编号切片（用于调试/展示）。
func (h *Hub) AllDevices() []string {
	h.mu.RLock()
	defer h.mu.RUnlock()
	out := make([]string, 0, len(h.devices))
	for id := range h.devices {
		out = append(out, id)
	}
	return out
}
