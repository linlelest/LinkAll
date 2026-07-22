package auth

import (
	"crypto/rand"
	"database/sql"
	"encoding/csv"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"
)

// InviteManager 邀请码管理器：生成、吊销、批量导出。
// 邀请码规则：单次使用 + 时效；生成时记录创建者、过期时间。
type InviteManager struct {
	db *sql.DB
}

// NewInviteManager 创建邀请码管理器。
func NewInviteManager(db *sql.DB) *InviteManager {
	return &InviteManager{db: db}
}

// InviteCode 邀请码记录。
type InviteCode struct {
	ID         int64  `json:"id"`
	Code       string `json:"code"`
	CreatedBy  int64  `json:"createdBy"`
	UsedBy     *int64 `json:"usedBy,omitempty"`
	ExpiresAt  int64  `json:"expiresAt"`
	UsedAt     *int64 `json:"usedAt,omitempty"`
	Revoked    bool   `json:"revoked"`
	Used       bool   `json:"used"`
	CreatedAt  int64  `json:"createdAt"`
	Note       string `json:"note,omitempty"`
}

// GenerateOptions 生成邀请码时的可选项。
type GenerateOptions struct {
	Count      int           // 生成数量（默认 1）
	TTL        time.Duration // 有效期（默认 7 天）
	CreatedBy  int64         // 创建者 user_id
	Note       string        // 备注
	CodeLength int           // 邀请码字符长度（默认 10）
}

// GenerateResult 单次生成结果。
type GenerateResult struct {
	Codes []string `json:"codes"` // 明文邀请码（仅此一次可见）
}

// Generate 生成邀请码。返回明文邀请码切片（落库时仅存哈希，但为便于分发也存明文）。
// 注意：迁移脚本中 invite_codes.code 列存的是明文以便管理员分发，
// code_hash 列存 SHA-256 哈希以便校验时避免明文比对。
func (m *InviteManager) Generate(opts GenerateOptions) (*GenerateResult, error) {
	if opts.Count <= 0 {
		opts.Count = 1
	}
	if opts.Count > 1000 {
		return nil, errors.New("单次生成数量不能超过 1000")
	}
	if opts.TTL <= 0 {
		opts.TTL = 7 * 24 * time.Hour // 默认 7 天
	}
	if opts.CodeLength < 8 {
		opts.CodeLength = 10
	}
	if opts.CreatedBy <= 0 {
		return nil, errors.New("createdBy 必填")
	}

	now := time.Now()
	expiresAt := now.Add(opts.TTL).Unix()

	codes := make([]string, 0, opts.Count)
	tx, err := m.db.Begin()
	if err != nil {
		return nil, fmt.Errorf("开启事务失败: %w", err)
	}
	defer tx.Rollback()

	for i := 0; i < opts.Count; i++ {
		code, err := generateInviteCode(opts.CodeLength)
		if err != nil {
			return nil, fmt.Errorf("生成邀请码失败: %w", err)
		}
		hash := sha256Hex(code)
		_, err = tx.Exec(
			`INSERT INTO invite_codes (code, code_hash, created_by, expires_at, note)
			 VALUES (?, ?, ?, ?, ?)`,
			code, hash, opts.CreatedBy, expiresAt, opts.Note,
		)
		if err != nil {
			return nil, fmt.Errorf("插入邀请码失败: %w", err)
		}
		codes = append(codes, code)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("提交事务失败: %w", err)
	}

	return &GenerateResult{Codes: codes}, nil
}

// Revoke 吊销指定邀请码（已使用的不能吊销）。
func (m *InviteManager) Revoke(code string) error {
	res, err := m.db.Exec(
		`UPDATE invite_codes SET revoked = 1 WHERE code = ? AND used = 0`,
		code,
	)
	if err != nil {
		return fmt.Errorf("吊销邀请码失败: %w", err)
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return errors.New("邀请码不存在或已被使用，无法吊销")
	}
	return nil
}

// RevokeByID 按 ID 吊销。
func (m *InviteManager) RevokeByID(id int64) error {
	res, err := m.db.Exec(
		`UPDATE invite_codes SET revoked = 1 WHERE id = ? AND used = 0`,
		id,
	)
	if err != nil {
		return fmt.Errorf("吊销邀请码失败: %w", err)
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return errors.New("邀请码不存在或已被使用，无法吊销")
	}
	return nil
}

