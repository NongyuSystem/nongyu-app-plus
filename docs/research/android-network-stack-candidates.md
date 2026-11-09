# Android 网络层选型：候选事实核查（nongyu-app-plus）

> 核查日期 2026-11-09。只列事实与其对选项的约束，**不给最终选型结论**。
> 所有版本号 / Maven 坐标 / `minCompileSdk` / 依赖树 / 许可均来自下方「§7 引用」的一手来源（Maven 元数据、AAR 解包、官方 README/CHANGELOG）。二手资料一律显式标注。
> **纪律说明**：本次未改动任何构建文件、未新增依赖、未跑 `gradlew`。所有「构建能否通过」的判断都标为 §5「待实测」；§3 的 `minCompileSdk` 是**实测值**（解包 AAR 得到），不是推断。

## 0 工程约束（背景，已确认，本文不重新验证）

| 项 | 值 |
| --- | --- |
| AGP | 9.2.1 |
| Gradle | 9.4.1 |
| Kotlin | 2.3.0 |
| JDK（编译） | 17 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Compose BOM | **锁定 2025.12.00**（本机只有 `platforms/android-36`） |
| 现有依赖 | core-ktx 1.17.0、lifecycle-runtime-ktx 2.10.0、activity-compose 1.11.0、Compose BOM 相关、junit 4.13.2 |
| release 构建 | `isMinifyEnabled=true` + `isShrinkResources=true` |
| 网络库 | **无** |
| `AndroidManifest.xml` | **连 `android.permission.INTERNET` 都没声明**（已读 `app/src/main/AndroidManifest.xml` 确认） |

Compose BOM 2025.12.00 的映射（实测读 POM）：`ui` / `runtime` / `runtime-saveable` / `animation` / `foundation` = **1.10.0**，`material3` = **1.4.0**。

## 1 结论速览

**一句话**：本工程唯一会真的「拖到 compileSdk 37」的东西是 **`okhttp-android` 这个 AAR**——从 **5.5.0** 起它把 `minCompileSdk` 抬到 **37**，`5.4.0` 是 **36**（恰好等于本工程，零余量），`5.3.2` 及更早是 **1**（无实质约束）。Retrofit、Moshi、Ktor、kotlinx.serialization 全部是 **jar**，没有 AAR，因此**根本没有 `minCompileSdk` 门槛**。

| 候选 | 版本 / 坐标 | `minCompileSdk` 门槛 | 本机（compileSdk 36）可用？ | 卡在哪 |
| --- | --- | --- | --- | --- |
| **A. Retrofit 3.0.0 + OkHttp 5.5.1** | `com.squareup.retrofit2:retrofit:3.0.0` + `com.squareup.okhttp3:okhttp:5.5.1` | okhttp 自身是 jar，但在 **Android 上会解析成 `okhttp-android` AAR = 37** | **不可用** | OkHttp 5.5.x 的 android 变体 `minCompileSdk=37` |
| **A'. Retrofit 3.0.0 + OkHttp 4.12.1** | `retrofit:3.0.0` + `okhttp:4.12.1` | 无（纯 jar，4.x 是单 jar，Android 类在同 jar 内） | **元数据层面可用** | 用旧 OkHttp 线；构建/运行待实测 |
| **A''. Retrofit 3.0.0 + OkHttp 5.4.0** | `retrofit:3.0.0` + `okhttp:5.4.0` | 36（**零余量**） | **元数据层面可用** | 只要 compileSdk 掉到 35 立刻崩；且必须显式压版本 |
| **A1. JSON：kotlinx.serialization** | `converter-kotlinx-serialization:3.0.0` + `kotlinx-serialization-json:1.10.0` | 无（jar） | **可用** | 需加 `org.jetbrains.kotlin.plugin.serialization`（2.3.0 已发布） |
| **A2. JSON：Moshi** | `converter-moshi:3.0.0` + `moshi:1.15.2` | 无（jar） | **可用，但有反射代价** | `moshi-kotlin` 拖 `kotlin-reflect:1.8.21`；codegen 需 KSP |
| **B1. Ktor + OkHttp 引擎** | `io.ktor:ktor-client-okhttp:3.6.1` | **间接** = okhttp-android 5.5.1 = **37** | **不可用** | Ktor 3.6.1 把 okhttp 钉在 5.5.1 → 同样撞 37 |
| **B2. Ktor + CIO 引擎** | `io.ktor:ktor-client-cio:3.6.1` | 无（全是 jar） | **元数据层面可用** | 官方要求 Android 7.0+（本工程 26 满足）；构建/运行待实测 |
| **C. 只用 OkHttp** | 同 A / A' / A'' | 取决于 OkHttp 版本 | 5.5.x **不可用**；5.4.0 可用但零余量；4.12.1 可用 | 5.5.x 的 37；且**不带 JSON 解析** |
| **D. 零第三方（HttpURLConnection + org.json）** | 无坐标 | **无** | **可用** | 无任何元数据门槛；代价是手写全部网络代码 |

补充三条横切事实：

1. **Ktor 全家桶没有任何 AAR**。实测 `ktor-client-core`、`ktor-client-okhttp`、`ktor-client-cio`、`ktor-client-content-negotiation`、`ktor-serialization-kotlinx-json`、`ktor-http`、`ktor-utils`、`ktor-io`、`ktor-network`、`ktor-client-android` 的 `3.6.1` 版本：`.aar` 全部 404，只有 `.jar`。所以 Ktor 自己**永远**不会是 compileSdk 37 的原因；它只能通过**传递依赖 OkHttp** 把 37 带进来。
2. **Retrofit 3.0.0 的 POM 把 OkHttp 钉在 4.12.0**（不是 5.x）。所以「用 OkHttp 5」必须**显式声明** okhttp 5.x 才能盖住它；否则你拿到的是 4.12.0 那条无门槛的线。
3. **没有任何候选会抬升 Compose / lifecycle**。逐条读过 POM：Retrofit / OkHttp / Moshi / Ktor / kotlinx.serialization 的依赖里**没有** `androidx.compose.*`，也没有 `androidx.lifecycle.*`。唯一被带进来的 androidx 是 OkHttp 5 的 android 变体：`androidx.startup:startup-runtime:1.2.0`（`minCompileSdk=34`）与 `androidx.annotation:annotation:1.10.0`（jar，无元数据）。→ **不存在「拖到 compileSdk 37」以外的 Compose BOM 冲突**。

## 2 逐候选章节

### A. Retrofit + OkHttp

#### A.1 版本与坐标（已实测）

| artifact | 最新稳定版 | 来源 |
| --- | --- | --- |
| `com.squareup.retrofit2:retrofit` | **3.0.0** | Maven Central `maven-metadata.xml`：`<release>3.0.0</release>`，`lastUpdated 20250515163423` |
| `com.squareup.retrofit2:retrofit-bom` | **3.0.0**（BOM 存在） | `retrofit-bom/maven-metadata.xml` 的 `<release>3.0.0` |
| `com.squareup.retrofit2:converter-kotlinx-serialization` | **3.0.0** | 同仓库 metadata |
| `com.squareup.retrofit2:converter-moshi` | **3.0.0** | 同上 |
| `com.squareup.okhttp3:okhttp` | **5.5.1** | `lastUpdated 20261022124600` |
| `com.squareup.okhttp3:okhttp-bom` | **5.5.1**（BOM 存在，34 个条目，含 `okhttp` / `okhttp-jvm` / `okhttp-android` / `logging-interceptor`） | 读 `okhttp-bom-5.5.1.pom` 确认 |
| `com.squareup.okhttp3:logging-interceptor` | 5.5.1 | metadata |
| `com.squareup.moshi:moshi` / `moshi-kotlin` / `moshi-kotlin-codegen` | 都是 **1.15.2**（`lastUpdated 20241205`） | metadata |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.12.0**（但见 §2.4） | metadata，`lastUpdated 20261019182949` |

**注意版本号语义**：`retrofit` 的稳定线已经**不是 2.x**——2.12.0（2025-05-15）之后直接是 3.0.0，`<release>` 就是 3.0.0。所谓「Retrofit 2」在本机仓库里已无更新的稳定版可选。Retrofit 3.0.0 的 CHANGELOG 明写：「The 3.x versions of Retrofit maintain forward binary-compatibility with the 2.x versions.」

#### A.2 `minCompileSdk` / `minAGP` 实测（关键）

