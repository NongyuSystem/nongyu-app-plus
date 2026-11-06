# 不降 AGP 能否让 IDEA 2025.3.2 sync / 跑起来（AGP 9.2.1）

> 只做事实核查，不改业务代码、不跑 git、不装东西。核实时间 2026-11-06。
> 背景与版本、IDE、已有三份调研、§2.4–2.6 的结论均按背景接受，不重复验证。
> 一手来源：developer.android.com、JetBrains 官方支持帖 / YouTrack / Marketplace API、kotlinlang.org、AGP release notes。

---

## 1. 结论（≤8 行）

- **Q1**：`android.builtInKotlin=false` + `android.newDsl=false` **不能**让 IDEA 读懂 AGP 9.2.1。它确实会恢复旧 DSL 实现类，但 IDE sync 读的是 Gradle Tooling API 的 `AndroidProject` 模型，不是 DSL 类；不改模型，故对 sync 无帮助。
- **Q2**：`gradle.ide.support.future.agp.versions` 是「未来 AGP 版本同步开关」，官方口径是让 IDE 接受**声明兼容**的更新 AGP；在 JetBrains 官方支持帖里提问者照做**仍报同样的错**，官方回「只能等新 tooling 合进 IDEA」。
- **Q3**：在 253 build 上装 `org.jetbrains.android`（Narwhal FD 基座，AGP ≤8.12）sync AGP 9.2.1，报错形态是 Tooling API 模型方法不匹配（典型 `Unsupported method: AndroidArtifact.getPrivacySandboxSdkInfo()`）；JetBrains 明确「IDEA 的 Android 插件尚不支持最新 AGP」。跨 build 强装 263 插件被 `since/until-build` 硬挡，官方不允许。
- **Q4**：不装 IDE、只看界面，除「命令行 `installDebug` + 蓝叠/真机」外，另有**官方命令行产 `@Preview` 截图**（`com.android.compose.screenshot` 插件的 `update{Debug}ScreenshotTest` / `validate…` Gradle 任务，或 Android CLI `android studio render-compose-preview`）；但都要额外装插件/依赖或连一个跑着的 Android Studio，且是**静态 PNG，不是实时界面**。独立无 IDE 的 layoutlib 渲染器、把官方 Compose Hot Reload 用到 Android Compose，都**不可行**。
- 一句话：**不降 AGP 让这台 IDEA「sync 通过 / IDE 里 Run」目前无解；但「在电脑上看界面」不止蓝叠一条，还可加一条官方截图任务（非实时）。**

---

## 2. Q1 判定（最重要）：flag 改的是 DSL，sync 读的是模型

### 2.1 `android.newDsl=false` 到底改什么

