# 在 IntelliJ IDEA 里编译、运行、看到界面并调试（nongyu-app-plus / AGP 9.2.1）

> 调研范围：只做事实核查，不含业务代码改动。所有命令均为 PowerShell 形态，可直接粘贴。
> 本机实测数据采集时间：2026-11-06。

---

## 1. 结论摘要

**最快能在电脑上看到界面的路径（推荐先做这条）**：

1. 你**没有** Android Studio，但本机装的 IDE 其实是 **IntelliJ IDEA 2025.3.2（build 253.30387.90）**，只是安装目录名写成了 `IntelliJ IDEA 2024.3.7`（见 §2.1，这是本报告最重要的一处事实纠正）。
2. 该 IDE 的 Android 插件（`org.jetbrains.android`）**当前没装**，只有 `android-gradle-dsl` 等少量模块；且它的可用版本（253.30387.90）基于 Android Studio Narwhal Feature Drop，**官方支持不到 AGP 9.2.1**。
3. 所以「IDEA 里点点点就起来」这条路当前走不通；**先用命令行把链路跑通**（工程已经能构建出 `app-debug.apk`），再决定要不要升级 IDE：
   - 装 `cmdline-tools`（本机 SDK 里目前**没有**）→ 装 `emulator` + `system-images;android-36;google_apis;x86_64` → `avdmanager create avd` → `emulator -avd` 启动 → `gradlew :app:installDebug` → `adb shell am start`。
   - 全流程约需下载 **≈2.4 GB**（cmdline-tools 148 MB + emulator 439 MB + 系统镜像 1.8 GB），C 盘剩余 ≈98 GB，够用。
4. 「不装模拟器就想看到界面」只有 `@Preview`（需要 IDE 侧 Android 插件/Design Tools 渲染，本机未装）或截图测试（Paparazzi / Roborazzi，纯 JVM 出 PNG，**可以看，但不是实时界面**）。
5. 有安卓手机的话，**真机是当前性价比最高的方案**：不需要模拟器镜像，只需 USB 驱动 + 开发者选项，`gradlew :app:installDebug` 即可上机。

---

## 2. 兼容性结论

### 2.1 先纠正一处关键事实：本机 IDE 是 2025.3.2，不是 2024.3.7

| 证据来源 | 实际值 |
| --- | --- |
| `C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.7\build.txt` | `IU-253.30387.90` |
| 同目录 `product-info.json` | `"version": "2025.3.2"`、`"buildNumber": "253.30387.90"`、`"majorVersionReleaseDate": "20251208"` |
| 注册表卸载项 `DisplayName` | `IntelliJ IDEA 2025.3.2  253.30387.90` |

**结论**：安装目录名（2024.3.7）是历史遗留，实际二进制是 **IntelliJ IDEA 2025.3.2**。下面所有兼容性判断按 2025.3.2 算。
（附带影响：IDEA 2025.3.x 自带 JBR 21，且 2025.3 才加入 Gradle 9 支持；如果它真的是 2024.3.7，连 Gradle 9.4.1 都跑不动。）

### 2.2 工程侧的硬约束（官方兼容矩阵）

| 组件 | 本工程 | 官方要求（min / default） | 结论 |
| --- | --- | --- | --- |
| AGP | 9.2.1 | — | 当前最新稳定线之一（另有 9.3/9.4/9.5） |
| Gradle | 9.4.1 | AGP 9.2 要求 **9.4.1 / 9.4.1** | ✅ 精确命中 |
| JDK | `JAVA_HOME=jdk-25`，可用 jdk-17/21/25 | AGP 9.2 要求 JDK **17 / 17**；Gradle 9.4.1 允许运行在 **Java 17–27** | ✅ 能跑；建议显式指定 21（见 §3.2） |
| build-tools | 36.0.0 已装 | AGP 9.2 要求 **36.0.0** | ✅ |
| Kotlin（KGP） | 只应用 `org.jetbrains.kotlin.plugin.compose` 2.3.0（AGP 9 起内置 Kotlin） | AGP 9.2 要求 KGP ≥ **2.2.10** | ✅ |
| compileSdk / targetSdk | 36 | AGP 9.2 最高支持 API **37.0**；API 36 最低要求 AS Meerkat 2024.3.1 Patch 1 / AGP 8.9.1 | ✅ |
| Gradle 内置 Kotlin | — | Gradle 9.4.0 内置 Kotlin 2.3.0 | 一致，无冲突 |

来源：
- AGP 9.2 兼容表（Gradle 9.4.1、JDK 17、Build Tools 36.0.0、KGP 2.2.10、最高 API 37.0）：<https://developer.android.com/build/releases/agp-9-2-0-release-notes>
- AGP ↔ Gradle 最低版本总表（9.2 → 9.4.1）：<https://developer.android.com/build/releases/about-agp>
- Gradle 9.x Java 兼容矩阵（运行 Gradle：17–27；Java 25 自 Gradle 9.1.0 起支持；内置 Kotlin 2.3.0 对应 Gradle 9.4.0）：<https://docs.gradle.org/current/userguide/compatibility.html>
- AGP 9.0 起内置 Kotlin、不再需要 `org.jetbrains.kotlin.android`：<https://developer.android.com/build/releases/agp-9-0-0-release-notes>

### 2.3 AGP 9.x 官方要求的最低 IDE 版本

Google 的官方表（Android Studio ↔ AGP 支持区间）：

| Android Studio 版本 | 支持 AGP 区间 |
| --- | --- |
| **Quail 1 \| 2026.1.1** | 7.1–**9.2** |
| Quail 2 \| 2026.1.2 | 7.1–9.3 |
| Panda 4 \| 2025.3.4 | 7.1–9.2 |
| Panda 3 \| 2025.3.3 | 7.0–9.1 |
| Panda 2 \| 2025.3.2 | 7.0–9.1 |
| Panda 1 \| 2025.3.1 | 7.0–9.0 |
| Narwhal Feature Drop \| 2025.1.2 | 4.0–8.12 |

