# AGENTS.md — nongyu-app-plus

本文件给在本仓库工作的 AI Agent 提供约定。**只写事实与规则，不写教程。**

## 项目概况

- **这是什么**：农屿 App 翻新版的 Android 原生客户端，Kotlin + Jetpack Compose。
- **技能套件**：本仓库使用 mattpocock-skills 工程技能套件，配置见 `docs/agents/`。

## 构建与验证

```bash
./gradlew :app:assembleDebug          # 调试包
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Windows 下用 `gradlew.bat`。详细版本约束与踩坑见 `README.md`（尤其 Compose BOM 版本锁定的原因）。

## Agent skills

### Issue tracker

Issues live as GitHub issues in `NongyuSystem/nongyu-app-plus`, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles use their default strings, unchanged (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

single-context. See `docs/agents/domain.md`.
