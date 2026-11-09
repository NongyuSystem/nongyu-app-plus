# 「程序预渲染快启动」：冷启动到首帧/可交互的官方机制与度量（技术事实核查）

> 范围：只核查维护者诉求原话「探究程序预渲染快启动的方法，这时要研究性能的优化方案」所需的**一手技术事实**——从第一性原理出发，核查「缩短冷启动到首帧 / 到可交互时间」的官方定义、度量口径、可用机制、度量工具、优化清单，以及「皮肤资源动态下发」放进启动路径的影响。不含选型结论、不改代码。
> 版本基线（背景已确认，不重复验证）：AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00（≈ Compose 1.10 + Material3 1.4.0）/ compileSdk=36 / targetSdk=36 / minSdk=26 / JDK 21；本机仅装 android-36 SDK（无 android-37）；无 Android Studio，调试走命令行 adb + 蓝叠 Tiramisu64（API 33，`enable_vsync=0`、`max_fps=60`、鼠标输入，官方文档已明确它不能评帧率与手感）；工程骨架阶段，无网络层 / DI / 状态管理 / 路由。仓内 `docs/adr/0001`、`docs/research/bluestacks-adb-compose.md` 说明以上。
> 核查日期 2026-11-08。事实来源均为 primary source（developer.android.com / 官方 API 参考 / AndroidX 源码与 release notes / Google Maven 的 POM 与 AAR 内 `aar-metadata.properties`），链接见 §11。因 `developer.android.com` 主站在本会话取不到，正文经 `developer.android.google.cn` 镜像读取全文，链接仍给官方 .com 地址。
> 标注 **未验证** 的条目见 §10，均未在本轮直接实证。AAR 元数据按 `docs/research/compose-navigation-shell.md` 的方法逐个下载 AAR 读取（本轮实证，见 §3.4）。

---

## 1. 结论摘要

1. 官方把启动分**冷 / 温 / 热**三态，并规定「始终按冷启动假设去优化」。核心两个指标是 **TTID**（首帧，`Displayed` 日志）与 **TTFD**（可交互，`reportFullyDrawn()` / `FullyDrawnReporter` / Compose 的 `ReportDrawn*`）。[S1][S2]
2. 官方目标值：**冷启动 < 500ms、温启动 < 200ms、热启动 < 150ms**，并要求 P95/P99 接近中位数。[S3]
3. **「预渲染启动画面」在官方口径里不是 App 自己提前渲染一帧，而是系统 SplashScreen 窗口**：Android 12（API 31）起平台 SplashScreen 是一个 `Window`，在进程创建前就由系统画出 `windowBackground` / 图标，掩盖冷启动延迟；`androidx.core:core-splashscreen` 把该 API 向后兼容到 **API 23**。[S4][S5] `setKeepOnScreenCondition` 可让启动画面「保持」到少量本地数据就绪，但官方明确「只加载一小份本地数据」「条件每帧求值要快」「应用可提前跳过动画」——它不是让你在启动期做网络/磁盘 IO。[S4]
4. **Baseline Profiles（基准配置文件）是最接近「预渲染/预编译快启动」的官方机制**：把启动/关键路径的类与方法 AOT 预编译，官方原文「improve code execution speed by about **30%** from the first launch」「Many apps measure performance boosts of about **30%**」；AGP 8.2 起 R8 重写规则再「+~15% 性能」「Startup Profiles 让启动再 +~15%」。[S6] 生成靠 Macrobenchmark `BaselineProfileRule` / Baseline Profile Gradle 插件，安装靠 `androidx.profileinstaller`。[S6][S7]
5. **在本工程硬约束下，Baseline Profile 的「依赖与构建」是可用的**：官方最低推荐 AGP 8.0.0 / `benchmark-macro-junit4:1.5.0` / `profileinstaller:1.4.1`，AGP 9.2.1 全部满足；本轮实证这些 AAR 的 `minCompileSdk` 全部 ≤ 36（`core-splashscreen 1.2.0`=35、`profileinstaller 1.4.1`=34、`benchmark-macro-junit4/benchmark-macro 1.5.0`=34、`startup-runtime 1.2.0`=34、`activity-compose 1.11.0`=36）。**硬障碍在「生成」而非「依赖」**：Macrobenchmark 明确遇到模拟器会报错 / 不产生可代表真机的数字，而本机唯一设备蓝叠是模拟器类环境且 `vsync=0`；生成 Baseline Profile 还需 rooted 真机或 API 33+ 的 connected device + `com.android.test` 模块 + Gradle 插件改构建（本轮按纪律不改）。[S7][S8][S9][S24]
6. **Compose 确有「预组合」API，但它是低层布局原语，不是启动期预渲染开关**：`SubcomposeLayoutState.precompose(slotId, content)`（Added in 1.0.0）与 `createPausedPrecomposition(slotId, content)`（Added in 1.9.0），语义是「提前组合某个 slot，使下一次 measure pass 的 `subcompose(slotId)` 更快」。[S11] 官方对启动期 Compose 的建议反而是**削减首次组合成本**（`setContent` 的 initial composition、避免主线程重计算 / 复杂层级、条件组合延迟不可见 UI）。[S1][S12]
7. **度量分层**：`am start -S -W` + `Displayed` logcat（TTID）在任何 adb 设备（含蓝叠）都能取；Perfetto / 系统跟踪（Android 9+ 设备自带 System Tracing，Android 10+ 出 `.perfetto-trace`）可看 `bindApplication`/`activityStart`/`Choreographer#doFrame`/`Compose:recompose|layout|draw`；**Macrobenchmark 的启动数字必须回 USB 真机**（官方明确模拟器不具代表性且会报错）。蓝叠只能验「链路 / 代码逻辑 / 看得见的时序」，不能给手感或可外推的启动毫秒数（沿用仓内结论）。[S1][S3][S8][S13][S14]
8. **皮肤资源动态下发放进启动路径与官方建议直接冲突**：官方把「Blocking screen drawing on disk, or network I/O」列为 Heavy activity initialization 的典型问题，把「Any disk I/O, deserialization」列为 Heavy app initialization 的诊断项，解法是 lazy init / 移到别的线程 / 先显示占位数据后更新。[S2] 一手资料里**没有**「启动期下载并解析皮肤包」的官方支持；能追到的官方依据是「先渲染最小/占位 UI、后台再加载资源」与「`reportFullyDrawn` 应等异步数据就绪才算 TTFD」——即「先默认皮肤、后台换肤」在机制上对应 `ReportDrawnWhen`/`ReportDrawnAfter` + `FullyDrawnReporter`，但是否把换肤计入 TTFD 属工程取舍。[S2][S12][S15]

---

## 2. Q1 — 冷/温/热启动定义与度量口径

### 2.1 三态定义（官方原文要点）