| AAR | minCompileSdk | minAGP | 来源 URL |
| --- | --- | --- | --- |
| `okhttp-android:5.5.1` | **37** | 1.0.0 | `https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.1/okhttp-android-5.5.1.aar` |
| `okhttp-android:5.5.0` | **37** | 1.0.0 | `.../okhttp-android/5.5.0/okhttp-android-5.5.0.aar` |
| `okhttp-android:5.4.0` | **36** | 1.0.0 | `.../okhttp-android/5.4.0/okhttp-android-5.4.0.aar` |
| `okhttp-android:5.3.2` | **1**（无实质约束） | 1.0.0 | `.../okhttp-android/5.3.2/okhttp-android-5.3.2.aar` |
| `okhttp-android:5.2.0` / `5.1.0` / `5.0.0` | 1 | 1.0.0 | 同规律 |
| `androidx.startup:startup-runtime:1.2.0` | 34 | 1.0.0 | `https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/1.2.0/startup-runtime-1.2.0.aar` |
| `retrofit:3.0.0` | **无 aar**（只有 jar，131218 B） | — | `.../retrofit/3.0.0/retrofit-3.0.0.jar` |
| `converter-kotlinx-serialization:3.0.0` / `converter-moshi:3.0.0` | **无 aar**（jar） | — | `.../converter-*/3.0.0/*.jar` |
| `moshi:1.15.2` / `moshi-kotlin:1.15.2` | **无 aar**（jar） | — | `.../moshi/1.15.2/moshi-1.15.2.jar` |
| `androidx.annotation:1.10.0` | **无 aar**（jar，20979 B） | — | `dl.google.com/.../androidx/annotation/annotation/1.10.0/annotation-1.10.0.jar` |

**为什么「okhttp 是 jar 却还有个 AAR」**——这是本次最容易踩空的一点，一手证据：

- OkHttp 5 是 KMP 工程。`com.squareup.okhttp3:okhttp:5.5.1` 的 **Gradle module 元数据**里，`androidApiElements-published` / `androidRuntimeElements-published` 两个变体带属性 `org.gradle.jvm.environment: android`、`org.gradle.libraryelements: aar`，并通过 `available-at` **指向 `okhttp-android:5.5.1`**；`jvmApiElements-published` 才指向 `okhttp-jvm`。
- OkHttp README 原文：「OkHttp is published as a Kotlin Multiplatform project. While Gradle handles this automatically, Maven projects must select between `okhttp-jvm` and `okhttp-android`. The `okhttp` artifact will be empty in Maven projects.」（实测印证：`okhttp-5.5.1.jar` 只有 **754 字节**）
- 所以：**Android 应用（AGP）加 `implementation("com.squareup.okhttp3:okhttp:5.5.1")`，Gradle 实际拿到的是 `okhttp-android-5.5.1.aar`**，AGP 就会校验它的 `minCompileSdk=37`。这和 `compose-navigation-shell.md` 里 navigation-compose 的坑是同一类，但路径更隐蔽：`okhttp` 这个名字看起来是「纯 JVM 库」。
- `okhttp-jvm:5.5.1` 的 jar 里**完全没有** `okhttp3/internal/platform/Android*.class`（实测：Android 相关 class 列表为空，只有 Conscrypt/BouncyCastle/Jdk9/OpenJSSE 等 JVM 平台）；而 `okhttp 4.12.0` 的单 jar 里**有** `AndroidPlatform.class` / `Android10Platform.class` / `okhttp3/internal/platform/android/*`。→ 4.x 是「一个 jar 通吃 JVM+Android」，5.x 把 Android 部分拆进 AAR。这解释了为什么 5.x 才有 AAR 元数据门槛。
- 推论（**待实测**）：若在 Android 工程里强行把依赖写成 `okhttp-jvm`，虽然躲开 AAR，但**会缺少 Android 平台实现**（`PlatformRegistry.findPlatform` 找不到 Android 分支），运行期行为不是官方支持路径。**不要把这当成绕过手段。**

#### A.3 传递依赖风险（会不会抬 kotlin-stdlib / coroutines / lifecycle / compose）

实测各 POM：

| 库 | 直接依赖 |
| --- | --- |
| `retrofit:3.0.0` | `okhttp:4.12.0`（compile）、`kotlin-stdlib:2.1.21` |
| `converter-kotlinx-serialization:3.0.0` | `retrofit:3.0.0`、`kotlinx-serialization-core-jvm:1.8.1`、`kotlin-stdlib:2.1.21` |
| `converter-moshi:3.0.0` | `retrofit:3.0.0`、`moshi:1.15.2` |
| `moshi:1.15.2` | `okio-jvm:3.7.0`、`kotlin-stdlib-jdk8:1.8.21` |
| `moshi-kotlin:1.15.2` | `moshi:1.15.2`、**`kotlin-reflect:1.8.21`**、`kotlin-stdlib-jdk8:1.8.21` |
| `okhttp-android:5.5.1`（androidRuntime 变体） | `kotlin-stdlib:2.1.21`、`kotlin-stdlib:2.2.21`（同一变体内列了两次，取高者）、`okio:3.18.1`、`androidx.annotation:1.10.0`、`androidx.startup:startup-runtime:1.2.0` |
| `okhttp-android:5.4.0` | 同上，但 `okio:3.17.0` |

结论：

- **kotlin-stdlib**：这些库最高只要到 **2.2.21**（okhttp-android），Retrofit/Moshi 更低（2.1.21 / 1.8.21）。工程本身用 Kotlin **2.3.0**，stdlib 版本号**高于**它们全部 → Gradle 取最高 = 2.3.0，**不会被动抬升**，也不会与 Kotlin 2.3.0 打架。
- **kotlinx-coroutines**：A 组**一个都不依赖 coroutines**。不会动 Compose runtime 1.10.0 现有的 `coroutines-android:1.9.0`。（对比：Ktor 会，见 B.3）
- **androidx.lifecycle / androidx.compose**：**完全没有**。`okhttp-android` 只带 `androidx.startup` + `androidx.annotation`，都低于工程现有 androidx 版本（lifecycle 2.10.0 / core-ktx 1.17.0），不构成抬升。
- **唯一真实的抬升**：`moshi-kotlin` 会把 **`kotlin-reflect:1.8.21`** 拉进来。这是 Kotlin 1.8 时代的反射库配 Kotlin 2.3.0 编译器的组合，**兼容性未验证**（§5）。而且 kotlin-reflect 体积大、R8 后仍需大量 keep（见 A.6）。→ 若要 Moshi，优先用 **codegen**（`moshi-kotlin-codegen`）而不是 `moshi-kotlin` 反射路径，但 codegen 需要 KSP，见 §4。

#### A.4 与 Kotlin 2.3.0 的兼容性

- **Retrofit / OkHttp / Moshi**：都是「用老 Kotlin 编译的 jar，被 2.3.0 消费者读」。实测它们的 `@kotlin.Metadata.mv`：retrofit 3.0.0 = **2.1**、okhttp-jvm 5.5.1 = **2.1.0**、okio-jvm 3.18.1 = 2.1.0、moshi 1.15.2 = 无 Kotlin 元数据（Java 写的）。全部**低于** 2.3.0 → 无前向兼容问题。
- **kotlinx.serialization 需要编译器插件**：需要 `org.jetbrains.kotlin.plugin.serialization`。**该插件随 Kotlin 2.3.0 发布，已确认存在**：`org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.3.0` 在 **Maven Central** 与 **Gradle Plugin Portal** 两个仓库都返回 200（`kotlin-serialization:2.3.0`、`kotlin-serialization-compiler-plugin-embeddable:2.3.0` 也都在）。官方 README：「New versions of the serialization plugin are released in tandem with each new Kotlin compiler version.」
  - **但**：本工程是 **AGP 9 内置 Kotlin**（`libs.versions.toml` 注释：「AGP 9 起内置 Kotlin 支持，无需再应用 `org.jetbrains.kotlin.android`，仅需 Compose 编译器插件」）。在「内置 Kotlin」下如何声明 kotlin 编译器插件（serialization），**本次未验证**——`developer.android.com` 从本机不可达（§0 记录），属 §5 待实测项。这是选 A1 路线时**第一个要落地的动作**。
- **kotlinx.serialization 版本选择（重要）**：运行时库版本**不能随便取最新**。实测各版本 `@kotlin.Metadata.mv` 与 POM 里声明的 kotlin-stdlib：

  | 版本 | mv | POM 声明的 kotlin-stdlib | CHANGELOG「based on Kotlin」 |
  | --- | --- | --- | --- |
  | 1.9.0 | 2.0 | 2.2.0 | 2.1.0 |
  | **1.10.0** | **2.3.0** | **2.3.0** | **2.3.0**（2026-01-21） |
  | 1.11.0 | 2.3.0 | 2.3.20 | 2.3.20（2026-04-10） |
  | 1.12.0 | **2.4.0** | **2.4.10** | 2.4.10（2026-10-19） |

  → **与 Kotlin 2.3.0 完全同源的是 1.10.0**（mv 2.3.0，stdlib 2.3.0）。1.11.0 的 mv 也是 2.3.0（只把 stdlib 提到 2.3.20），**也能被 2.3.0 编译器读**。1.12.0 的 mv 是 2.4.0，**实测仍能被 2.3.0 编译器接受**（见 §4 的实测），但它同时要求 stdlib 2.4.10 → 会把 stdlib 抬到 2.4.10，属于「静默升级 stdlib」。
  - 而 `converter-kotlinx-serialization:3.0.0` 自己钉的是 **`kotlinx-serialization-core-jvm:1.8.1`**，所以 converter 会往下拉 1.8.1，与你显式声明的 json 版本之间由 Gradle 取高者。**显式声明 `kotlinx-serialization-json:1.10.0`（或 1.11.0）即可**。

