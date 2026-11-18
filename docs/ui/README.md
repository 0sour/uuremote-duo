# DuoRemote UI 设计稿

> 这是 UI 开发前的设计稿，供修改和评审。所有页面是纯 HTML/CSS/JS，无外部依赖。
> 行为规格见 `spec.md`，实施计划见 `../implementation-plan.md`。

## 打开方式

双击 `index.html`，或在 Edge/Chrome 中打开：

```text
docs/ui/index.html
```

页面右上角可以切换亮色、缩放（75% / 100% / 125%）。设计稿按 **1 CSS px = 1dp** 绘制：

- 上屏：832×468dp（1920×1080 的 1/2.3 缩放，用于设计评审）
- 下屏：640×480dp（1280×960 @320dpi）
- 单屏：只做横屏，复用上屏或下屏尺寸；竖屏不在设计范围内

## 文件

| 文件 | 内容 |
| --- | --- |
| `index.html` | 设计稿总览、页面入口、Token 可视化 |
| `duo-home.html` | 双屏主页：上屏设备详情（只读）+ 下屏设备列表、连接/文件/观看/开机/更多 |
| `duo-stream.html` | 双屏串流：上屏桌面 + 下屏工具箱（触摸板/键盘/快捷键/状态）；键盘内置拼音 + 系统输入法按钮 |
| `duo-control-center.html` | **串流内控制中心**：画面与显示 / 外设 / 安全 / 更多；待应用与“应用并重连” |
| `duo-files.html` | **文件传输**：上屏只读总览 + 下屏“浏览/队列/存储”单一操作台 |
| `duo-display.html` | **多屏协作**：上屏预览（只读）+ 下屏显示器 / 超级屏 / 虚拟屏 |
| `duo-keymap.html` | **按键映射参考稿（未排期）**：官方形态；不做触屏虚拟按键/轮盘 |
| `duo-tools.html` | **工具**：观看模式 / 远程开机 / 终端 / 端口映射 |
| `duo-login.html` | 扫码登录：上屏二维码 + 下屏说明 |
| `duo-login-sms.html` | 短信验证码登录：上屏说明 + 下屏表单 |
| `duo-settings.html` | 设置：画面/屏幕/外设/安全/网络/外观/关于 + 上屏配置预览 |
| `duo-account.html` | **账号**：摘要、刷新设备、账号与安全、二次确认退出 |
| `single-landscape-home.html` | **单屏横屏主页**：上屏 832×468 / 下屏 640×480 可切换，master-detail |
| `single-control-center.html` | 单屏控制中心：832 左侧分类 / 640 顶部页签；底部应用并重连 |
| `single-files.html` | 单屏文件：832 浏览/队列/存储三栏，640 三页签 |
| `single-display.html` | 单屏多屏：显示器/超级屏/虚拟屏 |
| `single-tools.html` | 单屏工具：观看/开机/终端/端口 |
| `single-account.html` | 单屏账号：摘要 + 动作 |
| `single-settings.html` | 单屏设置：832 分类+选项，640 顶部分类页签 |
| `single-keymap.html` | 单屏按键映射参考稿（未排期） |
| `single-login.html` | 单屏登录：扫码/短信同屏页签 |
| `single-landscape-stream.html` | **单屏横屏串流**：视频 + 工具箱抽屉，手柄直通/本地切换，内置拼音 + 系统输入法 |
| `focus.md` | **手柄焦点与屏幕所有权**：单焦点树、不切屏的理由、键位、浮层、串流直通/本地、单屏规则 |
| `single.js` | 单屏尺寸切换 + 640 页签面板 |
| `tokens.md` | **UI 设计 Token 文档**（颜色/字体/间距/组件/交互/Compose 映射） |
| `spec.md` | **交互与状态规格**（导航/登录/会话/输入归属/键盘/宿主/异常/验收） |
| `mockup.css` | 共享样式；顶部 `:root` 是颜色/圆角/字体变量 |
| `mockup.js` | 亮色、缩放、Tab、IME 浮层交互 + 手柄焦点树/HUD（单焦点、L1/R1 页签、START 说明） |
| `sprite.js` | 内联 SVG 图标（不依赖网络） |

## 交互预览

- 任意 Tab：点击切换。
- `duo-stream.html` 键盘输入行（右侧展开图标）：打开内置拼音浮层；
  浮层内“系统输入法”切到 Gboard/搜狗示意，键盘面板的“系统”按钮可直接唤起。
