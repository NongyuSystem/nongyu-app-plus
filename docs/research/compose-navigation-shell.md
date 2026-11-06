# Compose 导航外壳事实核查（nongyu-app-plus）

> 核查日期 2026-11-06。只列事实与其对选项的约束，不给选型结论。所有版本号/坐标/API 名均来自下方「参考资料」的 primary source；未能取证的写「未验证」。
> 工程约束（背景已确认，不重复验证）：AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00 / compileSdk=36 / targetSdk=36 / minSdk=26 / JDK 21；本机仅 android-36；当前无导航依赖。

## 1 结论摘要

- 现稳定版 `androidx.navigation:navigation-compose` = **2.10.2**（Google Maven `<release>`；官方 release notes 标注 2026-09-23）。
- **硬阻断**：`navigation-compose-android:2.10.2` 的 AAR 元数据写 `minCompileSdk=37` / `minAndroidGradlePluginVersion=9.1.0`；本工程 compileSdk=36 → AGP 的 AAR 元数据校验会拒绝构建。这是 navigation-compose **自身** AAR 的要求，不是传递依赖带来的。
- 「2.9.x 要求 Compose 1.11 / compileSdk 37」**不成立**：2.9.8 / 2.9.7 / 2.9.0 的 AAR 元数据是 `minCompileSdk=35`。37 门槛由官方记录为 **2.10.0-alpha03**（2026-04-22）引入，落在 2.10.0 稳定线。
- 2.10.2 的 POM 把 Compose `animation`/`runtime`/`runtime-saveable`/`ui` 硬钉在 **1.10.5**，而 BOM 2025.12.00 映射 **1.10.0** → 引入它会把这几个模块抬离 BOM 指定版本。
- 零依赖方案所需 API（rememberSaveable、BackHandler、NavigationBar/BottomAppBar/Scaffold）全部在**现有依赖**里（BOM + activity-compose 1.11.0 + material3）。
- 「底部正中凸起 FAB + 两侧各两个 Tab」：M3 现成组件只能把 FAB 放在**末端**或 Scaffold 的 `FabPosition.Center`（在 bar 上方），**无居中 cradle/cutout**；精确表达需自绘 Box+offset / 自定义 Layout。
- Navigation 3 已稳定（1.2.0），但其 1.2.0 的 AAR 同样要求 minCompileSdk=37；1.0.0/1.1.0 是 36。

## 2 Q1：navigation-compose 在本工程约束下能不能用

### 2.1 当前稳定版本与确切坐标（已核实）

- Google Maven 索引 `.../androidx/navigation/navigation-compose/maven-metadata.xml`：`<latest>2.10.2</latest>`、`<release>2.10.2</release>`，`lastUpdated 20260923170048`。
- 官方 release notes 的「Latest Update / Stable Release」= September 23, 2026 / **2.10.2**，并给出 `val nav_version = "2.10.2"` 与 `implementation("androidx.navigation:navigation-compose:$nav_version")`。
- 确切坐标：`androidx.navigation:navigation-compose:2.10.2`（官方声明的 `packaging=aar` 由 `navigation-compose-android:2.10.2` 承载，见 POM 的 `navigation-compose-android` compile 依赖）。
- Kotlin/KMP 化事实：2.10.0 起 `navigation-common`/`navigation-runtime`/`navigation-testing` 增加 desktop/linux/macOS/iOS/js/WASM/watchos/tvos targets；`navigation-compose:2.10.2` 的 Gradle module 元数据含 `metadataApiElements`（common）与 `androidApiElements-published`（指向 `navigation-compose-android`）。**未验证**：这是否改变纯 Android 消费者可见的 public API；release notes 的 2.10.0 变化主要是 `predictivePop*Transition`、`NavBackStackEntryInfo`、`handleDeepLink` 收紧。
- 维护状态：官方 Navigation（2.x）release notes 页顶部有原文警告：「Caution: This library is in maintenance mode and will only receive critical fixes; new features are not planned. We recommend using Jetpack Compose for building Android UIs. See Compose-first for more information.」——但该页同时仍把 2.10.2 列为 Stable Release 且持续发版。**没有 3.x 的 `androidx.navigation:navigation-compose`**；「Navigation 3」是另一组 artifact（`androidx.navigation3:*`），见 §3.4。

