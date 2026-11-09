# 仓库整理记录 2026-11-09

## 为什么有这个文件

整理仓库时发现一个**未合并的 stash**，里面有旧版课表实现。
删除前先把它的内容原样导出为 patch 存档，避免「整理」变成「丢东西」。

## 被存档的 stash

```
stash@{0}: On master: 其他会话改动：README/MainShell/timetable/run.ps1/docs-preview（开 integration branch 前暂存）
commit 730bdef
```

内容（3 个文件，+79 −5）：

| 文件 | 变化 |
| --- | --- |
| `.gitignore` | +9 |
| `README.md` | +59 |
| `ui/MainShell.kt` | +16 −? / 被后来的 #15 实现取代 |

## 为什么没有直接合并回来

`MainShell.kt` 里那份是**旧版课表接入**：

- 旧：`TimetableGrid(bottomInset = navBottomInset)`，用一个算出来的 dp 常量做底部避让。
- 现：#15 已放行并推送到 `feat/timetable-step1-skeleton`，
  改用 `navTopY`（`onGloballyPositioned` **实测**底栏顶边）做避让，
  因为栏高/内边距/星云球凸起都乘了 `uiScale`，写死常量在别的屏宽上必错。

因此旧实现**已被取代**，合回来只会冲突并退回旧方案。
但它的 README 段落与 `.gitignore` 那 9 行可能仍有价值，故保留 patch 备查。

## 恢复方式

```bash
git apply docs/archive/stash-backup-20261109.patch          # 应用到工作区
# 或逐文件看差异：
git show stash@{0} -- README.md
```

另外把原 stash 提交固定成了本地分支 `backup/stash-2026-11-09`，
所以即使 stash 被 drop（本次未 drop），内容仍可从该分支找回：

```bash
git checkout backup/stash-2026-11-09 -- README.md
```

## 本次整理的其他动作

- `.gitignore` 补 `.worktrees/`：此前只靠本机 `.git/info/exclude` 忽略，
  换机器后 worktree 目录会被当成上千个待提交文件。
- 移除两个已推送的 worktree 工作副本（分支与提交全部保留在本地与远端）：
  - `feat/issue55-splashscreen` @ `a3d0082`
  - `feat/timetable-step1-skeleton` @ `83b0738`

## 已知待办（不阻塞）

- `net/auth/MinimalSessionStore.kt` 有 9 处弃用警告：
  `MasterKey` / `EncryptedSharedPreferences` 在 AndroidX security-crypto 1.1.0 起被弃用。
  属网络层（地图 #41）范围，不在课表这条线里，未改动。
