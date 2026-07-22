# Tasks

> **交付节奏**：按"一个端一个端"开发，共 6 个 Phase。每个 Phase 完成后自动 commit + push 至 GitHub `main` 分支（仓库 https://github.com/linlelest/LinkALL），触发对应 GitHub Actions workflow 编译。因本机无编译环境，所有构建验证依赖 CI。

---

## Phase 1 · 服务端（Go 单二进制）— 基础骨架与信令协议

- [ ] **Task 1.1**：搭建 Go 服务端工程骨架
  - [ ] SubTask 1.1.1：创建 `server/` 目录，`go mod init`，锁定 Go 1.24
  - [ ] SubTask 1.1.2：集成 Fiber v2.52.5 HTTP 框架、godotenv 配置加载、定义 `cmd/main.go` 入口
  - [ ] SubTask 1.1.3：实现 `./remote-server init` 交互式初始化（创建超级管理员、生成 `.env` 与 `data/` 目录）
  - [ ] SubTask 1.1.4：配置 modernc.org/sqlite 纯 Go 驱动（无 CGO）连接池

- [ ] **Task 1.2**：实现认证与邀请码模块
  - [ ] SubTask 1.2.1：实现 Argon2id 密码哈希工具
  - [ ] SubTask 1.2.2：实现 JWT 签发/校验中间件
  - [ ] SubTask 1.2.3：实现邀请码生成/吊销/批量导出逻辑（单次使用 + 时效）

- [ ] **Task 1.3**：编写 Pion WebRTC 信令服务器
  - [ ] SubTask 1.3.1：集成 Pion WebRTC v3.3.2，建立 WebSocket 信令通道
  - [ ] SubTask 1.3.2：实现 SDP offer/answer 交换与 ICE candidate 转发
  - [ ] SubTask 1.3.3：实现 STUN 直连 + TURN 中继 fallback 逻辑
  - [ ] SubTask 1.3.4：实现 WebSocket 心跳 15s + 断线指数退避重连（1s→2s→4s→max 30s）+ 会话超时 30min 休眠

- [ ] **Task 1.4**：定义 JSON 控制指令协议
  - [ ] SubTask 1.4.1：创建 `shared/` 共享协议目录，编写 JSON Schema（键鼠、文件元数据、设置同步、心跳、状态枚举、错误码）
  - [ ] SubTask 1.4.2：Go 端实现协议结构体与序列化/反序列化

- [ ] **Task 1.5**：数据库迁移脚本
  - [ ] SubTask 1.5.1：编写 users 表迁移（id/username/password_hash/role/invite_code_id/created_at/last_login_ip/device_count/traffic）
  - [ ] SubTask 1.5.2：编写 devices 表迁移（device_id 12位/device_code_hash/owner_user_id/online_status/last_seen/platform/version）
  - [ ] SubTask 1.5.3：编写 announcements 表迁移（id/title/content_md/pinned/platform/version_filter/created_at/signature）
  - [ ] SubTask 1.5.4：编写 ota_releases 表迁移（id/platform/version/file_path/file_hash/release_notes/force_update/created_at/download_count）

- [ ] **Task 1.6**：i18n 基础架构
  - [ ] SubTask 1.6.1：实现服务端扁平 JSON 语言包加载与热重载
  - [ ] SubTask 1.6.2：编写 zh-CN / en-US 基础错误码字符串

- [ ] **Task 1.7**：管理后台 API 路由
  - [ ] SubTask 1.7.1：用户管理路由（列表/封禁/重置密码/权限组）
  - [ ] SubTask 1.7.2：全局安全设置路由（强制 HTTPS/白名单/连接密码策略/最大并发/数据保留天数）
  - [ ] SubTask 1.7.3：服务器信息路由（CPU/内存/带宽/在线设备/信令延迟/.env 热重载预览）

- [ ] **Task 1.8**：GitHub Actions CI 配置（服务端）
  - [ ] SubTask 1.8.1：编写 `.github/workflows/server.yml`，Go 1.24 编译 Linux amd64 + Windows amd64 二进制（`ldflags="-s -w"`），上传 artifact
  - [ ] SubTask 1.8.2：配置触发条件（push 到 main，路径 `server/**`）

- [ ] **Task 1.9**：Phase 1 自动 push 触发 CI
  - [ ] SubTask 1.9.1：commit Phase 1 全部代码
  - [ ] SubTask 1.9.2：push 至 origin/main，确认 CI workflow 触发并构建成功

---

## Phase 2 · 网页端（Svelte 5）— 管理 + 控制一体

