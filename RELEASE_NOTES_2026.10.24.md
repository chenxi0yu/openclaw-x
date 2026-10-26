# OpenClaw X 2026.10.24

Android-focused fork update. This release rolls up every change since `2026.10.13`.

## English

### Chat selection and sharing
- Long-press batch selection with a circular checkbox rail, tap-to-select message bodies, and process rows that shift with their message without reflowing text.
- Share panel with **Copy link**, **WeChat**, and **Copy text**.
- Share links are created lazily, only when Copy link or WeChat is used; WeChat receives a bare URL instead of a title + URL.
- Thinking/tool chain polish: Lucide sparkles for reasoning, thicker strokes, shimmering labels while running, and larger process icons.
- Attachment, details, overflow and model menus now use Miuix icons.

### HyperOS / Miuix settings
- About page: fixed gradient hero, static title that fades and scales away, progressive-blur top bar with glass controls, app/gateway/link/log cards, and a log-range morph menu.
- Settings home: large title, search pill, 24dp cards, colored HyperOS-style icon tiles, right-aligned status, row spacing increased, and a crossfade between the large and top-bar titles.
- New Share management page: shared conversation cards with URL, remaining time, copy-link, delete confirmation, and the same progressive-blur top bar.
- Sidebar: Miuix icons throughout, single-line recent sessions with origin icons, 12 collapsed rows, and cached pin order for cold starts.
- Agent picker: capsule-to-card morph menu with avatar/name rows and a Miuix check on the selected agent.

### Gateway profile
- The Account card now shows the Gateway's durable person profile instead of a local device name.
- Avatar, display name, and linked email, using the native `users.self` / `users.list`, `users.setDisplayName`, and `users.setAvatar` APIs plus the authenticated avatar route.
- Choose an avatar from the system photo picker; images are center-cropped to 512px and uploaded as WebP (≤512 KB).
- Profile metadata is cached for 5 minutes and avatars use the existing Room image cache; a refresh button forces an update.

### Typography and sessions
- Fixed HyperOS flattening every font weight: the variable MiSans font was replaced with four static weights, with weight labels offset against the system `fontWeightAdjustment`. The APK grows to about 46 MB because of the four font files.
- A new chat stays out of the session list until its first message is sent.
- The recent session list now shows 12 rows when collapsed.

### Notes
- arm64 only, Android 12+.
- Profile edits require an operator with write/admin access. The current Gateway API can replace an avatar but cannot remove one.

## 中文

### 对话选择与分享
- 长按进入批量选择：左侧圆形勾选框、点击消息本身也能选中，思考/工具等过程行跟随消息一起位移，正文不重新换行。
- 分享面板包含 **复制链接**、**微信好友**、**复制文本**。
- 只有点击复制链接或微信好友时才真正创建分享；微信分享只发送裸 URL，不再附带标题。
- 思考/工具链优化：思考图标改为 Lucide sparkles、线条加粗、运行中文字扫光、过程图标整体放大。
- 附件、详情、更多菜单和模型菜单统一为 Miuix 图标。

### HyperOS / Miuix 设置界面
- 关于页：固定渐变背景、静态标题淡出缩小、渐进模糊顶栏 + 玻璃按钮，应用/网关/链接/日志卡片，日志范围 morph 菜单。
- 设置一级页：大标题、搜索胶囊、24dp 卡片、HyperOS 风格彩色图标块、右侧状态、行距加大、大标题与顶栏标题交叉淡入淡出。
- 新增分享管理页：分享对话卡片显示链接和剩余时间，支持复制链接、删除确认，顶栏与设置一级页一致。
- 侧边栏：全面换成 Miuix 图标；最近会话单行标题 + 来源图标；折叠状态显示 12 条；置顶顺序冷启动缓存。
- 智能体选择：胶囊展开为玻璃卡片的 morph 菜单，行内头像 + 名字，选中项显示 Miuix 勾。

### 网关个人资料
- 设置顶部账号卡改为网关的持久个人资料，不再使用本机设备名。
- 支持头像、显示名字、关联邮箱；使用原生 `users.self` / `users.list`、`users.setDisplayName`、`users.setAvatar` 及认证头像路由。
- 从系统照片选择器选择头像，本地居中裁成 512px 并以 WebP 上传（≤512KB）。
- 资料元数据缓存 5 分钟，头像使用现有 Room 图片缓存；详情页刷新按钮可强制更新。

### 字体与会话
- 修复澎湃 OS 把全部字重拍平的问题：可变 MiSans 改为四个静态字重文件，并用系统 `fontWeightAdjustment` 反向偏置声明权重。APK 因四份字体增大到约 46MB。
- 新建会话在发送首轮消息前不进入会话列表。
- 最近会话折叠状态显示 12 条。

### 说明
- 仅提供 arm64 版本，要求 Android 12+。
- 修改个人资料需要具有写/管理权限的 operator；当前网关 API 只能替换头像，不能删除头像。

---

Full changelog: https://github.com/chenxi0yu/openclaw-x/compare/v2026.10.13...v2026.10.24
