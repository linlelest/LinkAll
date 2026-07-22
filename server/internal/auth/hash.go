package auth

import (
	"crypto/sha256"
	"hash"
)

// newSHA256 返回一个 SHA-256 哈希器（封装以便测试替换）。
func newSHA256() hash.Hash {
	return sha256.New()
}
