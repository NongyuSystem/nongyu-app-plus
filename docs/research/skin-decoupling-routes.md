# 皮肤与视觉资产脱离 APK 发布周期：技术路线事实核查

> **范围**：只核查「视觉资产能否脱离 APK 发布周期（B 端上传皮肤 → 用户不更新软件即可下载使用）」涉及的技术事实与官方边界，不给选型结论、不替维护者拍板。维护者原话：「皮肤和分隔的两种同时和程序解耦方法（就是可以在 b 上传皮肤，然后用户不更新软件就可以下载使用）」。
> **版本基线**（硬约束，本轮不重复验证）：AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00（≈ Compose 1.10 + Material3 1.4.0）/ compileSdk = 36 / targetSdk = 36 / minSdk = 26 / JDK 21；本机仅 android-36。工程为骨架阶段：无网络层、无状态管理、无路由、无依赖注入、无图片加载库，`res/` 只有 launcher 图标（[README.md](../README.md)、[app/build.gradle.kts](../../app/build.gradle.kts)）。
> **仓内既有结论**（背景复用，不重复论证）：主题 = 颜色 token 集合，皮肤 = 主题 + 图标/字体等资源包（皮肤是主题的 superset）；服务端存「用户选哪套皮肤」与「客户端色板定义」是两个不同的层（[GLOSSARY.md](../../GLOSSARY.md)）。色板唯一真源为自建 `NongyuColors` + `CompositionLocal`，由数据源接口提供、本期 mock、后期换数据库/皮肤实现时 Composable 零改动；`ColorScheme` 只是 48 槽位投影（[ADR 0002](../adr/0002-主题token层用自建CompositionLocal承载.md)）。存储层已定 MinIO（S3 兼容），Go 服务已 DEPRECATED、现行 Node；已拍板待带入：后端单起 Go（需补压测证据）、只做资源防盗链/预签名 URL 不需要 SSO、色值走数据库下发。
> **写法与实证方法**：结构对齐 [compose-design-system-tokens.md](compose-design-system-tokens.md)；库版本核查沿用 [compose-navigation-shell.md](compose-navigation-shell.md) 的「下载 AAR 读 `META-INF/com/android/build/gradle/aar-metadata.properties`」方法（本轮在内存中解包，不落盘、不改工程）。
> 每条结论标注来源编号 `[S#]`；查不到就写「未验证」或「未见」，不编。标注 **未验证** 的条目汇总见 §11。

## 1. 结论摘要

