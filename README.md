# 农屿 · 翻新版（nongyu-app-plus）

农屿 App 翻新版的 Android 原生客户端工程，基于 Kotlin + Jetpack Compose 构建。

> 本工程目前处于**骨架阶段**：仅包含可编译、可打包、可启动的最小工程结构与占位首页，视觉与业务尚未接入设计稿。

## 技术栈

| 项 | 版本 | 说明 |
| --- | --- | --- |
| Gradle | 9.4.1 | AGP 9.x 要求 Gradle 9.1+ |
| Android Gradle Plugin | 9.2.1 | 该版本支持 compileSdk 36 |
| Kotlin | 2.3.0 | AGP 9 内置 Kotlin 支持，无需单独应用 kotlin-android 插件 |
| Compose BOM | 2025.12.00 | **不可直接升级到 2026.x**，见下方说明 |
| JDK | 17+（推荐 21） | 编译与 Gradle 运行均用 JDK 21 验证通过 |

### 关于 Compose BOM 的版本锁定

`compose-bom:2026.09.00` 中的 `runtime-saveable` 要求依赖方 `compileSdk >= 37`，
而当前开发机的 Android SDK 仅安装到 `android-36`，直接使用会导致构建失败：

```
Dependency 'androidx.compose.runtime:runtime-saveable-android:1.12.1' requires libraries
and applications that depend on it to compile against version 37 or later of the Android APIs.
```

因此本工程锁定 `2025.12.00`。待 SDK 升级到 `android-37` 后，可同步放开该版本。

## 环境要求

- JDK 17 或更高版本（已在 JDK 21 上验证）
- Android SDK：`compileSdk = 36`、`build-tools 36.0.0`
- 需将 `ANDROID_HOME` 指向 Android SDK 根目录

## 构建

```bash
# 发布包（R8 + 资源收缩 + 不 debuggable）
./gradlew :app:assembleRelease

# 产物位置
# app/build/outputs/apk/release/app-release.apk
```

调试时用 `./gradlew :app:assembleDebug`（产物在 `app/build/outputs/apk/debug/app-debug.apk`）。

Windows 下使用 `gradlew.bat`：

```powershell
.\gradlew.bat :app:assembleRelease
```

## 工程结构

```text
nongyu-app-plus/
├── app/
│   ├── build.gradle.kts            # 应用模块构建配置
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nongyu/appplus/
│       │   ├── MainActivity.kt     # 唯一入口 Activity，仅挂载 Compose 根节点
│       │   └── ui/
│       │       ├── NongyuApp.kt    # 应用根组件（当前为占位首页）
│       │       └── theme/Theme.kt  # Material 3 主题（当前为默认配色）
│       └── res/                    # 字符串、主题、图标、备份规则
├── gradle/
│   ├── libs.versions.toml          # 版本目录，统一依赖版本管理
│   └── wrapper/
├── build.gradle.kts                # 根构建脚本，仅声明插件版本
├── settings.gradle.kts
└── gradle.properties
```

## 应用标识

| 项 | 值 |
| --- | --- |
| applicationId | `com.nongyu.appplus` |
| minSdk | 26 |
| targetSdk / compileSdk | 36 |
| 应用名称 | 农屿 |

## 当前阶段的边界

- 视觉基调**未定**：主题使用 Material 3 默认配色，未建立 `design-system` 准则，等待设计稿接入。
- 无业务逻辑：无网络层、无状态管理、无路由、无依赖注入，均为后续接入。
- 未做运行时验证：本次仅验证到「编译打包成功」，尚未在真机/模拟器上运行。

## 后续计划

设计稿到位后，按以下顺序推进：设计准则建立 → 主题 Token 落地 → 路由与骨架页面 → 业务模块接入。
