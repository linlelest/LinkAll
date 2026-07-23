package handlers

// 安全策略中间件：
//   1. ReplayProtection: HTTP API 时间戳防重放（X-Ts 头 + 60 秒窗口）
//      防止请求被截获后重放，适用于敏感操作（连接确认、配对等）
//   2. SecurityPolicyCheck: 在信令握手时校验全局安全策略
//      （匿名连接总开关、设备码总开关、远程控制总开关）
//   3. EnsureSecureFilePermissions: 确保本地配置文件 0600 权限
//   4. 设备码非对称加密传输：服务端启动生成 RSA 密钥对，
//      客户端用公钥加密设备码后提交，服务端解密后再做 Argon2id 校验

import (
	"crypto"
	crand "crypto/rand"
	"crypto/ed25519"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"time"

	"github.com/gofiber/fiber/v2"
)

// 防重放窗口：60 秒
const replayWindowSeconds = 60

// ReplayProtection 时间戳防重放中间件。
// 要求请求头 X-Ts 为 Unix 秒时间戳，且与服务端时间差不超过 60 秒。
// 适用于敏感操作（匿名连接确认、设备配对等）。
func (d *Deps) ReplayProtection(c *fiber.Ctx) error {
	tsStr := c.Get("X-Ts")
	if tsStr == "" {
		// 未带时间戳的请求放行（兼容旧客户端），但记录
		return c.Next()
	}
	ts, err := strconv.ParseInt(tsStr, 10, 64)
	if err != nil {
		return failBadRequest(c, "X-Ts 格式错误")
	}
	now := time.Now().Unix()
	diff := now - ts
	if diff > replayWindowSeconds || diff < -replayWindowSeconds {
		return fail(c, fiber.StatusUnauthorized, "ERR_REPLAY_DETECTED", "请求时间戳超出窗口")
	}
	return c.Next()
}

