# Checklist

> 验证点对照 spec.md 与 tasks.md，每项完成后勾选。因本机无编译环境，构建类验证依赖 GitHub Actions CI 成功。

## Phase 1 · 服务端（Go）

- [ ] `server/` 工程存在，go.mod 锁定 Go 1.24，依赖 Fiber v2.52.5、modernc.org/sqlite、Pion WebRTC v3.3.2、godotenv
- [ ] `./remote-server init` 可交互式创建超级管理员，生成 `.env` 与 `data/` 目录
- [ ] Argon2id 密码哈希工具实现，JWT 签发/校验中间件实现
- [ ] 邀请码生成/吊销/批量导出逻辑实现（单次使用 + 时效）
- [ ] Pion WebRTC 信令服务器实现 WebSocket 通道，SDP/ICE 交换可完成
- [ ] STUN 直连 + TURN 中继 fallback 逻辑实现
- [ ] WebSocket 心跳 15s + 断线指数退避重连（1s→2s→4s→max 30s）+ 会话 30min 超时休眠实现
- [ ] `shared/` 目录 JSON Schema 协议定义存在（键鼠/文件/设置/心跳/状态枚举/错误码）
- [ ] 数据库迁移脚本覆盖 users / devices / announcements / ota_releases 四张表
- [ ] i18n 扁平 JSON 语言包加载与热重载实现，zh-CN / en-US 基础错误码字符串存在
- [ ] 管理后台 API 路由实现（用户管理、全局安全设置、服务器信息）
- [ ] `.github/workflows/server.yml` 存在，配置 push 到 main + 路径 `server/**` 触发
- [ ] Phase 1 已 push 至 origin/main，GitHub Actions 服务端构建 workflow 运行成功，产物 artifact 上传成功

## Phase 2 · 网页端（Svelte 5）

- [ ] `web/` 工程存在，Svelte 5 (Runes) + Vite 6 + Tailwind 4 集成完成
- [ ] 桌面侧边导航 + 移动底部 TabBar 布局实现，CSS Grid + Flex 自适应无媒体查询硬编码
- [ ] i18n 语言切换热更新实现，默认跟随系统语言，zh-CN / en-US 全量 UI 字符串存在
- [ ] 设备列表实现（卡片/列表视图切换、在线呼吸灯、复制设备编号/码、一键踢出）
- [ ] 账号设置实现（修改密码、绑定邀请码、查看公告折叠面板、语言切换）
- [ ] 高级设置实现（自定义服务器地址覆盖、连接超时阈值、日志级别开关）
- [ ] 检查更新实现（请求 /api/ota/check、弹窗详情与下载进度、静默下载 + 手动安装）
- [ ] 连接输入 UI 实现（12位设备编号 + 设备码 + 匿名/同账号模式切换）
- [ ] 匿名模式确认弹窗实现（仅本次/永久允许/拒绝）
- [ ] 屏幕缩放滑块实现（10%~300% 步进 5%，实时水印预览）
- [ ] 码率对数滑块实现（512Kbps~200Mbps，显示当前值与预估延迟）
- [ ] 帧率离散步进滑块实现（15~144 FPS 点击切换）
- [ ] 防窥屏开关实现（发送指令覆盖被控端黑色全屏）
- [ ] WebRTC MediaStream 渲染管线实现（VP8/H264 接收显示）
- [ ] 虚拟键盘实现（半屏、Ctrl/Alt/Win/Meta 组合键、回车/ESC 快捷栏）
- [ ] 虚拟鼠标实现（透明触控层、滑动移动、单击左键、长按右键、双击模拟）
- [ ] 左右键悬浮按钮实现（右下角固定 L/R、可拖动位置/透明度）
- [ ] 滚轮垂直滑块实现（灵敏度可调）
- [ ] 文件传输面板实现（左右分栏、拖拽、多选、断点续传、进度条、路径选择、队列管理）
- [ ] 状态栏实现（RTT 延迟、丢包率、码率/帧率、编解码格式、连接时长）
- [ ] `.github/workflows/web.yml` 存在，配置 push 到 main + 路径 `web/**` 触发
- [ ] Phase 2 已 push 至 origin/main，GitHub Actions 网页端构建 workflow 运行成功，生产包 <8KB artifact 上传成功

## Phase 3 · 桌面被控端（Tauri 2 + Rust）

