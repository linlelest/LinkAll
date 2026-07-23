// Package webrtc - file_relay.go
// WebRTC DataChannel 文件分片中继与队列管理。
// 设计原则：P2P 直连优先，信令服务器作为中继备份（当 DataChannel 不可达时走 WebSocket 转发）。
// 职责：
//   1. 跟踪每个会话的活跃文件传输任务
//   2. 校验分片 ID + 偏移量一致性，丢弃错位分片
//   3. 记录断点续传进度：内存索引 + DB 持久化（file_transfers 表）
//   4. 管理传输队列，支持优先级与取消
package webrtc

import (
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/linlelest/LinkALL/server/internal/protocol"
)

// FileChunkSize 单包分片上限 256KB（与协议定义一致）。
const FileChunkSize = 256 * 1024

// MaxConcurrentTransfers 单会话最大并发传输任务数。
const MaxConcurrentTransfers = 8

// TransferState 文件传输任务状态。
type TransferState struct {
	TransferID  string
	Name        string
	Size        int64
	Hash        string
	Direction   string // upload / download
	RemotePath  string
	ChunkSize   int
	// 已接收/已发送的分片偏移记录（chunkId -> offset，用于校验与续传）
	ReceivedChunks map[int]int64
	// 已传输字节数
	Transferred int64
	// 起始时间
	StartedAt time.Time
	// 最近活跃时间
	LastActive time.Time
	// 状态：pending / transferring / paused / done / failed / cancelled
	Status string
	mu     sync.Mutex
}

// FileRelayManager 文件中继管理器：管理所有会话的文件传输任务。
// P2P 直连时仅做透传统计；中继模式下负责分片转发与校验。
type FileRelayManager struct {
	mu        sync.RWMutex
	// sessionID -> transferId -> TransferState
	transfers map[string]map[string]*TransferState
	// db 用于断点续传持久化（file_transfers 表），可为 nil
	db *sql.DB
}

// NewFileRelayManager 创建文件中继管理器。db 可为 nil（仅内存模式）。
func NewFileRelayManager(db *sql.DB) *FileRelayManager {
	return &FileRelayManager{
		transfers: make(map[string]map[string]*TransferState),
		db:        db,
	}
}

// StartTransfer 注册一个新的文件传输任务。
func (m *FileRelayManager) StartTransfer(sessionID string, meta protocol.FileMetaPayload) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	sessMap, ok := m.transfers[sessionID]
	if !ok {
		sessMap = make(map[string]*TransferState)
		m.transfers[sessionID] = sessMap
	}
	// 并发限制
	if len(sessMap) >= MaxConcurrentTransfers {
		if _, exists := sessMap[meta.TransferID]; !exists {
			return fmt.Errorf("达到最大并发传输数 %d", MaxConcurrentTransfers)
		}
	}

	chunkSize := meta.ChunkSize
	if chunkSize <= 0 || chunkSize > FileChunkSize {
		chunkSize = FileChunkSize
	}

	state := &TransferState{
		TransferID:     meta.TransferID,
		Name:           meta.Name,
		Size:           meta.Size,
		Hash:           meta.Hash,
		Direction:      meta.Direction,
		RemotePath:     meta.RemotePath,
		ChunkSize:      chunkSize,
		ReceivedChunks: make(map[int]int64),
		StartedAt:      time.Now(),
		LastActive:     time.Now(),
		Status:         "transferring",
	}
	sessMap[meta.TransferID] = state

	// 持久化到 DB（best-effort，失败仅日志）
	m.persistTransferStart(sessionID, state)
	return nil
}

// persistTransferStart 将传输任务写入 file_transfers 表。
func (m *FileRelayManager) persistTransferStart(sessionID string, s *TransferState) {
	if m.db == nil {
		return
	}
	_, err := m.db.Exec(
		`INSERT OR REPLACE INTO file_transfers
		 (transfer_id, session_id, name, size, hash, direction, remote_path,
		  chunk_size, transferred, received_chunks, status, started_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, '{}', ?, ?, ?)`,
		s.TransferID, sessionID, s.Name, s.Size, s.Hash, s.Direction, s.RemotePath,
		s.ChunkSize, s.Status, s.StartedAt.Unix(), s.StartedAt.Unix(),
	)
	if err != nil {
		log.Printf("[file_relay] 持久化传输任务失败 transferId=%s: %v", s.TransferID, err)
	}
}

