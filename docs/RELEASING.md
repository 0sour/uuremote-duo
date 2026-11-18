# 发布构建与签名

DuoRemote 默认只构建 debug APK。发布包需要你自己的 Android 签名材料，仓库不会提供或接收任何 keystore。

## 1. 生成 keystore（只做一次）

```bash
keytool -genkeypair -v \
  -keystore duoremote-release.jks \
  -alias duoremote \
  -keyalg RSA -keysize 4096 -validity 10000
```

把 `.jks` 放到仓库根目录，并在 `.gitignore` 覆盖范围内（已忽略 `*.jks`）。

## 2. 配置 `keystore.properties`

在仓库根目录新建 `keystore.properties`（已忽略）：

```properties
storeFile=duoremote-release.jks
storePassword=...
keyAlias=duoremote
keyPassword=...
```

CI 中也可以改用环境变量：

- `DUOREMOTE_KEYSTORE`：keystore 路径
- `DUOREMOTE_KEYSTORE_PASSWORD`
- `DUOREMOTE_KEY_ALIAS`
- `DUOREMOTE_KEY_PASSWORD`

没有签名配置时，`assembleRelease` 仍可产出未签名 APK，便于本地检查混淆/资源，但不能直接安装分发。

## 3. 构建

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew clean :app:testDebugUnitTest :app:assembleRelease
```

产物：

- `app/build/outputs/apk/release/app-release.apk`
- 未配置签名时为 `app-release-unsigned.apk`

## 4. 校验

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 5. 发布前检查

- `docs/feature-matrix.md` 中 v1 项已验收。
- 不包含 `research/`、官方 APK、key、token、二维码、SDP、IP、账号数据。
- 版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName` 更新。
- 在 Duo 上完成双屏/单屏冒烟测试。