来源：<https://developer.android.com/build/releases/about-agp>（“Android Gradle plugin and Android Studio compatibility”）
另：AGP 9.x 要求配套 IDE 能读懂新 DSL —— AGP 9.0 起 `android` DSL 只实现新的公开接口，旧类型被隐藏，读不懂的 IDE 会在 sync 阶段直接报错（同链接 AGP 9.0 release notes）。

**所以 AGP 9.2.1 的“官方最低 IDE”= Android Studio Quail 1（2026.1.1）**；等价地，Panda 4（2025.3.4）也是上限 9.2。

### 2.4 IDEA 侧：2025.3.2 打不开 AGP 9.2.1（结构性缺口）

IntelliJ IDEA 的 Android 支持**不内置**，要装两个 Marketplace 插件：`Android`（`org.jetbrains.android`，插件 ID 22989）与 `Android Design Tools`（`com.android.tools.design`，插件 ID 22990）。
来源：<https://www.jetbrains.com/help/idea/android.html>、<https://www.jetbrains.com/help/idea/2024.1/create-your-first-android-application.html>

Android 插件是**按 IDE build 分发**的（每个 IDE 版本只认自己 build 段号的插件）。查 Marketplace 版本 API（`plugins.jetbrains.com/api/plugins/22989/updates`）得到：

| 插件版本 | 兼容 IDE | 基座 Android Studio | 该基座支持的最高 AGP |
| --- | --- | --- | --- |
| **253.30387.90** | IDEA **2025.3.2–2025.3.6.2** ← 本机 | Narwhal Feature Drop | 8.12 |
| 253.33813.99 | IDEA 2025.3.6–2025.3.6.2 | Narwhal Feature Drop | 8.12 |
| 261.27258.136 | IDEA 2026.1.5–2026.1.5.1 | Panda 1 | 9.0 |
| 262.11879.100 | IDEA 2026.2.4 | Panda 2（“including support for **AGP 9.1.0**”） | 9.1 |
| 263.7307.200 | IDEA **2026.3** | **Quail 2** | 9.3 |

（`compatibleVersions` 字段是官方数据；最后一列是把它对齐到上表得出的推断，不是插件自己声明的。）

