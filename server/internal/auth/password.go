// Package auth 提供密码哈希、JWT 签发校验、邀请码管理。
package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"strings"

	"golang.org/x/crypto/argon2"
)

// Argon2idParams 封装 Argon2id 哈希参数。
// 默认参数采用 OWASP 推荐的第二档（memory=64MB, iterations=3, parallelism=2）。
type Argon2idParams struct {
	Memory      uint32 // 内存（KB），64MB = 65536
	Iterations  uint32 // 迭代次数
	Parallelism uint8  // 并行度
	SaltLength  uint32 // 盐长度（字节）
	KeyLength   uint32 // 输出密钥长度（字节）
}

// DefaultParams 返回 OWASP 推荐的 Argon2id 参数。
func DefaultParams() *Argon2idParams {
	return &Argon2idParams{
		Memory:      64 * 1024, // 64MB
		Iterations:  3,
		Parallelism: 2,
		SaltLength:  16,
		KeyLength:   32,
	}
}

// PasswordHasher 封装 Argon2id 哈希与校验。
type PasswordHasher struct {
	params *Argon2idParams
}

// NewPasswordHasher 使用默认 Argon2id 参数创建哈希器。
// 可传入自定义参数；为 nil 时使用 OWASP 推荐参数。
func NewPasswordHasher(p *Argon2idParams) *PasswordHasher {
	if p == nil {
		p = DefaultParams()
	}
	return &PasswordHasher{params: p}
}

// Hash 对明文密码进行 Argon2id 哈希，返回标准编码字符串。
// 编码格式：$argon2id$v=19$m=<memory>,t=<iterations>,p=<parallelism>$<base64-salt>$<base64-hash>
func (h *PasswordHasher) Hash(plain string) (string, error) {
	if plain == "" {
		return "", fmt.Errorf("密码不能为空")
	}

	salt := make([]byte, h.params.SaltLength)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("生成盐失败: %w", err)
	}

	key := argon2.IDKey(
		[]byte(plain),
		salt,
		h.params.Iterations,
		h.params.Memory,
		h.params.Parallelism,
		h.params.KeyLength,
	)

	b64Salt := base64.RawStdEncoding.EncodeToString(salt)
	b64Key := base64.RawStdEncoding.EncodeToString(key)

	return fmt.Sprintf(
		"$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
		h.params.Memory,
		h.params.Iterations,
		h.params.Parallelism,
		b64Salt,
		b64Key,
	), nil
}

// Verify 校验明文密码与哈希是否匹配。
// 使用 subtle.ConstantTimeCompare 避免 timing 攻击。
func (h *PasswordHasher) Verify(plain, encodedHash string) (bool, error) {
	if encodedHash == "" {
		return false, fmt.Errorf("哈希为空")
	}

	// 解析编码哈希：$argon2id$v=19$m=...,t=...,p=...$<salt>$<key>
	parts := strings.Split(encodedHash, "$")
	if len(parts) != 6 {
		return false, fmt.Errorf("哈希格式无效")
	}
	if parts[1] != "argon2id" {
		return false, fmt.Errorf("非 Argon2id 哈希")
	}

	// 解析版本
	if parts[2] != "v=19" {
		return false, fmt.Errorf("不支持的 Argon2id 版本: %s", parts[2])
	}

	// 解析参数 m=...,t=...,p=...
	var memory, iterations uint32
	var parallelism uint8
	for _, p := range strings.Split(parts[3], ",") {
		switch {
		case strings.HasPrefix(p, "m="):
			if _, err := fmt.Sscanf(p, "m=%d", &memory); err != nil {
				return false, fmt.Errorf("解析内存参数失败: %w", err)
			}
		case strings.HasPrefix(p, "t="):
			if _, err := fmt.Sscanf(p, "t=%d", &iterations); err != nil {
				return false, fmt.Errorf("解析迭代参数失败: %w", err)
			}
		case strings.HasPrefix(p, "p="):
			var pVal uint32
			if _, err := fmt.Sscanf(p, "p=%d", &pVal); err != nil {
				return false, fmt.Errorf("解析并行度参数失败: %w", err)
			}
			parallelism = uint8(pVal)
		}
	}

	// 解码盐和密钥
	salt, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil {
		return false, fmt.Errorf("解码盐失败: %w", err)
	}
	key, err := base64.RawStdEncoding.DecodeString(parts[5])
	if err != nil {
		return false, fmt.Errorf("解码密钥失败: %w", err)
	}

	// 使用哈希中的参数重新计算
	otherKey := argon2.IDKey(
		[]byte(plain),
		salt,
		iterations,
		memory,
		parallelism,
		uint32(len(key)),
	)

	// 常量时间比较
	return subtle.ConstantTimeCompare(key, otherKey) == 1, nil
}

// MustHash 哈希失败时 panic，仅在初始化场景使用。
func (h *PasswordHasher) MustHash(plain string) string {
	hash, err := h.Hash(plain)
	if err != nil {
		panic(err)
	}
	return hash
}
