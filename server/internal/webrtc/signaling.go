package webrtc

import (
	"database/sql"
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gofiber/adaptor/v2"
	"github.com/gofiber/fiber/v2"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"

	"github.com/linlelest/LinkALL/server/internal/protocol"
)

// 常量：心跳与重连参数（与 shared/messages.json 一致）。
const (
	HeartbeatInterval = 15 * time.Second  // WebSocket 心跳 15s
	WriteWait         = 10 * time.Second  // 单条消息写入超时
	PongWait          = 60 * time.Second  // 等待对端 pong 超时（心跳间隔的 4 倍）
	PingPeriod        = HeartbeatInterval // 服务端主动 ping 周期
	SessionIdleLimit  = 30 * time.Minute  // 会话空闲阈值
	MaxMessageSize    = 1 << 20           // 1MB（信令消息上限）
	SendBufferSize    = 256               // 发送通道缓冲
)

// Client 封装一个 WebSocket 客户端连接。
// 既可以是控制端（Controller），也可以是被控端（Device）。
type Client struct {
	ID         string
	Conn       *websocket.Conn
	DeviceID   string    // 被控端：设备编号；控制端：空
	Role       string    // "controller" / "device"
	UserID     int64     // 控制端：登录用户 ID；被控端：所属用户 ID
	Send       chan []byte
	hub        *Hub
	closeOnce  sync.Once
	closed     int32
}

// NewClient 构造客户端。
func NewClient(conn *websocket.Conn, hub *Hub, role string, userID int64) *Client {
	return &Client{
		ID:     uuid.NewString(),
		Conn:   conn,
		Role:   role,
		UserID: userID,
		Send:   make(chan []byte, SendBufferSize),
		hub:    hub,
	}
}

// IsClosed 返回连接是否已关闭。
func (c *Client) IsClosed() bool { return atomic.LoadInt32(&c.closed) == 1 }

// Close 关闭连接（幂等）。
func (c *Client) Close() {
	c.closeOnce.Do(func() {
		atomic.StoreInt32(&c.closed, 1)
		close(c.Send)
		_ = c.Conn.Close()
	})
}

// SendJSON 异步发送一条 JSON 消息。
// 队列满时关闭连接以避免慢客户端阻塞。
func (c *Client) SendJSON(v interface{}) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	select {
	case c.Send <- data:
		return nil
	default:
		// 队列满，关闭慢客户端
		go c.Close()
		return websocket.ErrCloseSent
	}
}

// SendRaw 发送原始字节消息。
func (c *Client) SendRaw(data []byte) error {
	select {
	case c.Send <- data:
		return nil
	default:
		go c.Close()
		return websocket.ErrCloseSent
	}
}

// === 信令服务器 ===

// SignalingServer WebSocket 信令服务器。
// 提供 /ws/signaling 端点，完成 SDP/ICE 交换与会话管理。
type SignalingServer struct {
	hub         *Hub
	upgrader    websocket.Upgrader
	jwtSecret   []byte
	iceConfig   *ICEConfig
	db          *sql.DB // 用于安全策略校验（可为 nil）
}

// NewSignalingServer 创建信令服务器。
// jwtSecret 为空时表示允许匿名连接（被控端通常无需 JWT）。
// db 用于在 onConnect 时校验全局安全策略（可为 nil，此时跳过校验）。
func NewSignalingServer(hub *Hub, jwtSecret string, ice *ICEConfig, db *sql.DB) *SignalingServer {
	return &SignalingServer{
		hub:       hub,
		jwtSecret: []byte(jwtSecret),
		iceConfig: ice,
		db:        db,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			// 允许所有来源：跨端连接场景，由 JWT 与设备码保障安全
			CheckOrigin: func(r *http.Request) bool { return true },
		},
	}
}

// checkSecurityPolicy 校验全局安全策略是否允许指定连接模式。
// 返回 (allowed, reason)。db 为 nil 时直接放行。
func (s *SignalingServer) checkSecurityPolicy(mode string) (bool, string) {
	if s.db == nil {
		return true, ""
	}
	var (
		allowAnon    int
		allowDevCode int
		allowRemote  int
	)
	_ = s.db.QueryRow(
		`SELECT allow_anonymous, allow_device_code, allow_remote_control FROM security_settings WHERE id = 1`,
	).Scan(&allowAnon, &allowDevCode, &allowRemote)
	if allowRemote == 0 {
		return false, "远程控制已被全局禁用"
	}
	switch mode {
	case "anonymous":
		if allowAnon == 0 {
			return false, "匿名连接已被禁用"
		}
	case "device_code":
		if allowDevCode == 0 {
			return false, "设备码连接已被禁用"
		}
	case "same_account":
		// 同账号连接始终允许（已通过 JWT 认证）
	}
	return true, ""
}