#### A.5 minSdk 26 是否满足

- Retrofit README 原文：「Retrofit requires at minimum Java 8+ or Android API 21+.」→ 21 ≤ 26 **满足**。
- OkHttp README 原文：「OkHttp works on Android 5.0+ (API level 21+) and Java 8+.」；`okhttp-android` 的 `AndroidManifest.xml` 实测 `<uses-sdk android:minSdkVersion="21" />` → 21 ≤ 26 **满足**。
- Moshi：实测 class 文件 major = 52/60（Java 8 / Java 16），Java 16 的类都在 `META-INF/versions/16/` 下（`RecordJsonAdapter`），Android 会忽略不可用的多版本条目 → **满足**（Moshi 满足 API 21+ 是常识，但本次**未从官方 README 直接取证**，标为待核实）。
- **附带发现（有用）**：`okhttp-android` 的 `AndroidManifest.xml` 自带 `<uses-permission android:name="android.permission.INTERNET" />` 和一个 `androidx.startup.InitializationProvider`（`okhttp3.internal.platform.PlatformInitializer`）。也就是说**引入 OkHttp 5 会自动合并进 INTERNET 权限**。官方 README 也写：「On Android, OkHttp uses AndroidX Startup. If you disable the initializer in the manifest, then apps are responsible for calling `OkHttp.initialize(applicationContext)` in `Application.onCreate`.」→ 但**不要依赖库来给你 INTERNET 权限**，自己声明才是可控做法（当前 manifest 缺这条，任何候选都要补）。

#### A.6 R8 / ProGuard 官方要求（只写官方给的）

release 已开 `isMinifyEnabled=true` + `isShrinkResources=true`。各库**自带的规则**（实测 jar 内条目）：

| 库 | 自带规则 | R8 是否自动应用 |
| --- | --- | --- |
| `retrofit:3.0.0` | `META-INF/proguard/retrofit2.pro`（实测存在，内容含 `-keepattributes Signature, InnerClasses, EnclosingMethod`、`-if interface * { @retrofit2.http.* <methods>; } -keep,allowobfuscation interface <1>`、`-keep,allowobfuscation,allowshrinking class retrofit2.Response` 等） | **是**。Retrofit README 原文：「If you are using R8 the shrinking and obfuscation rules are included automatically. ProGuard users must manually add the options from retrofit2.pro.」 |
| `okhttp-android:5.5.1`（AAR） | `proguard.txt`（实测存在，含 `-dontwarn javax.annotation.**`、`-dontwarn org.codehaus.mojo.animal_sniffer.*`、`-dontwarn okhttp3.internal.platform.**`、`-dontwarn org.conscrypt.**`、`-dontwarn org.bouncycastle.**`） | **是**（AAR 的 consumer rules）。 |
| `okhttp:4.12.x`（jar） | `META-INF/proguard/okhttp3.pro`（实测存在） | 是（jar 内 `META-INF/proguard` 由 R8 读取） |
| `moshi:1.15.2` | `META-INF/proguard/moshi.pro`（实测存在，含 `-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }`、`-keep @com.squareup.moshi.JsonQualifier @interface *`、keep enum 的 `values()` 等） | **是**。但 Moshi README 原文：「Moshi contains minimally required rules for its own internals to work without need for consumers to embed their own. **However if you are using reflective serialization and R8 or ProGuard, you must add keep rules** ... for your reflectively serialized classes.」→ **用 `moshi-kotlin` 反射路径就必须自己写 keep**；用 codegen 不需要。官方另给一条：「Annotate enums with `@JsonClass(generateAdapter = false)` to prevent them from being removed/obfuscated by R8/ProGuard.」 |
| `kotlinx-serialization-core:1.10.0` / `1.12.0` | `META-INF/proguard/kotlinx-serialization-common.pro` + `META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro`（实测存在） | **是**。官方 README 原文：「By default, proguard rules are supplied with the library. These rules keep serializers for _all_ serializable classes that are retained after shrinking, so you don't need additional setup. **However, these rules do not affect serializable classes if they have named companion objects.**」→ 有具名 companion object 的 `@Serializable` 类要按官方给的模板另写规则（README 里分别给了 ProGuard/R8 compatibility mode 与 R8 full mode 两套）。 |

→ **A 组不需要你手写任何 keep**，除非：用了 `moshi-kotlin` 反射（要写）、或序列化类有具名 companion object（要写）、或 `@Serializable` 的 enum（Moshi 那条只针对 Moshi）。

#### A.7 许可

- Retrofit 3.0.0 POM `<license>`：**The Apache Software License, Version 2.0**（实测）。
- `converter-kotlinx-serialization` / `converter-moshi`：同为 Apache-2.0（实测 POM）。
- OkHttp 5.5.1 POM：**Apache-2.0**（实测）。GitHub API 显示仓库 `license.spdx_id = Apache-2.0`。
- Moshi 1.15.2 POM：**Apache-2.0**（实测）。
- `kotlinx-serialization-json` POM：**Apache-2.0**（实测）。
- `androidx.startup` / `androidx.annotation`：Apache-2.0。

#### A.8 一条需要记下的供应链观察（不是安全结论，只是事实）

- OkHttp **5.5.0 / 5.5.1** 的 POM 里 `<url>` 变成 `https://lysine.dev/okhttp/`、`<scm>` 变成 `https://github.com/lysine-dev/okhttp`；而 **5.4.0 及更早**仍是 `https://square.github.io/okhttp/` + `github.com/square/okhttp`（逐版本读过 POM 确认）。
- `https://api.github.com/repos/square/okhttp` 现在**重定向**到 `repositories/5152285`，解析结果为 `lysine-dev/okhttp`（`gh api` 与 urllib 都观察到）；`https://square.github.io/okhttp/` 现在 **404**。
- OkHttp CHANGELOG 5.5.0 条目的原文解释了这个变化：「OkHttp artifacts are now signed with our [new signing key]. This project and three sibling projects (Retrofit, Okio, and SQLDelight) recently joined [the Commonhaus Foundation].」
- → 结论：这是**项目治理迁移**（进入 Commonhaus Foundation），不是可疑的仿冒。但 5.5.0/5.5.1 的 **artifact 签名密钥变了**，如果将来配依赖校验（`dependencyVerification`），需要同步更新。Retrofit 的 README 链接也已指向 `lysine-dev/retrofit`。

### B. Ktor Client

#### B.1 版本与坐标（已实测）

| artifact | 最新稳定版 |
| --- | --- |
| `io.ktor:ktor-client-core` | **3.6.1**（`lastUpdated 20261029151253`） |
| `io.ktor:ktor-client-okhttp` / `ktor-client-cio` | 3.6.1 |
| `io.ktor:ktor-client-content-negotiation` / `ktor-serialization-kotlinx-json` | 3.6.1 |
| `io.ktor:ktor-bom` | **3.6.1**（BOM 存在，883 个 `<dependency>` 条目，含 `ktor-client-core` / `ktor-client-okhttp`） |

官方发布节奏佐证：ktor.io releases 页把 **3.6.1 (October 30, 2026)** 列为最新（「A patch release that fixes various issues with connection management, request cancellation, streaming, WebSockets, and platform-sp...」）。

#### B.2 `minCompileSdk` / `minAGP` 实测

**Ktor 全部没有 AAR。**逐条实测 `3.6.1`：

| artifact | `.aar` | `.jar` |
| --- | --- | --- |
| `ktor-client-core` | **404** | 102511 B |
| `ktor-client-okhttp` | **404** | 647 B（umbrella，只有 `kotlin-project-structure-metadata.json`） |
| `ktor-client-cio` | **404** | 16600 B |
| `ktor-client-content-negotiation` | **404** | 6122 B |
| `ktor-serialization-kotlinx-json` | **404** | 7665 B |
| `ktor-http` / `ktor-utils` / `ktor-io` / `ktor-network` | **404** | 60097 / 96743 / 110789 / 102969 B |
| `ktor-client-android` | **404** | 647 B（umbrella，同样只有元数据 json） |