- [ ] **Task 2.1**：初始化前端工程
  - [ ] SubTask 2.1.1：创建 `web/` 目录，`npm create vite@latest` Svelte 5 (Runes) 模板
  - [ ] SubTask 2.1.2：集成 Tailwind CSS v4.1.3 + Vite 6.2.1
  - [ ] SubTask 2.1.3：搭建路由与布局骨架（桌面侧边导航 + 移动底部 TabBar，CSS Grid + Flex 自适应）

- [ ] **Task 2.2**：i18n 与语言切换
  - [ ] SubTask 2.2.1：实现扁平 JSON 语言包加载与热切换（默认跟随系统语言）
  - [ ] SubTask 2.2.2：编写 zh-CN / en-US 全量 UI 字符串

- [ ] **Task 2.3**：管理模块
  - [ ] SubTask 2.3.1：设备列表（卡片/列表视图切换、在线状态呼吸灯、复制设备编号/码、一键踢出）
  - [ ] SubTask 2.3.2：账号设置（修改密码、绑定邀请码、查看公告折叠面板、语言切换）
  - [ ] SubTask 2.3.3：高级设置（自定义服务器地址输入框覆盖 OFFICIAL_SERVER、连接超时阈值、日志级别开关）
  - [ ] SubTask 2.3.4：检查更新（请求 /api/ota/check、弹窗更新详情与下载进度、静默下载 + 手动安装提示）

- [ ] **Task 2.4**：控制模块 - 连接建立
  - [ ] SubTask 2.4.1：连接输入 UI（12位设备编号 + 设备码 + 连接模式切换 匿名/同账号）
  - [ ] SubTask 2.4.2：匿名模式确认弹窗（"允许控制？[仅本次/永久允许/拒绝]"）

- [ ] **Task 2.5**：控制模块 - 控制画布与设置面板
  - [ ] SubTask 2.5.1：屏幕缩放滑块（10%~300% 步进 5%，实时比例水印预览）
  - [ ] SubTask 2.5.2：码率对数滑块（512Kbps~200Mbps，显示当前值与预估延迟）
  - [ ] SubTask 2.5.3：帧率离散步进滑块（15/30/45/60/75/90/105/120/135/144 FPS 点击切换）
  - [ ] SubTask 2.5.4：防窥屏开关（发送指令覆盖被控端黑色全屏）
  - [ ] SubTask 2.5.5：WebRTC MediaStream 渲染管线（VP8/H264 接收与显示）

- [ ] **Task 2.6**：控制模块 - 触屏辅助小工具（手机/平板触发）
  - [ ] SubTask 2.6.1：虚拟键盘（半屏布局，Ctrl/Alt/Win/Meta 组合键，回车/ESC 快捷栏）
  - [ ] SubTask 2.6.2：虚拟鼠标（透明触控层，滑动=移动，单击=左键，长按=右键，双击模拟）
  - [ ] SubTask 2.6.3：左右键悬浮按钮（右下角固定 L/R，可拖动位置/透明度）
  - [ ] SubTask 2.6.4：滚轮垂直滑块（右侧，上滑=滚轮上/下滑=滚轮下，灵敏度可调）

- [ ] **Task 2.7**：控制模块 - 文件传输面板与状态栏
  - [ ] SubTask 2.7.1：文件传输面板（左右分栏 本地/远程，拖拽、多选、断点续传、进度条、目标路径选择、传输队列管理）
  - [ ] SubTask 2.7.2：状态栏（实时 RTT 延迟、丢包率、当前码率/帧率、编解码格式 H264/VP9/AV1、连接时长）

- [ ] **Task 2.8**：GitHub Actions CI 配置（网页端）
  - [ ] SubTask 2.8.1：编写 `.github/workflows/web.yml`，Vite 构建 Svelte 生产包（<8KB），上传 artifact
  - [ ] SubTask 2.8.2：配置触发条件（push 到 main，路径 `web/**`）

- [ ] **Task 2.9**：Phase 2 自动 push 触发 CI
  - [ ] SubTask 2.9.1：commit Phase 2 全部代码
  - [ ] SubTask 2.9.2：push 至 origin/main，确认 CI workflow 触发并构建成功

---

## Phase 3 · 桌面被控端（Tauri 2 + Rust）

- [ ] **Task 3.1**：搭建 Tauri 2 工程骨架
  - [ ] SubTask 3.1.1：创建 `desktop/` 目录，`npm create tauri-app@latest` 初始化 Tauri 2 + Rust 1.85
  - [ ] SubTask 3.1.2：配置 src-tauri/Cargo.toml 依赖（scrap 截屏、enigo 键鼠注入、serde、tokio）
  - [ ] SubTask 3.1.3：实现本地 SQLite 凭据存储