// Hub 返回内部 Hub 引用（供 handlers 使用）。
func (s *SignalingServer) Hub() *Hub { return s.hub }

// HandleFiber 返回 Fiber 兼容的 WebSocket 处理器。
// 使用 fiber/adaptor 将 net/http 处理器桥接回 Fiber 上下文，
// 因 gorilla/websocket 需直接操作 http.Request/ResponseWriter。
func (s *SignalingServer) HandleFiber() fiber.Handler {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// JWT 校验（可选）：从 query 参数 token 或 Authorization 头获取
		role := "controller"
		var userID int64
		if token := r.URL.Query().Get("token"); token != "" {
			if uid, err := s.parseJWT(token); err == nil {
				userID = uid
				role = "controller"
			}
		} else if auth := r.Header.Get("Authorization"); auth != "" {
			if uid, err := s.parseJWT(stripBearer(auth)); err == nil {
				userID = uid
				role = "controller"
			}
		}

		conn, err := s.upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Printf("[signaling] websocket upgrade 失败: %v", err)
			return
		}
		client := NewClient(conn, s.hub, role, userID)
		s.hub.RegisterClient(client)
		defer func() {
			s.hub.UnregisterClient(client)
		}()

		// 启动读、写、心跳 goroutine
		go s.writePump(client)
		go s.heartbeatPump(client)
		s.readPump(client)
	})

	return adaptor.HTTPHandler(handler)
}

// parseJWT 解析 JWT 并返回 userID。
func (s *SignalingServer) parseJWT(tokenStr string) (int64, error) {
	if len(s.jwtSecret) == 0 {
		return 0, nil
	}
	tokenStr = stripBearer(tokenStr)
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		return s.jwtSecret, nil
	})
	if err != nil || !token.Valid {
		return 0, err
	}
	if claims, ok := token.Claims.(jwt.MapClaims); ok {
		if uid, ok := claims["uid"].(float64); ok {
			return int64(uid), nil
		}
	}
	return 0, nil
}

// stripBearer 去掉 Bearer 前缀。
func stripBearer(s string) string {
	if len(s) > 7 && (s[:7] == "Bearer " || s[:7] == "bearer ") {
		return s[7:]
	}
	return s
}

// === 读写循环 ===

// readPump 读取客户端消息并分发。
func (s *SignalingServer) readPump(c *Client) {
	defer c.Close()
	c.Conn.SetReadLimit(MaxMessageSize)
	_ = c.Conn.SetReadDeadline(time.Now().Add(PongWait))
	c.Conn.SetPongHandler(func(string) error {
		_ = c.Conn.SetReadDeadline(time.Now().Add(PongWait))
		return nil
	})

	for {
		_, message, err := c.Conn.ReadMessage()
		if err != nil {
			if !websocket.IsUnexpectedCloseError(err,
				websocket.CloseNormalClosure, websocket.CloseGoingAway) {
				log.Printf("[signaling] read 错误 client=%s: %v", c.ID, err)
			}
			return
		}
		s.handleMessage(c, message)
	}
}