- [ ] `desktop/` 工程存在，Tauri 2 + Rust 1.85 初始化完成，依赖 scrap/enigo/serde/tokio 配置
- [ ] 本地 SQLite 凭据存储实现
- [ ] scrap 截屏实现（Win DXGI / Linux X11+Wayland）
- [ ] 硬件编码实现（H.264 Baseline 默认，备选 VP9/AV1）对接 WebRTC MediaStream
- [ ] 动态码率自适应 GCC 算法实现（滑块为上限阈值）
- [ ] Win32 SendInput 键鼠注入实现
- [ ] Linux uinput 键鼠注入实现
- [ ] DataChannel 控制指令解析与注入对接完成
- [ ] Web 控制端 ↔ 桌面被控端音视频流 + DataChannel 双向通信打通
- [ ] 屏幕缩放、码率/帧率参数透传实现
- [ ] Win 防窥屏实现（SetThreadExecutionState + 全屏透明黑色 Overlay）
- [ ] Linux 防窥屏实现（X11/XCB 覆盖层）
- [ ] 控制端断开瞬间自动恢复实现
- [ ] 系统托盘图标实现（右键菜单：显示设置/暂停服务/退出）
- [ ] 主设置窗口实现（登录/登出、安全开关、12位编号显示、重置编号/密码二次确认、开机自启 Toggle）
- [ ] 开机自启实现（Win 注册表 Run / Linux autostart 或 systemd --user）
- [ ] 后台 Service/Daemon 模式实现（资源 <30MB RAM，CPU <2% 空闲）
- [ ] 高级设置实现（自定义服务器地址、日志导出、性能监控、安全退出释放端口）
- [ ] `.github/workflows/desktop.yml` 存在，配置 push 到 main + 路径 `desktop/**` 触发
- [ ] Phase 3 已 push 至 origin/main，GitHub Actions 桌面端构建 workflow 运行成功，Win64 + Linux-x86_64 安装包 artifact 上传成功

## Phase 4 · Android 端（Kotlin + Compose）

- [x] `android/` 工程存在，Kotlin 2.1.20 + Compose 1.7.2 + Koin 依赖注入配置完成
- [x] 三页签导航实现（被控/控制/管理），主题跟随系统深色模式，中英文热切换无需重启
- [x] 禁用 Jetpack Navigation，使用简单状态管理
- [x] MediaProjection 录屏流 + libwebrtc（io.github.webrtc-sdk:android m144）编码推送管道实现
- [x] AccessibilityService 全局键鼠事件注入与拦截实现
- [x] 前台保活服务 + 通知栏常驻快捷开关实现（断开/防窥屏/文件接收开关）
- [x] 首次权限引导页实现（无障碍/后台弹出/电池优化白名单/开机自启，适配各厂商）
- [x] 本地设置界面实现（登录/退出、Token 加密存储、安全设置、重置编号/设备码、退出清理）
- [x] 连接确认弹窗实现（匿名请求全屏遮罩、显示请求端 IP/ID/设备名、[允许一次/永久允许/拒绝/设备码放行]）
- [x] 连接页实现（设备编号/密码、模式切换、同账号自动发现设备列表）
- [x] 控制页全沉浸式实现（SurfaceView/ExoPlayer 渲染 + 双指捏合缩放）
- [x] 虚拟工具层实现（自定义 View：虚拟键盘输入法接管、虚拟鼠标 MotionEvent 映射、左右键悬浮球、滚轮侧边条）
- [x] 手势冲突处理实现（边缘滑动呼出设置面板）
- [x] 参数面板底部抽屉实现（缩放/码率/帧率滑块、防窥屏开关、文件传输入口）
- [x] SAF 文件传输实现（双向、进度通知、断点续传、外部 SD 卡读写）
- [x] 管理端用户中心实现（账号信息、邀请码管理、全局安全开关、自定义服务器覆盖）
- [x] 公告中心实现（下拉刷新、compose-markdown MD 渲染、图片点击放大）
- [x] 设备列表实现（在线/离线状态、一键连接、远程踢出、查看设备信息）
- [x] OTA 更新实现（后台静默检查、前台弹窗下载、FLAG_INSTALL 引导安装、强制更新拦截返回键）
- [x] Android 14+ 后台限制保活策略实现 + 自启动引导（各厂商 ROM 适配）
- [x] 内存占用 <60MB，冷启动 <0.8s
- [x] `.github/workflows/android.yml` 存在，配置 push 到 main + 路径 `android/**` 触发
- [x] Phase 4 已 push 至 origin/main，GitHub Actions Android 构建 workflow 运行成功，arm64-v8a + armeabi-v7a APK（R8 兼容模式：minify+shrink+obfuscate）artifact 上传成功