- `duo-stream.html` 顶部菜单按钮 / 系统返回示意：打开本地菜单；手柄模式 Chip 可切“直通电脑/本地操作”。
- 单屏横屏页顶部有“上屏 832×468 / 下屏 640×480”切换。
- 深链接：
  - `duo-stream.html?tab=keys` 直接打开键盘面板
  - `duo-stream.html?ime=1` 直接打开内置拼音浮层
  - `duo-stream.html?sysime=1` 直接打开系统输入法状态
  - `duo-stream.html?lang=en` 打开英文模式（键盘“中/英”切换后的状态）
  - `duo-stream.html?menu=1` 直接打开本地菜单
  - `duo-control-center.html?panel=peripheral` / `security` / `more` 直接打开控制中心分组
  - `duo-control-center.html?panel=peripheral&sub=peri:audio` 直接打开声音/麦克风
  - `duo-control-center.html?solo=1` 单屏 832×468 预览
  - `duo-settings.html?tab=screen|device|security|network|appearance|about` 直接打开设置分类
  - `duo-account.html` 账号页
  - `duo-control-center.html?state=pending` / `viewonly` / `applied` 预览待应用/仅观看/重连中
  - `duo-files.html` / `duo-display.html` / `duo-keymap.html` / `duo-tools.html?tool=watch|wake|terminal|ports`
  - `single-landscape-stream.html?ime=1` 打开单屏内置拼音浮层
  - `single-landscape-stream.html?sysime=1` 打开单屏系统输入法状态
  - `single-landscape-stream.html?menu=1` 打开单屏本地菜单
  - 任一 `single-*.html?size=bottom` 直接看 640×480 下屏布局
  - `single-login.html?panel=sl-sms` 直接看短信登录；其它单屏页支持 `?panel=<data-sa-panel>`
  - 单屏页面内左上角“上屏 832×468 / 下屏 640×480”可切换宿主
  - 双屏页焦点环/HUD 由 `mockup.js` 模拟：方向键、Enter=B、Esc=A、Q/E=L1/R1、[ ]=L2/R2、F1/?=START
  - 底部 HUD 是评审辅助，不进入产品界面；产品内只保留页脚键位提示 + 焦点环

## 怎么改

1. **改颜色/圆角/字体**：编辑 `mockup.css` 顶部的 `:root` 变量；`body.light` 是亮色覆盖。
2. **改文案/布局**：直接编辑对应 HTML；每个区块有注释。
3. **改设计规范**：视觉改 `tokens.md`，行为/状态改 `spec.md`，再改 `mockup.css`/HTML，避免文档和稿子漂移。
4. **加页面**：复制一个现有 HTML，改内容；共享样式已经包含设备卡、工具箱、键盘、设置等组件。

## 本版新增（2026-11-18 第二回：手柄焦点回改）

- **取消上下屏焦点切换**：双屏页上屏只读，下屏一棵焦点树；L1/R1 改为一级页签/分类，
  L2/R2 二级页签。理由与键位见 `focus.md`。
- **文件页重做**：上屏只保留传输总览；浏览/队列/存储全部收进下屏，不再出现
  “上屏选文件、下屏点下载”的跨屏操作。
- **全页面单屏版**：新增 `single-control-center/files/display/tools/account/settings/keymap/login`，
  加上已有的 `single-landscape-home/stream`，每个页面都有 832×468 / 640×480 两套布局。
- **页面流入口**：主页下屏动作条、串流本地菜单“页面”、控制中心“更多”、设置齿轮互相可达；
  `index.html` 新增“页面流”表。
- **手柄焦点运行时**：`mockup.js` 增加焦点树、焦点环、L1/R1/L2/R2、START 说明、
  浮层独占焦点、手柄轮询；每页首屏都有可见焦点环。
- 按键映射页改为**参考稿 · 未排期**：Duo 手柄常驻，不做触屏虚拟按键/轮盘。

## 本版新增（2026-11-18 第一回）

- 新增**串流内控制中心**：`画面与显示 / 外设 / 安全 / 更多`，把官方放在串流内的
  分辨率、帧率、画质、码率、缩放、手柄、触摸板、剪贴板、仅观看都放进同一面板；
  画面类改动进入“待应用”，底部统一“应用并重连”。
- 新增**文件传输**：上屏远程文件浏览，下屏下载/上传队列、暂停、断点续传和本机存储。
- 新增**多屏协作**：顶部屏幕页签、`+` 创建虚拟屏、显示器切换、超级屏 4K/144、
  虚拟屏删除与物理屏关闭提示。
- 新增**按键映射**：方案列表、虚拟按键预览、大小/透明度/触发方式、WASD 轮盘、宏序列。
- 新增**工具页**：观看模式（只看不控/批注）、远程开机（WOL）、PowerShell 终端、端口映射。
- 新增**账号页**：摘要、刷新设备、账号与安全、二次确认退出。
- 设置改为 7 个可切换分类（画面/屏幕/外设/安全/网络/外观/关于），每类都有完整选项；
  P2 项（小窗、亮度联动、断开锁屏、防窥、更新检查）以“后续”占位，不伪装成已可用。
