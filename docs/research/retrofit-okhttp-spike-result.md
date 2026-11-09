# Retrofit 3.0.0 + OkHttp 4.12.1 依赖 spike 实测结果（nongyu-app-plus #48）

> 核查日期 2026-11-09。**本文所有「通过 / 报错 / 字节数」都是本机真实构建结果，不是元数据推断。**
> 前序调研 `docs/research/retrofit-json-converter-routes.md`（#42）的「元数据可用」在本票被升级为「已跑通」；
> 该文 §4「待实测清单」里属于本票负责的条目，本文逐条给出结论。
>
> **纪律**：全程未 commit / push / reset（monorepo 根 `AGENTS.md` 明令 Agent 不得执行 git 写操作）。
> 改动只落在票面允许的 4 个文件上；三条转换器**串行**试，每次只留一条。

---

## 0 先说三件影响结论可信度的事

### 0.1 任务简报给的 `run.ps1` 在本仓库**不存在**

简报说「工程里已有 `run.ps1`，把这五个坑全封进去了」。实测：

```
$ find . -iname "run*.ps1" -not -path "./.git/*"
（无输出）
```

`run.ps1` 在本仓库、父目录、monorepo 根都没有。**等价流程存在于 `docs/testing.md`**，本文全程用它的做法：
JDK 17 + 干净 `GRADLE_USER_HOME` + 直接调已解压的 `gradle.bat`。没有因为找不到 `run.ps1` 而卡住。

### 0.2 工作区有**另一个 agent 并发改代码**，我改用隔离副本跑 sweep

第一次基线构建直接失败：

```
> Task :app:compileDebugKotlin FAILED
e: .../skin/SkinPack.kt:161:38 Unresolved reference 'SUPPORTED_SCHEMA_MAJOR'.
e: .../skin/SkinPack.kt:165:72 Unresolved reference 'SUPPORTED_SCHEMA_MAJOR'.
```

查 `git status` 发现 `M SkinPack.kt`，且文件 mtime 是 **14:22:30**（我这次会话正在跑的时候）。
即 #57 的 agent 正在同一工作区编辑，我的构建撞上了它**写了一半**的文件。重试一次后它写完了，编译通过。

**处置**：把工程复制到 `%TEMP%\nongyu-spike48` 做全部 sweep 实验，不碰主仓的构建产物、不与 #57 抢资源；
**最终验证仍回到主仓**（§6）。这既是纪律要求（不反复重试），也避免了我把别人的半成品误报成「依赖导致编译失败」。

### 0.3 体积对比的基线必须同源

第一次建的基线副本与 sweep 副本快照时点不同（中间 #57 又改了 5 个 skin 文件），直接比字节数会失真。
最终**用与 sweep 完全同源的代码**重建基线（`nongyu-base2`，由 sweep 副本回退而来），得到：

| | debug APK | release APK |
| --- | --- | --- |
| 真基线（0 改动，同源） | **11,866,464 B** | **1,114,715 B** |

这个基线可与下文所有 sweep 数字直接相减。

---

## 1 逐行说明改了什么（最终留在工作区的改动）

实际 `git diff --stat`：**3 个文件 +34 行，另加 1 个新文件（55 行）**。

### 1.1 `gradle/libs.versions.toml`（+20 行）

```toml
# [versions] 段，junit 之后新增
retrofit = "3.0.0"                    # 传输层
okhttp = "4.12.1"                     # 显式覆盖 Retrofit POM 自带的 4.12.0
kotlinxSerializationJson = "1.10.0"   # 转换器运行时（mv 2.3.0，与 Kotlin 2.3.0 同源）
coroutines = "1.9.0"                  # 显式声明，见 §4

# [libraries] 段新增 6 条
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# [plugins] 段新增 1 条
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### 1.2 `app/build.gradle.kts`（+11 行）

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)   // ← 新增，第 3 行
}

dependencies {
    // ...
    implementation(libs.retrofit)                              // ← 新增
    implementation(libs.okhttp)                                // ← 新增
    implementation(libs.retrofit.converter.kotlinx.serialization) // ← 新增
    implementation(libs.kotlinx.serialization.json)             // ← 新增
    implementation(libs.kotlinx.coroutines.core)                // ← 新增
    implementation(libs.kotlinx.coroutines.android)             // ← 新增
}
```

**没有**加 `buildscript { classpath(...) }`、**没有**应用 `org.jetbrains.kotlin.android`、**没有**碰 `gradle.properties`。

