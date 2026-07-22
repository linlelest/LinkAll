// Package i18n 实现扁平 JSON 语言包加载与热重载。
// 设计原则：字符串全部外置，杜绝硬编码；通过文件监听实现热加载无需重启。
package i18n

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// 常量
const (
	// DefaultLang 默认语言
	DefaultLang = "zh-CN"
	// FallbackLang 回退语言（缺失键时尝试）
	FallbackLang = "en-US"
	// SupportedLangs 支持的语言列表
)

var SupportedLangs = []string{"zh-CN", "en-US"}

// Loader 语言包加载器，支持热重载。
type Loader struct {
	dir      string
	mu       sync.RWMutex
	packs    map[string]map[string]string // lang -> key -> value
	modtimes map[string]time.Time         // lang -> 文件最后修改时间
	watching int32                        // 是否已启动监听
	quit     chan struct{}
}

// NewLoader 创建加载器。dir 为 locales 目录路径。
func NewLoader(dir string) *Loader {
	return &Loader{
		dir:      dir,
		packs:    make(map[string]map[string]string),
		modtimes: make(map[string]time.Time),
		quit:     make(chan struct{}),
	}
}

// Load 加载所有支持的语言包。
func (l *Loader) Load() error {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, lang := range SupportedLangs {
		if err := l.loadLangLocked(lang); err != nil {
			return err
		}
	}
	return nil
}

// loadLangLocked 加载单个语言包（调用方需持锁）。
func (l *Loader) loadLangLocked(lang string) error {
	path := filepath.Join(l.dir, lang+".json")
	data, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("读取语言包 %s 失败: %w", path, err)
	}
	// 扁平 JSON：直接 map[string]string
	pack := make(map[string]string)
	if err := json.Unmarshal(data, &pack); err != nil {
		return fmt.Errorf("解析语言包 %s 失败: %w", path, err)
	}
	l.packs[lang] = pack

	// 记录文件修改时间
	if fi, err := os.Stat(path); err == nil {
		l.modtimes[lang] = fi.ModTime()
	}
	return nil
}

// Watch 启动文件变更监听，定期检查修改时间实现热重载。
// interval 为检查间隔，推荐 5s。
func (l *Loader) Watch(interval time.Duration) {
	if !atomic.CompareAndSwapInt32(&l.watching, 0, 1) {
		return // 已在监听
	}
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				l.checkAndReload()
			case <-l.quit:
				return
			}
		}
	}()
}

// StopWatch 停止监听。
func (l *Loader) StopWatch() {
	if atomic.CompareAndSwapInt32(&l.watching, 1, 0) {
		close(l.quit)
		l.quit = make(chan struct{})
	}
}

// checkAndReload 检查文件修改时间，变更则重新加载。
func (l *Loader) checkAndReload() {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, lang := range SupportedLangs {
		path := filepath.Join(l.dir, lang+".json")
		fi, err := os.Stat(path)
		if err != nil {
			continue
		}
		if prev, ok := l.modtimes[lang]; !ok || !fi.ModTime().Equal(prev) {
			// 修改时间变化，重新加载
			if err := l.loadLangLocked(lang); err == nil {
				l.modtimes[lang] = fi.ModTime()
			}
		}
	}
}

// Translate 翻译键到指定语言。
// 缺失时回退到 FallbackLang，再缺失则返回 key 本身。
func (l *Loader) Translate(lang, key string) string {
	l.mu.RLock()
	defer l.mu.RUnlock()

	// 优先匹配指定语言
	if pack, ok := l.packs[lang]; ok {
		if v, ok := pack[key]; ok && v != "" {
			return v
		}
	}
	// 回退到 FallbackLang
	if lang != FallbackLang {
		if pack, ok := l.packs[FallbackLang]; ok {
			if v, ok := pack[key]; ok && v != "" {
				return v
			}
		}
	}
	// 最终回退到 key 本身
	return key
}

// TranslateWithArgs 翻译并替换 {0} {1} 占位符。
func (l *Loader) TranslateWithArgs(lang, key string, args ...interface{}) string {
	s := l.Translate(lang, key)
	if len(args) == 0 {
		return s
	}
	for i, a := range args {
		s = strings.ReplaceAll(s, fmt.Sprintf("{%d}", i), fmt.Sprintf("%v", a))
	}
	return s
}

// Global 全局加载器单例。
var (
	globalLoader     *Loader
	globalLoaderOnce sync.Once
)

// InitGlobal 初始化全局语言包加载器。
func InitGlobal(dir string) error {
	var err error
	globalLoaderOnce.Do(func() {
		globalLoader = NewLoader(dir)
		err = globalLoader.Load()
	})
	if err != nil {
		return err
	}
	// 允许重复调用以重新加载
	if globalLoader == nil {
		globalLoader = NewLoader(dir)
		if e := globalLoader.Load(); e != nil {
			return e
		}
	}
	return nil
}

// Global 返回全局加载器。
func Global() *Loader {
	if globalLoader == nil {
		// 兜底：使用空加载器
		globalLoader = NewLoader(".")
	}
	return globalLoader
}

// T 全局翻译快捷函数（默认语言）。
func T(key string) string {
	if globalLoader != nil {
		return globalLoader.Translate(DefaultLang, key)
	}
	return key
}

// TL 全局翻译快捷函数（指定语言）。
func TL(lang, key string) string {
	if globalLoader != nil {
		return globalLoader.Translate(lang, key)
	}
	return key
}