// Consume 消费邀请码（注册时调用）。返回创建者 user_id 与邀请码 id。
// 在事务中调用更安全（由调用方包裹）。
func (m *InviteManager) Consume(code string, usedBy int64) (int64, int64, error) {
	var (
		id        int64
		createdBy int64
		expiresAt int64
		used      int
		revoked   int
	)
	err := m.db.QueryRow(
		`SELECT id, created_by, expires_at, used, revoked FROM invite_codes WHERE code = ?`,
		code,
	).Scan(&id, &createdBy, &expiresAt, &used, &revoked)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return 0, 0, errors.New("邀请码无效")
		}
		return 0, 0, err
	}
	if revoked == 1 {
		return 0, 0, errors.New("邀请码已被吊销")
	}
	if used == 1 {
		return 0, 0, errors.New("邀请码已被使用")
	}
	if time.Now().Unix() > expiresAt {
		return 0, 0, errors.New("邀请码已过期")
	}

	_, err = m.db.Exec(
		`UPDATE invite_codes SET used = 1, used_by = ?, used_at = ? WHERE id = ? AND used = 0`,
		usedBy, time.Now().Unix(), id,
	)
	if err != nil {
		return 0, 0, fmt.Errorf("标记邀请码已使用失败: %w", err)
	}
	return createdBy, id, nil
}

// List 列出邀请码（支持分页）。
func (m *InviteManager) List(limit, offset int) ([]InviteCode, int, error) {
	if limit <= 0 {
		limit = 50
	}
	var total int
	if err := m.db.QueryRow(`SELECT COUNT(*) FROM invite_codes`).Scan(&total); err != nil {
		return nil, 0, err
	}

	rows, err := m.db.Query(
		`SELECT id, code, created_by, COALESCE(used_by,0), expires_at, COALESCE(used_at,0),
		        revoked, used, created_at, COALESCE(note,'')
		 FROM invite_codes ORDER BY id DESC LIMIT ? OFFSET ?`,
		limit, offset,
	)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var out []InviteCode
	for rows.Next() {
		var ic InviteCode
		var usedBy, usedAt int64
		if err := rows.Scan(
			&ic.ID, &ic.Code, &ic.CreatedBy, &usedBy, &ic.ExpiresAt, &usedAt,
			&ic.Revoked, &ic.Used, &ic.CreatedAt, &ic.Note,
		); err != nil {
			return nil, 0, err
		}
		if ic.Used {
			ub := usedBy
			ic.UsedBy = &ub
		}
		if usedAt != 0 {
			ua := usedAt
			ic.UsedAt = &ua
		}
		out = append(out, ic)
	}
	return out, total, rows.Err()
}

// Export 批量导出邀请码为 CSV 字节切片。
// 仅导出有效的（未使用、未吊销、未过期）邀请码，用于管理员分发。
func (m *InviteManager) Export() ([]byte, error) {
	rows, err := m.db.Query(
		`SELECT id, code, created_by, expires_at, created_at, COALESCE(note,'')
		 FROM invite_codes
		 WHERE used = 0 AND revoked = 0 AND expires_at > ?
		 ORDER BY id ASC`,
		time.Now().Unix(),
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var b strings.Builder
	w := csv.NewWriter(&b)
	// BOM 头让 Excel 正确识别 UTF-8
	b.WriteString("\xEF\xBB\xBF")
	if err := w.Write([]string{"id", "code", "created_by", "expires_at_iso", "created_at_iso", "note"}); err != nil {
		return nil, err
	}
	for rows.Next() {
		var (
			id, createdBy, expiresAt, createdAt int64
			code, note                          string
		)
		if err := rows.Scan(&id, &code, &createdBy, &expiresAt, &createdAt, &note); err != nil {
			return nil, err
		}
		if err := w.Write([]string{
			fmt.Sprintf("%d", id),
			code,
			fmt.Sprintf("%d", createdBy),
			time.Unix(expiresAt, 0).Format(time.RFC3339),
			time.Unix(createdAt, 0).Format(time.RFC3339),
			note,
		}); err != nil {
			return nil, err
		}
	}
	w.Flush()
	if err := w.Error(); err != nil {
		return nil, err
	}
	return []byte(b.String()), nil
}

// --- 辅助 ---

// generateInviteCode 生成随机邀请码（大写字母+数字，去除易混淆字符）。
const inviteAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 去除 I/O/0/1

func generateInviteCode(length int) (string, error) {
	buf := make([]byte, length)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	out := make([]byte, length)
	for i, b := range buf {
		out[i] = inviteAlphabet[int(b)%len(inviteAlphabet)]
	}
	return string(out), nil
}

// sha256Hex 计算 SHA-256 哈希并返回 Hex 编码（用于邀请码与文件哈希）。
func sha256Hex(s string) string {
	// 标准库实现以保持零依赖
	h := newSHA256()
	h.Write([]byte(s))
	return hex.EncodeToString(h.Sum(nil))
}
