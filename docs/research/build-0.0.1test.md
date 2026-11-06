# 0.0.1test 构建记录：绕开全局 init.d 与 wrapper 半截 zip

> 2026-11-06。目的：把农小屿三态概念稿（prototype/ai-orb-concept.html v2）落到 Android 工程，产出可安装调试包。

## 结果

```
BUILD SUCCESSFUL in 24s
36 actionable tasks: 13 executed, 23 up-to-date
```

产物 `app/build/outputs/apk/debug/app-debug.apk`（11,784,432 字节）。
`aapt2 dump badging` 校验：`package: name='com.nongyu.appplus' versionCode='2' versionName='0.0.1test' ... compileSdkVersion='36'`。
`output-metadata.json` 同值。**versionCode 从 1 升到 2**：Android 覆盖安装要求 versionCode 递增，即便 versionName 看起来是「降」到测试版。

## 可复现构建命令（PowerShell）

本机 `gradlew.bat` 直接跑会连续失败，原因见下。稳定路径是**直接调用已完整解压的 gradle.bat + 干净 GRADLE_USER_HOME**：

```powershell
Set-Location "D:\IT\program\web-program\nongyu-system\apps\nongyu-app-plus"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:ANDROID_HOME = "C:\Users\LENOVO\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $env:TEMP "nongyu-gradle-home"   # 干净目录，无 init.d
$gradle = "C:\Users\LENOVO\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat"
& $gradle :app:assembleDebug --no-daemon --console=plain --offline
```

前置准备（只需一次）：
1. 默认 `~/.gradle/init.d/aliyun-mirrors.gradle` 里有 `allprojects { repositories { ... } }`，与工程 `FAIL_ON_PROJECT_REPOS` 冲突 → 必须让 `GRADLE_USER_HOME` 指向**没有 init.d 的目录**。`--init-script` 传空脚本**不能**屏蔽 init.d（实测仍执行）。空 init 脚本若用 `Set-Content -Encoding UTF8` 带 BOM，Gradle 会报 `Unexpected character`，需用 `UTF8Encoding($false)` 无 BOM 写入。
2. `%TEMP%\nongyu-gradle-home\wrapper\dists\gradle-9.4.1-bin\<hash>\` 里原本只有 `gradle-9.4.1-bin.zip.part`（52MB）与 `.lck` → 删除这两个文件，把默认 home 里**完整解压**的 `gradle-9.4.1\` 目录整个复制过去。
3. 即便补齐解压目录，`gradlew.bat` 仍可能因校验 zip 再去下载并超时 → 直接调用 `gradle.bat` 并加 `--offline`，跳过 wrapper 下载逻辑。

排障判定：日志/进程 5 分钟无文件写入且 CPU 不涨 = 锁死（残留 gradle client 抢同一 home），按命令行含 `gradlew|gradle_home` 精确 kill，勿误杀用户其它 java 服务。长时间构建用 `*> $log` 落盘再 `Get-Content -Tail`，别用 `Out-String` 缓冲。

## 本次代码改动清单

| 文件 | 内容 |
| --- | --- |
| `app/build.gradle.kts` | versionCode=2 / versionName=0.0.1test |
| `local.properties` | `sdk.dir=C:/Users/LENOVO/AppData/Local/Android/Sdk`（正斜杠；已在 .gitignore，不提交） |
| `ui/theme/Theme.kt` | ADR-0002 落地：`NongyuColors` 分组色板 + `LocalNongyuColors` + 固定浅色（移除 darkTheme / dynamicColor） |
| `ui/ai/NongyuOrb.kt` | 星云球：API 33+ AGSL(fbm+域扭曲) / <33 Canvas 降级；`withFrameNanos` 累加相位、只在 draw 阶段读；紧贴球缘绿→透明外发光；无白椭圆高光、无白圆环描边；AGSL 失败自动降级 |
| `ui/MainShell.kt` | 贴底栏 2+2 分列 + 正中凸起球；静态/长按(加速+微放大+全屏dim)/点按(扩开覆盖导航栏→四按钮淡出→输入框，无左侧圆点)；返回键退出；零依赖 rememberSaveable |
| `ui/NongyuApp.kt` | 挂载 MainShell |

## 仍待验证（需真机）

- AGSL 字符串无编译期校验，运行时是否正确渲染（已加 try/catch 降级，但真机未跑）；
- minSdk 26 降级路径的视觉观感；
- 星云「乱而有序」在真机上的实际效果与帧率；
- 输入框形变动画按维护者意见仅做了基础版，真形变留到实现阶段。

## 边界

- 语音转文字 / 后端 agent / 网络层**未接入**（用户「之后的事后面会说」；README 明确无网络层）。
- 按 monorepo git 纪律，本轮只改文件、未提交。