### 1.3 `app/src/main/AndroidManifest.xml`（+3 行）

```xml
<uses-permission android:name="android.permission.INTERNET" />
```
放在 `<manifest>` 下、`<application>` 之前，带一段注释说明为何必须手写（见 §5）。

### 1.4 `app/src/main/java/com/nongyu/appplus/net/RetrofitSpikeProbe.kt`（新文件，55 行）

包含 `@Serializable data class SpikePing` + `object RetrofitSpikeProbe`（`Json` 配置、`buildRetrofit()`、
`roundTrip()`）。**不被任何 UI / 业务代码引用**，因此 R8 会把它连同未引用的库一起裁掉 ——
这既保证「不影响运行」，也意味着它证明的是**编译期可用**，不是运行时可用（见 §7）。

---

## 2 五个问题的结论

### Q1 `assembleDebug` 是否通过？

**通过。** 三条转换器路线全部 `BUILD SUCCESSFUL`，且**没有任何 AAR 报 `minCompileSdk`**。

| sweep | 加了什么 | debug 结果 | debug APK 字节 |
| --- | --- | --- | --- |
| 基线 | 无 | ✅ | 11,866,464 |
| A | 仅 retrofit + okhttp（无转换器） | ✅ | 12,465,384 |
| B | + kotlinx.serialization 插件 + converter + json 1.10.0 | ✅ | 12,596,515 |
| C | + Moshi 反射（converter-moshi + moshi + moshi-kotlin） | ✅ | 13,580,262 |
| C' | + Moshi codegen（KSP 2.3.6 + moshi-kotlin-codegen） | ✅ | 12,531,011 |
| D | + 自写 `Converter.Factory` + 平台 `org.json` | ✅ | 12,465,443 |

`checkDebugAarMetadata` / `checkReleaseAarMetadata` 在每一次 sweep 都通过（任务正常执行、无告警），
证实 #42 §3 的结论「三条路线的新增 artifact 全是 jar，无 `minCompileSdk` 门槛」在**真实构建**下成立。

### Q2 `assembleRelease` 是否通过？R8 是否需要额外 keep？不加 keep 能否成功？

**全部通过；三条路线在不加任何 keep 的情况下 release 都能构建成功。**

但这里有一个**必须讲清的分裂结论**，否则会被误读成「不需要 keep」：

