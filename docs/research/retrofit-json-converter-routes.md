# Retrofit JSON 转换器三条路线：硬约束下的事实边界（nongyu-app-plus #42）

> 核查日期 2026-11-09。**只列事实与其来源，不替维护者做选型。**
> 前提（用户已拍板，本文不重新论证）：传输层 = `com.squareup.retrofit2:retrofit:3.0.0` + `com.squareup.okhttp3:okhttp:4.12.1`。
> 仓内既有结论（`docs/research/android-network-stack-candidates.md`）本文直接沿用、不重复调研：Retrofit / OkHttp 4.x / Moshi / kotlinx.serialization **全部是 jar、无 AAR、无 `minCompileSdk` 门槛**；“Kotlin 2.3.0 编译器能读 mv=2.4.0 元数据（高 1 个 minor 可以）”；“无候选抬 Compose”。
>
> **纪律**：本次未改任何构建文件、未新增依赖、未跑 `gradlew`、未 commit。所有“构建能否通过”的判断都在 §4「待实测」；§3 的 AAR/POM 与 §2 的 `javap`/编译运行结果是**实测值**。

## 0 工程现状（已读，作为约束前提）

| 项 | 值 | 来源 |
| --- | --- | --- |
| AGP / Gradle / Kotlin | 9.2.1 / 9.4.1 / **2.3.0** | `gradle/libs.versions.toml` |
| compileSdk / minSdk | 36 / 26 | `app/build.gradle.kts` |
| Kotlin 插件 | **只有** `org.jetbrains.kotlin.plugin.compose`（注释明写「AGP 9 起内置 Kotlin，无需再应用 `org.jetbrains.kotlin.android`」） | `gradle/libs.versions.toml` `[plugins]` |
| release | `isMinifyEnabled=true` + `isShrinkResources=true` | `app/build.gradle.kts` |
| `testOptions {}` | **未配置**（即 `isReturnDefaultValues` 保持默认 `false`） | `app/build.gradle.kts` 全文无 `testOptions` |
| 既有纯 JVM 单测 | `:app:testDebugUnitTest`（#35），现只依赖 junit4 | `app/src/test/.../TestInfraSmokeTest.kt` |
| 既有 org.json 范式 | 逻辑层与 `JSONObject` 薄适配层分离；`SkinManifestParser.kt:91` 明写「这是唯一允许碰 `org.json` 的地方」 | 仓内源码 |
| `gradle.properties` | **无** `android.builtInKotlin` / `android.newDsl`（即全部走 AGP 9 默认值，内置 Kotlin 开启） | `gradle.properties` |

## 0.5 票里 7 个事实 → 本文定位

| 票里的编号 | 事实 | 本文位置 | 一句话结论 |
| --- | --- | --- | --- |
| 1 | AGP 9 内置 Kotlin 怎么声明 `org.jetbrains.kotlin.plugin.serialization` | §2.1.1 / §2.1.2 / §2.1.3 | **机制已取证（AGP 字节码 + compose 同路径对照 + Google 官方 AGP 9.3.2 工程）；本工程构建未验证** |
| 2 | KSP 与 Kotlin 2.3.0 的匹配（2.3.13 行不行） | §2.2.3 | **KSP≥2.3.1 才支持 AGP 9 内置 Kotlin；官方区间写到 KGP 2.3.x，故 2.3.13 理论可用，但 2.3.13 的 stdlib 要求是 2.4.0 → 保守选 2.3.6（stdlib=2.3.0）** |
| 3 | Moshi 反射在 R8 full mode 的官方 keep + `kotlin-reflect:1.8.21` 成本 | §2.2.2 / §2.2.4 | **反射必须自己写 keep（官方原文）；jar 3.18 MB；8.21 配 2.3.0 实测能跑；AGP 9 新默认 `strictFullModeForKeepRules=true` 会拆掉“keep 类顺带 keep 无参构造”** |
| 4 | Retrofit 3.0.0 `Converter.Factory` 扩展点 / suspend / `@Streaming` | §2.0 | **三个可覆写点在源码里读到；`suspend` 拿到的是 body 类型且有可空 TODO bug；`@Streaming` 只作用于 `ResponseBody` 返回类型，对自定义 converter 无效** |
| 5 | 三条路线的纯 JVM 单测可行性 | §2.1.4 / §2.2.2 / §2.3.2 | **(a)(b) 实测在纯 JVM 跑通；(c) 的 `org.json` 在单测里是 stub，必须分层或换 test 实现** |
| 6 | 传递依赖与是否抬 kotlin-stdlib / coroutines / androidx | §1 表「⑤」+ §2.1.4 / §2.2.1 | **(a) 完全零抬升（stdlib 正好 2.3.0）；(b) 只多 `kotlin-reflect`；(c) 零传递依赖；三条都不碰 coroutines / androidx** |
| 7 | 各路线 R8 下需要的官方 keep | §2.1（表格「⑥」）/ §2.2.4 / §2.3.3 | **(a) 自带规则、仅有具名 companion 的类例外；(b) 反射必须手写、codegen 不必；(c) 零 keep** |

## 1 结论速览

**一句话**：三条路线在**元数据层面全部可用**（全部是 jar，不带 `minCompileSdk`，不抬 Compose/lifecycle）；真正的差别不在“能不能编译”，而在 **① 编译器插件/KSP 在 AGP 9 内置 Kotlin 下的接入方式 ② 纯 JVM 单测里到底能不能跑 ③ R8 full mode 下谁需要你手写 keep**。

| 维度 | (a) kotlinx.serialization | (b) Moshi | (c) 自写 `Converter.Factory` + `org.json` |
| --- | --- | --- | --- |
| **① 新 artifact** | `converter-kotlinx-serialization:3.0.0`（jar）+ `kotlinx-serialization-json:1.10.0`（jar）+ 编译器插件 `org.jetbrains.kotlin.plugin.serialization:2.3.0` | `converter-moshi:3.0.0`（jar）+ `moshi:1.15.2`（jar）+ **反射**：`moshi-kotlin`（拖 `kotlin-reflect`）／**codegen**：`moshi-kotlin-codegen` + **KSP** | **零新 artifact**（`org.json` 来自 `android.jar`，实测存在） |
| **② `minCompileSdk` 门槛** | 无（全部 jar；`.module` 里无 android 变体，见 §3） | 无（同上） | 无 |
| **③ AGP 9 内置 Kotlin 下的接入** | **有强证据可行**：compose 插件走的是**同一条 `KotlinCompilerPluginSupportPlugin` 路径**，且 AGP 9.2.1 字节码显示版本号为空时回落到当前 KGP 版本（实测反编译，§2.1） | **反射**：不用编译器插件，**绕过整个问题**；**codegen**：必须 KSP，**KSP≥2.3.1 才支持 AGP 9 内置 Kotlin**（官方 issue 原文），Google 自家 AGP 9 skill 要求 **≥2.3.6** | **零插件**，不碰这条限制 |
| **④ 纯 JVM 单测可行性** | **实测跑通**（插件编译 + 明文 JSON 反序列化，无 `android.jar`） | **实测跑通**（`kotlin-reflect:1.8.21` 读 Kotlin 2.3.0 元数据成功）；codegen 产物是编译期生成的 Kotlin 代码，同样能跑 | **实测跑通**（手写 Factory 在纯 JVM 跑通）；但**若转换器内部调 `org.json`，单测必炸** —— 这是本仓既有痛点 |
| **⑤ 传递依赖 / 版本抬升** | `converter` 钉 `serialization-core-jvm:1.8.1` + `stdlib:2.1.21`；显式声明 `json:1.10.0` → core 1.10.0 + stdlib **2.3.0（= 工程，零抬升）**；**不碰 coroutines**；**不碰 androidx** | `moshi:1.15.2` → `okio-jvm:3.7.0` + `stdlib-jdk8:1.8.21`；`moshi-kotlin` → **`kotlin-reflect:1.8.21`**；**不碰 coroutines**；**不碰 androidx** | 零传递依赖 |
| **⑥ R8 官方 keep** | **自带** `kotlinx-serialization-common.pro` + `META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro`（实测存在）；**例外**：有具名 companion object 的 `@Serializable` 类要按 README 另写 | **自带** `moshi.pro`；**但反射序列化必须自己给 DTO 写 keep**（README 原文）；enum 需 `@JsonClass(generateAdapter=false)`；**codegen 无需** | **零 keep**（字段名是字符串字面量，不是反射；DTO 因为被代码引用而不会被裁掉） |
| **⑦ 新增成本（下载字节）** | 运行时：`serialization-json-jvm 279,729 B` + `core-jvm 403,584 B`；**构建期**：编译器插件 `562,093 B` + 插件实现 `6,310 B`（不进 APK） | 运行时：**`kotlin-reflect:1.8.21` = 3,180,995 B（官方 README 称 2.5 MiB）** + `moshi 162,258 B` + `moshi-kotlin 19,519 B`；**构建期**：`moshi-kotlin-codegen 1,682,882 B`（不进 APK） | **0 B**（`org.json` 来自 `android.jar`） |

