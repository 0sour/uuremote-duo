# DuoRemote 实施计划

> 版本：v0.2（2026-11-12）
> 依据：[research.md](research.md)（协议/架构）、[ui/tokens.md](ui/tokens.md)（视觉 token）、
> [ui/spec.md](ui/spec.md)（行为/状态/验收）。
> 范围：v0.1 可日常使用的非官方 UU 远程客户端，适配 Retroid Pocket Duo 双屏与单屏横屏。

---

## 1. 目标与非目标

### 目标

- 登录：扫码 / 短信验证码，token 加密保存。
- 设备：设备列表、在线状态、详情、刷新。
- 串流：加入房间、Engine.IO 信令、WebRTC H.264 视频 + Opus 音频。
- 输入：触摸板、内置拼音键盘、系统输入法按钮、Xbox 手柄直通/本地、快捷键。
- 双屏：上屏视频/详情，下屏列表/键盘/触摸板/设置。
- 单屏横屏：上屏 Activity 或下屏 Presentation 均可运行。
- 稳定性：失败可重试、断线重连、输入不卡键、资源可释放。
- 安全：签名 key/token 本地加密，仓库不含 key/APK/日志。

### 非目标（v0.1 不做）

- 竖屏布局。
- 文件传输、剪贴板同步、远程开机、多显示器切换、应用启动。
- H.265/AV1 编码。
- 官方账号体系之外的绕过/风控规避。
- 手机端完整适配（仅保证 Duo 横屏；手机不作为验收目标）。

---

## 2. 当前基线

| 项 | 状态 |
| --- | --- |
| 工程 | Gradle/Compose：AGP 8.4.0、Kotlin 1.9.23、compileSdk 34、minSdk 26、Compose BOM 2024.04.01 |
| 依赖 | WebRTC `io.github.webrtc-sdk:android:125.6422.07`、OkHttp、Gson、ZXing、security-crypto |
| 构建 | 2026-11-14 最新代码 `clean assembleDebug :app:testDebugUnitTest` 通过（52 个单测）；APK 位于 `app/build/outputs/apk/debug/` |
| UI | 已按 `docs/ui/` 逐页 1:1 重做：双/单屏主页、双/单屏设置、串流工具箱、本地菜单、IME、登录、账号页；无法完全还原处（缺账号名/在线时长等数据）按 bili-duo 设计语言降级 |
| 协议 | 签名/登录/设备/房间/信令/WebRTC 真机连通；手柄直通 `gpd_*`；画质/帧率/码率；`text_input` + SendToRom 兜底；文字剪贴板 TEXT 通道 RPC |
| 输入 | 触摸板（双指滚动/右键、长按拖拽）、内置拼音（长按连发/符号页）、系统 IME、快捷键、直接触摸视频；手柄 XInput 直通、死区/轴反向、本地 Tab/内容两级焦点；断线/菜单/切页 release-all |
| 稳定性 | 3 次自动重连（2/4/8s）+ 5s 断线看门狗；本地菜单/IME/设置抑制直通并释放输入 |
| 发布 | `keystore.properties`/环境变量签名、GitHub Actions、[RELEASING.md](RELEASING.md) |
| 文档 | `docs/research.md`、`docs/ui/tokens.md`、`docs/ui/spec.md`、`docs/feature-matrix.md`、本计划 |

> 构建环境注意：当前 PATH 无 `java`；已验证 JDK 位于
> `C:\path\to\jdk-17`。Gradle wrapper 为 8.7；如 AGP/Compose 版本冲突，优先固定
> JDK 17 + Gradle 8.7，不盲目升级。本机 `~/.gradle/gradle.properties` 配了 127.0.0.1:<proxy-port>
> 代理；代理未启动时用 `-Dhttp.proxyHost= ...` 绕过，见 [BUILDING.md](BUILDING.md)。
>
> M0 进度：双/单屏宿主、Presentation、Compose token、焦点环、手动 D-pad 选择已落地；
> 双屏/单屏上屏/单屏下屏已在 Duo 上完成首轮目视与切换验证。手动 D-pad 的最终按键验收
> 待设备空闲时进行。

