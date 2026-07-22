// Package protocol 定义 WebRTC DataChannel 与 WebSocket 信令通道上传输的
// 控制指令/信令消息的 Go 结构体。与 shared/protocol.json / shared/messages.json
// 一一对应，便于跨端序列化与反序列化。
package protocol

import (
	"encoding/json"
	"time"
)

// === 公共枚举 ===

// MessageType 控制指令消息类型。
type MessageType string

const (
	MsgKeyboard      MessageType = "keyboard"
	MsgMouse         MessageType = "mouse"
	MsgWheel         MessageType = "wheel"
	MsgFileMeta      MessageType = "file_meta"
	MsgFileChunk     MessageType = "file_chunk"
	MsgFileAck       MessageType = "file_ack"
	MsgFileComplete  MessageType = "file_complete"
	MsgSettingsSync  MessageType = "settings_sync"
	MsgHeartbeat     MessageType = "heartbeat"
	MsgHeartbeatAck  MessageType = "heartbeat_ack"
	MsgStatus        MessageType = "status"
	MsgError         MessageType = "error"
	MsgAuth          MessageType = "auth"
	MsgAuthAck       MessageType = "auth_ack"
	MsgScreenConfig  MessageType = "screen_config"
	MsgPrivacyScreen MessageType = "privacy_screen"
	MsgClipboard      MessageType = "clipboard"
)

// Platform 客户端平台。
type Platform string

const (
	PlatformWindows Platform = "windows"
	PlatformLinux   Platform = "linux"
	PlatformAndroid Platform = "android"
	PlatformWeb     Platform = "web"
	PlatformUnknown Platform = "unknown"
)

// DeviceStatus 设备在线状态。
type DeviceStatus string

const (
	DeviceOffline  DeviceStatus = "offline"
	DeviceOnline   DeviceStatus = "online"
	DeviceBusy     DeviceStatus = "busy"
	DeviceSleeping DeviceStatus = "sleeping"
)

// SessionStatus 会话状态。
type SessionStatus string

const (
	SessionActive   SessionStatus = "active"
	SessionSleeping SessionStatus = "sleeping"
	SessionClosed   SessionStatus = "closed"
	SessionTimeout  SessionStatus = "timeout"
	SessionPending  SessionStatus = "pending"
)

// ConnectionMode 连接模式。
type ConnectionMode string

const (
	ModeAnonymous   ConnectionMode = "anonymous"
	ModeSameAccount  ConnectionMode = "same_account"
	ModeDeviceCode   ConnectionMode = "device_code"
)

// MouseButton 鼠标按键。
type MouseButton string

const (
	MouseLeft    MouseButton = "left"
	MouseRight   MouseButton = "right"
	MouseMiddle  MouseButton = "middle"
	MouseBack    MouseButton = "back"
	MouseForward MouseButton = "forward"
)

// MouseAction 鼠标动作。
type MouseAction string

const (
	MouseMove        MouseAction = "move"
	MouseDown        MouseAction = "down"
	MouseUp          MouseAction = "up"
	MouseClick       MouseAction = "click"
	MouseDoubleClick MouseAction = "double_click"
)

// ErrorCode 错误码（与 shared/errors.json 一致）。
type ErrorCode string