// SecurityPolicyCheck 在信令握手时校验全局安全策略。
// mode: anonymous / same_account / device_code
// 返回 (allowed, reason)。
func (d *Deps) SecurityPolicyCheck(mode string) (bool, string) {
	var (
		allowAnon    int
		allowDevCode int
		allowRemote  int
	)
	_ = d.DB.QueryRow(
		`SELECT allow_anonymous, allow_device_code, allow_remote_control FROM security_settings WHERE id = 1`,
	).Scan(&allowAnon, &allowDevCode, &allowRemote)

	// 总开关：关闭则拒绝所有远程控制
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

// === 设备码非对称加密传输 ===

// rsaKeyHolder 持有 RSA 密钥对（启动时加载或生成）。
var (
	rsaKeyOnce sync.Once
	rsaPriv    *rsa.PrivateKey
	rsaPubPEM  string
)

// EnsureRSAKeyPair 确保 RSA 密钥对存在，持久化到 dataDir/keys/。
// 启动时调用一次。文件权限 0600。
func EnsureRSAKeyPair(dataDir string) error {
	var initErr error
	rsaKeyOnce.Do(func() {
		keysDir := filepath.Join(dataDir, "keys")
		if err := os.MkdirAll(keysDir, 0o700); err != nil {
			initErr = err
			return
		}
		privPath := filepath.Join(keysDir, "rsa_private.pem")
		pubPath := filepath.Join(keysDir, "rsa_public.pem")

		// 尝试加载已有密钥
		privData, err := os.ReadFile(privPath)
		if err == nil {
			block, _ := pem.Decode(privData)
			if block != nil {
				key, err := x509.ParsePKCS1PrivateKey(block.Bytes)
				if err == nil {
					rsaPriv = key
					pubData, _ := os.ReadFile(pubPath)
					rsaPubPEM = string(pubData)
					return
				}
			}
		}

		// 生成新密钥对（2048 位）
		key, err := rsa.GenerateKey(crand.Reader, 2048)
		if err != nil {
			// rand.Reader 失败时的兜底（不应发生）
			initErr = err
			return
		}
		rsaPriv = key

		// 序列化私钥
		privBytes := x509.MarshalPKCS1PrivateKey(key)
		privPEM := pem.EncodeToMemory(&pem.Block{
			Type:  "RSA PRIVATE KEY",
			Bytes: privBytes,
		})
		if err := os.WriteFile(privPath, privPEM, 0o600); err != nil {
			initErr = err
			return
		}

		// 序列化公钥
		pubBytes, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
		if err != nil {
			initErr = err
			return
		}
		pubPEM := pem.EncodeToMemory(&pem.Block{
			Type:  "PUBLIC KEY",
			Bytes: pubBytes,
		})
		if err := os.WriteFile(pubPath, pubPEM, 0o600); err != nil {
			initErr = err
			return
		}
		rsaPubPEM = string(pubPEM)
	})
	return initErr
}

// GetRSAPublicKey 返回 RSA 公钥 PEM（供客户端加密设备码）。
// GET /api/security/public-key
func (d *Deps) GetRSAPublicKey(c *fiber.Ctx) error {
	if rsaPubPEM == "" {
		return failInternal(c, "RSA 公钥未初始化")
	}
	return ok(c, fiber.Map{"publicKey": rsaPubPEM})
}

// DecryptDeviceCode 用 RSA 私钥解密客户端加密的设备码。
// 输入为 Base64 编码的 RSA-OAEP 加密结果。
// 若解密失败返回空字符串（调用方应回退到明文处理）。
func DecryptDeviceCode(encryptedB64 string) string {
	if rsaPriv == nil || encryptedB64 == "" {
		return ""
	}
	cipher, err := base64.StdEncoding.DecodeString(encryptedB64)
	if err != nil {
		return ""
	}
	plain, err := rsa.DecryptOAEP(sha256.New(), nil, rsaPriv, cipher, []byte("linkall-device-code"))
	if err != nil {
		return ""
	}
	return string(plain)
}

// EncryptWithRSAPublic 使用服务端 RSA 公钥加密（供测试用）。
func EncryptWithRSAPublic(plain []byte) (string, error) {
	if rsaPriv == nil {
		return "", errors.New("RSA 密钥未初始化")
	}
	cipher, err := rsa.EncryptOAEP(sha256.New(), nil, &rsaPriv.PublicKey, plain, []byte("linkall-device-code"))
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(cipher), nil
}

// EnsureSecureFilePermissions 确保配置文件与密钥文件权限正确。
// - .env: 0600
// - data/keys/: 0700
// - data/keys/*.pem: 0600
// 在服务启动时调用。
func EnsureSecureFilePermissions(envPath, dataDir string) {
	// .env 文件 0600
	if _, err := os.Stat(envPath); err == nil {
		_ = os.Chmod(envPath, 0o600)
	}
	// keys 目录 0700
	keysDir := filepath.Join(dataDir, "keys")
	if info, err := os.Stat(keysDir); err == nil && info.IsDir() {
		_ = os.Chmod(keysDir, 0o700)
	}
	// 密钥文件 0600
	entries, err := os.ReadDir(keysDir)
	if err == nil {
		for _, e := range entries {
			if !e.IsDir() && filepath.Ext(e.Name()) == ".pem" {
				_ = os.Chmod(filepath.Join(keysDir, e.Name()), 0o600)
			}
		}
	}
}

// === Ed25519 数字签名（公告系统）===

// ed25519KeyHolder 持有 Ed25519 密钥对。
var (
	ed25519Once sync.Once
	ed25519Priv ed25519.PrivateKey
	ed25519Pub  ed25519.PublicKey
	ed25519PubHex string // 公钥十六进制（供客户端验签）
)

// EnsureEd25519KeyPair 确保 Ed25519 密钥对存在，持久化到 dataDir/keys/。
// 启动时调用一次。文件权限 0600。
func EnsureEd25519KeyPair(dataDir string) error {
	var initErr error
	ed25519Once.Do(func() {
		keysDir := filepath.Join(dataDir, "keys")
		if err := os.MkdirAll(keysDir, 0o700); err != nil {
			initErr = err
			return
		}
		privPath := filepath.Join(keysDir, "ed25519_private.pem")
		pubPath := filepath.Join(keysDir, "ed25519_public.pem")

		// 尝试加载已有密钥
		privData, err := os.ReadFile(privPath)
		if err == nil {
			block, _ := pem.Decode(privData)
			if block != nil {
				key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
				if err == nil {
					if k, ok := key.(ed25519.PrivateKey); ok {
						ed25519Priv = k
						ed25519Pub = k.Public().(ed25519.PublicKey)
						ed25519PubHex = hex.EncodeToString(ed25519Pub)
						return
					}
				}
			}
		}

		// 生成新密钥对
		pub, priv, err := ed25519.GenerateKey(crand.Reader)
		if err != nil {
			initErr = err
			return
		}
		ed25519Priv = priv
		ed25519Pub = pub
		ed25519PubHex = hex.EncodeToString(pub)

		// 序列化私钥（PKCS8）
		privBytes, err := x509.MarshalPKCS8PrivateKey(priv)
		if err != nil {
			initErr = err
			return
		}
		privPEM := pem.EncodeToMemory(&pem.Block{
			Type:  "PRIVATE KEY",
			Bytes: privBytes,
		})
		if err := os.WriteFile(privPath, privPEM, 0o600); err != nil {
			initErr = err
			return
		}

		// 序列化公钥
		pubBytes, err := x509.MarshalPKIXPublicKey(pub)
		if err != nil {
			initErr = err
			return
		}
		pubPEM := pem.EncodeToMemory(&pem.Block{
			Type:  "PUBLIC KEY",
			Bytes: pubBytes,
		})
		if err := os.WriteFile(pubPath, pubPEM, 0o600); err != nil {
			initErr = err
			return
		}
	})
	return initErr
}

// GetEd25519PublicKeyHex 返回 Ed25519 公钥十六进制字符串。
func GetEd25519PublicKeyHex() string {
	return ed25519PubHex
}

// SignAnnouncement 对公告内容进行 Ed25519 签名。
// 签名内容 = title + "\n" + contentMd + "\n" + createdAt（十进制字符串）。
// 返回签名的 Base64 编码。
func SignAnnouncement(title, contentMd string, createdAt int64) string {
	if ed25519Priv == nil {
		return ""
	}
	msg := title + "\n" + contentMd + "\n" + strconv.FormatInt(createdAt, 10)
	sig := ed25519.Sign(ed25519Priv, []byte(msg))
	return base64.StdEncoding.EncodeToString(sig)
}

// VerifyAnnouncementSignature 验证公告签名。
// 客户端可用此函数验签（也可用公钥 hex 自行验签）。
func VerifyAnnouncementSignature(title, contentMd string, createdAt int64, sigB64 string) bool {
	if ed25519Pub == nil || sigB64 == "" {
		return false
	}
	sig, err := base64.StdEncoding.DecodeString(sigB64)
	if err != nil {
		return false
	}
	msg := title + "\n" + contentMd + "\n" + strconv.FormatInt(createdAt, 10)
	return ed25519.Verify(ed25519Pub, []byte(msg), sig)
}

// GetAnnouncementPublicKey 获取公告验签公钥（管理员/客户端）。
// GET /api/announcements/public-key
func (d *Deps) GetAnnouncementPublicKey(c *fiber.Ctx) error {
	if ed25519PubHex == "" {
		return failInternal(c, "Ed25519 公钥未初始化")
	}
	return ok(c, fiber.Map{"publicKey": ed25519PubHex})
}

// 辅助：使用 crypto.Hash 别名避免未使用导入
var _ crypto.Hash = crypto.SHA256
