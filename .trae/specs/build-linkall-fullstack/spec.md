# LinkALL 全平台远程控制系统 - 全栈构建 Spec

## Why

项目根目录下的 `设计方案.txt` 定义了一个极轻量级跨平台远程控制系统 LinkALL，涵盖服务端、网页端、桌面被控端、Android 原生 App 四端。当前仓库仅有设计文档与图标资源，尚无任何代码实现。本变更旨在按"一个端一个端"的开发节奏，通过单一长任务一次性完成设计方案中所述的全部功能，并利用 GitHub Actions CI 完成各端编译（因本机无编译环境），每完成一个 Phase 即自动 push 触发 CI 构建。

## What Changes

### 新增 - 按端分阶段交付

- **Phase 1 · 服务端（Go）**：搭建 Go 1.24 单二进制服务端骨架，集成 Fiber v2 HTTP 框架、modernc.org/sqlite（纯 Go 无 CGO）、godotenv 配置、JWT 认证、Argon2id 密码哈希、邀请码体系；实现 Pion WebRTC v3 信令服务器（WebSocket SDP/ICE 交换）；定义 JSON 控制指令协议；数据库迁移（用户/设备/公告/OTA 表）；i18n 基础架构。
- **Phase 2 · 网页端（Svelte 5）**：初始化 Svelte 5 (Runes) + Vite 6 + Tailwind 4 工程；实现响应式布局（桌面侧边导航/移动底部 TabBar）；管理模块（设备列表、账号设置、高级设置、检查更新）；控制模块（连接建立、控制画布、缩放/码率/帧率滑块、防窥屏、触屏虚拟键盘/鼠标/左右键/滚轮、文件传输面板、状态栏）；i18n 双语热切换。
- **Phase 3 · 桌面被控端（Tauri 2 + Rust）**：搭建 Tauri 2 + Rust 1.85 工程；集成 scrap 截屏（DXGI/X11/Wayland）与硬件编码；Win32 SendInput / Linux uinput 键鼠注入；WebRTC MediaStream 管线；防窥屏黑屏覆盖（Win/Linux）；系统托盘 UI（登录/安全开关/重置编号密码/开机自启）；后台 Service 模式。
- **Phase 4 · Android 端（Kotlin + Compose）**：搭建 Kotlin 2.1 + Compose 工程（MVVM + Koin）；三页签导航（被控/控制/管理）；MediaProjection 录屏流 + libwebrtc 编码；AccessibilityService 键鼠注入；被控页签（权限引导/前台服务/通知栏/连接确认）；控制页签（SurfaceView 渲染/双指缩放/虚拟工具层/手势冲突处理）；管理页签（账号/设备/公告 MD/OTA）；SAF 文件传输；Android 14+ 保活适配。
- **Phase 5 · 集成与高级功能**：WebRTC DataChannel 文件分片协议 + SHA-256 校验 + 断点续传；文件管理器 UI（双端）；匿名连接流程 + 设备端确认；同账号设备自动发现 + 免密直连；全局安全策略服务端校验与实时同步；自定义服务器地址热重载；公告系统 MD 解析/工具栏/多语言推送/阅读追踪；客户端日志系统 + 崩溃捕获上报。
- **Phase 6 · OTA、优化与交付**：服务端 /ota 路由（上传/元数据/版本管理/强制标志）；客户端 OTA 检查（静默轮询/版本对比/强制拦截/Ed25519 签名校验/失败回滚）；WebRTC 码率自适应（GCC 算法 + 滑块阈值联动）；内存泄漏优化；二进制体积优化（ldflags/Tauri 剔除依赖/R8）；弱网测试；压力测试；zh-CN/en-US 全量校对；部署文档；用户手册；GitHub Actions 流水线（Win/Linux/Android/Static Web）；安全审计；v1.0.0 发布。

### CI/CD 流程