### 2.2 compileSdk / minCompileSdk 门槛（核心，已实测）

逐个下载 AAR、读取 `META-INF/com/android/build/gradle/aar-metadata.properties`：

| AAR | minCompileSdk | minAGP |
| --- | --- | --- |
| `navigation-compose-android:2.10.2` | **37** | 9.1.0 |
| `navigation-runtime-android:2.10.2` | 34 | 8.1.1 |
| `compose.animation:animation-android:1.10.5` | 35 | 8.6.0 |
| `compose.ui:ui-android:1.10.5` | 35 | 8.6.0 |
| `compose.runtime:runtime-saveable-android:1.10.0` / `1.10.5` | 35 | 8.6.0 |
| `compose.runtime:runtime-android:1.10.5` | 34 | 8.1.1 |
| `lifecycle-*-android:2.11.0`、`savedstate-android:1.5.0` | 34 | 8.1.1 |

结论：**37 来自 navigation-compose 自身 AAR，不是传递依赖**。AGP 会校验 AAR 的 `minCompileSdk`（典型报错形如「Dependency ... requires 'compileSdkVersion' to be set to 37 or higher. Compilation target for module ':app' is 'android-36'」）。本工程 `compileSdk = 36` 且本机仅 android-36 → **2.10.2 无法在现配置下构建**。本工程的 AGP 9.2.1 满足 `minAndroidGradlePluginVersion=9.1.0`，不构成额外阻断。

对照历史版本（同一方法实测）：

| 版本 | minCompileSdk | minAGP |
| --- | --- | --- |
| `navigation-compose-android:2.9.8` / `2.9.7` / `2.9.0` | 35 | 8.6.0 |
| `navigation-compose:2.8.9` / `2.8.0` / `2.7.7` | 34 | 1.0.0 |

官方 release notes 原文（Version 2.10.0-alpha03, April 22, 2026）：「Updated Compose compileSdk to API 37. This means that a minimum AGP version of 9.2.0 is required when using Compose.」；2.10.0-rc01 另有「The minimum SDK for Navigation has been moved to API 24」。因此「2.9.x 要求 Compose 1.11 / compileSdk 37」**与证据不符**；37 是 2.10.0 稳定线的产物。

### 2.3 与 Compose BOM 2025.12.00 的兼容性

- BOM `2025.12.00` 的 POM（`compose-bom-2025.12.00.pom`）把 `compose.ui:ui`、`compose.runtime:runtime(-saveable)`、`compose.animation:animation` 映射到 **1.10.0**，`material3` 到 1.4.0。
- `navigation-compose:2.10.2` 的 POM / `.module` 把上述四个 Compose 模块的 `requires` 写为 **1.10.5**，`kotlin-stdlib` 为 2.1.20。Gradle 默认取最高版本 → 引入 2.10.2 会把这些模块从 1.10.0 抬到 1.10.5。
- 1.10.5 的 AAR 元数据仍只要 minCompileSdk=35（见上表），故「版本被抬高」本身**不新增** compileSdk 37 风险；风险只来自 `navigation-compose-android` 自身。
- 仓库 `gradle/libs.versions.toml:6-8` 的注释记录：BOM 2026.09.00 的 runtime-saveable 要求 compileSdk 37、本机 android-36 实测失败。旁证：`runtime-saveable-android:1.11.0` = 35，而 `1.12.1` = 37；BOM 2026.09.00 实际映射 `ui`/`runtime-saveable` 到 **1.12.1**。即 37 门槛出现在 Compose 1.12.x，与 navigation 2.10.x 各自的 37 是两条独立事实。

### 2.4 其它最低要求

- minSdk：官方 2.10.0-rc01 起 Navigation 最低 API 24；本工程 minSdk=26 满足。**未验证**：`navigation-compose` 的 `AndroidManifest.xml` 是否声明更高的 uses-sdk（未读取 manifest）。
- Kotlin：POM 的 `kotlin-stdlib` 为 2.1.20；本工程 Kotlin 2.3.0。**未验证**：是否存在 Kotlin 元数据版本（plugin 侧）不兼容的官方声明。
- compileSdk：见 §2.2，是唯一实测到的硬阻断。

