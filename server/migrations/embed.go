// Package migrations 将 SQL 迁移文件嵌入二进制，运行时不依赖磁盘目录。
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