**三条路线各一句**：

- **(a) kotlinx.serialization：可用，唯一未闭环的是“AGP 9 内置 Kotlin 下构建期是否真的挂上编译器插件”** —— 机制层面已取得 AGP 9.2.1 字节码级证据，且与已在跑的 compose 插件同路径；缺的是官方散文文档明写 + 一次真实构建。
- **(b) Moshi：反射路线可用且已验证跨版本反射 OK，但代价是 3.18 MB 的 `kotlin-reflect` + 必须手写 keep；codegen 路线卡在 KSP 与 AGP 9 内置 Kotlin 的兼容门槛上**（KSP ≥2.3.1 才支持，Google skill 要求 ≥2.3.6，最新 2.3.13 的 `symbol-processing-api` 又把 `kotlin-stdlib` 要求提到 2.4.0）。
- **(c) 自写 org.json Factory：可用，且是三条里唯一“零新依赖 + 零 R8 keep + 不碰编译器插件/KSP”的；唯一代价是 `org.json` 在 `:app:testDebugUnitTest` 里是 stub，转换器本身不可测** —— 必须沿用本仓既有的“逻辑层 / JSON 薄适配层”分层，否则单测覆盖不到解析决策。

## 2 逐路线小节

### 2.0 三条路线共用的前提：`Converter.Factory` 官方扩展点（事实 4）

Retrofit 3.0.0 的 `Converter.Factory` 源码（`retrofit-3.0.0-sources.jar` 内 `retrofit2/Converter.java`）只有三个可覆写点，**全部默认返回 `null`**：

```java
public @Nullable Converter<ResponseBody, ?> responseBodyConverter(
    Type type, Annotation[] annotations, Retrofit retrofit);
public @Nullable Converter<?, RequestBody> requestBodyConverter(
    Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit);
public @Nullable Converter<?, String> stringConverter(
    Type type, Annotation[] annotations, Retrofit retrofit);
```

配套的两个 `protected static` 工具方法：`getParameterUpperBound(int, ParameterizedType)`、`getRawType(Type)`。

来自源码的 5 条硬事实（都是 `Retrofit.java` / `HttpServiceMethod.java` / `BuiltInConverters.java` 原文，非二手转述）：

1. **工厂顺序不可协商**：`Retrofit.Builder.build()` 里固定 `[BuiltInConverters] + 用户工厂 + 默认工厂(OptionalConverterFactory)`。源码注释原文：“Add the built-in converter factory first. This prevents overriding its behavior”。→ 你的 Factory **永远拿不到** `ResponseBody` / `Void` / `Unit` / `RequestBody` 这四种返回类型，它们被 `BuiltInConverters` 先吃掉。
2. **`stringConverter` 有兜底**：没有任何工厂命中时，`Retrofit.stringConverter()` 退回 `BuiltInConverters.ToStringConverter`（就是 `value.toString()`）。→ 只写 `responseBodyConverter` 也能跑，但 `@Query`/`@Path`/`@Field` 的自定义类型会静默变成 `toString()`。
3. **找不到转换器时的报错形态**（实测）：`IllegalArgumentException: Could not locate ResponseBody converter for class java.lang.String.` 后面逐个列出 `Tried: * retrofit2.BuiltInConverters * <你的工厂> * retrofit2.OptionalConverterFactory`。**这是排查“为什么我的 JSON 没被解析”的第一手线索。**
4. **`suspend` 的行为**：`HttpServiceMethod.parseAnnotations` 取方法最后一个参数（`Continuation`）的实际类型参数，若它是 `Response<T>` 则**剥壳**得到 `T`、并标记 `continuationWantsResponse`；随后把 `adapterType` 构造成 `Call<responseType>`，所以 **`Converter.Factory` 收到的永远是“body 类型”，不是 `Response<T>`、也不是 `Continuation<T>`**。
   - `suspend` 方法返回 `Call<...>` → 直接 `IllegalArgumentException`：“Suspend functions should not return Call, as they already execute asynchronously.”
   - `suspend` 返回 `Unit` 走 `awaitUnit`（不解析 body）。
   - **可空返回类型没有被正确处理**：`HttpServiceMethod` 里 `boolean continuationBodyNullable = false;` 之后**再无赋值**（源码里留着 `// TODO figure out if type is nullable or not`），因此 `suspend fun x(): T?` 走的是**非空**分支 `KotlinExtensions.await()`；一旦服务端返回 `null` body，会抛 `KotlinNullPointerException: Response from <Service>.<method> was null but response body type was declared as non-null`。→ **声明 `T?` 也没用**，这是 3.0.0 的既有行为。
5. **`@Streaming` 语义**：`retrofit2/http/Streaming.java` 的 javadoc 原文限定了它的作用域——“Treat the response body on methods returning `ResponseBody` as is”。实际只在 `BuiltInConverters.responseBodyConverter` 里被检查（且仅当 `type == ResponseBody.class`）。**对自定义 Converter 无意义**：你的 Factory 想流式处理就自己在 `convert()` 里读 `ResponseBody.source()`，`@Streaming` 不会改变你被调用的方式、也不会替你关闭 body。
   - 相对地，`Call`/`Response` 语义在 3.0.0 与 2.x 一致：`CHANGELOG` 原文「The 3.x versions of Retrofit maintain forward binary-compatibility with the 2.x versions.」

**实测（一手）**：手写一个 `KvFactory : Converter.Factory()`（用 `k=v;k=v` 代替 JSON），在**纯 JVM、无 `android.jar`、无 `org.json`** 的 classpath 上编译并运行通过：

```
RESPONSE_CONVERTED={a=1, b=2}
REQUEST_BODY=a=1;b=2 contentType=text/plain; charset=utf-8
NO_FACTORY_FOR_STRING=java.lang.IllegalArgumentException: Could not locate ResponseBody converter for class java.lang.String.
  Tried:
   * retrofit2.BuiltInConverters
   * KvFactory
   * retrofit2.OptionalConverterFactory
```

> 附带一条对“纯 JVM 单测”有用的事实：`retrofit2.Platform` 有 `import static android.os.Build.VERSION.SDK_INT;`，但只在 `System.getProperty("java.vm.name") == "Dalvik"` 分支里读 `SDK_INT`；普通 JVM 走 `default` 分支。**所以 Retrofit 在纯 JVM 单测里不需要 `android.jar`**（上例就是证明）。这也解释了为什么 (a)/(b) 两条路线的 converter 都能在 `:app:testDebugUnitTest` 里跑。

### 2.1 路线 (a)：kotlinx.serialization（事实 1 的主战场）