## Phase 5 · 集成与高级功能

- [ ] WebRTC DataChannel 文件分片协议实现（单包 256KB、分片 ID+偏移量校验）
- [ ] SHA-256 哈希校验完整性实现
- [ ] 断点续传实现（记录至本地 DB）与传输队列管理实现
- [ ] 网页端文件管理器实现（目录树、上传/下载队列、路径选择、传输统计）
- [ ] Android 端 SAF 文件管理器完善
- [ ] 匿名连接流程实现（编号输入 + 设备端手动确认 单次/永久/拒绝/设备码放行）
- [ ] 同账号连接流程实现（设备自动发现 + 首次配对确认 + 免密直连）
- [ ] 全局安全策略实现（匿名/设备码/连接总开关服务端校验与实时同步）
- [ ] 自定义服务器地址覆盖热重载实现
- [ ] 控制指令时间戳防重放 + 设备码/编号非对称加密传输 + 本地配置 0600 权限实现
- [ ] 公告系统服务端实现（Markdown 解析、富文本工具栏、多条置顶、按平台/版本推送、阅读状态追踪、数字签名）
- [ ] 客户端分级日志实现（DEBUG/INFO/WARN/ERROR，一键打包导出）
- [ ] 崩溃捕获实现（Rust backtrace / Android 自研轻量 Crashlytics）上传至 /crash
- [ ] 服务端结构化日志实现（JSON 格式）
- [ ] Phase 5 已 push 至 origin/main，各端 CI workflow 运行成功

## Phase 6 · OTA、优化与交付

- [ ] 服务端 /ota 路由实现（文件上传、元数据入库、自动生成哈希、版本管理、强制标志下发）
- [ ] /ota CRUD、批量下架、下载量统计实现
- [ ] OTA 更新包 Ed25519 签名校验实现
- [ ] 客户端启动 + 每小时轮询 /api/ota/check 实现
- [ ] 客户端版本对比 + 强制更新锁定界面仅显示进度条 + 拦截返回键实现
- [ ] 客户端签名校验防篡改 + 失败自动回滚实现
- [ ] WebRTC 码率自适应算法优化实现（GCC + 滑块阈值联动）
- [ ] 内存泄漏排查与优化实现（WebRTC 轨道销毁、Compose 状态回收、Tauri GC 调优）
- [ ] 二进制体积优化实现（Go ldflags="-s -w"、Tauri 剔除无用依赖、Android ProGuard/R8 全开）
- [ ] 桌面端二进制 <8MB，Android APK <12MB，服务端 <15MB 达标
- [ ] 弱网测试验证通过（3G/高丢包/高延迟，重连/降码率/断线保活策略生效）
- [ ] 全链路压力测试验证通过（100+ 并发信令、多设备同时控制、大文件并发传输）
- [ ] zh-CN / en-US 全量字符串校对完成，无遗漏、语境准确
- [ ] 日期/数字格式化跟随 Locale，RTL 预留完成
- [ ] 部署文档编写完成（Linux Systemd / Win NSSM / Docker 可选、.env 配置说明）
- [ ] 用户手册编写完成（管理员后台、网页控制、桌面被控、安卓 App 全功能图文指南）
- [ ] 自动化打包流水线配置完成（GitHub Actions 编译 Win/Linux/Android/Static Web 全产物）
- [ ] 安全审计完成（渗透测试：信令劫持、XSS、越权、文件路径遍历、OTA 签名绕过）
- [ ] v1.0.0 发布，代码归档，Bug 追踪与反馈通道建立
- [ ] Phase 6 已 push 至 origin/main，CI 全产物构建成功，v1.0.0 release 归档完成

## 贯穿各 Phase · CI/CD

- [ ] 每个 Phase 完成后均执行 commit + push 至 origin/main
- [ ] 每个 Phase 的 GitHub Actions workflow 触发并构建成功
- [ ] 各端构建 artifact 均成功上传至 GitHub