- [ ] **Task 3.2**：截屏与硬件编码
  - [ ] SubTask 3.2.1：集成 scrap 实现 DXGI（Win）/ X11/Wayland（Linux）原生截屏
  - [ ] SubTask 3.2.2：实现硬件编码（H.264 Baseline 默认，备选 VP9/AV1）对接 WebRTC MediaStream 管线
  - [ ] SubTask 3.2.3：实现动态码率自适应（GCC 算法，滑块为上限阈值）

- [ ] **Task 3.3**：键鼠注入模块
  - [ ] SubTask 3.3.1：实现 Win32 SendInput 键鼠注入
  - [ ] SubTask 3.3.2：实现 Linux uinput 键鼠注入
  - [ ] SubTask 3.3.3：对接 DataChannel 控制指令解析与注入

- [ ] **Task 3.4**：打通 Web 控制端 ↔ 桌面被控端链路
  - [ ] SubTask 3.4.1：实现音视频流 + DataChannel 控制指令双向通信
  - [ ] SubTask 3.4.2：实现屏幕缩放、码率/帧率参数透传

- [ ] **Task 3.5**：防窥屏黑屏覆盖
  - [ ] SubTask 3.5.1：Win 实现 SetThreadExecutionState + 全屏透明黑色 Overlay 窗口
  - [ ] SubTask 3.5.2：Linux 实现 X11/XCB 覆盖层
  - [ ] SubTask 3.5.3：控制端断开瞬间自动恢复