- **冷启动 cold start**：「an app's starting from scratch」，系统进程创建 App 进程；发生在开机后首次启动或进程被系统杀死后。系统三步：① 加载并启动 App；② **立即显示一个空白的 starting window**；③ 创建 App 进程。随后 App 进程：① 创建 app 对象；② 启动主线程（UI thread）；③ 创建主 Activity；④ 初始化 UI；⑤ 把 UI 画到屏幕。「When the app process completes the first draw, the system process swaps out the displayed background window, replacing it with the main activity.」官方建议：「**always optimize based on an assumption of a cold start**」。[S1]
- **温启动 warm start**：冷启动操作的一个子集，开销大于热启动。两种典型：用户退出后重新打开（进程可能在，但 `onCreate` 要从零重建 Activity）；系统把 App 从内存逐出后重启（进程与 Activity 都要重建，但能从 saved instance state bundle 受益）。[S1]
- **热启动 hot start**：系统把 App 的 host Activity 带到前台；若 UI 仍驻留内存，可省去对象初始化 / UI 初始化 / 渲染。但 `onTrimMemory` 清理过的对象仍需重建。官方另在性能测量页给更细的 Systrace 口径：冷启动 = 无 saved state 全新建进程；温启动 = 复用进程重建 Activity 或带 saved state 重建进程；热启动 = 重启 Activity 且从 inflation 开始。[S1][S3]

### 2.2 TTID / TTFD 与 `Displayed`

- **TTID（time to initial display）**：显示第一帧的时间，含冷启动的进程初始化、冷/温启动的 Activity 创建、首帧绘制。官方：「**TTID is reported automatically for every app by the Android Framework**」。取法：logcat 中 `ActivityManager: Displayed com.android.myexample/.StartupTiming: +3s534ms`；该行可能带 `(total +1m22s643ms)`，第一个值只算首个被画的 Activity，`total` 从进程启动算起、仅在两者不同时出现。这条日志由 **system server**（不是 App 自己）打出，所以看 logcat 要关掉过滤器。[S1]
- **`Displayed` 的盲区（官方明确）**：「doesn't necessarily capture the amount of time until all resources are loaded」——不含首次组合未引用到的资源（异步加载的数据/图片）、不含对象初始化过程中创建的资源，因为它们「is an inline process and it doesn't block the app's initial display」。[S1]
- **TTFD（time to full display）**：App 变成 fully interactive / usable 的时间。取法：调用 `reportFullyDrawn()`；TTFD = 从系统收到 launch intent 到 `reportFullyDrawn()` 被调用的耗时。**不调用就没有 TTFD 值**。官方提醒别在系统测到 TTID 之前调 `reportFullyDrawn`；Compose-first 架构不要手动调 `activity.reportFullyDrawn`，改用 Compose 安全 API。[S1][S15]
- **提高 TTFD 准确度的官方机制**：`FullyDrawnReporter`（经 `ComponentActivity.fullyDrawnReporter()` 取）用 `addReporter()` / `removeReporter()` 记账，**所有 reporter 都 release 后才真正调 `reportFullyDrawn`**，从而把「后台任务填充 `LazyColumn`」这类延迟计入启动计时而不改变用户可见行为。Compose 提供 `ReportDrawn`（立即可交互）、`ReportDrawnWhen { predicate }`（如 `list.count > 0`）、`ReportDrawnAfter { suspend }`（挂起方法完成后）。`reportFullyDrawn()` 在 `Activity`（平台）与 `ComponentActivity`（backport，修了 API 19 崩溃并全 API 加 tracing）都可用；平台侧有 `ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN` 时间戳常量与 `ProfilingTrigger.TRIGGER_TYPE_APP_FULLY_DRAWN`（冷启动 reportFullyDrawn 后抓一次系统跟踪快照）。[S1][S15][S16]
- **目标值**：冷 < 500ms / 温 < 200ms / 热 < 150ms，且 P95、P99 要贴近中位数（IPC 与启动关键路径上不必要的 IO 会带来锁竞争与不一致）。[S3]

### 2.3 为什么「首帧」是系统给的，不是 App 提前渲染的

冷启动一开始系统就画了 **blank starting window**；App 进程完成首帧绘制后，系统进程才把 starting window 换成主 Activity。即「用户最早看到的画面」由系统窗口提供（Android 12+ 即 SplashScreen Window，见 §3）。[S1][S4] 这正是「预渲染」在 Android 启动语境里最可能被指的东西——但官方叫它 splash screen / starting window，不叫「App 预渲染」。

---

## 3. Q2 — SplashScreen：平台 API、向后兼容、能否「预渲染」、保持与退出

### 3.1 平台 API 与版本门槛

- 「**Starting in Android 12**, apps launch with a system splash screen … The splash screen is a `Window` and therefore covers an `Activity`.」平台类 `android.window.SplashScreen`：**Added in API level 31**（Android 12），其 `setOnExitAnimationListener` / `setSplashScreenTheme` 同为 31。[S4][S5]
- 触发时机：进程未运行（冷启动）或 Activity 未创建（温启动）时，系统按主题与动画显示 splash screen；**热启动永不显示**。「When the app is ready, the splash screen is dismissed and the app displays.」[S4]
- 组成（XML 主题属性）：app 图标（vector drawable，可静态/动画，动画建议 ≤ 1000ms）、图标背景、window background（单一不透明色）；尺寸沿用 adaptive icon 规范（品牌图 200×80dp、带底图标 240×240dp 内切 160dp 圆等）。[S4]

### 3.2 `androidx.core:core-splashscreen` 向后兼容范围

- 官方「Get started」原文：「The core `SplashScreen` library **brings the Android 12 splash screen to all devices from API 23**」，声明 `implementation("androidx.core:core-splashscreen:1.2.0")`。[S4]
- Core release notes（1.0.0-alpha01）原文：「core-splashscreen provides backward compatibility for the new Splash Screen APIs. This first alpha version contains **all the new APIs backported down to API 23**, with the exception of the icon background.」[S17]
- 兼容库 `androidx.core.splashscreen.SplashScreen`「wraps the platform `SplashScreen` API and works with any `ComponentActivity`, including activities that set their UI with Compose using `setContent`」。[S4] 用法：主题继承 `Theme.SplashScreen`、设 `postSplashScreenTheme`、manifest 应用该主题，**在 `super.onCreate()` 之前**调 `installSplashScreen()`。[S4]

### 3.3 「能否预渲染启动画面」的官方口径

- 关键事实：splash screen 是**系统的 Window**，在 App 进程创建/绘制之前就由系统按主题画出，作用是掩盖冷启动延迟；官方称「User research shows that perceived startup time is less when viewing an animation」。它**不是** App 用代码提前渲染并缓存一张位图。[S4]
- 保持时长：`setKeepOnScreenCondition { !viewModel.isReady.value }` 让 splash screen 停到条件满足；官方限定原文：「If you need to load **a small amount of data, such as loading in-app settings from a local disk asynchronously**」「**The condition is evaluated before each frame is drawn, so keep it fast to avoid blocking the UI**」。[S4]
- 退出动画：`SplashScreenViewProvider` 可自定义 transform/opacity/color 并手动 remove；但「When running the icon animation, app launch gives you the option to **skip the sequence in cases where the app is ready earlier**」「The splash screen must only be dismissed with `onResume()` when the app is stable from a visual standpoint, so no additional spinners are needed」。`windowSplashScreenAnimationDuration` **不影响 splash screen 实际显示时长**，只是给退出动画取用（`iconAnimationDurationMillis`）。[S4]
- 迁移硬要求：官方「Starting with Android 12, **migrating to the SplashScreen API is required**」；Android 11（API 30）及以下用 `windowDisablePreview` 或专用 SplashActivity 的旧做法会额外加时。[S2]
- 对「预渲染」的判定：官方**没有**「把启动画面预渲染成纹理/截图以加速」的 API 或文档；能做的是「系统 starting window + 主题化 splash screen + 动画掩盖感知时长」。故「预渲染启动画面」若指「系统级提前出画面」，答案是 SplashScreen；若指「App 自己预先渲染」，**未见官方 API**（检索范围见 §10）。