→ **Ktor 自身无 `minCompileSdk`、无 `minAGP` 门槛**（无 AAR 即无 `aar-metadata.properties`）。

**但 Ktor 会把 OkHttp 拖进来**（这是 B 组唯一真正的门槛来源）：

| Ktor 版本 | 钉的 okhttp | 对应 `okhttp-android` 的 `minCompileSdk` |
| --- | --- | --- |
| **3.6.1** | `okhttp:5.5.1` | **37** ← **卡住** |
| 3.6.0 | `okhttp:5.5.0` | **37** ← 卡住 |
| 3.5.2 | `okhttp:5.3.2` | **1** ← 不卡 |
| 3.5.1 / 3.5.0 | `okhttp:5.3.2` | 1 |
| 3.4.3 | `okhttp:5.3.2` | 1 |

（同一张表用 CIO 引擎则**完全不涉及 OkHttp**：`ktor-client-cio-jvm:3.6.1` 的 POM 只有 `slf4j-api`、`kotlinx-coroutines-core-jvm`、`ktor-client-core-jvm`、`ktor-http-cio-jvm`、`ktor-websockets-jvm`、`ktor-network-tls-jvm`、`kotlin-stdlib`。）

#### B.3 传递依赖风险

实测 `3.6.1` 的 jvm POM：

- `ktor-client-core-jvm`：`slf4j-api:2.0.19`、`kotlinx-coroutines-core-jvm:1.11.0`、`ktor-http-jvm`、`ktor-http-cio-jvm`、`ktor-events-jvm`、`ktor-websocket-serialization-jvm`、`ktor-sse-jvm`、**`kotlin-stdlib:2.3.21`**、`kotlinx-coroutines-slf4j:1.11.0`(runtime)
- `ktor-client-okhttp-jvm`：上述 + **`okhttp-jvm:5.5.1`**（对 `com.squareup.okhttp3:okhttp` 的 umbrella 依赖）+ `okio-jvm:3.18.2`
- `ktor-serialization-kotlinx-json-jvm`：+ **`kotlinx-serialization-json-jvm:1.11.0`**、`kotlinx-serialization-json-io-jvm:1.11.0`

由此产生的**真实抬升**（与 A 组的结论正相反）：

1. **kotlin-stdlib → 2.3.21**。工程用 Kotlin **2.3.0**，Ktor 要求 **2.3.21**（更高）。Gradle 会把 stdlib 提到 2.3.21。实测 `kotlin-stdlib:2.3.21` 的 `mv = 2.3.0`，== 2.3.0 编译器能读，**元数据层面不阻断**。但「stdlib 补丁版本 > 编译器版本」是否产生警告/行为差异 = **待实测**。
2. **kotlinx-coroutines → 1.11.0**。Compose runtime 1.10.0 现要求 `coroutines-android:1.9.0`；Ktor 要求 `coroutines-core-jvm:1.11.0`。Gradle 取高 → **coroutines 从 1.9.0 抬到 1.11.0**。这是 B 组与 A 组最大的差别：**A 组完全不碰 coroutines，B 组一定会动它**。coroutines 1.11.0 的 stdlib 依赖是 2.2.20（低于 2.3.0/2.3.21）→ 不额外抬 stdlib。是否与 Compose runtime 1.10.0 的行为兼容 = **待实测**（这是 Ktor 路线最值得先验的一条）。
3. **kotlinx-serialization-json → 1.11.0**（mv 2.3.0，可被 2.3.0 编译器读；stdlib 要求 2.3.20）。若同时显式声明 1.10.0，Gradle 取 1.11.0。
4. **slf4j-api 是新增运行时依赖**。Ktor 的 JVM 引擎都依赖 `slf4j-api`，Android 上若没有绑定实现会打印「Failed to load class org.slf4j.impl.StaticLoggerBinder」之类的提示（**待实测**；本次未在任何 AAR 里找到 slf4j-android 绑定）。
5. **androidx.compose / androidx.lifecycle：不会被抬**。Ktor 的 POM 里没有它们。

#### B.4 与 Kotlin 2.3.0 的兼容性

- Ktor 3.6.1 的 jar 实测 `mv = 2.3.0`（`ktor-client-core` / `ktor-client-okhttp` 都测到 2.3.x），POM 声明 `kotlin-stdlib:2.3.21`。→ 它**是用 Kotlin 2.3.x 编译的**，2.3.0 编译器读 2.3.0 元数据没问题。
- Ktor 官方文档**没有**给出「支持的 Kotlin 版本区间」这类硬性声明（查过 `client-engines`、`releases`、`whats-new-360` 等页面，未见 Kotlin 版本下限表）。只找到一条相关事实：releases 页 3.5.1（June 26, 2026）条目提到「a wide range of bug fixes covering **Kotlin 2.4 compiler compatibility**」→ 说明 3.5.0 那条线在 Kotlin 2.4 下有过问题、3.5.1 才修；本工程是 2.3.0，不在这条修复的直接路径上，但**「Ktor 3.6.1 + Kotlin 2.3.0」没有官方明文背书**（待核实）。
- CIO 引擎的官方平台要求（`client-engines` 页表格原文）：`CIO` 支持「Android 7.0+」/「Java 8+」，并附注「* To use the CIO engine on older Android versions, you need to enable Java 8 API desugaring.」→ **API 26 ≥ 24，满足**。OkHttp 引擎官方要求「Android 5.0+ / Java 8+」→ 满足。

#### B.5 minSdk 26

- CIO：官方要求 Android 7.0+（API 24）→ **26 满足**。
- OkHttp 引擎：Android 5.0+（API 21）→ **26 满足**。
- Ktor 无 AAR，无 `uses-sdk` 可读 → 以官方文档表格为准。

#### B.6 R8 / ProGuard 官方要求

- 实测 Ktor 各 jar 里**只有 `ktor-utils-jvm` 带 proguard 规则**：`META-INF/proguard/ktor.pro`，内容全文：
  ```
  # Most of volatile fields are updated with AtomicFU and should not be mangled/removed
  -keepclassmembers class io.ktor.** { volatile <fields>; }
  -keepclassmembernames class io.ktor.** { volatile <fields>; }
  # client engines are loaded using ServiceLoader so we need to keep them
  -keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer
  ```
  → 关键点：**Ktor 的引擎是通过 `ServiceLoader` 加载的**（实测 `ktor-client-okhttp-jvm` / `ktor-client-cio-jvm` 都含 `META-INF/services/io.ktor.client.HttpClientEngineContainer`），所以必须保留实现类，这条官方规则已在 `ktor.pro` 里。
- **Ktor 官方文档里没有 R8/ProGuard 专门页面**（查过 `client-engines`、`client-create-new-application`，`proguard`/`R8`/`ServiceLoader` 关键词均无命中）→ 「除自带 `ktor.pro` 外还需要什么」**待核实**，不要自己发明规则。
- kotlinx.serialization（Ktor 的 JSON 路径同样要用它）的规则见 §A.6：自带 `kotlinx-serialization-common.pro` + `kotlinx-serialization-r8.pro`，具名 companion object 除外。

#### B.7 许可

- `ktor-client-*` / `ktor-bom` 的 POM：**The Apache Software License, Version 2.0**（实测，逐条读过）。
- 传递的 `okhttp` / `okio` / `kotlinx-serialization` / `slf4j-api` 均为 Apache-2.0 或 MIT（SLF4J 是 MIT；**本次未逐个读 slf4j 的 POM 许可字段**）。

### C. 只用 OkHttp（不引 Retrofit）

#### C.1 版本与坐标

同 §A.1：`com.squareup.okhttp3:okhttp:5.5.1`（`okhttp-bom:5.5.1` 可用）；若走 4.x 线，最新是 **4.12.1**。

#### C.2 `minCompileSdk` 实测

完全取决于选哪条线（同 §A.2，不再重复表）：

- **5.5.1 / 5.5.0** → `okhttp-android` AAR **37** → **不可用**。
- **5.4.0** → **36** → 恰好等于本工程，零余量，**元数据层面可用**。
- **5.3.2 及更早** → **1** → 可用。
- **4.12.1 / 4.12.0** → **无 AAR**（纯 jar，341 个 class 里含 Android 平台实现，`META-INF/proguard/okhttp3.pro` 自带）→ **无 `minCompileSdk`**。

#### C.3 传递依赖风险

- 5.x android 变体：`kotlin-stdlib`（2.1.21 / 2.2.21）、`okio`、`androidx.annotation:1.10.0`、`androidx.startup:1.2.0`。**不碰 compose / lifecycle / coroutines**。
- 4.12.x：`okio:3.6.0`、`kotlin-stdlib-jdk8:1.8.21`。同样不碰 compose/lifecycle/coroutines。
- 唯一的「隐形代价」是 `androidx.startup` 会往 manifest 里合一个 `InitializationProvider`（见 §A.5），会增加一点启动期初始化，但**不涉及版本冲突**。