## 3 Q2：零依赖（sealed interface + rememberSaveable）可行性

### 3.1 rememberSaveable 能否直接保存当前 Tab（已核实）

官方《Save UI state in Compose》/《State lifespans in Compose》：

- `rememberSaveable` 通过 `Bundle` 保存，能自动存原始类型（`Int`/`Long`/`Float`/`Double`/`String` 及其数组）；跨 Activity 重建与进程死亡恢复，恢复出的是 `==` 相等但非 `===` 的新实例。
- 非原始类型需：Parcelize、`listSaver`/`mapSaver`、自定义 `Saver`，或改用 `rememberSerializable`（要求 `@Serializable`）。官方给出的 `listSaver` 例子正是把对象拆成支持类型的 List。
- 对四个 Tab 的约束：`sealed interface` 对象本身**不是**可直接存的类型；最省事是存 `enum 的 ordinal/name` 或 `Int`（官方 adaptive-navigation 示例即 `var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }`），否则要写 `Saver`。`sealed interface Tab { ... }` + `entries.indexOf(...)` 是可行的，但属于自定义类型要 Saver 的一类。
- 注意官方提示：Bundle 有大小上限，别往里塞大对象；只存最小状态（ID/key）。

### 3.2 系统返回键 / BackHandler（已核实）

- `BackHandler` 在 artifact **`androidx.activity:activity-compose`** 中（包名 `androidx.activity.compose.BackHandler`）。本工程 `libs.versions.toml:17` 已有 `androidx-activity-compose = 1.11.0`、`app/build.gradle.kts:42` 已 `implementation(...)` → **已在现有依赖里，零新增**。
- 官方 API：`@Composable fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)`；注册到 `OnBackPressedDispatcher`，跟随 LifecycleOwner（STARTED 时启用）；多个时组合中**最后**启用的生效；应无条件调用、用 `enabled` 控制。
- 手写 Tab 切换下，返回键语义（回上一 Tab / 回首页 / 退出）需自行定义；官方另有 `androidx.navigationevent`（Navigation Event，稳定 1.2.0）用于 predictive back，但本期四空页不涉及。**未验证**：是否必须引入 navigationevent 才能满足 Android 13+ predictive back 的合规要求。

### 3.3 迁移成本与官方「何时该用」建议（部分核实）

- 官方《Navigation》指南「Framework options」原文：「Compose : If your app is built entirely with Jetpack Compose, use Navigation Compose. Destinations in your graph are composables.」——这是按 UI 架构给的**框架选择**，不是「几屏以上才该用」的阈值。
- 同页「Benefits and features」列出 Navigation component 提供的能力：动画/转场、深链、导航抽屉与底部导航等 UI 模式、类型安全的参数传递、ViewModel 按图作用域、Fragment 事务、默认正确的 back/up。
- **未验证**：官方是否有「简单应用用状态切换即可、无需 Navigation Compose」的明确文字。本次检索未找到这样一句原文；只找到官方在 adaptive-navigation 指南中用 `rememberSaveable` 跟踪当前 destination 的示例。
- 事实约束（非结论）：手写 `when(tab)` 切换不提供返回栈、深链解析、类型安全参数、按 destination 的 ViewModel 作用域。未来接这些能力时需引入导航库并重写外壳；而 §2.2 显示现稳定版在本机还被 compileSdk 卡住。
- 官方提供《Migrate from Navigation 2 to Navigation 3》指南（把 route 改为实现 `NavKey`、用 `rememberNavBackStack`/`rememberSerializable`），说明后续官方路线是 Navigation 3。

### 3.4 中间方案与稳定状态（已核实）