---

## 3. 模块划分

```text
app/src/main/java/com/sour/duoremote/
├─ app/
│  ├─ DuoRemoteApp.kt              Application、全局容器
│  └─ MainActivity.kt              单 Activity 入口、横屏、宿主选择
├─ ui/
│  ├─ theme/                       Color/Typography/Spacing/Radius → Compose token
│  ├─ components/                  DeviceCard、FocusRing、Chip、PadButton、StatCard…
│  ├─ host/                        TopHost、SingleHost、DuoPresentation、HostCoordinator
│  ├─ screens/
│  │  ├─ keysetup/                 签名材料导入/提取
│  │  ├─ login/                    QR / SMS
│  │  ├─ home/                     设备列表 + 详情
│  │  ├─ settings/                 分类/选项/恢复默认
│  │  └─ streaming/                视频、抽屉、工具箱、本地菜单
│  ├─ keyboard/                    BuiltinKeyboard、CandidateBar、PinyinEngine、ImeController
│  └─ input/                       InputRouter、GamepadRouter、FocusTree、KeyRepeat
├─ session/
│  ├─ SessionViewModel.kt          Activity-scoped
│  ├─ SessionStateMachine.kt       Idle/Joining/.../Streaming/Reconnecting/Failed
│  └─ SessionController.kt         编排 API/信令/RTC/输入
├─ protocol/
│  ├─ api/                         UuApiClient、RequestSigner、AuthRepository、DeviceRepository
│  ├─ signaling/                   EngineIoClient、SignalingClient、SocketFrames
│  ├─ rtc/                         UuRtcClient、PeerFactory、DataChannels、CodecPolicy
│  ├─ input/                       MouseSender、KeyboardSender、TextSender、GamepadSender
│  └─ proto/                       ConnectOptions writer、最小 varint/len-delimited
├─ data/
│  ├─ SettingsStore.kt             DataStore
│  ├─ SecureStore.kt               EncryptedSharedPreferences（token/key）
│  └─ models/                      Device、SessionConfig、InputState
└─ diagnostics/
   ├─ StatsCollector.kt
   └─ SanitizedLogger.kt           脱敏日志，默认关闭
```

资源：

- `assets/pinyin_dict.txt`：本地拼音词典（词/拼音/词频），构建时校验大小与 license。
- `res/`：图标、主题、字符串；所有用户可见文案进 `strings.xml`，便于审校。

---

## 4. 阶段计划

> 单人估算，含真机联调；不含协议大改。总工期约 14–21 天，建议预留 20–30% buffer。

### M0 脚手架与宿主骨架（1–2 天）

| # | 任务 | 产出 |
| --- | --- | --- |
| 0.1 | 修复构建：JDK/Gradle/AGP/Compose/WebRTC 依赖，跑通 `assembleDebug` | 可安装 APK |
| 0.2 | Compose token：颜色/字体/间距/圆角/焦点环 | `ui/theme` |
| 0.3 | `MainActivity` 横屏、edge-to-edge、显示枚举 | 单 Activity 启动 |
| 0.4 | `DuoPresentation`（Lifecycle/ViewModelStore/SavedState/BackDispatcher owner） | 下屏可显示 Compose |
| 0.5 | `HostCoordinator`：双屏 / 单屏上屏 / 单屏下屏；黑色宿主 | 三种宿主可切换 |
| 0.6 | `InputRouter` + `GamepadRouter` 骨架；normalizeFaceKey；焦点树 | 手柄可导航 |
| 0.7 | 最小页面：主页/设置/登录占位；返回栈 | 可点通 |

**验收**：MuMu/Duo 上双屏显示不同内容；单屏上/下屏均可启动；手柄移动焦点；Back 正常；无 Dialog in Presentation。

### M1 签名材料、登录、设备列表（2–3 天）