// writePump 从 Send 通道读取消息并发送给客户端。
func (s *SignalingServer) writePump(c *Client) {
	ticker := time.NewTicker(PingPeriod)
	defer func() {
		ticker.Stop()
		c.Close()
	}()
	for {
		select {
		case msg, ok := <-c.Send:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(WriteWait))
			if !ok {
				_ = c.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		case <-ticker.C:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(WriteWait))
			if err := c.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// heartbeatPump 心跳检测：每 15s 检查会话活跃度。
// 注意：WebSocket 层的 Ping 已在 writePump 中处理；
// 此处处理应用层 heartbeat 消息与 30min 会话空闲超时。
func (s *SignalingServer) heartbeatPump(c *Client) {
	ticker := time.NewTicker(HeartbeatInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			if c.IsClosed() {
				return
			}
			// 发送应用层 ping 信令
			env, _ := protocol.NewSignalEnvelope(protocol.SigPing, protocol.PingPayload{
				ClientTs: time.Now().UnixMilli(),
			})
			_ = c.SendJSON(env)
		}
	}
}

// === 消息分发 ===

// handleMessage 根据信令类型分发处理。
func (s *SignalingServer) handleMessage(c *Client, raw []byte) {
	env, err := protocol.DecodeSignalEnvelope(raw)
	if err != nil {
		s.sendError(c, "", protocol.ErrInvalidPayload, "消息格式错误")
		return
	}
	env.Ts = time.Now().UnixMilli() // 标准化时间戳

	switch env.Type {
	case protocol.SigConnect:
		s.onConnect(c, env)
	case protocol.SigSDPOffer:
		s.forwardToDevice(c, env)
	case protocol.SigSDPAnswer:
		s.forwardToController(c, env)
	case protocol.SigICE:
		s.forwardICE(c, env)
	case protocol.SigICEComplete:
		s.forwardToPeer(c, env)
	case protocol.SigBye:
		s.onBye(c, env)
	case protocol.SigPing:
		s.onPing(c, env)
	case protocol.SigPong:
		// 由对端发起，此处忽略
	case protocol.SigDCRelay:
		s.onDCRelay(c, env)
	default:
		s.sendError(c, env.SessionID, protocol.ErrInvalidPayload, "未知消息类型: "+string(env.Type))
	}
}

// onConnect 处理控制端发起的连接请求（含设备编号与模式）。
// 被控端也通过此消息注册自己的设备编号。
func (s *SignalingServer) onConnect(c *Client, env *protocol.SignalEnvelope) {
	var p protocol.ConnectPayload
	if err := protocol.ParseSignalPayload(env, &p); err != nil {
		s.sendError(c, "", protocol.ErrInvalidPayload, "解析 connect 失败")
		return
	}

	// 被控端注册：to 字段为空且 deviceId 等于本机注册编号
	if c.Role == "device" || (env.From == "device") {
		s.hub.RegisterDevice(c, p.DeviceID)
		// 回复确认
		ack, _ := protocol.NewSignalEnvelope(protocol.SigConnectAck, protocol.ConnectAckPayload{
			OK: true,
		})
		_ = c.SendJSON(ack)
		return
	}

	// 控制端发起连接：校验全局安全策略
	if allowed, reason := s.checkSecurityPolicy(string(p.Mode)); !allowed {
		ack, _ := protocol.NewSignalEnvelope(protocol.SigConnectAck, protocol.ConnectAckPayload{
			OK:   false,
			Code: protocol.ErrPermissionDenied,
		})
		_ = c.SendJSON(ack)
		log.Printf("[signaling] 连接被安全策略拒绝 device=%s mode=%s: %s", p.DeviceID, p.Mode, reason)
		return
	}

	// 查找被控端
	device := s.hub.FindDevice(p.DeviceID)
	if device == nil {
		ack, _ := protocol.NewSignalEnvelope(protocol.SigConnectAck, protocol.ConnectAckPayload{
			OK:   false,
			Code: protocol.ErrDeviceOffline,
		})
		_ = c.SendJSON(ack)
		return
	}

	// 创建会话
	sess := s.hub.CreateSession(p.DeviceID, c, string(p.Mode))
	sess.Touch()

	// 转发连接请求给被控端，附上 sessionId
	env.SessionID = sess.ID
	env.To = p.DeviceID
	env.From = "controller"
	_ = device.SendJSON(env)

	// 控制端等待被控端 connect_ack
	// 被控端收到后应回复 connect_ack，由 forwardToController 转发
}

// forwardToDevice 将控制端的消息（SDP offer / ICE）转发给被控端。
func (s *SignalingServer) forwardToDevice(c *Client, env *protocol.SignalEnvelope) {
	sess := s.hub.FindSession(env.SessionID)
	if sess == nil || sess.Device == nil {
		// 尝试通过 sessionId 中的设备编号
		s.sendError(c, env.SessionID, protocol.ErrSessionNotFound, "会话不存在或被控端离线")
		return
	}
	env.From = "controller"
	_ = sess.Device.SendJSON(env)
	if sess.Controller != nil {
		sess.Touch()
	}
}

// forwardToController 将被控端的消息（SDP answer / ICE）转发给控制端。
func (s *SignalingServer) forwardToController(c *Client, env *protocol.SignalEnvelope) {
	sess := s.hub.FindSession(env.SessionID)
	if sess == nil || sess.Controller == nil {
		s.sendError(c, env.SessionID, protocol.ErrSessionNotFound, "会话不存在或控制端离线")
		return
	}
	env.From = "device"
	_ = sess.Controller.SendJSON(env)
	if sess.Device != nil {
		sess.Touch()
	}
}

// forwardICE 根据发送方角色转发 ICE candidate。
func (s *SignalingServer) forwardICE(c *Client, env *protocol.SignalEnvelope) {
	if c.Role == "controller" {
		s.forwardToDevice(c, env)
	} else {
		s.forwardToController(c, env)
	}
}

// forwardToPeer 通用转发：根据角色转发到对端。
func (s *SignalingServer) forwardToPeer(c *Client, env *protocol.SignalEnvelope) {
	if c.Role == "controller" {
		s.forwardToDevice(c, env)
	} else {
		s.forwardToController(c, env)
	}
}

// onBye 处理会话结束。
func (s *SignalingServer) onBye(c *Client, env *protocol.SignalEnvelope) {
	if env.SessionID != "" {
		sess := s.hub.FindSession(env.SessionID)
		if sess != nil {
			// 通知对端
			peer := sess.Controller
			if c == sess.Controller {
				peer = sess.Device
			}
			if peer != nil {
				_ = peer.SendJSON(env)
			}
			s.hub.CloseSession(env.SessionID)
		}
	}
}

// onPing 处理应用层心跳，回复 pong（含 RTT）。
func (s *SignalingServer) onPing(c *Client, env *protocol.SignalEnvelope) {
	var p protocol.PingPayload
	_ = protocol.ParseSignalPayload(env, &p)
	var rtt int64
	if p.ClientTs > 0 {
		rtt = time.Now().UnixMilli() - p.ClientTs
	}
	ack, _ := protocol.NewSignalEnvelope(protocol.SigPong, protocol.PongPayload{
		ServerTs: time.Now().UnixMilli(),
		RTT:      int(rtt),
	})
	_ = c.SendJSON(ack)
}

// onDCRelay 处理 DataChannel 消息中继（P2P 不可达时走信令转发）。
// 信令信封的 payload 内嵌一个 DataChannel Envelope（含 type/ts/seq/payload）。
// 文件传输相关消息（file_meta/file_chunk/file_ack/file_complete 等）经此通道中继，
// 由 FileRelayManager 做分片校验与传输队列统计。
func (s *SignalingServer) onDCRelay(c *Client, env *protocol.SignalEnvelope) {
	sess := s.hub.FindSession(env.SessionID)
	if sess == nil {
		s.sendError(c, env.SessionID, protocol.ErrSessionNotFound, "会话不存在")
		return
	}
	// 解析内嵌的 DataChannel Envelope
	var dcEnv protocol.Envelope
	if err := json.Unmarshal(env.Payload, &dcEnv); err != nil {
		s.sendError(c, env.SessionID, protocol.ErrInvalidPayload, "dc_relay payload 解析失败")
		return
	}
	// 防重放：校验时间戳（允许 60 秒窗口）
	if dcEnv.Ts > 0 {
		diff := time.Now().UnixMilli() - dcEnv.Ts
		if diff > 60000 || diff < -60000 {
			s.sendError(c, env.SessionID, protocol.ErrReplayDetected, "消息时间戳超出窗口")
			return
		}
	}
	// 通过文件中继管理器转发并校验
	fromController := c.Role == "controller"
	s.hub.FileRelay().RelayFileMessage(sess, fromController, &dcEnv)
}

// sendError 向客户端发送错误信令。
func (s *SignalingServer) sendError(c *Client, sessionID string, code protocol.ErrorCode, msg string) {
	env, _ := protocol.NewSignalEnvelope(protocol.SigError, protocol.ErrorPayload{
		Code:    code,
		Message: msg,
	})
	env.SessionID = sessionID
	_ = c.SendJSON(env)
}

// === 客户端重连策略（供客户端参考，服务端不维护客户端重连状态） ===
//
// 客户端断线后应使用指数退避重连：
//   delay = min(initialDelay * 2^attempt, maxDelay)
//   initial=1s, multiplier=2, max=30s
// 会话超时 30min 无操作自动休眠（由 Hub.SweepIdleSessions 处理）。
