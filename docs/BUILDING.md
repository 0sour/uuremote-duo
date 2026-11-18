# 构建与真机调试

> 适用于当前 Windows + Git Bash 环境；不包含任何 key/APK/token。

## 1. 环境

- JDK 17：`C:\path\to\jdk-17`
- Android SDK：`C:\path\to\Android\sdk`（已写入 `local.properties`）
- Gradle wrapper：8.7
- 目标设备：Retroid Pocket Duo（`sdk` 里的 adb 可直接识别）

在 Git Bash 中：

```bash
export JAVA_HOME="/path/to/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## 2. 构建

```bash
cd uuremote-duo
./gradlew :app:assembleDebug
```

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 3. 代理问题

如果 Gradle 报：

```text
Connect to 127.0.0.1:<proxy-port> failed: Connection refused
```

说明 `~/.gradle/gradle.properties` 里配置了本地代理，但代理没有启动。二选一：

1. 启动本地代理（端口 <proxy-port>）；或
2. 本次构建绕过代理：

```bash
./gradlew :app:assembleDebug \
  -Dhttp.proxyHost= -Dhttp.proxyPort= \
  -Dhttps.proxyHost= -Dhttps.proxyPort=
```

不要为了绕过代理而把代理配置提交到仓库。`*.properties` 中的本机配置不要提交。

## 4. 安装/启动（手动）

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.sour.duoremote
adb shell am start -n com.sour.duoremote/.MainActivity
```

Duo 有两个显示：

- 上屏：Activity，1920×1080，`displayId 0`
- 下屏：Presentation，1280×960，`displayId 2`，`FLAG_PRESENTATION`

截图/输入需要指定逻辑 display：

```bash
adb shell input -d 2 tap 640 480       # 下屏逻辑坐标
adb shell input -d 2 keyevent 4        # 下屏 Back
```

`screencap` 使用 SurfaceFlinger 的物理 display id（用
`adb shell dumpsys SurfaceFlinger --display-id` 查看），不要和逻辑 `displayId` 混用。
不指定 `-d` 时系统会提示 “Multiple displays were found”，默认截取第一块屏，结果不稳定：

```bash
adb exec-out screencap -d <top-display-id> -p > build/top.png   # 上屏
adb exec-out screencap -d <bottom-display-id> -p > build/down.png  # 下屏
```

把占位符换成 `dumpsys` 输出的当前物理 id；它们可能随设备/系统更新变化。
`input ... -d 2 keyevent` 注入手柄键时，WindowManager 可能把副屏切为焦点并让应用退到
后台；不要用注入键判断手柄逻辑，最终以真实手柄为准。

Git Bash 下 `adb push`/`pull` 的 `/sdcard/...` 会被路径转换；用 `MSYS_NO_PATHCONV=1`：

```bash
MSYS_NO_PATHCONV=1 adb push app.apk /sdcard/Download/app.apk
```

## 5. 单元测试

```bash
./gradlew :app:testDebugUnitTest
```

当 Windows 项目路径含中文时，Gradle test worker 的
classpath argfile 编码可能不一致，表现为：

```text
ClassNotFoundException: com.sour.duoremote...Test
```

用 GBK 作为 Gradle JVM 编码重跑即可（测试已通过）：

```bash
./gradlew :app:testDebugUnitTest \
  -Dorg.gradle.jvmargs="-Xmx3072m -Dfile.encoding=GBK"
```

测试覆盖：

- `RequestSignerTest`：HMAC-SHA256、X-Param 头排序/过滤。
- `DexKeyExtractorTest`：从本地官方 TV 3.5.0 APK 提取 key，只校验 SHA-256，不打印 key；
  APK 不存在时自动 skip。

## 6. 增量构建产物异常（重要）

AGP 8.4 在 Windows/中文路径/增量构建下出现过两类问题：

1. 源码已编译，但合并 dex 漏类，安装后启动报：

```text
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/sour/duoremote/...
Caused by: java.lang.ClassNotFoundException
```

2. 中间产物已经更新，但 `packageDebug` 复用了旧 APK 中的 `classes*.dex`。表现是
   源码里的旧文案/旧逻辑仍然出现在界面上，例如按钮点击调用旧空实现，看起来“点击没反应”。

处理方式：

```bash
./gradlew clean :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装前核对 APK 内容，而不是只看 `app/build/tmp`：

```bash
unzip -Z1 app/build/outputs/apk/debug/app-debug.apk | grep '^classes.*\.dex$'
# 对可疑类做 dexdump；对旧文案做 grep（先解出 classes*.dex）
```

只要改了 Compose/UI 或协议类后出现“代码已改但真机行为没变”，先做一次 clean 重建并校验
APK，再怀疑业务代码。不要只删 `app/build/tmp`。

## 7. 调试约定

- 不要在真机自动连点/自动按键；需要操作设备时先确认。
- `BuildConfig.DEBUG` 才显示“宿主调试”入口；正式版放到设置 > 屏幕。
- 日志不得包含 token、二维码内容、SDP、IP、手机号、签名 key。
- 真机验证前先确认当前前台是 `com.sour.duoremote`，避免误操作其他应用。