| # | 任务 | 产出 |
| --- | --- | --- |
| 1.1 | APK 选择/读取 `classes*.dex`、提取 24 字节 key、SHA-256 指纹校验 | KeyImporter |
| 1.2 | EncryptedSharedPreferences 保存 key/token；错误与重试 | SecureStore |
| 1.3 | UU API 客户端 + HMAC-SHA256 签名 + 错误映射 | `UuApiClient` |
| 1.4 | 扫码登录：二维码获取、轮询、过期、成功 | QR UI + AuthRepository |
| 1.5 | 短信登录：发送、倒计时、校验、登录 | SMS UI |
| 1.6 | 设备列表/详情/刷新/离线/空态 | Home UI |
| 1.7 | SettingsStore：屏幕模式、单屏宿主、画质等默认值 | DataStore |
| 1.8 | 单元测试：签名、URL/参数、错误映射、key 指纹 | JVM tests |

**验收**：真账号登录；重启保持登录；设备列表正确；无 key/token 日志；错误可重试。

> M1 进度（2026-11-11 真机）：
> - `DexKeyExtractor`（DEX string table + SHA-256 指纹）、`SecureStore`、`RequestSigner` 已完成，单测通过。
> - 真机已验证：SAF 选择官方 TV APK → 提取 → 加密保存；重启后无需重新导入。
> - 真机已验证：API init + 扫码二维码生成/刷新，说明签名头与网络链路正常。
> - 扫码/短信登录 UI、`getDevices` 解析、设备列表接入主页已实现；扫码后的 token 流程与
>   真实设备列表仍需账号扫码验证；短信发送为避免打扰用户未触发。
> - `SettingsStore`（DataStore）已接入：屏幕模式/单屏宿主/播放/手柄/触摸板默认值可持久化；
>   宿主切换会写回设置。
> - 双屏登录已拆分：上屏 `LoginTopPane`（二维码/短信提示），下屏 `LoginBottomPane`
>   （步骤、状态、刷新、短信入口/表单），共享 `LoginController`；单屏复用
>   `LoginSingleScreen`，单屏下屏由 Presentation 承载。
> - 真机验证：双屏扫码页上下同屏显示，二维码/倒计时/短信模式在两屏同步；短信表单
>   在 640×480dp 下不溢出。Y/X/A 登录快捷键已在 Activity、Presentation、Compose
>   三层接入。
> - 手柄焦点环已落地：登录页默认选中主操作，↑↓ 移动，A 确认/进入输入，B 返回；
>   扫码→短信切换、短信字段焦点、退出输入均已在真机验证。签名导入页同样有默认焦点环
>   和 A/B 操作。部分手柄会同时上报 `BUTTON_A` 与合成 `DPAD_CENTER`，登录页已吞掉
>   合成点击，避免重复触发或误点内部焦点控件。
> - 剩余：扫码端到端验收、设备列表真实数据校验；单屏登录布局与签名导入页焦点环待目视验收。

### M2 协议打通：加入房间 + 信令 + WebRTC（3–5 天）