### 3.4 minSdk=26 下的可用版本与 AAR 元数据（本轮实证）

Google Maven `maven-metadata.xml`：现稳定版 `core-splashscreen` = **1.2.0**（`lastUpdated 20251105`）。[S18] 逐个下载 AAR 读 `META-INF/com/android/build/gradle/aar-metadata.properties`：

| AAR | minCompileSdk | minAndroidGradlePluginVersion | 备注 |
| --- | --- | --- | --- |
| `core-splashscreen:1.2.0` | **35** | 8.6.0 | ≤ 本工程 compileSdk 36；AGP 9.2.1 满足 |
| `core-splashscreen:1.0.1` | 31 | 1.0.0 | 旧稳定线，门槛更低 |

- minSdk 维度：兼容库 backport 到 **API 23**，本工程 minSdk=26 > 23，**可用**。平台 API 31 仅在 API 31+ 生效，26–30 由兼容库提供一致外观。[S4][S17]
- `activity-compose:1.11.0`（本工程已用，见 `gradle/libs.versions.toml`）的 AAR 元数据 **minCompileSdk=36 / minAGP=8.9.1** —— 这从侧面确认 compileSdk=36 是本工程当前的天花板，任何要求 37 的 artifact 都会被 AGP 的 AAR 元数据校验拒绝（与 `compose-navigation-shell.md` 的 37 阻断同理）。[S19]

---

## 4. Q3 — Baseline Profiles：原理、生成、工具链、收益量级、在本工程可用性

### 4.1 原理（AOT 预编译热点代码）

- 官方原文：「Baseline Profiles improve code execution speed by **about 30% from the first launch** by avoiding interpretation and just-in-time (JIT) compilation steps for included code paths.」「By shipping a Baseline Profile … Android Runtime (ART) can optimize specified code paths through **Ahead-of-Time (AOT) compilation** … This Profile Guided Optimization (PGO) lets apps optimize startup, reduce interaction jank … from the first launch.」[S6]
- 不用的代价：「all app code is either JIT-compiled in memory after being interpreted, or written to an `odex` file in the background when the device is idle … users have a suboptimal experience from the first time they run it until new code paths are optimized.」[S6]
- **Startup Profiles**：Baseline Profile 的子集，**编译期**用来优化 DEX 布局（把启动要用的类/方法塞进主 `classes.dex`、把非启动代码移出），减少启动期 major page faults；与「运行时 AOT」不同。[S6][S20]
- **分 Android 版本的编译行为（官方表，逐字要点）**：[S6]
  - Android 5–6（API 21–23）：Full AOT，安装期全量优化 → 安装等待久、RAM/磁盘占用大、从磁盘加载代码时间长，**可能反增冷启动**。
  - Android 7–8.1（API 24–27）：Partial AOT（Baseline Profile）。**`androidx.profileinstaller` 在 App 模块声明该依赖时，于首次运行安装 Baseline Profile**；ART 还会在使用中添加 profile 规则、设备空闲时编译。
  - Android 9（API 28）+：Partial AOT（Baseline + Cloud Profile）。Play 在安装期用 Baseline Profile 优化 APK，并把 ART profile 聚合为 Cloud Profiles 给其他用户。
  - 官方同时注明：非 Google Play 分发渠道可能安装期不支持 Baseline Profile，用户要等后台 `bg-dexopt`（多半隔夜）才受益；`ProfileInstaller` 在 API 29+ 提供把打包在 `assets` 的 profile 转码后写入 `cur` 目录、由 `bg-dexopt` 拾取的机制。[S6][S21]
- 对 minSdk=26 的意义：API 26 (Android 8.0) 落在「7–8.1」档 → **需要 `androidx.profileinstaller` 才能在首次运行装 profile**；本工程若引入 Baseline Profile，profileinstaller 不是可选项。

### 4.2 生成方式与工具链

- 官方《Create Baseline Profiles》：用 `com.android.test` 模块 + `androidx.baselineprofile` Gradle 插件 + Macrobenchmark `BaselineProfileRule.collect()` 定义 CUJ；跑 `:app:generateBaselineProfile` / `:app:generateVariantBaselineProfile`，产物拷到 `app/src/<variant>/generated/baselineProfiles/baseline-prof.txt`。AGP 8.2+ 可用 Android Studio 的 Baseline Profile Generator 模块模板。[S7]
- 生成 vs 发布的构建差异（官方强调）：**生成 profile 的变体必须 `isMinifyEnabled = false`（不混淆/不 R8 优化）**，否则规则对不上方法签名；**release 必须 `isMinifyEnabled = true`**，R8 会把未混淆的规则重写成匹配混淆后代码的规则。Startup Profile / DEX 布局优化还要求 release 开启 R8 全部优化。Baseline Profile Gradle 插件自动处理这套配置。[S6]
- 生成设备要求（官方）：「Run the generator as an **instrumented test on a rooted physical device, emulator, or Gradle Managed Device**. If you use a Gradle Managed Device, set `aosp` as the `systemImageSource`, because you need root access」「When using connected devices, they **must be rooted or API 33 and higher**」；并「After you generate the Baseline Profile, **benchmark it using a physical device** to measure the speed improvements.」[S7][S9]
- **最低推荐稳定版本（官方原文）**：AGP `com.android.tools.build:8.0.0`、`androidx.benchmark:benchmark-macro-junit4:1.5.0`、`androidx.profileinstaller:profileinstaller:1.4.1`（AGP 9.1 起支持库模块任意命名的多 profile 文件；8.2 起 R8 重写 + Startup Profiles）。[S6]
- **确切 artifact 坐标（Google Maven 实证）**：
  - `androidx.benchmark:benchmark-macro-junit4` — 现稳定 **1.5.0**（`1.6.0-alpha01` 为 latest）。[S18]
  - `androidx.benchmark:benchmark-macro` — 1.5.0（junit4 POM 依赖）。
  - `androidx.benchmark:benchmark-gradle-plugin` — 1.5.0（Baseline Profile / Macrobenchmark Gradle 插件；`androidx.baselineprofile/baselineprofile-gradle-plugin` 路径 404）。[S18][S19]
  - `androidx.profileinstaller:profileinstaller` — 现稳定 **1.4.1**（`lastUpdated 20241002`）；1.4.0 起「supports capturing/installing profiles on API 35+」。[S18][S22]
  - `androidx.startup:startup-runtime` — 现稳定 **1.2.0**（见 §5）。[S18]
- ProfileInstaller 1.4.1 的 release note 还写明：Macrobenchmark 需要 target app 含 **ProfilerInstaller ≥ 1.3**，`<profileable>` 由模块向导自动加到 manifest。[S8][S22]

### 4.3 收益量级（引用官方原文，不换算）

