# 降 AGP 到 8.12 的影响（核准版）

> 只做事实核查，未改任何业务代码、未执行 git、未安装或降级任何东西。
> 核实时间：2026-11-06。核实对象：本仓 `nongyu-app-plus`（AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00 / compileSdk 36 / minSdk 26 / JDK 21）。
>
> **本文是对 #9 里上一轮结论的修订。** 上一轮把「Gradle 9.4.1 超出 KGP 2.3.0 的 Gradle 上限 9.0.0」写成了硬阻断，经复核**该措辞错误**，见 §1.1。

---

## 1. 结论摘要

1. **降 AGP 不是改一行版本号**，要动 4 个构建文件 + 降 Gradle + 把 `org.jetbrains.kotlin.android` 插件加回来，并且**还得额外装两个 IDEA 插件**才有 Run 按钮。
2. **唯一真正的硬阻断是 Kotlin 2.3.0 的 AGP 上限 = 8.13.0**：降到 8.12 尚在窗口内，但**不能降到 8.11 或更低**（KGP 2.3.0 的 AGP 下限是 8.2.2，上限 8.13.0）。
3. **上一轮说的「Gradle 9.4.1 超出 KGP 2.3.0 上限」不是硬阻断**，是「不完全支持、可能有弃用警告」；官方文档明说可以继续用最新版。而且本工程当前就在这个组合下成功构建过 APK。
4. **真正劝退的不是构建，是依赖生态**：本工程缓存里的 AAR 元数据最高要求 `minAndroidGradlePluginVersion=8.9.1` + `minCompileSdk=36`（activity 1.11.0 / core 1.17.0）。AGP 8.12 勉强达标（8.12 ≥ 8.9.1、最高支持 API 36），但**已经贴着上限**，以后任何依赖升级都可能要求 AGP 9.1+ 而把工程卡死。
5. **降 AGP 解决不了「在 IDEA 里看」**：它只让「装了插件之后能读懂」，插件本机根本没装；且「装插件后 sync 真能过」没有一手证据。
6. **三个已有决策形成夹击**：#2（IDE 读不懂 AGP 9）、#3（MeshGradient 要 compileSdk 37）、#8（蓝叠 Tiramisu64 验 AGSL）。降 AGP 会让第 3 条彻底无解，而第 8 条不受影响。
7. **建议：不降**。改用「纯命令行 + 蓝叠 Tiramisu64 + IDEA Remote JVM Debug」，或装 Android Studio Quail 1。

---

## 2. 版本窗口（三张表一起读）