#### 2.1.1 声明链：marker POM → 插件实现 → 编译器插件 artifact

四层全部逐一读过，链路完整：

| 层 | 坐标 | 一手内容 |
| --- | --- | --- |
| Gradle 插件 marker | `org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.3.0` | marker POM 只有一条依赖：→ `org.jetbrains.kotlin:kotlin-serialization:2.3.0` |
| 插件实现 jar | `org.jetbrains.kotlin:kotlin-serialization:2.3.0`（**6310 B**） | 内含 `META-INF/gradle-plugins/org.jetbrains.kotlin.plugin.serialization.properties` → `implementation-class=…SerializationGradleSubplugin`；同时注册了旧 id `kotlinx-serialization`（两个 `.properties` 指向同一类）；`META-INF/services/org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin` 也指向它 |
| 插件类 | `org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin` | `javap` 实测：`implements org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin`；`getCompilerPluginId()` 返回常量 `org.jetbrains.kotlinx.serialization`；`getPluginArtifact()` 返回 `SubpluginArtifact("org.jetbrains.kotlin", "kotlin-serialization-compiler-plugin-embeddable", null)` —— **version 传的是 `null`** |
| 编译器插件 | `org.jetbrains.kotlin:kotlin-serialization-compiler-plugin-embeddable:2.3.0`（jar **562093 B**，无 AAR） | 本机实测：把它作为 `-Xplugin` 交给 Kotlin 2.3.0 编译器，`@Serializable` 编译成功 |

#### 2.1.2 关键：AGP 9 内置 Kotlin 怎么把它挂上（**字节码级证据**）

`developer.android.com` 从本机仍不可达（`urllib` 全超时；`modsearch` 的 firecrawl 429），所以改用三条替代通道，结论一致：

**(i) AGP 9.2.1 的 `TaskManager` 字节码（本机 Gradle 缓存里的 `gradle-9.2.1.jar`）**

反编译 `com.android.build.gradle.internal.TaskManager` 得到 `addSubpluginOptionsForBuiltInKotlin(KotlinCompilation, TaskProvider)` 与内部静态方法 `addSubpluginOptionsForBuiltInKotlin$addMavenDependency(Project, String, SubpluginArtifact)`。后者的字节码是：

```
0: aload_2
1: invokevirtual  SubpluginArtifact.getVersion()Ljava/lang/String;
4: dup
5: ifnonnull 13                       // version != null → 直接用
8: pop
9: aload_0
10: invokestatic  KgpUtils.getKotlinAndroidPluginVersion(Project)   // version == null → 回落
```

即 **`SubpluginArtifact.version == null` 时，AGP 用“当前生效的 KGP 版本”去解析编译器插件 artifact**。再看 `KgpUtils.getKotlinAndroidPluginVersion` 的字节码：先 `plugins.findPlugin(KotlinBaseApiPlugin::class.java)`，再退回 `plugins.findPlugin("kotlin-android")`，再反射调用其 `getKotlinPluginVersion()` / `getPluginVersion()`。本工程是内置 Kotlin（`KotlinBaseApiPlugin` 由 AGP 施加），拿到的就是 **KGP 2.3.0**。

而同一条收集链路（同一个方法体）是「遍历 `project.plugins`，凡是 `instanceof KotlinCompilerPluginSupportPlugin` 就收进来，唯一排除 `getCompilerPluginId() == "org.jetbrains.kotlin.kapt3"`」。→ **serialization 插件（`KotlinCompilerPluginSupportPlugin`）与 compose 插件走的是同一条路，只是被收进来的对象不同。**

**(ii) 与已经在跑的 compose 插件对照（这是最强的经验证据）**

`javap` 实测 `compose-compiler-gradle-plugin:2.3.0` 的 `ComposeCompilerGradleSubplugin.getPluginArtifact()`，同样返回 `SubpluginArtifact("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", **null**)` —— **version 也是 `null`，靠的是同一个回落逻辑**。而这个插件在本工程**当前就在正常工作**（`app/build/intermediates/compose_mapping/release/compose-mapping.txt` 等产物存在、APK 已产出）。

→ 所以「version=null 能不能被 AGP 9 内置 Kotlin 正确解析」这个问题，在本工程**已经被 compose 插件事实上回答过一次**。

**(iii) 一手工程对照：Google 自己的 AGP 9 示例（`android/nowinandroid`，`main` 分支）**

- `gradle/libs.versions.toml`：`androidGradlePlugin = "9.3.2"`、`kotlin = "2.3.0"`、`[plugins] kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`
- 根 `build.gradle.kts`：`plugins { alias(libs.plugins.kotlin.serialization) apply false; … }`
- `core/network/build.gradle.kts`：`plugins { … id("kotlinx-serialization") }`，依赖 `implementation(libs.retrofit.kotlin.serialization)` + `implementation(libs.kotlinx.serialization.json)`
- `gradle.properties`：**没有** `android.builtInKotlin`，也**没有** `android.newDsl` → 即内置 Kotlin + 新 DSL 全默认开启
- 全文搜索 `"kotlin.android"` → **0 处命中**，即确实没应用 `org.jetbrains.kotlin.android`

→ **即：一个 Google 维护的、AGP 9.3.2 + Kotlin 2.3.0 + 内置 Kotlin 默认开启的工程，正在用「和本工程同样的目录声明方式」应用 serialization 插件。** 声明形式因此可以照抄：`[plugins]` 加一行 `{ id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`，模块级 `plugins { alias(...) }`。

**(iv) AGP 9 会把哪些插件判为“与内置 Kotlin 不兼容”**

`BuiltInKotlinServicesKt.failIfIncompatiblePluginsArePresent` 的字符串常量只有三个：`org.jetbrains.kotlin.android`、`org.jetbrains.kotlin.kapt`、`org.jetbrains.kotlin.multiplatform`。**serialization 不在其中** → 不会触发“请在 gradle.properties 里设 `android.builtInKotlin=false`”那条错误。

#### 2.1.3 版本对齐：不需要额外固定 KGP

- AGP 9.2.1 的 POM（Google Maven）对 `org.jetbrains.kotlin:kotlin-gradle-plugin` 是 **`2.2.10` / `runtime`**。
- 但 `compose-compiler-gradle-plugin:2.3.0.pom`（Maven Central，实测）把 `org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0` 列为 `compile` 依赖 → **本工程的 buildscript classpath 已经是 KGP 2.3.0**，与 `kotlin = "2.3.0"` 一致。
- `kotlin-serialization:2.3.0.pom` 只依赖 `kotlin-gradle-plugin-api:2.3.0`（并排除 `kotlin-stdlib` / `kotlin-reflect` 等），**不依赖 `kotlin-gradle-plugin`** → 加它不会额外拉第二个 KGP 版本，也不会引发 classpath 冲突。AGP 官方文档给的做法（「要升 KGP，在根 `buildscript { dependencies { classpath(...) } }` 里写」）本工程**不需要**。

#### 2.1.4 运行时库版本：钉 1.10.0

沿用既有结论（1.10.0 = mv 2.3.0 + stdlib 2.3.0，与 Kotlin 2.3.0 完全同源），本次补一条 POM 事实：`kotlinx-serialization-json-jvm:1.10.0` 依赖 `kotlinx-serialization-core-jvm:1.10.0` + `kotlin-stdlib:2.3.0`；而 `converter-kotlinx-serialization:3.0.0` 钉的是 `core-jvm:1.8.1` + `stdlib:2.1.21`。Gradle 取高 → **core 1.10.0 / stdlib 2.3.0（等于工程值，零抬升）**。

**实测（一手，即票里「事实 5：纯 JVM 单测可行性」的答案）**：`kotlin-compiler-embeddable:2.3.0` + `-Xplugin=kotlin-serialization-compiler-plugin-embeddable:2.3.0` 编译 `@Serializable data class`，在**纯 JVM**（无 `android.jar`、无 `org.json`、无 `kotlin-reflect`）下运行：