| 量级 | 官方原文出处 |
| --- | --- |
| ~30% 首次启动代码执行速度 | 「Baseline Profiles improve code execution speed by about 30% from the first launch」；「Many apps measure performance boosts of about 30% after optimizing」[S6] |
| ~30% 方法覆盖 / ~15% 性能 | AGP 8.2 表：「Increases Baseline Profile coverage of methods by ~30% and increases app performance by ~15%」[S6] |
| 启动再 ~15% | AGP 8.2 表：「[Startup Profiles] … Increases startup performance by an additional ~15%, or significantly more for large apps」[S6] |
| 官方博客案例（旁证，非规范数字） | 「Tinder cuts app cold starts by 47% with new R8 Configuration Analyzer」「Grab … improves startup time by 35% with R8 and Profile guided …」[S23] |
| DEX 布局优化定性 | 「改进启动期间所用代码的位置，缩短启动时间，从而减少应用启动期间发生的主要页面故障的数量」[S20] |

### 4.4 在 AGP 9.2.1 + BOM 2025.12.00 + 仅 android-36 下是否可用

- **依赖 / 构建层：可用（本轮实证）**。逐个下载 AAR 读 `aar-metadata.properties`：[S19]

| AAR | minCompileSdk | minAGP | 与 compileSdk=36 关系 |
| --- | --- | --- | --- |
| `core-splashscreen:1.2.0` | 35 | 8.6.0 | ✅ |
| `profileinstaller:1.4.1` | 34 | 1.0.0 | ✅ |
| `profileinstaller:1.3.1` | 33 | 1.0.0 | ✅ |
| `benchmark-macro-junit4:1.5.0` | 34 | 8.1.1 | ✅ |
| `benchmark-macro:1.5.0` | 34 | 8.1.1 | ✅ |
| `startup-runtime:1.2.0` | 34 | 1.0.0 | ✅ |
| `activity-compose:1.11.0`（工程现有） | 36 | 8.9.1 | ✅（=36 边界） |

  全部 ≤ 36，AGP 9.2.1 ≥ 各 minAGP。**没有任何一个目标 artifact 要求 compileSdk 37**；与 `compose-navigation-shell.md` 里 navigation-compose 2.10.2 要 37 是两回事。所需最低 AGP 8.0.0 也被 9.2.1 覆盖。[S6][S19]
- **「生成 profile」层：本机不成立或受限**。① Macrobenchmark「**returns errors when attempting to measure an emulator**」「We discourage running the benchmarks on an emulator, as they don't produce performance numbers representative of the end-user experience」；本机唯一设备蓝叠是模拟器类环境（且 `enable_vsync=0`、60fps 上限、鼠标输入，按 `docs/adr/0001`、`bluestacks-adb-compose.md` 不能定手感/帧率）。[S8][S9][S24] ② 生成需 `com.android.test` 模块、应用 `androidx.baselineprofile` 插件、新增 `baselineProfile(project(...))` 依赖与 `benchmark` buildType（`isMinifyEnabled=false`）——这属于**改构建文件**，本轮按纪律不改、不在本工程实跑 `assembleDebug`/`generateBaselineProfile`。③ 要用 connected device 则须 rooted 或 API 33+，蓝叠 Tiramisu64 标称 API 33 但未回读 `getprop` 实证（仓内已列为未验证），且是模拟器。
- **结论（事实约束，非建议）**：Baseline Profile 的「消费端」（release 打包 `baseline-prof.txt` + profileinstaller 在 API 26/28+ 装 profile）与本工程硬约束不冲突；「生产端」（用 Macrobenchmark 在设备上采集规则）在当前无 USB 真机、仅蓝叠、无 Android Studio、骨架期无真实 CUJ 的条件下缺乏官方认可的测量设备。官方允许在无 Macrobenchmark 时「Manually create and measure Baseline Profiles」（手写 HRF 规则 + `cmd package compile -m speed-profile`），但那是另一条路径且本轮未验证。[S21]

---

## 5. Q4 — 「程序预渲染」还可能对应的官方机制

### 5.1 androidx.startup App Startup（ContentProvider 合并 / 懒初始化）

- 官方原文：「Instead of defining **separate content providers** for each component … App Startup allows you to define component initializers that **share a single content provider**. **This can significantly improve app startup time.」」「content providers are **expensive to instantiate** and can slow down the startup sequence unnecessarily. Additionally, Android initializes content providers in an **undetermined order**.」[S25]
- 机制：`Initializer<T>` 的 `create()` / `dependencies()`；`InitializationProvider`（manifest 中 `androidx.startup.InitializationProvider`）通过 `<meta-data>` 发现 initializer 并按依赖排序调用；`tools:node="merge"` 供 manifest merger 合并。[S25]
- **懒初始化（官方定义）**：「you can also use `AppInitializer` directly in order to manually initialize components that your app doesn't need at startup. **This is called lazy initialization, and it can help minimize startup costs.**」做法：用 `tools:node="remove"` 去掉单个 `<meta-data>`，或去掉整个 `InitializationProvider`，再 `AppInitializer.getInstance(context).initializeComponent(X::class.java)`。[S25]
- 启动优化页也把「Use the Jetpack App Startup library to streamline the initialization of components during app startup」列为改进手段；launch-time 页在 Heavy app initialization 的解决方案里写「If your app uses content providers to initialize app components at startup, consider using the App Startup library instead」。[S2][S12]
- 版本：`androidx.startup:startup-runtime` 现稳定 **1.2.0**；AAR minCompileSdk=34 / minAGP=1.0.0（本轮实证），本工程可用。[S18][S19]
- 注意：App Startup 是**合并/排序/延迟**初始化，不是「预渲染」；它减少的是 ContentProvider 实例化开销。

### 5.2 类 / 资源预加载

- 官方层能追到的「预加载」类机制有两类，都**不是 App 主动 API**：① **ART 的 AOT / speed-profile 编译**（`adb shell cmd package compile -m speed -f <pkg>` 全量、`-m speed-profile` 按 profile；官方说 `speed` 与 `speed-profile` 都减少解释执行与后台 JIT 干扰，只有 `speed-profile` 还减少运行时从 dex 类加载的影响，profile 在 `/data/misc/profiles/ref/<pkg>/primary.prof`）；② **Baseline / Cloud Profile**（§4.1）。[S3][S6]
- 「App 在 `Application.onCreate` 里预先 `Class.forName` / 预读资源以加速」这条**官方未作为推荐机制出现**；相反，官方把启动期 disk I/O、反序列化、bitmap 解码/VectorDrawable 栅格化列为要 lazy 化、移到别的线程、或先占位后更新的对象。[S2] 故「类/资源预加载」在本轮检索范围内**未见官方正面 API/建议**，按「未验证/未见」处理（§10）。

### 5.3 Compose 首次组合 / 首次布局测量成本（官方口径）

- launch-time 页（Activity creation 段）原文：「In a Jetpack Compose app, the `onCreate` method's overhead often comes from the **initial composition**. This happens when your app calls `setContent` and invokes your top-level composables. **A deep or complex UI hierarchy, or composables that perform heavy calculations on the main thread, can increase this composition time.**」[S1]
- 《App startup analysis and optimization》的改进项：「**Make your initial composition as lightweight as possible**」「Minimize the number and size of bitmap resources loaded during startup」「Identify **unnecessary UI elements that are included in the initial composition**」，并给出「conditional composition」把首屏不可见的 UI（错误屏、可选详情、次级 tab）用 `if (shouldLoad) { … }` + `LaunchedEffect { shouldLoad = true }` 推迟到首帧之后。[S12]
- Perfetto 里看 `Choreographer#doFrame` 下的 `Compose:recompose` / `Compose:layout` / `Compose:draw` 切片（配合 composition tracing / `runtime-tracing`）。[S1][S13]
- 《Jetpack Compose phases》被官方在冷启动页引用为布局阶段背景（composition → layout/measure → draw）。[S1]