| 方案 | 稳定版本（Google Maven `<release>` / 官方 release notes） | 本工程相关 AAR 元数据 |
| --- | --- | --- |
| `androidx.navigation:navigation-runtime`（不带 compose） | 2.10.2 | `navigation-runtime-android:2.10.2` minCompileSdk=34（可用），但它只提供 `NavController`/graph，**不含 Compose `NavHost`**，四个空页用不上 |
| `androidx.navigation3:navigation3-runtime` / `:navigation3-ui`（`NavDisplay`） | **1.2.0 稳定**（官方「1.2.0 is now stable!」，2026-09-23；1.0.0 于 2025-11-19 首个稳定） | `navigation3-ui-android:1.2.0` / `navigation3-runtime-android:1.2.0` = **minCompileSdk=37**；`1.0.0` / `1.1.0` = 36 |
| `androidx.navigationevent:navigationevent-compose` | 1.2.0 | `navigationevent-compose-android:1.2.0` = minCompileSdk=36 |

补充事实：`androidx.navigation3:navigation3` / `androidx.navigation/navigation3` 这两个路径在 Google Maven 上是 404；正确 groupId 是 `androidx.navigation3`。Navigation 3 的 `1.2.0` POM 把 Compose 钉在 **1.10.0**（与 BOM 2025.12.00 一致），但 ui/runtime 的 Android AAR 要求 37 → 1.2.0 在本工程仍被卡；`1.1.0` 是 36 且 Compose 1.10.0，但是旧稳定线，官方配套 `lifecycleViewmodelNav3=2.12.0-alpha04`、`material3AdaptiveNav3=1.4.0-alpha02` 为 alpha。别把 Navigation 3 当成「稳定且能直接解本期问题」的现成答案。

## 4 Q3：本期「四个空页面」值不值得付这个依赖（只列事实约束，不给结论）

- **传递依赖数量**：`navigation-compose:2.10.2` 的 POM 直接依赖 9 项：`kotlin-stdlib:2.1.20`、`compose.animation:animation:1.10.5`、`navigation-common:[2.10.2]`、`compose.ui:ui:1.10.5`、`navigation-runtime:[2.10.2]`、`compose.runtime:runtime:1.10.5`、`navigation-compose-android:2.10.2`、`compose.runtime:runtime-saveable:1.10.5`；再往下 `navigation-runtime-android`/`navigation-common-android` 各带约 20 个 `lifecycle`/`savedstate`/`collection`/`activity`/`kotlinx-serialization-core` 依赖（POM 内的 `dependencyManagement` 原子组约束还列了 14 个 navigation 模块，但它们是版本约束、不是全部会被解析进图）。精确解析后的总数**未验证**（未跑 Gradle dependency tree）。
- **APK 体积量级**（实测下载字节，未打包、未 R8；仅作量级锚点）：`navigation-compose-android` ≈ 177 KB、`navigation-runtime-android` ≈ 178 KB、`navigation-common-android` ≈ 350 KB；其 Compose 传递 AAR 合计 ≈ 10.8 MB（ui 5.76 MB、runtime 2.50 MB、runtime-saveable 1.06 MB、animation 1.52 MB）——但这些 Compose 模块工程里本来就有（BOM 1.10.0），**增量主要是 1.10.0→1.10.5 的差分 + 约 0.7 MB 的 navigation AAR + lifecycle/savedstate 差分**。**未验证**：实际 `assembleDebug` 的 APK 增量字节（本次不构建）。
- **新的 minCompileSdk 风险**：有。`navigation-compose-android:2.10.2` 的 37 > 本机 36，是构建期硬失败，不是运行期风险。
- **是否影响已锁定 BOM**：会。2.10.2 把 4 个 Compose 模块从 BOM 指定的 1.10.0 抬到 1.10.5（§2.3）；`libs.versions.toml` 的「BOM 统一约束版本」注释将不再严格成立。
- 四个页面本期全空、无真实业务；所需能力仅为「记住当前 Tab + 点击切换」。

## 5 Q4：AI 圆按钮与导航的关系

### 5.1 M3 现成组件（已核实 API 签名）