```
DECODED=SkinManifest(packId=abc, schemaVersion=3)
ENCODED={"packId":"abc","schemaVersion":3}
RUN_EXIT=0
```

并额外验证了 converter 实际用到的那个 API `Json.serializersModule.serializer(Type)`（`Factory.kt` 里就是 `format.serializersModule.serializer(type)`）：**在 classpath 上没有 `kotlin-reflect` 的情况下依然成功**，`Class.forName("kotlin.reflect.full.KClasses")` 为 `false`：

```
SERIALIZER_BY_TYPE=Foo
LIST_SERIALIZER=kotlin.collections.ArrayList
REFLECT_ON_CLASS_PATH=false
```

→ **路线 (a) 的运行时不需要 `kotlin-reflect`**（这是与 Moshi 反射路线的关键成本差）。

### 2.2 路线 (b)：Moshi

#### 2.2.1 两条子路线必须先分清

| 子路线 | 需要的额外库 | 是否需要编译器插件/KSP | 需要 `kotlin-reflect` |
| --- | --- | --- | --- |
| **反射**（`KotlinJsonAdapterFactory`） | `com.squareup.moshi:moshi-kotlin:1.15.2` | **否** | **是**（POM 实测 `kotlin-reflect:1.8.21`） |
| **codegen**（`@JsonClass(generateAdapter = true)`） | `com.squareup.moshi:moshi-kotlin-codegen:1.15.2`（`ksp` 配置） | **是，需要 KSP** | 否（README 原文：“Kotlin codegen has no additional runtime dependency”） |

`moshi-kotlin-codegen:1.15.2.jar` 实测注册了 `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` → 确实是 KSP 处理器。

#### 2.2.2 `kotlin-reflect:1.8.21` 的实际风险 —— **实测证伪了一个过度担忧**

官方 README 原文：“the reflection adapter transitively depends on the `kotlin-reflect` library which is a **2.5 MiB .jar file**”。实测 `kotlin-reflect-1.8.21.jar` = **3,180,995 B**。

真正的问题是“Kotlin 1.8 的反射库能不能读 Kotlin 2.3.0 编出来的类的元数据”。**本机实测（一手）：能。** 用 Kotlin 2.3.0 编译器编一个 `data class`，运行期分别挂 `kotlin-reflect:1.8.21` 与 `kotlin-reflect:2.3.0`：

```
--- run with kotlin-reflect 1.8.21 (what moshi-kotlin POM pulls) ---
REFLECT_JAR=...\kotlin-reflect-1.8.21.jar
KOTLIN_VERSION=2.3.0
DECODED=SkinManifest(packId=abc, schemaVersion=3)
ENCODED={"packId":"abc","schemaVersion":3}
RUN_EXIT_1821=0
--- run with kotlin-reflect 2.3.0 ---
RUN_EXIT_2300=0
```

→ **`moshi-kotlin` 的 1.8.21 反射库配 Kotlin 2.3.0 编译器产物，在这台机器上（JDK 25 / 纯 JVM）工作正常。** 这是“兼容性”层面的通过；**体积与 keep 成本仍然存在**（§2.2.4）。

同时验证了反向的失败形态（对排查有用）：把 `moshi-kotlin` 放 classpath 但**不放** `kotlin-reflect`：

```
kotlin.jvm.KotlinReflectionNotSupportedError: Kotlin reflection implementation is not found at runtime.
Make sure you have kotlin-reflect.jar in the classpath
    at …KotlinJsonAdapterFactory.create(KotlinJsonAdapter.kt:215)
    at retrofit2.converter.moshi.MoshiConverterFactory.responseBodyConverter(MoshiConverterFactory.java:106)
```

→ **漏依赖的报错发生在「第一次创建 adapter」时（即 Retrofit 解析服务方法时），不是编译期**。这条要写进排查手册。

#### 2.2.3 codegen 路线 = KSP 路线（事实 2）

Moshi codegen 绕不开 KSP，所以「Moshi codegen 能不能用」等价于「KSP 能不能在这个 AGP 9 内置 Kotlin 工程里用」。事实如下（全部一手）：

