# AGENTS.md — nongyu-app-plus

本文件给在本仓库工作的 AI Agent 提供约定。**只写事实与规则，不写教程。**

## 项目概况

- **这是什么**：农屿 App 翻新版的 Android 原生客户端，Kotlin + Jetpack Compose。
- **技能套件**：本仓库使用 mattpocock-skills 工程技能套件，配置见 `docs/agents/`。

## 构建与验证

```bash
./gradlew :app:assembleRelease        # 发布包（R8 + 资源收缩 + 不 debuggable）
# 产物：app/build/outputs/apk/release/app-release.apk
```

调试时仍可用 `./gradlew :app:assembleDebug`（不收缩、断点/堆栈可读），但**对外发布的产物必须是 release 包**。

Windows 下用 `gradlew.bat`。详细版本约束与踩坑见 `README.md`（尤其 Compose BOM 版本锁定的原因）。

### 打包发布流程（强制）

**每次打包都必须同时做三件事，缺一不可**：

1. **递增版本号**：`app/build.gradle.kts` 的 `versionCode` 与 `versionName` 都要更新（`versionCode` 必须严格递增才能覆盖安装）。
2. **提交到 GitHub**：按全局 AGENTS.md 的提交规范 `git add .` + `git commit`，然后推送。
   - 本机 `git push` 可能被重置（`Recv failure: Connection was reset`）。此时用 `gh api` 走 GitHub Git Data API：
     建 blob → create tree（`base_tree` = 远端 HEAD 的 tree sha）→ create commit（`parents` = 远端 HEAD）→ PATCH `git/refs/heads/master`。
     文件用 `content=base64` + `encoding=base64`，必须经 `--input JSON 文件` 提交（`-f` 会把内容当字面量）。
   - 可复现命令见 `docs/research/build-0.0.1test.md`。
3. **建 GitHub Release 并上传 APK**：`gh release create v<versionName> app/build/outputs/apk/release/app-release.apk --target master`，tag 用 `v<versionName>`。

版本号、Release tag、APK 三者必须一一对应。

**切 release 后必须实装验证不崩**：R8 的风险不是「构建失败」（那会立刻报错），而是「构建成功但运行时某个入口崩」——只有装到设备上才发现。发版前至少手工过一遍：四个 Tab 切换、AI 球（长按/点按/返回）、输入框展开收起、返键退出。`proguard-rules.pro` 里已写明「将来引入反射 / 序列化 / JNI / XML 自定义 View 时必须回来补 keep」。

## Agent skills

### Issue tracker

Issues live as GitHub issues in `NongyuSystem/nongyu-app-plus`, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles use their default strings, unchanged (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

single-context. See `docs/agents/domain.md`.