- `NavigationBar(modifier, containerColor, contentColor, tonalElevation, windowInsets, content: @Composable RowScope.() -> Unit)`：官方文档写明 content「typically 3-5 `NavigationBarItem`s」，内部是 `Row` + `Arrangement.spacedBy`。**没有 FAB 插槽、没有 cutout 参数**。
- `Scaffold` 有 `floatingActionButton` + `floatingActionButtonPosition`；`FabPosition` 的取值有 `Start` / `Center` / `End` / `EndOverlay`。官方对 `Center` 的释义是「Position FAB at the bottom of the screen in the center, **above** the NavigationBar (if it exists)」——即居中但**在 bar 上方**，bar 本身不被挖空。
- `BottomAppBar` 有带 `floatingActionButton: (@Composable () -> Unit)?` 的重载，但官方原文：「It can optionally display a `FloatingActionButton` **embedded at the end** of the BottomAppBar.」参数说明同样写「optional floating action button **at the end** of this BottomAppBar」。
- M3 的 `BottomAppBar` 签名**无 `cutoutShape`**；`cutoutShape` 是 M2 (`androidx.compose.material.BottomAppBar`) 的参数。即 M3 不提供 cradle/锚定。

### 5.2 「正中凸起 + 两侧各两个 Tab」能否用现成组件表达

- 事实约束：`NavigationBar` 的 `RowScope` 内容会把所有 child 排成一行；若塞入一个 FAB，它是第 5 个等宽 child，而不是「浮在中间、两侧各两个」的凸起。`BottomAppBar` 的 FAB 插槽固定在末端。`FabPosition.Center` 能把 FAB 放到正中，但位置在 bar 上方、不嵌入、不挖空。
- 因此「正中凸起（docked/cradle）+ 左右各两个 Tab 项」**没有单一 M3 现成组件能直接表达**：要么用 `Scaffold(floatingActionButtonPosition = FabPosition.Center)` 接受「在上方而非嵌入」，要么自绘（Box + `Modifier.offset` / 自定义 Layout / 自定义 Shape 做 cutout）。社区常见做法即 Box+offset，但那是第三方实践、非官方 API，本核查不作为 primary source 结论。
- AI 入口与导航的关系：官方把 FAB 归为 Scaffold/BottomAppBar 的插槽、把 `NavigationBarItem` 归为 `NavigationBar` 的内容；二者是**独立的槽位**，不依赖 `NavController`。本期用 `onClick` 直接改状态即可表达「独立 AI 入口」。

## 6 事实 → 决策约束表

| 事实 | 约束了哪个选项 |
| --- | --- |
| 2.10.2 是当前稳定版（官方 + Maven 索引） | 若要「用最新稳定」，就是 2.10.2，不存在更低的「稳定版」可挑 |
| `navigation-compose-android:2.10.2` minCompileSdk=37 | 现配置（compileSdk 36 / 仅 android-36）下该版本**构建不过**；要用它必须先解决 android-37 |
| 2.9.8/2.9.7/2.9.0 minCompileSdk=35 | 降到 2.9.x 可绕开 37，但用的是旧稳定线，且 `navigation-runtime 2.9.x` 把 lifecycle 钉在 2.9.x（工程现为 2.10.0） |
| 2.10.2 把 Compose 钉在 1.10.5 ≠ BOM 1.10.0 | 引入它 = 放弃「BOM 严格锁定」这一既有约定（或需显式 force/升级 BOM） |
| Compose 1.12.x AAR = minCompileSdk 37 | 直接升 BOM 到 2026.09.00 同样过不了本机（与仓库 toml 注释一致） |
| BackHandler 在 activity-compose 1.11.0（已有） | 零依赖方案的返回键处理**不新增依赖** |
| rememberSaveable 只自动存原始类型 | sealed interface 当前 Tab 需存 ordinal/name 或写 Saver |
| Navigation 3 1.2.0 稳定但 AAR 要 37；1.1.0 是 36 | 「用官方新导航库」在本机同样要处理 compileSdk；1.1.0 可用但配套多为 alpha |
| M3 NavigationBar/BottomAppBar 无居中 FAB cutout | 正中凸起 AI 按钮必须偏离现成组件（Center 位置或自绘） |
| Navigation 2.x 官方标注 maintenance mode | 新建长期依赖的维护预期需纳入考量（但仍发稳定版） |

## 7 未验证清单