- [ ] **Task 3.6**：托盘 UI 与设置窗口
  - [ ] SubTask 3.6.1：系统托盘图标（右键菜单：显示设置/暂停服务/退出）
  - [ ] SubTask 3.6.2：主设置窗口（登录/登出、安全开关、12位设备编号显示、重置编号/密码二次确认、开机自启 Toggle）
  - [ ] SubTask 3.6.3：开机自启实现（Win 注册表 Run / Linux ~/.config/autostart/*.desktop 或 systemd --user）
  - [ ] SubTask 3.6.4：后台 Service/Daemon 模式（无托盘时运行，资源 <30MB RAM，CPU <2% 空闲）
  - [ ] SubTask 3.6.5：高级设置（自定义服务器地址、日志导出、性能监控开关、安全退出释放端口）

- [ ] **Task 3.7**：GitHub Actions CI 配置（桌面端）
  - [ ] SubTask 3.7.1：编写 `.github/workflows/desktop.yml`，Tauri 构建 Win64 + Linux-x86_64 安装包，上传 artifact
  - [ ] SubTask 3.7.2：配置触发条件（push 到 main，路径 `desktop/**`）

- [ ] **Task 3.8**：Phase 3 自动 push 触发 CI
  - [ ] SubTask 3.8.1：commit Phase 3 全部代码
  - [ ] SubTask 3.8.2：push 至 origin/main，确认 CI workflow 触发并构建成功

---

## Phase 4 · Android 原生 App（Kotlin + Jetpack Compose）

- [ ] **Task 4.1**：搭建 Android 工程骨架
  - [ ] SubTask 4.1.1：创建 `android/` 目录，Kotlin 2.1.20 + Jetpack Compose 1.7.2，配置 MVVM + Koin 依赖注入
  - [ ] SubTask 4.1.2：实现三页签导航（被控/控制/管理），主题跟随系统深色模式，中英文热切换
  - [ ] SubTask 4.1.3：禁用 Jetpack Navigation 重型组件，改用简单状态管理

- [ ] **Task 4.2**：被控端 - 截屏与注入
  - [ ] SubTask 4.2.1：实现 MediaProjection 录屏流 + libwebrtc（org.webrtc:libwebrtc-m2 m142+）编码推送管道
  - [ ] SubTask 4.2.2：开发 AccessibilityService 模块，实现全局键鼠事件注入与拦截
  - [ ] SubTask 4.2.3：前台保活服务 + 通知栏常驻快捷开关（断开/防窥屏/文件接收开关）

- [ ] **Task 4.3**：被控端 - 权限引导与设置
  - [ ] SubTask 4.3.1：首次进入权限引导页（无障碍服务/后台弹出界面/电池优化白名单/开机自启，适配各厂商）
  - [ ] SubTask 4.3.2：本地设置界面（登录/退出、Token 加密存储、安全设置、重置设备编号/设备码、退出软件清理）
  - [ ] SubTask 4.3.3：连接确认弹窗（匿名请求全屏遮罩，显示请求端 IP/ID/设备名，[允许一次/永久允许/拒绝/输入设备码放行]）

- [ ] **Task 4.4**：控制端 - 连接与控制页
  - [ ] SubTask 4.4.1：连接页（设备编号/密码输入，模式切换，同账号自动发现设备列表）
  - [ ] SubTask 4.4.2：控制页全沉浸式（SurfaceView/ExoPlayer 渲染视频流 + 双指捏合缩放）
  - [ ] SubTask 4.4.3：虚拟工具层（自定义 View：虚拟键盘输入法框架接管、虚拟鼠标 MotionEvent 映射、左右键悬浮球、滚轮侧边条）
  - [ ] SubTask 4.4.4：手势冲突处理（边缘滑动呼出设置面板，避免与控制手势冲突）
  - [ ] SubTask 4.4.5：参数面板底部抽屉（缩放/码率/帧率滑块、防窥屏开关、文件传输入口）

- [ ] **Task 4.5**：控制端 - 文件管理
  - [ ] SubTask 4.5.1：集成 Storage Access Framework (SAF)，双向传输、进度通知、断点续传
  - [ ] SubTask 4.5.2：支持外部 SD 卡读写

- [ ] **Task 4.6**：管理端
  - [ ] SubTask 4.6.1：用户中心（账号信息、邀请码管理、全局安全开关、高级设置自定义服务器覆盖）
  - [ ] SubTask 4.6.2：公告中心（下拉刷新列表，compose-markdown MD 渲染，图片点击放大）
  - [ ] SubTask 4.6.3：设备列表（在线/离线状态、一键连接、远程踢出、查看设备信息）
  - [ ] SubTask 4.6.4：OTA 更新（后台静默检查、前台弹窗下载、FLAG_INSTALL 引导系统安装器、强制更新拦截返回键）

- [ ] **Task 4.7**：Android 14+ 保活适配
  - [ ] SubTask 4.7.1：适配 Android 14+ 后台限制，实现保活策略
  - [ ] SubTask 4.7.2：自启动引导（各厂商 ROM 适配文档）

- [ ] **Task 4.8**：GitHub Actions CI 配置（Android 端）
  - [ ] SubTask 4.8.1：编写 `.github/workflows/android.yml`，Gradle 构建 arm64-v8a + armeabi-v7a APK（R8 全开），上传 artifact
  - [ ] SubTask 4.8.2：配置触发条件（push 到 main，路径 `android/**`）

- [ ] **Task 4.9**：Phase 4 自动 push 触发 CI
  - [ ] SubTask 4.9.1：commit Phase 4 全部代码
  - [ ] SubTask 4.9.2：push 至 origin/main，确认 CI workflow 触发并构建成功

---

## Phase 5 · 集成与高级功能（文件传输、安全、公告、日志）

- [ ] **Task 5.1**：WebRTC DataChannel 文件分片协议
  - [ ] SubTask 5.1.1：实现分片传输（单包 256KB，分片 ID+偏移量校验）
  - [ ] SubTask 5.1.2：实现 SHA-256 哈希校验完整性
  - [ ] SubTask 5.1.3：实现断点续传（记录至本地 DB）与传输队列管理

- [ ] **Task 5.2**：文件管理器 UI（双端）
  - [ ] SubTask 5.2.1：网页端文件管理器（目录树、上传/下载队列、路径选择、传输统计）
  - [ ] SubTask 5.2.2：Android 端 SAF 文件管理器完善

- [ ] **Task 5.3**：匿名与同账号连接流程
  - [ ] SubTask 5.3.1：匿名连接流程（编号输入 + 设备端手动确认 单次/永久/拒绝/设备码放行）
  - [ ] SubTask 5.3.2：同账号连接流程（设备自动发现 + 首次配对确认 + 免密直连）

- [ ] **Task 5.4**：全局安全策略
  - [ ] SubTask 5.4.1：匿名/设备码/连接总开关服务端校验与实时同步
  - [ ] SubTask 5.4.2：自定义服务器地址覆盖逻辑（高级设置热重载）
  - [ ] SubTask 5.4.3：控制指令带时间戳防重放、设备码/编号非对称加密传输、本地配置 0600 权限

- [ ] **Task 5.5**：公告系统
  - [ ] SubTask 5.5.1：服务端 Markdown 解析、富文本编辑工具栏（加粗/斜体/代码块/表格/图片/链接）
  - [ ] SubTask 5.5.2：多条置顶、按平台/版本推送、阅读状态追踪、数字签名

- [ ] **Task 5.6**：客户端日志与崩溃上报
  - [ ] SubTask 5.6.1：客户端分级日志（DEBUG/INFO/WARN/ERROR），一键打包导出
  - [ ] SubTask 5.6.2：崩溃捕获（Rust backtrace / Android 自研轻量 Crashlytics）上传至 /crash
  - [ ] SubTask 5.6.3：服务端结构化日志（JSON 格式）

- [ ] **Task 5.7**：Phase 5 自动 push 触发 CI
  - [ ] SubTask 5.7.1：commit Phase 5 全部代码
  - [ ] SubTask 5.7.2：push 至 origin/main，确认 CI workflow 触发并构建成功

---

## Phase 6 · OTA、优化与交付

- [ ] **Task 6.1**：服务端 OTA 路由
  - [ ] SubTask 6.1.1：实现 /ota 路由（文件上传至 /ota 目录、元数据入库、自动生成哈希、版本管理）
  - [ ] SubTask 6.1.2：实现强制标志下发、CRUD、批量下架、下载量统计
  - [ ] SubTask 6.1.3：OTA 更新包 Ed25519 签名校验

- [ ] **Task 6.2**：客户端 OTA 检查逻辑
  - [ ] SubTask 6.2.1：启动 + 每小时轮询 /api/ota/check，版本对比
  - [ ] SubTask 6.2.2：强制更新锁定界面仅显示进度条、拦截返回键
  - [ ] SubTask 6.2.3：签名校验防篡改，失败自动回滚

- [ ] **Task 6.3**：性能优化
  - [ ] SubTask 6.3.1：WebRTC 码率自适应算法优化（GCC + 滑块阈值联动）
  - [ ] SubTask 6.3.2：内存泄漏排查（WebRTC 轨道销毁、Compose 状态回收、Tauri GC 调优）
  - [ ] SubTask 6.3.3：二进制体积优化（Go ldflags="-s -w"、Tauri 剔除无用依赖、Android ProGuard/R8 全开）
  - [ ] SubTask 6.3.4：网络弱网测试（3G/高丢包/高延迟模拟，验证重连/降码率/断线保活）
  - [ ] SubTask 6.3.5：全链路压力测试（100+ 并发信令、多设备同时控制、大文件并发传输）

- [ ] **Task 6.4**：多语言收尾
  - [ ] SubTask 6.4.1：zh-CN / en-US 全量字符串校对，确保无遗漏、语境准确
  - [ ] SubTask 6.4.2：日期/数字格式化跟随 Locale，RTL 预留

- [ ] **Task 6.5**：文档
  - [ ] SubTask 6.5.1：部署文档（Linux Systemd / Win NSSM / Docker 可选、.env 配置说明）
  - [ ] SubTask 6.5.2：用户手册（管理员后台、网页控制、桌面被控、安卓 App 全功能图文指南）
  - [ ] SubTask 6.5.3：自动化打包流水线（GitHub Actions 编译 Win/Linux/Android/Static Web 全产物）

- [ ] **Task 6.6**：安全审计
  - [ ] SubTask 6.6.1：渗透测试（信令劫持、XSS、越权、文件路径遍历、OTA 签名绕过）

- [ ] **Task 6.7**：正式发布
  - [ ] SubTask 6.7.1：发布 v1.0.0，归档代码，建立 Bug 追踪与反馈通道

- [ ] **Task 6.8**：Phase 6 自动 push 触发 CI
  - [ ] SubTask 6.8.1：commit Phase 6 全部代码
  - [ ] SubTask 6.8.2：push 至 origin/main，确认 CI workflow 触发并构建成功，归档 v1.0.0 release

---

# Task Dependencies

- **Phase 1（服务端）** 为所有后续 Phase 的基础：信令协议、认证、DB schema 供前端/客户端对接
- **Phase 2（网页端）** 依赖 Phase 1 的 API 与信令通道
- **Phase 3（桌面端）** 依赖 Phase 1 的信令服务器与 Phase 2 的协议透传验证
- **Phase 4（Android 端）** 依赖 Phase 1 的信令与协议定义
- **Phase 5（集成）** 依赖 Phase 1-4 各端基础链路打通
- **Phase 6（OTA/优化/交付）** 依赖 Phase 1-5 功能完整
- 每个 Phase 内部 Task 按序执行，CI 配置 Task（如 1.8/2.8/3.7/4.8）在该 Phase 功能完成后、push（1.9/2.9/3.8/4.9/5.7/6.8）前完成