#### C.4 与 Kotlin 2.3.0 兼容

- okhttp-jvm 5.5.1 的 `mv = 2.1.0`、okio-jvm 3.18.1 `mv = 2.1.0` → 低于 2.3.0，无前向兼容问题。
- class 文件 major：okhttp-jvm 5.5.1 = {52, 53}（Java 8 / 9）→ 与 JDK 17 编译目标无冲突。

#### C.5 minSdk 26

- 官方 README：「OkHttp works on Android 5.0+ (API level 21+) and Java 8+」；AAR manifest 实测 `minSdkVersion=21` → **26 满足**。

#### C.6 R8

- 5.x：AAR 的 `proguard.txt`（consumer rules）自动生效；4.x：jar 的 `META-INF/proguard/okhttp3.pro`。**都不需要手写 keep**。
- 但**没有 Retrofit 那层「接口由 Proxy 生成」的处理**——纯 OkHttp 没有反射代理，反而不需要那些规则。

#### C.7 许可

- Apache-2.0（POM 实测 + GitHub `license.spdx_id`）。

#### C.8 C 组特有的功能缺口（事实层面）

- **不带 JSON 解析**。要么另加 kotlinx.serialization / Moshi（回到 A1/A2 的版本与插件问题），要么用 Android 自带的 `org.json`（但不属于「第三方」——见 D）。
- 需要自己写：请求构造、URL/query 拼接、错误码→异常的映射、并发调度（OkHttp 的 `enqueue` 已给回调线程）、拦截器（可自己写 `Interceptor` 做 base-url / 鉴权 / 日志）。这些是**工作量**，不是风险，故不展开。

### D. 零第三方依赖：`HttpURLConnection` / `HttpsURLConnection`

#### D.1 版本与坐标

无。使用 `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection`（Android 平台自带），JSON 用 `org.json.*`（`android.jar` 自带，不需要任何 Gradle 依赖）。

#### D.2 `minCompileSdk` / `minAGP`

**无任何 AAR、无任何 POM、无元数据门槛。** 不存在「拖到 compileSdk 37」的可能。

#### D.3 传递依赖风险

**零传递依赖。** 不会抬升 kotlin-stdlib / coroutines / lifecycle / compose。这是它最大的结构性优势：Compose BOM 2025.12.00 的锁定完全不受影响。

#### D.4 与 Kotlin 2.3.0 兼容

- 无第三方字节码 → 无 Kotlin 元数据版本问题。
- `HttpURLConnection` 是 Java API，Kotlin 直接调用；需要在协程里用 `withContext(Dispatchers.IO)` 或自建线程池（工程已有 Kotlin 2.3.0 + 无 coroutines 显式依赖，但 Compose runtime 会带 `coroutines-android:1.9.0` 进来，**所以 `Dispatchers.IO` 可用**——这一条基于 okhttp 之外的 POM 实测，见 `runtime-android:1.10.0` 的依赖里有 `kotlinx-coroutines-android:1.9.0`）。

#### D.5 minSdk 26

- `HttpURLConnection` / `HttpsURLConnection` 自 API 1 就有；`org.json` 自 API 1 就有。**26 满足**。
- TLS：Android 上 TLS 1.2 的默认启用情况随版本变化（API 20+ 默认启用），26 上没有已知缺口——但**本次未取得一手来源**（developer.android.com 不可达），标为待核实。

#### D.6 R8

- **不需要任何 keep 规则**：没有反射、没有序列化框架、没有 ServiceLoader。
- 前提是**不在 JSON 层用反射**（用 `org.json` 手工取值就不涉及）。

#### D.7 许可

- 不引入任何第三方代码 → 无许可义务（`org.json` 随 Android 平台分发）。

#### D.8 已知的工程代价（事实，不是评价）

- 需要自己实现：连接复用（`HttpURLConnection` 有 `Keep-Alive` 但需自己管理 `disconnect()`）、超时（`setConnectTimeout`/`setReadTimeout`）、重试、gzip 解压（`Accept-Encoding: gzip` 需自己判断）、cookie、HTTP/2（**不支持**，只有 HTTP/1.1）、multipart、拦截器链。
- **明文流量**：`targetSdk=36` ≥ 28 时，Android 默认禁止 cleartext HTTP（需 `networkSecurityConfig` 或 `usesCleartextTraffic` 显式放开）。这一条对**所有候选**都成立，但零依赖方案没有库帮你兜底。**本次未能取得 developer.android.com 的一手来源**（该域从本机超时，§0），标为待核实。

## 3 `minCompileSdk` 实测总表

**方法**：Python `urllib` 下载 `.aar` 字节 → `zipfile.ZipFile(io.BytesIO(...))` → 读 `META-INF/com/android/build/gradle/aar-metadata.properties` → 正则取 `minCompileSdk` / `minAndroidGradlePluginVersion`；无该条目则记「无 AAR / 无元数据」。同时解 `AndroidManifest.xml` 取 `minSdkVersion`。

| # | artifact | version | minCompileSdk | minAGP | 解包来源 URL |
| --- | --- | --- | --- | --- | --- |
| 1 | `com.squareup.okhttp3:okhttp-android` | 5.5.1 | **37** | 1.0.0 | `https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.1/okhttp-android-5.5.1.aar` |
| 2 | `com.squareup.okhttp3:okhttp-android` | 5.5.0 | **37** | 1.0.0 | `.../okhttp-android/5.5.0/okhttp-android-5.5.0.aar` |
| 3 | `com.squareup.okhttp3:okhttp-android` | 5.4.0 | **36** | 1.0.0 | `.../okhttp-android/5.4.0/okhttp-android-5.4.0.aar` |
| 4 | `com.squareup.okhttp3:okhttp-android` | 5.3.2 | **1** | 1.0.0 | `.../okhttp-android/5.3.2/okhttp-android-5.3.2.aar` |
| 5 | `com.squareup.okhttp3:okhttp-android` | 5.2.0 | 1 | 1.0.0 | `.../okhttp-android/5.2.0/okhttp-android-5.2.0.aar` |
| 6 | `com.squareup.okhttp3:okhttp-android` | 5.1.0 | 1 | 1.0.0 | `.../okhttp-android/5.1.0/okhttp-android-5.1.0.aar` |
| 7 | `com.squareup.okhttp3:okhttp-android` | 5.0.0 | 1 | 1.0.0 | `.../okhttp-android/5.0.0/okhttp-android-5.0.0.aar` |
| 8 | `androidx.startup:startup-runtime` | 1.2.0 | 34 | 1.0.0 | `https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/1.2.0/startup-runtime-1.2.0.aar` |
| 9 | `com.squareup.retrofit2:retrofit` | 3.0.0 | **无 AAR**（jar 131218 B） | — | `https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0.jar` |
| 10 | `com.squareup.retrofit2:converter-kotlinx-serialization` | 3.0.0 | 无 AAR（jar 10765 B） | — | `.../converter-kotlinx-serialization/3.0.0/...jar` |
| 11 | `com.squareup.retrofit2:converter-moshi` | 3.0.0 | 无 AAR（jar 6312 B） | — | `.../converter-moshi/3.0.0/...jar` |
| 12 | `com.squareup.okhttp3:okhttp` | 4.12.0 / 4.12.1 | **无 AAR**（jar 789531 B / 含 Android 平台类） | — | `.../okhttp/4.12.0/okhttp-4.12.0.jar` |
| 13 | `com.squareup.okhttp3:okhttp` | 5.5.1 | 无内容（jar 仅 **754 B**，android 变体指向 #1） | — | `.../okhttp/5.5.1/okhttp-5.5.1.jar` |
| 14 | `com.squareup.okhttp3:okhttp-jvm` | 5.5.1 | 无 AAR（jar 963622 B） | — | `.../okhttp-jvm/5.5.1/okhttp-jvm-5.5.1.jar` |
| 15 | `com.squareup.okio:okio-jvm` | 3.18.1 | 无 AAR（jar 392463 B） | — | `.../okio-jvm/3.18.1/okio-jvm-3.18.1.jar` |
| 16 | `com.squareup.moshi:moshi` | 1.15.2 | 无 AAR（jar 162258 B） | — | `.../moshi/1.15.2/moshi-1.15.2.jar` |
| 17 | `com.squareup.moshi:moshi-kotlin` | 1.15.2 | 无 AAR（jar 19519 B） | — | `.../moshi-kotlin/1.15.2/moshi-kotlin-1.15.2.jar` |
| 18 | `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.10.0 / 1.11.0 / 1.12.0 | 无 AAR（jar） | — | `.../kotlinx-serialization-json-jvm/<v>/...jar` |
| 19 | `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.10.0 / 1.12.0 | 无 AAR（jar） | — | `.../kotlinx-serialization-core-jvm/<v>/...jar` |
| 20 | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | **无 AAR**（jar 17819 B） | — | `.../kotlinx-coroutines-android/1.11.0/...jar` |
| 21 | `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.11.0 | 无 AAR（jar 1577052 B） | — | `.../kotlinx-coroutines-core-jvm/1.11.0/...jar` |
| 22 | `io.ktor:ktor-client-core` / `-okhttp` / `-cio` / `-content-negotiation` / `-android` | 3.6.1 | **无 AAR（全部 404）** | — | `https://repo1.maven.org/maven2/io/ktor/<artifact>/3.6.1/<artifact>-3.6.1.jar` |
| 23 | `io.ktor:ktor-http` / `ktor-utils` / `ktor-io` / `ktor-network` | 3.6.1 | 无 AAR（全部 404） | — | 同上 |
| 24 | `io.ktor:ktor-serialization-kotlinx-json` | 3.6.1 | 无 AAR（jar 7665 B） | — | `.../ktor-serialization-kotlinx-json/3.6.1/...jar` |
| 25 | `androidx.annotation:annotation` | 1.10.0 | 无 AAR（jar 20979 B） | — | `dl.google.com/.../androidx/annotation/annotation/1.10.0/annotation-1.10.0.jar` |
| — | 对照：`androidx.compose.runtime:runtime-saveable-android` | 1.12.1（BOM 2026.09.00 会选） | **37**（仓库 README 已记录本机实测失败） | — | 引用仓库 `README.md` 的报错原文（本次未重复解包） |