1. 本工程实际跑 `./gradlew :app:assembleDebug` 引入 2.10.2 后的**确切报错文本**与 APK 增量字节（本次按要求不构建、不改代码）。§2.2 的失败判定基于 AAR 元数据 + AGP 已知的 AAR 元数据校验机制，未在本工程实测。
2. `navigation-compose(android):2.10.2` 的 `AndroidManifest.xml` 是否声明高于 24 的 minSdk（未读取 manifest）。
3. Kotlin 2.3.0 与 navigation-compose 2.10.2 编译所用 Kotlin 元数据版本（stdlib 2.1.20）是否有官方兼容性声明。
4. 官方是否有一句明确的「简单/少量屏幕无需 Navigation Compose，用状态即可」的原文建议（本次未检索到）。
5. Gradle 实际解析后的完整传递依赖树条数、以及 R8 开启后的真实 APK 体积增量。
6. Navigation 2.10.x KMP 化后，纯 Android 消费者的 public API 是否有破坏性变化（release notes 未逐条列出）。
7. Android 13+ predictive back 对「手写 BackHandler」方案的合规要求是否必须引入 `androidx.navigationevent`。

## 8 参考资料（primary source）

- Google Maven 索引：[navigation-compose maven-metadata.xml](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml)、[navigation-runtime](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-runtime/maven-metadata.xml)、[navigation3-runtime](https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-runtime/maven-metadata.xml)、[navigation3-ui](https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-ui/maven-metadata.xml)、[navigationevent-compose](https://dl.google.com/dl/android/maven2/androidx/navigationevent/navigationevent-compose/maven-metadata.xml)、[runtime-saveable-android](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-saveable-android/maven-metadata.xml)
- POM / Gradle module 元数据：[navigation-compose-2.10.2.pom](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/2.10.2/navigation-compose-2.10.2.pom)、[...2.10.2.module](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/2.10.2/navigation-compose-2.10.2.module)、[compose-bom-2025.12.00.pom](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.12.00/compose-bom-2025.12.00.pom)、[compose-bom-2026.09.00.pom](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom)
- AAR 内的 `META-INF/com/android/build/gradle/aar-metadata.properties`（本次逐个下载并读取，URL 形如）：[navigation-compose-android-2.10.2.aar](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose-android/2.10.2/navigation-compose-android-2.10.2.aar)、[navigation-runtime-android-2.10.2.aar](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-runtime-android/2.10.2/navigation-runtime-android-2.10.2.aar)、[navigation3-ui-android-1.2.0.aar](https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-ui-android/1.2.0/navigation3-ui-android-1.2.0.aar)
- 官方 release notes：[Navigation（含 2.10.0-alpha03 compileSdk 37 原文、2.10.2 稳定版、maintenance mode 警告）](https://developer.android.com/jetpack/androidx/releases/navigation)、[navigation3（1.2.0 稳定）](https://developer.android.com/jetpack/androidx/releases/navigation3)、[Compose Runtime（compileSdk 37 相关）](https://developer.android.com/jetpack/androidx/releases/compose-runtime)
- 官方指南：[Navigation（Framework options / Benefits / 依赖声明）](https://developer.android.com/guide/navigation)、[Save UI state in Compose（rememberSaveable / Saver）](https://developer.android.com/develop/ui/compose/state-saving)、[State lifespans in Compose](https://developer.android.com/develop/ui/compose/state-lifespans)、[Navigation 3 概览](https://developer.android.com/guide/navigation/navigation-3)、[Navigation 3 Get started（artifact 与版本）](https://developer.android.com/guide/navigation/navigation-3/get-started)、[Migrate from Navigation 2 to Navigation 3](https://developer.android.com/guide/navigation/navigation-3/migration-guide)、[Build adaptive navigation（rememberSaveable 跟踪 destination 示例）](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)
- 官方 API 参考：[BackHandler](https://developer.android.com/reference/kotlin/androidx/activity/compose/BackHandler.composable)、[BottomAppBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/BottomAppBar.composable)、[NavigationBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBar.composable)、[NavigationBarItem](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBarItem.composable)、[FabPosition](https://developer.android.com/reference/kotlin/androidx/compose/material3/FabPosition)、[rememberSaveable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/rememberSaveable.composable)
- 工程内文件（仅阅读）：`gradle/libs.versions.toml`、`app/build.gradle.kts`
