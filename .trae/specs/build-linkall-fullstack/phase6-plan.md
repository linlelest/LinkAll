# Phase 6 重构计划：桌面端 + 服务端首次启动初始化

> 本计划聚焦三项重构，不涉及 Phase 6 原有的 OTA/优化/交付任务（那些将合并为 Phase 7 另行规划）。

---

## A. 桌面端 UI 重构：被控端 + 控制端

### 现状问题
当前 [desktop/frontend/index.html](file:///c:/Users/Kevin/Music/LinkALL/desktop/frontend/index.html) 是单页堆叠布局：账户/设备信息/服务器/服务控制/视频设置/系统全部垂直排列在一个长页面，功能密度高、层次不清晰、无法扩展控制端能力。纯 HTML+JS 无组件化能力，难以承载"被控+控制"双模式。

### 目标
- 重构为三页签导航（`被控` | `控制` | `设置`），对齐 Android 端的设计语言
- 被控页：服务状态、设备身份、连接请求确认、防窥屏开关
- 控制页：设备编号/设备码输入 → 全屏沉浸式控制画布（与 Web/Android 控制页功能等价）
- 设置页：账户/服务器/视频编码/系统（开机自启/日志导出等）
- 控制页虚拟工具层：虚拟鼠标（相对/绝对模式）、虚拟键盘、滚轮、左右键、缩放/码率/帧率抽屉、防窥屏开关、文件传输入口

### 技术方案
保持 Tauri 2 前端纯 HTML+JS（不引入 Svelte/React 框架，符合"极轻量"原则），但采用：
- **多页面分区**：通过 `data-view` 切换的 SPA 路由，单 `index.html` + 多 JS 模块
- **CSS Grid/Flex 布局**：顶部 TabBar + 主内容区，TabBar 固定，内容区切换
- **模块化 JS**：将现有 `app.js` 拆分为 `views/controlled.js`、`views/control.js`、`views/settings.js`、`views/connect.js`、`lib/webrtc.js`、`lib/api.js`、`lib/i18n.js` 等 ES Module
- **新增 Tauri 命令**（[commands.rs](file:///c:/Users/Kevin/Music/LinkALL/desktop/src-tauri/src/commands.rs)）：
  - `connect_to_device(device_id, device_code, mode)` — 作为控制端发起连接
  - `send_control_event(event_json)` — 发送键鼠/滚轮/手势事件
  - `get_peer_stats()` — 获取 RTT/丢包率/帧率
  - `disconnect_peer()` — 断开控制会话
- **新增 Rust 模块**（`src/control/`）：客户端 WebRTC PeerSession + 信令客户端 + DataChannel 控制指令发送 + 远端视频流接收（Surface 渲染）
- **双语 i18n**：新增 `frontend/i18n/zh-CN.json` 和 `en-US.json`，JS 运行时切换

### 文件结构
```
desktop/frontend/
├── index.html              # 主框架 + 顶部 TabBar
├── styles.css              # 重构的样式
├── i18n/
│   ├── zh-CN.json
│   └── en-US.json
├── views/
│   ├── controlled.js       # 被控页：服务状态/设备身份/连接请求
│   ├── control.js         # 控制页入口：连接表单 → 控制画布
│   └── settings.js         # 设置页：账户/服务器/视频/系统
├── control/
│   ├── connect-form.js    # 设备编号/码输入 + 模式切换
│   ├── canvas.js          # 控制画布（video + 虚拟工具层）
│   ├── virtual-mouse.js   # 虚拟鼠标
│   ├── virtual-keyboard.js
│   ├── wheel-slider.js
│   └── settings-drawer.js # 缩放/码率/帧率抽屉
├── lib/
│   ├── api.js             # Tauri invoke 封装
│   ├── webrtc.js          # 控制端 WebRTC 客户端
│   ├── i18n.js            # i18n 运行时
│   └── toast.js           # 轻量 Toast
└── app.js                 # 入口：路由分发 + 全局状态
```

---

## B. 服务端首次启动管理员创建流程

### 现状问题
当前首次启动需在终端执行 `./remote-server init` 命令行交互创建管理员。用户期望网页端首次访问时强制弹出管理员创建页面。

### 目标
- 服务端启动时检查数据库是否已有 `superadmin` 账户
- 若无：标记 `needsSetup = true`，开放 `POST /api/setup/init` 路由（仅在未初始化时可用）
- 网页端任何路径首次访问时：调用 `GET /api/setup/status` 检查，若 `needsSetup=true` 则强制渲染 `Setup.svelte` 页面（输入用户名/密码/二次确认），完成创建后跳转登录
- 创建完成后：`/api/setup/init` 路由立即禁用，防止二次创建

### 技术方案
- **服务端新增** `internal/handlers/setup.go`：
  - `GET /api/setup/status` — 返回 `{needsSetup: bool}`
  - `POST /api/setup/init` — 接收 `{username, password}`，Argon2id 哈希后写入 `users` 表（role=superadmin, status=active），成功后设置内存标志位
  - 使用 `sync.Once` 或 atomic 标志确保 `init` 仅可调用一次（即使数据库为空也拒绝二次创建）
- **服务端 main.go 启动时**：查询 `SELECT COUNT(*) FROM users WHERE role='superadmin'`，若为 0 则设置 `needsSetup=true` 并打印 banner 提示"请通过网页端完成初始化"
- **网页端新增** `routes/Setup.svelte`：全屏卡片式 UI，用户名 + 密码 + 二次确认 + 服务条款确认，完成后自动用新账户登录并跳转 Dashboard
- **App.svelte 路由守卫**：在 `login` 判断之前先调用 `/api/setup/status`，若 `needsSetup` 则强制跳 `setup`，不论当前路由
- **i18n**：新增 setup 相关字符串（zh-CN/en-US）

### 文件改动
- 新增 `server/internal/handlers/setup.go`
- 修改 `server/internal/handlers/router.go` — 注册 setup 路由（无需鉴权，但仅在未初始化时可用）
- 修改 `server/cmd/main.go` — 启动时检查 superadmin 数量
- 新增 `web/src/routes/Setup.svelte`
- 修改 `web/src/App.svelte` — 路由守卫增加 setup 检查
- 修改 `web/src/lib/api/client.ts` — 新增 `getSetupStatus()` / `initSetup()` 方法
- 修改 `web/src/lib/i18n/zh-CN.json` 和 `en-US.json` — 新增 setup 字符串

---

## C. 执行顺序

1. **服务端首次启动 setup**（Task 6.A.1 - 6.A.4）— 服务端先行，网页端依赖
2. **网页端 setup 页面**（Task 6.B.1 - 6.B.3）— 依赖服务端 setup API
3. **桌面端 UI 重构**（Task 6.C.1 - 6.C.6）— 独立，可与 1-2 并行
4. **CI 验证 + push**（Task 6.D.1）

---

## 任务清单

### Task 6.A：服务端首次启动初始化 API
- [ ] 6.A.1 新增 `server/internal/handlers/setup.go`：`GET /api/setup/status` + `POST /api/setup/init`，atomic 标志防二次创建
- [ ] 6.A.2 修改 `router.go`：注册 setup 路由（公开，但 init 仅在 needsSetup=true 时可用）
- [ ] 6.A.3 修改 `cmd/main.go`：启动时查询 superadmin 数量，banner 显示初始化状态
- [ ] 6.A.4 修改 `shared/errors.json`：新增 setup 相关错误码（`E_SETUP_ALREADY_DONE` / `E_SETUP_INVALID_INPUT`）

### Task 6.B：网页端 Setup 页面与路由守卫
- [ ] 6.B.1 新增 `web/src/routes/Setup.svelte`：用户名 + 密码 + 二次确认 + 提交，完成后自动登录
- [ ] 6.B.2 修改 `web/src/App.svelte`：路由守卫在 login 前检查 `/api/setup/status`，needsSetup=true 时强制渲染 Setup
- [ ] 6.B.3 修改 `web/src/lib/api/client.ts`：新增 `getSetupStatus()` / `initSetup(username, password)`；更新 i18n 字符串

### Task 6.C：桌面端 UI 重构（被控 + 控制双模式）
- [ ] 6.C.1 重构 `desktop/frontend/index.html` + `styles.css`：顶部 TabBar（被控/控制/设置）三视图切换框架
- [ ] 6.C.2 拆分 `app.js` 为 ES Modules：`views/` + `control/` + `lib/`，新增 i18n 双语
- [ ] 6.C.3 新增 Rust 控制端模块 `desktop/src-tauri/src/control/`：客户端 WebRTC PeerSession + 信令 + DataChannel 控制指令
- [ ] 6.C.4 新增 Tauri 命令：`connect_to_device` / `send_control_event` / `get_peer_stats` / `disconnect_peer`
- [ ] 6.C.5 实现控制页：连接表单 → 全屏控制画布（虚拟鼠标/键盘/滚轮/左右键 + 缩放/码率/帧率抽屉 + 防窥屏 + 文件传输入口）
- [ ] 6.C.6 实现被控页：服务状态/设备身份/连接请求确认弹窗/防窥屏开关

### Task 6.D：CI 验证
- [ ] 6.D.1 commit + push，等待 server/desktop CI 通过