1. **纯数据/配置下发是官方明文支持的「不发版改外观」路径**。Firebase Remote Config 官方把「用参数改 layout 或 **color theme**、无需发布 app update」直接列为 Key capability，并明确规定参数值是**字符串**、由客户端库 fetch/cache、**由 app 自己决定何时 activate** `[S1]` `[S2]`。它在本笔记中是「运行时换主题的官方口径」依据；**是否把 Firebase 作为落地依赖另说**（仓内已定 MinIO + 数据库下发色值，且 Firebase 在大陆的连通性本轮**未验证**）。
2. **颜色落到 Compose 有官方投影路径，但 `ColorScheme` 加不了槽位**：`ColorScheme` 是 `@Immutable class`（非 data class）、主构造器固定 48 个 `Color` 槽位；官方提供 `lightColorScheme(primary = …)` 与「扩展主题包裹 `MaterialTheme` + 自建 `CompositionLocal`」，并明确说这种 ExtendedTheme **支持多主题**（可嵌套）`[S3]` `[S4]`。远程 JSON 只能映射到编译期已知的槽位/自建 `NongyuColors` 字段。
3. **换肤的重组代价是官方可查的**：`compositionLocalOf` 换值只失效读 `current` 的节点；`staticCompositionLocalOf` 换值导致提供处整块 content lambda 重组 `[S5]`。ADR 0002 已选 `compositionLocalOf`。这是一次换肤要付的成本，不是第二条解耦轴线。
4. **「外部二进制资源挂进 `Resources`」：隐藏 API 路线不可作为稳定方案，但有公开 API 替代**。`AssetManager` 的公开 API 里**没有** `addAssetPath`（只有 `open/list/openFd/openNonAssetFd/openXmlResourceParser`）`[S6]`；AOSP 中 `addAssetPath` 带 `@hide`/`{@hide}` `[S7]`。实测 Android 16 官方 `hiddenapi-flags.csv`：`addAssetPath` = `unsupported`（可用但「不受支持、随时可能改、预计未来会被条件屏蔽」）、`addAssetPathAsSharedLibrary` = `unsupported`、`addAssetPathInternal` = `blocked`、`setApkAssets` = `max-target-o` `[S8]`；且 Android 9（API 28）起对 non-SDK 接口的限制**同样适用于反射与 JNI** `[S9]`。
5. **做得到的公开路径（API 30+）**：`ResourcesProvider.loadFromApk(ParcelFileDescriptor)` / `loadFromDirectory` / `loadFromTable` / `loadFromSplit` → `ResourcesLoader` → `Resources.addLoaders(ResourcesLoader…)`；官方原文「supply additional resources and assets **or modify the values of existing resources and assets**」`[S10]` `[S11]` `[S12]`。代价：minSdk 26 → 需 API 30 版本分支；26–29 **无公开等价**；挂进去的资源靠 `getIdentifier` 按名查（编译期 `R.*` 不认识）；改 provider/loader 列表应在 UI 线程，否则与 UI 线程锁竞争 `[S11]`。
6. **不挂 `Resources` 也能换图标/字体**（本工程最贴近的一手依据）：`BitmapFactory.decodeFile/decodeStream`、`ImageDecoder.createSource(File)`、`Typeface.createFromFile(File)` 都接受文件路径/流，把下载物当**普通文件解码**即可，无 API 30 门槛 `[S13]` `[S14]` `[S15]`；颜色用 `android.graphics.Color.parseColor("#RRGGBB"/"#AARRGGBB")` 得 32 位 sRGB color int，Compose `Color(int)` 直接吃 `[S16]` `[S17]`。
7. **Play Asset Delivery / Dynamic Feature Module 不满足「用户不更新软件」**：PAD 资产包在**构建期**由 `com.android.asset-pack` 插件声明并打进 App Bundle，全部由 Google Play 托管 `[S18]` `[S19]`；官方「Asset updates」原文写明 `install-time` 包**随 base app update 一起更新**，`fast-follow`/`on-demand` 包的更新流程也以「上传新版本到 Google Play」为前提 `[S19]`。PFD 明确「requires you to publish your app using an app bundle」`[S20]`。
8. **中国大陆无 Play 场景下 PAD/DFM 不可用（事实）**：Google 官方支持论坛原文「Google Play is not currently available in China.」`[S21]`；PAD 又要求资产包由 Play 托管/下发 `[S18]`，因此该通道在此场景不成立。注意「[开发者与商家注册支持地区](https://support.google.com/googleplay/android-developer/answer/9306917?hl=en) 列有 China」是**开发者账号注册**范畴，与消费者侧 Play Store 可用性不是一回事；精确的「分发国家/地区列表」官方页本轮 404，**未验证** `[S21]`。
9. **远程 UI/WebView 在 Compose 里只是「嵌一块 View」**：官方原文「To use a `WebView` in Jetpack Compose, you **must wrap it in an `AndroidView`**」，并列出状态持久化、返回键、嵌套滚动（`LazyColumn` 嵌套「does not currently work properly」）、主题同步（`prefers-color-scheme` / DayNight）等额外成本；替代方案是 Chrome Custom Tabs `[S22]`。**未检索到** Android 官方的「Compose Server-Driven UI」指南（只见第三方文章，按「未见」处理）`[S23]`。
10. **launcher 图标不能由下载文件直接指定**：`<activity-alias android:icon="drawable resource" …>` 的图标是 **manifest 中的 drawable 资源**，alias 必须**在 manifest 里、且声明在 targetActivity 之前**编译期预置 `[S24]`；运行时只能在预置 alias 集合间用 `PackageManager.setComponentEnabledSetting` 切换 `[S25]`。Android Q（API 29）起 `LauncherApps.getActivityList` 官方注释：除非满足例外，否则至少有一个 activity 或**合成 activity（系统设置里的应用详情页）**会出现在列表里 `[S26]`。
11. **安全边界**：Play《Device and Network Abuse》原文禁止「从 Google Play 以外来源下载可执行代码（dex、JAR、.so）」，但明写该限制**不适用于**运行在虚拟机/解释器中、间接访问 Android API 的代码（如 WebView 里的 JavaScript）；同时禁止 webview 加载未验证 URL 的 JS Interface `[S27]`。**纯色值/图标/字体不是可执行代码**，不触犯该条；但该政策只约束**经 Google Play 分发**的应用——本仓按 [AGENTS.md](../../AGENTS.md) 走 GitHub Release 分发时不受此政策约束，**Android 平台侧限制（non-SDK、APK 签名）仍然适用**。
12. **签名与预签名 URL 的定位**：Android 要求所有 APK 安装/更新前必须用证书签名，且 app signing key 在应用生命周期内不变 `[S28]`；但**下载的资源包不是已安装 APK，平台不代它做完整性校验**。`[S27]` 的 Play 政策里另有「不得用 Google Play 以外的方式 modify/replace/update 自身应用」。AWS S3（MinIO 兼容的 S3 语义）官方定义：presigned URL 是「用安全凭证授予**时间受限**的对象下载权限」，本质是 **bearer token**，有效期到配置时间或其底层凭证失效为止（CLI/SDK 最长 7 天、控制台 12 小时）`[S29]` `[S30]`。即预签名 URL 解决「谁能取」，**不解决「取到的是不是官方包」**；与仓内已拍板「只做资源防盗链/预签名 URL、不需要 SSO」一致。
13. **版本协商没有 Android 官方规范（未见）**，但有三条一手锚点：Remote Config 的客户端模板条件规则含 **App version**、模板**版本化 + 可回滚**、并要求「每次更新 app 时同步 in-app 默认参数值」`[S2]` `[S1]`；`kotlinx.serialization` 的 `Json { ignoreUnknownKeys = true }` 官方 API 明确「遇到未知属性忽略而不是抛 `SerializationException`，默认 `false`」，示例正是忽略新增的 `"version"` 键 `[S31]`；`Resources.getIdentifier` 是公开的名字式资源查找 `[S12]`。
14. **新库在 compileSdk 36 下的硬结论（AAR 实测）**：`coil3` 现稳定版 3.6.3 `minCompileSdk=37` **构建不过**；**3.5.0 是最后一条 `minCompileSdk=36` 的稳定线**（3.4.0 = 35，3.0.0–3.3.0 = 1，3.6.0 起 = 37），且 3.5.0 的 `coil-android` / `coil-compose-core-android` / `coil-core-android` 均为 36、manifest `minSdkVersion=23` `[S32]` `[S33]`。`com.google.android.play:asset-delivery:2.3.0` / `feature-delivery:2.1.0` / `core:1.10.3` 的 AAR **不含 `aar-metadata.properties`**（无 minCompileSdk 约束），但受 §7 的 Play 依赖限制 `[S34]`。`ktor-client-android:3.6.1` 与 `kotlinx-serialization-json:1.12.0` 是 KMP  umbrella，Android/JVM 变体落到 **JAR**（无 AAR、无 minCompileSdk），但其 `.module` 把 `kotlin-stdlib` 要求为 **2.3.21 / 2.4.10**，均 ≥ 或 > 本仓 Kotlin 2.3.0 → 兼容性**未验证** `[S35]` `[S36]`。
15. **【Q9】「两种解耦方法」用词含糊，从第一性原理可以归纳出两条相互独立、正交的解耦轴线**（**只归纳可能性，由维护者拍板**）：**轴线 A = 载荷通道（数据/色值通道 vs 二进制资源包通道）**，依据是「主题 ⊂ 皮肤」的载荷分层与两套互不相同的官方加载机制；**轴线 B = 绑定方式（资源本体下发 vs 偏好标识下发）**，依据是 GLOSSARY 已把「服务端存用户选哪套」与「客户端色板定义」判为两层，且「只下发 skinId、资源全内置」同样能实现不更新换外观。详见 §9。

## 2. Q1 — 纯数据/配置下发：官方口径、Compose 可行性、重组代价

### 2.1 官方对「运行时换主题 / 动态配置」的明文口径

Firebase Remote Config 官方首页原文 `[S1]`：

> "Firebase Remote Config is a cloud service that lets you **change the behavior and appearance of your client app or server without requiring users to download an app update**."
> Key capabilities：「You can make changes to your app's default behavior and appearance by changing parameter values remotely. For example, you could use a Remote Config parameter as a feature flag to **change your app's layout or color theme** to support a seasonal promotion, **with no need to publish an app update**.」

机制（同页「How does it work?」）`[S1]`：in-app 默认值 → 控制台/后端 API 覆盖 → 客户端库负责 **fetching / caching**，但「**Your app or server implementation controls when updates are applied**」（何时 activate 由 app 决定）。实现路径给出 `setDefaults()`、fetch/activate/get、以及「Whenever you update your app, you should **synchronize its default parameter values** with the Remote Config backend」。

参数形状与上限 `[S2]` `[S1]`：参数值**一律存为字符串**（控制台可选 `String`/`Boolean`/`Number`/`JSON` 做格式校验）；每项目每模板类型 **3000 个参数**、**2000 个条件**、key ≤ 256 字符、**所有参数值总字符串长度 ≤ 1,000,000 字符**；每模板类型最多存 **300 个生命周期模板版本**，可对比与回滚 `[S2]`。客户端模板的条件规则含 **App version**、OS/平台、语言、国家、Analytics 受众、随机百分位、自定义信号 `[S2]`。

政策边界（官方「Policies and limits」原文）`[S1]`：
> "Don't use Remote Config to make app updates that should require a user's authorization." / "Don't store confidential data in Remote Config parameter keys or parameter values … end users can access any default or fetched Remote Config parameter that is available to their app instance." / "**Don't attempt to circumvent the requirements of your app's target platform using Remote Config.**"

**对本工程的定位（事实，非建议）**：上述是「运行时换主题」的官方口径依据；但仓内已拍板「色值走数据库下发」、存储层已定 MinIO，故 Remote Config 在本笔记中**只作事实参照、不是既定实现**。Firebase/Google Play services 在中国大陆的连通性本轮**未验证**（§11）。

### 2.2 JSON/配置 → ColorScheme / 自定义色板的可行性

- **解析层**：`android.graphics.Color.parseColor(String)` 支持 `#RRGGBB` / `#AARRGGBB`，返回 32 位 sRGB color int，不可解析时抛 `IllegalArgumentException` `[S16]`；Compose `androidx.ui.graphics.Color` 官方给出「from a 32-bit SRGB color integer」的 `Color(int)` 构造 `[S17]`。即「色值字符串 → Compose Color」是公开 API 可完成的，**不需要**任何反射或隐藏 API。
- **投影层**：`ColorScheme` 是 `@Immutable class`、主构造器固定 **48 个 `val …: Color`**（非 data class，无 `equals`/`componentN`），旧 29/36 槽位构造器已弃用；官方提供 `lightColorScheme(primary = …)` / `darkColorScheme(…)` 与 Material Theme Builder 导出 Compose 代码 `[S3]` `[S4]`。本轮再次核对 androidx 源码 `ColorScheme.kt`（androidx-main）确认 `@Immutable public class ColorScheme(` + 48 个 `val`、弃用注解文案 "Use constructor with additional 'fixed' container roles." `[S3]`。
- **结论（事实推导）**：远程 JSON 能映射到**编译期已存在**的 48 个 MD3 槽位 + 自建 `NongyuColors` 的分组字段；**不能**凭空给 `ColorScheme` 加 `brandMuted`/`glass`/`aiOrb`（仓内 ADR 0002 已据此决策）。「语义 token 表 → ColorScheme 的官方自动映射 API」**未见**（§11）。
- **入库形状**：仓内 ADR 0002 定的是「分组结构 + 数据源接口 + 本期 mock」，因此「数据库/皮肤实现替换时 Composable 零改动」是**仓内架构约定**，不是 Android 官方 API 承诺 `[S4]`；本轮只核实其依赖的 Compose 机制合法。

### 2.3 动态替换 CompositionLocal 色板与重组代价

官方「Custom design systems in Compose」给出完整骨架并明确支持多主题 `[S4]`：

> "Another approach is to define an extended theme that **'wraps' MaterialTheme and its values**."
> ```kotlin
> @Immutable data class ExtendedColors(val caution: Color, val onCaution: Color)
> val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors(Color.Unspecified, Color.Unspecified) }
> @Composable fun ExtendedTheme(...) {
>     val extendedColors = ExtendedColors(...)
>     CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
>         MaterialTheme(/* colors = ..., ... */ content = content)
>     }
> }
> object ExtendedTheme { val colors: ExtendedColors @Composable get() = LocalExtendedColors.current }
> ```
> "It also **supports multiple themes** as you can nest `ExtendedTheme`s in the same way as `MaterialTheme`."

同页「Use Material components」原文：扩展值要生效必须「wrap them in your own composable functions, **directly setting the values you want to alter**」（示例 `ButtonDefaults.buttonColors(containerColor = ExtendedTheme.colors.caution, …)`）`[S4]` —— 即 M3 组件默认只读 `colorScheme`，自定义 Local 不会自动跟色（仓内 ADR 0002 已把这条列为最主要的日常代价）。

两种 Local 的重组语义（`Locally scoped data with CompositionLocal` 逐字）`[S5]`：

> "`compositionLocalOf`: **Changing the value provided during recomposition invalidates only the content that reads its `current` value.**"
> "`staticCompositionLocalOf`: … reads … are **not tracked** by Compose. **Changing the value causes the entirety of the content lambda where the CompositionLocal is provided to be recomposed**, instead of just the places where the `current` value is read."
> "**If the value provided to the CompositionLocal is highly unlikely to change or will never change, use `staticCompositionLocalOf`** …"
> "`CompositionLocal` is **what the Material theme uses under the hood**"; "**Note: `CompositionLocal` works well for foundational architecture**"; "**A `CompositionLocal` should have a good default value** … previewing … will always require it to be explicitly provided."

**事实约束**：换肤 = 一次「换 CompositionLocal 提供值」。用 `compositionLocalOf`（ADR 0002 的选择）只重组读它的节点；若改用 `staticCompositionLocalOf`（官方 ExtendedTheme 示例用的就是它）则是提供处整块 content 全量重组。这是**实现代价**，与「解耦了几条轴线」无关（§9）。

## 3. Q2 — 二进制资源包下发：能否把 APK 外资源挂进 AssetManager / Resources

### 3.1 `addAssetPath` 不是公开 SDK（源码与官方 API 参考）

`AssetManager` 官方 API 参考全文列出的 public methods 为：`close / getLocales / list / open(String,int) / open(String) / openFd / openNonAssetFd(×2) / openXmlResourceParser(×2)` —— **没有 `addAssetPath`**；类文档写的是「Provides access to an application's **raw asset files** … files that have been **bundled with the application** as a simple stream of bytes」`[S6]`。

AOSP 源码中该方法带隐藏标记：`core/java/android/content/res/AssetManager.java` 的 `addAssetPath` 上方为 `@hide` / `{@hide}` `[S7]`。

### 3.2 非公开 API 限制的官方依据（Android 9+）与 Android 16 实测分类

官方《Restrictions on non-SDK interfaces》原文 `[S9]`：

> "**Starting in Android 9 (API level 28), the platform restricts which non-SDK interfaces your app can use. These restrictions apply whenever an app references a non-SDK interface or attempts to obtain its handle using reflection or JNI.**"
> 列表语义：Blocklist = 「Non-SDK interfaces that you **cannot use regardless of your app's target API level**」；Conditionally blocked (`max-target-x`) = 按 target API 屏蔽；Unsupported = 「Non-SDK interfaces that are **unrestricted and your app can use**. Note however that these interfaces are **unsupported and subject to change without notice. Expect these interfaces to be conditionally blocked in future Android versions**」。
> 访问受限接口的后果表：`NoSuchFieldError` / `NoSuchMethodError` / `getDeclaredMethod` 抛 `NoSuchMethodException` / JNI `GetMethodID` 返回 NULL 等。官方结论句：「using any non-SDK method or field always carries a **high risk of breaking your app** … you should begin planning a migration to SDK interfaces or other alternatives」。

**本轮实证**（沿用仓内「下载并读取一手文件」方法，在内存中下载官方 Android 16 / API 36 `hiddenapi-flags.csv` 并 grep `AssetManager`）`[S8]`：

| 签名 | Android 16 分类 | 含义 |
|---|---|---|
| `Landroid/content/res/AssetManager;->addAssetPath(Ljava/lang/String;)I` | **`unsupported`** | 当前可用，但不受支持、随时可能变、预计未来被条件屏蔽 |
| `…->addAssetPathAsSharedLibrary(Ljava/lang/String;)I` | **`unsupported`** | 同上 |
| `…->addAssetPathInternal(Ljava/util/List;Z)I` | **`blocked`** | 无论 target API 都不可用 |
| `…->setApkAssets([Landroid/content/res/ApkAssets;Z)V` | **`lo-prio,max-target-o`** | 按 target API 条件屏蔽 |

**结论**：靠 `addAssetPath`（反射/隐藏 API）把外部资源挂进 `Resources` **不能作为稳定方案**——它不是 SDK、官方明写「迁移到 SDK 接口」，且 `addAssetPathInternal` 已被 blocklist。注意措辞要精确：不是「绝对做不到」，而是「**不在公开 API 内、不受支持、有版本风险**」；官方同时提供了下面的公开替代。

### 3.3 公开替代：`ResourcesProvider` / `ResourcesLoader` / `Resources.addLoaders`（API 30+）

`ResourcesProvider`（Added in API level 30）官方类文档 `[S10]`：

> "**Provides methods to load resources data from APKs (`.apk`) and resources tables (eg. `resources.arsc`) for use with `ResourcesLoader(s)`.**"

public static 方法逐个（均为 API 30，`loadOverlay` 为 API 34）`[S10]`：

| 方法 | 官方释义要点 |
|---|---|
| `loadFromApk(ParcelFileDescriptor)` | 「Creates a ResourcesProvider from an APK (`.apk`) file descriptor」；fd 会被 dup，原 fd 可随时关 |
| `loadFromApk(ParcelFileDescriptor, AssetsProvider)` | 「The assets provider can **override the loading of files within the APK and can provide entirely new files that do not exist in the APK**」 |
| `loadFromDirectory(String, AssetsProvider)` | 「File-based resources will be resolved within the directory **as if the directory is an APK**」 |
| `loadFromTable(ParcelFileDescriptor, AssetsProvider)` | 从 `.arsc` 资源表创建；「resources table format is not an archive format and therefore **cannot** asset files within itself」 |
| `loadFromSplit(Context, String)` | 读「a split installed alongside the application」 |
| `empty(AssetsProvider)` | 「useful for loading **file-based assets not associated with resource identifiers**」 |
| `loadOverlay(OverlayInfo)`（API 34） | 官方示例明确写出调用链：`ResourcesProvider` → `ResourcesLoader` → `Resources.addLoaders(ResourcesLoader...)` |

`ResourcesLoader`（API 30）官方类文档 `[S11]`：

> "A container for supplying `ResourcesProvider(s)` to `Resources` objects. `ResourcesLoader(s)` are added to Resources objects to **supply additional resources and assets or modify the values of existing resources and assets**. Multiple Resources objects can share the same ResourcesLoaders and ResourcesProviders. **Changes to the list of `ResourcesProvider(s)` a loader contains propagates to all Resources objects that use the loader.**"
> "Loaders must be added to Resources objects in **increasing precedence order**. A loader will **override** the resources and assets of loaders added before itself."
> "**Modifying the list of providers a loader contains or the list of loaders a Resources object contains can cause lock contention with the UI thread. APIs that modify the lists … should only be used on the UI thread.** Providers can be instantiated on any thread."

`Resources.addLoaders(ResourcesLoader...)` / `removeLoaders(...)` 均为 **Added in API level 30**，官方：「Adds a loader to the list of loaders … **This should only be called from the UI thread to avoid lock contention**」`[S12]`；`Resources` 公开构造器为 `Resources(AssetManager, DisplayMetrics, Configuration)`，公开方法含 `getIdentifier(String, String, String)`、`getDrawable`、`getFont` 等 `[S12]`。API diff 亦记录 API 30 由预发布版的 `addLoader/clearLoaders/setLoaders` 改为 `addLoaders/removeLoaders` `[S12]`。

**可行性结论（事实）**：**做得到**——下载一个 **APK 形状（含 `resources.arsc`）的资源包**到内部存储，用 `ParcelFileDescriptor.open(file, MODE_READ_ONLY)` 交给 `ResourcesProvider.loadFromApk`，放进 `ResourcesLoader`，再 `context.resources.addLoaders(loader)`；官方明确说这会「附加资源/资产，或**修改已有资源与资产的值**」。这是公开 SDK，不受 §3.2 的 non-SDK 限制。

**边界（逐条可追）**：
- **版本差异**：三个类/方法均为 **API 30** `[S10]` `[S11]` `[S12]`；本工程 **minSdk = 26**，故 API 26–29 **无公开等价路径**（要么 `Build.VERSION.SDK_INT >= 30` 分支，要么退回 §3.4 的文件解码路线）。`loadOverlay` 更是 API 34 `[S10]`。
- **不是「安装」**：`loadFromApk` 收的是 **ParcelFileDescriptor**，官方文档未要求该 APK 被安装或签名校验（本轮未逐字核到「不校验签名」的原文，见 §11）。
- **按名查找**：新资源在编译期不存在 → `R.*` 里没有，只能走 `getIdentifier(name, defType, defPackage)` `[S12]`；皮肤包内资源 ID/包名与宿主冲突时的具体行为**未验证**（§11）。
- **线程**：改 loader/provider 列表应在 UI 线程，否则与 UI 线程锁竞争 `[S11]` `[S12]`。
- **载荷形态**：`loadFromTable` 只吃 `.arsc`、不含文件；`empty(AssetsProvider)` 专用于「与资源 ID 无关的文件型资产」`[S10]`。

### 3.4 不挂 `Resources` 的「下载为文件 → 直接解码」路线（无 API 30 门槛）

若皮肤里的图标/字体不需要变成 `R.drawable`/`R.font`，官方公开 API 允许把它们当**普通文件**读 `[S13]` `[S14]` `[S15]`：

| 载荷 | 官方 API | 原文要点 |
|---|---|---|
| 位图 | `BitmapFactory.decodeFile(String[, Options])`、`decodeStream(InputStream, …)`、`decodeByteArray` `[S13]` | 「Decode a file path into a bitmap」，不可解码返回 null；`decodeStream` 接受任意 InputStream |
| 图片 → Drawable/Bitmap | `ImageDecoder.createSource(File)` → `decodeDrawable/decodeBitmap` `[S15]` | 「A class for converting encoded images (like PNG, JPEG, WEBP, GIF, or HEIF)」；`createSource(File)` 官方注明「should only be used for files that you have **direct access to**」 |
| 字体 | `Typeface.createFromFile(File)` / `createFromFile(String)`；`Typeface.CustomFallbackBuilder` + `Font.Builder("your_font_file.ttf")` `[S14]` | 「Create a new typeface from the specified font file」；可配 `setSystemFallback` |
| 色值 | `Color.parseColor(String)` → `androidx.ui.graphics.Color(int)` `[S16]` `[S17]` | 字符串即配置，无二进制 |

**事实约束**：这条路绕开了 `Resources`/隐藏 API，在 minSdk 26 全区间可用；但 Compose 侧把 `Bitmap`/`Typeface` 包成 `ImageBitmap`/`Font`/`TextStyle` 的确切 API 与版本要求本轮**未验证**（§11）。另外「下载物落在内部存储」的完整性/签名校验是**应用层责任**（§7）。

## 4. Q3 — Play Asset Delivery / Dynamic Feature Module 是否满足诉求

### 4.1 官方机制原文

Play Asset Delivery 首页 `[S18]`：

> "Play Asset Delivery (PAD) brings the benefits of app bundles to **games**. … **Using PAD, all asset packs are hosted and served on Google Play removing the need to use a content delivery network (CDN)** to get your game resources to players."
> "Play Asset Delivery uses asset packs, which are composed of assets …, **but no executable code**. Through Dynamic Delivery, you can customize how and when each asset pack is downloaded onto a device according to **three delivery modes: install-time, fast-follow, and on-demand**."
> "PAD offers **flexible delivery modes, auto-updates, compression, and delta patching** … Automatic updates: Let Play auto-update your game assets".

更细的原文（同页，经官方搜索结果取回）`[S19]`：

> "`install-time` asset packs are delivered when the app is installed. **These packs are served as split APKs (part of the APK set)** … These packs **can't be modified or deleted by the user**."
> "Asset packs configured as `fast-follow` and `on-demand` are **served as archive files by the Google Play Store (and not as split APKs)**. These packs are then expanded in the app's internal storage."
> **「Asset updates」**："When the app is updated, `install-time` asset packs are updated **as part of the base app update** (with no action needed from the developer). App updates for `fast-follow` and `on-demand` asset packs follow these steps: 1. The patch for the app, including all assets, is downloaded … 2. The app binary is updated … 3. All previously-downloaded asset packs are invalidated …"
> **「App version updates」**："**After a new version of an app is uploaded to Google Play**, it is possible for the user to open the previous version … the app can choose to force an update or recommend an update by calling the In-App Updates API."

构建期声明（官方「Integrate asset delivery」）`[S19]`：必须在工程里建 asset pack 目录、`com.android.asset-pack` 插件、`assetPack { packName; dynamicDelivery { deliveryType = … } }`，并在 app 的 `build.gradle` 写 `assetPacks = [":asset-pack-name", …]`、`settings.gradle` include，最后「Build the Android App Bundle with Gradle」，Gradle 为每个包生成 `manifest/AndroidManifest.xml`（标识 + delivery mode）。

Play Feature Delivery `[S20]`：
> "Google Play's app serving model uses **Android App Bundles** to generate and serve optimized APKs for each user's device configuration … Play Feature Delivery uses advanced capabilities of app bundles, allowing certain features of your app to be delivered conditionally or downloaded on demand."
> "On demand delivery: **Allows your app to request and download feature modules as needed** … use the Play Feature Delivery Library to request to download the module on demand."
> "**Because Play Feature Delivery requires you to publish your app using an app bundle**, make sure that you're aware of app bundle known issues."；"Only devices running **Android 5.0 (API level 21) and higher** support downloading and installing features on demand"；"Enable **SplitCompat**, so that your app has access to downloaded feature modules"。

### 4.2 是否「用户不更新软件」——事实判定

**不满足**。三条一手依据叠加：
1. 资产包/功能模块在**构建期**声明并打进 App Bundle `[S19]` `[S20]` —— B 端「上传一套新皮肤」若指新增一个包，需要重新构建并**上传新版本 bundle**；
2. 官方「Asset updates」把资产更新绑定在「app 更新 / 新版本上传到 Play」流程里 `[S19]`；
3. 全部资产包由 **Google Play 托管与下发** `[S18]`。

即 PAD/DFM 解的是「**首包体积 / 按需下载**」，不是「**脱离 APK 发布周期**」；用户拿到新皮肤的前提仍是商店侧有一次应用更新（或至少一次新版本发布）。`install-time` 包作为 split APK、`on-demand` 包作为 Play 下发的归档，二者都不是「App 自己从任意服务器下载一个文件」。

### 4.3 中国大陆无 Play 场景（事实，不推测）

- Google 官方支持论坛原文：「**Google Play is not currently available in China.**」（另有官方论坛帖「How to access Google services in Mainland China」指向 Play Console 帮助）`[S21]`。
- PAD 的机制前提是「all asset packs are **hosted and served on Google Play**」`[S18]`，PFD 的前提是「publish your app using an app bundle」+ Play Feature Delivery Library `[S20]`。
- 因此在中国大陆无 Play 的设备上，**PAD/DFM 的按需下发通道不可用**（事实）。
- 需区分：官方《Supported locations for developer and merchant registration》表中列有 `China ✔`（支持**开发者/商家注册**）`[S21]`，这与消费者侧 Play Store 是否可用是**不同范畴**，不能用来反驳上一条。「精确的官方分发国家/地区列表」页本轮 404，**未验证**（§11）。
- 补充事实：本仓按 [AGENTS.md](../../AGENTS.md) 走 **GitHub Release + 直接分发 APK**，本就不是 Play 分发链路。

## 5. Q4 — 远程 UI / DSL 或 WebView 在原生 Compose 工程里的位置（只作事实对比）

官方《Wrap a WebView in Compose》`[S22]`：

> "**To use a `WebView` in Jetpack Compose, you must wrap it in an `AndroidView`.**"
> "`WebView` deals with **complex state lifecycles that are separate from the Android View lifecycle and Compose lifecycle**. Integrating Compose can introduce complex `WebView` scenarios that result in difficult bugs."
> "**Nested scrolling is not easily supported** … placing a `WebView` inside a scrollable Compose container, such as a `LazyColumn` … **does not currently work properly**."
> 「Synchronize app theme with WebView content」：若拥有页面内容，用 `prefers-color-scheme` 媒体查询；原生控件用 DayNight 主题；否则可用 algorithmic darkening。
> 「Alternative to an embedded WebView」：Chrome Custom Tabs / 《Use web content within your Android app》。

Play 政策侧对 WebView 的特殊口径 `[S27]`：下载可执行代码的禁令「**does not apply to code that runs in a virtual machine or an interpreter where either provides indirect access to Android APIs (such as JavaScript in a webview or browser)**」；但「Apps or third-party code … with interpreted languages … loaded at run time … **must not allow potential violations of Google Play policies**」，且「webview with added **JavaScript Interface that loads untrusted web content** (for example, http:// URL) or **unverified URLs obtained from untrusted sources**」被列为常见违规。

**事实对比（不推荐）**：远程 UI/DSL 在 Compose 里不是「换一套资源」，而是「换一套渲染栈」——Compose UI 是**编译期 Kotlin 可组合函数**，官方没有给出「服务端下发 DSL → Compose 渲染」的 API；本轮检索**未见** Android 官方的 Compose Server-Driven UI 指南（命中的是 ProAndroidDev / Medium 等第三方实践，按「未见」处理）`[S23]`。WebView 路线是官方认可的「嵌一块 View」互操作，但要自付状态、返回键、嵌套滚动、主题同步与 JS 桥安全成本 `[S22]` `[S27]`。它不解决「导航壳、主题 token、M3 组件」的皮肤化，只解决「一块内容区由 Web 渲染」。

## 6. Q5 — 动态更换应用图标 / launcher icon 的官方限制

- `<activity-alias>` 官方 `[S24]`：属性含 `android:icon="drawable resource"`、`android:label`、`android:enabled`、`android:targetActivity`；原文「**An alias for an activity, named by the `targetActivity` attribute. The target must be in the same application as the alias and declared before the alias in the manifest.**」；「the intent filters on the alias might specify `ACTION_MAIN` and `CATEGORY_LAUNCHER`, **causing it to be represented in the application launcher**」。
- 运行时切换：`PackageManager` 公开常量 `COMPONENT_ENABLED_STATE_ENABLED` / `_DISABLED` / `_DEFAULT` / `_DISABLED_UNTIL_USED` / `_DISABLED_USER` 与 `DONT_KILL_APP` `[S25]`；通行做法是用 `setComponentEnabledSetting` 在**预置 alias** 之间切换（该 API 的方法级原文本轮未取全，见 §11）。
- **Android Q（API 29）起的限制**（`LauncherApps.getActivityList` 官方注释）`[S26]`：
  > "If an app doesn't have any activities that specify `ACTION_MAIN` or `CATEGORY_LAUNCHER`, **the system adds a synthesized activity to the list. This synthesized activity represents the app's details page within system settings.**"
  > "**As of Android Q, at least one of the app's activities or synthesized activities appears in the returned list unless** the app satisfies at least one of the following conditions: The app is a system app. / The app doesn't request any permissions. / The app doesn't have a launcher activity that is enabled by default."

**事实结论**：`android:icon` 是 **manifest 中的 drawable 资源引用**，alias 也必须**编译期写进 manifest** → **下载来的图片文件不能直接成为 launcher icon**；运行时只能在 APK 内预置的 alias/icon 集合里切换。因此「皮肤包换 launcher 图标」在现有机制下**做不到**（除非改 APK / 用快捷方式等非 launcher-icon 机制，后者本轮未验证）。另外 Q 起「隐藏/替换图标」还受合成 activity 规则约束 `[S26]`。README 也确认本仓 `res/` 目前只有 launcher 图标。

## 7. Q6 — 远程资源安全：动态代码、Play 政策、签名、预签名 URL 的定位

### 7.1 Play 政策原文（注意适用范围）

《Device and Network Abuse》Full Policy `[S27]`：

> "**An app distributed via Google Play may not modify, replace, or update itself using any method other than Google Play's update mechanism. Likewise, an app may not download executable code (such as dex, JAR, .so files) from a source other than Google Play.** This restriction **does not apply to code that runs in a virtual machine or an interpreter** where either provides indirect access to Android APIs (such as JavaScript in a webview or browser)."
> "Apps or third-party code, like SDKs, with **interpreted languages** (JavaScript, Python, Lua, etc.) loaded at run time (for example, not packaged with the app) **must not allow potential violations of Google Play policies**."
> 常见违规举例：「Apps or third party code … that **download executable code, such as dex files or native code, from outside Google Play**」、「containing a webview with added **JavaScript Interface that loads untrusted web content** … or **unverified URLs obtained from untrusted sources**」。

**事实边界**：
- **纯色值 JSON / 图标 / 字体不是 dex、JAR、.so**，不落入该条的「可执行代码」禁令 → 仓内已拍板「只做资源防盗链/预签名 URL」与这条不冲突（前提是不夹带代码、不自建解释器执行远程逻辑）。
- 该政策**只约束「distributed via Google Play」的应用**。本仓走 GitHub Release 直接分发 APK（[AGENTS.md](../../AGENTS.md)）时**不受该政策约束**；但 **Android 平台层**限制依旧生效：non-SDK 接口限制 `[S9]`、APK 签名 `[S28]`。这是适用范围事实，不是合规建议。
- 若皮肤走 WebView/JS 或远程 DSL，则进入上文的 VM/解释器豁免与「不得加载未验证 URL」条款 `[S27]`。

### 7.2 签名：平台校验什么、不校验什么

《Sign your app》原文 `[S28]`：

> "**Android requires that all APKs be digitally signed with a certificate before they are installed on a device or updated.**"
> "**As part of Android's secure update model, the signing key never changes during the lifetime of your app.** The app signing key is private and must be kept secret. You can, however, share the certificate…"

**事实推导**：平台的签名校验对象是**被安装/被更新的 APK**。`ResourcesProvider.loadFromApk` 收的是一个 **ParcelFileDescriptor**（§3.3）、`BitmapFactory.decodeFile` 收的是一个文件路径（§3.4）——**下载的资源包不是已安装 APK，平台不会替它做 APK 签名校验**。因此「确认取到的皮肤包确由 B 端发布、未被篡改」是**应用层完整性校验**责任；本轮**未见** Android 官方「校验外部下载资源包签名」的专门 API（§11）。可参照的一手锚点：`PackageManager` 有 `CERT_INPUT_RAW_X509` / `CERT_INPUT_SHA256` 常量 `[S25]`、`apksigner` 是 APK 签名/校验工具 `[S28]` —— 二者都面向 APK，不是通用资源包。

### 7.3 预签名 URL 的准确定位（S3 语义一手依据）

AWS S3 官方（MinIO 兼容的 S3 语义；MinIO 本轮对应文档页 404，见 §11）`[S29]` `[S30]`：

> "By default, all Amazon S3 objects are private … **A presigned URL uses security credentials to grant time-limited permission to download objects.** The URL can be entered in a browser or used by a program to download the object. **The credentials used by the presigned URL are those of the AWS user who generated the URL.**"
> "You can use presigned URLs to **grant time-limited access to objects … without updating your bucket policy**."
> "**In essence, presigned URLs are bearer tokens that grant access to those who possess them. As such, we recommend that you protect them appropriately.**"
> 有效期：控制台 1 分钟–12 小时；CLI/SDK 最长 **7 天**；且「A presigned URL expires at either its configured expiration time or **when its associated credentials expire**, whichever occurs first」。

**定位（事实）**：预签名 URL 是**传输/访问层的临时授权与防盗链**，解决「谁能在多长时间内取到这个对象」；它**不做内容完整性校验**、**不做用户身份认证/SSO**（bearer token，谁拿到谁能用）。这与仓内已拍板的「只做资源防盗链/预签名 URL（不需要 SSO）、MinIO 不提供身份认证」一致，也与 §7.2 的「签名校验是应用层责任」是两件事，不要混为一层。

## 8. Q7 / Q8 — 版本协商，以及新库在 compileSdk 36 / BOM 2025.12.00 / minSdk 26 下的可用版本

### 8.1 版本协商：官方依据与通行做法

**未见** Android 官方「皮肤包 schema 版本协商」规范或标准（§11）。能追到的一手锚点：

| 锚点 | 原文 / API | 对「旧 APK 不认识新 token/图标」的作用 |
|---|---|---|
| Remote Config **App version 条件规则** `[S2]` | 客户端模板条件规则含 "App version"、自定义信号、随机百分位 | 服务端可按客户端版本给不同参数值，旧 APK 只拿它能解析的那份 |
| Remote Config **in-app defaults + 模板版本化/回滚** `[S1]` `[S2]` | 「set in-app default values using `setDefaults()`」；「Every time you update and publish … Firebase creates and stores a **new version of your template**. You can view version history, compare changes … and **roll back**」；「Whenever you update your app, you should synchronize its default parameter values」 | 旧 APK 无网络/拿到新 schema 时回落到编译期内置默认值；服务端可回滚 |
| `kotlinx.serialization` `ignoreUnknownKeys` `[S31]` | 「Specifies whether encounters of unknown properties in the input JSON should be **ignored instead of throwing `SerializationException`**. **`false` by default**.」官方示例：`Json { ignoreUnknownKeys = true }` 解析 `{"name":…, "version":2.0}` 时 `"version"` 被忽略；默认 `Json` 会「Fails with 'Encountered an unknown key version'」 | 这是**库官方**给的前向兼容手段：旧 APK 忽略新 schema 里多出来的 key，而不是崩 |
| `Resources.getIdentifier` `[S12]` | 公开的按名资源查找（`getIdentifier(String, String, String)`） | 新图标不靠 `R.*`，由皮肤清单声明 key、客户端逐 key 查找；查不到的回退行为**未验证** |
| `ColorScheme` 固定 48 槽位 `[S3]` | `@Immutable class`，不可加字段 | 远程新增的语义 token 必须有**编译期已知**的落点（自建 `NongyuColors` 分组字段），否则旧 APK 只能忽略 |

**通行做法（非官方规范，标注清楚）**：包内带 `schemaVersion` / `minClientVersion`；客户端按 `schemaVersion` 决定是否加载、缺失 token 回落到内置默认、未知 key 忽略（`ignoreUnknownKeys = true`）、服务端按 app version 分发并对模板做版本化与回滚。以上每一条都有上面的官方/库官方锚点，但**「皮肤包 schema 版本协商」整体没有 Android 官方规范**。

### 8.2 新库版本与 AAR `minCompileSdk` 实证（本轮逐个下载并读取）

方法同 [compose-navigation-shell.md](compose-navigation-shell.md)：下载 AAR → 读 `META-INF/com/android/build/gradle/aar-metadata.properties`；本轮在内存中完成，不写入工程。先用一个**仓内已有依赖**做方法自检：`androidx.core:core-ktx:1.17.0` = `minCompileSdk=36` / `minAndroidGradlePluginVersion=8.9.1`（与本仓 `libs.versions.toml` 一致）。

| artifact | 版本 | 仓库 | `<release>` / 文件形态 | AAR `minCompileSdk` | 在 compileSdk 36 下 |
|---|---|---|---|---|---|
| `io.coil-kt.coil3:coil-compose-android`（含 `coil-android`/`coil-compose-core-android`/`coil-core-android` 传递 AAR） | **3.5.0** | Maven Central `[S32]` | 稳定版，AAR；manifest `minSdkVersion="23"` | **36**（`minAGP=1.0.0`） | ✅ 可用（本轮实测最后一个 ≤36 的稳定线） |
| 同上 | 3.6.0 / 3.6.1 / 3.6.2 / **3.6.3**（当前 `<release>`） | Maven Central `[S32]` | 稳定版 | **37** | ❌ 构建期被 AAR 元数据校验拒绝（本工程仅 android-36） |
| 同上 | 3.4.0 | Maven Central | 稳定版 | 35 | ✅ 可用 |
| 同上 | 3.0.0–3.3.0 | Maven Central | 稳定版 | 1（无实质约束） | ✅ 可用 |
| `com.google.android.play:asset-delivery` | **2.3.0** | Google Maven `[S34]` | `<release>`=2.3.0，`lastUpdated 20241217110041`；AAR **无 `aar-metadata.properties`** | 无约束 | 构建期不被卡，但受 §4 的 Play 依赖限制 |
| `com.google.android.play:feature-delivery` | **2.1.0** | Google Maven `[S34]` | `<release>`=2.1.0；AAR 无元数据文件 | 无约束 | 同上 |
| `com.google.android.play:core` | 1.10.3 | Google Maven `[S34]` | `<release>`=1.10.3；AAR 无元数据文件 | 无约束 | 同上（且为旧 monolithic 线，release notes 记 2.0.0 起拆分） |
| `io.ktor:ktor-client-android` | **3.6.1** | Maven Central `[S35]` | `<release>`=3.6.1；KMP umbrella，`.module` 的 jvm 变体 `available-at` → `ktor-client-android-jvm:3.6.1` **JAR**（实测 28 KB） | 无 AAR → 无 minCompileSdk | 构建期不被 AAR 元数据卡；⚠️ `.module` 要求 `kotlin-stdlib:2.3.21`、`kotlinx-coroutines-core:1.11.0`，`createdBy gradle 9.7.1` → 与 Kotlin 2.3.0 / Gradle 9.4.1 兼容性**未验证** |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.12.0** | Maven Central `[S36]` | `<release>`=1.12.0；KMP umbrella，jvm 变体 `kotlinx-serialization-json-jvm:1.12.0` **JAR**（实测 299 KB） | 无 AAR → 无 minCompileSdk | 同上；⚠️ `.module` 要求 `kotlin-stdlib:2.4.10`（> 本仓 2.3.0），且本仓**未应用** serialization 插件 → **未验证** |

补充事实：
- **coil 版本阶梯是本轮实测的完整曲线**：`1 → 1 → 1 → 1 → 35 → 36 → 37 → 37 → 37 → 37`（对应 3.0.0/3.1.0/3.2.0/3.3.0/3.4.0/3.5.0/3.6.0/3.6.1/3.6.2/3.6.3）`[S32]`。仓内 `README.md` 与 `libs.versions.toml` 已记录「`runtime-saveable 1.12.1` 要求 37 → BOM 锁 2025.12.00」的同类现象；coil 3.6.x 是**另一个独立的 37 门槛**。
- coil POM 侧：`coil-compose-android:3.6.3` 把 `kotlin-stdlib` 钉在 **2.4.10**，3.4.0 钉在 **2.3.10** `[S33]` → 即便解决 compileSdk，stdlib 与 Kotlin 2.3.0 的元数据兼容性仍需验证（§11）。
- 仓库解析能力：`settings.gradle.kts` 用 `google()` + `mavenCentral()` 且 `FAIL_ON_PROJECT_REPOS` `[S37]` → 上述 Google Maven / Maven Central 坐标均可解析，无需新增仓库。
- 本仓目前**没有任何网络/序列化/图片加载库**（`app/build.gradle.kts` 仅 core-ktx / lifecycle / activity-compose / BOM / junit）`[S37]`，故以上均为「若要引入」的前置核查，不代表要引入。

## 9. Q9 — 「两种解耦方法」：从第一性原理归纳两条相互独立的解耦轴线

> **只归纳可能性，明确写「由维护者拍板」，不替他选。**

维护者原话里的「两种」用词含糊。把「解耦」还原成第一性原理：**让某个东西的取值不再由「编译 + 打 APK + 发版」这个周期决定**。可变的量不止一个，且彼此正交；本轮从已核实的技术机制里归纳出**两条相互独立的轴线**。

### 9.1 轴线 A —— 载荷通道（**什么被解耦**：数据/色值通道 vs 二进制资源包通道）

| | A1 数据 / 色值通道 | A2 二进制资源包通道 |
|---|---|---|
| 载荷 | token JSON、色值字符串、皮肤清单 | 图标、字体、含 `resources.arsc` 的 APK 形资源包 |
| 官方机制 | `Color.parseColor` → `Color(int)` → `lightColorScheme(primary=…)` / 自建 `NongyuColors`；`CompositionLocalProvider` 换值 `[S16]` `[S17]` `[S3]` `[S4]` | ① 文件解码：`BitmapFactory.decodeFile` / `ImageDecoder.createSource(File)` / `Typeface.createFromFile` `[S13]` `[S14]` `[S15]`；② 挂 Resources：`ResourcesProvider.loadFromApk` → `ResourcesLoader` → `Resources.addLoaders`（**API 30+**）`[S10]` `[S11]` `[S12]` |
| 门槛 | 无 API 版本门槛；但 `ColorScheme` 固定 48 槽位、换值有重组代价 `[S3]` `[S5]` | 文件解码无门槛；挂 Resources 需 API 30（本仓 minSdk 26 → 26–29 无公开等价）`[S10]` `[S12]`；隐藏 API `addAssetPath` 不受支持 `[S8]` `[S9]` |
| 官方「不发版」口径 | Remote Config 明确把改 color theme 列为无需发版 `[S1]` | 官方 API 允许运行时加载外部资源内容，但**没有**「B 端上传 → 用户直接下载」的官方通道；PAD 反而不满足（§4） |

**技术依据**：GLOSSARY 把「主题 = 颜色 token 集合」「皮肤 = 主题 + 图标/字体等资源包（superset）」判为两层载荷 `[S38]`；ADR 0002 已把 A1 的绑定点定为「数据源接口 + 本期 mock、后期换数据库/皮肤实现，Composable 零改动」`[S39]`。两者机制互不依赖：只做 A1 就能换配色，只做 A2 就能换图标字体，**可以各自单独成立**。

### 9.2 轴线 B —— 绑定方式（**怎么被绑定**：资源本体下发 vs 偏好标识下发）

GLOSSARY 已明文：「**服务端存储用户选哪套皮肤，与客户端色板定义是两个不同的层：前者是偏好标识，后者是资源本体**」`[S38]`。把它推到底：

| | 资源本体**随 APK 内置** | 资源本体**由服务端下发** |
|---|---|---|
| **偏好标识随 APK 内置**（编译期枚举 / 默认 skinId） | 全内置，无解耦 | A1/A2 全量下发（维护者原话的最直观读法） |
| **偏好标识由服务端下发**（一个 skinId / 配置键） | **只下发一个标识即可换外观**：APK 内预置多套皮肤，服务端告诉客户端「用第 3 套」 → 同样实现「用户不更新软件」 | 两者都下发 |

**技术依据**：Remote Config 的参数就是字符串标识，且支持按 App version / 用户分群下发条件值、模板版本化与回滚 `[S1]` `[S2]`；ADR 0002 的「数据源接口不变、实现可换」正是「**绑定点固定、实现可替换**」这一解耦形态 `[S39]`。

### 9.3 两条轴线的独立性与「第三种候选」的判定

- A、B 正交：A 决定「搬的是数据还是二进制」，B 决定「搬的是本体还是只是一个 ID」；任意组合都成立（2×2 表见上）。**「两种解耦方法」可能指 A1 vs A2，也可能指「资源本体解耦 vs 偏好标识下发」，还可能指两条轴线各取一条** —— 用词无法唯一确定，**由维护者拍板**。
- 任务里给出的另一个候选「**视觉资产解耦 vs 渲染/启动成本解耦**」：本轮判定它**不是**一条独立解耦轴线。§2.3 的重组代价（`compositionLocalOf` / `staticCompositionLocalOf`）`[S5]`、§3.3 的 `addLoaders` UI 线程锁竞争 `[S11]`、离线首屏（ADR 0002 记「色板编译期内置 → 离线首屏天然成立」`[S39]`）都是**任何方案都要付的实现约束**，不构成「第二种解耦方法」。这是基于官方机制做的归纳判断，同样**不替维护者排除**他的读法。
- 与「皮肤/分隔」措辞的关系：仓内 GLOSSARY 无「分隔」这一术语；是否指「皮肤 vs 主题的分层（A 轴线）」或「皮肤 vs 偏好标识的分层（B 轴线）」，**未验证**，需维护者澄清。

## 10. 事实 → 决策约束表

| # | 事实（来源） | 如何约束「视觉资产脱离 APK 发布周期」的选项 |
|---|---|---|
| F1 | Remote Config 官方明文「改 color theme / layout 无需发 app update」；参数值是字符串、app 控制 activate 时机 `[S1]` `[S2]` | 数据通道是官方支持路径；但载荷只能是编译期能解析的字符串，且「何时生效」由客户端决定（离线首屏需内置默认） |
| F2 | 参数/条件/模板有硬上限（3000 参数、2000 条件、key 256、总值 1,000,000 字符、300 版本）；政策禁止存机密、禁止绕过平台要求、禁止做需用户授权的更新 `[S1]` `[S2]` | 大体积皮肤清单/整包图标不适合塞进配置参数；也不能借远程配置改变需授权的行为 |
| F3 | `ColorScheme` 是 `@Immutable class`、固定 48 槽位、非 data class；`lightColorScheme(primary=…)` 是官方投影 `[S3]` `[S4]` | 远程新增语义 token 必须有编译期已知的落点（自建 `NongyuColors`）；旧 APK 不认识新 token 是必然问题，需 §8.1 的协商 |
| F4 | 官方 ExtendedTheme = `CompositionLocalProvider` 包 `MaterialTheme`，「supports multiple themes」；扩展值须手动塞进 `ButtonDefaults.buttonColors` 等 `[S4]` | 「MaterialTheme + 自有 Local」是合法落点；自定义色不会自动跟色，换肤后每个用到的 M3 组件都要有包装 |
| F5 | `compositionLocalOf` 换值只失效读者；`staticCompositionLocalOf` 换值整块 content 重组；官方要求好默认值 `[S5]` | 换肤频率/范围决定用哪个 API；无好默认值时 @Preview/测试必须显式 Provide。这是成本，不是第二条轴线（§9.3） |
| F6 | `AssetManager` 公开 API 无 `addAssetPath`；AOSP 带 `@hide`；Android 16 实测 `addAssetPath=unsupported`、`addAssetPathInternal=blocked`、`setApkAssets=max-target-o`；Android 9+ 反射/JNI 同样受限 `[S6]` `[S7]` `[S8]` `[S9]` | 隐藏 API 路线**不能**作为稳定支柱；官方明确建议迁移到 SDK 接口 |
| F7 | `ResourcesProvider.loadFromApk/…` + `ResourcesLoader` + `Resources.addLoaders` 是 **API 30** 公开 API，可「附加或修改已有资源与资产」；改列表应在 UI 线程 `[S10]` `[S11]` `[S12]` | 「外部资源挂进 Resources」**做得到**，但 minSdk 26 需版本分支、26–29 无公开等价；新资源按名查；有锁竞争与缓存失效问题 |
| F8 | `BitmapFactory.decodeFile` / `ImageDecoder.createSource(File)` / `Typeface.createFromFile` 直接吃文件路径/流，无 API 30 门槛 `[S13]` `[S14]` `[S15]` | 图标/字体可绕开 Resources 机制、全 minSdk 区间可用；但 Compose 侧包装 API 未验证，且完整性校验是应用层责任 |
| F9 | PAD 资产包构建期声明、由 Play 托管；资产更新随 app update / 新版本上传；PFD 要求 App Bundle + Play Feature Delivery Library `[S18]` `[S19]` `[S20]` | **不满足「用户不更新软件」**；解的是首包体积/按需下载，不是脱离发版周期 |
| F10 | Google 官方支持论坛：「Google Play is not currently available in China.」；PAD 前提是由 Play 托管下发 `[S21]` `[S18]` | 中国大陆无 Play 场景下 PAD/DFM 不可用（事实）；本仓本就走 GitHub Release 分发 `[S37]` |
| F11 | WebView 必须包 `AndroidView`；嵌套滚动/状态/主题同步有额外成本；官方无 Compose SDUI 指南（未见）；JS in WebView 属 VM 豁免但禁未验证 URL `[S22]` `[S27]` `[S23]` | 远程 UI 是「换渲染栈」而非「换资源」；不能替代导航壳/主题 token 的皮肤化 |
| F12 | `android:icon` 是 manifest 中的 drawable 资源；alias 必须编译期声明；Android Q 起至少有合成 activity `[S24]` `[S25]` `[S26]` | **下载文件不能直接换 launcher icon**；只能在 APK 预置集合里切换 → 皮肤包换 launcher 图标做不到 |
| F13 | Play 政策禁从非 Play 来源下载 dex/JAR/.so，豁免 VM/解释器代码；纯资源不是可执行代码；政策仅约束 Play 分发应用 `[S27]` | 纯资源包不触犯该条；本仓非 Play 分发时该政策不适用，但平台限制（F6、F14）仍适用 |
| F14 | APK 安装/更新必须签名且签名密钥生命周期不变；下载的资源包不是已安装 APK `[S28]` | 平台不代验皮肤包完整性 → 签名/哈希校验是应用层责任；别把「APK 签名」与「资源包校验」混为一层 |
| F15 | 预签名 URL = 时间受限的 bearer token，解决「谁能取」；最长 7 天（CLI/SDK）/12h（控制台），凭证失效即失效 `[S29]` `[S30]` | 与仓内「只做防盗链/预签名 URL、不需要 SSO」一致；它**不**做内容完整性、也**不**做身份认证 → 需与 F14 的校验分层 |
| F16 | 版本协商无 Android 官方规范（未见）；锚点 = Remote Config 的 App version 条件 + in-app defaults + 模板版本化/回滚、`ignoreUnknownKeys`（默认 false）、`getIdentifier` `[S1]` `[S2]` `[S31]` `[S12]` | 「旧 APK 不认识新 token/图标」必须显式设计（schemaVersion / 回落内置默认 / 忽略未知 key）；不能假定官方有现成协商协议 |
| F17 | coil 3.5.0 = minCompileSdk 36（最后一个 ≤36），3.6.0–3.6.3 = 37；Play 三个库 AAR 无元数据；ktor/serialization 是 JAR 变体但 stdlib 要求高于 Kotlin 2.3.0 `[S32]` `[S33]` `[S34]` `[S35]` `[S36]` | 新库版本被 compileSdk 36 卡在 coil 3.5.0；JAR 型库不被 AAR 元数据卡，但 Kotlin/Gradle 版本兼容性未验证；引入前须实测 |
| F18 | 两条独立解耦轴线 A（载荷通道）/ B（绑定方式）可由 GLOSSARY 分层 + 两套官方加载机制推出 `[S38]` `[S39]` `[S10]` `[S13]` | 「两种」到底指哪两条、以及「分隔」的所指，**由维护者拍板**；本笔记只给可能性与技术依据 |

## 11. 未验证清单 / 不适用

### 11.1 未验证

1. **中国大陆无 Play 的「官方分发国家/地区列表」精确页面**：`support.google.com/googleplay/answer/6131783` 本轮 404；只拿到 Google 官方支持论坛「Google Play is not currently available in China.」`[S21]` 与「开发者/商家注册支持地区含 China」这一**不同范畴**的页面。开发者注册可用 ≠ 消费者 Play Store 可用。
2. **Android 10 预览版 release note 关于 launcher icon visibility 的确切原文**：本轮只取得 `LauncherApps.getActivityList` 的现行 API 参考 `[S26]` 与第三方对该预览说明的转述；预览版原文本身未取证。
3. **`PackageManager.setComponentEnabledSetting` 的方法级官方全文**：只核实了常量表 `[S25]` 与 `LauncherApps` 注释 `[S26]`；在 targetSdk 36 设备上切换 alias 的精确系统行为（是否重启 launcher、快捷方式是否失效）未实测。
4. **MinIO 官方 presigned URL 文档**：`min.io/docs/minio/linux/developers/minio-drivers/reference/Kotlin.html` 404（且该 URL 疑似 Kotlin 驱动页而非 presigned URL 页）；本轮以 AWS S3 官方文档 `[S29]` `[S30]` 作 **S3 语义**的一手依据，MinIO 具体 API 名/行为**未验证**。
5. **Compose 侧把下载物包装成 `ImageBitmap` / `Font` / `TextStyle` 的确切 API 与 BOM 2025.12.00 下的版本要求**：只实证了平台侧 `BitmapFactory` / `Typeface` / `ImageDecoder` `[S13]` `[S14]` `[S15]`。
6. **实际构建兼容性**：coil 3.5.0（stdlib 2.3.10）、ktor 3.6.1（stdlib 2.3.21、coroutines 1.11.0、Gradle metadata createdBy 9.7.1）、kotlinx-serialization 1.12.0（stdlib 2.4.10）与 Kotlin 2.3.0 / Gradle 9.4.1 / AGP 9.2.1 / BOM 2025.12.00 的编译结果；按要求**不构建、不改代码**，未跑 `assembleDebug`，也未跑 dependency tree。
7. **官方是否存在「语义 token 表 → ColorScheme 自动映射 API」与「皮肤包 schema 版本协商规范」**：检索未见，按「未见」处理，未做全量 API 索引或规范检索。
8. **官方 Compose Server-Driven UI 指南**：命中的均为第三方（ProAndroidDev / Medium），按「未见」处理 `[S23]`。
9. **`Resources.getIdentifier` 对未知名的精确返回行为**，以及 `ResourcesLoader` 换 provider 后**已缓存 resource ID / 已解析 Drawable** 的失效语义：只读了 API 参考，未读 AOSP 实现、未实测 `[S12]`。
10. **`ResourcesProvider.loadFromApk` 对未签名/未安装 APK 的校验要求**、资源包 `resources.arsc` 的 package id 与宿主冲突时的行为：官方 API 文档未展开，未实测 `[S10]`。
11. **API 30 / 31 / 34 / 36 各版本上 `loadFromApk` + `addLoaders` 的行为差异矩阵**：只读了统一 API 参考（API 30 引入、API 34 加 `loadOverlay`），未做设备矩阵实测。
12. **PAD 是否硬性仅支持游戏**：官方页多处写 "games"、案例均为游戏 `[S18]`，但**未找到**「非游戏应用禁止使用」的明文；按「文案面向 games、是否硬限未验证」处理。
13. **`asset-delivery-ktx` / `feature-delivery-ktx` 等 KTX 变体坐标与 AAR 元数据**：本轮只查了三个主 artifact `[S34]`。
14. **Firebase / Google Play services 在中国大陆的连通性**：Remote Config 本轮只作官方口径引用，不验证其在本场景可落地。
15. **`coil-compose:3.5.0` 的 KMP 根 `.module` 在纯 Android 消费者下最终解析到哪个变体**：只实证了 `-android` AAR 与 POM `[S32]` `[S33]`。
16. **「分隔」在维护者语境中的确切所指**：仓内 GLOSSARY / ADR 无该术语；§9 给的两种读法均为归纳，需维护者澄清。

### 11.2 不适用（事实判定，非建议）

- **Play Asset Delivery / Dynamic Feature Module**：不适用于本诉求（依赖 Play 商店；大陆无 Play；资产包构建期声明且随 app update 更新，不满足「用户不更新软件」）`[S18]` `[S19]` `[S20]` `[S21]`。
- **`AssetManager.addAssetPath` 等隐藏 API**：不适合作稳定方案（non-SDK；Android 16 标 `unsupported` / 相关方法 `blocked`；官方要求迁移到 SDK 接口）`[S6]` `[S7]` `[S8]` `[S9]`。
- **动态加载 DEX / JAR / .so**：不适用（Play 政策禁止从非 Play 来源下载 `[S27]`；且诉求是视觉资产而非代码；Android 9+ non-SDK 限制与 APK 签名模型同样收紧 `[S9]` `[S28]`）。
- **Firebase Remote Config 作为落地依赖**：本仓已拍板「色值走数据库下发」、存储层 MinIO；Remote Config 在本笔记中**仅作官方口径参照**。是否引入由维护者拍板，大陆连通性未验证（§11.1-14）。
- **SSO / 身份认证**：仓内已拍板「只做资源防盗链/预签名 URL、不需要 SSO」；预签名 URL 是 bearer token，不做身份认证 `[S29]` `[S30]`。
- **Go 后端压测证据、后端语言选型**：仓内已拍板待带入，不属本次「一手事实调研」范围。
- **运行时验证 / 构建 / 改代码 / git 操作**：按任务纪律一律不做（本轮只新增本笔记一个文件）。

## 12. 参考资料（primary source）

> 说明：本轮 `developer.android.com` 主站直连取不到，官方 API/指南全文经 `read_page`（Firecrawl 云端渲染）与 `developer.android.google.cn` 镜像读取；下列链接仍给官方 `.com` / 官方域名地址，与 [compose-design-system-tokens.md](compose-design-system-tokens.md) 的处理一致。AAR 与 `hiddenapi-flags.csv` 为直接下载并读取原始字节。

**官方指南 / API 参考（Android）**
- `[S1]` [Firebase Remote Config（总览：无需发版改外观、How does it work、Policies and limits、Implementation path）](https://firebase.google.com/docs/remote-config)
- `[S2]` [Remote Config parameters and conditions（参数/条件/配额/模板版本化、App version 条件规则）](https://firebase.google.com/docs/remote-config/parameters)
- `[S3]` androidx 源码 [ColorScheme.kt（androidx-main）](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ColorScheme.kt) · [ColorScheme API 参考](https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme)
- `[S4]` [Custom design systems in Compose（ExtendedTheme / CompositionLocalProvider / 多主题 / 手动传色）](https://developer.android.com/develop/ui/compose/designsystems/custom) · [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) · [Anatomy of a theme in Compose](https://developer.android.com/develop/ui/compose/designsystems/anatomy)
- `[S5]` [Locally scoped data with CompositionLocal（两种 API 的重组语义、好默认值告诫）](https://developer.android.com/develop/ui/compose/compositionlocal)
- `[S6]` [AssetManager API 参考（public methods 全文）](https://developer.android.com/reference/android/content/res/AssetManager)
- `[S7]` AOSP 源码 [core/java/android/content/res/AssetManager.java（`@hide` addAssetPath）](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/content/res/AssetManager.java) · [Android 4.4.2_r2 同文件（`{@hide}` 版）](https://android.googlesource.com/platform/frameworks/base/+/android-4.4.2_r2.0.1/core/java/android/content/res/AssetManager.java)
- `[S8]` [Android 16（API 36）官方 `hiddenapi-flags.csv`](https://dl.google.com/developers/android/baklava/non-sdk/hiddenapi-flags.csv)（本轮下载并 grep `AssetManager`，结果见 §3.2）
- `[S9]` [Restrictions on non-SDK interfaces（Android 9 起、反射/JNI、各列表语义、访问后果表）](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces) · [Android 10 non-SDK 更新](https://developer.android.com/about/versions/10/non-sdk-q)
- `[S10]` [ResourcesProvider API 参考（loadFromApk / loadFromDirectory / loadFromTable / loadFromSplit / empty / loadOverlay）](https://developer.android.com/reference/android/content/res/loader/ResourcesProvider)
- `[S11]` [ResourcesLoader API 参考（附加或覆盖资源、优先级、UI 线程锁竞争）](https://developer.android.com/reference/android/content/res/loader/ResourcesLoader)
- `[S12]` [Resources API 参考（addLoaders/removeLoaders = API 30、getIdentifier、公开构造器）](https://developer.android.com/reference/android/content/res/Resources) · [API 29→30 diff（Resources 方法改名）](https://developer.android.com/sdk/api_diff/30/changes)
- `[S13]` [BitmapFactory API 参考（decodeFile / decodeStream / decodeByteArray）](https://developer.android.com/reference/android/graphics/BitmapFactory)
- `[S14]` [Typeface API 参考（createFromFile / CustomFallbackBuilder）](https://developer.android.com/reference/android/graphics/Typeface)
- `[S15]` [ImageDecoder API 参考（createSource(File) / 支持格式）](https://developer.android.com/reference/android/graphics/ImageDecoder)
- `[S16]` [android.graphics.Color API 参考（parseColor 支持格式）](https://developer.android.com/reference/android/graphics/Color)
- `[S17]` [androidx.ui.graphics.Color API 参考（Color(int) from 32-bit SRGB）](https://developer.android.com/reference/kotlin/androidx/ui/graphics/Color)
- `[S18]` [Play Asset Delivery 总览（Play 托管、无可执行代码、三种模式、自动更新）](https://developer.android.com/guide/playcore/asset-delivery)
- `[S19]` [Play Asset Delivery — Asset updates / App version updates 章节](https://developer.android.com/guide/playcore/asset-delivery) · [Integrate asset delivery (Kotlin/Java)](https://developer.android.com/guide/playcore/asset-delivery/integrate-java) · [Integrate asset delivery (Native)](https://developer.android.com/guide/playcore/asset-delivery/integrate-native) · [PAD release notes（2.3.0 稳定）](https://developer.android.com/reference/com/google/android/play/core/release-notes-asset_delivery) · [Test asset delivery](https://developer.android.com/guide/playcore/asset-delivery/test)
- `[S20]` [Overview of Play Feature Delivery（App Bundle、on-demand、SplitCompat、API 21+）](https://developer.android.com/guide/playcore/feature-delivery)
- `[S21]` Google 官方支持论坛：[「Google Play is not currently available in China.」](https://support.google.com/googleplay/thread/245057995/how-to-change-my-google-play-country-to-mainland-china?hl=en) · [How to access Google services in Mainland China](https://support.google.com/googleplay/android-developer/thread/288247561?hl=en) · [Supported locations for developer and merchant registration（含 China，属注册范畴）](https://support.google.com/googleplay/android-developer/answer/9306917?hl=en)
- `[S22]` [Wrap a WebView in Compose（AndroidView、状态、嵌套滚动、主题同步、替代方案）](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose)
- `[S23]` 官方 Compose Server-Driven UI 指南：**检索未见**（命中的 [ProAndroidDev](https://proandroiddev.com/exploring-android-compose-with-sdui-a-practical-guide-with-examples-3732ac3c1c1d) 等为第三方，仅作「未见官方指南」的旁证，不作事实来源）
- `[S24]` [\<activity-alias\>（icon 为 drawable resource、须声明在 targetActivity 之前、MAIN/LAUNCHER）](https://developer.android.com/guide/topics/manifest/activity-alias-element)
- `[S25]` [PackageManager API 参考（COMPONENT_ENABLED_STATE_* / DONT_KILL_APP / CERT_INPUT_* 常量）](https://developer.android.com/reference/android/content/pm/PackageManager)
- `[S26]` [LauncherApps API 参考（getActivityList：Android Q 起合成 activity 规则）](https://developer.android.com/reference/android/content/pm/LauncherApps)
- `[S27]` [Play Console Help — Device and Network Abuse（禁止自更新/下载可执行代码、VM 解释器豁免、WebView 常见违规）](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en)
- `[S28]` [Sign your app（APK 必须签名、签名密钥生命周期不变、apksigner、Play App Signing）](https://developer.android.com/studio/publish/app-signing)
- `[S29]` [AWS S3 — Sharing objects with presigned URLs（时间受限、凭证归属、12h/7d）](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html)
- `[S30]` [AWS S3 — Download and upload objects with presigned URLs（bearer token、有效期与凭证失效取先到者）](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)
- `[S31]` [kotlinx.serialization `JsonBuilder.ignoreUnknownKeys`（默认 false、忽略未知键示例）](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-builder/ignore-unknown-keys.html)

**Maven 元数据 / POM / AAR（本轮逐个下载并读取）**
- `[S32]` [coil-compose maven-metadata.xml](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose/maven-metadata.xml) · [coil-compose-android-3.5.0.aar](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-android/3.5.0/coil-compose-android-3.5.0.aar) · [3.6.3.aar](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-android/3.6.3/coil-compose-android-3.6.3.aar)（`META-INF/com/android/build/gradle/aar-metadata.properties`，版本阶梯见 §8.2）
- `[S33]` [coil-compose-android-3.5.0.pom](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-android/3.5.0/coil-compose-android-3.5.0.pom) · [3.4.0.pom](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-android/3.4.0/coil-compose-android-3.4.0.pom) · [3.6.3.pom](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-android/3.6.3/coil-compose-android-3.6.3.pom)
- `[S34]` Google Maven：[asset-delivery maven-metadata.xml](https://dl.google.com/dl/android/maven2/com/google/android/play/asset-delivery/maven-metadata.xml) · [feature-delivery](https://dl.google.com/dl/android/maven2/com/google/android/play/feature-delivery/maven-metadata.xml) · [core](https://dl.google.com/dl/android/maven2/com/google/android/play/core/maven-metadata.xml) · [asset-delivery-2.3.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/play/asset-delivery/2.3.0/asset-delivery-2.3.0.aar) · [feature-delivery-2.1.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/play/feature-delivery/2.1.0/feature-delivery-2.1.0.aar) · [core-1.10.3.aar](https://dl.google.com/dl/android/maven2/com/google/android/play/core/1.10.3/core-1.10.3.aar) · [方法自检用 core-ktx-1.17.0.aar](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.17.0/core-ktx-1.17.0.aar)
- `[S35]` [ktor-client-android maven-metadata.xml](https://repo1.maven.org/maven2/io/ktor/ktor-client-android/maven-metadata.xml) · [ktor-client-android-3.6.1.module](https://repo1.maven.org/maven2/io/ktor/ktor-client-android/3.6.1/ktor-client-android-3.6.1.module) · [ktor-client-android-jvm-3.6.1.jar](https://repo1.maven.org/maven2/io/ktor/ktor-client-android-jvm/3.6.1/ktor-client-android-jvm-3.6.1.jar)
- `[S36]` [kotlinx-serialization-json maven-metadata.xml](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) · [1.12.0.module](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/1.12.0/kotlinx-serialization-json-1.12.0.module) · [kotlinx-serialization-json-jvm-1.12.0.jar](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.12.0/kotlinx-serialization-json-jvm-1.12.0.jar)
- `[S37]` 工程内文件（只读）：[gradle/libs.versions.toml](../../gradle/libs.versions.toml) · [app/build.gradle.kts](../../app/build.gradle.kts) · [settings.gradle.kts](../../settings.gradle.kts) · [README.md](../../README.md) · [AGENTS.md](../../AGENTS.md)
- `[S38]` [GLOSSARY.md（主题/皮肤/色板/Token 定义与「偏好标识 vs 资源本体」边界说明）](../../GLOSSARY.md)
- `[S39]` [ADR 0002 — 主题 token 层用自建 CompositionLocal 承载](<../adr/0002-主题token层用自建CompositionLocal承载.md>) · [compose-design-system-tokens.md](compose-design-system-tokens.md) · [compose-navigation-shell.md（AAR 实证方法）](compose-navigation-shell.md)

---

> 核查日期 2026-11-08。本文件为 `/research` 一手事实调研产物：**只新增本文件，未修改任何其它文件、未执行任何 git 操作、未改代码、未构建**。所有选型与「两种解耦方法」的最终解释 **由维护者拍板**。