| 路线 | 不加 keep 能否构建 | 不加 keep 时 R8 干了什么 | 结论 |
| --- | --- | --- | --- |
| (a) kotlinx.serialization | ✅ 成功 | 把整个类**和整个序列化运行时一起裁掉**（`usage.txt` 里 okhttp3 318 类 / retrofit2 120 类 / kotlinx.serialization 364 类全部 REMOVED，`mapping.txt` 里 0 条保留） | 构建成功，但**代码根本没进包** |
| (b) Moshi 反射 | ✅ 成功 | 同上，DTO 与被引用的库整体裁掉 | 构建成功 ≠ 可用；真用起来**必须**手写 keep |
| (b') Moshi codegen | ✅ 成功 | KSP 自动生成 `META-INF/proguard/moshi-<类名>.pro`，**不需要**手写 | codegen 才是「零 keep」 |
| (c) 自写 org.json | ✅ 成功 | DTO 未被引用则裁掉；一旦被引用，**字段名是字符串字面量，混淆不影响正确性** | 真正零 keep（连 attributes 都不用加） |

**为什么「不加 keep 也成功」不能当成「不需要 keep」**：因为 `RetrofitSpikeProbe` 没有被任何业务代码引用，
R8 看不到它，就把整条链一起删了。这时的「成功」是**空洞的成功**。
一旦业务代码真的调用，keep 的问题立刻变成**运行时**问题（构建仍然会成功）。
这正是 `app/proguard-rules.pro` 注释里点名「JSON / 协议序列化（字段名被混淆后对不上）」那条警告的场景。

**R8 需要额外 keep 吗？按路线分**：

- **(a) serialization**：库自带规则够用。实测 R8 保留了 `kotlinx.serialization` 的 41 个类（规则生效）。
  **例外**：`@Serializable` 类带具名 `companion object` 时需按 README 另写 —— 本票的 `SpikePing` 用的是
  编译器插件自动生成的 companion，未触发例外，**该例外未实测**（见 §7）。
- **(b) Moshi 反射**：**必须手写 keep**（官方 README 原文要求），且 AGP 9 的
  `android.r8.strictFullModeForKeepRules=true` 使 `-keep class A` 不再隐含 keep 无参构造 ——
  要写成 `-keep class A { <init>(); }`。本票**没有**为反射路线写 keep（只测了「不写 keep 能否构建」），
  所以反射路线的 release **运行时**正确性未验证（见 §7）。
- **(c) 自写 org.json**：零 keep。

### Q3 三条转换器里哪几条真的能声明成功？

**三条全部能声明成功，且我拿到了比「构建通过」更硬的证据。**

#### (a) kotlinx.serialization —— 可用，且已取得编译器插件生效的**字节码级证据**

这是本票最关键的未知项（#42 §4 第 1 条）。三项独立证据：

1. **构建通过**。声明方式就是 `[plugins]` 一行 + 模块 `alias(...)`，**没有** `buildscript classpath`。
2. **`$$serializer` 真的生成了**，而且路径说明了它是**哪条编译路径**产出的：

   ```
   app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/nongyu/appplus/net/SpikePing$$serializer.class
   app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/nongyu/appplus/net/SpikePing$$serializer.class
   ```

   注意 `built_in_kotlinc` 这个目录名 —— 说明插件挂在 **AGP 9 内置 Kotlin** 的编译任务上，
   而不是旧的 `kotlin-android` 路径。这正面回答了「AGP 9 内置 Kotlin 下 serialization 插件是否真的生效」。
3. **`javap` 确认生成物形态正确**：

   ```
   public final class com.nongyu.appplus.net.SpikePing$$serializer
       implements kotlinx.serialization.internal.GeneratedSerializer<com.nongyu.appplus.net.SpikePing> {
     public final void serialize(kotlinx.serialization.encoding.Encoder, SpikePing);
     public final SpikePing deserialize(kotlinx.serialization.encoding.Decoder);
     public final KSerializer<?>[] childSerializers();
   }
   ```

→ #42 §2.1.2 用 AGP 字节码 + compose 插件对照 + Google 示例工程三条侧证推断的机制，**在此被本工程的构建结果证实**。
`SubpluginArtifact.version == null` 回落到 KGP 版本这条路径确实管用。

#### (b) Moshi —— 两条子路线都能编，但代价差一个数量级

- **反射子路线**：`converter-moshi` + `moshi` + `moshi-kotlin`，debug/release 均通过。
  代价是 `kotlin-reflect`：debug APK 从 12,465,384 涨到 13,580,262（**+1,114,878 B**）。
- **codegen 子路线（KSP）**：通过，且生成了 adapter 与自带的 proguard 规则：

  ```
  app/build/generated/ksp/debug/kotlin/com/nongyu/appplus/net/SpikePingJsonAdapter.kt
  app/build/generated/ksp/debug/resources/META-INF/proguard/moshi-com.nongyu.appplus.net.SpikePing.pro
  ```

  最后这个文件是官方「codegen 不需要你手写 keep」的机制来源，内容形如
  `-keepnames class ...SpikePing` / `-keep class ...SpikePingJsonAdapter { public <init>(Moshi); }`。

#### (c) 自写 `Converter.Factory` + 平台 `org.json` —— 可用，零新依赖

`org.json` 来自 `android.jar`（#42 §2.3.1 已实测存在），因此 release 增量与「只加传输层」**完全相同**（都是 1,156,590）。

#### 附：KSP 配 Kotlin 2.3.0 的实际情况（#42 §4 第 3、4 条）

- **版本解析没有踩坑**。简报特别警告「`google()` 白名单会让 `com.google.devtools.ksp` 去 Google Maven 查，
  那里停在 2021 年的 `1.5.30-1.0.0`，可能静默解析到旧版」。用 `buildEnvironment` 实测：

  ```
  +--- com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.6
  |    \--- com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.6
  |         +--- com.google.devtools.ksp:symbol-processing-api:2.3.6
  |         \--- com.google.devtools.ksp:symbol-processing-common-deps:2.3.6
  ```

  → 实际解析到 **2.3.6**，`mavenCentral()` 先于 Google 的陈旧条目命中。**该风险未发生**。
- **KSP 与 AGP 9.2.1 内置 Kotlin + Kotlin 2.3.0 协同工作**。关键日志：

  ```
  i: [ksp] loaded provider(s): [com.squareup.moshi.kotlin.codegen.ksp.JsonClassSymbolProcessorProvider]
  ```

  即 KSP 真的把处理器加载并跑了，产出了 `SpikePingJsonAdapter.kt`。**不是**「任务存在但 SKIPPED」那种假通过。

- **一个必须记的踩坑（不是失败，是耗时的坑）**：首次挂 KSP 时构建卡了 **超过 10 分钟且无任何输出**，
  Gradle daemon 的 CPU 增量只有 **0.09s / 12s**（完全空闲）。看起来像死锁，实际是首次冷启动在做
  大量 artifact transform 的结构缓存（`StructureTransformAction`），完成一次之后就正常了。
  **判断方法**：`Get-Process <daemon-pid> | ... .CPU` 两次采样看增量 —— 有增量是在干活，无增量才是真卡。

### Q4 release APK 体积增量（字节数）

基线 release = **1,114,715 B**（同源、0 改动）。

| 配置 | release APK | 增量 | 说明 |
| --- | --- | --- | --- |
| 基线 | 1,114,715 | — | |
| **仅传输层**（retrofit + okhttp） | **1,156,590** | **+41,875** | 见下方分解 |
| **+ serialization 转换器（但代码未被引用）** | 1,156,590 | +41,875 | R8 把 serialization 整条链裁掉了，**与仅传输层一字不差** |
| **+ serialization 转换器（代码被引用、保留）** | **1,189,358** | **+74,643** | 这才是「真用起来」的成本 |
| + Moshi 反射（代码保留） | 1,583,022 | **+468,307** | 主要来自 `kotlin-reflect` |
| + 自写 org.json（代码被引用） | 1,156,590 | +41,875 | 与仅传输层相同，零额外成本 |
| 最终交付态（serialization + INTERNET） | **1,156,622** | **+41,907** | 注：探针未被引用→整条链被裁，故≈仅传输层 |

**那 41,875 B 到底是什么？** 逐条目 diff（基线 vs 最终 APK）只有 2 个文件是新增的：

```
+ okhttp3/internal/publicsuffix/NOTICE          (218 B)
+ okhttp3/internal/publicsuffix/publicsuffixes.gz (41,394 B)
  另: classes.dex 反而 -1,073 B（net −41875 = +0，巧合使总数接近 publicsuffixes 本身）
```

即 **增量几乎全部是 OkHttp 自带的公共后缀表 `publicsuffixes.gz`**（OkHttp 用它做 cookie 域校验）。
R8 裁剪的是 class，**不会裁 jar 内的资源**，所以即便代码全部被删，这张表仍然会进包。
→ 这条对选型有意义：**OkHttp 的 41 KB 地板价与转换器选谁无关**，只要引 OkHttp 就要付。

**关于「+74,643 vs +41,875」的差异来源**：为测「真用起来」的体积，我临时往 `proguard-rules.pro`
加了 `-keep` 让探针及其依赖不被裁；这个临时规则**已还原**，不在最终改动里（见 §3 回退清单）。

### Q5 `INTERNET` 权限是否必须显式加？

**必须显式加。已用「APK 里到底有没有」实证，不是凭经验。**

1. 只声明 retrofit / okhttp、**不**写权限时，AGP 合并出的清单里**没有** `INTERNET`：

   ```
   app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
   → 只有 com.nongyu.appplus.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
   ```
2. 用 aapt2 直接读真实 release APK，同样没有：
   ```
   $ aapt2 dump permissions app-release.apk
   uses-permission: name='com.nongyu.appplus.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
   ```
3. 手写 `<uses-permission android:name="android.permission.INTERNET" />` 后，APK 里出现了：
   ```
   uses-permission: name='android.permission.INTERNET'
   ```
   代价 **+32 B**（1,156,590 → 1,156,622）。

→ **retrofit / okhttp 都是 AAR-free 的 jar，不携带任何 manifest，因此不会自动合并出 `INTERNET`。**
不加的话，`UnknownHostException`/`SecurityException` 会在运行时才炸。

---

## 3 回退清单（要恢复原状需要动哪几行）

改动一共 4 处，全部可直接删，**没有任何隐式副作用**（没动 `gradle.properties`、没加 `testOptions`、
没改 `settings.gradle.kts`、没改 `proguard-rules.pro`、没 bump 版本号）。

| # | 文件 | 要删什么 | 行数 |
| --- | --- | --- | --- |
| 1 | `gradle/libs.versions.toml` | `[versions]` 里 `retrofit` / `okhttp` / `kotlinxSerializationJson` / `coroutines` 四条（含注释） | 10 行 |
| 2 | `gradle/libs.versions.toml` | `[libraries]` 里 6 条（retrofit、okhttp、converter、json、coroutines × 2） | 7 行 |
| 3 | `gradle/libs.versions.toml` | `[plugins]` 里 `kotlin-serialization` 一条（含注释） | 3 行 |
| 4 | `app/build.gradle.kts` | `plugins {}` 里 `alias(libs.plugins.kotlin.serialization)` + 其注释 | 2 行 |
| 5 | `app/build.gradle.kts` | `dependencies {}` 里网络层 6 行 + 注释块 | 9 行 |
| 6 | `app/src/main/AndroidManifest.xml` | `<uses-permission ... INTERNET />` + 注释 | 4 行 |
| 7 | `app/src/main/java/com/nongyu/appplus/net/RetrofitSpikeProbe.kt` | **整个文件删除**（含 `net/` 目录，若为空） | 新文件 55 行 |

**只保留传输层、不要转换器**时：删上表 #3 / #4 / #5 中 converter 与 json 那 2 行（`retrofit-converter-kotlinx-serialization`、
`kotlinx-serialization-json` 及其 implementation），其余保留即可 —— 该组合已由 sweep A 实测通过。

**要换成 Moshi codegen** 时：换掉 #2/#3/#5 的转换器部分，另加 KSP 插件（`ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`，
版本 **2.3.6**）+ `ksp(libs.moshi.kotlin.codegen)`，**不要**引 `moshi-kotlin`（那是反射路线，会拖进 3.18 MB 的 `kotlin-reflect`）。
该组合已由 sweep C' 实测通过。

---

## 4 附带结论：coroutines 显式声明版本（#50 Q3）

**问题**：当前工程一处 coroutines 都没写，靠 Compose 传递。显式声明什么版本能与 Compose BOM 2025.12.00 兼容？

**实测事实**：

1. 改动前，`releaseRuntimeClasspath` 里 coroutines 就已经是 **1.9.0**，直接来源是
   `androidx.lifecycle:lifecycle-common:2.10.0`（不是 Compose BOM）：
   ```
   +--- androidx.lifecycle:lifecycle-common:2.10.0
   |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0
   +--- androidx.lifecycle:lifecycle-runtime-ktx:2.10.0
   |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
   ```
   另外可见两处被抬升：`kotlinx-coroutines-core:1.8.1 -> 1.9.0`、`kotlinx-coroutines-core:1.7.3 -> 1.9.0`。
2. 显式钉 `coroutines = "1.9.0"` 后：`assembleDebug` 通过，依赖树全部收敛到 1.9.0，
   **debug APK 字节数 12,465,384 —— 与「只加传输层」完全相同**，即零体积增量。

**结论：显式声明用 `1.9.0`。** 理由不是「猜它兼容」，而是「它本来就已经是实际解析到的版本」——
钉死等于把这个既有事实写进版本目录，避免将来某次传递依赖变更把版本悄悄抬走。
不建议用 1.10.x（未经本工程实测，会引入未知抬升）。

---

## 5 附带结论：`security-crypto:1.1.0` 能否一起编过（#43 选型）

**能。debug 与 release 均 `BUILD SUCCESSFUL`，无 `minCompileSdk` 报错。**

- 解析链：`androidx.security:security-crypto:1.1.0` → `com.google.crypto.tink:tink-android:1.8.0`。
- `checkDebugAarMetadata` / `checkReleaseAarMetadata` 均通过 —— 即 AAR 元数据里的 `minCompileSdk`
  没有超过本工程的 `compileSdk = 36`。
- release 体积看着只 +0（1,156,622 → 1,156,806，+184 B），**但这是假象**：R8 把未引用的
  `androidx.security` / `com.google.crypto.tink` 共 **1,432 个类**全部裁掉了（`usage.txt` 计数），
  `mapping.txt` 里保留数 = 0。**真实接入后的体积增量本票未测**（见 §7）。
- 该组合已在实验副本中验证，**未留在最终改动里**（#43 还没定是否采用）。

---

## 6 最终验证（在主仓原地跑，不是副本）

| 项 | 结果 |
| --- | --- |
| `:app:assembleDebug` | **BUILD SUCCESSFUL in 1m 35s**，APK **12,596,551 B** |
| `:app:assembleRelease` | **BUILD SUCCESSFUL in 1m 54s**，APK **1,156,622 B** |
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL**（既有纯 JVM 单测全过，未被新依赖破坏） |
| `SpikePing$$serializer` 生成 | ✅ debug + release 两个 `built_in_kotlinc` 目录都有 |
| 交付 APK 权限 | ✅ `uses-permission: name='android.permission.INTERNET'` |

> 注：主仓最终 debug APK（12,596,551）比 sweep B（12,596,515）多 36 B，
> 因为主仓多了 `INTERNET` 权限且 `SpikePing` 字段/KDoc 略有不同。属正常抖动，不影响任何结论。

---

## 7 仍未验证的部分（诚实标注）

1. **运行时正确性一次都没验**。全部结论止于「构建通过」。没有设备、没有真机 smoke、
   没有 `assembleRelease` 后装包运行。`RetrofitSpikeProbe` 故意不被引用，因此 release 包里它和
   整条依赖链都被 R8 删了 —— 这证明「编译期可用」，**不证明**「运行时可用」。
2. **serialization 在 R8 full mode 下 `Json.serializersModule.serializer(Type)` 是否仍可用**
   （#42 §4 第 7 条）：本票只验证了 `$$serializer` 被生成 + R8 保留规则生效，没跑端到端反序列化。
3. **`@Serializable` 类带具名 `companion object` 的 R8 例外**：未构造该形状，未测。
4. **Moshi 反射路线的 release 运行时正确性**：只测了「不写 keep 也能构建」，没测「写了 keep 之后运行时能不能真的反序列化」。
   AGP 9 `strictFullModeForKeepRules=true` 要求 `-keep class A { <init>(); }` 这一点来自官方 release notes，**未在本工程实测**。
5. **`security-crypto` 真实接入后的体积与运行时**：只验证了「能编过」，接入后 R8 不再裁它时的增量、以及 Tink 的 keep 需求未测。
6. **OkHttp 的其他运行时需求**：TLS/证书、`NetworkSecurityConfig`（targetSdk 36 的明文 HTTP 限制见
   `docs/research/cleartext-http-targetsdk36.md`）、代理/超时配置，一概未涉及。
7. **`converter-kotlinx-serialization` 的 `Factory` 与 3.0.0 的 `suspend` 可空 bug 交互**（#42 §2.0 第 4 条）：
   未写任何真实 `@GET` 接口，未触发。
8. **`kotlinx-serialization-json 1.10.0` 与 KSP / Moshi 混用的相互影响**：未测（本票按纪律串行、一次只留一条）。
9. **`INTERNET` 之外的权限**：若将来需要 `ACCESS_NETWORK_STATE`（网络状态判断）等，本票未涉及。

---

## 8 `git status` 与 `git diff --stat` 快照

```
$ git status --short
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M gradle/libs.versions.toml
?? app/src/main/java/com/nongyu/appplus/net/
?? docs/research/android-network-stack-candidates.md
?? docs/research/backend-plus-app-contract.md
?? docs/research/retrofit-json-converter-routes.md

$ git diff --stat
 app/build.gradle.kts             | 11 +++++++++++
 app/src/main/AndroidManifest.xml |  3 +++
 gradle/libs.versions.toml        | 20 ++++++++++++++++++++
 3 files changed, 34 insertions(+)
```

**说明**：
- 三个 `?? docs/research/*.md` 是 #42 等前序票留下的未跟踪文件，**不是本次改动**。
- `?? app/src/main/java/com/nongyu/appplus/net/` 是本票新增的最小验证类（`RetrofitSpikeProbe.kt`）。
- **未执行任何 git 写操作**（无 add / commit / push / reset / stash），符合 monorepo 根 `AGENTS.md` 的强制约束。

---

## 9 一句总结

**三条转换器路线在本工程全部「能声明、能构建」；真正的区分度不在「能不能编」，
而在三笔账：① 体积（serialization +74 KB（真用时）／ Moshi 反射 +458 KB（主要是 `kotlin-reflect`）／ 自写 org.json +0 额外）
② keep 负担（serialization 自带规则够用／Moshi 反射必须手写且要适配 AGP 9 的新 strict 默认／org.json 零 keep）
③ 可测性（org.json 在 `:app:testDebugUnitTest` 里是 stub，必须沿用本仓既有的「逻辑层 / JSON 薄适配层」分层）。
另：OkHttp 自带 41 KB 的 `publicsuffixes.gz` 是**地板价**，与转换器无关；`INTERNET` 权限必须手写，库不会替你加。**