**读表要点**：

- 只有 **`okhttp-android`** 和 **`androidx.startup`** 这两个 AAR 有门槛，且都 ≤ 37。
- `minCompileSdk=1` 的含义是「无实质约束」，**不是「要求 API 1」**。
- 「无 AAR」≠「没有门槛」——对 `com.squareup.okhttp3:okhttp`（umbrella）来说，Android 消费者拿到的是 `okhttp-android`（#1），**门槛在 #1 那一行**。这是本次最容易看错的地方。

## 4 冲突与风险清单

### 4.1 会「拖到 compileSdk 37」的候选（**硬阻断**）

| 候选 | 传导路径 | 证据 |
| --- | --- | --- |
| **OkHttp 5.5.0 / 5.5.1**（任何直接或间接使用者） | `okhttp` → Gradle 选 android 变体 → `okhttp-android:5.5.1` → `minCompileSdk=37` | module 元数据 `available-at` + AAR 实测 |
| **Retrofit 3.0.0 + OkHttp 5.5.1** | 同上（Retrofit 本身钉 4.12.0，但显式加 5.5.1 就覆盖它） | 同上 |
| **Ktor 3.6.1 / 3.6.0 + OkHttp 引擎** | `ktor-client-okhttp-jvm` → `okhttp:5.5.1`/`5.5.0` → `okhttp-android` 37 | Ktor jvm POM 实测 + AAR 实测 |
| Ktor 3.5.x + OkHttp 引擎 | → `okhttp:5.3.2` → `minCompileSdk=1` | **不构成 37** |

→ **结论性事实**：**任何走 OkHttp 5.5.x 的路线，在本机（compileSdk 36 / 仅 android-36）都构建不过**。要在 OkHttp 线上避开 37，只能（a）钉 OkHttp 5.4.0（36，零余量）、（b）钉 5.3.2 或更早、（c）走 4.12.x（无 AAR）。

### 4.2 会静默升级版本、但不撞 compileSdk 的

| 风险 | 细节 | 严重度 |
| --- | --- | --- |
| **kotlin-stdlib 被抬到 2.3.21**（Ktor 路线） | Ktor 3.6.1 要求 stdlib 2.3.21 > 工程 2.3.0。stdlib 2.3.21 的 mv 实测 = 2.3.0，元数据可读 | 中（警告级，待实测） |
| **kotlinx-coroutines 从 1.9.0 抬到 1.11.0**（Ktor 路线） | Compose runtime 1.10.0 要 1.9.0，Ktor 要 1.11.0，取高。A 组不触发 | 中（Compose 运行时行为待实测） |
| **kotlinx-serialization-json 被抬到 1.11.0 / 1.12.0** | 1.11.0 的 mv=2.3.0（可读）；**1.12.0 的 mv=2.4.0 且要 stdlib 2.4.10** | 低-中（见 4.3） |
| **kotlin-reflect 1.8.21 被引入**（`moshi-kotlin` 反射路径） | Kotlin 1.8 的反射库 + 2.3.0 编译器；R8 后体积/keep 成本 | 中（待实测） |
| **slf4j-api 2.0.19 进入运行时**（Ktor 路线） | Android 上无绑定实现的日志噪声 | 低（待实测） |
| **androidx.startup 初始化提供者**（OkHttp 5 路线） | manifest 合并出 `InitializationProvider`，启动期初始化；可用 `tools:node="remove"` + 手动 `OkHttp.initialize()` | 低（官方给了做法） |

### 4.3 `@kotlin.Metadata` 版本兼容——**本次做了实测，结论与直觉不同**

不只看 CHANGELOG，直接拿 **`kotlin-compiler-embeddable:2.3.0`** 跑 `K2JVMCompiler` 编译一段引用 `kotlinx.serialization.json.Json` 的代码，classpath 分别放不同版本的序列化库：

| 用例 | 编译器 | classpath 上的库 | 结果 |
| --- | --- | --- | --- |
| A | 2.3.0 | serialization 1.11.0（mv 2.3.0）+ stdlib 2.3.21 | **exit 0（通过）** |
| B | 2.3.0 | serialization **1.12.0（mv 2.4.0）** + stdlib 2.3.21 | **exit 0（通过）** |
| C | 2.3.0 | serialization 1.12.0（mv 2.4.0）+ **stdlib 2.4.10** | **exit 0（通过）** |
| D | **2.2.0** | serialization 1.12.0（mv 2.4.0） | **exit 1**：`error: module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0, expected version is 2.2.0.` |
| E | 2.3.0 | **stdlib 2.5.0-Beta2（mv 2.5.0）** | **exit 1**：`... The binary version of its metadata is 2.5.0, expected version is 2.3.0.` |

**由实测反推的规则（有证据，但只覆盖测过的组合）**：Kotlin 2.x 编译器**能读比自身高 1 个 minor 的元数据**，**高 2 个 minor 就报错**。

- 2.3.0 编译器 ← mv 2.3.0 ✅、mv 2.4.0 ✅、mv 2.5.0 ❌
- 2.2.0 编译器 ← mv 2.4.0 ❌（2 个 minor）

**对本工程的直接含义**：

- 用 **Kotlin 2.3.0 编译器**时，`kotlinx.serialization` **1.11.0（mv 2.3.0）和 1.12.0（mv 2.4.0）都能编译**。所以「1.12.0 是 2.4.10 编的，2.3.0 用不了」这个推断**是错的**（本次证伪）。
- 真正的约束变成**间接**的：1.12.0 要求 `kotlin-stdlib ≥ 2.4.10`，会把 stdlib 抬到 2.4.10；而 **stdlib 2.4.10 的 mv 是 2.4.0 ≥ 2.3.0 编译器（1 个 minor）→ 仍可编译**（用例 C 已验证 exit 0）。也就是说 **serialization 1.12.0 在这条路线上其实是能编过的**，代价是把 stdlib 从 2.3.0 抬到 2.4.10。
- 但**「能编译」≠「安全」**：stdlib 2.4.10 的运行时配 2.3.0 的编译器产物，属于「runtime 比 compiler 新」，是否产生 `Runtime JAR files in the classpath have version ...` 类警告、以及 AGP 9 内置 Kotlin 是否另有一套校验，**本次未验证**（§5）。保守做法仍是钉 **1.10.0**（与 Kotlin 2.3.0 完全同源：mv 2.3.0 + stdlib 2.3.0）或 **1.11.0**。

### 4.4 R8 相关风险汇总