> 关键路径。建议先做一个最小调试页（单 Activity 全屏视频），验证通过再接双屏 UI。
>
> 进度（2026-11-11）：
> - `UuApiClient.joinRoom()`：`POST /api/v1/room/join/by_device/{id}`，解析房间 `token`、
>   `signaling_list` / `signaling_server`、`iceServers`、`ws_connect_timeout_ms`。
> - `SignalingEndpoint`：校验只允许 `wss://*.163.com`，固定 Socket.IO 路径；单测覆盖
>   bare host、带端口、signaling_list 回退与恶意主机拒绝。
> - `SocketIoCodec` + `SignalingClient`：Engine.IO 握手、ping/pong、`40`、
>   Socket.IO event/ack、`45x-` 二进制附件、`0x04` 前缀、room_info/control 请求、
>   released/left；单测 + MockWebServer 集成测试已跑通（握手→room_info→control→二进制）。
> - `ConnectOptions` protobuf writer：Main/Capture/Decoder/Resolution，1080p60 与 720p30
>   结构单测通过。
> - `SoacCodec`：offer/answer/candidate（candidate 嵌在 `data.candidate`），单测通过。
> - `UuRtcClient`：PeerConnectionFactory、recvonly H.264/Opus、五条有序数据通道、
>   offer/answer、候选缓存、ICE/连接状态、远程视频轨道回调；`RtcRuntime` 共享 EglBase。
> - `SessionController`：join room → signaling → room_info → control(ConnectOptions) →
>   PeerConnection → SOAC；主页 B / 连接按钮已接线，状态显示在主页/详情页头部。
> - 真机已验证（2026-11-11）：真实账号扫码登录、token 持久化、真实设备列表；选择在线电脑
>   按 B 后完成房间加入 → 信令 → 控制权 → WebRTC；上屏 Surface 显示 1920×1080 远端桌面，
>   下屏显示“串流进行中/串流通道已建立”，B/A 断开后安全回到设备列表，可重复连接。
> - 已修复：控制权请求必须用 Socket.IO type-5（`451-<id>`）携带二进制附件，普通 `42` 加
>   二进制帧会被服务端断开。
> - 已修复：增量构建可能把旧 APK 打包进来，导致“点击连接没反应”；安装前必须 clean 重建并
>   校验 APK。
> - 已完成（2026-11-12）：下屏工具箱（触摸板/键盘/拼音/快捷键/状态）、本地菜单、
>   单屏上屏/下屏串流与抽屉、亮/暗主题、设置持久化与重置、下屏系统 IME（Gboard 实机弹出）、
>   主页 B 连接 / 单屏底部宿主 / 工具箱 Tab 焦点环。
> - UI 1:1 重做（2026-11-12）：按 `mockup.css` 的数值重写主页（上屏 60/40 分栏 + 准备远控卡、
>   下屏 44dp 顶栏 + 76dp 设备卡 + 徽标/chevron）、串流工具箱（44dp 工具条 + 紧凑 Tab +
>   点阵触摸板 + 左下主键/拖拽/齿轮）、本地菜单（430dp 卡片、B 确认键帽）、内置/系统 IME
>   （44dp 输入框、42dp 候选条、预览卡、40/44dp 键帽）、设置（上屏配置摘要+焦点预览、下屏
>   图标分类+54dp 行）与登录（上屏二维码/短信说明、下屏表单+数字键盘）。所有本地确认统一为
>   实体 B、返回统一为实体 A；合成 `DPAD_CENTER` 全局吞掉。
> - 降级项：账号名/在线时长/局域网信息等 API 未提供，用图标或“—”代替；设置保留 8 个分类
>   （比设计多“触摸板/键盘”），单屏设置/登录复用同一套响应式布局；系统 IME 为真实 Gboard
>   面板而非设计稿的示意图。
> - 尚未实现/未接入：手柄直通 `gpd_connect/update/disconnect`、工具箱内容级焦点
>   （触摸板按键/快捷键卡片）、远端音频播放、直接触摸视频、断线自动重连。

| # | 任务 | 产出 |
| --- | --- | --- |
| 2.1 | Engine.IO v4 WebSocket：握手、ping/pong、binary attachment | `EngineIoClient` |
| 2.2 | 加入房间、`room_info`/`control`/`soac`/`released` 事件 | `SignalingClient` |
| 2.3 | ConnectOptions protobuf（capture params/decoder/client_type） | `ConnectOptionsWriter` |
| 2.4 | PeerConnection：H.264/Opus、五个数据通道、ICE | `UuRtcClient` |
| 2.5 | 首帧状态、SurfaceViewRenderer、音频轨道 | 最小串流页 |
| 2.6 | 统计：FPS/码率/RTT/丢包/编解码 | StatsCollector |
| 2.7 | 超时、取消、释放、重连（ICE restart） | 状态机 |
| 2.8 | 真电脑 1080p60 验证；720p30 降级 | 验收记录 |

**验收**：真电脑出画面/声音；1080p60 稳定；取消/断开无泄漏；断网可重连或明确失败。

### M3 输入协议与中文输入（3–4 天）