| 组件 | 官方要求 | 来源 |
| --- | --- | --- |
| AGP 9.2 | Gradle **≥9.4.1**、JDK 17、Build Tools 36.0.0、最高 API 37.0、KGP ≥2.2.10 | [about-agp](https://developer.android.com/build/releases/about-agp)、[AGP 9.2.0 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes) |
| AGP 8.12 | Gradle **≥8.13**、JDK 17、Build Tools 35.0.0、**最高 API 36** | [AGP 8.12.0 release notes](https://developer.android.com/build/releases/agp-8-12-0-release-notes) |
| AGP 8.13（8.x 最终版） | Gradle ≥8.13、最高 API 36.1 | [about-agp](https://developer.android.com/build/releases/about-agp) |
| **KGP 2.3.0** | Gradle **7.6.3–9.0.0（完全支持）**、**AGP 8.2.2–8.13.0** | [Kotlin Gradle 兼容矩阵](https://kotlinlang.org/docs/gradle-configure-project.html) |
| 当前工程 | Gradle 9.4.1、AGP 9.2.1、KGP 2.3.0、compileSdk 36 | — |

### 2.1 纠正：9.0.0 是「完全支持」上限，不是硬阻断

Kotlin 官方原文：

> In the following table, there are the minimum and maximum **fully supported** versions of Gradle and Android Gradle plugin (AGP)… **You can also use Gradle and AGP versions up to the latest releases**, but if you do, keep in mind that you might encounter deprecation warnings or some new features might not work.
> — [Configure a Gradle project](https://kotlinlang.org/docs/gradle-configure-project.html)

也就是说：

- 表里的 9.0.0 / 8.13.0 是「**fully supported**」边界，越过它**不是编译失败**，而是弃用警告 / 部分新特性不可用。
- **本工程当前就是活证据**：Gradle 9.4.1（超 9.0.0）+ AGP 9.2.1（超 8.13.0）+ KGP 2.3.0，在 2026-11-06 已成功产出 `app/build/outputs/apk/debug/app-debug.apk`。
- 但注意方向性：AGP 9 的内置 Kotlin 与 `kotlin-android` 不一样——AGP 9.2 内置 KGP 是 **2.2.10**（[9.2 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)），而 `kotlin-compose` 插件用的是 2.3.0。这套「混搭」能构建，但不是官方标称的完全支持组合。

### 2.2 真正的硬约束：AGP 上限 8.13.0

KGP 2.3.0 对 **AGP** 的窗口是 8.2.2–8.13.0。这条约束比 Gradle 那条更硬，因为退回 `kotlin-android` 插件模式后，KGP 会直接与 AGP 交互。所以：

- 目标只能是 **AGP 8.12 或 8.13**（不能更低）。
- 但 **IDE 侧只认到 8.12**：AS↔AGP 表里 API 36.1 需要 Narwhal 3 Feature Drop / AGP 8.13.0，而本机 IDEA 2025.3.2 可用插件的基座是 Narwhal Feature Drop（2025.1.2）→ AGP ≤8.12。
- **结论：目标被锁死在 AGP 8.12**，没有选择余地。

---

## 3. 依赖生态：AAR 元数据的一手证据

从本工程上次 AGP 9.4.1 构建留下的 Gradle 缓存
（`%TEMP%\nongyu-gradle-home\caches\9.4.1\transforms\*\transformed\*\META-INF\com\android\build\gradle\aar-metadata.properties`）
直接读出当前依赖声明的门槛：

| 依赖 | `minAndroidGradlePluginVersion` | `minCompileSdk` |
| --- | --- | --- |
| `androidx.activity:activity{-compose,-ktx}:1.11.0` | **8.9.1** | **36** |
| `androidx.core:core{-ktx}:1.17.0` | **8.9.1** | **36** |
| Compose BOM 2025.12.00 的 `ui` / `ui-graphics` / `material3` / `foundation` / `runtime-saveable` / `ui-tooling*` / `animation*` 等 | 8.6.0 | 35 |
| `lifecycle-*:2.10.0` / `savedstate-*` / `runtime` / `ui-unit` 等 | 8.1.1 | 34 |
| 其余（`annotation-experimental` / `emoji2` / `graphics-path` / `tracing` 等） | 1.0.0 | 31–34 |

来源：[`AarMetadata` API 参考](https://developer.android.google.cn/reference/tools/gradle-api/9.2/com/android/build/api/variant/AarMetadata)（`minAgpVersion` / `minCompileSdk` 字段语义）。

**判定**：

- AGP 8.12 ≥ 8.9.1 ✅，compileSdk 36 ≥ 36 ✅ → **当前这套依赖在 8.12 上不会因 AAR 元数据被拒**。
- 但 AGP 8.12 的**最高 API 就是 36**，而依赖已经要求 36 —— **贴着天花板，零余量**。
- 社区已有实例：升级到较新 AndroidX 时报 `Dependency 'androidx.core:core-ktx:1.19.0' requires Android Gradle plugin 9.1.0 or higher`（[例子](https://github.com/Moritz-Staat/block-instagram-reels/issues/51)）。也就是说**停在 AGP 8.12 等于把依赖生态锁死在 2025 年底这一批版本**，以后想升 Compose / core / activity 都会撞墙。

> 顺带更正 README 里那句锁定理由：`compose-bom:2026.09.00` 的 `runtime-saveable` 要求 **compileSdk 37**（不是 AGP 版本）。AGP 8.12 最高只支持 API 36，所以**降级后连「以后装 android-37 再放开 BOM」这条路也没了**——这与 #3 结论里「MeshGradient 要求 compileSdk 37」是同一个天花板。

---

## 4. 具体改动清单（如果真要降）

AGP 9 的 built-in Kotlin 在 8.x **不存在**，`org.jetbrains.kotlin.android` 必须加回来（[官方迁移文档](https://developer.android.com/build/migrate-to-built-in-kotlin) 的反向操作）。

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `gradle/libs.versions.toml` | `agp = "9.2.1"` → `"8.12.2"`（8.12 有 .1/.2 补丁版，[release notes](https://developer.android.com/build/releases/agp-8-12-0-release-notes) 列了 8.12.2）；**新增** `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }` |
| 2 | `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` → `gradle-8.14-bin.zip`（或 8.13）。**注意**：KGP 2.3.0 的 Gradle 完全支持上限 9.0.0，8.14 在窗口内且是 8.x 末版 |
| 3 | `build.gradle.kts`（根） | 新增 `alias(libs.plugins.kotlin.android) apply false` |
| 4 | `app/build.gradle.kts` | `plugins {}` 新增 `alias(libs.plugins.kotlin.android)` |
| 5 | `app/build.gradle.kts` | **可能需要** `kotlinOptions { jvmTarget = "17" }`：现在 `compileOptions` 只设了 Java 17，Kotlin 侧 jvmTarget 在 built-in Kotlin 下由 AGP 对齐，退回插件模式后可能报不一致（未实测） |
| 6 | `app/build.gradle.kts` | `isMinifyEnabled = false` 不动；`buildFeatures { compose = true }` 不动 |
| 7 | `README.md` | 技术栈表与「Compose BOM 版本锁定」一节全部要重写 |

**还要额外做的（不属于构建文件）**：

8. 装 IDEA 插件 `org.jetbrains.android`（ID 22989）+ `com.android.tools.design`（22990）。
9. 验证 sync 真的通过 —— **无任何一手证据**，这是整条路最大的不确定点。

---

## 5. 会失去什么（按对本工程的实际影响排序）

| 能力 | 本工程是否在用 | 降级后的影响 |
| --- | --- | --- |
| built-in Kotlin（不必 apply `kotlin-android`） | ✅ 正在用 | 退回插件模式，多一个插件、多一处版本要对齐；AGP 10 起这条路会消失，等于**主动往回走** |
| 新 DSL / 隐藏旧类型 | ✅ 隐含在用（`android {}` 块） | 退回旧 DSL 类型；本身对骨架工程无感，但**这正是 IDE 能 sync 的原因** |
| compileSdk 37 / Android 16 支持 | ❌ 当前用 36 | **失去未来空间**：8.12 最高 API 36，而 Compose BOM 2026.09 与 MeshGradient 都要 37（见 #3） |
| `deviceProvider` / `testServer`（AGP 9.0 移除） | ❌ 未用 | 无影响 |
| NDK r28c 默认、AarMetadata minCompileSdk 强制 | ❌ 未用 | 无影响 |
| AndroidX 依赖的可升级范围 | ✅ 隐含 | 见 §3：锁死在 2025 年底这批版本 |

---

## 6. 与另两条路的成本对比

| 路线 | 一次性成本 | 是否解决「在 IDEA/电脑上看界面」 | 长期负担 |
| --- | --- | --- | --- |
| **A 降 AGP 到 8.12** | 改 4–7 个文件 + 降 Gradle + 装 2 个插件 + **sync 结果未验证** | 部分（要三步全过才有 Run/Preview） | IDEA 2026.3 发布后要再改一遍升回 AGP 9；依赖升级撞 8.9.1/API36 天花板 |
| **B 装 Android Studio Quail 1（2026.1.1）** | 2–3 GB 安装 | ✅ 完整（Run / 断点 / Preview / Layout Inspector / Logcat） | 无，官方唯一完整支持 AGP 9.2.1 的组合 |
| **C 纯命令行 + 蓝叠 Tiramisu64 + Remote JVM Debug** | 写一个 `run.ps1`（蓝叠 ADB 已开，见 #8） | ✅ 能看到界面、能打断点；`@Preview` 仍不可用 | 无；不受 IDE/AGP 版本漂移影响 |

**三条路的共同前提**：无论选哪条，`@Preview` 在本机当前都不可用（渲染器随 Android 插件走，插件没装）。

---

## 7. 与已有决策的相互影响

- **#2**：降 AGP 就是为了绕开这条结论；但降完仍要装插件，绕不干净。
- **#3**：MeshGradient 因为 `minCompileSdk=37` 已判定硬阻断。降 AGP 8.12 后**连「以后装 android-37 再解锁」的可能性都没了**（8.12 最高 API 36）。如果未来想要 MeshGradient，等于要先把 AGP 升回来 —— 双重返工。
- **#8**：蓝叠 Tiramisu64（API 33）验证 AGSL 不依赖 AGP 版本，降级对它无影响。R1/R2 原型照常可做。
- **#5 / #6**：导航壳与 AI 圆原型都跑在 APK 上，AGP 8.12 能装到蓝叠，功能上不受影响；但每次改动要重新验证构建。

---

## 8. 结论与建议

**不建议降 AGP。** 理由按权重：

1. 它把一个「IDE 装不上」的问题，换成了「三方版本窗口被压窄 + 依赖生态贴天花板 + 未来要改两遍」。
2. 最核心的收益（「装插件后 IDEA 能 sync」）**没有任何一手证据**，是整条路最大的赌注。
3. 它与 #3 的「未来解锁 MeshGradient」直接冲突。
4. 而 **C 路线今天就能跑**：蓝叠 ADB 已开、命令行链路已备好、Remote JVM Debug 能打断点，成本只是一个 `run.ps1`。

如果维护者坚持「必须在 IDEA 里点 Run」，那 **B（装 Android Studio Quail 1）是唯一确定性方案**，比 A 更省事且不留技术债。

---

## 9. 未验证清单

1. AGP 8.12.2 + Gradle 8.14 + KGP 2.3.0 + Compose BOM 2025.12.00 在本工程**实跑**能否构建通过（本文全是静态推导 + 缓存元数据，没真降过）。
2. `kotlinOptions { jvmTarget = "17" }` 是否真的需要。
3. 装了 `org.jetbrains.android`（22989）+ Design Tools（22990）后，IDEA 2025.3.2 能否 sync 一个 AGP 8.12 工程 —— **整条 A 路线的生死线**。
4. AGP 8.12 用 SDK Build Tools 35.0.0 时，本机只装了 34/35/36，35.0.0 在；但 `compileSdk 36` 与 Build Tools 35 的组合是否会有警告或失败。
5. 降级后 `app-debug.apk` 产物与当前是否一致（R8 / 资源压缩 / manifest 合并行为在不同 AGP 下可能不同）。
6. 是否真会触发 `minAndroidGradlePluginVersion` 检查失败 —— 元数据读出来最高是 8.9.1，8.12 应满足，但未实跑。
7. IDEA 2026.3 及其 Android 插件 263.7307.200 的发布状态与实测可用性。

---

## 10. 参考资料

- [About Android Gradle plugin（AGP↔Gradle、AS↔AGP/API 兼容表）](https://developer.android.com/build/releases/about-agp)
- [AGP 8.12.0 release notes（Gradle 8.13 / Build Tools 35.0.0 / 最高 API 36）](https://developer.android.com/build/releases/agp-8-12-0-release-notes)
- [AGP 9.2.0 release notes（KGP 2.2.10 / Gradle 9.4.1 / 最高 API 37）](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [AGP 9.0 / 新 DSL 说明](https://developer.android.com/build/r/new-dsl)
- [Migrate to built-in Kotlin（反向操作即降级所需）](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Kotlin: Configure a Gradle project（KGP↔Gradle/AGP 兼容矩阵）](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Gradle Java 兼容矩阵](https://docs.gradle.org/current/userguide/compatibility.html)
- [AarMetadata API 参考（minAgpVersion / minCompileSdk 语义）](https://developer.android.google.cn/reference/tools/gradle-api/9.2/com/android/build/api/variant/AarMetadata)
- [AGP 9.2.1 的 POM（本机 Gradle 缓存，一手）](file:///C:/Users/LENOVO/AppData/Local/Temp/nongyu-gradle-home/caches/modules-2/files-2.1/com.android.tools.build/gradle/9.2.1/)
- [本仓既有调研：IDEA 调试链路](idea-android-debugging.md)
- [本仓既有调研：AI 圆按钮技术路线](compose-ai-orb-button.md)
- [本仓既有调研：蓝叠 ADB 与 Compose](bluestacks-adb-compose.md)