const (
	ErrOK                       ErrorCode = "ERR_OK"
	ErrAuthFailed               ErrorCode = "ERR_AUTH_FAILED"
	ErrAuthExpired              ErrorCode = "ERR_AUTH_EXPIRED"
	ErrPermissionDenied         ErrorCode = "ERR_PERMISSION_DENIED"
	ErrDeviceNotFound           ErrorCode = "ERR_DEVICE_NOT_FOUND"
	ErrDeviceOffline            ErrorCode = "ERR_DEVICE_OFFLINE"
	ErrDeviceBusy               ErrorCode = "ERR_DEVICE_BUSY"
	ErrDeviceCodeInvalid        ErrorCode = "ERR_DEVICE_CODE_INVALID"
	ErrInviteCodeInvalid        ErrorCode = "ERR_INVITE_CODE_INVALID"
	ErrInviteCodeUsed           ErrorCode = "ERR_INVITE_CODE_USED"
	ErrInviteCodeExpired        ErrorCode = "ERR_INVITE_CODE_EXPIRED"
	ErrUserBanned               ErrorCode = "ERR_USER_BANNED"
	ErrMaxSessionsReached       ErrorCode = "ERR_MAX_SESSIONS_REACHED"
	ErrSessionNotFound          ErrorCode = "ERR_SESSION_NOT_FOUND"
	ErrSessionTimeout           ErrorCode = "ERR_SESSION_TIMEOUT"
	ErrSignalingHandshakeFailed ErrorCode = "ERR_SIGNALING_HANDSHAKE_FAILED"
	ErrWebRTCConnectionFailed   ErrorCode = "ERR_WEBRTC_CONNECTION_FAILED"
	ErrFileTransferFailed       ErrorCode = "ERR_FILE_TRANSFER_FAILED"
	ErrFileHashMismatch         ErrorCode = "ERR_FILE_HASH_MISMATCH"
	ErrRateLimited              ErrorCode = "ERR_RATE_LIMITED"
	ErrInvalidPayload           ErrorCode = "ERR_INVALID_PAYLOAD"
	ErrReplayDetected           ErrorCode = "ERR_REPLAY_DETECTED"
	ErrInternalError            ErrorCode = "ERR_INTERNAL_ERROR"
	ErrMaintenance              ErrorCode = "ERR_MAINTENANCE"
)

// === 消息信封 ===

// Envelope 控制指令外层信封。
type Envelope struct {
	Type      MessageType `json:"type"`
	Ts        int64       `json:"ts"`         // Unix 毫秒
	Seq       int64       `json:"seq"`        // 单调递增
	SessionID string      `json:"sessionId,omitempty"`
	Payload   json.RawMessage `json:"payload,omitempty"`
}

// === 控制指令 Payload ===

// KeyboardPayload 键盘事件。
type KeyboardPayload struct {
	Key       string   `json:"key"`
	Action    string   `json:"action"` // down / up / press
	Modifiers []string `json:"modifiers,omitempty"`
}

// MousePayload 鼠标事件。
type MousePayload struct {
	Action MouseAction `json:"action"`
	Button MouseButton `json:"button,omitempty"`
	X      int         `json:"x,omitempty"`
	Y      int         `json:"y,omitempty"`
	DX     int         `json:"dx,omitempty"`
	DY     int         `json:"dy,omitempty"`
}

// WheelPayload 滚轮事件。
type WheelPayload struct {
	DeltaX int `json:"deltaX,omitempty"`
	DeltaY int `json:"deltaY"`
}

// FileMetaPayload 文件传输元数据。
type FileMetaPayload struct {
	TransferID string `json:"transferId"`
	Name       string `json:"name"`
	Size       int64  `json:"size"`
	Hash       string `json:"hash"`
	Direction  string `json:"direction,omitempty"` // upload / download
	RemotePath string `json:"remotePath,omitempty"`
	ChunkSize  int    `json:"chunkSize,omitempty"`
}

// FileChunkPayload 文件分片数据。
type FileChunkPayload struct {
	TransferID string `json:"transferId"`
	ChunkID    int    `json:"chunkId"`
	Offset     int64  `json:"offset"`
	Data       string `json:"data"` // Base64
}

// FileAckPayload 分片确认。
type FileAckPayload struct {
	TransferID string `json:"transferId"`
	ChunkID    int    `json:"chunkId"`
	OK         bool   `json:"ok"`
}

// FileCompletePayload 传输完成。
type FileCompletePayload struct {
	TransferID string `json:"transferId"`
	OK         bool   `json:"ok"`
	Hash       string `json:"hash,omitempty"`
}