- **每个 Phase 完成后自动 push 至 GitHub `main` 分支**（仓库：https://github.com/linlelest/LinkALL）。
- 配置 GitHub Actions workflow 文件，按端构建：
  - Go 服务端：Linux amd64 + Windows amd64 二进制（`ldflags="-s -w"`）
  - 网页端：静态资源构建产物
  - Tauri 桌面端：Win64 + Linux-x86_64 安装包
  - Android 端：arm64-v8a + armeabi-v7a APK（R8 全开）

## Impact

- **Affected specs**: 无（首次创建）
- **Affected code**: 整个仓库从零搭建，目录结构规划如下：
  ```
  LinkALL/
  ├── server/              # Go 服务端
  │   ├── cmd/             # main 入口
  │   ├── internal/        # auth/webrtc/db/handlers/ota
  │   ├── migrations/      # SQL 迁移脚本
  │   └── go.mod
  ├── web/                 # Svelte 5 网页端
  │   ├── src/
  │   └── package.json
  ├── desktop/             # Tauri 2 桌面被控端
  │   ├── src-tauri/
  │   └── src/
  ├── android/             # Kotlin/Compose Android App
  │   └── app/
  ├── shared/              # 共享协议定义（JSON Schema）
  ├── .github/workflows/   # CI 构建流水线
  ├── ota/                 # OTA 更新包目录
  ├── 设计方案.txt
  └── icon.ico / icon.jpeg
  ```

## ADDED Requirements

### Requirement: 服务端单二进制骨架（Phase 1）
系统 SHALL 提供 Go 1.24 单二进制服务端，集成 Fiber v2、modernc.org/sqlite（纯 Go 无 CGO）、godotenv，支持 `./remote-server init` 交互式创建超级管理员。

#### Scenario: 首次初始化
- **WHEN** 管理员运行 `./remote-server init`
- **THEN** 终端引导创建超级管理员账户（用户名/密码/二次确认），生成 `.env` 与 `data/` 目录

### Requirement: JWT 认证与邀请码体系（Phase 1）
系统 SHALL 提供 JWT 认证模块，密码采用 Argon2id 哈希；邀请码支持生成/吊销/批量导出，单次使用 + 时效。

#### Scenario: 邀请码生成
- **WHEN** 管理员请求生成邀请码
- **THEN** 系统生成单次使用邀请码并记录时效，支持批量导出与吊销

### Requirement: WebRTC 信令服务器（Phase 1）
系统 SHALL 基于 Pion WebRTC v3 提供 WebSocket 信令通道，完成 SDP/ICE 交换，支持 P2P 直连（STUN）与 TURN 中继 fallback。

#### Scenario: NAT 穿透失败
- **WHEN** P2P 直连失败（NAT 严格/企业防火墙）
- **THEN** 自动 fallback 至 TURN 中继

### Requirement: JSON 控制指令协议（Phase 1）
系统 SHALL 定义 JSON 控制指令协议（键鼠、文件、设置同步、心跳），集中定义于共享 JSON Schema 文件，通过代码生成同步至各端。

### Requirement: 数据库迁移（Phase 1）
系统 SHALL 提供 SQLite 迁移脚本，覆盖用户、设备、公告、OTA 记录表结构。

### Requirement: 网页端响应式布局（Phase 2）
网页端 SHALL 采用 CSS Grid + Flex 自适应布局，桌面侧边导航 + 主工作区，移动底部 TabBar + 全屏堆叠面板，无媒体查询硬编码。

### Requirement: 网页端控制模块（Phase 2）
网页端 SHALL 提供控制画布与设置面板，含屏幕缩放滑块（10%~300% 步进 5%）、码率对数滑块（512Kbps~200Mbps）、帧率离散步进（15~144FPS）、防窥屏开关、触屏虚拟键盘/鼠标/左右键/滚轮、文件传输分栏面板、状态栏（RTT/丢包率/码率帧率/编解码/连接时长）。

#### Scenario: 移动端触屏控制
- **WHEN** 用户在手机/平板使用控制功能
- **THEN** 触发虚拟键盘（半屏 + 组合键）、虚拟鼠标（滑动移动/单击左键/长按右键）、左右键悬浮按钮（可拖动/透明度可调）、滚轮垂直滑块