| 风险 | 说明 |
| --- | --- |
| **`moshi-kotlin` 反射 + R8 full mode** | 官方明说要自己写 keep（§A.6）。漏写 → debug 正常、release 崩（与本仓库 `proguard-rules.pro` 注释里描述的症状一致）。 |
| **`kotlinx.serialization` + 具名 companion object** | 官方明说自带规则**不覆盖**这种类，要按 README 模板补（§A.6）。 |
| **Ktor 引擎 ServiceLoader** | 官方 `ktor.pro` 已 keep `io.ktor.client.engine.** implements HttpClientEngineContainer`；**但 R8 的 ServiceLoader 优化是否仍会移除此实现，未实测**。 |
| **Retrofit 的 `-if interface * { @retrofit2.http.* <methods>; }`** | 官方规则已覆盖。R8 full mode 下「接口由 Proxy 生成、R8 把可能值替换成 null」这个坑，官方注释里点名了并给了 `-keep,allowobfuscation interface <1>`。 |
| **`isShrinkResources=true`** | 与网络库无关，但注意任何放在 `res/raw` 的证书/配置若无引用会被删（本工程暂无）。 |
| **零依赖方案（D）** | 无需任何 keep。 |

### 4.5 许可冲突

**无冲突**：所有候选（Retrofit / OkHttp / Okio / Moshi / Ktor / kotlinx.serialization / androidx.startup / androidx.annotation）均为 **Apache-2.0**（POM 实测）。SLF4J 为 MIT（未读 POM，标待核实）。零依赖方案无第三方。

### 4.6 工程侧必须先补的一件事（与候选无关）

**`app/src/main/AndroidManifest.xml` 目前没有 `android.permission.INTERNET`。** 任何候选落地都必须先补这一条（只有 OkHttp 5 会因为 AAR manifest 合并而"顺带"获得该权限，但不应该依赖这个）。

## 5 待实测清单（**未跑构建，一律未验证**）

> 本次严格按要求未执行 `gradlew`、未改任何构建文件。以下全部**未验证**，不要当成已验证结论。

1. **构建能否通过**：对每个候选执行 `./gradlew :app:assembleDebug`，确认 AGP 是否真的因 `okhttp-android` 的 `minCompileSdk=37` 报错。§4.1 的判定基于 AAR 元数据 + AGP 的 AAR 元数据校验机制，**未在本工程实测**（对照：仓库 `README.md` 已记录 Compose `runtime-saveable 1.12.1` 的同类报错原文，机制相同）。
2. **Gradle 是否真的把 `okhttp` 解析成 `okhttp-android`**：`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`。本文依据的是 module 元数据的变体属性（`org.gradle.jvm.environment=android` + `available-at`），未看实际解析结果。
3. **AGP 9「内置 Kotlin」下如何应用 `org.jetbrains.kotlin.plugin.serialization`**：插件在 Maven Central / Gradle Plugin Portal 的 2.3.0 版本已确认存在，但**在 AGP 9 built-in Kotlin 工程里的正确声明方式未验证**。`developer.android.com` 从本机不可达（本次用 urllib 访问超时、Firecrawl 返回 429），因此**没有取到官方页面原文**。
4. **KSP 与 Kotlin 2.3.0 的匹配**：`moshi-kotlin-codegen` 需要 KSP。实测 Maven Central 上 `com.google.devtools.ksp.gradle.plugin` 最新为 **2.3.13**，且**不存在** `2.3.0-<kspver>` 这种旧式版本号（旧式只到 `2.2.21-2.0.5`）。KSP 2.3.13 是否与 Kotlin 2.3.0 兼容 → **待核实**。这也是「Moshi codegen 路线」的前置风险。
5. **kotlin-reflect 1.8.21 + Kotlin 2.3.0 编译器的实际行为**（`moshi-kotlin` 反射路径）。仅知道版本号，未测。
6. **`kotlin-stdlib 2.3.21/2.4.10` 配 Kotlin 2.3.0 编译器是否产生警告或 AGP 侧报错**。元数据层面已实测可通过（§4.3 用例 B/C），但没有在真实 Gradle 工程里验证。
7. **coroutines 1.9.0 → 1.11.0 对 Compose runtime 1.10.0 的影响**（仅 Ktor 路线触发）。
8. **release 包（R8 full mode）能否跑通**：`./gradlew :app:assembleRelease` + 真机 smoke test。本次无真机、未构建。
9. **APK 体积增量**：仅记录 jar/AAR 下载字节（okhttp-android 5.5.1 ≈ 950 KB、okhttp-android 5.4.0 ≈ 844 KB、ktor-client-core-jvm ≈ 966 KB、ktor-client-cio-jvm ≈ 145 KB、retrofit ≈ 131 KB、moshi ≈ 162 KB），**R8 后的真实增量未测**。
10. **Ktor 的 R8 要求**：官方文档里没有 ProGuard/R8 专门页面（本次在 `client-engines`、`client-create-new-application` 均未搜到 `proguard`/`R8`/`ServiceLoader` 字样），除自带 `ktor.pro` 之外还需要什么 → **待核实**。
11. **cleartext HTTP 默认被禁**（`targetSdk=36` ≥ 28 需 `networkSecurityConfig`）：**未能取得一手来源**（developer.android.com 不可达），按常识记录但未取证。
12. **TLS 1.2 在 API 26 的默认启用状态**：同上，未取证。
13. **Moshi 官方最低 API 版本声明**：未从官方 README 取到明文（只从 class 文件 major/多版本目录推断）。
14. **SLF4J 的许可字段**：未读 POM。
15. **Ktor 3.6.1 官方支持的 Kotlin 版本区间**：官方未给明文；只有 3.5.1 releases 条目提到修过「Kotlin 2.4 compiler compatibility」。

## 6 核查通道说明（本次实际怎么取的证）

| 通道 | 用途 | 状态 |
| --- | --- | --- |
| Python `urllib` 直连 `repo1.maven.org` | 下载 POM / `maven-metadata.xml` / `.jar` / `.aar`，全部在内存里 `zipfile` 解包 | **可用**，本次主通道 |
| Python `urllib` 直连 `dl.google.com/dl/android/maven2` | androidx AAR（`startup-runtime`、`annotation`）与 Compose BOM POM | **可用** |
| `gh api`（GitHub CLI 2.102.0） | 读各库 README / CHANGELOG（Retrofit、OkHttp、Moshi、kotlinx.serialization）、确认仓库重定向与 license | **可用**（`github.com` 网页直连会被 reset，`api.github.com` 正常） |
| `kotlinlang.org` | serialization / `-language-version` / `-api-version` / 演进原则文档 | **可用** |
| `ktor.io` | `client-engines`（引擎平台表）、`releases`、`whats-new-360` | **可用** |
| `developer.android.com` | AGP built-in Kotlin、cleartext、TLS 默认值 | **不可达**（urllib 超时；Firecrawl 429）→ 相关断言全部标「待核实」 |
| `kotlin-compiler-embeddable:2.3.0` + `K2JVMCompiler`（本地 JDK 25 执行） | 实测 metadata 版本兼容边界（§4.3） | **可用**，这是本文最有价值的一手实测 |
| `javap -v`（`C:\Program Files\Java\jdk-25\bin\javap.exe`） | 交叉验证 `@kotlin.Metadata.mv` 的解读 | **可用**（作为自写 class 解析器的对照） |

## 7 引用（每条断言的一手来源）

**Maven 元数据 / POM / 制品（全部本次直接下载）**