// SettingsSyncPayload 设置同步。
type SettingsSyncPayload struct {
	Category string          `json:"category"` // screen / codec / network / control / security
	Screen   *ScreenSettings `json:"screen,omitempty"`
	Codec    *CodecSettings  `json:"codec,omitempty"`
	Control  *ControlSettings `json:"control,omitempty"`
}

// ScreenSettings 屏幕设置。
type ScreenSettings struct {
	Scale       float64 `json:"scale,omitempty"`
	FPS         int     `json:"fps,omitempty"`
	MaxBitrate  int     `json:"maxBitrate,omitempty"`
}

// CodecSettings 编解码设置。
type CodecSettings struct {
	Video string `json:"video,omitempty"`
	Audio string `json:"audio,omitempty"`
}

// ControlSettings 控制设置。
type ControlSettings struct {
	PrivacyScreen bool `json:"privacyScreen,omitempty"`
	ClipboardSync bool `json:"clipboardSync,omitempty"`
}

// HeartbeatPayload 心跳。
type HeartbeatPayload struct {
	ClientTs int64 `json:"clientTs"`
}

// HeartbeatAckPayload 心跳响应。
type HeartbeatAckPayload struct {
	ServerTs int64 `json:"serverTs"`
	RTT      int   `json:"rtt"`
}

// StatusPayload 状态上报。
type StatusPayload struct {
	RTT         int     `json:"rtt"`
	PacketLoss  float64 `json:"packetLoss,omitempty"`
	Bitrate     int     `json:"bitrate,omitempty"`
	FPS         int     `json:"fps,omitempty"`
	Codec       string  `json:"codec,omitempty"`
	Duration    int     `json:"duration,omitempty"`
}

// ErrorPayload 错误消息。
type ErrorPayload struct {
	Code    ErrorCode   `json:"code"`
	Message string      `json:"message,omitempty"`
	Details interface{} `json:"details,omitempty"`
}

// AuthPayload 连接鉴权。
type AuthPayload struct {
	DeviceID   string          `json:"deviceId"`
	Mode       ConnectionMode  `json:"mode"`
	DeviceCode string          `json:"deviceCode,omitempty"`
	Token      string          `json:"token,omitempty"`
	ClientTs   int64           `json:"clientTs,omitempty"`
}

// AuthAckPayload 鉴权响应。
type AuthAckPayload struct {
	OK         bool      `json:"ok"`
	Code       ErrorCode `json:"code,omitempty"`
	SessionID  string    `json:"sessionId,omitempty"`
}

// ScreenConfigPayload 屏幕配置。
type ScreenConfigPayload struct {
	Width   int     `json:"width"`
	Height  int     `json:"height"`
	Scale   float64 `json:"scale,omitempty"`
	Primary bool    `json:"primary,omitempty"`
}

// PrivacyScreenPayload 防窥屏指令。
type PrivacyScreenPayload struct {
	Enabled bool `json:"enabled"`
}

// ClipboardPayload 剪贴板同步。
type ClipboardPayload struct {
	Text string `json:"text"`
}

// === 信令通道消息（WebSocket） ===

// SignalingType WebSocket 信令消息类型。
type SignalingType string

const (
	SigConnect     SignalingType = "connect"
	SigConnectAck  SignalingType = "connect_ack"
	SigSDPOffer    SignalingType = "sdp_offer"
	SigSDPAnswer   SignalingType = "sdp_answer"
	SigICE         SignalingType = "ice_candidate"
	SigICEComplete SignalingType = "ice_complete"
	SigBye         SignalingType = "bye"
	SigPing        SignalingType = "ping"
	SigPong        SignalingType = "pong"
	SigError       SignalingType = "error"
)

// SignalEnvelope 信令消息外层信封。
type SignalEnvelope struct {
	Type      SignalingType   `json:"type"`
	Ts        int64           `json:"ts"`
	SessionID string          `json:"sessionId,omitempty"`
	From      string          `json:"from,omitempty"` // controller / device
	To        string          `json:"to,omitempty"`   // 设备编号
	Payload   json.RawMessage `json:"payload,omitempty"`
}