### Requirement: 桌面被控端 Tauri 工程（Phase 3）
桌面端 SHALL 基于 Tauri 2 + Rust 1.85，集成 scrap 截屏（DXGI/X11/Wayland）与硬件编码，实现 Win32 SendInput / Linux uinput 键鼠注入，内存占用 <30MB RAM，CPU <2% 空闲。

#### Scenario: 后台运行
- **WHEN** 用户关闭设置窗口
- **THEN** 程序以托盘模式运行，保持截屏与注入服务，资源占用达标

### Requirement: 桌面端防窥屏（Phase 3）
桌面端 SHALL 实现防窥屏黑屏覆盖：Win 使用 SetThreadExecutionState + 全屏透明黑色 Overlay 窗口；Linux 使用 X11/XCB 覆盖层；控制端断开瞬间自动恢复。

### Requirement: Android 三页签导航（Phase 4）
Android App SHALL 提供三页签导航（被控/控制/管理），纯 Kotlin/Compose 实现，内存占用 <60MB，冷启动 <0.8s。

### Requirement: Android MediaProjection + Accessibility（Phase 4）
Android 被控端 SHALL 使用 MediaProjection 截屏流 + libwebrtc 编码推送，AccessibilityService 实现全局键鼠注入；前台保活服务 + 通知栏常驻快捷开关。

#### Scenario: 首次权限引导
- **WHEN** 用户首次进入被控页签
- **THEN** 引导开启无障碍服务、后台弹出界面、电池优化白名单、开机自启（适配各厂商）

### Requirement: Android 控制端虚拟工具层（Phase 4）
Android 控制端 SHALL 提供 SurfaceView 渲染 + 双指捏合缩放，自定义 View 覆盖实现虚拟键盘（输入法框架接管）、虚拟鼠标（MotionEvent 映射）、左右键悬浮球、滚轮侧边条；边缘滑动呼出设置面板，避免手势冲突。

### Requirement: 文件传输协议（Phase 5）
系统 SHALL 通过 WebRTC DataChannel 实现文件分片传输，单包限制 256KB，分片 ID+偏移量校验，SHA-256 哈希校验完整性，支持断点续传（记录至本地 DB）与传输队列管理。

### Requirement: 匿名与同账号连接（Phase 5）
系统 SHALL 实现匿名连接流程（编号输入 + 设备端手动确认：单次/永久/拒绝/设备码放行）与同账号连接流程（设备自动发现 + 首次配对确认 + 免密直连）。

### Requirement: 公告系统（Phase 5）
服务端 SHALL 实现公告系统，支持 Markdown 富文本编辑（加粗/斜体/代码块/表格/图片/链接）、多条置顶、按平台/版本推送、阅读状态追踪、数字签名。

### Requirement: OTA 更新体系（Phase 6）
系统 SHALL 提供 OTA 更新体系：服务端 /ota 路由（上传/元数据入库/版本管理/强制标志下发/下载量统计）；客户端启动 + 每小时轮询 /api/ota/check，版本对比，强制更新锁定界面仅显示进度条，Ed25519 签名校验防篡改，失败自动回滚。

#### Scenario: 强制更新
- **WHEN** 客户端检测到强制更新版本
- **THEN** 锁定界面仅显示下载进度条，拦截返回键，引导系统安装器

### Requirement: GitHub Actions CI（贯穿各 Phase）
每个 Phase 完成后 SHALL 自动 push 至 GitHub main 分支，触发对应端的 GitHub Actions workflow 构建产物。

#### Scenario: Phase 完成自动 push
- **WHEN** 某 Phase 全部任务完成并通过本地验证
- **THEN** 自动 commit 并 push 至 origin/main，触发 CI 构建

### Requirement: 双语本地化（贯穿各 Phase）
全端 SHALL 内置 zh-CN / en-US 双语切换，默认跟随系统语言，首次启动可手动选择；字符串全部外置，杜绝硬编码；热加载无需重启。

## MODIFIED Requirements

无（首次创建，无既有需求修改）。

## REMOVED Requirements

无（首次创建，无既有需求移除）。