- Retrofit： [maven-metadata.xml](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/maven-metadata.xml) · [retrofit-3.0.0.pom](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0.pom) · [retrofit-3.0.0.jar](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0.jar) · [retrofit-bom metadata](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit-bom/maven-metadata.xml)
- Retrofit converters： [converter-kotlinx-serialization](https://repo1.maven.org/maven2/com/squareup/retrofit2/converter-kotlinx-serialization/3.0.0/converter-kotlinx-serialization-3.0.0.pom) · [converter-moshi](https://repo1.maven.org/maven2/com/squareup/retrofit2/converter-moshi/3.0.0/converter-moshi-3.0.0.pom)
- OkHttp： [metadata](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml) · [okhttp-5.5.1.pom](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/5.5.1/okhttp-5.5.1.pom) · [okhttp-5.5.1.module](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/5.5.1/okhttp-5.5.1.module) · [okhttp-4.12.0.pom](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom) · [okhttp-5.4.0.pom](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/5.4.0/okhttp-5.4.0.pom)
- OkHttp AAR（**`minCompileSdk` 实测对象**）： [okhttp-android-5.5.1.aar](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.1/okhttp-android-5.5.1.aar) · [5.5.0](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.5.0/okhttp-android-5.5.0.aar) · [5.4.0](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.4.0/okhttp-android-5.4.0.aar) · [5.3.2](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.3.2/okhttp-android-5.3.2.aar) · [5.2.0](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-android/5.2.0/okhttp-android-5.2.0.aar)
- OkHttp BOM / jvm： [okhttp-bom-5.5.1.pom](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-bom/5.5.1/okhttp-bom-5.5.1.pom) · [okhttp-jvm-5.5.1.pom](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.1/okhttp-jvm-5.5.1.pom) · [okhttp-jvm-5.5.1.jar](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.1/okhttp-jvm-5.5.1.jar)
- Moshi： [metadata](https://repo1.maven.org/maven2/com/squareup/moshi/moshi/maven-metadata.xml) · [moshi-1.15.2.pom](https://repo1.maven.org/maven2/com/squareup/moshi/moshi/1.15.2/moshi-1.15.2.pom) · [moshi-kotlin-1.15.2.pom](https://repo1.maven.org/maven2/com/squareup/moshi/moshi-kotlin/1.15.2/moshi-kotlin-1.15.2.pom) · [moshi-kotlin-codegen-1.15.2.pom](https://repo1.maven.org/maven2/com/squareup/moshi/moshi-kotlin-codegen/1.15.2/moshi-kotlin-codegen-1.15.2.pom)
- kotlinx.serialization： [metadata](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) · [1.10.0.pom](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.10.0/kotlinx-serialization-json-jvm-1.10.0.pom) · [1.11.0.pom](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.11.0/kotlinx-serialization-json-jvm-1.11.0.pom) · [1.12.0.pom](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.12.0/kotlinx-serialization-json-jvm-1.12.0.pom)
- kotlinx.coroutines： [1.11.0 pom](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.11.0/kotlinx-coroutines-android-1.11.0.pom)
- Ktor： [client-core metadata](https://repo1.maven.org/maven2/io/ktor/ktor-client-core/maven-metadata.xml) · [ktor-client-okhttp metadata](https://repo1.maven.org/maven2/io/ktor/ktor-client-okhttp/maven-metadata.xml) · [ktor-bom-3.6.1.pom](https://repo1.maven.org/maven2/io/ktor/ktor-bom/3.6.1/ktor-bom-3.6.1.pom) · [ktor-client-core-jvm-3.6.1.pom](https://repo1.maven.org/maven2/io/ktor/ktor-client-core-jvm/3.6.1/ktor-client-core-jvm-3.6.1.pom) · [ktor-client-okhttp-jvm-3.6.1.pom](https://repo1.maven.org/maven2/io/ktor/ktor-client-okhttp-jvm/3.6.1/ktor-client-okhttp-jvm-3.6.1.pom) · [ktor-client-cio-jvm-3.6.1.pom](https://repo1.maven.org/maven2/io/ktor/ktor-client-cio-jvm/3.6.1/ktor-client-cio-jvm-3.6.1.pom) · [ktor-serialization-kotlinx-json-jvm-3.6.1.pom](https://repo1.maven.org/maven2/io/ktor/ktor-serialization-kotlinx-json-jvm/3.6.1/ktor-serialization-kotlinx-json-jvm-3.6.1.pom) · [ktor-client-android-3.6.1.pom](https://repo1.maven.org/maven2/io/ktor/ktor-client-android/3.6.1/ktor-client-android-3.6.1.pom)
- Kotlin 编译器（metadata 实测用）： [kotlin-compiler-embeddable-2.3.0.jar](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.3.0/kotlin-compiler-embeddable-2.3.0.jar) · [kotlin-compiler-embeddable-2.2.0.jar](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.0/kotlin-compiler-embeddable-2.2.0.jar) · [kotlin-stdlib 2.3.0](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.3.0/kotlin-stdlib-2.3.0.jar) / [2.3.21](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.3.21/kotlin-stdlib-2.3.21.jar) / [2.4.10](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.10/kotlin-stdlib-2.4.10.jar) / [2.5.0-Beta2](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.5.0-Beta2/kotlin-stdlib-2.5.0-Beta2.jar)
- serialization Gradle 插件（**2.3.0 存在性**）： [Maven Central 插件 marker POM](https://repo1.maven.org/maven2/org/jetbrains/kotlin/plugin/serialization/org.jetbrains.kotlin.plugin.serialization.gradle.plugin/2.3.0/org.jetbrains.kotlin.plugin.serialization.gradle.plugin-2.3.0.pom) · [Gradle Plugin Portal 同一坐标](https://plugins.gradle.org/m2/org/jetbrains/kotlin/plugin/serialization/org.jetbrains.kotlin.plugin.serialization.gradle.plugin/2.3.0/org.jetbrains.kotlin.plugin.serialization.gradle.plugin-2.3.0.pom) · [kotlin-serialization-2.3.0.pom](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-serialization/2.3.0/kotlin-serialization-2.3.0.pom)
- KSP： [com.google.devtools.ksp.gradle.plugin metadata](https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml)

**Google Maven（androidx）**

- [androidx.startup:startup-runtime:1.2.0 AAR](https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/1.2.0/startup-runtime-1.2.0.aar)（`minCompileSdk=34` 实测对象）
- [compose-bom-2025.12.00.pom](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.12.00/compose-bom-2025.12.00.pom)（Compose 1.10.0 / material3 1.4.0 映射实测对象）
- [androidx.compose.runtime:runtime-android:1.10.0 POM](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-android/1.10.0/runtime-android-1.10.0.pom)（`coroutines-android:1.9.0`、`kotlin-stdlib:2.0.21` 来源）
- [androidx.lifecycle:lifecycle-runtime-ktx:2.10.0 POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/2.10.0/lifecycle-runtime-ktx-2.10.0.pom)
- [androidx.annotation:annotation:1.10.0 jar](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.10.0/annotation-1.10.0.jar)

**官方 README / CHANGELOG（`gh api` 读取）**

- Retrofit CHANGELOG（3.0.0 升级到 OkHttp 4.12、3.x 向前二进制兼容）· [README（Java 8+/API 21+、R8 规则自动包含）](https://github.com/lysine-dev/retrofit/blob/master/README.md)
- [OkHttp CHANGELOG（5.0.0 拆分 JVM/Android artifact、5.5.0 Commonhaus 与签名密钥、5.5.1 时间）](https://github.com/lysine-dev/okhttp/blob/master/CHANGELOG.md)
- OkHttp README（API 21+ / Java 8+、AndroidX Startup 用法、KMP 与 `okhttp-jvm` vs `okhttp-android` 说明）
- [Moshi README（R8/ProGuard 一节：自带规则够用，但反射序列化必须自己加 keep；enum 需 `@JsonClass(generateAdapter=false)`）](https://github.com/square/moshi/blob/master/README.md)
- [kotlinx.serialization README（插件随 Kotlin 发布、ProGuard 规则默认自带、具名 companion object 例外）](https://github.com/Kotlin/kotlinx.serialization/blob/master/README.md)
- [kotlinx.serialization CHANGELOG（1.10.0 based on Kotlin 2.3.0；1.11.0 → 2.3.20；1.12.0 → 2.4.10）](https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md)

**Kotlin 官方文档**

- [Serialization（kotlinx.serialization 与 Kotlin 版本独立演进、格式库清单）](https://kotlinlang.org/docs/serialization.html)
- [Compiler reference（`-language-version` / `-api-version`；「可配置最新三个稳定版本」；「编译器忽略使用更新版本 stdlib API 的声明」）](https://kotlinlang.org/docs/compiler-reference.html)
- [Kotlin evolution principles（JVM 上额外支持至少三个旧 language/API 版本，让库作者用新编译器仍兼容旧消费者）](https://kotlinlang.org/docs/kotlin-evolution-principles.html)
- [Kotlin Metadata JVM library（`mv` / `JvmMetadataVersion` 概念）](https://kotlinlang.org/docs/metadata-jvm.html)

**Ktor 官方文档**

- [Client engines（引擎平台表：Android 引擎、OkHttp、CIO；「CIO Android 7.0+，旧版需 Java 8 desugaring」；`ktor-client-okhttp-jvm` / `ktor-client-android-jvm` 坐标）](https://ktor.io/docs/client-engines.html)
- [Ktor releases（3.6.1 = October 30, 2026；3.5.1 修 Kotlin 2.4 compiler compatibility）](https://ktor.io/docs/releases.html)
- [What's new in Ktor 3.6.0](https://ktor.io/docs/whats-new-360.html)

**工程内文件（仅阅读，未修改）**

- `gradle/libs.versions.toml`（版本锁定注释、`kotlin.plugin.compose` 为唯一 Kotlin 插件）
- `app/build.gradle.kts`（compileSdk 36 / minSdk 26 / JDK 17 / release 开 R8 + 资源收缩 / 依赖清单）
- `app/src/main/AndroidManifest.xml`（**无 INTERNET 权限**）
- `app/proguard-rules.pro`（现状：只保留行号，其余靠工具链自动规则）
- `README.md`（Compose BOM 2026.09.00 因 `runtime-saveable 1.12.1` 要求 compileSdk 37 而锁定 2025.12.00 的实测记录）
- `docs/research/compose-navigation-shell.md`（AAR 元数据核查法先例）
