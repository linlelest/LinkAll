// Package static 将网页端构建产物（web/dist）嵌入服务端二进制，
// 实现单二进制部署：启动服务端即自动提供网页端，无需额外静态文件服务器。
//
// 构建流程（CI）：
//  1. npm run build → web/dist/
//  2. cp -r web/dist/* server/web_dist/
//  3. go build → 前端被 embed 进二进制
package static

import (
	"embed"
	"io/fs"
)

//go:embed all:web_dist
var embeddedFiles embed.FS

// FS 返回网页端构建产物的 fs.FS（根路径为 web_dist 目录）。
func FS() fs.FS {
	sub, err := fs.Sub(embeddedFiles, "web_dist")
	if err != nil {
		// 理论上不会失败：web_dist 目录在构建时必然存在（至少有 .gitkeep）
		panic("static: web_dist 嵌入失败: " + err.Error())
	}
	return sub
}

// Exists 判断嵌入的 web_dist 是否包含实际构建产物（非空占位文件）。
// 用于启动时日志提示是否已嵌入前端。
func HasFrontend() bool {
	entries, err := embeddedFiles.ReadDir("web_dist")
	if err != nil {
		return false
	}
	for _, e := range entries {
		if !e.Is() {
			return true // 至少存在一个文件
		}
	}
	return false
}