### 5.4 「Compose 是否有官方预渲染 / Precomposition API」

**有，但语义与「启动期预渲染」不同**：[S11]

| API | 加入版本 | 官方语义（逐字） |
| --- | --- | --- |
| `SubcomposeLayoutState.precompose(slotId, content): PrecomposedSlotHandle` | 1.0.0 | 「Composes the content for the given `slotId`. **This makes the next `scope.subcompose(slotId)` call during the measure pass faster as the content is already composed.**」可用 `PrecomposedSlotHandle.dispose()` 释放 |
| `SubcomposeLayoutState.createPausedPrecomposition(slotId, content): PausedPrecomposition` | 1.9.0 | 「allows to perform the composition in an incremental manner … Performing full or partial precomposition makes the next `scope.subcompose(slotId)` call during the measure pass faster」 |

- 这是 `SubcomposeLayout`（`androidx.compose.ui:ui`）的低层布局状态 API，服务于「测量阶段才需要子组合」的场景（如 `LazyColumn` / `BoxWithConstraints` 类机制），**不是**「Activity 启动前把整棵 UI 预渲染好」的开关；官方启动文档给的方向反而是削减首次组合（§5.3）。[S11][S12]
- release notes 佐证演进：Compose UI 1.0.0-beta07「Introduces ability to hoist the SubcomposeLayout state which allows you to **precompose** the content into a … slotId which would make the next measure pass faster」；1.2.0-alpha03「Added a new api which allows to **premeasure** children of `SubcomposeLayout` you precomposed」。[S26]
- **检索范围与判定**：本轮在 developer.android.com（API 参考、`SubcomposeLayoutState` 页、Compose UI release notes、启动/性能指南）检索 `precompose` / `precomposition` / `premeasure` / `Precomposition`；命中上述低层 API，**未命中**任何名为「App 启动预渲染 / 首帧预渲染 / PreRender / prewarm composition」的官方高层 API。故「高层启动预渲染 API」按「未见官方 API」处理（§10）。

---

## 6. Q5 — 度量工具与「哪些能在蓝叠测、哪些必须回 USB 真机」

| 工具 | 官方口径 | API/环境要求 | 在本机（蓝叠 Tiramisu64 / API 33 / vsync=0） |
| --- | --- | --- | --- |
| `adb shell am start -S -W` | launch-time 页给的 TTID 测法；终端出 `ThisTime / TotalTime / WaitTime`；`Displayed` 仍看 logcat。[S1] | 任意 adb 设备 | ✅ **能取**（仓内 `bluestacks-adb-compose.md` 的 adb 链路已覆盖）；只代表该虚拟环境的毫秒数，不可外推真机 |
| logcat `Displayed` | TTID 自动上报；由 system server 打出，需关过滤器。[S1] | 任意 | ✅ 能取 |
| `reportFullyDrawn` / `FullyDrawnReporter` / `ReportDrawn*` | TTFD 口径；Compose-first 用 Compose 安全 API。[S1][S15] | `ComponentActivity` backport 全 API | ✅ 代码里能埋、能在蓝叠验证「逻辑是否触发」 |
| Perfetto / 系统跟踪 | 看 `Android App Startups` 派生指标切片、`bindApplication` / `activityStart`、主线程 `Choreographer#doFrame`、`Compose:recompose/layout/draw`。[S1][S13] | 设备自带 System Tracing app 需 **Android 9 (API 28)+**；**Android 10 (API 29)+** 出 `.perfetto-trace`，更早出 `.ctrace`；`adb pull /data/local/traces/`。[S14] | ⚠️ 蓝叠标称 API 33（未回读实证）——理论上能录 Perfetto 格式；但 `vsync=0` 使帧时序不可信，仅适合「看代码路径/切片是否存在」 |
| Macrobenchmark `StartupTimingMetric` | `timeToInitialDisplayMs` / `timeToFullDisplayMs`（停在 fully drawn 信号后首帧渲染完成）；输出 min/median/max，**评估看 median**；target 必须 `profileable`、含 ProfilerInstaller ≥1.3、release-like（non-debuggable + minify）。[S8][S15] | `com.android.test` 模块；官方明确「**returns errors when attempting to measure an emulator**」「discourage … on an emulator, as they don't produce performance numbers representative of the end-user experience」；低电量也报错。[S8][S9] | ❌ **不能用于定稿/给数字**：本机唯一设备是模拟器类环境；且官方要「benchmark it using a **physical device**」[S7] |
| `dumpsys gfxinfo` / JankStats / FrameTimeline | 帧数据五种采集法；FrameTimeline 需 **Android 12 (API 31)+**；gfxinfo 看跨版本趋势、JankStats 看字段数据。[S12][S13] | 见左 | ⚠️ 能跑但帧节奏不可信（`enable_vsync=0`、60 上限），沿用仓内「不能评帧率与手感」结论 |
| Benchmark 环境校准 | 「Run A/B comparisons across the **same device and same OS version**」「Test on a system that is as close to production as possible, while suppressing sources of noise」；`lockClocks` 可用于 Microbenchmark 降噪，但**不建议**用于 app launch / jank 这类用户体验测试。[S3] | — | 蓝叠不满足「接近生产」与固定时钟口径 |

- **交叉结论**：能在蓝叠做的 = 埋点/链路/日志/切片存在性验证（`am start -W`、`Displayed`、`reportFullyDrawn` 是否触发、Perfetto 里有没有 `Compose:recompose` 段）；**必须回 USB 真机** = 任何要写进文档/决策的启动毫秒数、Macrobenchmark 数字、帧率/手感、Baseline Profile 收益量级。依据：官方 Macrobenchmark 对模拟器的明确排斥 + 仓内 `docs/adr/0001`「长按手感/帧率的定稿必须回 USB 真机」与 `bluestacks-adb-compose.md`「`enable_vsync=0` + 60 上限 + 鼠标输入，帧节奏不对齐真机」。[S8][S9][S24]

---

## 7. Q6 — 性能优化方案清单（每条一手依据 + 本工程硬约束下的可行性）