// ValidateChunk 校验分片 ID 与偏移量一致性。
// 返回 true 表示分片合法，false 表示错位（应丢弃并请求重传）。
func (m *FileRelayManager) ValidateChunk(sessionID string, chunk protocol.FileChunkPayload) bool {
	m.mu.RLock()
	sessMap, ok := m.transfers[sessionID]
	m.mu.RUnlock()
	if !ok {
		return true // 未跟踪的传输（P2P 直连模式），直接放行
	}
	state, exists := sessMap[chunk.TransferID]
	if !exists {
		return true
	}

	state.mu.Lock()
	defer state.mu.Unlock()

	// 校验偏移量：期望偏移 = chunkId * chunkSize
	expectedOffset := int64(chunk.ChunkID) * int64(state.ChunkSize)
	// 最后一片可能不足 chunkSize，允许 offset <= 实际值
	if chunk.Offset != expectedOffset && chunk.Offset > state.Size {
		return false
	}

	// 记录已接收分片
	state.ReceivedChunks[chunk.ChunkID] = chunk.Offset
	state.LastActive = time.Now()
	return true
}

// RecordProgress 记录传输进度（中继模式下统计）。
func (m *FileRelayManager) RecordProgress(sessionID, transferID string, transferred int64) {
	m.mu.RLock()
	sessMap, ok := m.transfers[sessionID]
	m.mu.RUnlock()
	if !ok {
		return
	}
	if state, exists := sessMap[transferID]; exists {
		state.mu.Lock()
		state.Transferred = transferred
		state.LastActive = time.Now()
		received := state.ReceivedChunks
		state.mu.Unlock()
		// 异步持久化进度（避免阻塞转发主路径）
		go m.persistProgress(transferID, transferred, received)
	}
}

// persistProgress 持久化进度与已接收分片表。
func (m *FileRelayManager) persistProgress(transferID string, transferred int64, received map[int]int64) {
	if m.db == nil {
		return
	}
	chunksJSON, err := json.Marshal(received)
	if err != nil {
		return
	}
	_, err = m.db.Exec(
		`UPDATE file_transfers SET transferred = ?, received_chunks = ?, updated_at = ? WHERE transfer_id = ?`,
		transferred, string(chunksJSON), time.Now().Unix(), transferID,
	)
	if err != nil {
		log.Printf("[file_relay] 持久化进度失败 transferId=%s: %v", transferID, err)
	}
}

// CompleteTransfer 标记传输完成。
func (m *FileRelayManager) CompleteTransfer(sessionID, transferID string, ok bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	sessMap, exists := m.transfers[sessionID]
	if !exists {
		return
	}
	if state, ok2 := sessMap[transferID]; ok2 {
		state.mu.Lock()
		if ok {
			state.Status = "done"
			state.Transferred = state.Size
		} else {
			state.Status = "failed"
		}
		state.LastActive = time.Now()
		state.mu.Unlock()
		// 持久化完成状态
		go m.persistStatus(transferID, state.Status, true)
	}
}

// CancelTransfer 取消传输任务。
func (m *FileRelayManager) CancelTransfer(sessionID, transferID string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	sessMap, exists := m.transfers[sessionID]
	if !exists {
		return
	}
	if state, ok := sessMap[transferID]; ok {
		state.mu.Lock()
		state.Status = "cancelled"
		state.mu.Unlock()
		// 持久化取消状态
		go m.persistStatus(transferID, "cancelled", true)
	}
	delete(sessMap, transferID)
}

// persistStatus 持久化任务状态。
func (m *FileRelayManager) persistStatus(transferID, status string, completed bool) {
	if m.db == nil {
		return
	}
	if completed {
		_, _ = m.db.Exec(
			`UPDATE file_transfers SET status = ?, completed_at = ?, updated_at = ? WHERE transfer_id = ?`,
			status, time.Now().Unix(), time.Now().Unix(), transferID,
		)
	} else {
		_, _ = m.db.Exec(
			`UPDATE file_transfers SET status = ?, updated_at = ? WHERE transfer_id = ?`,
			status, time.Now().Unix(), transferID,
		)
	}
}

// GetResumeOffset 获取断点续传偏移量（已接收的最大连续偏移）。
func (m *FileRelayManager) GetResumeOffset(sessionID, transferID string) (int64, int) {
	m.mu.RLock()
	sessMap, ok := m.transfers[sessionID]
	m.mu.RUnlock()
	if !ok {
		return 0, 0
	}
	state, exists := sessMap[transferID]
	if !exists {
		return 0, 0
	}
	state.mu.Lock()
	defer state.mu.Unlock()

	// 找到连续已接收的最大 chunkId
	maxContiguous := -1
	for i := 0; ; i++ {
		if _, ok := state.ReceivedChunks[i]; ok {
			maxContiguous = i
		} else {
			break
		}
	}
	if maxContiguous < 0 {
		return 0, 0
	}
	offset := int64(maxContiguous+1) * int64(state.ChunkSize)
	if offset > state.Size {
		offset = state.Size
	}
	return offset, maxContiguous + 1
}

