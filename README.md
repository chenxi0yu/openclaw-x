# OpenClaw X

> OpenClaw Android 客户端的个人分支：移动端 UI 全面重构 + 原生实时通知（小米超级岛 / Android 16 Live Updates）。
> An Android-focused fork of [OpenClaw](https://github.com/openclaw/openclaw) (MIT) — mobile UI overhaul and native Live Update notifications.

![运行状态上岛](fork-assets/00-live-island.png)

---

## 这是什么

[OpenClaw](https://github.com/openclaw/openclaw) 是把 AI 助手接到你自己设备与聊天渠道的开源项目（Gateway 服务端 + 多端客户端）。

**本仓库是它的 Android 客户端分支**，只做两件事：

1. **把移动端体验重做一遍**——材质、层级、动效、排版全部按 iOS 级标准重排；
2. **让 agent 的运行状态出现在系统级入口**——不依赖任何厂商白名单，用 Android 16 标准 Live Update 协议在小米超级岛 / 焦点通知上直接显示「响应中 / 思考中 / 生成中 / 已完成」。

仓库只保留 Android 相关工程（见[仓库范围](#仓库范围)），iOS / macOS / 服务端请去上游。

---

## 实时通知与超级岛

这是本分支最有价值的部分，也是踩坑最多的地方。

### 效果

Agent 开始运行时发出一条 promoted-ongoing 通知：左侧应用剪影图标，右侧状态词，底部不确定进度动画。在小米 HyperOS 上被识别为**焦点通知**并进入超级岛；在原生 Android 16 上表现为状态栏 chip 与锁屏实时更新。运行期间这条通知同时是**前台服务的保活本体**——回复生成全程连接不被冻结；空闲时通知收起，不再常驻一条"已连接"。

### 技术要点（可直接抄作业）

| 要点 | 说明 |
| --- | --- |
| **必须声明 `POST_PROMOTED_NOTIFICATIONS`** | `<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />`。缺它时 `setRequestPromotedOngoing()` 会被系统**静默忽略**——通知长得一模一样，但永远升不成实时活动。这是能否上岛的决定性条件。 |
| **用经典不确定进度条，不要用 `ProgressStyle`** | 小米焦点通知模板读取的是 `android.progress` / `android.progressIndeterminate` 这组标准 extras。Android 16 新增的 `Notification.ProgressStyle` 写的是另一套 extras 形状，模板不识别。`setProgress(0, 0, true)` 才是通吃的写法。 |
| **`setShortCriticalText()` 提供状态词** | API 36+。岛上和状态栏 chip 显示的短词来自这里，不是 `contentTitle`。 |
| **通知与前台服务共用同一 notification id** | 运行期间 `startForeground()` 直接采用这条通知，避免出现"状态通知 + 连接通知"两行；空闲时 `stopForeground(STOP_FOREGROUND_REMOVE)` 一起收掉。 |
| **提升必须发生在合法窗口内** | Android 12+ 拒绝从后台调用 `startForeground()`。对已降级的服务"事后补提升"会被拒——必须在动作发起的那一刻（用户点发送、App 还在前台）就 `startForegroundService()` 拿到 5 秒窗口。 |
| **提升失败要降级，不能让状态机哑掉** | 捕获提升异常后回落到普通通知继续更新。否则一次异常会永久杀死通知收集协程，之后所有状态都再也上不去。 |
| **断线不能清空运行状态** | 网络抖动触发的 `onDisconnected` 会把 pending run 保留到重连；运行状态也应保留（本仓库给 2 分钟上限），否则通知会随抖动消失。 |
| **小图标必须是透明底剪影** | 用自适应图标的 `monochrome` 层。拿整幅彩色图标当 `smallIcon`，系统压成剪影后就是一块纯色方块，岛上也像个方形贴片。 |

---

## UI 重构

### 1 · 输入栏毛玻璃

输入栏改为毛玻璃材质：半透明白底提亮 + 背景实时模糊，键盘弹起 / 收起时与页面内容自然叠合，不再是贴死在底部的实心色块。

![输入栏与 Markdown 排版](fork-assets/01-chat-markdown.png)

### 2 · 顶部渐进式模糊与胶囊按钮

顶栏使用渐进式模糊（向下渐隐，避免硬边），"新建会话"和"更多操作"从两个圆形按钮合并为**一枚胶囊**：两图标共用一个胶囊面，图标间距与边距按同一节奏对齐，带高光描边与投影。图标换用 Lucide 的 `square-pen` 与 `ellipsis`。

### 3 · 浮动卡片体系（Q 弹非线性动画）

模型列表、附件菜单、顶部菜单统一收敛到一套毛玻璃浮动卡片：`hazeState` 实时背景模糊 + 从锚点角落展开的**非线性 spring 动效**（阻尼 0.52 / 刚度 380），收尾带回弹。

关键修复：spring 过冲时内容会超出布局边界形成"两层不同步的白边"，用 `graphicsLayer { clip = true }` 把动画层裁到自身边界解决；卡片设 `focusable = false`，选择模型时不会抢焦点、输入法不会弹回。

### 4 · 侧边栏改为 push drawer

从悬浮式 overlay drawer 改为**推挤式**：抽屉滑出时页面整体平移、并随进度渐进压暗，页面元素逐级释放，不被挤压变形。左滑进入抽屉仅在会话页启用。

实现上交给 `ModalNavigationDrawer` 处理手势（自定义手势层在条件增删时会中途取消手势），用 `drawerState.currentOffset` 反向驱动内容 `translationX`。同时统一了抽屉内的视觉尺度：顶部"页面"折叠项与底部"最近"会话行使用同一套 26dp 圆角 + 12dp 内边距 + 36dp 图标框，宽度完全对齐。

![push drawer 侧边栏](fork-assets/02-sidebar-push-drawer.png)

### 5 · Lucide 图标体系

界面图标全面换用 [Lucide](https://lucide.dev)，以 path data 内置为 `ImageVector`（无运行时依赖、可主题着色），线宽与圆角风格统一。

### 6 · 消息气泡与 agent 回复样式

重写消息气泡：区分用户消息与 agent 回复的容器、间距与字号层级；工具行、思考行、正文使用同一套节奏，长回复不再糊成一片。

### 7 · MiSans 可变字重 + 排版层级

字体换用 MiSans（可变字重）。注意 MiSans 的视觉重量高于苹方——同样标 Bold 会明显更"黑"，因此标题层级下调一档到 SemiBold，并按 iOS 排版规范重排字号与负字距：

| 层级 | 字号 / 行高 | 字重 | 字距 |
| --- | --- | --- | --- |
| H1 | 26 / 32sp | SemiBold | -0.3sp |
| H2 | 22 / 28sp | SemiBold | -0.2sp |
| H3 | 19 / 25sp | Bold | — |
| 行内强调 | 正文 | Bold | — |

标题上方追加 16 / 12 / 8dp 的层级间距，正文与列表、代码块之间也有呼吸感；行内加粗保持 Bold 以在小字号下保住对比度。

### 8 · 上下文与思考强度卡片

模型列表、上下文用量、思考强度三处信息卡统一毛玻璃材质：上下文窗口占用进度条、最近一次运行的输入 / 输出 token 与预估费用、权限策略、默认模型与收藏星标，都在同一张卡里分层展示。

![上下文信息卡片](fork-assets/03-context-card.png)

![模型选择浮动卡片](fork-assets/04-model-picker.png)

---

## 功能增强

### 完整展示思考链与工具链

Agent 的多步执行不再被折叠成一行"正在工作"：思考段落、命令执行、文件写入、工具完成各自成行并保留状态词（思考完成 / 已执行 / 已写入 / 已完成），可展开查看细节，顶部聚合为"多个步骤 N"。

![思考链与工具链](fork-assets/05-reasoning-tools.png)

### 流式输出适配

同时兼容网关下发的累计快照（`streamText`）与增量分片（`deltaText`）两种形态，快照优先以避免同包内重复渲染；思考流与正文流分离缓冲，心跳包不会清空已有内容；生命周期结束事件先于正文到达时保留投影，避免回复"闪一下没了"。

### 输出性能优化

流式渲染走增量路径，状态更新按变化去重（避免每个 token 触发整树重绘与通知重发），长回复滚动稳定不掉帧。

### 模型选择与上下文信息解耦

原先模型切换和上下文用量挤在同一处，现在拆开：点输入栏的模型位弹出模型浮动卡（最近 / 模型分组 / 收藏），底部"更多信息"进入上下文与运行信息卡。选择模型不打断输入法状态。

### 公式渲染换用原生引擎

数学公式从 WebView 方案改为原生渲染，跟随系统主题与文字颜色，无白块闪烁、无额外 WebView 开销。

---

## 仓库范围

本仓库是上游 monorepo 的 **Android 子集快照**（不含上游历史）：

```
apps/android/                     # Android 应用（Kotlin + Jetpack Compose）
apps/shared/                      # 构建期需要的共享资源（tool-display、mermaid 产物）
packages/mermaid-renderer/        # 构建期依赖
packages/normalization-core/      # 构建期依赖
patches/                          # 构建补丁
LICENSE  THIRD_PARTY_NOTICES.md   # 上游许可与第三方声明（原样保留）
```

`apps/android/app/build.gradle.kts` 直接引用 `apps/shared`、`ui/public/provider-icons` 与 `packages/mermaid-renderer`，这些目录不可删。

## 构建

```bash
cd apps/android

# Debug
./gradlew app:assemblePlayDebug

# Release（单 ABI 打包，产物在 app/build/outputs/apk/play/release/）
./gradlew app:assemblePlayRelease \
  -PopenclawAbiFilters=arm64-v8a \
  -PopenclawBuildCommit=$(git rev-parse HEAD) \
  -PopenclawBuildTimestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
```

需要 JDK 17+ 与 Android SDK（`apps/android/local.properties` 里配 `sdk.dir`）。

**签名**：仓库不含任何密钥。发行构建通过命令行 `-POPENCLAW_ANDROID_STORE_PASSWORD=... -POPENCLAW_ANDROID_KEY_PASSWORD=...` 或 `~/.gradle/gradle.properties` 提供；`.gitignore` 已忽略 `*.keystore` / `*.jks` / `signing/`。

## 许可与声明

- 代码沿用上游 **MIT License**，版权声明与许可文本、`THIRD_PARTY_NOTICES.md` 均完整保留。
- 本项目为**个人非官方分支**，与 OpenClaw Foundation 无关联；"OpenClaw" 名称与标志的商标权利归原项目所有。
- 上游：https://github.com/openclaw/openclaw