| # | 方案 | 一手依据（官方口径） | 本工程可行性 / 约束 |
| --- | --- | --- | --- |
| P1 | 启动路径裁剪：lazy initialization，只初始化立即需要的对象；全局静态对象改惰性单例；DI（Hilt）首次注入才建对象 | 「the solution is **lazy initialization** … only initialize objects that are immediately needed」[S2]；《analysis-optimization》「Non-essential resources can wait to load until after startup completes」「Load content lazily and asynchronously to speed up TTID」[S12] | ✅ 纯代码层，不改构建，骨架期最易落地；本工程无 DI/网络层，暂无可裁剪对象，属「先记原则、有真实初始化再做」 |
| P2 | 避免启动期磁盘/网络 IO、反序列化、bitmap 解码/VectorDrawable 栅格化；移到别的线程；先占位后更新 | 「**Blocking screen drawing on disk, or network I/O**」列为 Heavy activity 问题；「Any disk I/O, deserialization, or tight loops」为诊断项；「Load and display your UI with **placeholder data first**, and then later update」[S2]；「keep expensive operations such as **file I/O and network access off the main thread**」「StrictMode.ThreadPolicy … debug builds」[S12] | ✅ 与 §8 皮肤下发直接相关；本工程当前无网络层，风险在于**将来**别把皮肤下载塞进启动路径 |
| P3 | 减少 `Application` / `ContentProvider` 初始化；用 App Startup 合并 + 懒初始化 | 「content providers are expensive to instantiate」「share a single content provider … significantly improve app startup time」「lazy initialization … minimize startup costs」[S25]；「consider using the App Startup library instead」[S2] | ✅ `startup-runtime:1.2.0` AAR minCompileSdk=34 可用[§4.4][S19]；但会新增依赖，且骨架期无 ContentProvider，收益此刻为 0 |
| P4 | SplashScreen 保持 / 系统 starting window 掩盖延迟 | 系统 Window 在首帧前画出；`setKeepOnScreenCondition` 等少量本地数据；动画 ≤1000ms、提前可跳过[§3.3][S4]；Android 12 起**必须迁移**到 SplashScreen API[S2] | ✅ `core-splashscreen:1.2.0` backport 到 API 23、AAR minCompileSdk=35，minSdk 26 可用[§3.4]；注意它**不授权**启动期网络 IO |
| P5 | Baseline Profile + Startup Profile（DEX 布局优化） | ~30% 首次启动、AGP 8.2 R8 重写 ~15%、Startup Profile 再 ~15%[S6]；DEX 把启动代码塞进主 `classes.dex` 减少 page fault[S20] | ⚠️ 消费端可用（§4.4 全部 AAR ≤36）；**生成端**受「必须物理设备/非模拟器」+「需改构建、加 com.android.test 模块」限制，本机不成立[§4.4][S7][S8] |
| P6 | R8 / 资源收缩（release 开启 `isMinifyEnabled`/`isShrinkResources`） | R8「**Faster startup time** / Reduced memory / Improved rendering」「Class merging … improving startup speed」；AGP 9.0+ 开 `isShrinkResources` 自动启用 optimized resource shrinking；「If R8 is enabled, you should also create Startup Profiles」[S27] | ✅ AGP 9.2.1 用 legacy DSL（`isMinifyEnabled`/`isShrinkResources`，9.3 才有 `optimization {}` 新 DSL）[S27]；骨架期无 keep rules 负担；但官方建议只在 release 开（debug 变体对性能有严重影响、堆栈难读）[S3][S27] |
| P7 | 削减 Compose 首次组合：扁平层级、避免启动期重组/重计算、条件组合延迟不可见 UI、减少启动期 bitmap | 「initial composition」是 `onCreate` 主要开销；「Make your initial composition as lightweight as possible」「conditional composition」「Minimize the number and size of bitmap resources」[S1][S12] | ✅ 纯代码层；骨架四空页首次组合极轻，主要是「别把皮肤包解析/大图塞进 setContent」 |
| P8 | 用 `FullyDrawnReporter` / `ReportDrawnWhen` 正确度量 TTFD（把异步数据计入） | 「delay calling `reportFullyDrawn` until after your app becomes usable」「FullyDrawnReporter doesn't call `reportFullyDrawn` until all added reporters are released」[S1][S15] | ✅ 度量手段，非收益手段；决定「换肤是否计入 TTFD」的关键开关（§8） |
| P9 | 避免 trampoline Activity | 「A trampoline activity can extend app startup time unnecessarily」；`activityStart` 紧跟 `activityStart` 却无帧[S3] | ✅ 骨架期单 Activity，天然不触发；记入约束即可 |
| P10 | `adb shell cmd package compile -m speed` / `speed-profile` 固定编译态做 A/B | 「Compile your app on-device to a known state … generally `speed` … or `speed-profile`」；否则 JIT 干扰测量[S3] | ⚠️ 蓝叠可跑命令，但数字不可外推；真机做 A/B 才有意义 |

> 官方《Enable app optimization with R8》还给出一条边界：「enable app optimization **only in the final version** … optimizations increase build time and can make debugging harder」——本工程无 Android Studio、靠命令行看界面，debug 变体保持不混淆是合理的。[S27]

---

## 8. Q7（交叉）— 皮肤资源动态下发放进启动路径的影响

### 8.1 与官方启动建议的冲突（事实，不是推断）

- 「下载 / 解析 / 校验皮肤包」= 网络 IO + 磁盘 IO + 反序列化/解析。官方把这三类并列为启动期要避免的东西：
  - Heavy activity initialization：「**Blocking screen drawing on disk, or network I/O**」[S2]
  - Heavy app initialization 诊断项：「Any **disk I/O, deserialization**, or tight loops」[S2]
  - 《analysis-optimization》：「keep expensive operations such as **file I/O and network access off the main thread**」「Move long-running operations to the background」「**Non-essential resources can wait to load until after startup completes**」「I/O-related interruptible sleep can be a really good opportunity for improvement. **Other processes doing I/O, even if they're unrelated apps, can contend with the I/O**」[S12]
- 因此「把皮肤下载/解析/校验同步放进冷启动关键路径」与上述官方建议**直接冲突**：它把可等待的非必要资源放进 TTID 之前，并把 IO 竞争带进启动窗口。官方给的替代口径是「**Load and display your UI with placeholder data first, and then later update visual properties**」[S2] 与「display something that requires minimal rendering to the user as quickly as possible until the rest of the screen is finished loading」[S12]。

### 8.2 「离线首屏兜底 / 先默认皮肤、后台换肤」的一手依据

- 一手资料里**没有**「动态皮肤包」这个具体功能的规范（皮肤下发是本工程业务概念，非 Android API）。能追到的官方机制依据有三条：
  1. **占位/延迟加载**：「placeholder data first … later update」「conditional composition」把非首屏可见内容推迟到首帧之后[§7 P2/P7][S2][S12]。→ 对应「先渲染内置默认皮肤首屏」。
  2. **异步记账**：`FullyDrawnReporter` / `ReportDrawnWhen { predicate }` / `ReportDrawnAfter { suspend }` 让「后台任务完成、列表/资源就绪」成为 TTFD 的终点，且「without changing the app's behavior for the user」[S1][S15]。→ 对应「后台换肤是否算启动完成」是可配置的度量边界。
  3. **SplashScreen 保持的边界**：`setKeepOnScreenCondition` 官方只授权「a **small amount of data** … from a **local disk** asynchronously」「condition 每帧求值要快」「可提前跳过」[S4]。→ 不能用「hold splash 等网络皮肤包」来掩盖下载。
- **推断（待维护者拍板，不作为事实）**：把「默认皮肤」做成 APK 内置资源/主题（离线可得、零 IO 阻塞），启动期 `setContent` 直接渲染它；皮肤包的下载/校验/解析放后台，完成后通过状态切换换肤，并用 `ReportDrawnWhen/ReportDrawnAfter` 决定换肤是否计入 TTFD。这条与官方「占位先显示 + 后台更新 + 启动期避免网络/磁盘 IO」方向一致，但**「换肤重组是否触发全树重组、默认皮肤落点」属另两张票**（`docs/research/compose-design-system-tokens.md` 与 `docs/adr/0002`）的范围，本笔记不替其拍板。[S2][S12][S15]