- 主页设备详情增加**连接偏好**摘要（1080p · 60 · 高清）和文件传输/观看模式/远程开机/
  重启关机快捷动作；设置重组为 `画面/屏幕/外设/安全/网络/外观/关于`。
- `mockup.css` 增加控制中心、文件、显示器、按键映射、工具、子 Tab、快捷动作等组件；
  `sprite.js` 增加 eye/folder/sliders/pause/play/trash/edit/cast 图标。
- `tokens.md` 第 11 节、`spec.md` 第 15 节记录新组件的尺寸和状态机。

## 本版新增（2026-11-11）

- **单屏只做横屏，竖屏不考虑**：删除原 `single-home.html` / `single-stream.html` 竖屏参考稿；
  新增 `single-landscape-home.html` / `single-landscape-stream.html`，
  同一个 UI 可切上屏 Activity（832×468dp）或下屏 Presentation（640×480dp）。
- **单屏跑下屏**：整个 UI 在 Presentation 里，系统 Back/IME 由 Presentation 自己接；
  上屏 Activity 只做黑色宿主。不要用 Dialog/AlertDialog，所有模态做 Compose overlay。
- **手柄提示改为 B 连接 / A 返回**（Duo 实体丝印）；输入入口归一化为 canonical Xbox，
  本地 UI 的 B/A 语义不影响远端 Xbox 映射。
- **串流手柄默认直通电脑**；本地菜单只走系统 Return/Back 或触摸按钮，不占用任何手柄键。
  只有切到“本地操作”才暂停远端口柄，切换时先发零值 + `gpd_disconnect`，切回时重连。
- 主页/设置/登录的 B/A 提示已同步更新；工具箱顶栏新增“手柄 直通电脑/本地操作” Chip。
- 新增 `spec.md`：补齐导航、登录、设备列表、会话状态机、输入所有权、键盘/IME 状态机、
  双屏/单屏宿主生命周期、错误文案矩阵、设置默认值与验收清单。
- 设置页补充“屏幕模式（自动/双屏/单屏）”和“单屏宿主（自动/上屏/下屏）”；单屏抽屉明确
  “默认 42% / 键盘页 58% / 最大 68%”；返回优先级明确为 IME → 浮层 → 本地菜单 → 页面。
- **键盘改为内置拼音 + 系统输入法双路径**：键盘 Tab 自带拼音候选条（本地词典，空格上屏首选、数字键选候选），
  键盘底部用“中/英”键切换中英文，不再单独放“拼音”按钮；中文提交走 `text_input`，英文/控制键走 VK。
  同时保留“系统输入法”按钮唤起 Gboard/搜狗，系统 IME 可见时隐藏内置键盘，避免双键盘叠加。

## 当前建议先定的 15 件事

1. 主色：冷青 `#6CCBFF`，是否换品牌色？
2. 焦点环：白环 + 青色柔光，是否改纯青/双色？
3. 设备卡：下屏 63dp × 4 张，是否改 68dp × 5 张？
4. 键盘：QWERTY + 功能键 + 修饰键，是否需要第二页符号/功能键？内置拼音词典规模与候选数（默认 5–7 个）？
5. 工具箱 Tab：触摸板/键盘/快捷键/状态 四个，是否合并？
6. 单屏抽屉：默认 42%、键盘页 58%、最大 68%，是否调整？
7. 单屏主页分栏：左 56% / 右 44%，640 宽度下是否改“列表全宽 + 详情浮层”？
8. 单屏默认宿主：自动（优先上屏）/ 固定上屏 / 固定下屏？
9. 串流默认手柄模式：直通电脑 / 本地操作？
10. 上屏详情：左信息 + 右只读状态卡，是否改上下布局/大图背景？
11. 系统输入法在下屏 Presentation 的兼容性：需真机验证；不支持时只保留内置拼音并提示。
12. 控制中心默认打开哪个分组（画面与显示推荐）/是否记住上次分组？
13. 双屏页是否同意“上屏只读、下屏单焦点树、不做切屏键”的输入契约？（当前稿已按此改）
14. 画面类改动是否必须“应用并重连”，还是允许逐项立即重连？
15. 文件传输默认保存路径与“下载完成通知”是否在 v1 做？

## 备注

- 设计稿里的二维码是 CSS 占位图，实际由 ZXing 根据 `qrcode_jump_url` 生成。
- 设计稿里的桌面是 CSS 占位图，实际是 WebRTC `SurfaceViewRenderer` 的视频。
- 上屏在串流时默认无 OSD；左上角小统计可通过设置开关。
- 亮色主题只用于列表/详情/设置；视频始终黑底。
