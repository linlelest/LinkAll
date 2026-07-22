// Package webrtc 封装 Pion WebRTC 信令服务器、连接 Hub、STUN/TURN 配置。
package webrtc

import (
	"fmt"
	"strings"

	"github.com/pion/webrtc/v3"
)

// ICEConfig 封装 STUN/TURN 配置。
type ICEConfig struct {
	STUNServers   []string
	TURNServers   []string
	TURNUsername  string
	TURNCredential string
}

// ToWebRTCConfiguration 转换为 Pion WebRTC Configuration。
// 默认 ICEPolicy 为 All（host + srflx + relay），允许 STUN 直连与 TURN 中继 fallback。
func (c *ICEConfig) ToWebRTCConfiguration() webrtc.Configuration {
	servers := make([]webrtc.ICEServer, 0, len(c.STUNServers)+len(c.TURNServers))

	// STUN 服务器
	if len(c.STUNServers) > 0 {
		servers = append(servers, webrtc.ICEServer{
			URLs: c.STUNServers,
		})
	}

	// TURN 服务器（带凭据）
	for _, turn := range c.TURNServers {
		if strings.TrimSpace(turn) == "" {
			continue
		}
		servers = append(servers, webrtc.ICEServer{
			URLs:       []string{turn},
			Username:   c.TURNUsername,
			Credential: c.TURNCredential,
		})
	}

	return webrtc.Configuration{
		ICEServers:         servers,
		ICETransportPolicy: webrtc.ICETransportPolicyAll, // 默认 All，失败时调用方可切 relay
		BundlePolicy:       webrtc.BundlePolicyMaxBundle,
	}
}

// RelayConfiguration 当 P2P 直连失败时切换为 TURN 中继。
func (c *ICEConfig) RelayConfiguration() webrtc.Configuration {
	base := c.ToWebRTCConfiguration()
	base.ICETransportPolicy = webrtc.ICETransportPolicyRelay
	return base
}

// Describe 返回人类可读的描述字符串，用于日志与初始化输出。
func (c *ICEConfig) Describe() string {
	parts := []string{}
	if len(c.STUNServers) > 0 {
		parts = append(parts, fmt.Sprintf("STUN=%d", len(c.STUNServers)))
	} else {
		parts = append(parts, "STUN=0(仅host)")
	}
	if len(c.TURNServers) > 0 {
		parts = append(parts, fmt.Sprintf("TURN=%d(user=%s)", len(c.TURNServers), c.TURNUsername))
	} else {
		parts = append(parts, "TURN=0(无中继回退)")
	}
	return strings.Join(parts, " ")
}