| # | 任务 | 产出 |
| --- | --- | --- |
| 3.1 | 触摸板：绝对/相对移动、点击、滚轮、拖拽、双指 | MouseSender |
| 3.2 | 键盘：VK 映射、修饰键粘滞/锁定、长按连发、release all | KeyboardSender |
| 3.3 | 内置拼音引擎：词典加载、切分、候选、翻页 | PinyinEngine |
| 3.4 | 键盘 UI：中/英键、候选条、浮层、输入行展开 | BuiltinKeyboard |
| 3.5 | 文本发送：`text_input`、失败回退、组合/退格/标点 | TextSender |
| 3.6 | 系统输入法：隐藏 EditText、commitText 转发、收起恢复 | ImeController |
| 3.7 | 手柄：`gpd_connect/update/disconnect`、位掩码、轴/扳机、振动 | GamepadSender |
| 3.8 | 三态归属：直通/本地/临时菜单；零值→断开→本地 | GamepadRouter |
| 3.9 | 单元测试：VK/XInput/拼音候选/状态转换 | JVM tests |

**验收**：远端文本编辑器可输入中文/英文；退格/回车/标点正确；手柄游戏可用；切换模式无卡键。

### M4 双屏/单屏 UI 集成（3–4 天）

| # | 任务 | 产出 |
| --- | --- | --- |
| 4.1 | 双屏主页：下屏列表/焦点环 + 上屏详情 | Dual Home |
| 4.2 | 双屏串流：上屏视频 + 下屏工具箱四 Tab | Dual Stream |
| 4.3 | 单屏横屏主页：master-detail；上/下屏宿主 | Single Home |
| 4.4 | 单屏串流：视频 + 抽屉 42%–68% + 悬浮按钮 | Single Stream |
| 4.5 | 本地菜单 overlay；设置页；登录页双/单布局 | Overlays |
| 4.6 | 键盘页/浮层/系统 IME 在双屏与单屏的适配 | IME UI |
| 4.7 | 宿主生命周期：下屏断开/新增、Activity 重建、焦点重建 | HostCoordinator |
| 4.8 | 状态矩阵：加载/空/离线/错误/重连/首帧 | State UI |

**验收**：Duo 真机完成双屏/单屏主页→连接→触摸板→中文输入→手柄直通/本地→断开全流程；下屏断开不丢会话。

### M5 收尾与发布（2–3 天）

| # | 任务 | 产出 |
| --- | --- | --- |
| 5.1 | 设置全部生效/恢复默认；主题亮暗 | Settings |
| 5.2 | 错误矩阵、诊断脱敏、日志开关 | Diagnostics |
| 5.3 | 无障碍：焦点环/描述/字号/对比度 | A11y pass |
| 5.4 | 性能：首帧/延迟/内存/发热；720p30 档 | Perf notes |
| 5.5 | README、GPL-3.0、非官方声明、构建说明 | Release docs |
| 5.6 | release 签名、R8、无 key/APK/日志检查 | v0.1 APK |

**验收**：可日常使用；公开仓库不含敏感文件；安装/升级/卸载正常。

---

## 5. 关键路径与依赖

```text
M0 构建/宿主
   └─ M1 key/登录/设备
        └─ M2 协议/WebRTC  ← 最大不确定性，优先验证
             └─ M3 输入
                  └─ M4 UI 集成
                       └─ M5 收尾
```

- M2 不依赖完整 UI；M0 完成后即可并行做 M1 与最小串流调试页。
- M3 的拼音引擎可与 M2 并行开发（纯本地模块）。
- M4 依赖 M1/M2/M3 的稳定接口；之前只做静态 UI/导航。
- 设计文档已就绪，不再是阻塞项；若实现中发现状态缺口，先改 `spec.md` 再改代码。

---

## 6. 测试计划

### 6.1 单元测试（JVM）

- HMAC 签名、参数排序、URL 编码、时间戳。
- ConnectOptions varint/len-delimited 编码。
- VK 映射、修饰键状态、XInput 位掩码/轴归一化。
- 拼音切分、候选排序、翻页、最大长度。
- 会话状态机：超时/取消/失败/重连转换。
- 设置序列化/默认值/恢复默认。
- 日志脱敏：token/key/SDP/IP/手机号不出现。

### 6.2 集成/协议

- Engine.IO 帧解析（文本/二进制/ping/pong/attachment）。
- 信令事件顺序：`room_info → control → soac → first frame`。
- 数据通道打开/关闭/重连。
- 取消/异常路径资源释放（socket/PeerConnection/tracks/channels）。
- 可用录制帧或 mock server 做离线回归；真实电脑做最终验证。