// ConnectPayload 连接请求。
type ConnectPayload struct {
	DeviceID   string         `json:"deviceId"`
	Mode       ConnectionMode `json:"mode"`
	Token      string         `json:"token,omitempty"`
	DeviceCode string         `json:"deviceCode,omitempty"`
}

// ConnectAckPayload 连接响应。
type ConnectAckPayload struct {
	OK             bool      `json:"ok"`
	Code           ErrorCode `json:"code,omitempty"`
	SessionID      string    `json:"sessionId,omitempty"`
	RequireConfirm bool      `json:"requireConfirm,omitempty"`
}

// SDPPayload SDP offer/answer。
type SDPPayload struct {
	SDP SDPBody `json:"sdp"`
}

// SDPBody SDP 主体。
type SDPBody struct {
	Type string `json:"type"` // offer / answer
	SDP  string `json:"sdp"`
}

// ICEPayload ICE candidate 转发。
type ICEPayload struct {
	Candidate ICEBody `json:"candidate"`
}

// ICEBody ICE 主体。
type ICEBody struct {
	Candidate        string `json:"candidate"`
	SDPMid           string `json:"sdpMid,omitempty"`
	SDPMLineIndex    int    `json:"sdpMLineIndex,omitempty"`
	UsernameFragment string `json:"usernameFragment,omitempty"`
}

// ByePayload 会话结束。
type ByePayload struct {
	Reason string `json:"reason,omitempty"`
}

// PingPayload WebSocket 心跳。
type PingPayload struct {
	ClientTs int64 `json:"clientTs,omitempty"`
}

// PongPayload WebSocket 心跳响应。
type PongPayload struct {
	ServerTs int64 `json:"serverTs"`
	RTT      int   `json:"rtt,omitempty"`
}

// === 序列化辅助 ===

// NewEnvelope 构造一个带时间戳的信封。
func NewEnvelope(t MessageType, seq int64, payload interface{}) (*Envelope, error) {
	raw, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	return &Envelope{
		Type:    t,
		Ts:      time.Now().UnixMilli(),
		Seq:     seq,
		Payload: raw,
	}, nil
}

// NewSignalEnvelope 构造信令信封。
func NewSignalEnvelope(t SignalingType, payload interface{}) (*SignalEnvelope, error) {
	raw, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	return &SignalEnvelope{
		Type:    t,
		Ts:      time.Now().UnixMilli(),
		Payload: raw,
	}, nil
}

// EncodeEnvelope 序列化信封为 JSON 字节。
func EncodeEnvelope(e interface{}) ([]byte, error) {
	return json.Marshal(e)
}

// DecodeEnvelope 从 JSON 字节反序列化信封，并返回原始 payload 供二次解析。
func DecodeEnvelope(data []byte) (*Envelope, error) {
	var env Envelope
	if err := json.Unmarshal(data, &env); err != nil {
		return nil, err
	}
	return &env, nil
}

// DecodeSignalEnvelope 反序列化信令信封。
func DecodeSignalEnvelope(data []byte) (*SignalEnvelope, error) {
	var env SignalEnvelope
	if err := json.Unmarshal(data, &env); err != nil {
		return nil, err
	}
	return &env, nil
}

// ParsePayload 将信封 payload 反序列化为目标结构。
func ParsePayload(env *Envelope, v interface{}) error {
	if len(env.Payload) == 0 {
		return nil
	}
	return json.Unmarshal(env.Payload, v)
}

// ParseSignalPayload 将信令信封 payload 反序列化为目标结构。
func ParseSignalPayload(env *SignalEnvelope, v interface{}) error {
	if len(env.Payload) == 0 {
		return nil
	}
	return json.Unmarshal(env.Payload, v)
}