---

## 9. 事实 → 对决策的约束表（不是建议）

| # | 事实 | 约束了哪个选项 |
| --- | --- | --- |
| F1 | 冷启动首帧前由系统画 blank starting window / SplashScreen Window；App 完成首帧后系统才换窗[S1][S4] | 「预渲染启动画面」若指系统提前出画面 → 唯一官方机制是 SplashScreen；不是 App 自绘缓存 |
| F2 | `Displayed`=TTID 自动上报；TTFD 需 `reportFullyDrawn`/`FullyDrawnReporter`/`ReportDrawn*`[S1][S15] | 只量「首帧」会漏掉异步资源；皮肤/列表类延迟要靠 TTFD 口径才看得见 |
| F3 | 目标冷<500 / 温<200 / 热<150ms，P95/P99 贴近中位[S3] | 任何优化都要有可重复 A/B 数字；蓝叠给的数字不能当达标证据 |
| F4 | SplashScreen 平台 API 31；`core-splashscreen:1.2.0` backport 到 API 23、AAR minCompileSdk=35[S4][S17][S19] | 本工程 minSdk 26 / compileSdk 36 下可用；但保持条件只许「少量本地数据」，不授权启动期网络 IO |
| F5 | Baseline Profile ~30% 首次启动；Startup Profile 再 ~15%；DEX 布局优化减 page fault[S6][S20] | 「预编译快启动」的官方主力机制；收益量级只能引官方原文，不能自己编百分比 |
| F6 | 本轮实证 profileinstaller / benchmark / startup / splashscreen AAR 全部 minCompileSdk ≤36，AGP 9.2.1 ≥ minAGP[S19] | Baseline Profile **消费端**不被 compileSdk 36 卡住；与 navigation-compose 2.10.2 的 37 阻断是两回事 |
| F7 | Macrobenchmark 在模拟器上报错 / 不具代表性；生成需 rooted 或 API 33+ 设备 + 改构建[S7][S8][S9] | **生成端**在本机（仅蓝叠、无真机、骨架期无 CUJ、按纪律不改构建）不成立；别承诺「现在就能产出 baseline-prof.txt」 |
| F8 | API 24–27 需 `androidx.profileinstaller` 首次运行装 profile；API 28+ 走 Play/Cloud[S6] | minSdk 26 覆盖 24–27 档，profileinstaller 在本工程是**必需**而非可选 |
| F9 | Compose 有 `precompose`/`createPausedPrecomposition`（低层 SubcomposeLayoutState），语义是「让下一次 measure 更快」[S11] | 「官方有预组合 API」成立，但**不是**启动期整屏预渲染开关；别据此宣称「Compose 支持启动预渲染」 |
| F10 | 官方启动建议是削减首次组合、lazy init、避免 IO、条件组合[S1][S2][S12] | 骨架期最现实的方向是「少做」，不是「多做预加载」 |
| F11 | 皮肤下载/解析/校验 = 网络+磁盘+反序列化，被官方列为启动期要移走的对象；占位先显示 + 后台更新是官方口径[S2][S12] | 「先默认皮肤、后台换肤」有机制依据；「启动期同步等皮肤包」与官方建议冲突 |
| F12 | `ReportDrawnWhen`/`FullyDrawnReporter` 可决定异步任务是否计入 TTFD[S1][S15] | 「换肤算不算启动完成」是可配置的度量边界，需维护者拍板 |

---

## 10. 未验证清单（不要当结论用）

1. **本工程实跑**：未执行 `gradlew :app:assembleDebug` / `generateBaselineProfile` / `connectedCheck`；§4.4 的「可用」判定基于 AAR 元数据 + 官方最低版本声明，未在本工程构建实测（按纪律不改代码、不改构建、不跑 git）。
2. **蓝叠实例内真实 API level 未回读**：`adb shell getprop ro.build.version.sdk` 在本轮未跑（仓内 `bluestacks-adb-compose.md` 亦列为未验证）；「Tiramisu64=API 33」由镜像名 + 官方 Android 版本表推出，不是本机实测输出。这直接影响「connected device 须 rooted 或 API 33+」是否满足。
3. **Baseline Profile Gradle 插件在 AGP 9.2.1 / Gradle 9.4.1 的确切兼容性**：官方只给「最低 AGP 8.0.0」「推荐最新 AGP」，并列表到 AGP 9.1；未找到针对 9.2.1 的显式声明，也未实跑。`benchmark-gradle-plugin:1.5.0` 与 AGP 9.2.1 的组合未验证。
4. **Kotlin 2.3.0 / Compose BOM 2025.12.00 与 benchmark 1.5.0 POM 的 `kotlin-stdlib` 版本是否有官方兼容性声明**：未查到；benchmark-macro-junit4 1.5.0 POM 的传递依赖未逐条比对 BOM。
5. **`core-splashscreen:1.2.0` / `profileinstaller:1.4.1` 等 AAR 的 `AndroidManifest.xml` 内 uses-sdk minSdk 精确值**：AAR manifest 是二进制 AXML，本轮未用 aapt/axml 解析，minSdk 维度只采信官方「backport down to API 23」与编译行为表，未做 manifest 实证。
6. **「App 主动预加载类/资源（Class.forName / 预读 drawable）」是否有官方正面推荐**：本轮在启动/性能/Compose 指南与 API 参考中未见；未做全量 API 索引检索，按「未见」处理，不排除存在未命中页面。
7. **「高层 Compose 启动预渲染 API」是否存在**：检索了 `precompose`/`precomposition`/`premeasure` 命中的 `SubcomposeLayoutState` 与 Compose UI release notes；未穷举所有 `@Experimental` 内部/预览 API，按「未见官方 API」处理。
8. **`createPausedPrecomposition`（Added in 1.9.0）在 BOM 2025.12.00 解析出的 `ui` 1.10.0 里的精确可用/稳定状态**：API 参考标 Cmn、Added 1.9.0；未解包本机 Gradle 缓存中 BOM 实际 AAR 复核。
9. **官方「~30% / ~15%」收益的测算条件**：原文未给机型/基线/测量方法细节；本笔记只引原文数字，未核实其实验条件，也未在本工程复现。
10. **「先默认皮肤、后台换肤」对重组范围/首次组合成本的实际影响**：属 `compose-design-system-tokens.md` / `docs/adr/0002` 范围，本笔记只给机制依据，未测重组次数与耗时。
11. **Macrobenchmark `timeToFullDisplayMs` 在蓝叠上是否真会报错**：官方只说「attempting to measure an emulator」会报错；蓝叠是模拟器类但通过 TCP adb 连接，是否被判为 emulator 未实跑验证。
12. **Perfetto / System Tracing app 在蓝叠 Tiramisu64 上是否可用**：官方口径是「设备自带 System Tracing 需 API 28+」；蓝叠是否内置该 app、开发者选项能否开启未验证。
13. **R8 / Startup Profile 与本工程现有 `libs.versions.toml` 的相互作用**：只确认了 activity-compose 1.11.0 的 AAR 元数据 =36；未跑 R8、未生成 startup-prof.txt。

---

## 11. 参考资料（primary source）

