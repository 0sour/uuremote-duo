# UUYC-Duo 调研报告

> 目标：为 Retroid Pocket Duo（RP Duo）做一个非官方 UU 远程第三方客户端。
> 要求：手柄操作、单屏/双屏双模式；双屏时下屏主页显示设备列表、上屏显示选中设备详情；
> 远控过程中上屏显示桌面、下屏显示工具箱（触摸板 + 自带拼音输入法的键盘）。
>
> 调研日期：2026-11-11。以下结论来自官方 APK、官方文档、开源客户端源码与 RP Duo 实测经验。

---

## 0. 结论摘要

1. **可行，而且不需要网关。** 官方 UU 远程的主控协议已经被多个开源项目复刻，Switch 端已有可运行客户端；
   Android 端可以用标准 WebRTC + Kotlin 实现，直接连接用户电脑上运行的原版 UU 远程电脑端。
2. **最直接的切入口是 NS 客户端。** `Antman2023/UURemote-ns` 与 `doke69/YOUYOU-Remote-ns`
   已经实现了：扫码/短信登录、设备列表、加入房间、Socket.IO 信令、WebRTC H.264/Opus、
   五条数据通道、键鼠/手柄输入。它们证明了官方服务接受第三方主控端。
   `Antman2023/UURemote-web` 的 `docs/protocol.md` 是目前最完整的协议分析；
   `hanakokoizumi/uux` 是更现代的 Rust 客户端，补充了 `text_input`、剪贴板、多屏等细节。
3. **唯一的敏感材料是请求签名 key。** 官方 TV 3.5.0 APK 的 `classes.dex` 里有一个 24 字节字符串，
   用于 HMAC-SHA256 签名（SHA-256 指纹 `40cb3a32…528c`）。开源客户端都要求用户自备这个 APK。
   建议本应用首次运行时让用户导入/下载该 APK，本地提取并加密保存；不要把 key 放进仓库或公开发布的 APK。
4. **Duo 的形态非常适合这个产品。** 下屏是 `FLAG_PRESENTATION`、`canHostTasks=false` 的 1280×960
   （640×480dp）副屏，正好用 `Presentation` 承载完整 Compose UI；上屏是 1920×1080（832×468dp），
   适合显示设备详情/远程桌面。bili-duo 已经踩过 Presentation、焦点、IME、手柄风格的全部坑，可以直接复用其模式。
5. **推荐架构：** 上屏 Activity（视频/详情）+ 下屏 Presentation（设备列表/工具箱）+ 共享 ViewModel；
   标准 Android WebRTC 接收 H.264/Opus，OkHttp 处理 HTTPS/WebSocket；
   手柄在“主页”导航下屏，在“串流”时直通远端 Xbox 手柄。
6. **单屏模式也是横屏**：同一套 Compose UI 可以在上屏 Activity（832×468dp）或下屏 Presentation（640×480dp）跑；
   没有副屏时自动回退。主页 master-detail，串流视频 + 底部抽屉。
7. **主要风险：** 协议/签名可能随官方更新失效；第三方客户端可能违反 UU ToS；WebRTC 互通与延迟需要真机联调；
   文本输入路径需要真实电脑验证。建议保留“官方 URL Scheme”兜底，并做好版本检测与错误提示。

---

## 1. 官方 UU 远程与第三方生态

### 1.1 官方能力

- 官网：<https://uuyc.163.com/>，被控端支持 Windows/macOS，主控端支持 Windows/macOS/iOS/Android/TV。
- 官方宣传：4K/144 帧、手柄/键鼠外设直连、文件传输、远程开机、按键映射、无线副屏。
- 官方下载（2026-11-11 实测）：
  - Android 4.46.0：`https://api.nrd.nie.163.com/api/v1/release/dl/2?channel=android_gwqd`
  - Android TV 3.5.0：`https://api.nrd.nie.163.com/api/v1/release/dl/5?channel=TV_gwqd`
  - Linux：`https://api.nrd.nie.163.com/api/v1/release/dl/8?channel=android_gwqd`（`.deb`）
- 官方给第三方 App 的集成方式只有 **URL Scheme**，不能嵌入自定义 UI：
  - 文档：<https://github.com/netease-uuyc/uuremote-integration-guide>
  - 设备绑定：`uuremote://external/device/authorize?callback=...&nonce=...`
  - 发起远控：`uuremote://external/device/control?anonymous_device_id=...&window_type=0|1`
  - 仅支持 Windows/macOS 被控设备；全屏或 PiP；官方 App 包名 `com.netease.uuremote`。
  - **结论：** 适合作为“官方模式/兜底”，无法实现下屏工具箱，因此不能作为主方案。

### 1.2 开源客户端/服务

