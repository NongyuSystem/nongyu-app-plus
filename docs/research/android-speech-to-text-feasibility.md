# Android 语音转文字（STT / ASR）可行性调研

> 调研对象：`apps/nongyu-app-plus`（AGP 9.2.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00 / **JDK 17 编译** / **compileSdk 36 / targetSdk 36 / minSdk 26**）。JDK 版本口径以 `app/build.gradle.kts:44-47` 的 `sourceCompatibility/targetCompatibility = VERSION_17` 与地图 #41 的一手约束为准（部分历史构建记录写 JDK 21，那是 gradle 运行期 JAVA_HOME，不是本工程编译目标，勿混用）。
> 一手事实复核：`app/build.gradle.kts:8-13` 确认 compileSdk/targetSdk/minSdk=36/36/26；`app/src/main/AndroidManifest.xml`（本次实读）**未声明 `INTERNET`、也未声明 `RECORD_AUDIO`**。
> 包体积基线：ADR-0007 探针实测 release APK ≈ **804KB**（`docs/adr/0007-整体性能优化排序与放行门槛.md:3`，`app/build.gradle.kts:27-41` 开启 R8 + 资源收缩）。本仓对包体积敏感。
> 工具链硬约束：本机 SDK 只有 `android-36`，**任何 `minCompileSdk=37` 的依赖都是硬阻断**（`docs/research/compose-ai-orb-button.md` R3 节、README 已记录同类实测失败）。
> 落点：**农小屿（Nongyu AI）**（`GLOSSARY.md:55-57`）——用户「通过语音或文字向后端 agent 提问」，激活时接管当前界面。语音转文字最自然的落点是「语音 → 文字 → 交给后端 agent」。本文统一用「农小屿」，不写「AI 圆球 / 水晶球」。
> 约束边界：`docs/adr/0006-冷启动只读本地皮肤包不碰网络.md` 的「冷启动只读本地、绝不碰网络」是**启动路径（首帧）约束**，约束的是皮肤包读取与 TTFD 口径，**不是「整个 App 不许联网」**。农小屿是用户主动激活的交互入口，不在冷启动首帧路径上，因此 STT/后续网络请求不与 ADR-0006 冲突；但任何实现都不得把网络拉回启动路径（ADR-0007 硬约束重述了这一点）。
> 接缝：STT 产出的文字最终要经网络层发往后端 agent；本仓网络层尚在选型（地图 #41、`docs/research/android-network-stack-candidates.md`），本文**只点接缝、不展开网络层选型**。
> 纪律：本次**只做调研 + 写这一份笔记**，未建 issue、未改构建文件、未 `git commit`、未跑 `gradlew`。所有「构建能否通过 / 真机表现」一律标进 §7 未验证清单。取证通道说明见 §8。
> 只做事实调研。所有断言标注一手来源 URL；二手博客/问答只当线索，不作结论依据。

---

## 1. 结论摘要

