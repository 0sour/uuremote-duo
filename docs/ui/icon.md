# DuoRemote 应用图标 · A1 定稿

> 2026-11-18 选定。A1「双屏手柄」为最终方向，Bili-Duo 系列语言。

## 图形

- 黑底、白描上屏、青描下屏；扁平双色，不做渐变/阴影。
- 上屏：光标居中偏左，远控信号弧在光标右上并向外展开；信号顶距 28/512。
- 下屏：白色十字方向键 + 经典 ABXY 菱形（青色，保持系列双色）。
- 上下屏间距 16/512；整体内容外框 332/512。
- 单色版仅作为设计稿对比，**不实装**：两个屏幕和手柄细节在单色下会糊在一起。

## Android 资源

- Adaptive icon：
  - 背景 `#0B0D10`（`values/colors.xml`）。
  - 前景 `drawable/ic_launcher_foreground.xml`，108dp 画布，内容缩到约 50dp，保证圆形遮罩不裁切。
  - `mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml`，**没有 `<monochrome>` 层**。
- 兼容 PNG：`mipmap-mdpi`–`xxxhdpi` 的 `ic_launcher.png` / `ic_launcher_round.png`。
- Manifest：`android:icon` 与 `android:roundIcon` 指向上述 mipmap。
- 商店/文档 512：`docs/ui/duoremote-icon-512.png`；矢量源 `docs/ui/duoremote-icon.svg`。

## 应用内品牌位

- `drawable/ic_brand_mark.xml`：64 视口的双色短标（白上屏、青下屏、光标/信号、十字/ABXY），不随系统主题单色化。
- `ui/home/HomeScreen.kt` 的 `DuoBrandMark(size)` 统一使用该短标：主页上屏、设置上屏摘要、账号上屏、登录页顶部/登录品牌行。
- 设置「关于」页顶部增加品牌卡：`ic_brand_mark` 32dp + `DuoRemote` + 版本号。

## 验证

- RP Duo 圆形启动器实测：两块屏、光标、信号弧、十字和 ABXY 均可见，无裁切。
- 上屏设置摘要、关于页品牌卡实测替换成功。
- `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleRelease` 通过。