| 事实 | 来源 |
| --- | --- |
| KSP 从 **2.3.0 起与 Kotlin 版本解耦**：「KSP version is no longer tied to the Kotlin compiler version (moving away from the old `<kotlinversion>-<kspversion>` format)」；「KSP is no longer a compiler plugin… built on stable compiler APIs」 | `gh api repos/google/ksp/releases/tags/2.3.0` 的 release body 原文 |
| 因此**不存在** `2.3.0-<kspver>` 这种版本号；`2.3.0` 之后是 `2.3.1 … 2.3.13`（Maven Central `maven-metadata.xml` 实测，共 161 个版本，`<release>2.3.13</release>`） | 实测 |
| **KSP 2.3.1 才是第一个与 AGP 9.0 内置 Kotlin 兼容的版本**（KSP 维护者 hfmehmed 在 `google/ksp#2615` 2025-11-04 的评论原文：「Version 2.3.1 of KSP was released which is compatible with AGP 9.0 … Please do test it out」；此前 2025-10-02 他说「Yes, this is a temporary thing and we do plan to support AGP 9 and built in kotlin」） | `gh api` issue + comments |
| **官方版本区间（散文）**：「The latest KSP version, 2.3.10, supports the following dependency versions: KGP **2.2.10–2.3.x**；AGP **8.12.0 or later**；Gradle **8.13 or later**（For AGP 9.0 or later, use Gradle 9.x）；JDK 17+」 | `kotlinlang.org/docs/ksp-overview.html`「Requirements」原文 |
| **Google 自家 AGP 9 skill 的硬要求**：「If KSP (`com.google.devtools.ksp`) is used in the project, **ensure it is on version 2.3.6 or higher**」 | `android/skills` 仓库 `build-system/agp/agp-9-upgrade/SKILL.md`（`last-updated: 2026-09-18`） |
| KSP 2.3.10 修过一个与内置 Kotlin 直接相关的 bug：「Fix R-class resolution in KSP when **AGP 9 built-in Kotlin** is enabled (#2857)」；2.3.6 也改过「Improved the detection of built-in Kotlin by removing the reliance on `KotlinBaseApiPlugin`」 | `gh api repos/google/ksp/releases`（2.3.10 / 2.3.6 条目） |
| `symbol-processing-common-deps` 的 `kotlin-stdlib` 要求随版本上升：2.3.6 → **2.3.0**；2.3.7 → 2.3.20；2.3.13 → **2.4.0** | 逐版本读 POM，实测 |

**回答票里的问题「2.3.13 能不能配 Kotlin 2.3.0」**：按官方散文（KGP 2.2.10–2.3.x）与解耦后的版本语义，**能**；但两点残留风险必须明说：

1. 2.3.13 的 `symbol-processing-api` / `common-deps` 把 `kotlin-stdlib` 要求提到 **2.4.0**，高于工程的 2.3.0。KSP 把这两个 artifact 加到它自己的 resolvable configuration（`kspPluginClasspath` / `kspPluginClasspathNonEmbeddable`，源码 `KspSubplugin.kt` 实测），**理论上只影响构建 classpath、不进 app runtime classpath**；但「是否真的没泄漏到 `releaseRuntimeClasspath`」**待实测**。
2. **没有任何一手来源明文说「KSP 2.3.13 + Kotlin 2.3.0 已测」。** 官方区间只写到 2.3.10，Google skill 只要求 ≥2.3.6。→ 保守选择是 **2.3.6**（stdlib 要求正好 = 2.3.0，与工程同源；且满足 Google skill 的 ≥2.3.6 门槛），而不是 2.3.13。

**一处必须避开的坑**：本工程 `settings.gradle.kts` 的 `pluginManagement.repositories` 里，`google()` **带了 `includeGroupByRegex` 白名单**（只放行 `com.android.*` / `com.google.*` / `androidx.*`）。KSP 的 marker **确实存在于 Google Maven**，但那里的版本停在 `1.5.30-1.0.0`（`lastUpdated 20210907192627`）——一旦解析顺序让 `google()` 先命中，就会拿到 2021 年的旧版并报出误导性错误。实际解析会落到 `mavenCentral()`（`com.google.devtools.ksp` 命中 `com\.google.*` 白名单，所以 `google()` 会被查询）。**这是“看起来能解析、其实拿到旧版本”的高风险点，务必在 #48 里用 `./gradlew buildEnvironment` / `dependencies` 确认实际版本。**

#### 2.2.4 Moshi 的 R8 成本（事实 3、7 的交点）

- 库自带：`moshi-1.15.2.jar` 内 `META-INF/proguard/moshi.pro`（实测存在，内容含 `-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }`、`-keep @com.squareup.moshi.JsonQualifier @interface *`、enum 的 `values()` 等）。**R8 会自动读取，不需要你搬。**
- 官方对反射路径的硬要求（README「R8 / ProGuard」原文）：“Moshi contains minimally required rules for its own internals to work without need for consumers to embed their own. **However if you are using reflective serialization and R8 or ProGuard, you must add keep rules** in your proguard configuration file for your reflectively serialized classes.”
- enum 的官方要求（同节）：“Annotate enums with `@JsonClass(generateAdapter = false)` to prevent them from being removed/obfuscated from your code by R8/ProGuard.”
- `kotlin-reflect:1.8.21.jar` 自带 4 份规则（实测）：`META-INF/proguard/kotlin-reflect.pro`、`META-INF/com.android.tools/proguard/kotlin-reflect.pro`、`META-INF/com.android.tools/r8-from-1.6.0/kotlin-reflect.pro`、`r8-upto-1.6.0/…`。内容含 `-keep class kotlin.Metadata { *; }`、`-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod`、`-assumenosideeffects class …CacheByClassKt`。**同样自动生效。**
- **AGP 9 新增的坑（一手，官方 AGP 9.0 release notes 的 Gradle 属性默认值变更表）**：`android.r8.strictFullModeForKeepRules` 由 `false` → **`true`**，含义原文：“Allows R8 to keep less by not implicitly keeping the default constructor when a class is kept. That is, `-keep class A` no longer implies `-keep class A { <init>(); }`”。→ **从网上抄来的 Moshi 反射 keep 规则（以及任何手写 keep）如果依赖“keep 类就顺带 keep 无参构造器”，在 AGP 9 下会失效**。官方给的迁移建议原文：“Replace `-keep class A` with `-keep class A { <init>(); }`”。这条是本次**新发现**的、既有调研文件里没有的风险，正好落在“Moshi 反射必须手写 keep”这条路上。

### 2.3 路线 (c)：自写 `Converter.Factory`（基于平台 `org.json`）

#### 2.3.1 `org.json` 的可得性（一手实测）

- `C:\…\platforms\android-36\android.jar` 里 `org/json/` 共 6 个条目：`JSONObject`、`JSONStringer`、`JSONTokener`、`JSONException`、`JSONArray`（+ 包目录）。→ **运行时零依赖，`minSdk 26` 天然可用**，不需要 `org.json:json`。
- 所以本路线**不引入任何新 artifact**，`minCompileSdk` 问题不存在。

#### 2.3.2 为什么它在单测里“有代价但可控”

本仓既有结论：`org.json` 在 `:app:testDebugUnitTest` 里是 **stub**，一调就抛 `RuntimeException: Method put in org.json.JSONObject not mocked`。本次找到了这条行为的**一手实现证据**：AGP `builder-9.2.1.jar` 里 `com.android.builder.testing.MockableJarGenerator` 的字符串常量包含

```
method ' not mocked. See https://developer.android.com/r/studio-ui/build/not-mocked for details.
```

且该类有 `private final boolean returnDefaultValues` 字段与构造器 `MockableJarGenerator(boolean)`。对应的公开 DSL 在 `gradle-api-9.2.1.jar` 里实测存在：

```
public interface com.android.build.api.dsl.UnitTestOptions {
  public abstract boolean isReturnDefaultValues();
  public abstract void setReturnDefaultValues(boolean);
  public abstract boolean isIncludeAndroidResources();
  ...
}
```

→ **三条可选出路（都必须实测，见 §4）**：
1. **沿用本仓范式**：把“解析决策”放在不碰 `JSONObject` 的纯逻辑层（`ColorPayloadParser` / `SkinManifestParser` 已经是这个形状），JSON 薄适配层不测。**零构建改动，最稳。**
2. `testOptions.unitTests.isReturnDefaultValues = true`：mockable jar 改成返回默认值（`null` / `0` / `false`）而不是抛异常。**但“返回默认值”意味着测试拿到的是假数据，`JSONObject("…")` 会返回一个空壳，断言没有意义** —— 不建议当作“能测 org.json”的手段。
3. `testImplementation("org.json:json:…")` 或 `testImplementation("com.vaadin.external.google:android-json:…")`。许可实测：`org.json:json:20250107` = **Public Domain**；`com.vaadin.external.google:android-json:0.0.20131108.vaadin1` = **Apache-2.0**。**但“它和 mockable android.jar 的 classpath 顺序谁先”未验证**，标 §4 待实测。

#### 2.3.3 它的真实优势与真实代价

- **优势（全是本票其它两条路线要付的账，这条不用付）**：
  - 零编译器插件 → 完全绕开“AGP 9 内置 Kotlin 下怎么挂 kotlin 编译器插件”这个最大盲区。
  - 零 KSP → 完全绕开“KSP 与 Kotlin 2.3.0 匹配”。
  - 零第三方运行时 → 零传递依赖、零版本抬升、零许可新增。
  - **零 R8 keep**：字段名是代码里的字符串字面量（`obj.optString("packId")`），不依赖反射/注解，混淆不影响正确性；DTO 因被转换器代码直接引用而不会被裁。对照 `app/proguard-rules.pro` 里“JSON / 协议序列化（字段名被混淆后对不上）”那条警告，本路线**恰好不触发它**。
- **代价**：
  - 要自己写 `responseBodyConverter` + `requestBodyConverter`（+ 可能 `stringConverter`），每个 DTO 手写映射（无 codegen）。
  - 没有 `@Serializable` / `@JsonClass` 那类“类型安全 + 编译期校验”的保护，字段改名/漏字段只能靠单测。
  - 与既有 org.json stub 的摩擦（§2.3.2）。

## 3 `minCompileSdk` 实测表（新增 artifact）

方法同既有文件：Python `urllib` 直连 `repo1.maven.org` / `dl.google.com`，**在内存里**判 `.aar` 是否存在（`HEAD` 请求，不落盘）；再读 Gradle `.module` 元数据确认**没有** `available-at` 指向某个 android 变体（这一步比“`.aar` 404”更强，能排除 okhttp-5 那种 `okhttp → okhttp-android` 的隐蔽重定向）。

### 3.1 制品形态（实测）

| artifact | `.aar` | `.jar` | 结论 |
| --- | --- | --- | --- |
| `converter-kotlinx-serialization:3.0.0` | **404** | 200（10,765 B） | jar，无 `minCompileSdk` |
| `converter-moshi:3.0.0` | **404** | 200（6,312 B） | jar，无门槛 |
| `retrofit:3.0.0` | **404** | 200（131,218 B） | jar（沿用既有结论） |
| `okhttp:4.12.1` | **404** | 200（791,976 B） | jar（4.x 单 jar 通吃 JVM+Android，沿用既有结论） |
| `okio-jvm:3.7.0` | **404** | 200（360,630 B） | jar |
| `moshi:1.15.2` | **404** | 200（162,258 B） | jar |
| `moshi-kotlin:1.15.2` | **404** | 200（19,519 B） | jar |
| `moshi-kotlin-codegen:1.15.2` | **404** | 200（1,682,882 B） | jar（仅 `ksp` 配置，编译期） |
| `kotlin-reflect:1.8.21` | **404** | 200（**3,180,995 B**） | jar |
| `kotlin-serialization:2.3.0`（Gradle 插件） | **404** | 200（6,310 B） | jar（buildscript classpath） |
| `kotlin-serialization-compiler-plugin-embeddable:2.3.0` | **404** | 200（562,093 B） | jar（编译器插件） |
| `kotlinx-serialization-json-jvm:1.10.0` | **404** | 200（279,729 B） | jar |
| `kotlinx-serialization-core-jvm:1.10.0` | **404** | — | jar |
| `symbol-processing-api:2.3.6` / `2.3.13` | **404** | 200（122,384 B / —） | jar |
| `symbol-processing-gradle-plugin:2.3.6` / `2.3.13` | **404** | 200（184,889 B / 173,578 B） | jar |

### 3.2 Gradle module 元数据（关键：排除 android 变体重定向）

| module | 变体情况 | 是否有 android 变体 |
| --- | --- | --- |
| `retrofit:3.0.0` | 3 个变体（`apiElements` / `runtimeElements` / `sourcesElements`），全部 `org.gradle.jvm.environment = standard-jvm`，`libraryelements = jar` | **无** |
| `okhttp:4.12.1` | 4 个变体，全部 `standard-jvm` | **无**（对比：5.5.1 有 `androidApiElements-published` → `available-at: okhttp-android`） |
| `moshi:1.15.2` / `moshi-kotlin:1.15.2` | 各 2 个变体，`standard-jvm` | **无** |
| `converter-kotlinx-serialization:3.0.0` | 3 个变体，`standard-jvm` | **无** |
| `symbol-processing-api:2.3.13` | 2 个变体，`standard-jvm` | **无** |
| `okio:3.6.0` | `jvmApiElements-published` → `available-at: okio-jvm:3.6.0`；其余全是 ios/js/linux/macos/mingw/tvos/wasm/watches | **无 android 目标**（只有 `okio-jvm`） |
| `kotlinx-serialization-json:1.10.0` / `-core:1.10.0` | `jvmApiElements-published` → `available-at: …-jvm:1.10.0`；其余是 `androidNativeArm32/64`、`ios*`、`js`、`wasm*` 等 KMP 目标 | **无 android 目标**（`androidNative*` 是 Kotlin/Native，与 Android JVM 无关） |
| `kotlin-reflect:1.8.21` | **`.module` 404**（该版本只发 POM），只有 jar | **无** |

### 3.3 结论

**三条路线新增的 artifact 没有一个带 `minCompileSdk`/`minAGP` 门槛。** 唯一的门槛来源仍是既有结论里的 `okhttp-android` AAR，而本工程走 OkHttp **4.12.1（无 AAR）** 已经绕开它。→ **“三条路线 × compileSdk 36”这一维度全部安全，不构成选型区分度。**

## 4 待实测清单（**本次一律未验证，不要当成已验证结论**）

> 本次严格未改动任何构建文件、未新增依赖、未运行 `gradlew`。以下全部未验证，归 #48。

1. **serialization 插件在 AGP 9 内置 Kotlin 下是否真的挂上编译期**。§2.1.2 给的是 AGP 字节码 + compose 插件对照 + Google 示例工程三条侧证，**仍不是本工程的构建结果**。验证方式：加插件后跑 `./gradlew :app:assembleDebug`，再确认 `app/build/tmp/kotlin-classes/…` 里 `@Serializable` 类旁边出现了 `…$$serializer`。
2. **`org.jetbrains.kotlin.plugin.serialization` 与工程现有 KGP 2.3.0 是否零冲突**（推断零冲突，依据：compose 插件已把 KGP 拉到 2.3.0、serialization 插件只依赖 `kotlin-gradle-plugin-api`）。未跑 `buildEnvironment`。
3. **KSP 与 AGP 9.2.1 built-in Kotlin + Kotlin 2.3.0 的实际可用组合**（2.3.6 还是更高）。特别是 §2.2.3 结尾那条：`settings.gradle.kts` 的 `google()` 白名单会让 `com.google.devtools.ksp` 也去 Google Maven 查，而那里只有 2021 年的 `1.5.30-1.0.0`。
4. **KSP 2.3.7+/2.3.13 的 `kotlin-stdlib 2.3.20/2.4.0` 要求是否泄漏到 `releaseRuntimeClasspath`**（源码显示只加进 `kspPluginClasspath`，未实测）。
5. **`testImplementation("org.json:json:…")` 能否覆盖掉 mockable android.jar 里的 stub**（classpath 顺序问题未验证）。若不能，路线 (c) 就只能走“逻辑层分离”这一条。
6. **`:app:testDebugUnitTest` 里跑通三条路线的真实单测**（本次是在 `java -cp` 手工 classpath 上跑通的，不是通过 Gradle 的 unit test 任务；两者的 classpath 组成不同 —— Gradle 那条会额外带上 mockable `android.jar`）。
7. **`serialization 1.10.0` 的 `Json.serializersModule.serializer(Type)` 在 R8 full mode 后仍可用**（本次只验证了 debug/JVM 产物；R8 会裁 `$$serializer` 的 `Companion`，官方规则覆盖了默认 companion，未验证构建后端到端）。
8. **Moshi 反射路径在 R8 后的真实行为**：需要先写出 keep 规则（并按 §2.2.4 的 `strictFullModeForKeepRules=true` 补 `{ <init>(); }`），再 `assembleRelease` + 真机 smoke。
9. **APK 体积增量**：只有下载字节数（`kotlin-reflect` 3.18 MB / compiler plugin 562 KB / serialization-json 280 KB），**R8 后真实增量未测**。
10. **`kotlinx-coroutines` 的必要性**：Retrofit 3.0.0 的 `suspend` 支持在字节码层依赖 `kotlinx.coroutines`（`retrofit2.KotlinExtensions.await` 里 `new kotlinx/coroutines/CancellableContinuationImpl`），但 **Retrofit 的 POM 没有声明 coroutines**。本工程因 Compose runtime 1.10.0 已带 `coroutines-android:1.9.0`，预计满足；「若将来移除 Compose 依赖，`suspend` 会 `NoClassDefFoundError`」未验证。
11. **`android.r8.strictFullModeForKeepRules` 默认变 `true` 对本工程现有 keep 的影响**：现有 `app/proguard-rules.pro` 只有 `-keepattributes`（无 `-keep class`），预计无影响，未验证。
12. **`testOptions.unitTests.isReturnDefaultValues` 在 AGP 9.2.1 新 DSL 下的确切写法**（接口方法实测存在，但 `app/build.gradle.kts` 里未出现过任何 `testOptions`，写法未验证）。
13. **Moshi 官方最低 API 级别**：README 无明文（搜索 `API 21` / `minSdk` / `Java 8` 均 0 命中），本次仍未取到官方声明。

## 5 引用（每条断言的一手来源）

**Maven / Google Maven 制品（本次直接下载或 `HEAD`）**

- 转换器： [`converter-kotlinx-serialization-3.0.0.pom`](https://repo1.maven.org/maven2/com/squareup/retrofit2/converter-kotlinx-serialization/3.0.0/converter-kotlinx-serialization-3.0.0.pom) · [`…-sources.jar`](https://repo1.maven.org/maven2/com/squareup/retrofit2/converter-kotlinx-serialization/3.0.0/converter-kotlinx-serialization-3.0.0-sources.jar) · [`converter-moshi-3.0.0.pom`](https://repo1.maven.org/maven2/com/squareup/retrofit2/converter-moshi/3.0.0/converter-moshi-3.0.0.pom) · [`…-sources.jar`](https://repo1.maven.org/maven2/com/squareup/retrofit2/converter-moshi/3.0.0/converter-moshi-3.0.0-sources.jar)
- Retrofit 源码（`Converter.Factory` 签名、工厂顺序、suspend、`@Streaming`、`stringConverter` 兜底）： [`retrofit-3.0.0-sources.jar`](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0-sources.jar) · [`retrofit-3.0.0.pom`](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0.pom) · [`retrofit-3.0.0.module`](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0.module) · [`retrofit-3.0.0.jar`](https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/3.0.0/retrofit-3.0.0.jar)
- OkHttp 4.12.1（无 AAR、`.module` 无 android 变体）： [`okhttp-4.12.1.pom`](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.1/okhttp-4.12.1.pom) · [`okhttp-4.12.1.module`](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.1/okhttp-4.12.1.module)
- Moshi： [`moshi-1.15.2.pom`](https://repo1.maven.org/maven2/com/squareup/moshi/moshi/1.15.2/moshi-1.15.2.pom) · [`moshi-kotlin-1.15.2.pom`](https://repo1.maven.org/maven2/com/squareup/moshi/moshi-kotlin/1.15.2/moshi-kotlin-1.15.2.pom) · [`moshi-kotlin-codegen-1.15.2.pom`](https://repo1.maven.org/maven2/com/squareup/moshi/moshi-kotlin-codegen/1.15.2/moshi-kotlin-codegen-1.15.2.pom) · [`moshi-kotlin-codegen-1.15.2.jar`](https://repo1.maven.org/maven2/com/squareup/moshi/moshi-kotlin-codegen/1.15.2/moshi-kotlin-codegen-1.15.2.jar)（含 KSP `SymbolProcessorProvider` 注册）
- kotlinx.serialization： [`kotlinx-serialization-json-1.10.0.module`](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/1.10.0/kotlinx-serialization-json-1.10.0.module) · [`json-jvm-1.10.0.pom`](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.10.0/kotlinx-serialization-json-jvm-1.10.0.pom) · [`core-jvm-1.10.0.pom`](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.10.0/kotlinx-serialization-core-jvm-1.10.0.pom)
- serialization Gradle 插件链： [marker POM 2.3.0](https://repo1.maven.org/maven2/org/jetbrains/kotlin/plugin/serialization/org.jetbrains.kotlin.plugin.serialization.gradle.plugin/2.3.0/org.jetbrains.kotlin.plugin.serialization.gradle.plugin-2.3.0.pom) · [`kotlin-serialization-2.3.0.pom`](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-serialization/2.3.0/kotlin-serialization-2.3.0.pom) · [`kotlin-serialization-2.3.0.jar`](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-serialization/2.3.0/kotlin-serialization-2.3.0.jar) · [`kotlin-serialization-compiler-plugin-embeddable-2.3.0.jar`](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-serialization-compiler-plugin-embeddable/2.3.0/kotlin-serialization-compiler-plugin-embeddable-2.3.0.jar) · 对照 [`compose-compiler-gradle-plugin-2.3.0.pom`](https://repo1.maven.org/maven2/org/jetbrains/kotlin/compose-compiler-gradle-plugin/2.3.0/compose-compiler-gradle-plugin-2.3.0.pom)（钉 `kotlin-gradle-plugin:2.3.0`）
- kotlin-reflect 1.8.21： [`kotlin-reflect-1.8.21.jar`](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/1.8.21/kotlin-reflect-1.8.21.jar)（3,180,995 B；内含 4 份 `kotlin-reflect.pro`）
- KSP： [marker `maven-metadata.xml`（Central，161 个版本）](https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml) · [`2.3.0` marker](https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/2.3.0/com.google.devtools.ksp.gradle.plugin-2.3.0.pom) · [`2.3.13` marker](https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/2.3.13/com.google.devtools.ksp.gradle.plugin-2.3.13.pom) · [`common-deps-2.3.6.pom`](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-common-deps/2.3.6/symbol-processing-common-deps-2.3.6.pom) / [`2.3.7`](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-common-deps/2.3.7/symbol-processing-common-deps-2.3.7.pom) / [`2.3.10`](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-common-deps/2.3.10/symbol-processing-common-deps-2.3.10.pom) / [`2.3.13`](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-common-deps/2.3.13/symbol-processing-common-deps-2.3.13.pom)
- KSP 在 **Google Maven 的陈旧版本**： [`com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml`](https://dl.google.com/dl/android/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml)（`<release>1.5.30-1.0.0</release>`，`lastUpdated 20210907192627`）
- AGP 9.2.1（KGP 2.2.10 runtime 依赖）： [`gradle-9.2.1.pom`](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.2.1/gradle-9.2.1.pom)
- 许可： [`org.json:json:20250107.pom`](https://repo1.maven.org/maven2/org/json/json/20250107/json-20250107.pom)（Public Domain）· [`android-json:0.0.20131108.vaadin1.pom`](https://repo1.maven.org/maven2/com/vaadin/external/google/android-json/0.0.20131108.vaadin1/android-json-0.0.20131108.vaadin1.pom)（Apache-2.0）

**官方文档（经 `android/skills` 镜像取得；`developer.android.com` 原文不可达）**

- [`migrate-to-built-in-kotlin.md`](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/references/android/build/migrate-to-built-in-kotlin.md)（内置 Kotlin 的迁移步骤；`kotlin-android` 必删；kapt 不兼容）
- [`agp-9-0-0-release-notes.md`](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/references/android/build/releases/agp-9-0-0-release-notes.md)（**KGP 2.2.10 runtime 依赖 + 升/降 KGP 的官方写法**；`android.builtInKotlin` 默认 `true`；**`android.r8.strictFullModeForKeepRules` 默认 `false`→`true`**）
- [`agp-9-upgrade/SKILL.md`](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/SKILL.md)（**「KSP 必须 2.3.6 或更高」**）
- [`ksp-kapt.md`](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/references/ksp-kapt.md)（判断某个处理器是否支持 KSP：看 jar 里有没有 `services/com.google.devtools.ksp.processing.SymbolProcessorProvider`）
- [Google 官方 AGP 9.3.2 + Kotlin 2.3.0 + serialization 示例工程 `android/nowinandroid`](https://github.com/android/nowinandroid)： [`gradle/libs.versions.toml`](https://github.com/android/nowinandroid/blob/main/gradle/libs.versions.toml) · [`build.gradle.kts`](https://github.com/android/nowinandroid/blob/main/build.gradle.kts) · [`core/network/build.gradle.kts`](https://github.com/android/nowinandroid/blob/main/core/network/build.gradle.kts) · [`app/build.gradle.kts`](https://github.com/android/nowinandroid/blob/main/app/build.gradle.kts) · [`gradle.properties`](https://github.com/android/nowinandroid/blob/main/gradle.properties)（无 `builtInKotlin` / `newDsl`）

**Kotlin 官方文档**

- [KSP overview — Requirements（KGP 2.2.10–2.3.x / AGP 8.12+ / Gradle 8.13+；AGP 9+ 用 Gradle 9.x）](https://kotlinlang.org/docs/ksp-overview.html)
- [KSP quickstart（`id("com.google.devtools.ksp") version "2.3.10"`；Moshi 作为 KSP 处理器示例）](https://kotlinlang.org/docs/ksp-quickstart.html)
- [KGP API `KotlinGradleSubplugin.kt` 源码（`SubpluginArtifact(groupId, artifactId, version: String? = null)` 的文档：「The optional version… The default value is `null`. In this case, the version of the Kotlin Gradle plugin is used.」）](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin-api/2.3.0/kotlin-gradle-plugin-api-2.3.0-sources.jar)

**GitHub（`gh api` / raw）**

- [KSP `2.3.0` release body（版本解耦、不再是编译器插件、为何叫 2.3.0）](https://github.com/google/ksp/releases/tag/2.3.0)
- [KSP issue #2615「Regarding AGP 9 and built-in Kotlin」（维护者原话：2.3.1 起兼容 AGP 9.0）](https://github.com/google/ksp/issues/2615)
- [KSP releases（2.3.6 / 2.3.10 的内置 Kotlin 相关修复）](https://github.com/google/ksp/releases)
- [KSP `KspSubplugin.kt` 源码（把 `symbol-processing-api` 加进 `kspPluginClasspath`，不作为 app 依赖）](https://github.com/google/ksp/blob/main/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt) · [`agpUtils.kt`（`MINIMUM_SUPPORTED_AGP_VERSION = 8.12.0`；`isAgpBuiltInKotlinUsed`）](https://github.com/google/ksp/blob/main/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/utils/agpUtils.kt) · [`kgpUtils.kt`](https://github.com/google/ksp/blob/main/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/utils/kgpUtils.kt)
- [Moshi README（`moshi.pro` 自带规则够用但反射必须自己 keep；enum 需 `@JsonClass(generateAdapter=false)`；`kotlin-reflect` = 2.5 MiB；codegen 无额外运行时依赖）](https://github.com/square/moshi/blob/master/README.md)
- [kotlinx.serialization README（具名 companion object 的 R8 full mode 专用规则模板）](https://github.com/Kotlin/kotlinx.serialization/blob/master/README.md)
- [Retrofit CHANGELOG（3.0.0 = 升级 OkHttp 4.12；3.x 向前二进制兼容；2.12.0 引入 `withStreaming()`）](https://github.com/lysine-dev/retrofit/blob/master/CHANGELOG.md)

**本机一手实测（无外部链接，命令与结论见正文）**

- AGP 9.2.1 jar 字节码：`com.android.build.gradle.internal.TaskManager#addSubpluginOptionsForBuiltInKotlin`、`…$addMavenDependency`、`com.android.build.gradle.internal.utils.KgpUtils`、`com.android.build.gradle.internal.services.BuiltInKotlinServicesKt`、`com.android.builder.testing.MockableJarGenerator`
- `gradle-api-9.2.1.jar`：`com.android.build.api.dsl.UnitTestOptions`
- `kotlin-compiler-embeddable:2.3.0` + `-Xplugin=kotlin-serialization-compiler-plugin-embeddable:2.3.0` 的 5 组编译/运行实验（serialization 编译、纯 JVM 反序列化、`serializer(Type)` 无 reflect、`kotlin-reflect:1.8.21` 读 2.3.0 元数据、手写 `Converter.Factory` 端到端）
- `platforms/android-36/android.jar`：`org/json/` 6 个条目

**工程内文件（仅阅读，未修改）**

- `gradle/libs.versions.toml`（AGP 9.2.1 / Kotlin 2.3.0 / 只有 compose 插件）
- `app/build.gradle.kts`（compileSdk 36 / minSdk 26 / JDK 17 / release 开 R8 + 资源收缩 / **无 `testOptions`**）
- `gradle.properties`（**无** `android.builtInKotlin` / `android.newDsl`）
- `settings.gradle.kts`（`google()` 带 `includeGroupByRegex` 白名单）
- `app/proguard-rules.pro`（现状：只有 `-keepattributes`；注释里点名「JSON / 协议序列化（字段名被混淆后对不上）」是未来必须回来补 keep 的场景）
- `app/src/main/java/com/nongyu/appplus/skin/ColorPayloadParser.kt` / `SkinManifestParser.kt`（org.json 分层范式）
- `app/src/test/java/com/nongyu/appplus/TestInfraSmokeTest.kt`（`:app:testDebugUnitTest` 现状）
- `docs/research/android-network-stack-candidates.md`（本文沿用的既有结论来源）

## 6 核查通道说明（本机网络受限，这点很重要）

`developer.android.com` 与 `android-developers.googleblog.com` **从本机仍然完全不可达**：本次 `urllib`（带 UA）对两者的四个 URL 全部 `URLError: timed out`；`read_page`（modsearch）返回 `firecrawl 429 Too Many Requests`（`retry_after_seconds: 76721`）+ `local: Request failed`。**所以没有取到任何 `developer.android.com` 的原始页面。**

替代通道（按重要性排序）：

| 通道 | 用途 | 状态 |
| --- | --- | --- |
| **`raw.githubusercontent.com` 直连** | 读 `android/skills`（Google 维护的官方 AGP 技能仓库，内含 `migrate-to-built-in-kotlin.md`、`agp-9-0-0-release-notes.md` **全文镜像**，`last-updated: 2026-09-18`）、`android/nowinandroid`（AGP 9.3.2 真实工程）、`square/moshi`、`Kotlin/kotlinx.serialization`、`lysine-dev/retrofit`、`google/ksp` 的 README/CHANGELOG/源码 | **本次主通道，可用** |
| **`gh api`（`api.github.com`）** | `google/ksp` releases 与 issue #2615/#3053 的正文与评论、code search（`repos/.../git/trees?recursive=1`） | **可用**（`github.com` 网页直连会被 reset） |
| **`repo1.maven.org`（Python `urllib`，内存解包）** | POM / `maven-metadata.xml` / Gradle `.module` / jar 内 `META-INF`（proguard 规则、`gradle-plugins/*.properties`、`services/*`）/ `-sources.jar`（`Converter.java`、`Retrofit.java`、`HttpServiceMethod.java`、`KotlinExtensions.kt`、converter 的 `.kt`）/ `.aar` 存在性 `HEAD` | **可用** |
| **`dl.google.com/dl/android/maven2`** | AGP 9.2.1 POM（`kotlin-gradle-plugin:2.2.10` runtime 依赖）、KSP marker 在 Google Maven 的「陈旧版本」证据 | **可用** |
| **`kotlinlang.org`** | `ksp-overview.html` 的 KSP 版本区间硬表（KGP 2.2.10–2.3.x / AGP 8.12+ / Gradle 8.13+）、`ksp-quickstart.html` | **可用** |
| **本机 Gradle 缓存 + `javap`（JDK 25）** | **AGP 9.2.1 与 KGP 2.3.0 的实际字节码**：`TaskManager.addSubpluginOptionsForBuiltInKotlin$addMavenDependency` 的 version-null 回落、`KgpUtils.getKotlinAndroidPluginVersion`、`BuiltInKotlinServicesKt` 的不兼容插件名单、`KgpUtils` 的插件 id 常量、`MockableJarGenerator` 的 “not mocked” 常量、`gradle-api-9.2.1` 的 `UnitTestOptions` 接口、`kotlin-serialization` / `compose-compiler-gradle-plugin` 的 `getPluginArtifact()` 常量池 | **可用，本次最有价值的一手证据来源** |
| **`kotlin-compiler-embeddable:2.3.0` + `K2JVMCompiler` + `-Xplugin`（本地 JDK 25 执行）** | 五组实测：① serialization 插件编 `@Serializable` ② 纯 JVM 反序列化 ③ `serializer(Type)` 无 `kotlin-reflect` ④ `kotlin-reflect:1.8.21` 读 2.3.0 元数据 ⑤ 手写 `Converter.Factory` 端到端 | **可用，这是票里「事实 3 / 事实 5」的答案来源** |
| **`developer.android.com` / Android Developers Blog** | 本文涉及的 AGP 9 官方文档 | **不可达**；相关内容改由 `android/skills` 的官方镜像取证（该仓库由 Google 生成，文本与官方页面一致） |

**同一结论的交叉验证方式**（避免单一通道误判）：AGP 9 内置 Kotlin 的插件接入，分别用了 ①AGP jar 字节码 ②compose 插件的行为对照（本工程已跑通）③Google 官方示例工程 `gradle/libs.versions.toml` 三条独立来源，结论一致。