| 项目 | 语言/平台 | 作用 | 对本项目的价值 |
| --- | --- | --- | --- |
| [Antman2023/UURemote-ns](https://github.com/Antman2023/UURemote-ns) | C++/libnx，Switch | 原生主控端：扫码/短信、设备、WebRTC H.264/Opus、Joy-Con/触屏键鼠 | **首选切入口**：完整协议、签名提取、信令、输入 |
| [doke69/YOUYOU-Remote-ns](https://github.com/doke69/YOUYOU-Remote-ns) | C++/libnx，Switch | 上游的增强版：H.265、NVDEC、画质/码率、Xbox 手柄直连、剪贴板文字 | **手柄/画质/文本兜底** 的最佳参考 |
| [Antman2023/UURemote-web](https://github.com/Antman2023/UURemote-web) | Node + 浏览器 WebRTC | Web 主控端 + 本地信令转接 | **协议文档** `docs/protocol.md`、Protobuf、信令/输入实现 |
| [hanakokoizumi/uux](https://github.com/hanakokoizumi/uux) | Rust + webrtc-rs，Linux | 现代 Linux 主控端：多屏、剪贴板、文件、终端 | **现代协议细节**：`text_input`、`streamer_data`、信令头 |
| [GaryOAO/UUWay](https://github.com/GaryOAO/UUWay) | Linux 被控端 | 官方 Windows 被控端跑在 Wine，原生采集/输入 | 验证“手机输入法→KEYEVENTF_UNICODE”路径 |
| [moaeiou/openuuyc-linux](https://github.com/moaeiou/openuuyc-linux) | Rust，Linux 被控端 | 另一个 Linux host 实现 | 协议/文本注入参考 |
| [netease-uuyc/uuremote-integration-guide](https://github.com/netease-uuyc/uuremote-integration-guide) | 官方文档 | URL Scheme 集成 | 兜底/官方模式 |

> 许可证：Antman/doke69 为 GPL-3.0-or-later；uux 为 GPL/类似许可证。若移植其代码，本项目应按 GPL-3.0 发布并注明来源；
> 若只使用协议事实（接口、JSON 字段、字段号）重写 Kotlin 实现，仍建议 GPL-3.0 以保持合规和社区友好。

### 1.3 为什么 NS 客户端是切入口

- 它们不依赖官方 App 转发，直接连接 **原版 UU 远程电脑端**，说明主控协议是完整可复刻的。
- 它们已经解决了最难的几件事：
  1. 从 TV APK 提取签名 key；
  2. 官方 API 的 HMAC 签名、二维码/短信登录、设备列表；
  3. Socket.IO 信令、二进制附件 `0x04`、SOAC/SDP/ICE；
  4. WebRTC 五条数据通道与输入 JSON；
  5. 手柄输入的 XInput 位掩码与轴值。
- 它们用 libdatachannel/FFmpeg 在 Switch 上跑通，Android 用标准 WebRTC 只会更容易。

---

## 2. 协议拆解

### 2.1 API 与请求签名

- Base URL：`https://api.nrd.nie.163.com`
- 签名：`HMAC-SHA256(key, method + path(含原始 query) + 排序后的小写 x-param 头串 + 原始 JSON body)`
  - 头串：`name=value` 以 `&` 连接，按 name 升序；排除 `X-Param-SIGN`；`Authorization` 不参与签名。
- 关键请求头（TV 3.5.0 兼容）：

```text
X-Param-CHN: gwqd
X-Param-PKGN: com.netease.uuremote
X-Param-PLAT: 5
X-Param-REL: prod
X-Param-TS: <ms>
X-Param-VC: 30500
X-Param-VN: 3.5.0
X-Param-ABI: arm64-v8a
X-Param-client-id: <持久化 UUID>
X-Param-device-id: <init 返回的主控设备 id>
X-Param-user-id: <登录后的 user_id>
X-Param-CNT: CN
X-Param-LANG: zh-CN
X-Param-SIGN: <hex>
Authorization: Bearer <token>
Content-Type: application/json; charset=utf-8
```

- 主要接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/device/android_tv/init` | 注册主控设备，返回 `device_id` |
| POST | `/api/v1/qrcode/gen/login` | 生成登录二维码 |
| POST | `/api/v1/qrcode/login/status` | 轮询扫码状态（1 生成 / 2 待确认 / 3 取消 / 4 确认） |
| POST | `/api/v1/login/by_qrcode` | 确认后换取 token |
| POST | `/api/v1/security/mobile/code` | 发送短信验证码 |
| POST | `/api/v1/login/by_mobile` | 短信登录 |
| GET | `/api/v1/user/info` | 校验 token / 账号信息 |
| GET | `/api/v1/device/list` | 账号设备列表 |
| GET | `/api/v1/app/list/of/device/{id}` | 设备应用列表（可选） |
| POST | `/api/v1/room/join/by_device/{id}` | 加入远控房间，body `{"force_join":false}` |

- 设备列表字段：`my_binded_devices[]`、`others_shared_devices[]`；每项至少 `device_id`、`alias`、`status`
  （`offline` 表示离线）；过滤掉自己的主控设备 id，按 `device_id` 去重。
- 房间响应：`token`、`signaling_list[]` / `signaling_server`、`iceServers`、`publisher`、`ws_connect_timeout_ms`。
- 二维码：生成接口返回 `qrcode_jump_url` 与 `token`；轮询时把整个响应对象带上（含 `status_query_ticket`），
  `login_status` 随上次状态更新；确认后用同一对象登录。

### 2.2 信令（Socket.IO / Engine.IO v4）

- URL：把房间返回的信令地址补成 `wss://<host>/socket.io/?EIO=4&transport=websocket`。
- 握手头：

```text
X-NRD-AUTH: <room.token>
X-NRD-CONTROLLING: 0
streamer_version: V1.14.22   # TV/NS 兼容；现代客户端用 V4.3.0
streamer_flag: 1             # 现代客户端可选
```

- 协议帧：
  - 服务器先发 `0{"sid":...,"pingInterval":25000,"pingTimeout":20000}`；
  - 客户端发 `40`；服务器回 `40{...}`；
  - 心跳：服务器 `2` → 客户端 `3`（可带 payload）；
  - 事件：`42["event", ...]`；带 ack id：`42<id>["event", ...]`；
  - ack：`43<id>[...]`（也可能 `431[...]`）；
  - 二进制事件：`45<attachments>-<id>["control", {... "_placeholder":true,"num":0 ...}]` + 二进制帧；
    **二进制帧首字节必须是 `0x04`**，对端解析时去掉。
- 关键事件：
  - `room_info`（需要 ack）→ 房间对象，取 `publisher.client_id`；
  - `control`（需要 ack）→ 申请控制权：

```json
{
  "app_data": {"_placeholder": true, "num": 0},
  "streamer_data": "",
  "app_control_id": "<uuid>"
}
```

  - `app_data` 附件 = `0x04 || ConnectOptions(protobuf)`；
  - ack 返回 `["success", {client_id, ice_id, iceServers, force_relay, streamer_data}]`；
  - `soac` → SDP/ICE：

```json
{"client_id":"<remote>","data":{"type":"offer","sdp":"...","ice_id":"...","app_control_id":"..."}}
{"client_id":"<remote>","data":{"type":"candidate","candidate":{"candidate":"...","sdpMid":"0","sdpMLineIndex":0},"ice_id":"...","app_control_id":"..."}}
```

  - `released` / `left` → 权限被接管 / 电脑退出；`streamer_push` → 主机推送。
- 断开：发 `release`、`leave`、`41`、`1`，关闭 WebSocket。

### 2.3 WebRTC 与数据通道

- 主控端创建 recvonly 的 video/audio transceiver，H.264 优先；视频/音频由主机推流。
- 五条 SCTP 数据通道（必须同名创建）：

```text
CONTROL_DATA_CHANNEL
TEXT_DATA_CHANNEL
STREAMER_DATA_CHANNEL
FILE_DATA_CHANNEL
BINARY_DATA_CHANNEL
```

- CONTROL 通道承载 JSON 输入（UTF-8 二进制帧）；TEXT 通道承载 `Main.Message` protobuf（文字/剪贴板兜底）；
  STREAMER 承载主机能力/控制消息；FILE/BINARY 用于文件/剪贴板（v1 可不实现，但建议创建）。
- ICE：使用 `control` ack 返回的 `iceServers`；`force_relay` 时 `iceTransportPolicy=relay`；
  `bundlePolicy=max-bundle`。候选先于 answer 到达时要缓存。
- Android 端使用 `org.webrtc`（`io.github.webrtc-sdk:android:125.6422.07` 已确认包含
  `RtpTransceiver.setCodecPreferences`、`JavaAudioDeviceModule` 等 API）。

### 2.4 输入协议（CONTROL_DATA_CHANNEL，JSON）

**鼠标**：

```json
{"action":"mouse_move_absolute","abs_x":0.5,"abs_y":0.5}
{"action":"mouse_move_relative","delta_x":12,"delta_y":-8}
{"action":"mouse_press","button":1}
{"action":"mouse_release","button":1}
{"action":"mouse_click","button":1}
{"action":"mouse_scroll","delta_x":0,"delta_y":120}
```

- `abs_x/abs_y` 是 0–1 的画面分数；映射时要排除黑边（letterbox），用实际视频矩形计算。
- `button`：1 左键、2 右键、16 中键（uux 还允许 32/64）。
- 触摸板建议维护虚拟光标（0–1），把手指相对位移累加后发 `mouse_move_absolute`；点击前先发一次绝对坐标。
  这样与 NS/Web 已验证的路径一致，也避免相对位移单位不确定。

**键盘**（Windows VK）：

```json
{"action":"kbd_press","key":17}
{"action":"kbd_release","key":17}
```

- 常用 VK：Enter 13、Esc 27、Backspace 8、Tab 9、Space 32、Delete 46、方向键 37–40、
  Ctrl 17、Shift 16、Alt 18、Win 91、字母 65–90、数字 48–57、F1–F12 112–123。
- 组合键按顺序发送 press/release；连接断开/切页时释放所有按住的键。

**文字输入（现代路径，推荐）**：

```json
{"action":"text_input","content":"你好"}
```

- 官方 Android 4.46 的 `ControllerService.sendText` 与 uux 的 `text` 命令都使用这条路径；
  它直接走 CONTROL 通道，不需要 protobuf，也不依赖电脑端输入法。
- 适合“系统输入法 commit 文本 → 整段发送”的模式。

**文字输入（旧版/多屏路径，兜底）**：

- `Main.Message`（protobuf）：

```proto
message Message {
  int64 seq = 1;
  int64 timestamp = 2;
  oneof which {
    ...
    SendToRom send_to_rom = 11;
    ...
  }
}
message SendToRom {
  enum MessageType { RomMsg_VINPUT=0; RomMsg_Text=1; ... }
  MessageType input_type = 1;
  string input_msg = 2;
  int32 index = 3;
}
```

- 官方 TV/Android 的 `SendTextToRoom` 会构造 `SendToRom{RomMsg_Text, input_msg, index}`，
  通过 `TEXT_DATA_CHANNEL` 发送；`input_msg` 在原生层经过 `OnIMEText` 处理。
- 原生库里的控制命令是符号串：`ENTER`、`BACKSPACE`、`HIDESELF`；`RoData` 里有 `TEXT_CONTROL:` 前缀。
  目前没有 100% 确认 `OnIMEText` 的完整前缀，建议优先用 `text_input`，此路径作为兜底并留可配置前缀。
- 另一个兜底：剪贴板 RPC + Ctrl+V（doke69 已验证）：
  - `Main.Message` field 21 = RpcRequest；`RpcRequest` field 10 = `ClipboardTextChangeRequest{format_id=13, data=text}`；
  - 等待 RpcResponse field 22 的 `ClipboardTextChangeResponse.err == 1`（CB_SUCCEEDED）；
  - 再发 `Ctrl+V`（17/86 press/release）。文本上限 2048 字节。

**手柄**（XInput 语义）：

```json
{"action":"gpd_connect","index":0}
{"action":"gpd_update","index":0,"buttons":4096,"left_trigger":0,"right_trigger":0,
 "left_thumb_x":0,"left_thumb_y":0,"right_thumb_x":0,"right_thumb_y":0}
{"action":"gpd_disconnect","index":0}
{"action":"gpd_vibration", ...}   // 主机→客户端
```

- `buttons` 位掩码（与 Windows XInput 一致）：

| 位 | 按钮 | 位 | 按钮 |
| --- | --- | --- | --- |
| 1 | D-Pad Up | 256 | LB |
| 2 | D-Pad Down | 512 | RB |
| 4 | D-Pad Left | 4096 | A |
| 8 | D-Pad Right | 8192 | B |
| 16 | Start | 16384 | X |
| 32 | Back/Select | 32768 | Y |
| 64 | L3 | | |
| 128 | R3 | | |

- 轴值：`-32767..32767`；扳机 `0..255`。NS 实现会在变化或每 500ms 发送一次；我们建议 60Hz 节流。
- 手柄风格：RP Duo 的“Retro”模式会交换 A/B、X/Y 的 keycode；应用入口统一归一化为 Xbox 语义，
  业务层只认 Xbox keycode（bili-duo 的 `GamepadStyle` 模式）。

### 2.5 ConnectOptions（protobuf）

```proto
message CaptureParams {
  FPS fps = 1;                       // 30=1, 60=2, 90=3, 144=4
  VideoQuality video_quality = 2;    // Fast=1, General=2, HD=3, Bluray=4, Auto=5, Custom=6
  bool cursor_capture = 3;
  ChooseResolutionType choose_resolution_type = 4; // DEFAULT=1, FOLLOW_LOCAL=2, FOLLOW_REMOTE=3, RESOLUTION=4
  ScreenResolution local_resolution = 5;
  ScreenResolution choose_resolution = 6;
  ChromaFormat chroma_format = 7;    // 420=1
  int32 max_custom_bitrate = 8;
  bool enable_hdr = 9;
  VideoQuality auto_frame_quality = 10;
  int32 fpsCount = 11;
}
message DecoderCap {
  int32 fps = 1;
  CodecType codec_type = 2;          // H264=1, H265=2
  int32 resolution_width = 3;
  int32 resolution_height = 4;
  ChromaFormat chroma_format = 5;
}
message ConnectOptions {
  Type capture_type = 1;             // DESKTOP=1
  int32 type_value = 2;              // -1
  CaptureParams capture_params = 3;
  repeated DecoderCap decoder_cap_list = 4;
  bool force_virtual_display = 5;
  ...
  ClientType client_type = 8;        // Android=2
  string device_id = 9;
  ControlConnectType control_connect_type = 10;
  FeatureFlag feature_flag = 11;
  string client_version = 12;
}
```

- Web 客户端的极简版本（`1920×1080@60, quality=3`）已实测能出画面；NS 版本增加了
  `choose_resolution_type=4`、`fpsCount`、`decoder_cap_list`。建议：
  - 桌面：`capture_type=1`、`type_value=-1`、`cursor_capture=true`；
  - 分辨率：默认 `1920×1080`，可选 `1280×720`/`960×540`；
  - FPS：默认 60，可选 30/90/144；
  - 画质：默认 3（HD），可选 1–6；自定义码率 0–100 Mbps；
  - `decoder_cap_list` 至少 H264/420/目标分辨率；如需 H265 再加（Android WebRTC 原生不支持 H265，
    所以主控端应坚持 H264）。
- `streamer_data` 可以发空字符串（Web/NS 已验证）；现代 uux 会发 `control_id + device_capability` JSON，
  包含 `display_info` 与 `video_codec_capability`。可作为后续兼容性增强。

### 2.6 签名材料提取

- 参考 APK：`UURemote-tvProduction-3.5.0-30500-2b0f97d4-TV_gwqd.apk`（官方 TV 3.5.0）。
- `classes.dex` 的 string table 中有一个 **24 字节** 字符串，其 SHA-256 为：
  `40cb3a32f9f6f90d9962fb9736685bb881979e848b5956649b6dae3cc957528c`。
- 提取算法（可在 Kotlin/Java 实现）：
  1. 读取 APK zip 里的 `classes.dex`；
  2. 解析 DEX header：`string_ids_size` @ offset 56，`string_ids_off` @ offset 60；
  3. 遍历每个 `string_id` 的 data offset，跳过 ULEB128 长度，读到 NUL；
  4. 找长度 24 且 SHA-256 匹配的字符串；返回它。
- **注意：**
  - 官方 Android 4.46 APK 里没有这个 key（它用 `libnrdsign.so` 的 native 签名），所以必须用 **TV 3.5.0 APK**。
  - 不要把 key 提交到仓库、打进公开 APK 或写入日志。
  - 首次运行可提供三种方式：从已安装的官方 TV App 读取 APK、用户用 SAF 选择 APK、应用从官方下载接口拉取 APK。
    提取后存 `EncryptedSharedPreferences`，并用 `/device/android_tv/init` 验证（失败提示“官方已更新/密钥不匹配”）。
  - 开发调试可用 `local.properties` 指向本地 key 文件，gradle 生成到 debug assets（gitignore）。

### 2.7 文本输入路径优先级

1. **`{"action":"text_input","content":...}`**（CONTROL 通道）——最简单、官方 4.46 与 uux 都在用。
2. **`SendToRom{RomMsg_Text}` + `TEXT_DATA_CHANNEL`**——旧版/TV 路径，需要确认 `OnIMEText` 前缀
   （`TEXT_CONTROL:` / `TEXT:` 候选），可作为设置里的兼容开关。
3. **剪贴板 RPC + Ctrl+V**——doke69 已验证，适合长文本/兜底（≤2048 字节）。
4. 纯 ASCII 也可以逐字符发 `kbd_press/release`，但中文必须走 1/2/3。

---

## 3. Duo 工程约束（来自 bili-duo 实测）

### 3.1 屏幕

- 上屏：display 0，1920×1080，369dpi，横屏 832×468dp，120Hz，`canHostTasks=true`。
- 下屏：display 2，1280×960，320dpi，640×480dp，4:3，触摸 EXTERNAL，`FLAG_PRESENTATION`，
  `canHostTasks=false` → **只能用 `Presentation` 或 Surface，不能 `startActivity`**。
- 下屏 UI 必须按 640×480dp 的“中等宽度 + 极矮高度”设计；Material 手机/平板断点都不适用。
- 截图/注入测试（仅开发用）：`screencap -d <unique display id>`；下屏触摸 `input -d 2 tap`；
  验证 Activity 转发用 `input -d 0 keyevent`；裸 `input keyevent` 不可靠。

### 3.2 焦点/按键

- 下屏 Presentation 获得焦点后，系统 Return/Home 不再经过 Activity 的 `dispatchKeyEvent`；
  必须在 Presentation 里捕获 `KEYCODE_BACK`/`KEYCODE_ESCAPE`，并注册 API 33+ 的 `OnBackInvokedCallback`。
- 手柄 A/B/X/Y 的 keycode 受系统“手柄风格”影响；Retro 模式会交换 A↔B、X↔Y。
  入口统一归一化，业务层只认 Xbox 语义（bili-duo `GamepadStyle.normalizeFaceKey`）。
- 不要让两个窗口同时处理按键；建立单一 `InputRouter`，按状态决定“导航下屏”还是“直通远端”。
- 下屏 Compose 焦点环要外扩、在 clip 前绘制、Lazy 首行留焦点余量；否则会被裁切。

### 3.3 中文输入与 IME

- **默认内置拼音**：应用自带键盘 + 本地拼音词典/候选条，不依赖系统 IME，下屏 Presentation 也能用。
  字母实时匹配候选；空格提交首选，数字键 1–9 或点候选选择；组合中的拼音只在本机显示。
- 内置拼音提交时走 `text_input` 整段发送；英文/控制键走 `kbd_press`/`kbd_release`（VK）。
- **系统输入法按钮**：聚焦隐藏/可见 EditText → `InputMethodManager.showSoftInput`，Gboard/搜狗
  在当前屏幕出现；系统 IME 可见时隐藏内置键盘，避免双键盘叠加。
  系统 `commitText` 同样转发为 `text_input`；`setComposingText` 只本地显示；Enter/Backspace 发 VK。
- 系统 IME 在下屏 Presentation 的副屏显示能力需真机验证；不支持时回退内置拼音并提示。
- 不要用 `WindowInsets.isImeVisible` 判断下屏键盘：Presentation 冷启动/空闲会上报 true。
  使用自己的 `DuoImeState`（`onCreateInputConnection` / `onWindowFocusChanged` / InputMethodManager 回调）。
- “聚焦输入框只亮焦点，按确认才弹键盘；返回先收键盘、再按才返回”是 bili-duo 用户确认的模型
  （Duo 实体丝印为 B 确认、A 返回）。我们的输入浮层：A/返回先收系统 IME，再收内置浮层。

### 3.4 输入归属模型

建议三态：

```text
HOME_BOTTOM     ：手柄/键盘 → 下屏 Compose 焦点导航
STREAM_REMOTE   ：手柄 → 远端 Xbox；触摸 → 下屏工具箱/上屏直接触摸
LOCAL_OVERLAY   ：手柄 → 本地菜单（设置/断开确认）；远端输入暂停
```

- 主页：下屏 `requestFocus()`，上屏详情页 `focusable=false`，避免抢焦点。
- 串流：上屏视频 `requestFocus()`；下屏触摸仍可用，但下屏捕获到的游戏手柄事件也转发给远端
  （保证用户摸了下屏后游戏手柄不断线）。
- 本地菜单：由下屏“菜单”按钮或系统 Back 打开；此时才暂停直通。

---

## 4. 推荐产品方案

### 4.1 两种模式

**双屏模式（Duo 默认）**

```text
主页：
  上屏：选中设备详情（名称/在线/系统/上次连接/连接提示）
  下屏：设备列表（焦点卡）+ 账号/刷新/设置/登录入口
连接中：
  上屏：设备名 + 进度（加入房间→信令→协商→等待画面）
  下屏：进度 + 取消
串流中：
  上屏：远程桌面（全屏，无 OSD；可开关小统计）
  下屏：工具箱（触摸板 / 键盘 / 快捷键 / 状态）
```

**单屏模式（横屏，Duo 上屏或下屏）**

```text
主页：左列表 + 右详情（master-detail）
串流：视频铺满 + 底部工具箱抽屉（默认 42%，可拖到 68%）
工具箱：触摸板/键盘/快捷键/状态；收起后只剩视频 + 悬浮“工具箱”按钮
```

- 单屏必须横屏：上屏 Activity 832×468dp，或下屏 Presentation 640×480dp；两种都是唯一屏幕。
- 单屏跑下屏时：整个 UI 在 Presentation 里，上屏 Activity 只做黑色宿主；系统 Back/IME 由
  Presentation 自己接，不能用 `WindowInsets.isImeVisible` 作为唯一判断，不能用 `Dialog`。
- 自动检测副屏：有 `FLAG_PRESENTATION` 副屏且用户未强制单屏 → 双屏；否则单屏。
  单屏默认优先上屏；设置里可选“自动 / 固定上屏 / 固定下屏”。
- 双屏↔单屏切换不重建 WebRTC 会话；只切换 UI 宿主（Presentation 显示/隐藏、Activity 布局），
  视频 Surface 重新 attach。

### 4.2 界面细节

**下屏设备列表（640×480dp）**

- 顶部 44dp：应用名/logo、账号昵称、在线数、刷新/账号按钮。
- 设备卡 72–80dp：名称、系统图标、状态点、上次连接、共享标记；焦点卡白环 + 左侧强调条。
- 每屏 4–5 张卡，滚动；D-pad/左摇杆移动，B 连接，A 返回，Y 刷新，X 账号，START 设置。
- 空态：说明 + “扫码登录”主按钮；离线设备灰显，仍可选中查看详情。
- 底部 28dp：按键提示（实体丝印：B 确认/A 返回/Y 刷新 等，沿用 bili-duo 的提示规范）。

**上屏详情（832×468dp）**

- 深色/亮色主题；大标题设备名、状态徽标、系统/分辨率/网络信息。
- 右侧或底部信息卡：“B 连接”“A 返回”；离线时“请在电脑上打开 UU 远程”。
- 连接中：分阶段进度条 + 设备名 + 取消提示。
- 串流中：`SurfaceViewRenderer` 全屏，黑底，`SCALE_ASPECT_FIT`；可选左上角小统计。
- 错误：错误码/原因 + “B 重试 / A 返回”。

**下屏工具箱（640×480dp）**

- 顶栏 44dp：设备名/状态点、延迟/FPS、静音、设置、断开。
- Tab 36dp：触摸板 / 键盘 / 快捷键 / 状态。
- 触摸板：大圆角区域 + 点阵背景；单指移动、点击左键、双指滚动、双指点击右键、长按拖拽；
  底部左/中/右按钮 + “拖拽锁定” + 灵敏度。
- 键盘：顶部“内置拼音 / 系统输入法”行；下方 QWERTY + 功能键 + 修饰键（粘滞 Ctrl/Alt/Shift/Win）；
  上方候选条（本地词典，空格上屏首选、数字键选候选）；长按 Backspace/方向键连发；符号/功能键第二页。
- 快捷键：网格（Ctrl+C/V/X/Z/A、Alt+Tab、Win+D/E、Esc、Tab、Enter、Backspace、Delete、方向键、F11 等）。
- 状态：分辨率/编码/码率/RTT/丢包/解码器/P2P 或 TURN；重连/断开。
- IME 浮层：内置拼音候选 + QWERTY，右上角“系统输入法”按钮；系统 IME 状态由 Gboard/搜狗绘制；
  commit 即发 `text_input`；“完成”收键盘；A/返回先收系统 IME，再收内置浮层。

**单屏工具箱（横屏）**

- 视频铺满；底部抽屉默认 42%、可拖到 68%；收起后只剩视频 + 悬浮“工具箱”按钮。
- 抽屉顶栏：设备/状态、手柄模式 Chip（直通/本地）、延迟、菜单、收起、断开。
- 切到系统输入法时抽屉上移/压缩；640×480 下可切“键盘页”占满抽屉。
- 手柄默认直通；抽屉按钮触摸操作；系统 Back/Return 打开本地菜单；需要用手柄操作抽屉时
  在本地菜单切“本地操作”。切换时先发零值 + `gpd_disconnect`，切回时重连。

### 4.3 手柄操作

- **主页**：D-pad/左摇杆导航列表；B 连接；A 返回；Y 刷新；X 账号；START 设置；L1/R1 切页（可选）。
- **串流**：手柄默认直通远端（标准 Xbox 位掩码 + 轴 + 扳机），60Hz 节流；
  Start/Back/LB/RB/L3/R3/扳机全支持。**不要占用任何手柄键做本地快捷键。**
- **本地菜单**：只走系统 Return/Back 或下屏触摸“菜单”按钮；打开时暂停直通、发零值并
  `gpd_disconnect`，释放远端所有按键；关闭后恢复直通并 `gpd_connect`。
- **本地操作**：工具箱 Chip 可切“手柄：直通电脑 / 本地操作”；本地操作时手柄导航工具箱，
  远端输入暂停；切回直通时重连。切换时显示提示，避免用户不知道远端手柄已暂停。
- **手柄风格**：Xbox/Retro 首次设置 + 设置页可改；入口归一化，提示用实体丝印（B 确认/A 返回）。
  本地 UI 的 B/A 语义和远端 Xbox A/B 是两套；远端直通发送 canonical Xbox，不受本地语义影响。
- 可选“手柄键鼠”模式：左摇杆鼠标、B/A 确认/返回、Y/X 左右键、右摇杆滚轮（参考 NS 方案）。
- 振动：解析 `gpd_vibration`，用 Duo 振动马达/线性马达；可关。
- 不要全局拦截 `dispatchGenericMotionEvent`；只有本地模式且确有摇杆消费者时才消费
  `AXIS_X/AXIS_Y`，`AXIS_HAT_X/AXIS_HAT_Y` 必须放行。

### 4.4 文本/输入法

两条路径，默认内置拼音，同时保留系统输入法按钮：

1. **内置拼音（默认）**：应用自带键盘 UI + 本地拼音引擎/词典负责候选，不依赖系统 IME。
   - 输入字母实时出候选；空格提交首选，数字键 1–9 或点击候选选择；`中/英` 切换。
   - 中文 `commit` → 发 `text_input`（整段）；英文/控制键 → VK press/release。
   - 组合中的拼音只在本机显示；退格先删拼音，再删已提交文本。
   - 下屏 Presentation 也能用，不需要系统 IME 在副屏出现。
2. **系统输入法（按钮）**：点“系统输入法” → 聚焦隐藏 `EditText` → Gboard/搜狗出现在当前屏幕。
   - 系统 IME 可见时隐藏内置键盘，避免双键盘；`commitText` → `text_input`；`setComposingText` 只本地显示。
   - Enter/Backspace → VK 13/8；系统 IME 收起后回到内置拼音。
   - 副屏 Presentation 的系统 IME 兼容性需真机验证；失败则提示并留在内置拼音。
- A/返回先收系统 IME，再收内置浮层，再关键盘页；B/确认在本地操作模式下确认/发送。
- 中文不依赖电脑端输入法，直接以文本插入远端焦点；`text_input` 失败时回退 `SendToRom{RomMsg_Text}`。

---

## 5. 技术架构

```text
MainActivity (display 0)
  ├─ TopHost: Idle / DeviceDetail / Connecting / Video(SurfaceViewRenderer) / Error
  ├─ DuoPresentation (display 2, Presentation)
  │    ├─ DeviceList / Login / Settings
  │    └─ Toolkit: Touchpad / Keyboard / Shortcuts / Stats
  ├─ SessionViewModel (Activity-scoped)
  │    ├─ AuthRepository (QR/SMS, token)
  │    ├─ DeviceRepository (list/refresh)
  │    ├─ UuSession (state machine)
  │    │    ├─ UuApiClient (OkHttp + HMAC)
  │    │    ├─ SignalingClient (Engine.IO v4 over OkHttp WebSocket)
  │    │    └─ UuRtcClient (PeerConnection, 5 channels, tracks, stats)
  │    ├─ InputRouter / GamepadState
  │    └─ SettingsStore / SecureStore
  └─ SingleHost (same composables, responsive layout + bottom sheet)
```

- **UI**：Kotlin + Jetpack Compose + Material3；下屏 Presentation 复用 bili-duo 的
  `SecondScreenPresentation` 模式（Lifecycle/ViewModelStore/SavedState/ActivityResult/BackDispatcher owner）。
- **网络**：OkHttp（API + WebSocket）；手写 Engine.IO v4 帧解析，避免 socket.io-client 对
  `X-NRD-AUTH` 头和二进制 `0x04` 的不确定性。
- **WebRTC**：`io.github.webrtc-sdk:android:125.6422.07`；
  `PeerConnectionFactory` + `DefaultVideoDecoderFactory` + `JavaAudioDeviceModule`（`setAudioRecordEnabled(false)`）；
  `RtpTransceiver.setCodecPreferences` 只留 H.264；`SurfaceViewRenderer` 上屏。
- **Protobuf**：手写最小 writer（varint/len-delimited），避免引入 protobuf runtime；字段号见 §2.5。
- **JSON**：kotlinx.serialization 或 Moshi；信令用 `org.json`/动态树，API 用 data class。
- **存储**：DataStore Preferences（设置）；EncryptedSharedPreferences（token、签名 key）。
- **输入法**：`BuiltinPinyinEngine` + 本地拼音词典 asset，负责拼音切分与候选；`ImeMode` 为
  `BUILTIN / SYSTEM`。中文 commit 统一 `Session.sendText()` → `text_input`；系统 IME 用
  `EditText`/`InputConnectionWrapper`，只转发 commit 文本，composing 不发送。
- **二维码**：ZXing core 生成 Bitmap；不扫别人的码（由手机官方 App 扫我们）。
- **权限**：INTERNET、ACCESS_NETWORK_STATE、VIBRATE、WAKE_LOCK；音频播放不需要麦克风
  （如用 JavaAudioDeviceModule 则 `setAudioRecordEnabled(false)`，不申请 RECORD_AUDIO）。

### 5.1 会话状态机

```text
Idle → JoiningRoom → Signaling → Negotiating → WaitingFirstFrame → Streaming
                                   ↘ Error / Cancelled
Streaming → Reconnecting → Streaming
Streaming → Closed
```

- 每个阶段都有超时（房间 15s、信令 15s、控制 15s、首帧 30–40s）。
- 取消/断开要释放：信令 socket、PeerConnection、数据通道、输入按住状态、音频。
- 首帧到达才把上屏切到视频；之前显示连接进度，避免黑屏。
- `released`/`left`/WebRTC failed → 明确提示 + 重试/返回。
- 断线重连：先尝试 WebRTC ICE restart/等待 12s；失败则重新 join room（可选）。

### 5.2 输入映射

- **触摸板**：虚拟光标 `cursorX/Y ∈ [0,1]`；手指位移 × 灵敏度 ÷ 视图尺寸；节流 16ms；
  点击前补一次绝对坐标；双指滚动 `delta_y = -dy`；双指 tap 右键；长按拖拽。
- **直接触摸**（可选）：触摸上屏视频/下屏“直接触摸”区域 → 按实际视频矩形映射到 0–1 →
  `mouse_move_absolute` + click；黑边不发送。
- **键盘**：VK 映射表；修饰键粘滞/锁定；长按连发；断开时 release all。
- **手柄**：Xbox 位掩码；Android `AXIS_Y` 需要取反以匹配 XInput 的正向上；死区 0.15；
  扳机从 `AXIS_LTRIGGER`/`AXIS_RTRIGGER` 或 `AXIS_BRAKE`/`AXIS_GAS` 读取；60Hz 节流；
  首次 update 前发 `gpd_connect`，停止发 `gpd_disconnect` + 全零 update。

---

## 6. 里程碑

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| M0 脚手架（1–2 天） | Kotlin/Compose 工程、主题、单/双屏宿主、Presentation、GamepadRouter 骨架 | MuMu/Duo 能显示上下屏不同内容，手柄能导航下屏 |
| M1 账号（2–3 天） | 签名 key 导入/提取、API 客户端、扫码/短信登录、设备列表、设置持久化 | 真账号登录、看到电脑列表、重启保持登录 |
| M2 串流（3–5 天） | 房间加入、Engine.IO 信令、WebRTC 视频/音频、首帧状态、统计 | 真电脑出画面/声音，1080p60 稳定 |
| M3 输入（3–4 天） | 鼠标绝对/相对、键盘 VK、内置拼音引擎/候选、`text_input`、Xbox 手柄、release-all | 远端文本编辑器/游戏可用，中文可输入，键鼠/手柄无卡键 |
| M4 双屏体验（3–4 天） | 下屏设备列表+上屏详情、工具箱（触摸板/键盘/IME/快捷键/状态）、单屏横屏上屏/下屏、手柄三态 | Duo 真机完成双屏/单屏主页→连接→触摸板→中文输入→手柄直通/本地→断开全流程 |
| M5 收尾（2–3 天） | 设置/主题/错误/重连/诊断、README、GPL、release 签名 | 可日常使用的 v0.1，公开仓库不含 key/APK |

> 关键顺序：**M2 必须尽早用真电脑验证**。WebRTC 互通是最大不确定性，UI 做得再好在协议不通时也没意义。
> 建议 M0 后先写一个“最小命令行/单 Activity 串流验证版”，只做登录+连接+视频，再展开双屏 UI。
> 详细任务、依赖与验收见 `implementation-plan.md`。

---

## 7. 风险与备选

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 官方改协议/签名 | 登录或串流失效 | 版本检测；key 指纹校验；错误提示；关注上游 NS/uux 更新；保留官方 URL Scheme 兜底 |
| 第三方客户端违反 ToS | 账号风险 | 明确非官方声明；不绕过付费/风控；只连接用户自己的电脑；让用户知情 |
| WebRTC 互通/延迟 | 无画面/高延迟 | 先用 Web/NS 的参数；H.264 强制；ICE/TURN 兼容；真机分阶段验证；必要时试官方 libstreamer |
| Android WebRTC 不支持 H.265 | 只能 H.264 | ConnectOptions 只报 H264；设置里不提供 H265 |
| 文本输入路径不确定 | 中文打不进去 | 优先 `text_input`；兜底 SendToRom / 剪贴板 RPC + Ctrl+V；设置里可切换 |
| 下屏 IME/焦点 | 键盘弹不出/按键丢 | 复用 bili-duo 的 DuoImeState/InputConnectionWrapper；真机逐项验收 |
| 手柄风格/轴方向 | 按键错位/摇杆反向 | 入口归一化；轴方向设置；用远端“游戏控制器测试”页校准 |
| 性能/发热 | 掉帧/延迟 | 720p/30 档；硬件解码；统计面板；可关音频/降低码率 |
| 密钥分发 | 合规/泄露 | 不提交 key/APK；首次运行本地提取；加密存储；debug 用 local.properties |

### 备选方案

1. **官方 URL Scheme 模式**：在设置里提供“用官方 App 远控”按钮，调用
   `uuremote://external/device/control`；没有下屏工具箱，但协议失效时仍能远控。
2. **官方 `libstreamer.so`**：从官方 APK 提取 `libstreamer.so`/`libstreamer-jni.so`，复刻
   `StreamerController` Java 包装，直接复用官方 WebRTC/解码。优点是兼容性最好；缺点是闭源、体积大、
   JNI 未文档化、分发不合规。仅作为技术验证/个人兜底，不建议作为主线。
3. **纯网页壳**：用 WebView 跑 UURemote-web + 本地 Node 转接。不可行（Android 上无法跑 Node 服务），
   且无法控制下屏工具箱。

---

## 8. 待确认的产品决策

1. **应用名/包名/品牌**：建议 `DuoRemote` / `com.sour.duoremote`，非官方声明，不使用 UU/网易 logo。
2. **许可证**：建议 GPL-3.0（参考项目为 GPL；协议事实重写也保持社区友好）。
3. **签名 key 获取**：建议首次运行“导入/下载官方 TV 3.5.0 APK → 本地提取 → 加密保存”，仓库不含 key。
4. **单屏默认宿主**：Duo 单屏优先上屏 Activity，设置里可强制下屏 Presentation；不做竖屏。
5. **v1 范围**：只做“设备列表/详情 + 远控 + 触摸板 + 键盘/IME + 手柄 + 单/双屏 + 设置/统计”；
   文件传输、剪贴板同步、应用启动、多显示器切换、远程开机放 v2。
6. **文本输入**：v1 默认内置拼音（本地词典/候选，中文 `text_input`），同时保留系统输入法按钮；
   系统 IME 在下屏 Presentation 的副屏兼容性需真机验证，失败时回退内置拼音。

---

## 9. 参考

- 官方：<https://uuyc.163.com/>、<https://uuyc.163.com/download/>、
  <https://github.com/netease-uuyc/uuremote-integration-guide>
- 协议/客户端：
  - <https://github.com/Antman2023/UURemote-ns>
  - <https://github.com/doke69/YOUYOU-Remote-ns>
  - <https://github.com/Antman2023/UURemote-web>（`docs/protocol.md`）
  - <https://github.com/hanakokoizumi/uux>
  - <https://github.com/GaryOAO/UUWay>
  - <https://github.com/moaeiou/openuuyc-linux>
- Android WebRTC：`io.github.webrtc-sdk:android:125.6422.07`
- Duo/双屏经验：同系列 bili-duo 项目的 `HANDOFF.md`、`SecondScreenPresentation.kt`、
  `InputRouter.kt`、`GamepadPreference.kt`、`DuoImeState.kt`；以及 bili-duo 记忆中
  “下屏 640×480dp / Presentation-only / 手柄风格 / IME 门控”等条目。

---

## 10. 下一步建议

设计文档已补齐：视觉见 `ui/tokens.md`，行为/状态/验收见 `ui/spec.md`，
实施拆解见 `implementation-plan.md`。

建议先做 **M0 + M2 的最小串流验证**（不做完整 UI）：

1. 现有 Kotlin/Compose 工程已跑通构建；继续加入/验证 WebRTC 依赖；
2. 实现签名 key 导入 + API 登录 + 设备列表；
3. 实现 Engine.IO 信令 + WebRTC 视频，单 Activity 全屏显示；
4. 用真实电脑验证 1080p60 画面/声音；
5. 验证通过后，再展开双屏设备列表/详情/工具箱。

这样可以在 3–5 天内排除最大的技术风险（协议互通），再投入 UX 开发。