### 6.3 真机（Duo）

- 双屏：上屏详情/视频，下屏列表/工具箱；焦点不跨屏。
- 单屏上屏 / 单屏下屏：主页、串流、抽屉、IME、Back。
- 手柄：标准/Retro 风格；直通/本地/临时菜单；卡键检查。
- 键盘：内置拼音中/英、候选、退格、回车、标点；系统 IME 唤起/收起。
- 显示热插拔：下屏移除/新增；Activity 重建；锁屏/回前台。
- 网络：断网、弱网、重连、首帧超时、服务端 released。

### 6.4 UI/视觉

- 截图对比关键页（已有 `docs/ui/` 基线）。
- 焦点遍历顺序、焦点环不被裁切、滚动可见。
- 字体缩放 1.0–1.3；亮/暗主题对比度。
- 触摸目标 ≥44dp；键盘键帽 ≥38dp。

### 6.5 安全

- `grep` 仓库确认无 key/APK/token/日志。
- 日志输出审计：二维码内容、SDP、IP、手机号。
- 首次运行 APK 解析后临时文件删除。
- release 包不含 debug 签名/诊断开关默认关闭。

---

## 7. 风险与应对

| 风险 | 概率/影响 | 应对 |
| --- | --- | --- |
| WebRTC 与官方不互通 | 中/高 | M2 最早验证；先照抄 NS/uux 参数；H.264 only；保留官方 libstreamer 备选 |
| 签名 key 提取失败/版本变化 | 中/高 | 指纹校验；只支持 TV 3.5.0；错误提示；关注上游更新 |
| 下屏 Presentation 的 IME/Back 异常 | 高/中 | 复用 bili-duo 模式；双接 Back + 去抖；真机逐项验收；内置拼音兜底 |
| 系统输入法不在副屏显示 | 已缓解 | 真机实测 Gboard 可在下屏 Presentation 显示；需先触摸输入框，让下屏获得焦点。内置拼音始终可用 |
| 双屏输入焦点跑向上屏 | 高/中 | Activity 与 Presentation 都路由同一套主页/串流按键；合成 `DPAD_CENTER` 全局吞掉，避免误触底层控件 |
| 手柄风格/轴方向错 | 中/中 | 入口归一化；设置里轴反向/死区；远端控制器测试页校准 |
| 输入卡键 | 中/高 | release-all；零值→`gpd_disconnect`；切页/断线统一清理；单元测试 |
| 性能/发热 | 中/中 | 720p30 档；硬解；统计；降码率优先于降输入 |
| 账号/ToS 风险 | 中/高 | 非官方声明；只连自己的电脑；不绕过付费/风控；用户知情 |
| 构建工具链不稳 | 中/低 | 固定 JDK 17 + Gradle 8.7；M0 先跑通；锁定依赖版本 |

---

## 8. 完成定义（DoD）

- 所有 M0–M5 验收项通过；真机全流程可重复。
- 无 P0/P1 崩溃；无卡键/黑屏不可恢复；错误有明确下一步。
- 关键路径有单元测试；协议/输入映射有回归用例。
- 文档与实现一致：`spec.md` 状态/文案/默认值与代码一致。
- 仓库不含 key/APK/token/签名文件/日志；GPL-3.0 与非官方声明齐全。
- release APK 可安装、可升级、可卸载。

---

## 9. 立即开始（M0 清单）

1. 用 JDK 17 跑 `./gradlew :app:assembleDebug`，修复版本/依赖/资源错误。
2. 建立 `ui/theme`：把 `tokens.md` 的颜色/字体/间距/圆角映射为 Compose。
3. 实现 `MainActivity` 横屏 + `HostCoordinator` + `DuoPresentation` 空壳。
4. 实现 `GamepadRouter` 骨架与焦点环组件；用占位页面验证上下屏焦点。
5. 建立 `spec.md` 对应的状态枚举/路由接口（先空实现），避免 UI 后期返工。

> M0 通过后再进入 M1/M2；M2 的最小串流验证优先于完整 UI。