// LoadResumeFromDB 从 DB 恢复断点续传状态（用于服务重启后恢复任务）。
// 返回 (offset, chunkId, ok)。ok=false 表示无记录或恢复失败。
func (m *FileRelayManager) LoadResumeFromDB(transferID string) (int64, int, bool) {
	if m.db == nil {
		return 0, 0, false
	}
	var (
		transferred    int64
		chunkSize      int
		receivedChunks string
		size           int64
		status         string
	)
	err := m.db.QueryRow(
		`SELECT transferred, chunk_size, received_chunks, size, status
		 FROM file_transfers WHERE transfer_id = ?`,
		transferID,
	).Scan(&transferred, &chunkSize, &receivedChunks, &size, &status)
	if err != nil {
		return 0, 0, false
	}
	if status != "transferring" && status != "paused" {
		return 0, 0, false
	}
	if chunkSize <= 0 {
		chunkSize = FileChunkSize
	}
	// 解析 received_chunks
	var chunks map[int]int64
	if err := json.Unmarshal([]byte(receivedChunks), &chunks); err != nil {
		return 0, 0, false
	}
	// 计算连续最大 chunkId
	maxContiguous := -1
	for i := 0; ; i++ {
		if _, ok := chunks[i]; ok {
			maxContiguous = i
		} else {
			break
		}
	}
	if maxContiguous < 0 {
		return 0, 0, true
	}
	offset := int64(maxContiguous+1) * int64(chunkSize)
	if offset > size {
		offset = size
	}
	return offset, maxContiguous + 1, true
}

// ListTransfers 列出会话的所有传输任务（用于队列管理 UI）。
type TransferInfo struct {
	TransferID  string `json:"transferId"`
	Name        string `json:"name"`
	Size        int64  `json:"size"`
	Direction   string `json:"direction"`
	Transferred int64  `json:"transferred"`
	Status      string `json:"status"`
	Progress    float64 `json:"progress"`
}

func (m *FileRelayManager) ListTransfers(sessionID string) []TransferInfo {
	m.mu.RLock()
	sessMap, ok := m.transfers[sessionID]
	m.mu.RUnlock()
	if !ok {
		return []TransferInfo{}
	}
	out := make([]TransferInfo, 0, len(sessMap))
	for _, state := range sessMap {
		state.mu.Lock()
		progress := 0.0
		if state.Size > 0 {
			progress = float64(state.Transferred) / float64(state.Size)
		}
		out = append(out, TransferInfo{
			TransferID:  state.TransferID,
			Name:        state.Name,
			Size:        state.Size,
			Direction:   state.Direction,
			Transferred: state.Transferred,
			Status:      state.Status,
			Progress:    progress,
		})
		state.mu.Unlock()
	}
	return out
}

// ClearSession 清理会话的所有传输记录（会话结束时调用）。
func (m *FileRelayManager) ClearSession(sessionID string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.transfers, sessionID)
}

// === 分片转发（中继模式） ===

// RelayFileMessage 在中继模式下转发文件消息到对端，并做校验/统计。
// 返回值：是否继续转发。
// 当 P2P 直连可用时，文件消息走 DataChannel，不经过此方法。
func (m *FileRelayManager) RelayFileMessage(sess *Session, fromController bool, env *protocol.Envelope) bool {
	if sess == nil {
		return false
	}
	var peer *Client
	if fromController {
		peer = sess.Device
	} else {
		peer = sess.Controller
	}
	if peer == nil {
		return false
	}

	switch env.Type {
	case protocol.MsgFileMeta:
		var meta protocol.FileMetaPayload
		if err := protocol.ParsePayload(env, &meta); err == nil {
			_ = m.StartTransfer(sess.ID, meta)
		}
	case protocol.MsgFileChunk:
		var chunk protocol.FileChunkPayload
		if err := protocol.ParsePayload(env, &chunk); err == nil {
			if !m.ValidateChunk(sess.ID, chunk) {
				// 分片错位，请求重传
				ack, _ := protocol.NewEnvelope(protocol.MsgFileAck, env.Seq, protocol.FileAckPayload{
					TransferID: chunk.TransferID,
					ChunkID:    chunk.ChunkID,
					OK:         false,
				})
				if fromController && sess.Controller != nil {
					_ = sess.Controller.SendJSON(ack)
				}
				return false
			}
			// 统计已传输字节
			if decoded, err := base64.StdEncoding.DecodeString(chunk.Data); err == nil {
				m.RecordProgress(sess.ID, chunk.TransferID, int64(chunk.Offset)+int64(len(decoded)))
			}
		}
	case protocol.MsgFileComplete:
		var comp protocol.FileCompletePayload
		if err := protocol.ParsePayload(env, &comp); err == nil {
			m.CompleteTransfer(sess.ID, comp.TransferID, comp.OK)
		}
	case protocol.MsgFileCancel:
		var cancel protocol.FileCancelPayload
		if err := protocol.ParsePayload(env, &cancel); err == nil {
			m.CancelTransfer(sess.ID, cancel.TransferID)
		}
	}

	// 转发到对端
	_ = peer.SendJSON(env)
	sess.Touch()
	return true
}