AGP 9.0.1 release notes 原文（[developer.android.com/r/tools/new-dsl](https://developer.android.com/r/tools/new-dsl)，另 [AGP 9.0 release notes 镜像](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/references/android/build/releases/agp-9-0-0-release-notes.md)）：

> "AGP 9.0 uses our new DSL interfaces exclusively, and the implementations have changed to new types that are fully hidden."
> "If you are blocked by incompatible third-party plugins, you can opt out and **get back the old implementations for the DSL, as well as the old variant API**."

所以 **确实会恢复旧 DSL 实现类**（`BaseExtension`、旧 `applicationVariants` 等），不是纯兼容开关。release notes 给出的典型报错也印证这是**配置期 / build script 层**的问题：

```
java.lang.ClassCastException: class …internal.dsl.ApplicationExtensionImpl$AgpDecorated_Decorated
cannot be cast to class com.android.build.gradle.BaseExtension
```

release notes 里 `android.newDsl` 那一行的「Function」写的是「Use the new DSL interfaces, **without exposing the legacy implementations of the `android` block**」——作用域明确限定在 **`android` block 的实现类 / 构建脚本与第三方 Gradle 插件可见的 API**。

### 2.2 `android.builtInKotlin=false` 与 `newDsl=false` 的绑定关系

[Migrate to built-in Kotlin（官方镜像全文）](https://github.com/android/skills/blob/main/build/agp/agp-9-upgrade/references/android/build/migrate-to-built-in-kotlin.md) 原文：

> "If you are unable to migrate … set `android.builtInKotlin=false` … **Note: You also need to set `android.newDsl=false` to opt out of the new DSL because the `kotlin-android` plugin is not compatible with it.**"

即这两个 flag 是**一对**：目的是「让 `kotlin-android` 插件能在 AGP 9 下继续工作」。JetBrains 官方博客也写明这是**临时**退出、AGP 10.0 会移除（[Update your Kotlin projects for AGP 9.0](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9)）。它与 IDE 兼容性无关。

### 2.3 sync 读模型，不读 DSL 类（关键判断）

IDEA 的 Android 插件做 Gradle sync 时，走的是 **Gradle Tooling API**：AGP 通过 **model builder** 注册并产出 `com.android.builder.model.AndroidProject` 等模型对象，IDE 通过 Tooling API 反序列化这些模型，而不是去 `extensions.getByType(BaseExtension::class)` 读 DSL 实现类。

依据：

- AGP 侧 [ModelBuilder.java（AOSP platform/tools/base）](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/ide/ModelBuilder.java)：`canBuild()` 明确按模型名匹配 `AndroidProject.class.getName()` / `GlobalLibraryMap` / `Variant` / `ProjectSyncIssues`；`buildAndroidProject()` 里用 `SyncOptions.buildModelOnlyVersion(...)`、`StudioVersions.verifyIDEIsNotOld(...)`——这是**为 IDE 单独构建的模型通道**，`android.newDsl` 不在其分支里。
- [Understanding the Sync Process](https://rogerhu.github.io/studying-android-studio-internals/Sync-Process.html)：Android Studio sync 通过 Tooling API 拿「previously registered models」，命令行带 `-Pandroid.injected.build.model.only=true` 等注入参数，走 `ExtraModelBuilder` / `ModelBuilderService`，并非解析 `.gradle` 或读 DSL 类。
- 反面实锤：真实 IDEA↔AGP 9 报错是
  `Unsupported method: AndroidArtifact.getPrivacySandboxSdkInfo()`。
  该文案在 [StackOverflow 同类报错](https://stackoverflow.com/questions/61981230/org-gradle-tooling-model-unsupportedmethodexception-unsupported-method-android) 里被标为 `org.gradle.tooling.model.UnsupportedMethodException`（`ProtocolToModelAdapter$InvocationHandlerImpl.invoke`）——是 **Tooling API 模型接口方法不匹配**，不是 `ClassCastException: …BaseExtension`。`AndroidArtifact` 是 IDE sync 的模型接口，不是 DSL 类。这正说明 AGP 9 卡住 IDEA 的地方是**模型层**，而 `newDsl=false` 只动 DSL 实现类层。

> **判断**：`android.newDsl=false` 对「sync 读模型」无用。即便它恢复了 `BaseExtension`，IDE 侧报的 `getPrivacySandboxSdkInfo()` 方法不匹配依旧存在；两者是不同层的对象。

### 2.4 Q1 结论

这两个 flag 是「让 `kotlin-android` 能在 AGP 9 下工作」的**构建侧迁移逃生开关**，恢复的是旧 **DSL / variant API 实现类**；IDEA Android 插件 sync 依赖的是 **AGP model builder 产出的 Tooling API 模型**。二者不同层，所以：

> **设这两个 flag 不可能让 IDEA 2025.3.2 的 Android 插件 sync 通过 AGP 9.2.1。**

（补充：工程 `[plugins]` 里本就没有 `kotlin-android`，opt-out 反而要把它加回来，纯属倒退，与 IDE 问题无关。）

---

## 3. Q2：`gradle.ide.support.future.agp.versions` 的确切状态

原文来自 [JetBrains 官方支持帖 34072502627218（已在本会话浏览器读到全文）](https://intellij-support.jetbrains.com/hc/en-us/community/posts/34072502627218-Android-project-fails-to-build-works-fine-in-Android-Studio-Unsupported-method-AndroidArtifact-getPrivacySandboxSdkInfo)：

**确切名字 / 值 / 位置**（官方 Serge Baranov 回复逐步给出）：
1. `Help | Edit Custom Properties...` → 打开的文件里加 `idea.is.internal=true` → 重启；
2. `Ctrl+Shift+A` → `Edit Studio Flags` → 找到 `gradle.ide.support.future.agp.versions`，**设为 true**；
3. 重新 sync。
（也可直接写进 `idea.properties`：`gradle.ide.support.future.agp.versions=true`，见 [JetBrains/android StudioFlags.java 转述](https://blog.jonathan.lermitage.biz/things-to-do-after-installing-intellij-edit-your-idea-properties) 与 [Metro 贡献文档](https://zacsweers.github.io/metro/1.3.2/contributing)。注意 `idea.is.internal` 是开启内部模式以显示 Studio Flags 编辑器，不是 flag 本身。）

**官方是否说它只是「警告抑制」**：措辞需精确，不能夸大。
- 官方支持帖里 Serge 把它描述为「a hidden flag that **lets IDEA sync with unsupported AGP versions**」，并称「should allow the project to sync and build，只是部分 Android 专属功能可能不完美」——**不是**明确说「仅警告抑制」。
- 但 StudioFlags 源码的转述口径是：「Support opening projects that use future AGPs … **if that version of AGP declares that it is compatible**」（[来源链接](https://github.com/JetBrains/android/blob/master/android-common/src/com/android/tools/idea/flags/StudioFlags.java#L823-L828)）。即它绕过的是 IDE 侧「最新受支持 AGP 版本」的**版本检查 / 拦截**，并**不能**让旧插件代码理解新模型接口。
- **未验证**：我无法直接抓到 StudioFlags.java 源码正文（GitHub 与 googlesource 直连在本环境超时/404），因此「官方明确写『只用于警告抑制、不保证读懂新 DSL』」这一句**没有一手证据**，只能确认官网口径是「按 AGP 自声明兼容来放行版本检查」。请勿当成定论。

**有没有人成功**：
- 该支持帖提问者（IDEA 2025.3.3 Ultimate）明确回「**Didn't help unfortunately, I get the same error**」，Serge 回「只能等更新 tooling 合进 IDEA」，并确认「IDEA 2026.1 + Android 插件最新支持仅到 **AGP 9.0.0-alpha06**」。
- [Kotlinlang Slack 讨论（AGP 9.1.0）](https://slack-chats.kotlinlang.org/t/33182560/i-recently-upgraded-to-agp-9-1-0-in-my-kotlin-multiplatform-)：用户「followed this workaround but **it does not work, even with IDEA 2026.1 EAP**」，同样的 `getPrivacySandboxSdkInfo()` 报错。
- 反向证据（**非 JetBrains 官方、且看起来是较旧 AGP**）：[florisboard PR #3224](https://github.com/florisboard/florisboard/commit/d3a7a2e186c63a028e2da3abad88ac7f2a393cd7) 注释写「若用 IDEA 必须开 Future AGP Versions，否则 AGP 9.0.0 不工作」，指向 [IDEA-348937 的 comment 27-11721710](https://youtrack.jetbrains.com/issue/IDEA-348937/2024.1-Beta-missing-option-to-enable-sync-with-future-AGP-versions#focus=Comments-27-11721710.0-0)。**未验证**该评论具体内容（YouTrack 动态渲染读不到）。综合看：对**模型接口已变**的 AGP 9.1/9.2，无可靠成功报告；对 9.0 早期版本有社区称可用，但无一手复现。

相关跟踪单：[IDEA-385007（AGP 9 compatibility）](https://youtrack.jetbrains.com/issue/IDEA-385007)、[IDEA-322286（Enable IntelliJ IDEA to work with unsupported versions of AGP）](https://youtrack.jetbrains.com/issue/IDEA-322286)。两单状态**未验证**（YouTrack 页面动态渲染读不到正文）。

---

## 4. Q3：装插件这条路

### 4.1 装了 `org.jetbrains.android`（22989）之后 sync 的报错形态

- 本机 253 build 能装到的版本，按 [Marketplace updates API](https://plugins.jetbrains.com/api/plugins/22989/updates?size=100) 的 `since/until` 与 `compatibleVersions`：
  - `253.33813.99`：`since 253.33813 — until 253.*`，`IDEA: 2025.3.6–2025.3.6.2`，notes「**Android Studio Narwhal Feature Drop STABLE**」；
  - `263.7307.200`：`IDEA_PRO: 2026.3`、notes「Android Studio **Quail 2**」，`modules` 含 `intellij.libraries.layoutlib`。
  - 背景里 §2.4 列的 `253.30387.90` 同样指向 Narwhal FD 基座（AGP ≤8.12）。本次 API 拉取的当前页未出现该具体版本号，但在 `253.*` 段内、基座一致，结论不变。
- 报错形态（一手，来自上面那篇 [JetBrains 支持帖](https://intellij-support.jetbrains.com/hc/en-us/community/posts/34072502627218-Android-project-fails-to-build-works-fine-in-Android-Studio-Unsupported-method-AndroidArtifact-getPrivacySandboxSdkInfo)，IDEA 2025.3.3 Ultimate / 新 AGP）：

```
Multiple build operations failed.
Unsupported method: AndroidArtifact.getPrivacySandboxSdkInfo().
The version of Gradle you connect to does not support that method.
To resolve the problem you can change/upgrade the target version of Gradle you connect to.
Alternatively, you can ignore this exception and read other information from the model.
```

注意官方在该帖里把「升级 Gradle」列为建议是**误导性模板文案**（错误其实来自 IDE 侧模型接口过旧），Serge 自己也定性为「IDEA Android 插件尚不支持最新 AGP」。

- **明确的「不支持」声明**：
  - 同帖 Serge Baranov：「IntelliJ IDEA's Android plugin **does not yet support the newest versions of the Android Gradle Plugin (AGP)**」；Aleksandr Nagovitsyn：「IDEA 2026.1 + Android 插件最新支持仅到 **AGP 9.0.0-alpha06**」。
  - JetBrains 官方 [Kotlin AGP9 迁移版本矩阵](https://github.com/Kotlin/kotlin-agent-skills/blob/main/skills/kotlin-tooling-agp9-migration/references/VERSION-MATRIX.md)：`IntelliJ IDEA | Not supported | Does not support AGP 9.0 as of 2026.1, use Android Studio instead`。
  - [AS↔AGP 兼容表](https://developer.android.com/build/releases/about-agp)：Narwhal Feature Drop / 2025.1.2 上限 AGP 8.12；AGP 9.2 需 Quail 1（2026.1.1）或 Panda 4。

### 4.2 能否「只装 Gradle model 部分」/ 跨 build 强装 263 插件

- **不能只装 model 部分**：Android 插件的 sync 模型支持代码与 IDE 版本、AGP 版本是配套的整包（Narwhal FD 基座 ⇄ 8.12）。Marketplace API 里 263 插件的 `modules` 含 `intellij.libraries.layoutlib` 等，是整包依赖模块，没有「单独抽 model 部分」的官方分发物。
- **跨 build 装不允许**：插件 `plugin.xml` 的 `<idea-version since-build/until-build>` 由 IDE 强制校验，不匹配则报 `Plugin … can't be loaded because: since-build or until-build don't match this product's build number`（[JetBrains 支持帖](https://intellij-support.jetbrains.com/hc/en-us/community/posts/206420259-Using-existing-plugins-in-EAP-versions)），磁盘安装同样被拒（`plugin … is not compatible with this installation`，[StackOverflow](https://stackoverflow.com/questions/53804804/installing-python-plugin-with-webstorm)）。把 `263.7307.200`（`since 263.7307`）塞进 `253.30387.90` 会直接被挡；且即便绕过，263 插件还依赖 253 IDE 没有的平台 API，属**未验证且官方不支持**的做法。
- **未验证**：是否有社区 hack（改 `plugin.xml` / `idea.properties` 放宽 build 检查）能强行加载并让 sync 成功——没有任何可靠一手报告，不写死。

---

## 5. Q4：IDE 之外「不降 AGP 也能看界面」的路

### 5.1 独立 Layoutlib / Compose Preview 渲染器（不装 Android 插件）

- `@Preview` 的渲染器 layoutlib 是 **Android 插件自带的模块**（Marketplace API `modules: ["intellij.libraries.layoutlib", …]`）。没有 Google/JetBrains 提供的「独立安装、供 IDEA 裸用」的官方 layoutlib 渲染器制品。
- **未验证**：是否存在可靠的第三方把 layoutlib 抽出来给 IDEA 裸渲染 `@Preview`。Paparazzi 直接跑 layoutlib，但它是 Gradle 截图测试库（见 §5.2），不是 IDE 内实时预览。
- 结论：无官方独立方案；这条路不成立。

### 5.2 Gradle 任务直接产 `@Preview` 截图（官方支持，命令行可用）

这是本次相对背景结论的**实质补充**：官方确有「不靠 IDE、命令行出 `@Preview` 图」的方案。

- **Compose Preview Screenshot Testing**：[官方文档](https://developer.android.com/studio/preview/compose-screenshot-testing) / [android/skills 镜像全文](https://github.com/android/skills/blob/main/testing/testing-setup/references/android/studio/preview/compose-screenshot-testing.md)。
  - 把 `@Preview` 标上 `@PreviewTest` 放进 `screenshotTest` 源集，加 `com.android.compose.screenshot` 插件（`0.0.1-alpha16`）与 `screenshot-validation-api`、`ui-tooling` 依赖，开 `android.experimental.enableScreenshotTest=true`。
  - 纯命令行跑 `gradlew :app:updateDebugScreenshotTest`（出基线 PNG，落在 `app/src/screenshotTestDebug/reference/`）或 `validateDebugScreenshotTest`（出对比 HTML 报告）。**「只用底层 Gradle 任务、不要 IDE 集成」的要求是 AGP ≥8.5.0、Kotlin ≥1.9.20、JDK ≥17**——本工程 AGP 9.2.1 / Kotlin 2.3.0 / JDK 21 名义上都满足。
  - ⚠️ **重要未验证**：该页顶部标注「独立 `com.android.compose.screenshot` 插件**已停止维护**，改用 [AGP test suites](https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites)（要求 **AGP ≥9.5.0-alpha03**、Android Studio Rabbit 1 Canary 4 IDE 集成）」。文档示例仍写 `agp = "9.0.0-rc03"`，**没有声明支持 9.2.1**；社区还报告 AGP 9.1.0 下 `newDsl=false` 时 `SourceProvider.getCDirectories()` 缺失（[Sonar 社区](https://community.sonarsource.com/t/working-towards-the-support-of-android-gradle-plugin-9/177015)）。所以「本工程 9.2.1 能跑通」**必须实测**，不能当结论。
  - 另有 Android CLI `android studio render-compose-preview`（[命令文档](https://developer.android.com/tools/agents/android-cli/commands/studio_render-compose-preview)）：但它**需要一个正在运行的 Android Studio**（文档：「connects Android CLI to a running Android Studio instance」「Requires Android Studio Quail 2 or higher」）——本机没有 AS，这条不满足「不装 IDE」。
  - 定位：这些是**静态 PNG / 回归对比**，不是可交互实时界面；且要改 `build.gradle.kts` + `gradle.properties`、加依赖（本票不改代码，故只记录，不实施）。

### 5.3 Compose Hot Reload 能否用于 Android Compose

- **不能**。[JetBrains/compose-hot-reload README](https://github.com/JetBrains/compose-hot-reload) / [kotlinlang 文档](https://kotlinlang.org/docs/multiplatform/compose-hot-reload.html)：「Compose Hot Reload **needs a JVM target**」「Android and other platforms are not supported yet」「runs your application on a special desktop JVM」。
- 官方 FAQ 对 Android-only 工程的答复是要「从 Jetpack Compose 插件换成 Compose Multiplatform 插件，另加一个 desktop/JVM 模块」（[FAQ](https://mintlify.com/JetBrains/compose-hot-reload/troubleshooting/faq)）——这已不是「看这个 Android 界面」，而是另起一个桌面目标，代价远超本票范围。
- 根因（社区解释，非官方文档）：JVM HotSwap 支持方法体重定义，ART/DEX 的结构性类重定义受限（[r/androiddev 讨论](https://reddit.com/r/androiddev/comments/1ryq1u3/compose_hotswan_compose_hot_reload_on_your_real)）。第三方 Compose HotSwan 是 IDE 插件、走自定义编译器插件 + ART agent，属**未验证**的第三方方案，不作结论。

### 5.4 Q4 结论

- 「不装 IDE、不降 AGP、看**可交互实时界面**」：仍是**命令行 `installDebug` + 蓝叠/真机**这一条（背景已确认），`@Preview` 交互式预览依赖 Android 插件、本机不可用。
- 「不降 AGP、看**静态界面图**」：多一条**官方**的 `@Preview` 截图 Gradle 任务（§5.2），但需加插件/依赖、且 9.2.1 兼容性未验证；再加 Paparazzi / Roborazzi（见 `idea-android-debugging.md` §5.4，均标注「AGP 9.2.1 未验证」）。
- 独立 layoutlib 渲染器、官方 Compose Hot Reload 用于 Android Compose：**都不可行**。

---

## 6. 未验证清单（不要当结论用）

1. **StudioFlags.java 源码正文**：直连 GitHub / android.googlesource 超时或 404，未能核对 `gradle.ide.support.future.agp.versions` 的官方注释原文与实现分支；「仅绕过版本检查 / 按 AGP 自声明兼容放行」来自第三方转述，非一手。
2. **「官方明确说该 flag 只是警告抑制、不保证读懂新 DSL」**：无一手证据。官方支持帖反而说「应能 sync」，只是实测无效。
3. **IDEA-348937 的 comment 27-11721710**（florisboard/Metro 指向的 workaround 细节）与 **IDEA-385007 / IDEA-322286 当前状态**：YouTrack 动态渲染读不到正文。
4. **florisboard PR 称「AGP 9.0.0 开 Future AGP 即可用 IDEA」**：无一手复现，且与 AGP 9.1/9.2 的失败报告冲突；未验证其 IDE/AGP 具体版本。
5. **AGP 9.2.1 + `com.android.compose.screenshot` 0.0.1-alpha16 是否真能跑通** `updateDebugScreenshotTest`：文档示例停在 9.0.0-rc03、且该插件已标注停止维护，未实测。
6. **AGP 9.2.1 的 `AndroidProject` 模型相对 8.12 具体新增了哪些方法**（`getPrivacySandboxSdkInfo` 是 9.x 通用报告，未确认 9.2.1 精确差异）；`newDsl=false` 是否会**间接**改变任何注入给 IDE 的模型字段——官方未述，未做源码级验证。但报错类型（Tooling API 模型方法）与 DSL opt-out 作用域不同层，判断「无帮助」依据充分。
7. **跨 build 强装 263 插件的社区 hack**（改 `plugin.xml` / 放宽 build 检查）：无任何可靠报告，未验证。
8. **本机 IDEA 实按 sync 的报错**：本票只做事实核查，未在真机 IDE 装插件/按 sync（沿用 `idea-android-debugging.md` §8.2 的同一限制）。
9. **§2.4 所列 `253.30387.90` 具体版本**：本次 Marketplace API 当前页未列出该号（同为 `253.*` Narwhal FD 段），未逐页翻全量历史确认，但基座结论不受影响。

---

## 7. 参考资料（均可点击）

**Google / Android**
1. [AGP 9.0.1 release notes — new DSL 与 `android.newDsl=false` opt-out 原文](https://developer.android.com/r/tools/new-dsl)
2. [AGP 9.0 release notes 镜像（android/skills）](https://github.com/android/skills/blob/main/build-system/agp/agp-9-upgrade/references/android/build/releases/agp-9-0-0-release-notes.md)
3. [Migrate to built-in Kotlin — opt-out 与 `android.newDsl=false` 绑定（镜像全文）](https://github.com/android/skills/blob/main/build/agp/agp-9-upgrade/references/android/build/migrate-to-built-in-kotlin.md)
4. [AGP DSL/API 迁移时间表（AGP 10 移除 opt-out）](https://developer.android.com/build/releases/gradle-plugin-roadmap?hl=zh-cn)
5. [AS ↔ AGP 兼容表（Narwhal FD ≤8.12）](https://developer.android.com/build/releases/about-agp)
6. [Compose Preview Screenshot Testing（Gradle 任务与要求）镜像](https://github.com/android/skills/blob/main/testing/testing-setup/references/android/studio/preview/compose-screenshot-testing.md)
7. [Compose Screenshot Testing with test suites（AGP ≥9.5.0-alpha03）](https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites)
8. [Android CLI `android studio render-compose-preview`](https://developer.android.com/tools/agents/android-cli/commands/studio_render-compose-preview)
9. [AGP ModelBuilder.java（AOSP，sync 产出 AndroidProject 模型）](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/ide/ModelBuilder.java)

**JetBrains**
10. [支持帖 34072502627218（`getPrivacySandboxSdkInfo` + Studio Flags workaround + 实测无效）](https://intellij-support.jetbrains.com/hc/en-us/community/posts/34072502627218-Android-project-fails-to-build-works-fine-in-Android-Studio-Unsupported-method-AndroidArtifact-getPrivacySandboxSdkInfo)
11. [IDEA-385007 AGP 9 compatibility](https://youtrack.jetbrains.com/issue/IDEA-385007)
12. [IDEA-322286 Enable IDEA to work with unsupported AGP](https://youtrack.jetbrains.com/issue/IDEA-322286)
13. [IDEA-348937 future AGP versions（workaround 出处，正文未读到）](https://youtrack.jetbrains.com/issue/IDEA-348937/2024.1-Beta-missing-option-to-enable-sync-with-future-AGP-versions)
14. [Marketplace API：org.jetbrains.android (22989) 各版本 since/until/compatibleVersions/modules](https://plugins.jetbrains.com/api/plugins/22989/updates?size=100)
15. [Kotlin AGP9 迁移版本矩阵（IDEA Not supported）](https://github.com/Kotlin/kotlin-agent-skills/blob/main/skills/kotlin-tooling-agp9-migration/references/VERSION-MATRIX.md)
16. [JetBrains 博客：Update your Kotlin projects for AGP 9.0](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9)
17. [since-build/until-build 不匹配导致插件无法加载](https://intellij-support.jetbrains.com/hc/en-us/community/posts/206420259-Using-existing-plugins-in-EAP-versions)

**Kotlin / Hot Reload / 其它**
18. [JetBrains/compose-hot-reload README（需 JVM target，Android 不支持）](https://github.com/JetBrains/compose-hot-reload)
19. [kotlinlang：Compose Hot Reload](https://kotlinlang.org/docs/multiplatform/compose-hot-reload.html)
20. [Compose Hot Reload FAQ（Android-only 需换 CMP 插件 + 加 desktop 模块）](https://mintlify.com/JetBrains/compose-hot-reload/troubleshooting/faq)
21. [kotlinlang Slack：AGP 9.1.0 下 workaround 无效的报告](https://slack-chats.kotlinlang.org/t/33182560/i-recently-upgraded-to-agp-9-1-0-in-my-kotlin-multiplatform-)
22. [Understanding the Sync Process（Tooling API / model builder）](https://rogerhu.github.io/studying-android-studio-internals/Sync-Process.html)
23. [UnsupportedMethodException 同类报错（Tooling API 模型接口）](https://stackoverflow.com/questions/61981230/org-gradle-tooling-model-unsupportedmethodexception-unsupported-method-android)
24. [What Are You Syncing About?（IDE sync 用 Tooling API + model building 阶段）](https://liutikas.net/2026/08/03/What-Are-You-Syncing-About.html)
25. [Sonar 社区：`android.newDsl=false` 在 AGP 9.1.0 下仍有模型方法缺失](https://community.sonarsource.com/t/working-towards-the-support-of-android-gradle-plugin-9/177015)
26. [florisboard PR #3224（Future AGP Versions 对 9.0.0 的社区说法，未复现）](https://github.com/florisboard/florisboard/commit/d3a7a2e186c63a028e2da3abad88ac7f2a393cd7)

**已有调研（本票不重复）**
27. `docs/research/idea-android-debugging.md` §2.4–2.6、§5、§8
28. `docs/research/bluestacks-adb-compose.md`（命令行 + 蓝叠链路）