1. **现在就能做、且零新依赖、零 GMS、零包体积增量的路线是「系统 `SpeechRecognizer` / `RecognizerIntent`」**：`android.speech.*` 是平台 API（API 8 起），`createSpeechRecognizer` 在 minSdk 26 全量可用；一次性识别用 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`（Activity 结果），连续/内嵌用 `SpeechRecognizer` + `RecognitionListener`。这是最小可验证的一步。
2. **「离线端侧」要分档，不能笼统说「系统 API 支持离线」**：`createOnDeviceSpeechRecognizer` / `isOnDeviceRecognitionAvailable` 都是 **API 31** 才引入（`SpeechRecognizer` 参考页），且要求设备装了可用的 on-device `RecognitionService`；`RecognizerIntent.EXTRA_PREFER_OFFLINE` 是 **API 23** 的「请求只用离线引擎」，官方原文是「indicate whether to only use an offline speech recognition engine」，**它是请求、不是保证**。minSdk 26 上 26–30 这一段没有 `createOnDeviceSpeechRecognizer` 可用。
3. **正确回答「是否装了可用语音识别服务」**：`SpeechRecognizer.isRecognitionAvailable(Context)`（API 8）判「有没有任意服务」；`isOnDeviceRecognitionAvailable(Context)`（API 31）判「有没有端侧服务」；`createSpeechRecognizer(Context, ComponentName)` 指定组件前须用 `PackageManager.queryIntentServices` 核实组件确实存在并提供 `RecognitionService.SERVICE_INTERFACE`（= `"android.speech.RecognitionService"`）。**Android 11（API 30）起，targeting 30+ 的 App 必须在 manifest 里加 `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`**，否则与识别服务交互会失败——本仓 targetSdk 36，这一条是必做项。
4. **ML Kit 的「通用 ASR」现状要纠正印象**：旧的「ML Kit speech recognition 测试版」作为 Firebase ML Kit 时代的 API 已不在现行产品清单里（Firebase ML 将于 **2027-06-15** 停用，见 [迁移指南](https://developers.google.com/ml-kit/migration)）；但 Google **在 2026-10-26 新发布了 ML Kit GenAI Speech Recognition API**（`com.google.mlkit:genai-speech-recognition:1.0.0-alpha2`，[release notes](https://developers.google.com/ml-kit/release-notes)），**当前是 Alpha、非下线**。它 minSdk 26、Basic 模式要求设备 API 31+、Advanced 仅 Pixel 10/11 系等 Gemini Nano 设备；**支持中文**（Basic `cmn-Hans-CN`/`cmn-Hant-TW` 标 beta，Advanced 为正式 locale），流式输出、端侧推理。代价：Alpha 无 SLA、依赖 AICore + GMS、POM 拖 `kotlin-stdlib 2.4.0` / `coroutines 1.11.0`（高于本仓 2.3.0 / 1.9.0）。见 §3.2。
5. **compileSdk 36 硬阻断核查结论**：本次实际解包的所有 STT 相关 AAR 的 `minCompileSdk` 均 **≤36 或无元数据**（详见 §6 表）：ML Kit GenAI ASR 的 AAR 本体 = **1**（无实质约束）/ 无元数据文件，`genai-common 1.0.0-beta5`、`common 18.11.0` 无 `aar-metadata.properties`，`play-services-basement 18.9.0` = **1**，`vosk-android 0.3.75` = **1**，`sherpa-onnx 1.13.8` = **1**，`tensorflow-lite 2.13.0` 无元数据。**没有发现任何 STT 路线自身带 `minCompileSdk=37` 的硬阻断**。真正要盯的不是 37，而是 ① 是否接受引入 GMS ② 是否接受模型体积 ③ 是否接受 kotlin-stdlib/coroutines 被抬高。
6. **包体积对照 804KB 基线**：系统 API 路线 = **0 增量**；ML Kit GenAI ASR 仅 AAR 本体 ≈ **1.24MB**（不含其 GMS/传递依赖，R8 后增量未测），但 AICore/Gemini Nano 模型走系统侧、不进 APK；云端 ASR 只加 SDK/网络代码；端侧开源模型是数量级冲击——whisper.cpp tiny ≈ **75 MiB**、Vosk 中文小模型 ≈ **42 MB**、sherpa-onnx AAR ≈ **50MB**（含 4 ABI `.so`）且还要另带中文模型（27–200MB）；`vosk-android` AAR 单独就 **13.5MB**。任何端侧开源模型都会把 804KB 基线抬高 1~2 个数量级，必须产品先拍板。
7. **权限**：所有路线都需要 `android.permission.RECORD_AUDIO`（`SpeechRecognizer` 参考页原文「the application must have Manifest.permission.RECORD_AUDIO permission to use this class」），它是 dangerous / 运行时权限（API 23 起需动态申请）。本仓 manifest 当前**两条都没声明**，任何路线落地第一步都要补 `RECORD_AUDIO`（云端还需 `INTERNET`，ML Kit GenAI AAR 自带 `INTERNET`/`ACCESS_NETWORK_STATE` 合并）。Android 14（API 34）+ targetSdk 34 起若用 `foregroundServiceType="microphone"` 的前台服务做后台录音，系统会在创建 FGS 时校验「当前是否已有 `RECORD_AUDIO`」，没有则抛 `SecurityException`；而 while-in-use 权限只在前台持有，后台起录音 FGS 会失败。农小屿是**前台交互**，正常不涉及后台 FGS，但这条决定了「不要把它做成后台常驻服务」。Android 13（API 33）新增 `POST_NOTIFICATIONS` 运行时权限，影响 FGS 通知可见性，不直接影响前台录音。见 §5。
8. **分阶段结论**：**阶段 0（现在、可立即做）** = 用系统 `SpeechRecognizer` + `RecognizerIntent` 做「按住说话 → 一次性出文字 → 填入现有『向后端 agent 提问』输入」的最小闭环，零依赖、零体积、零 GMS，用来验证交互与识别质量；**阶段 1（需拍板后）** = 若要求「离线且中文稳定」，在 31+ 分支试 `createOnDeviceSpeechRecognizer` 或 ML Kit GenAI Basic（中文 beta）；**阶段 2（需拍板）** = 云端 ASR 或端侧开源模型，分别对应「费用/数据出站」与「体积/集成成本」的取舍。见 §7 前的 §6 分阶段表。

---

## 2. 路线对比表

> 「最低 API」列：平台 API 的门禁用 API level；SDK/库用其官方文档声明的 minSdk 或运行条件。「中文」列区分「官方列了 locale」与「模型支持」。体积列对照 release APK 804KB 基线，**均为一手来源给出的下载/制品字节，不是 R8 后增量**（增量未测）。

| # | 路线 | 最低 API / minSdk | 依赖 GMS / 系统服务 | 在线 / 离线 | 中文识别 | 延迟 / 流式 | 模型 / 依赖体积（对照 804KB） | 权限 | 隐私与数据出站 | 许可证 | 对 compileSdk 36 / minSdk 26 兼容性 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **S1** | 系统 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`（Activity 结果，一次性） | Intent 常量 API 3；`SpeechRecognizer` API 8 | 依赖**系统默认语音识别服务**（通常 GMS 的「Speech Services by Google」，但接口是 AOSP 契约，不直接依赖 GMS 库） | 由实现决定，默认可联网 | 取决于实现；`EXTRA_LANGUAGE` 传 BCP-47（如 `zh-CN`），官方**未承诺**任意服务支持中文 | 一次性；`EXTRA_PARTIAL_RESULTS`(API 8) 可请求部分结果 | **0 增量**（全在 `android.jar`） | `RECORD_AUDIO` | 类文档明确「实现 likely to stream audio to remote servers」——音频可能出设备 | 平台 API，无第三方许可 | ✅ 26 全量可用；targetSdk 36 需 `<queries>`（见 S3） |
| **S2** | 系统 `SpeechRecognizer` + `RecognitionListener`（内嵌、可连续） | API 8 | 同 S1 | 同 S1 | 同 S1 | `onPartialResults`/`onResults` 回调，可流式 | **0 增量** | `RECORD_AUDIO` | 同上 | 平台 API | ✅ 26 全量可用；targetSdk 36 需 `<queries>` |
| **S3** | 端侧 `createOnDeviceSpeechRecognizer` + `isOnDeviceRecognitionAvailable` | **API 31**（两方法均 since 31） | 依赖设备装了**可用的 on-device `RecognitionService`**（`isOnDeviceRecognitionAvailable` 判空） | **离线**（端侧） | 取决于该端侧服务的语言包；`ERROR_LANGUAGE_UNAVAILABLE`(API 31) 表示「支持但未下载」 | 同 S2 回调 | **0 增量**（模型在系统服务侧，不进 APK） | `RECORD_AUDIO` | 端侧，音频不出设备（但需确认具体实现） | 平台 API | ⚠️ 26–30 **不可用**（`SDK_INT>=31` 门禁）；31+ 需 `isOnDeviceRecognitionAvailable=true` |
| **S4** | `RecognizerIntent.EXTRA_PREFER_OFFLINE`（请求离线） | API 23 | 同 S1 | 请求「only offline engine」，**非保证** | 同 S1 | 同 S1 | **0 增量** | `RECORD_AUDIO` | 若生效则端侧 | 平台 API | ✅ 26+ 可传该 extra；效果由实现决定 |
| **M1** | **ML Kit GenAI Speech Recognition**（Basic） | minSdk **26**；Basic 运行要求设备 **API 31+** | 依赖 **AICore**（`<queries>` 声明 `com.google.android.aicore`）+ **GMS**（POM 拖 play-services-base/basement/tasks） | **端侧**（on-device），但 AICore 初始化/模型下载需要网络 | **支持**：Basic `cmn-Hans-CN`/`cmn-Hant-TW`（标 beta） | 流式（Kotlin Flow，连续文本流，可能先出 partial 再定稿） | AAR ≈ **1.24MB** + GMS/传递依赖（R8 后未测）；模型走系统侧 | `RECORD_AUDIO`；AAR 自带 `INTERNET`/`ACCESS_NETWORK_STATE` | 端侧推理；但 AICore 需联网下载配置/模型 | **Alpha**，官方原文「not subject to any SLA or deprecation policy」；商用须看 GenAI API Terms | ⚠️ compileSdk 36 **元数据层面可过**（AAR `minCompileSdk=1`/无文件）；但 POM 把 `kotlin-stdlib` 抬到 **2.4.0**、`coroutines` 抬到 **1.11.0**（本仓 2.3.0 / 1.9.0），且 Basic 需设备 31+ |
| **M2** | **ML Kit GenAI Speech Recognition**（Advanced / Gemini Nano） | 同 M1；**仅 Pixel 10/11 系 + 特定 Gemini Nano 芯片机型**，且**不支持 unlocked bootloader 设备** | AICore + Gemini Nano | 端侧 | **支持**：Advanced `cmn-Hans-CN`/`cmn-Hant-TW`（正式 locale，更广覆盖） | 同 M1 | 同 M1（模型由 AICore 共享） | 同 M1 | 端侧 | 同 M1（Alpha） | 同 M1 + 设备面极窄，本仓测试机（蓝叠/普通真机）大概率不满足 |
| **C1** | Google Cloud Speech-to-Text（V1/V2，REST/gRPC 流式） | 云端，无 Android API 门槛；客户端用 google-cloud-speech Java SDK | 不依赖 GMS，依赖**网络**（本仓 manifest 缺 `INTERNET`） | **在线** | **支持**：`cmn-Hans-CN`、粤语、国语（台湾）等，[语言表](https://cloud.google.com/speech-to-text/docs/languages) | 流式 `StreamingRecognize` + `interim_results`；一次性 `Recognize` | 无本地模型；SDK 体积未测 | `RECORD_AUDIO` + `INTERNET` | **音频出站到 Google 云**；有 data logging 选项与「without data logging」价目 | 云服务计费（V1 Standard 前 60 分钟/月免费，之后 $0.016/分钟，见 [定价](https://cloud.google.com/speech-to-text/pricing)） | ✅ 无 compileSdk 门槛；但国内可用性/合规/数据出境需人拍板 |
| **C2** | 国内云 ASR：百度 / 阿里云 / 讯飞（Android SDK / WebSocket） | 各厂商 SDK 自有 minSdk（本次未逐一取到明文，见 §9 未验证） | 不依赖 GMS，依赖网络 + 厂商 SDK | **在线**（讯飞/百度另有「离线听写/离线合成」能力，但离线 ASR 授权/资源另计） | **中文为强项**：百度短语音/实时识别支持普通话、粤语、四川话；阿里实时识别支持普通话+方言；讯飞语音听写 | 短语音（<60s）一次性、实时 WebSocket 流式 | 厂商 SDK（jar/.so）体积未测 | `RECORD_AUDIO` + `INTERNET` + 厂商要求权限 | **音频出站到国内云厂商**；需 AppKey/Token/实名账号 | 商业授权/按量计费（各厂商文档） | ✅ 无 compileSdk 门槛（本地 SDK 未解 AAR 元数据）；需人拍板供应商与费用 |
| **O1** | **whisper.cpp**（ggml，JNI/NDK，端侧） | 无官方 Android minSdk 明文；有官方 `examples/whisper.android` 与 Java binding | 不依赖 GMS | **离线** | Whisper 多语言模型含中文（`.en` 为纯英文，非 `.en` 支持多语言） | 不是天然流式；官方有 VAD、stream 示例，延迟取决于模型与设备 | **tiny 75 MiB / base 142 MiB / small 466 MiB / medium 1.5 GiB / large 2.9 GiB**（[README 内存表](https://github.com/ggml-org/whisper.cpp/blob/master/README.md)）；最小也比 804KB 大 ~95 倍 | `RECORD_AUDIO` | 端侧，音频不出设备 | **MIT**（[LICENSE](https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE)） | ✅ 无 Maven AAR 门槛（NDK 自编译）；体积与 NDK 集成是主成本 |
| **O2** | **whisper.tflite**（usefulsensors/openai-whisper） | 依赖 TFLite runtime（AAR） | 不依赖 GMS（TFLite 本地） | 离线 | 同 Whisper | 一次性/近实时（需自行做流式切片） | `.tflite` 模型 + TFLite runtime（`tensorflow-lite 2.13.0` AAR ≈ **6.6MB**，无 `minCompileSdk`） | `RECORD_AUDIO` | 端侧 | 仓库 README 首行 **「Deprecated / This repo is not being worked on」**（[README](https://github.com/usefulsensors/openai-whisper/blob/main/README.md)）→ 不建议新项目采用 | ⚠️ 上游已弃维护；NNAPI 在 **Android 15 已弃用**（[迁移指南](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)），GPU delegate 对 Whisper 的 `WHILE` 等算子兼容性有已知问题（二手线索，见 §9） |
| **O3** | **sherpa-onnx**（ONNX Runtime，端侧，Kotlin/Java API） | AAR manifest `minSdkVersion=21`（本次解包实测）；官方称支持 Android | 不依赖 GMS | **离线**；**官方明确支持 streaming 与 non-streaming** | **中文强**：官方 README 列 Zipformer/Paraformer/SenseVoice 的「Chinese + English」实时识别、中文多方言模型；中文流式小模型 ~27MB（第三方汇总，见 §9） | 流式（streaming ASR） | AAR ≈ **50MB**（v1.13.8，含 4 ABI `.so`，本次实测 50,129,134 B）+ 另带模型（数十~数百 MB） | `RECORD_AUDIO` | 端侧 | **Apache-2.0**（[LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE)）；onnxruntime 为 MIT（二手） | ✅ AAR `minCompileSdk=1`（实测）；但 50MB AAR 本身就把 804KB 基线抬 ~62 倍 |
| **O4** | **Vosk**（Kaldi，端侧） | `vosk-android 0.3.75` AAR manifest `minSdk 21`（实测） | 不依赖 GMS | **离线**（README 原文 offline） | **中文**：`vosk-model-small-cn-0.22` ≈ **42MB**（[models.md](https://github.com/alphacep/vosk-space/blob/master/models.md)），「Lightweight model for Android」 | 流式（README 原文 streaming API / zero-latency） | `vosk-android` AAR **13.5MB**（实测）+ 小模型 42MB | `RECORD_AUDIO` | 端侧 | **Apache-2.0**（[COPYING](https://github.com/alphacep/vosk-api/blob/master/COPYING)），模型标 Apache 2.0 | ✅ AAR `minCompileSdk=1`（实测）；体积仍远超基线 |

---

## 3. 逐路线细节

### 3.0 本仓前置事实（已实读，不再验证）

| 项 | 值 | 来源 |
|---|---|---|
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 | `app/build.gradle.kts:8-13` |
| release 形态 | `isMinifyEnabled=true` + `isShrinkResources=true`，签名沿用 debug | `app/build.gradle.kts:27-41` |
| release APK 基线 | ≈ 804KB（debug 10.4MB，13.3 倍） | `docs/adr/0007-整体性能优化排序与放行门槛.md:3` |
| 本机 SDK | 仅 `android-36`；`minCompileSdk=37` 依赖硬阻断 | `docs/research/compose-ai-orb-button.md` R3 节 / README |
| manifest 权限 | **无 `INTERNET`、无 `RECORD_AUDIO`、无 `<queries>`** | `app/src/main/AndroidManifest.xml`（本次实读 26 行） |
| 网络层 | 无；候选见 `docs/research/android-network-stack-candidates.md`（该文亦确认 manifest 无 `INTERNET`） | 同上 |
| 冷启动约束 | ADR-0006：启动路径只读本地皮肤包、不碰网络；**不是全局禁止联网** | `docs/adr/0006-冷启动只读本地皮肤包不碰网络.md` |
| 术语 | 农小屿（Nongyu AI）：语音或文字向后端 agent 提问，激活时接管当前界面 | `GLOSSARY.md:55-57` |

**ADR-0006 对 STT 的适用边界（重要，别机械套用）**：ADR-0006 的决策对象明确是「冷启动同步读本地皮肤包、首帧不跳色、不做版本比对、网络检查/下载全部放到首帧之后」。它管的是**启动路径**，并已被 ADR-0007 重述为「不得把网络拉回启动路径」的硬约束。农小屿是用户主动点按/长按激活的**前台交互入口**，发生在首帧之后，STT 录音与「文字 → 后端 agent」的网络请求都不在冷启动路径内，因此**不与 ADR-0006 冲突**。真正要守住的是：不要在 `MainActivity.onCreate` / `Application` / `ContentProvider` / `setKeepOnScreenCondition` 里初始化 STT 或发网络请求；也不要借 STT 把 AICore/模型下载塞进启动期。

---

### 3.1 系统路线：`android.speech.SpeechRecognizer` / `RecognizerIntent` / `RecognitionService`

**这是本仓唯一「零新增 Maven 依赖、零 APK 体积增量、零 GMS 直接依赖」的正规路线**，也是阶段 0 的落点。全部类在 `android.jar`（平台 API），不存在 AAR `minCompileSdk` 问题。

**API level 与方法（逐条取自 [`SpeechRecognizer` 参考页](https://developer.android.com/reference/android/speech/SpeechRecognizer)）**

| 成员 | since | 要点（官方原文要点） |
|---|---|---|
| `SpeechRecognizer` 类 | **API 8** | 「provides access to the speech recognition service」；**不要直接 new**，用 `createSpeechRecognizer(Context)`；「methods must be invoked only from the main application thread」；用完必须 `destroy()` |
| `isRecognitionAvailable(Context)` | **API 8** | 「Checks whether a speech recognition service is available on the system」——判「有没有任意服务」 |
| `createSpeechRecognizer(Context)` | **API 8** | 用系统默认识别服务 |
| `createSpeechRecognizer(Context, ComponentName)` | **API 8** | 指定服务组件；官方要求**先用 `PackageManager.queryIntentServices(Intent,int)` 核实该组件确实存在且提供 `RecognitionService.SERVICE_INTERFACE`** |
| `createOnDeviceSpeechRecognizer(Context)` | **API 31** | 返回「a new on-device SpeechRecognizer」；`isOnDeviceRecognitionAvailable=false` 时抛 `UnsupportedOperationException` |
| `isOnDeviceRecognitionAvailable(Context)` | **API 31** | 「Checks whether an on-device speech recognition service is available on the system. If false, `createOnDeviceSpeechRecognizer` will fail」 |
| `checkRecognitionSupport` / `triggerModelDownload` | 见参考页 | 支持查询/触发模型下载（配合 `ERROR_LANGUAGE_UNAVAILABLE` = 支持但未下载） |
| `setRecognitionListener` / `startListening` / `stopListening` / `cancel` / `destroy` | **API 8** | 全部「must be called from the main thread」；先 `setRecognitionListener` 再 `startListening`，否则收不到回调 |

**`<queries>` 必做项（targetSdk 36 直接相关）**：`createSpeechRecognizer(Context)` 与 `createSpeechRecognizer(Context, ComponentName)` 的参考页原文写明——「For apps targeting **Android 11 (API level 30)** interaction with a speech recognition service requires `<queries>` element to be added to the manifest」：

```xml
<queries>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

依据：[`SpeechRecognizer` 参考页](https://developer.android.com/reference/android/speech/SpeechRecognizer)、[包可见性总览](https://developer.android.com/training/package-visibility)（「When an app targets Android 11 (API level 30) or higher and queries for information about the other apps… declare using `<queries>`」）、[`<queries>` 元素参考](https://developer.android.com/guide/topics/manifest/queries-element)。本仓 targetSdk 36 ≥ 30，**这一条不补，S1/S2/S3/S4 在真机上会失败**。

**「设备上是否装了可用语音识别服务」的判定方式（可判定结论）**
1. `SpeechRecognizer.isRecognitionAvailable(context)` → 系统有没有任意 `RecognitionService`（API 8）。
2. 若要端侧：`Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` → 有没有端侧服务（API 31）。
3. 若要指定某个服务：`PackageManager.queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), …)` 取列表，确认目标 `ComponentName` 在列且声明了 `SERVICE_INTERFACE`，再传给 `createSpeechRecognizer(Context, ComponentName)`。`RecognitionService.SERVICE_INTERFACE` 常量值 = `"android.speech.RecognitionService"`（API 8），见 [`RecognitionService` 参考页](https://developer.android.com/reference/android/speech/RecognitionService)。
4. `RecognitionService` 本身是「**供实现方扩展的基类**」——参考页原文「This class provides a base class for recognition service implementations. This class should be extended only in case you wish to implement a new speech recognizer」。即它是「别人（如 GMS 的 Speech Services by Google / Android System Intelligence）实现的识别服务」的契约，**不是 App 调用端要继承的类**；App 侧绝不要去写 `RecognitionService`。

**`RecognizerIntent`（[参考页](https://developer.android.com/reference/android/speech/RecognizerIntent)）关键常量**

| 常量 | since | 用途 |
|---|---|---|
| `ACTION_RECOGNIZE_SPEECH` | API 3 | 起一个 Activity 录音并返回文字；**必需** `EXTRA_LANGUAGE_MODEL` |
| `ACTION_WEB_SEARCH` / `ACTION_VOICE_SEARCH_HANDS_FREE` | API 3 / 16 | 语音搜索 / 免提 |
| `EXTRA_LANGUAGE_MODEL` = `LANGUAGE_MODEL_FREE_FORM` / `LANGUAGE_MODEL_WEB_SEARCH` | API 3 | free_form 适合自由说话（农小屿选这个） |
| `EXTRA_LANGUAGE` | API 3 | BCP-47 语言标签，如 `zh-CN`（「Optional IETF language tag」） |
| `EXTRA_PARTIAL_RESULTS` | API 8 | 请求部分结果（流式雏形） |
| `EXTRA_MAX_RESULTS`、`EXTRA_PROMPT`、`EXTRA_RESULTS` | API 3 | 结果数量 / 提示语 / `ArrayList<String>` 结果 |
| `EXTRA_PREFER_OFFLINE` | **API 23** | 官方原文「indicate whether to **only use an offline speech recognition engine**. The default is false」——**是请求，不是保证** |
| `EXTRA_ENABLE_FORMATTING`、`EXTRA_BIASING_STRINGS`、`EXTRA_SEGMENTED_SESSION`、`EXTRA_AUDIO_SOURCE` | API 33 | 格式化/偏置词/分段会话/自定义音频源 |
| `EXTRA_ENABLE_LANGUAGE_DETECTION`、`EXTRA_ENABLE_LANGUAGE_SWITCH` | API 34 | 语言检测/自动切换 |
| `ACTION_GET_LANGUAGE_DETAILS` / `getVoiceDetailsIntent` | API 8 | 查询实现方声明的支持语言（官方注明「Whether these are actually provided is up to the particular implementation… not required」）→ **不能当成中文可用性的可靠依据** |

**在线 vs 离线、隐私、GMS**：`SpeechRecognizer` 类文档原文「**The implementation of this API is likely to stream audio to remote servers to perform speech recognition. As such this API is not intended to be used for continuous recognition, which would consume a significant amount of battery and bandwidth.**」——即**默认实现可能把音频发到远端**；连续识别被官方明确不推荐。真正的「不出设备」只有 `createOnDeviceSpeechRecognizer`（且前提是 `isOnDeviceRecognitionAvailable=true`）。提供系统识别服务的通常是 GMS 的「Speech Services by Google」（`com.google.android.tts`），但**App 侧只依赖 AOSP 契约 `android.speech.*`，不直接依赖 GMS 库**；是否可用取决于设备/用户默认语音输入设置（二手线索：Android 12 起默认服务可能被设为 Android System Intelligence 导致第三方 `SpeechRecognizer` 报 `ERROR_CLIENT`，见 §9）。

**错误与降级信号**（[`SpeechRecognizer` 参考页](https://developer.android.com/reference/android/speech/SpeechRecognizer)）：`ERROR_NETWORK` / `ERROR_NETWORK_TIMEOUT`（联网失败）、`ERROR_NO_MATCH`、`ERROR_SPEECH_TIMEOUT`、`ERROR_RECOGNIZER_BUSY`、`ERROR_INSUFFICIENT_PERMISSIONS`、`ERROR_LANGUAGE_NOT_SUPPORTED`(31)、`ERROR_LANGUAGE_UNAVAILABLE`(31，支持但未下载)、`ERROR_SERVER`(8)、`ERROR_TOO_MANY_REQUESTS`(31)。这些正好是 §4 降级路径要分支处理的枚举。

---

### 3.2 Google Play 服务 / ML Kit 的「通用 ASR」现状（**重点核实项**）

**结论：不能写「ML Kit 没有通用 ASR」或「ML Kit ASR 已下线」——现状是「旧的 Firebase ML Kit 时代 ASR 不在现行清单，但 2026-10-26 新上线了 ML Kit GenAI Speech Recognition（Alpha）」。**

- **旧测试版的处置**：现行 [ML Kit 迁移指南](https://developers.google.com/ml-kit/migration) 的 on-device base API 清单是「Barcode scanning / Face detection / Image labeling / Object detection / Text recognition / Language ID / Smart reply / Translate / AutoML Vision Edge」，**不含 speech recognition**；并写明「Firebase ML will be turned down on **June 15, 2027**」「existing on-device base APIs in the ML Kit for Firebase SDK are deprecated」。Google Maven `com/google/mlkit/group-index.xml`（本次实读）里**只有 `genai-speech-recognition` / `genai-speech-translation` / `genai-text-to-speech`，没有任何「经典版」speech artifact**。→ 可判定结论：**旧的那条 ML Kit/Firebase 通用 ASR 线不在现行独立 ML Kit SDK 产品里**，但「具体哪一天、以哪份公告下线」本次未从一手来源取到（见 §9）。
- **新 API（现状可用，Alpha）**：[ML Kit GenAI Speech Recognition API](https://developers.google.com/ml-kit/genai/speech-recognition/android)。
  - 坐标 `com.google.mlkit:genai-speech-recognition:1.0.0-alpha2`；[release notes](https://developers.google.com/ml-kit/release-notes) 记「released GenAI Speech Recognition API to transcribe audio content to text」，`1.0.0-alpha2` 日期 **10/26/2026**，`1.0.0-alpha1` 亦在 2026；官方原文「**This API is offered in alpha, and is not subject to any SLA or deprecation policy. Changes may be…**」。
  - **minSdkVersion 26**（官方页面「The SDK requires minSdkVersion 26 or higher」；AAR manifest 实测 `android:minSdkVersion="26"`）。
  - 两种模式：**Basic** = 传统端侧识别模型（「similar to the SpeechRecognizer API」），设备要求 **API 31+**；**Advanced** = 端侧 Gemini Nano（AICore），官方 [设备支持表](https://developers.google.com/ml-kit/genai) 限「Pixel 10 / Pixel 11 series, plus devices with Gemini Nano on following chipsets」，并注明「**not supported on devices with an unlocked bootloader**」。
  - **中文**：官方 Supported languages 表（实读）——Advanced 含 `cmn-Hans-CN`、`cmn-Hant-TW`（正式 locale）；Basic 含 `cmn-Hans-CN (beta)`、`cmn-Hant-TW (beta)`，并注明「Languages in Beta may have a slightly higher word error rate」。→ **中文有官方支持，但 Basic 档标 beta**。
  - **流式**：官方「Captures streaming input from microphone or an audio file」「Transcribed text is provided as a continuous stream, which may initially be partial」；SDK 用 `SpeechRecognizer` client + `checkFeatureStatus()` / `download` + Kotlin Flow 输出。音频输入支持麦克风或 MP3/WAV/FLAC/M4A/OGG，自定义 `ParcelFileDescriptor` 约 **32,000 B/s（16kHz/16bit/mono PCM）**。
  - **端侧但需联网初始化**：GenAI APIs 依赖 **AICore**（`genai-common` AAR 的 `<queries>` 实测声明 `com.google.android.aicore`）；官方明确 AICore 刚装/刚重置时需联网下载配置，可能几分钟到几小时，期间 API 不可用，须先 `checkFeatureStatus()`。AAR 自带 `INTERNET` / `ACCESS_NETWORK_STATE` 权限并会合并进 manifest。
  - **GMS 依赖（POM 实测）**：`genai-speech-recognition:1.0.0-alpha2` 的 [POM](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-speech-recognition/1.0.0-alpha2/genai-speech-recognition-1.0.0-alpha2.pom) 拖 `com.google.android.gms:play-services-base:18.5.0` / `play-services-basement:18.9.0` / `play-services-tasks:18.2.0`、`com.google.mlkit:common:18.11.0`、`genai-common:1.0.0-beta5`、`com.google.firebase:firebase-encoders*`、`firebase datatransport`，以及 **`kotlin-stdlib:2.4.0`**、**`kotlinx-coroutines-core/guava/reactive:1.11.0`**。→ 相对本仓 Kotlin 2.3.0 / Compose runtime 1.10.0 的 coroutines 1.9.0，会**抬高 stdlib 与 coroutines**（是否产生构建/运行差异，见 §9 未验证）。
  - **体积**：AAR 本体实测 **1,241,474 B ≈ 1.24MB**（不含传递依赖与 R8 后真实增量）；模型走 AICore 系统侧，不进 APK。
  - **compileSdk 36 兼容性（本次实测解包）**：`genai-speech-recognition-1.0.0-alpha2.aar`、`genai-common-1.0.0-beta5.aar`、`common-18.11.0.aar` **均无 `META-INF/com/android/build/gradle/aar-metadata.properties`**（旧式打包，不触发该检查，见 KB playbook）；`play-services-basement-18.9.0.aar` 的 `minCompileSdk=1`、`play-services-base/tasks` 无元数据。→ **没有 `minCompileSdk=37` 硬阻断**。但请注意：本仓 KB 已验证 `play-services-*` 某些新版本可能抬高编译门槛，落地前应对锁定的具体版本逐个解包复核（本次已对 POM 锁定的版本做过，见 §6）。
- **Google Play 服务本身有没有「另一个」通用 ASR**？本次未找到 `com.google.android.gms:play-services-mlkit-speech-recognition` 之类的独立 GMS ASR artifact（`group-index` / release notes 均无）；现行 Google 的端侧 ASR 入口就是上述 ML Kit GenAI API 与平台 `SpeechRecognizer`。`play-services-mlkit-*` 系列（barcode / text-recognition / face…）是视觉/文本，不是语音 ASR。

---

### 3.3 云端 ASR（Google Cloud / 百度 / 阿里 / 讯飞）

**共通取舍**：在线、中文能力强、无本地模型体积、但需要 `INTERNET`（本仓 manifest 缺失，任何云端路线都要先补）、音频**出站**、按量计费、且「文字 → 后端 agent」这一跳本来就要网络。对农小屿而言云端 ASR 与「向后端 agent 提问」是同一条网络链路的两端，接缝最自然，但**数据出站与费用必须人/产品拍板**。

- **Google Cloud Speech-to-Text**：[语言表](https://cloud.google.com/speech-to-text/docs/languages) 明列 `cmn-Hans-CN`（普通话）、粤语、`cmn-Hant-TW` 等；[流式识别](https://cloud.google.com/speech-to-text/docs/streaming-recognize) 支持 `StreamingRecognize` / `interim_results` / single utterance；[定价](https://cloud.google.com/speech-to-text/pricing) V1 Standard「0–60 分钟/月免费，之后 $0.016/分钟」，并区分「with data logging」与「without data logging」两档价格；[data logging](https://cloud.google.com/speech-to-text/docs/data-logging) 说明音频日志策略。**取舍**：国内网络可达性、数据出境合规、Google 账号/结算是产品决策，不是技术问题。
- **百度智能云语音识别**：官方[能力说明](https://cloud.baidu.com/discover/guide-speech-language.html) 与 [文档](https://cloud.baidu.com/doc/SPEECH/s/8k38lxz8o)（本次均 200 可达）：短语音识别 <60s、实时语音识别走 WebSocket、Android SDK 支持 `LANGUAGE` 参数（普通话/英语/粤语/四川话）；短语音标准版按次数、实时按小时计费；支持中文多方言模型（pid 15376）。**取舍**：厂商账号/实名、按量计费、SDK（jar/.so）体积与 `minCompileSdk` 本次未取（见 §9）。
- **阿里云智能语音交互**：官方[实时语音识别](https://help.aliyun.com/zh/isi/getting-started/one-sentence-recognition)（本次 200 可达）支持普通话+方言、热词、时间戳，DashScope SDK / WebSocket 两种接入，文档建议 SDK 优先；一句话识别/实时识别/录音文件识别三种场景。**取舍**：同国内云厂商的账号/计费/SDK 问题。
- **讯飞语音听写**：官方 [Android SDK 文档](https://www.xfyun.cn/doc/asr/voicedictation/Java-SDK.html)（本次 200 可达）为 MSC 体系（`msc.jar` + `libmsc.so` + assets），并有「离线听写 IatDemo」demo 目录（离线能力/授权另计）。**取舍**：商业授权、离线资源购买、armeabi-v7a 等 ABI 与 `.so` 体积。

> 国内三家本次只确认了「官方文档可达 + 能力/形态」层面，**没有逐一取到 SDK 的 minSdk / AAR `minCompileSdk` / 许可字段 / 精确体积**，全部进 §9 未验证清单，不写成结论。

---

### 3.4 端侧开源模型（whisper.cpp / whisper.tflite / sherpa-onnx / Vosk）

**共通取舍**：全部**离线、端侧、音频不出设备、中文可用（中文模型/beta 档）**，代价是**体积相对 804KB 基线是数量级冲击** + NDK/JNI 集成 + R8 需补 JNI keep（`app/proguard-rules.pro` 注释已预告「将来引入 JNI / 反射时必须回来补 keep」）+ 需自己处理录音、VAD、流式切片、线程。license 均为宽松许可（MIT / Apache-2.0）。

- **whisper.cpp**（[README](https://github.com/ggml-org/whisper.cpp/blob/master/README.md) / [LICENSE](https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE) / [models README](https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md)）：纯 C/C++ 无依赖、MIT；官方列 **Android**（`examples/whisper.android`）与 **Java binding**；Vulkan GPU 支持、VAD、量化。**体积实测表**：tiny 75 MiB / base 142 MiB / small 466 MiB / medium 1.5 GiB / large 2.9 GiB；`ggml-tiny.bin` 75 MiB、`ggml-base.bin` 142 MiB（[models README](https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md)）。非 `.en` 模型为多语言（含中文）。最小 tiny 也比 804KB 大约 95 倍。无 Maven AAR 门槛（NDK 自编译 `.so`）。
- **whisper.tflite**（[useful-sensors/openai-whisper](https://github.com/usefulsensors/openai-whisper/blob/main/README.md)）：仓库 README 首行 **「## Deprecated — This repo is not being worked on」**，指向 useful-transformers。→ **新项目不建议采用**。TFLite runtime：`tensorflow-lite 2.13.0` AAR 实测 **6,623,648 B**、**无 `aar-metadata.properties`**（无 `minCompileSdk` 门槛）。**加速层的坑**：[NNAPI 迁移指南](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide) 官方写明「**NNAPI is deprecated … deprecated in Android 15**」，推荐迁移到 LiteRT in Play Services / TFLite GPU delegate；GPU delegate 对 Whisper 的 `WHILE` 等算子兼容问题是二手线索（见 §9）。
- **sherpa-onnx**（[README](https://github.com/k2-fsa/sherpa-onnx/blob/master/README.md) / [LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) / [Android 文档](https://k2-fsa.github.io/sherpa/onnx/android/) / [AAR 构建说明](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/android/SherpaOnnxAar)）：**Apache-2.0**；官方「locally / Speech-to-text (ASR); **both streaming and non-streaming**」；README 列表里有大量「Chinese + English」Zipformer / Paraformer / SenseVoice / 中文多方言实时识别条目；平台表含 **Android / WearOS**；通过 `android/SherpaOnnxAar` 用 NDK 把 `jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}` 打进 AAR（也支持 Qualcomm QNN NPU）。**体积实测**：`sherpa-onnx-1.13.8.aar` = **50,129,134 B ≈ 50MB**，`minCompileSdk=1`、manifest `minSdk 21`；中文小模型「Zipformer Chinese Small 27MB」等来自第三方汇总（[wmkeyboard wiki](https://github.com/wasi-master/wmkeyboard/wiki/Offline-voice-(Sherpa))，二手，见 §9）。
- **Vosk**（[vosk-api README](https://github.com/alphacep/vosk-api/blob/master/README.md) / [COPYING](https://github.com/alphacep/vosk-api/blob/master/COPYING) / [models.md](https://github.com/alphacep/vosk-space/blob/master/models.md)）：**Apache-2.0**；README 原文「offline open source speech recognition toolkit」「20+ languages… **Chinese**」「Vosk models are small (50 Mb)」「zero-latency response with **streaming API**」「from… Android smartphone」；中文小模型 `vosk-model-small-cn-0.22` = **42M**、标注「Lightweight model for Android and RPi」、Apache 2.0，大字错率 23.54(SpeechIO-02)/38.29(SpeechIO-06)。**体积实测**：`com.alphacephei:vosk-android:0.3.75` AAR = **13,472,638 B ≈ 13.5MB**，`minCompileSdk=1`、manifest `minSdk 21`。

---

## 4. 针对农小屿的接入接缝

> 这一节只描述「把 STT 接进现有产品链路」的结构，不含实现代码、不展开网络层选型。

**录音与生命周期（Compose / Activity）**
- 农小屿「激活时接管当前界面」（`GLOSSARY.md:56`），语音入口应是一个**前台、有明确起止**的交互（如按住说话 / 点按后进入「聆听态」），不要做成后台常驻服务：Android 14（API 34）起 `foregroundServiceType="microphone"` 的 FGS 在创建时会校验 `RECORD_AUDIO`，而该权限是 while-in-use（仅前台持有），后台起会抛 `SecurityException`（[FGS while-in-use 限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[FGS 类型](https://developer.android.com/develop/background-work/services/fgs/service-types)）。前台交互天然规避这一条。
- `SpeechRecognizer` 的所有方法必须在**主线程**调用（参考页原文），`setRecognitionListener` 必须先于 `startListening`，用完 `destroy()`。Compose 侧应把「创建/销毁 recognizer」挂在 `DisposableEffect`（随进入/退出农小屿语音态），把 `onResults`/`onPartialResults`/`onError` 作为回调写入一个 `remember` 的状态持有者，避免把回调注册进 composition 造成每帧重组（与 `docs/research/compose-ai-orb-button.md` 的「draw 阶段读 state」纪律同源）。
- `RECORD_AUDIO` 是运行时权限（dangerous）：进入语音态前用 `rememberLauncherForActivityResult(RequestPermission())` 申请；被拒 → 直接走「键盘输入」降级，不弹二次解释（本期无设置页，见 ADR-0006 一致的「失败静默」风格）。
- `targetSdk 36` 必须补 `<queries>`（§3.1）；manifest 还要补 `RECORD_AUDIO`，云端路线再补 `INTERNET`。**注意**：`docs/research/android-network-stack-candidates.md:20` 已记录本仓连 `INTERNET` 都没声明——STT 文字发往后端 agent 这一跳无论选哪条路线都绕不开网络层，本文不展开选型，只标接缝。

**识别结果如何送进「向后端 agent 提问」链路**
- 契约保持**纯文本**：STT 的唯一产出物是 `String`（`RecognizerIntent.EXTRA_RESULTS` 的 `ArrayList<String>` 取 `[0]`，或 `RecognitionListener.onResults(Bundle)` 里 `RESULTS_RECOGNITION`，或 ML Kit GenAI 的 Flow 定稿文本）。把该 `String` 当作「用户本应在文本框里敲的那行字」，交给现有/既定的「向后端 agent 提问」入口即可——**不要在 STT 层引入任何后端协议、JSON、会话 ID**，网络层选型独立进行。
- **流式 vs 一次性**：
  - 一次性（`ACTION_RECOGNIZE_SPEECH` 或 `onResults` 定稿）→ 最贴合「说一句 → 提交一个问题」，实现最简，延迟感知是「说完后一次性出字」。
  - 流式（`EXTRA_PARTIAL_RESULTS` / `onPartialResults` / ML Kit GenAI Flow / sherpa streaming）→ 可做「边说边上屏」，但 partial 文本**会变**（官方 GenAI 页原文「may initially be partial (and subject to change)」）。产品需拍板：partial 是否只做「正在聆听」的视觉反馈、还是真的逐字上屏并允许编辑。建议阶段 0 只把 partial 用作 UI 反馈、以最终 `onResults` 文本提交，避免把不稳定中间态发给后端 agent。
- **失败 / 无网络 / 无语音服务的降级路径**（按 `SpeechRecognizer` 错误枚举分流，参考页同源）：
  1. `isRecognitionAvailable=false` 或 `createOnDeviceSpeechRecognizer` 抛 `UnsupportedOperationException` → 语音入口置灰/隐藏，回落到纯文字输入。
  2. `ERROR_INSUFFICIENT_PERMISSIONS` → 重新申请 `RECORD_AUDIO` 或回落文字。
  3. `ERROR_NETWORK` / `ERROR_NETWORK_TIMEOUT` / `ERROR_SERVER` → 云端或联网实现失败：提示「网络不可用」并回落文字；若同时具备端侧能力（S3/M1 Basic）可尝试切端侧。
  4. `ERROR_LANGUAGE_UNAVAILABLE` / `ERROR_LANGUAGE_NOT_SUPPORTED` → 中文语言包未下载/不支持：可 `triggerModelDownload`（平台）或 `download`+`checkFeatureStatus`（ML Kit GenAI），否则回落文字。
  5. `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT` → 没听清/没说话：保持聆听态让用户重试，不提交空串。
  6. ML Kit GenAI 专属：`checkFeatureStatus()` 返回 `DOWNLOADABLE/UNAVAILABLE`（AICore 未就绪）→ 官方要求「先 checkStatus 再显示 UI」，此时不要展示语音入口或给出「稍后可用」。
  7. 统一兜底：**键盘/文字输入始终是最终降级**（农小屿本来就支持「语音或文字」），任何 STT 失败都不应阻断向后端 agent 提问。

---

## 5. 权限与 Android 13+ 变化（逐条一手来源）

| 项 | 结论 | 来源 |
|---|---|---|
| `RECORD_AUDIO` | `SpeechRecognizer` 类文档原文「the application must have Manifest.permission.RECORD_AUDIO permission to use this class」；常量 since **API 1** | [`SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer)、[`Manifest.permission`](https://developer.android.com/reference/android/Manifest.permission) |
| dangerous / 运行时 | `RECORD_AUDIO` 属运行时（dangerous）权限；官方权限总览把「microphone and camera」列为「particularly sensitive」、需运行时请求 | [权限总览](https://developer.android.com/guide/topics/permissions/overview)、[请求运行时权限](https://developer.android.com/training/permissions/requesting) |
| 本仓现状 | manifest **无 `RECORD_AUDIO`、无 `INTERNET`** → 任何路线都要先补 | `app/src/main/AndroidManifest.xml`（实读）、`docs/research/android-network-stack-candidates.md` §0 |
| `<queries>`（Android 11 / API 30） | targeting 30+ 与识别服务交互必须声明 `<queries>` + `android.speech.RecognitionService` intent | [`SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer)、[包可见性](https://developer.android.com/training/package-visibility) |
| Android 12（API 31） | 系统加入麦克风/摄像头隐私开关与指示器；`createOnDeviceSpeechRecognizer` / `isOnDeviceRecognitionAvailable` 自 31 引入 | [Android 12 行为变更](https://developer.android.com/about/versions/12/behavior-changes-12)、[`SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer) |
| Android 13（API 33） | 新增 `POST_NOTIFICATIONS` 运行时权限，影响前台服务通知可见性；**不直接影响前台录音** | [Android 13 通知权限变更](https://developer.android.com/about/versions/13/changes/notification-permission) |
| **Android 14（API 34）对录音最实质的一条** | targeting 34+ 时，创建 `foregroundServiceType="microphone"` 的 FGS，系统校验「当前是否已有 `RECORD_AUDIO`」，没有则抛 `SecurityException`；而 while-in-use 权限仅前台持有 → **后台起录音 FGS 会失败**；同时 34 起每个 FGS 必须声明类型 | [FGS while-in-use 限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[FGS 类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[Android 14 FGS 变更](https://developer.android.com/about/versions/14/changes/fgs-types) |
| Android 15 | **NNAPI 弃用**（影响端侧模型的硬件加速选型，不影响权限） | [NNAPI 迁移指南](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)、[NNAPI](https://developer.android.com/ndk/guides/neuralnetworks) |
| ML Kit GenAI AAR 自带权限 | 实读 AAR manifest：`INTERNET` + `ACCESS_NETWORK_STATE` + `<queries>`(AICore/TTS) → 会合并进 manifest，但不应该依赖库来给权限 | [AAR](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-speech-recognition/1.0.0-alpha2/genai-speech-recognition-1.0.0-alpha2.aar)（本次解包） |

> 说明：用户问到的「Android 13+ 的变化」里，**对 STT 权限模型影响最大的是 Android 14 的 microphone FGS 校验**，而不是 13 本身；13 的 `POST_NOTIFICATIONS` 只在采用前台服务方案时才相关。农小屿是前台交互，正常两条都不触发，但决定了「不要做成后台常驻录音」。

---

## 6. `minCompileSdk` 实测与分阶段可行性结论

### 6.1 AAR 元数据实测（对照 KB playbook：先 `group-index.xml` 取版本，再下 AAR 解 `aar-metadata.properties`）

> 方法：`Python urllib`（显式 `HTTPS_PROXY=http://127.0.0.1:7890`）下载 `.aar` 字节 → `zipfile.ZipFile(io.BytesIO(...))` → 读 `META-INF/com/android/build/gradle/aar-metadata.properties`；无该条目记「无元数据（旧式打包，不触发检查）」。依据 KB playbook《用 group-index.xml + AAR 元数据判定 AndroidX 库能否用于指定 compileSdk》与《在内存中核查 AAR minCompileSdk…》。

| artifact | 版本 | `minCompileSdk` | `minAndroidGradlePluginVersion` | manifest minSdk | 解包来源 |
|---|---|---|---|---|---|
| `com.google.mlkit:genai-speech-recognition` | 1.0.0-alpha2 | **无元数据文件**（AAR 1,241,474 B） | — | 26 | [AAR](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-speech-recognition/1.0.0-alpha2/genai-speech-recognition-1.0.0-alpha2.aar) |
| `com.google.mlkit:genai-common` | 1.0.0-beta5 | 无元数据文件 | — | 26（targetSdk 35） | [AAR](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-common/1.0.0-beta5/genai-common-1.0.0-beta5.aar) |
| `com.google.mlkit:common` | 18.11.0 | 无元数据文件 | — | 21 | [AAR](https://dl.google.com/dl/android/maven2/com/google/mlkit/common/18.11.0/common-18.11.0.aar) |
| `com.google.android.gms:play-services-basement` | 18.9.0 | **1** | 1.0.0 | — | [AAR](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-basement/18.9.0/play-services-basement-18.9.0.aar) |
| `com.google.android.gms:play-services-base` | 18.5.0 | 无元数据文件 | — | 21 | [AAR](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-base/18.5.0/play-services-base-18.5.0.aar) |
| `com.google.android.gms:play-services-tasks` | 18.2.0 | 无元数据文件 | — | 21 | [AAR](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-tasks/18.2.0/play-services-tasks-18.2.0.aar) |
| `com.alphacephei:vosk-android` | 0.3.75 | **1** | 1.0.0 | 21 | [AAR](https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar) |
| `k2-fsa sherpa-onnx`（GitHub release） | 1.13.8 | **1** | 1.0.0 | 21 | [AAR](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar) |
| `org.tensorflow:tensorflow-lite` | 2.13.0 | 无元数据文件 | — | — | [AAR](https://repo1.maven.org/maven2/org/tensorflow/tensorflow-lite/2.13.0/tensorflow-lite-2.13.0.aar) |
| 对照：`com.google.mlkit group-index.xml` | — | 只有 `genai-speech-recognition(alpha1,alpha2)` / `genai-speech-translation` / `genai-text-to-speech`，**无经典 speech artifact** | — | — | [group-index.xml](https://dl.google.com/dl/android/maven2/com/google/mlkit/group-index.xml) |

**判定**：`minCompileSdk=1` 的含义是「无实质约束」（KB playbook）；「无元数据文件」= 该库由较老 AGP 打包、不做该检查。**本次所有 STT 相关 AAR 均 ≤36，没有发现 `minCompileSdk=37` 的硬阻断**。对照本仓已踩过的坑：`compose-ui 1.12.x` / `okhttp-android 5.5.x` 才是 37（见 `docs/research/compose-ai-orb-button.md` 与 `docs/research/android-network-stack-candidates.md`），STT 路线不在其列。

**需警惕的非 37 风险**：ML Kit GenAI ASR 的 POM 把 `kotlin-stdlib` 钉到 **2.4.0**、`kotlinx-coroutines` 钉到 **1.11.0**，而本仓是 Kotlin **2.3.0** / Compose runtime 1.10.0 的 coroutines **1.9.0**。Gradle 取高会静默抬高两者；`docs/research/android-network-stack-candidates.md` §4.3 已实测「Kotlin 2.3.0 编译器能读 mv=2.4.0 的元数据」，但「runtime 比 compiler 新 + AGP 9 内置 Kotlin 是否另有校验」**未验证**（§9）。这是 M1/M2 路线第一个要落地的实测动作，不是编译期 37 阻断。

### 6.2 分阶段可行性结论（可判定）

| 阶段 | 能不能做 | 具体动作 | 硬阻断 / 前置 | 需要人/产品先拍板 |
|---|---|---|---|---|
| **阶段 0：现在、立即可做** | ✅ **能** | 用平台 `SpeechRecognizer`（或 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`）做「按住说话 → 一次性 `String` → 填入农小屿现有『向后端 agent 提问』文字入口」的最小闭环。补 `RECORD_AUDIO` + `<queries>`，运行时申请权限，按 §4 处理错误与降级 | 无 `minCompileSdk` 门槛、无新 Maven 依赖、无 GMS 直接依赖、**APK 体积 0 增量**；唯一硬前置是 manifest 两条声明 | 基本无：这是验证「识别质量 / 交互手感」的最低成本实验 |
| **阶段 0 的最小可验证一步** | ✅ | 在现有入口挂一个「语音」按钮 → `isRecognitionAvailable` 判空 → `RecognizerIntent(ACTION_RECOGNIZE_SPEECH)` + `EXTRA_LANGUAGE_MODEL=FREE_FORM` + `EXTRA_LANGUAGE=zh-CN` + `EXTRA_PREFER_OFFLINE` 对照试一次 → `onActivityResult` 取 `EXTRA_RESULTS[0]` 填入输入框。**先不接任何后端**、先看中文识别率与是否联网 | 需一台装了可用语音识别服务的真机（蓝叠/无 GMS 设备可能没有） | 用哪台设备验、验收的中文识别率门槛 |
| **阶段 1a：系统端侧离线** | ⚠️ 条件可做 | `SDK_INT>=31 && isOnDeviceRecognitionAvailable` → `createOnDeviceSpeechRecognizer`；31 以下回落阶段 0 的联网实现或纯文字 | **26–30 无此 API**（硬 API 门禁）；依赖设备装了端侧服务与中文语言包（`ERROR_LANGUAGE_UNAVAILABLE` 时可 `triggerModelDownload`） | 是否接受「部分设备/部分系统版本没有端侧服务」的覆盖缺口 |
| **阶段 1b：ML Kit GenAI ASR（Basic）** | ⚠️ 元数据可过、但多项待拍板 | 加 `genai-speech-recognition:1.0.0-alpha2`，`checkFeatureStatus()` → `download` → Kotlin Flow 流式出文本，中文 `cmn-Hans-CN`(beta) | **Alpha 无 SLA**（官方原文）；Basic 需设备 **API 31+**；依赖 **AICore + GMS**；POM 抬高 stdlib/coroutines（未实测）；AAR ≈1.24MB | ① 是否接受引入 **GMS 依赖**（本仓目前零 GMS）② 是否接受 **Alpha** ③ 是否接受 stdlib/coroutines 被抬高 ④ 是否接受中文为 **beta** 档 |
| **阶段 1c：ML Kit GenAI ASR（Advanced）** | ❌ 本仓当前不现实 | 同上但 `MODE_ADVANCED`，中文正式 locale、质量更好 | **设备面极窄**：Pixel 10/11 系 + 特定 Gemini Nano 芯片，且不支持 unlocked bootloader 设备；本仓蓝叠/普通真机大概率不满足 | 是否有目标机型采购/验证计划 |
| **阶段 2a：云端 ASR（Google / 百度 / 阿里 / 讯飞）** | ✅ 技术可行、需拍板 | 录音 → 厂商 SDK/WebSocket 流式 → 文本 → 现有文字链路；补 `INTERNET` | 无 compileSdk 门槛；但本仓网络层尚在选型，SDK 与网络层可能重复 | ① **数据出站**（音频发到谁、是否合规/出境）② **费用**（按分钟/按次数）③ 供应商 ④ 国内可达性 ⑤ 是否接受音频离开设备 |
| **阶段 2b：端侧开源模型** | ✅ 技术可行、需拍板 | whisper.cpp / sherpa-onnx / Vosk 任选，NDK/JNI + 中文模型 + VAD/流式切片 | 无 compileSdk 门槛（AAR 均 =1）；但**体积数量级冲击**（13.5MB~50MB AAR + 27MB~142MB+ 模型 vs 804KB 基线）、R8 需补 JNI keep、NDK 集成、自行维护录音/线程 | ① **是否接受 APK 体积从 804KB 涨到几十 MB**（与 ADR-0007 包体积敏感直接冲突）② 模型随包还是首次下载 ③ 中文准确率门槛 ④ 低端机延迟/内存（whisper tiny 仍 ~273MB 运行内存，见 [whisper.cpp README](https://github.com/ggml-org/whisper.cpp/blob/master/README.md)） |

**硬阻断汇总（明确）**：① 本机 SDK 仅 `android-36` → 任何 `minCompileSdk=37` 依赖硬阻断，**但本次调研的 STT 路线没有触碰它**；② `createOnDeviceSpeechRecognizer` 在 API 26–30 硬不可用；③ ML Kit GenAI Advanced 在绝大多数设备硬不可用；④ `whisper.tflite` 上游已弃维护 + NNAPI 在 Android 15 弃用；⑤ 本仓 manifest 缺 `RECORD_AUDIO`/`INTERNET`/`<queries>` 是「必补前置」，不是阻断。

---

## 7. 未验证清单（没从一手来源确认的，一律不写成结论）

1. **构建能否通过**：本次严格未跑 `gradlew`、未改构建文件。§6 的 `minCompileSdk` 判定基于「解包 AAR 元数据 + AGP 校验机制」，**没有在本工程实测 `assembleDebug`**。ML Kit GenAI ASR 是否真能在 AGP 9.2.1 / compileSdk 36 / Kotlin 2.3.0 下编译通过，未验证。
2. **ML Kit GenAI 传递依赖的版本抬升后果**：`kotlin-stdlib 2.4.0` / `coroutines 1.11.0` 配 Kotlin 2.3.0 编译器、AGP 9 内置 Kotlin，是否产生警告或构建/运行差异，未实测（参考 `docs/research/android-network-stack-candidates.md` §4.3 的元数据兼容实测，但组合不同）。
3. **`genai-speech-recognition` 全部传递 AAR 的元数据**：本次只解了 POM 直接列出的 GMS/MLKit AAR（base/basement/tasks/common/genai-common 与本体）；**firebase datatransport / firebase-encoders 系列 AAR 未逐个解包** `minCompileSdk`，不能断言「整棵依赖树无 37」。
4. **ML Kit GenAI ASR 的商用许可条款**：只取到「Alpha、无 SLA、GenAI API Terms / Android Data Disclosure 链接」，**未读 Terms 正文**；商用限制、数据披露细则未验证。
5. **旧「ML Kit speech recognition 测试版」的确切下线时间与公告**：本次只从一手来源确认「现行产品清单/迁移指南不含它、Firebase ML 将于 2027-06-15 停用、group-index 无经典 speech artifact」，**未取到「某日下线」的原始公告**，因此写成「不在现行清单」而非「已下线于 X 日」。
6. **系统识别服务在中文下的实际表现**：`EXTRA_LANGUAGE=zh-CN` 是否可用、`EXTRA_PREFER_OFFLINE` 是否真生效、端侧服务是否带中文语言包——官方文档明确「up to the particular implementation / not required」，**只能真机实测**，未验证。
7. **特定设备/ROM 的默认识别服务可用性**：Android 12 起默认语音输入被设为 Android System Intelligence 导致第三方 `SpeechRecognizer` 报 `ERROR_CLIENT`、需手动改回「Speech Services by Google」的说法来自 StackOverflow（二手线索），**未从 AOSP/官方取到**，不写成结论；但它提示「蓝叠/无 GMS 设备可能没有可用服务」这一风险要真机确认。
8. **GMS 依赖的可接受性**：本仓目前零 GMS。ML Kit GenAI 路线会不会因 GMS/AICore 缺失而在目标设备（尤其国内 ROM、蓝叠）完全不可用，未验证。
9. **国内云厂商 SDK 的工程参数**：百度/阿里/讯飞 Android SDK 的 `minSdk`、AAR/`minCompileSdk`、jar/.so 体积、离线听写授权与资源体积、许可字段，本次只确认官方文档可达与能力形态，**未逐一取到明文**，全部未验证。
10. **Google Cloud STT 在中国的网络可达性 / 数据出境合规**：非技术事实，未验证，需人拍板。
11. **端侧模型的真机延迟、内存、中文 WER**：whisper.cpp/sherpa-onnx/Vosk 的体积取自官方 README/models 表，但**未在本仓目标设备跑过**；中文小模型「27MB」等数字来自第三方汇总（[wmkeyboard wiki](https://github.com/wasi-master/wmkeyboard/wiki/Offline-voice-(Sherpa))），二手，未在官方模型表核实；whisper tiny 运行内存 ~273MB 亦为官方 README 值、未在真机复核。
12. **sherpa-onnx / Vosk 的 NNAPI / GPU 加速现状**：sherpa-onnx 走 ONNX Runtime（有 QNN/NPU 说明），Vosk 走 Kaldi；「是否用 NNAPI delegate」本次未从官方取到明确结论。TFLite GPU delegate 对 Whisper `WHILE` 算子不兼容来自 [TensorFlow issue](https://github.com/tensorflow/tensorflow/issues/62867)（一手 issue 但非官方承诺），仅作线索。
13. **R8 后真实 APK 增量**：本文所有体积都是「下载/制品字节」，**没有 `assembleRelease` 实测**；ML Kit GenAI 的 1.24MB、Vosk 13.5MB、sherpa 50MB、TFLite 6.6MB 都不是 R8 后增量。
14. **`RecognizerIntent` 路线在 targetSdk 36 下 `startActivityForResult` 的具体行为**（是否被系统 UI 接管、返回 `EXTRA_RESULTS` 的形态），未真机验证。
15. **Android 12 麦克风隐私开关 / 指示器对录音的具体拦截行为**：本次从 `about/versions/12/behavior-changes-12` 只取到页面可达与分类信息，**未取到「开关关闭时录音被静音」的原文**，故正文只写「系统加入麦克风/摄像头隐私开关与指示器」这一层，不展开拦截语义。
16. **`POST_NOTIFICATIONS` 与 FGS 的具体交互细节**：仅标注「影响 FGS 通知可见性」，未逐字取到相关段落，未验证。
17. **本仓是否已有「向后端 agent 提问」的代码入口**：`GLOSSARY.md` 只定义了产品概念，网络层与后端契约尚在选型（`docs/research/backend-plus-app-contract.md` / `docs/research/android-network-stack-candidates.md`），本次未读全部契约文件，正文按「存在/既定一个纯文字入口」的抽象接缝描述，未指认具体类或函数。

---

## 8. 取证通道说明（本次实际怎么取的证）

| 通道 | 用途 | 状态 |
|---|---|---|
| `pwsh` 查 WinINET | 确认系统代理 `127.0.0.1:7890` 已开、env 无 `HTTPS_PROXY`（命中 KB《Windows 系统代理不被 curl/urllib/web_fetch 继承》） | ✅ 可用 |
| Python `urllib` + 显式 `HTTPS_PROXY=http://127.0.0.1:7890`（临时脚本在 `%TEMP%`，**不落盘到工作区**） | `developer.android.com` 参考页、`developers.google.com/ml-kit`、`cloud.google.com`、Google Maven AAR/POM、GitHub raw README/LICENSE 全部 200 | ✅ 主通道 |
| `dsh_browser_open` | `developer.android.google.cn` 镜像渲染正常（标题「SpeechRecognizer \| API reference」），与 `.com` 同源 | ✅ 可用（镜像备选） |
| `web_search` / `read_page` | 仅作线索发现；`read_page` 的 firecrawl 引擎返回 429、local 引擎因页面 >2MB 失败，故**结论不依赖它** | ⚠️ 仅线索 |
| 裸 `curl` / 裸 `urllib` / `web_fetch` | 对 `developer.android.com` 超时（代理未继承），`curl -x` 还撞 schannel `SEC_E_NO_CREDENTIALS` → **不要据此判「官方没这个 API」**（KB 已记录的坑） | ❌ 不可用 |
| `ops_bash` | 本会话 bash 沙箱 `EXECUTOR_UNAVAILABLE`（CreateFileMapping Win32 error 5），未使用；文件操作改用 `pwsh` + 原生 `read`/`write` | ❌ 不可用 |
| Google Maven `group-index.xml` + AAR 内存解包 | `minCompileSdk` 实测（§6.1），方法来自本仓 KB playbook | ✅ 可用，`dl.google.com` 裸连可达 |

---

## 9. 参考资料（分组，每条可点击 URL；均为一手来源或明确标注的二手线索）

### 本仓文件（一手事实，已实读）
- `app/build.gradle.kts`:8-13（compileSdk/targetSdk/minSdk）、:27-41（R8/资源收缩）、`app/src/main/AndroidManifest.xml`（权限现状）
- `GLOSSARY.md`:55-57（农小屿定义）
- `docs/adr/0006-冷启动只读本地皮肤包不碰网络.md`（冷启动约束边界）
- `docs/adr/0007-整体性能优化排序与放行门槛.md`:3（804KB / 3.03 倍基线）
- `docs/research/compose-ai-orb-button.md`（`minCompileSdk=37` 硬阻断先例）
- `docs/research/android-network-stack-candidates.md`:20（manifest 无 `INTERNET`、AAR 解包方法与 Kotlin 元数据兼容实测）
- `docs/research/probe-debug-vs-release-baseline.md`、`docs/research/backend-plus-app-contract.md`（接缝背景）

### Android 平台 API（developer.android.com，亦可用 `developer.android.google.cn` 镜像）
- [`android.speech.SpeechRecognizer` 参考页](https://developer.android.com/reference/android/speech/SpeechRecognizer)（API level、`createOnDeviceSpeechRecognizer`/`isOnDeviceRecognitionAvailable`/`isRecognitionAvailable`、`<queries>`、错误常量、主线程要求、remote servers 说明）
- [`android.speech.RecognizerIntent` 参考页](https://developer.android.com/reference/android/speech/RecognizerIntent)（`ACTION_*`、`EXTRA_*`、`EXTRA_PREFER_OFFLINE` API 23、BCP-47 `EXTRA_LANGUAGE`）
- [`android.speech.RecognitionService` 参考页](https://developer.android.com/reference/android/speech/RecognitionService)（服务实现基类、`SERVICE_INTERFACE="android.speech.RecognitionService"`）
- [`Manifest.permission` 参考页](https://developer.android.com/reference/android/Manifest.permission)（`RECORD_AUDIO` since API 1）
- [权限总览](https://developer.android.com/guide/topics/permissions/overview) / [请求运行时权限](https://developer.android.com/training/permissions/requesting)（dangerous / runtime）
- [包可见性总览](https://developer.android.com/training/package-visibility) / [`<queries>` 元素](https://developer.android.com/guide/topics/manifest/queries-element)（Android 11 / API 30）
- [前台服务 while-in-use 限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) / [FGS 类型](https://developer.android.com/develop/background-work/services/fgs/service-types)（Android 14 microphone 校验）
- [Android 12 行为变更](https://developer.android.com/about/versions/12/behavior-changes-12) / [Android 13 通知权限](https://developer.android.com/about/versions/13/changes/notification-permission) / [Android 14 FGS 变更](https://developer.android.com/about/versions/14/changes/fgs-types)
- [NNAPI](https://developer.android.com/ndk/guides/neuralnetworks) / [NNAPI 迁移指南（Android 15 弃用）](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)

### ML Kit / Google Play 服务（developers.google.com + Google Maven）
- [ML Kit GenAI Speech Recognition API（Android）](https://developers.google.com/ml-kit/genai/speech-recognition/android)（Alpha、Basic/Advanced、minSdk 26、流式、Supported languages 含 `cmn-Hans-CN`/`cmn-Hant-TW`、Supported devices、AICore、坐标）
- [ML Kit GenAI APIs 总览](https://developers.google.com/ml-kit/genai)（AICore / Gemini Nano、设备支持表）
- [ML Kit release notes](https://developers.google.com/ml-kit/release-notes)（`genai-speech-recognition 1.0.0-alpha2` 10/26/2026、「released GenAI Speech Recognition API」）
- [ML Kit 迁移指南](https://developers.google.com/ml-kit/migration)（现行 on-device API 清单、Firebase ML 2027-06-15 停用） / [Android 迁移](https://developers.google.com/ml-kit/migration/android)
- [Google Maven `com/google/mlkit/group-index.xml`](https://dl.google.com/dl/android/maven2/com/google/mlkit/group-index.xml)
- [`genai-speech-recognition-1.0.0-alpha2.pom`](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-speech-recognition/1.0.0-alpha2/genai-speech-recognition-1.0.0-alpha2.pom) / [`.aar`](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-speech-recognition/1.0.0-alpha2/genai-speech-recognition-1.0.0-alpha2.aar)（GMS / stdlib 2.4.0 / coroutines 1.11.0 / minSdk 26 / 1.24MB）
- [`genai-common-1.0.0-beta5.aar`](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-common/1.0.0-beta5/genai-common-1.0.0-beta5.aar)（AICore `<queries>`）/ [`common-18.11.0.aar`](https://dl.google.com/dl/android/maven2/com/google/mlkit/common/18.11.0/common-18.11.0.aar)
- [`play-services-basement-18.9.0.aar`](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-basement/18.9.0/play-services-basement-18.9.0.aar)（`minCompileSdk=1`）/ [base](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-base/18.5.0/play-services-base-18.5.0.aar) / [tasks](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-tasks/18.2.0/play-services-tasks-18.2.0.aar)

### 云端 ASR（官方文档）
- [Google Cloud STT 语言支持](https://cloud.google.com/speech-to-text/docs/languages)（`cmn-Hans-CN` 等）
- [Google Cloud STT 流式识别](https://cloud.google.com/speech-to-text/docs/streaming-recognize) / [定价](https://cloud.google.com/speech-to-text/pricing) / [data logging](https://cloud.google.com/speech-to-text/docs/data-logging)
- [百度智能云语音识别能力说明](https://cloud.baidu.com/discover/guide-speech-language.html) / [百度语音文档](https://cloud.baidu.com/doc/SPEECH/s/8k38lxz8o)
- [阿里云实时语音识别](https://help.aliyun.com/zh/isi/getting-started/one-sentence-recognition)
- [讯飞语音听写 Android SDK](https://www.xfyun.cn/doc/asr/voicedictation/Java-SDK.html)

### 端侧开源模型（官方 GitHub README / LICENSE / release）
- [whisper.cpp README](https://github.com/ggml-org/whisper.cpp/blob/master/README.md)（Android/Java、Vulkan、VAD、内存表 tiny 75MiB…） / [LICENSE(MIT)](https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE) / [models README](https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md)（ggml 模型体积）
- [usefulsensors/openai-whisper (whisper.tflite) README](https://github.com/usefulsensors/openai-whisper/blob/main/README.md)（**Deprecated**）
- [tensorflow-lite 2.13.0 AAR](https://repo1.maven.org/maven2/org/tensorflow/tensorflow-lite/2.13.0/tensorflow-lite-2.13.0.aar)
- [sherpa-onnx README](https://github.com/k2-fsa/sherpa-onnx/blob/master/README.md)（streaming/non-streaming、中文模型、Android） / [LICENSE(Apache-2.0)](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) / [Android 文档](https://k2-fsa.github.io/sherpa/onnx/android/) / [Build for Android](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html) / [SherpaOnnxAar 构建说明](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/android/SherpaOnnxAar) / [v1.13.8 AAR](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- [Vosk README](https://github.com/alphacep/vosk-api/blob/master/README.md)（offline、20+ 语言含中文、~50MB、streaming） / [COPYING(Apache-2.0)](https://github.com/alphacep/vosk-api/blob/master/COPYING) / [模型表 models.md](https://github.com/alphacep/vosk-space/blob/master/models.md)（`vosk-model-small-cn-0.22` 42M） / [vosk-android 0.3.75 AAR](https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar)

### 仅作线索、未作结论依据（二手 / 待核实）
- [wmkeyboard wiki: sherpa 模型体积/许可汇总](https://github.com/wasi-master/wmkeyboard/wiki/Offline-voice-(Sherpa))（「Zipformer Chinese Small 27MB」等，未在官方表核实）
- [StackOverflow: Android 12 默认识别服务导致 `ERROR_CLIENT`](https://stackoverflow.com/questions/69827554/why-did-my-android-app-using-speechrecognizer-stop-working-after-upgrading-to-an)（默认语音输入设置问题，未从 AOSP 取证）
- [TensorFlow issue #62867: Whisper TFLite GPU/NNAPI delegate 算子不兼容](https://github.com/tensorflow/tensorflow/issues/62867)
- [StackOverflow / grpc-java issue: Cloud STT V2 `cmn-Hans-CN` + chirp_2 流式 CANCELLED](https://github.com/grpc/grpc-java/issues/12072)（提示具体模型/区域组合要实测，未写成结论）
