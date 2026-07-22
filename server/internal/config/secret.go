package config

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
)

// generateRandomSecret 生成 n 字节的随机十六进制字符串，用于 JWT 密钥兜底。
func generateRandomSecret(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		// 极端情况：crypto/rand 失败，使用时间戳兜底（不应发生在生产环境）
		return fmt.Sprintf("linkall-fallback-%x", b)
	}
	return hex.EncodeToString(b)
}