**JetBrains 官方论坛的答复也印证了这个上限**：IntelliJ IDEA 的 Android 插件尚不支持最新 AGP，用较新 AGP 的工程会报
`Unsupported method: AndroidArtifact.getPrivacySandboxSdkInfo()`，
官方给的最新区间是「IDEA 2026.1 + 插件 → 最高 AGP 9.0.0-alpha06」，并指向未完成的 issue [IDEA-385007（AGP 9 compatibility）](https://youtrack.jetbrains.com/issue/IDEA-385007) 与 [IDEA-322286](https://youtrack.jetbrains.com/issue/IDEA-322286)。
来源：<https://intellij-support.jetbrains.com/hc/en-us/community/posts/34072502627218-Android-project-fails-to-build-works-fine-in-Android-Studio-Unsupported-method-AndroidArtifact-getPrivacySandboxSdkInfo>

同一帖给出的**隐藏开关**（官方口径的 workaround，属于“可能能 sync、Android 专属功能可能不正常”）：
```
Help | Edit Custom Properties...  → 加一行：idea.is.internal=true
重启 → Ctrl+Shift+A → Edit Studio Flags → 打开 gradle.ide.support.future.agp.versions
```
⚠️ 该帖里提问者反馈**照做后仍然同样的报错**，所以这条只能算“值得一试”，不是解法。

### 2.5 如果 2024.3.7/2025.3.2 不够，需要哪个版本

| 目标 | 需要 | 依据 |
| --- | --- | --- |
| 只想在 **IDEA** 里 sync/build/运行 AGP 9.2.x | **IntelliJ IDEA 2026.3** + Android 插件 263.7307.200（基座 Quail 2，AGP ≤9.3） | 插件 API `compatibleVersions` + Google AS↔AGP 表（推断） |
| 想要最省事、和 AGP 9.2.1 100% 对得上 | **Android Studio Quail 1（2026.1.1）或更新** | Google 官方 AS↔AGP 表 |
| 只想跑通现有工程（不折腾版本） | 保持现有 IDEA，**用命令行 Gradle + adb**，IDE 只当代码编辑器 | §3/§4 |

### 2.6 IDEA 与 Android Studio 的差异（哪些功能只有 AS 有）

先给确定的事实：
- 两者共用同一份 Android 插件代码库；AS 里的改动会陆续进 IDEA 插件，但**IDE 版本对 AGP 的支持总是落后**（同上官方论坛答复 + 插件版本表）。
- IDEA 官方 Android 教程（2024.1）里确实演示了 **Device Manager 建 AVD**、**Android 运行配置**、**UI Designer 渲染 XML 布局**——说明这些能力在 IDEA 装好两个插件后是存在的。
  来源：<https://www.jetbrains.com/help/idea/2024.1/create-your-first-android-application.html>
- JetBrains 当前版本（2026）的 IDEA Help 已把 Android 章节压缩成一句话：“Android 开发支持不内置，请安装 Android 与 Android Design Tools 插件；详细文档见 IDEA 早期版本文档”，并注明“完整使用指南去 Android Studio 用户指南”。
  来源：<https://www.jetbrains.com/help/idea/android.html>
- Compose `@Preview` 的渲染器（layoutlib）是**Android 插件自带的模块**：插件 263.x/253.x 的 `modules` 字段里含 `intellij.libraries.layoutlib`（`plugins.jetbrains.com/api/plugins/22989/updates`）。所以「能不能预览」取决于 Android 插件版本，而不是 IDE 版本。
- Compose Preview 的官方定位（Kotlin 官方文档）：「在 IDE（IntelliJ IDEA 与 Android Studio）里渲染，无需启动模拟器」，但**KMP 场景还要求 Kotlin Multiplatform IDE 插件 + CMP 1.10+ 的 `@Preview`**。
  来源：<https://kotlinlang.org/docs/multiplatform/compose-previews.html>

| 功能 | IDEA（装齐 Android 插件） | 说明 |
| --- | --- | --- |
| Compose `@Preview` 渲染 | ⚠️ 取决于插件版本，**未在本机验证** | 需要 Android 插件 + Android Design Tools + layoutlib；本机两个插件都没装 |
| Layout Inspector | **未验证** | 未见 IDEA 侧文档明确说明 |
| Device Manager（建/启动 AVD） | ✅（IDEA 2024.1 官方教程有截图流程） | 需要 Android 插件 |
| APK Analyzer | **未验证**（社区普遍反馈 IDEA 缺 “Android APK Support” 插件，二手来源，不作为结论） | — |
| Logcat 面板 | **未验证（当前 2025.3 文档已不描述）** | 老版 IDEA 文档有 “Android 工具窗口 → Logcat 页”（13.0/14.0/15.0 Help），现代版本无对应章节。**替代：`adb logcat`（见 §4.3）** |
| AGP / 新 DSL 支持 | ❌ 落后于 AS | 见 §2.4 |

---

## 3. 方案 A：IDEA + 模拟器（本机唯一“真的看到界面”的完整路径）

前置事实：
- SDK 在 `C:\Users\LENOVO\AppData\Local\Android\Sdk`，现有 `platforms/android-36`、`build-tools/{34,35,36}`、`platform-tools`、`ndk`、`cmake`、`licenses`。
- **没有** `emulator/`、**没有** `system-images/`、**没有** `cmdline-tools/`（所以 `sdkmanager`、`avdmanager` 目前都不可用，必须先装）。
- `~/.android/avd` 为空；`adb` 已存在（`platform-tools/adb.exe`，1.0.41 / 34.0.5）。
- 机器：Windows 10.0.26200（25H2）、AMD64、24 逻辑核、C 盘剩余 ≈98.6 GB；`hvservice`、`vmcompute` 服务在跑（Hyper-V/WHPX 可用）。
  （WMI 被沙箱拒绝，CPU 型号与 HypervisorPresent 标志未取到 —— 见 §7 未验证清单。）

### 3.1 安装 cmdline-tools（本机缺这一步）

官方要求：`cmdline-tools` 解压后放进 `sdk/cmdline-tools/latest`，里面的内容要**直接**是 `bin/`、`lib/`、`NOTICE.txt`、`source.properties`。
来源：<https://developer.android.com/tools/sdkmanager>（“sdkmanager 位于 Android SDK Command-Line Tools 包中”）

Windows 包（最新 `cmdline-tools;latest` = rev 23.0，`commandlinetools-win-16111833_latest.zip`，154,957,218 字节 ≈ 148 MiB），URL 取自官方仓库索引 `https://dl.google.com/android/repository/repository2-3.xml`：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$zip = "$env:TEMP\cmdline-tools-win.zip"
Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip" -OutFile $zip
Expand-Archive -Path $zip -DestinationPath "$env:TEMP\cmdline-tools-extract" -Force
New-Item -ItemType Directory -Force "$sdk\cmdline-tools\latest" | Out-Null
Copy-Item "$env:TEMP\cmdline-tools-extract\cmdline-tools\*" "$sdk\cmdline-tools\latest\" -Recurse -Force
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --version
```
> 若 `Invoke-WebRequest` 报 TLS/证书错误，改用浏览器下载同一个 URL 再解压到同一位置。
> 我在这台机器上用 `curl` 直连 `dl.google.com` 时 schannel 报 `SEC_E_NO_CREDENTIALS`（curl 自身凭证问题），用 Invoke-WebRequest 或 Python 均正常 —— 所以这是本地 curl 环境问题，不是网络不可达。

### 3.2 配置 `local.properties` + Gradle JDK

**`local.properties`（工程根，与 `settings.gradle.kts` 同级）**。`local.properties` 是 `java.util.Properties` 格式，`load()` 里反斜杠是转义符、`=`/`:` 也要转义（规范见 <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html#load(java.io.Reader)>）。两种正确写法：

```properties
# 写法一：正斜杠（最省事，Gradle 接受）
sdk.dir=C:/Users/LENOVO/AppData/Local/Android/Sdk
```
```properties
# 写法二：原生反斜杠，必须双写
sdk.dir=C\:\\Users\\LENOVO\\AppData\\Local\\Android\\Sdk
```
❌ 错误写法：`sdk.dir=C:\Users\LENOVO\AppData\Local\Android\Sdk`（`\U`、`\A`、`\L`、`\S` 会被 Properties 吞掉，路径静默变错）。

生成命令（PowerShell）：
```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$p = Join-Path (Get-Location) "local.properties"
"sdk.dir=" + ($sdk -replace '\\','/') | Set-Content -Path $p -Encoding ASCII
Get-Content $p
```

**Gradle JDK**（官方路径）：
`File → Settings（Ctrl+Alt+S）→ Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM`，选 `21`（本机 `C:\Program Files\Java\jdk-21`）。
来源：<https://www.jetbrains.com/help/idea/2024.3/gradle.html>（“Check Gradle JVM and language level”、Gradle settings）
> 为什么不选 JDK 25：AGP 9.2 的最低要求是 17、默认 17，Gradle 9.4.1 虽支持运行在 25，但 IDEA 自身跑在 JBR 21 上，混用 25 容易踩 toolchain/字节码不一致；21 是工程 `compileOptions` 17 的稳妥宿主。

**Android SDK 入口**：
`File → Project Structure → Platform Settings → SDKs`，新增/确认一个 “Android SDK” 条目，`SDK home` 填上面那个路径，`Java SDK` 选 JDK 21。
来源：<https://www.jetbrains.com/help/idea/2024.1/create-your-first-android-application.html>（“Configure project JDK” 一节）

**装插件**（本机当前缺失）：
`Settings → Plugins → Marketplace`，装 **Android** 与 **Android Design Tools**，重启。
来源：<https://www.jetbrains.com/help/idea/android.html>、<https://plugins.jetbrains.com/plugin/22989-android>、<https://plugins.jetbrains.com/plugin/22990-android-design-tools>

### 3.3 装 emulator + 系统镜像（android-36 有哪些变体，本机核实过的清单）

数据来自 Google 官方仓库索引（我直接解析了 `sys-img2-3.xml`，`<size>` 为单位）：
- `google_apis`：<https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-3.xml>
- `google_apis_playstore`：<https://dl.google.com/android/repository/sys-img/google_apis_playstore/sys-img2-3.xml>
- `default`(AOSP)：<https://dl.google.com/android/repository/sys-img/android/sys-img2-3.xml>
- `aosp_atd`：<https://dl.google.com/android/repository/sys-img/aosp_atd/sys-img2-3.xml>
- `google_atd`：<https://dl.google.com/android/repository/sys-img/google_atd/sys-img2-3.xml>

**android-36 系列确实存在的包（下载体积，MiB）**：

| 包名 | 体积 |
| --- | --- |
| `system-images;android-36;google_apis;x86_64` | **1807.6** |
| `system-images;android-36;google_apis;arm64-v8a` | 1785.9 |
| `system-images;android-36;google_apis_playstore;x86_64` | 1835.5 |
| `system-images;android-36;google_apis_playstore;arm64-v8a` | 1799.1 |
| `system-images;android-36;google_apis_ps16k;x86_64` | 1763.8（16KB page size 变体） |
| `system-images;android-36;default;x86_64` | 805.1（AOSP，可 `adb root`） |
| `system-images;android-36;aosp_atd;x86_64` | 711.0（自动化测试裁剪版，无 UI 体验） |
| `system-images;android-36;google_atd;x86_64` | 819.8 |
| `system-images;android-36.1;google_apis;x86_64` | 1869.7 |
| `system-images;android-37.0;google_apis;x86_64` | 2044.6 |

其他（同一索引）：
| 包 | 体积 |
| --- | --- |
| `emulator`（windows x64，rev 37.3.7） | **439.3** |
| `emulator`（windows x64，rev 37.2.13，上一版） | 434.3 |
| `platform-tools`（windows，r37.0.2） | 7.9 |
| `platforms;android-36`（已装） | 62.8 |
| `build-tools;36.0.0`（windows，已装） | 56.0 |

> x86_64 宿主请选 `x86_64` 镜像（`arm64-v8a` 是给 ARM 宿主的）。
> `default`/`aosp_atd` 是 AOSP 镜像，可 `adb root`；带 Play Store 的镜像为发布密钥签名，不能 root（官方说明：<https://developer.android.com/studio/run/managing-avds> “System images” 一节）。

安装：
```powershell
$sdk    = "$env:LOCALAPPDATA\Android\Sdk"
$sdkman = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"

# 1) 接受许可（会连续问多个 y）
& $sdkman --licenses

# 2) 装模拟器 + 系统镜像（约 2.25 GB 下载）
& $sdkman --install "emulator" "system-images;android-36;google_apis;x86_64"

# 3) 校验
& $sdkman --list_installed
```
想省 ~1 GB 就把镜像换成 `system-images;android-36;default;x86_64`（805 MB，AOSP，无 Google Play 服务 —— 本工程目前只依赖 androidx + Compose，够用；将来要接地图/推送/FCM 再换 `google_apis`）。

### 3.4 建 AVD 并启动

`avdmanager` 的官方语法（`create avd -n name -k "sdk_id" [-c ...] [-f] [-p path]`）：
```powershell
$sdk     = "$env:LOCALAPPDATA\Android\Sdk"
$avdman  = "$sdk\cmdline-tools\latest\bin\avdmanager.bat"
$emu     = "$sdk\emulator\emulator.exe"

# 先看可用的硬件配置 id（不确定就别用 -d）
& $avdman list device

# 建 AVD（最小可用形式）
& $avdman create avd -n pixel36 -k "system-images;android-36;google_apis;x86_64"
# 想指定机型再加 -d，例如（id 必须来自上一条 list device 的输出）
# & $avdman create avd -n pixel36 -k "system-images;android-36;google_apis;x86_64" -d <deviceId>

# 确认
& $avdman list avd
& $emu -list-avds

# 启动（-no-snapshot 冷启动，避免旧快照干扰；GPU 异常时再加 -gpu 参数，见下）
Start-Process -FilePath $emu -ArgumentList "-avd","pixel36","-no-snapshot"
```
来源：`avdmanager` 语法 <https://developer.android.com/tools/avdmanager>；`emulator -avd/-list-avds/-no-snapshot` <https://developer.android.com/studio/run/emulator-commandline>

> 说明：
> - `create avd` 不带 `-d` 也会用默认硬件配置，先跑通再加机型，避免 deviceId 猜错（本机还没有 avdmanager，无法列出真实 id，故不写死）。
> - 黑屏/花屏时可换 GPU 后端：先 `& $emu -help-gpu` 看当前版本支持的值，再 `-gpu swiftshader_indirect`（软件渲染，稳但慢）。
> - 本机 `hvservice`/`vmcompute` 在跑，模拟器可走 WHPX；若报缺少 Hypervisor，可装 `extras;google;Android_Emulator_Hypervisor_Driver`（该包名存在于 `addon2-3.xml` 索引）。

### 3.5 构建 → 安装 → 启动 → 看 logcat（不依赖 IDE 的完整闭环）

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"

# 0) 等模拟器起来（首次冷启动 1–3 分钟）
& $adb wait-for-device
do { Start-Sleep -Seconds 3; $b = (& $adb shell getprop sys.boot_completed).Trim() } while ($b -ne "1")
& $adb devices -l

# 1) 编译 + 安装（工程已有 :app 模块，applicationId = com.nongyu.appplus）
.\gradlew.bat :app:installDebug

# 2) 拉起 Launcher Activity（manifest 里是 .MainActivity）
& $adb shell am start -n com.nongyu.appplus/.MainActivity

# 3) 只看本进程日志
$pid_ = (& $adb shell pidof -s com.nongyu.appplus).Trim()
& $adb logcat --pid=$pid_
```
（`com.nongyu.appplus` / `.MainActivity` 来自工程 `app/build.gradle.kts` 与 `app/src/main/AndroidManifest.xml`；`adb logcat --pid=` 与 `adb shell pidof` 属 adb 标准用法，本机未连设备故未实跑验证 —— 见 §7。）

常用变体：
```powershell
# 过滤关键字（崩溃/异常）
& $adb logcat -s AndroidRuntime:E System.err:W
# 清空后重新抓
& $adb logcat -c; & $adb logcat
# 卸载重装
& $adb uninstall com.nongyu.appplus
```

### 3.6 Run Configuration 怎么建

**情况 1：Android 插件已装且能 sync（按 §2.4，AGP 9.2.1 下大概率不行）**
Android 插件提供原生 **Android App** 运行配置，IDE 会自动生成一个，工具栏上还能选 Device Manager 里的虚拟设备直接 Run/Debug。断点、变量、单步都走这套。
来源：<https://www.jetbrains.com/help/idea/2024.1/create-your-first-android-application.html>（“Configure Android virtual device” + “Run the application”）

**情况 2：sync 不通（当前现实）→ 用 Gradle 配置 + adb 兜底**

`Run → Edit Configurations → + → Gradle`，建两条：

| 名称 | 配置 |
| --- | --- |
| `assembleDebug` | Gradle project: 工程根；Tasks: `:app:assembleDebug`；Gradle JVM: 21 |
| `installDebug` | Tasks: `:app:installDebug`（会自动依赖 assemble），运行前需模拟器已启动 |

再建一条 `Run → Edit Configurations → + → Shell Script`（或 External Tool）执行拉起：
```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.nongyu.appplus/.MainActivity
```

**断点调试**（Gradle 配置给不了 Java 断点，要用 JDWP attach）：
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
# 让 app 在入口处挂起等调试器
& $adb shell am start -D -n com.nongyu.appplus/.MainActivity
# 取 pid 并做端口转发（8700 可换）
$pid_ = (& $adb shell pidof -s com.nongyu.appplus).Trim()
& $adb forward tcp:8700 jdwp:$pid_
```
然后在 IDEA 建 `Run → Edit Configurations → + → Remote JVM Debug`，Host `localhost`、Port `8700`，点 Debug 即可断点/单步。
（`am start -D` 与 `adb forward ... jdwp:<pid>` 属 Android 标准调试流程，见 <https://developer.android.com/studio/debug>；本机无设备，未实跑验证。）

---

## 4. 方案 B：IDEA + 真机（当前性价比最高）

### 4.1 手机上要做的事（Android 官方流程）

1. 设置 → 关于手机 → 连点「版本号」7 次进开发者模式；
2. 开发者选项 → 打开 **USB 调试**（部分 ROM 还要开「USB 安装」/「仅充电时允许调试」）；
3. USB 连电脑，手机弹「允许 USB 调试吗」→ 勾「一律允许」→ 确定。
   来源：<https://developer.android.com/studio/run/device>（Run apps on a hardware device）

### 4.2 Windows 侧驱动

- 通用方案：装 Google USB Driver。它在 SDK 里的包名是 **`extras;google;usb_driver`**（我在官方 `addon2-3.xml` 索引里核实到该条目存在）：
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "extras;google;usb_driver"
```
装完后在「设备管理器」里对未识别设备 → 更新驱动 → 手动选择 `...\Sdk\extras\google\usb_driver`。
- 国产机（小米/OPPO/vivo/华为/荣耀）建议直接装厂商的 Windows 驱动/手机助手，Google 驱动对它们经常无效（这条是经验，不是官方文档）。

### 4.3 `adb devices` 排障顺序

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb kill-server
& $adb start-server
& $adb devices -l          # 期望看到 "<serial>  device"；unauthorized = 手机上没点允许
& $adb logcat              # 能出日志就说明链路通
```
判读：
| 现象 | 含义 / 处理 |
| --- | --- |
| 列表为空 | 驱动没装好 / 线材只充电 / 手机没开 USB 调试 |
| `unauthorized` | 手机上没授权；删 `%USERPROFILE%\.android\adbkey*` 后重插，或手机上「撤销 USB 调试授权」再连（本机已有 `adbkey`/`adbkey.pub`） |
| `offline` | 换线/换口，`adb kill-server` 重来 |
| `more than one device` | 加 `-s <serial>` 指定 |

### 4.4 部署与调试

```powershell
.\gradlew.bat :app:installDebug
& $adb shell am start -n com.nongyu.appplus/.MainActivity
& $adb logcat --pid=$((& $adb shell pidof -s com.nongyu.appplus).Trim())
```
断点同 §3.6 的 JDWP attach（也可 `am start -D` 让进程等调试器）。

### 4.5 IDEA 能不能直接部署到真机

能 —— **前提是 Android 插件可用**（Device Manager 里会列出已连接真机，Android App 运行配置的目标设备下拉里有它）。IDEA 官方教程在“Run the application”一节末尾明确指向 Android Studio 的“Run apps on a hardware device”文档，说明这套流程是共用的。
来源：<https://www.jetbrains.com/help/idea/2024.1/create-your-first-android-application.html>、<https://developer.android.com/studio/run/device>

---

## 5. 方案 C：不装设备，只靠 Preview / 截图测试

### 5.1 Compose `@Preview`（工程已具备条件，IDE 侧缺件）

| 项 | 结论 |
| --- | --- |
| 工程侧依赖 | ✅ 已就绪：`androidx-compose-ui-tooling-preview`（implementation）+ `androidx-compose-ui-tooling`（debugImplementation），见 `gradle/libs.versions.toml` 与 `app/build.gradle.kts` |
| 现有预览 | ✅ `app/src/main/java/com/nongyu/appplus/ui/NongyuApp.kt:59` 已有 `@Preview(showBackground = true)` |
| IDE 侧 | ❌ 本机**没装** Android 插件与 Android Design Tools；渲染器 layoutlib 随 Android 插件走 |
| IDEA 社区版/旗舰版差异 | **未验证**。插件是 Marketplace 通用包（`compatibleVersions` 里同时列 `IDEA` 与 `IDEA_COMMUNITY` 字段，说明官方按两种发行版分别标注），本机是 Ultimate（IU） |
| 渲染不出来的常见原因 | 未 sync 成功 / 没装 Design Tools / 插件版本与 AGP 不匹配 |

结论：**装好 §3.2 的两个插件、且 sync 成功之前，`@Preview` 不可用**；而 sync 成功又受制于 AGP 9.2.1（§2.4）。所以在本机当前状态，Preview 不是“零安装”方案。

### 5.2 Gradle Managed Devices（GMD）——AGP 9 里还在，仍在演进

- 官方文档仍然有效（`testOptions.managedDevices.localDevices`，要求 API ≥27）：
  <https://developer.android.com/studio/test/managed-devices>
- AGP 9.0 把旧的 `deviceProvider` / `testServer`（自定义测试设备）**移除**，官方指定替代就是 GMD —— 也就是说 GMD 是 AGP 9 的正式方向，不是弃用项。
  来源：<https://developer.android.com/build/releases/agp-9-0-0-release-notes>（“DEPRECATED API in the android block” 表中 `deviceProvider` 行）
- 仍带实验性开关的部分：`android.experimental.testOptions.managedDevices.customDevice`、`android.experimental.androidTest.numManagedDeviceShards`（分片）。ATD 镜像目前官方注明「ATDs currently support only API level 30」（同页）。
- **它不能给你“看界面”**：GMD 是给 instrumented test 用的无头设备编排，会自己下载镜像、跑测试、出报告，没有可交互的模拟器窗口。

### 5.3 Robolectric + Compose UI 测试能不能截图

- 单独 Robolectric **不能**截真实像素；要用 Robolectric Native Graphics（RNG）：Roborazzi README 明确要求 `Robolectric 4.10 alpha1+` 且测试类加 `@GraphicsMode(GraphicsMode.Mode.NATIVE)`。
  来源：<https://github.com/takahirom/roborazzi>（Try it out / Add Roborazzi）
- Robolectric 官方兼容表：**4.17 支持 API 23 → 37，构建于 Gradle 9.6.1 + AGP 9.2.1**；Android 16（API 36）运行测试要求 **Java 21**。
  来源：<https://robolectric.org/compatibility_table/>
  → 这条对本工程非常关键：**Robolectric 4.17 就是用 AGP 9.2.1 构建的**，版本对得上。
- 实际截图请配合 Roborazzi（见 §5.4）。

### 5.4 Paparazzi / Roborazzi（纯 JVM 出 PNG，无需设备）

| | Paparazzi | Roborazzi |
| --- | --- | --- |
| 最新版 | `2.0.0-alpha05.1`（2026-09-28） | `1.77.0`（2026-10-22） |
| 原理 | 直接跑 layoutlib，不 mock Android 框架 | 基于 Robolectric + RNG |
| AGP 支持 | CHANGELOG 明确：「[Gradle Plugin] Android Gradle Plugin **9.0.0**」（alpha05.1） | 仓库自身构建用 AGP 8.13.2；**AGP 9.2.1 未验证** |
| Gradle 支持 | alpha05.1 的 `reportType=native` 要求 **Gradle 9.4+**（正好覆盖 9.4.1） | 有 Gradle 9 相关已知问题：同一次 Gradle 调用里多任务抢同一输出目录会硬失败 `Cannot access input property 'roborazziImageInput'`（issue #830），解法是 `roborazzi { separateOutputDirs.set(true) }` |
| JDK | alpha04 起**要求 Java 21+** | 需 Java 21（Robolectric API 36） |
| Compose | LayoutLib v16.2.3；alpha05 自带 Compose 1.11.2 | 支持 `captureRoboImage { Composable }`、`onNode().captureRoboImage()` |
| 命令 | `./gradlew :sample:recordPaparazziDebug` / `verifyPaparazziDebug` | `./gradlew recordRoborazziDebug` / `verifyRoborazziDebug`，产物在 `build/outputs/roborazzi`，报告 `build/reports/roborazzi/index.html` |
| 对本工程的风险 | 工程用 Compose BOM 2025.12.00（实际解析到 Compose 1.10.0，见 `app/build/intermediates` 里 manifest-merger 报告的 `material3-android:1.4.0`、`material:1.10.0`）；Paparazzi 自带 1.11.2，**版本错位是否冲突未验证**；AGP 9.2.1（Paparazzi 只声明到 9.0.0）也**未验证** | AGP 9.2.1 + Gradle 9.4.1 组合**未验证**；Gradle 9 记得开 `separateOutputDirs` |

来源：
- Paparazzi README：<https://github.com/cashapp/paparazzi>
- Paparazzi CHANGELOG（AGP 9.0.0 / Gradle 9.4+ / Java 21+ / Compose 1.11.2）：<https://raw.githubusercontent.com/cashapp/paparazzi/master/CHANGELOG.md>
- Roborazzi README：<https://raw.githubusercontent.com/takahirom/roborazzi/main/README.md>
- Robolectric 兼容表：<https://robolectric.org/compatibility_table/>

**定位提醒**：这两个都是“跑测试出 PNG”，不是可交互界面；适合做 UI 回归/像素对比，不适合“点一下看看效果”。

### 5.5 直接调 Layoutlib

不可行/不建议：layoutlib 是 IDE/渲染器的内部实现，没有稳定公开 API；Paparazzi 就是它的事实封装。本条**未验证**（没有找到 Google 提供的 layoutlib 公共 API 文档）。

---

## 6. 决策建议

| 目标 | 推荐 | 理由 |
| --- | --- | --- |
| **装一次就长期用** | **Android Studio Quail 1（2026.1.1）+ 本机 SDK 补 cmdline-tools/emulator/镜像**，工程继续用 IDEA 2025.3.2 写代码 | AGP 9.2.1 的官方最低 IDE 就是 Quail 1；AS 自带 Device Manager / Logcat / Compose Preview / Layout Inspector，一次装完所有“只有 AS 有”的功能都在。IDEA 想追平要等 2026.3（插件 263.7307.200，基座 Quail 2）。 |
| **零安装（不装 AS、不装模拟器镜像）** | **Roborazzi 1.77.0 + Robolectric 4.17 截图测试**（先只加一个最小测试类） | 纯 JVM、出 PNG、可进 CI；Robolectric 4.17 本身就用 AGP 9.2.1 构建，版本最对得上。缺点：不是实时界面，AGP 9.2.1 + Gradle 9.4.1 组合需实测，且要开 `separateOutputDirs`。Paparazzi 作为备选（声明支持 AGP 9.0.0、要求 Gradle 9.4+ 与 Java 21+）。 |
| **最接近真机** | **真机 + `gradlew :app:installDebug` + `adb logcat --pid=`**（IDEA 里用 Gradle/Shell 配置驱动） | 真实 GPU/性能/传感器/输入法，零镜像下载；只多一个 USB 驱动（`extras;google;usb_driver`）和开发者选项。断点用 `am start -D` + `adb forward jdwp:<pid>` + IDEA Remote JVM Debug。 |

补充：**如果只是想先看见界面**，最快其实是 §3.3 里装 `default;x86_64`（805 MB）+ emulator（439 MB），比 `google_apis` 省 1 GB，本工程现在没有任何 Google Play 服务依赖。

---

## 7. 参考资料

**Google / Android**
1. AGP 9.2.0 release notes（Gradle 9.4.1、JDK 17、Build Tools 36.0.0、KGP 2.2.10、最高 API 37.0）— <https://developer.android.com/build/releases/agp-9-2-0-release-notes>
2. About AGP（AGP↔Gradle 最低版本表；Android Studio↔AGP 区间表；API level 最低工具版本表）— <https://developer.android.com/build/releases/about-agp>
3. AGP 9.0.1 release notes（内置 Kotlin；新 DSL 只保留公开接口；`deviceProvider`/`testServer` 移除指向 GMD）— <https://developer.android.com/build/releases/agp-9-0-0-release-notes>
4. sdkmanager（cmdline-tools 目录结构、`--licenses`、`--install`）— <https://developer.android.com/tools/sdkmanager>
5. avdmanager（`create avd -n -k [-c] [-f] [-p]`、`list device/avd`）— <https://developer.android.com/tools/avdmanager>
6. 启动命令行模拟器（`emulator -avd`、`-list-avds`、`-no-snapshot`、AVD 目录结构）— <https://developer.android.com/studio/run/emulator-commandline>
7. Create and manage virtual devices（系统镜像变体、AOSP 可 `adb root`、Play 镜像不可 root）— <https://developer.android.com/studio/run/managing-avds>
8. Gradle Managed Devices（`managedDevices.localDevices`、API≥27、ATD 仅 API 30、分片开关）— <https://developer.android.com/studio/test/managed-devices>
9. Configure your build（`local.properties` 之外的构建配置文件总览）— <https://developer.android.com/build>
10. Run apps on a hardware device — <https://developer.android.com/studio/run/device>
11. Debug your app（JDWP / attach 调试）— <https://developer.android.com/studio/debug>
12. SDK 仓库索引（体积与包名的一手来源）：
    - <https://dl.google.com/android/repository/repository2-3.xml>（cmdline-tools / emulator / platform-tools / platforms / build-tools）
    - <https://dl.google.com/android/repository/addon2-3.xml>（`extras;google;usb_driver`、`extras;google;Android_Emulator_Hypervisor_Driver`）
    - `sys-img` 五个索引见 §3.3

**JetBrains**
13. IntelliJ IDEA — Android（Android 支持不内置，需 Android + Android Design Tools 插件）— <https://www.jetbrains.com/help/idea/android.html>
14. IDEA 2024.1 教程：Create your first Android application（Project Structure→SDKs、Gradle JVM、Device Manager、运行配置）— <https://www.jetbrains.com/help/idea/2024.1/create-your-first-android-application.html>
15. IDEA — Gradle（Gradle JVM / Gradle 版本设置入口）— <https://www.jetbrains.com/help/idea/2024.3/gradle.html>
16. Android 插件页（22989）— <https://plugins.jetbrains.com/plugin/22989-android>
17. Android Design Tools 插件页（22990）— <https://plugins.jetbrains.com/plugin/22990-android-design-tools>
18. 插件版本与兼容 IDE 的一手数据 — <https://plugins.jetbrains.com/api/plugins/22989/updates>（`since`/`until`/`compatibleVersions`/`modules` 字段）
19. IDEA-385007 AGP 9 compatibility — <https://youtrack.jetbrains.com/issue/IDEA-385007>
20. IDEA-322286 允许 IDEA 使用未支持的 AGP — <https://youtrack.jetbrains.com/issue/IDEA-322286>
21. JetBrains 支持帖：IDEA 打不开新 AGP、`Unsupported method: AndroidArtifact.getPrivacySandboxSdkInfo()`、`gradle.ide.support.future.agp.versions` workaround — <https://intellij-support.jetbrains.com/hc/en-us/community/posts/34072502627218>
22. IntelliJ IDEA 2025.3 RC 公告（“Gradle 9” 支持）— <https://blog.jetbrains.com/idea/2025/11/intellij-idea-2025-3-release-candidate>

**Gradle / JDK**
23. Gradle Compatibility Matrix（运行 Gradle 需 Java 17–27；Java 25 自 9.1.0；Gradle 9.4.0 内置 Kotlin 2.3.0；测试过的 AGP 范围 9.0–9.5.0-alpha02）— <https://docs.gradle.org/current/userguide/compatibility.html>
24. `java.util.Properties#load` 转义规则 — <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html>

**截图测试**
25. Paparazzi README — <https://github.com/cashapp/paparazzi>
26. Paparazzi CHANGELOG — <https://raw.githubusercontent.com/cashapp/paparazzi/master/CHANGELOG.md>
27. Roborazzi README — <https://raw.githubusercontent.com/takahirom/roborazzi/main/README.md>
28. Robolectric Compatibility Table — <https://robolectric.org/compatibility_table/>
29. Compose UI previews（Kotlin 官方：IDE 内渲染，无需模拟器，KMP 需 Kotlin Multiplatform 插件）— <https://kotlinlang.org/docs/multiplatform/compose-previews.html>

---

## 8. 未验证清单（不要当成结论用）

1. **WMI 被沙箱拒绝**：CPU 型号、`HypervisorPresent` 标志、内存容量没取到（`Get-CimInstance` → `拒绝访问`）。只确认了 `PROCESSOR_ARCHITECTURE=AMD64`、24 逻辑核、Windows 10.0.26200、C 盘剩 98.6 GB、`hvservice`/`vmcompute` 在跑。
2. **AGP 9.2.1 在 IDEA 里 sync 到底会不会失败**：只从「插件基座 = AS Narwhal FD（AGP ≤8.12）」+ JetBrains 支持答复推断会失败，没有在真机 IDE 上按下 sync（会装插件、动工程配置，超出只做调研的范围）。
3. **`gradle.ide.support.future.agp.versions` 开关对本工程是否有效**：不确定；官方帖里提问者反馈无效。
4. **IDEA 2026.3（插件 263.7307.200）能否真的跑 AGP 9.2.1**：属于「插件基座 Quail 2 → AGP ≤9.3」的对齐推断，未实测。
5. **IDEA 里 Compose `@Preview` 的实际可用性**：两个插件都没装，未验证；社区版/旗舰版差异也未验证。
6. **IDEA 是否仍有 Logcat 面板 / Layout Inspector / APK Analyzer**：当前 IDEA Help 已不描述这些章节，只能确认「老版 IDEA 有 Logcat」，现代版本未验证。
7. **`avdmanager list device` 的真实 device id**（如 `pixel_7`）没验证，因为本机没有 avdmanager；文档里的示例 id 一律没写进命令。
8. **模拟器能否在本机顺利起来**（WHPX/AEHD 是否就绪、GPU 后端选哪个）：没装 emulator，未验证。
9. **`adb logcat --pid=` / `am start -D` / `adb forward ... jdwp:` 在本机实跑效果**：没有连接设备，命令按 adb/Android 文档写，未实跑。
10. **Paparazzi 2.0.0-alpha05.1 与 AGP 9.2.1 + Compose BOM 2025.12.00（解析到 Compose 1.10.0）是否兼容**：Paparazzi 只声明到 AGP 9.0.0，Compose 版本错位未验证。
11. **Roborazzi 1.77.0 与 AGP 9.2.1 + Gradle 9.4.1 的组合**：仓库自身构建用 AGP 8.13.2，未验证；Gradle 9 的 `separateOutputDirs` 是已知规避项。
12. **`extras;google;usb_driver` 对国产手机是否有效**：官方驱动只覆盖 Google/Pixel 类设备，其他厂商需自备驱动（经验判断，非文档结论）。
13. **本机 `JAVA_HOME=jdk-25` 下直接跑 `gradlew` 是否一切正常**：现有 `app-debug.apk` 生成于 2026-11-06 10:27，说明命令行构建是通的，但那次构建用的 `GRADLE_USER_HOME` 是临时目录 `%TEMP%\nongyu-gradle-home`（从 `app/build/outputs/logs/manifest-merger-debug-report.txt` 的缓存路径可见），与默认 `~/.gradle` 不同，未验证默认目录下的行为。