> 本轮 `developer.android.com` 主站在会话内取不到（web_fetch 报 `fetch failed` / Python urllib 10060），正文经 `developer.android.google.cn` 镜像读取全文，以下链接仍给官方 .com 地址（与 `compose-design-system-tokens.md` 的处理一致）。AAR / POM / maven-metadata 取自 `dl.google.com`（Google Maven，一手）。

**启动定义与度量**
- [S1] [App startup time — cold/warm/hot、TTID/TTFD、`Displayed`、Perfetto、initial composition](https://developer.android.com/topic/performance/issues/launch-time)
- [S2] [App startup time — Solve common issues（Heavy app/activity initialization、custom splash screens）](https://developer.android.com/topic/performance/issues/launch-time#common)
- [S3] [Overview of measuring app performance — 500/200/150ms 目标、`speed`/`speed-profile` 编译、trampoline、校准](https://developer.android.com/topic/performance/measuring-performance)
- [S12] [App startup analysis and optimization — lazy/async、StrictMode、conditional composition、frame data](https://developer.android.com/topic/performance/appstartup/analysis-optimization)
- [S15] [Capture Macrobenchmark metrics — StartupTimingMetric `timeToInitialDisplayMs`/`timeToFullDisplayMs`、`ReportDrawn*`](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [S16] [Activity API 参考（`reportFullyDrawn()`）](https://developer.android.com/reference/android/app/Activity)、[Activity 1.2.0 release note（reportFullyDrawn backport 到 ComponentActivity）](https://developer.android.com/jetpack/androidx/releases/activity)、[ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN](https://developer.android.com/reference/android/app/ApplicationStartInfo)、[ProfilingTrigger.TRIGGER_TYPE_APP_FULLY_DRAWN](https://developer.android.com/reference/android/os/ProfilingTrigger)

**SplashScreen**
- [S4] [Splash screens — Android 12 系统 Window、core-splashscreen 到 API 23、`setKeepOnScreenCondition`、退出动画、installSplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [S5] [android.window.SplashScreen API 参考（Added in API level 31）](https://developer.android.com/reference/android/window/SplashScreen)
- [S17] [Core release notes — 1.0.0-alpha01「all the new APIs backported down to API 23, with the exception of the icon background」](https://developer.android.com/jetpack/androidx/releases/core)
- [S18] Google Maven maven-metadata： [core-splashscreen](https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/maven-metadata.xml)、[profileinstaller](https://dl.google.com/dl/android/maven2/androidx/profileinstaller/profileinstaller/maven-metadata.xml)、[benchmark-macro-junit4](https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-macro-junit4/maven-metadata.xml)、[benchmark-gradle-plugin](https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-gradle-plugin/maven-metadata.xml)、[startup-runtime](https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/maven-metadata.xml)、[activity-compose](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml)

**Baseline Profiles / 工具链**
- [S6] [Baseline Profiles overview — ~30%、AOT/PGO、最低版本、AGP 版本表、分 Android 版本编译行为表、R8 生成/发布差异](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [S7] [Create Baseline Profiles — BaselineProfileRule、generateBaselineProfile、GMD/`aosp`、rooted 或 API 33+、physical device 测收益](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)
- [S20] [DEX layout optimizations and startup profiles — 要求、主 classes.dex、startup-prof.txt、R8](https://developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations)
- [S21] [Manually create and measure Baseline Profiles — ProfileInstaller 在 API 29+ 装 profile、`bg-dexopt`、`cmd package compile -m speed-profile`](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure)
- [S22] [ProfileInstaller release notes — 1.4.1 / 1.4.0（API 35+ 支持）](https://developer.android.com/jetpack/androidx/releases/profileinstaller)
- [S23] [Android Developers Blog — Tinder cuts app cold starts by 47% with new R8 Configuration Analyzer](https://developer.android.com/blog/posts/tinder-cuts-app-cold-starts-by-47-with-new-r8-configuration-analyzer)
- [S27] [Enable app optimization with R8 — Faster startup、class merging、isMinifyEnabled/isShrinkResources、AGP 9.x DSL、optimized resource shrinking](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [S19] 本轮逐个下载并读取的 AAR 内 `META-INF/com/android/build/gradle/aar-metadata.properties`（URL 形如）：[core-splashscreen-1.2.0.aar](https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/1.2.0/core-splashscreen-1.2.0.aar)、[profileinstaller-1.4.1.aar](https://dl.google.com/dl/android/maven2/androidx/profileinstaller/profileinstaller/1.4.1/profileinstaller-1.4.1.aar)、[benchmark-macro-junit4-1.5.0.aar](https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-macro-junit4/1.5.0/benchmark-macro-junit4-1.5.0.aar)、[benchmark-macro-1.5.0.aar](https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-macro/1.5.0/benchmark-macro-1.5.0.aar)、[startup-runtime-1.2.0.aar](https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/1.2.0/startup-runtime-1.2.0.aar)、[activity-compose-1.11.0.aar](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.11.0/activity-compose-1.11.0.aar)；POM：[benchmark-macro-junit4-1.5.0.pom](https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-macro-junit4/1.5.0/benchmark-macro-junit4-1.5.0.pom)、[core-splashscreen-1.2.0.pom](https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/1.2.0/core-splashscreen-1.2.0.pom)

**App Startup / Compose 预组合 / 度量工具**
- [S25] [App Startup — 单 ContentProvider 合并、`Initializer`、`InitializationProvider`、lazy initialization](https://developer.android.com/topic/libraries/app-startup)
- [S11] [SubcomposeLayoutState API 参考 — `precompose` / `createPausedPrecomposition` / `PrecomposedSlotHandle`](https://developer.android.com/reference/kotlin/androidx/compose/ui/layout/SubcomposeLayoutState)
- [S26] [Compose UI release notes — 1.0.0-beta07「hoist the SubcomposeLayout state … precompose」、1.2.0-alpha03「premeasure … precomposed」](https://developer.android.com/jetpack/androidx/releases/compose-ui)
- [S8] [Write a Macrobenchmark — com.android.test、`profileable`、ProfilerInstaller ≥1.3、「returns errors when attempting to measure an emulator」、connectedCheck](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [S9] [Benchmark in Continuous Integration（Macrobenchmark 对 emulator 的告诫）](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci)
- [S13] [App startup time — Perfetto `Android App Startups` 派生指标、`Compose:recompose/layout/draw`](https://developer.android.com/topic/performance/issues/launch-time#app-startup-perfetto)（同 S1 章节）
- [S14] [Capture a system trace on a device — System Tracing app 需 API 28+、Android 10+ 出 `.perfetto-trace`](https://developer.android.com/topic/performance/tracing/on-device)

**仓内（仅阅读，用于约束与交叉）**
- [S24] `docs/adr/0001-调试链路走命令行加蓝叠而非IDE集成.md`、`docs/research/bluestacks-adb-compose.md`（蓝叠 vsync=0 / 60fps / 鼠标输入，不能评帧率与手感；手感定稿必须回 USB 真机）
- `docs/research/compose-navigation-shell.md`（下载 AAR 读 `aar-metadata.properties` 的实证方法；compileSdk 37 阻断的先例）
- `docs/research/compose-design-system-tokens.md`、`docs/adr/0002-主题token层用自建CompositionLocal承载.md`（皮肤/主题落点，交叉但不替其拍板）
- `gradle/libs.versions.toml`（版本基线，只读）
