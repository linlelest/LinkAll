// Package auth 提供密码哈希、JWT 签发校验、邀请码管理。
package auth

import (
	"crypto/subtle"
	"fmt"

	"github.com/alexedwards/argon2id"
)

// PasswordHasher 封装 Argon2id 哈希与校验。
// 默认参数采用 OWASP 推荐的第二档（memory=64MB, iterations=3, parallelism=2）。
type PasswordHasher struct {
	params *argon2id.Params
}

// NewPasswordHasher 使用默认 Argon2id 参数创建哈希器。
// 可传入自定义参数；为 nil 时使用 OWASP 推荐参数。
func NewPasswordHasher(p *argon2id.Params) *PasswordHasher {
	if p == nil {
		p = &argon2id.Params{
			Memory:      64 * 1024, // 64MB
			Iterations:  3,
			Parallelism: 2,
			SaltLength:  16,
			KeyLength:   32,
		}
	}
	return &PasswordHasher{params: p}
}

// Hash 对明文密码进行 Argon2id 哈希，返回可直接入库的编码字符串。
func (h *PasswordHasher) Hash(plain string) (string, error) {
	if plain == "" {
		return "", fmt.Errorf("密码不能为空")
	}
	hash, err := argon2id.CreateHash(plain, *h.params)
	if err != nil {
		return "", fmt.Errorf("Argon2id 哈希失败: %w", err)
	}
	return hash, nil
}

// Verify 校验明文密码与哈希是否匹配。
// 使用 subtle.ConstantTimeCompare 避免 timing 攻击（argon2id 内部已使用）。
func (h *PasswordHasher) Verify(plain, hash string) (bool, error) {
	if hash == "" {
		return false, fmt.Errorf("哈希为空")
	}
	match, err := argon2id.ComparePasswordAndHash(plain, hash)
	if err != nil {
		return false, fmt.Errorf("Argon2id 校验失败: %w", err)
	}
	_ = subtle.ConstantTimeCompare // 保留引用以表明意图
	return match, nil
}

// MustHash 哈希失败时 panic，仅在初始化场景使用。
func (h *PasswordHasher) MustHash(plain string) string {
	hash, err := h.Hash(plain)
	if err != nil {
		panic(err)
	}
	return hash
}
