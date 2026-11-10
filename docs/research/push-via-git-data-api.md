# github.com 不可达时如何推送：GitHub Git Data API 手工重建提交

> 记录时间：2026-11-10 · 仓库 `NongyuSystem/nongyu-app-plus`

## 什么时候用这个

**判断依据只有一个：两个域名分别探测。**

```powershell
curl.exe -s -m 8 -o NUL -w "github: %{http_code}`n" https://github.com
curl.exe -s -m 8 -o NUL -w "api: %{http_code}`n" https://api.github.com
```

- 两个都 200 → 正常 `git push`，不要用本文方案。
- `github.com` 返回 `000` 而 `api.github.com` 返回 `200` → **本文方案适用**。

本次实测就是后者：`github: 000` / `api: 200`。
表现是所有 `git push` 报 `Failed to connect to github.com port 443`
或 `Recv failure: Connection was reset`，**重试无效**。

### 不要走的弯路

| 尝试 | 结果 | 原因 |
| --- | --- | --- |
| `git push` 重试 8 次 | 全失败 | 域名级阻断，重试无用 |
| `git -c credential.helper='!gh auth git-credential'` | 仍失败 | 只解决认证，传输目标仍是 github.com |
| `powershell -File push.ps1` | 解析报错 | PS 5.1 按 GBK 读 UTF-8 无 BOM 脚本，中文注释里的引号破坏语法 |
| `pwsh -File push.ps1` | 命令不存在 / 执行策略拒绝 | 本机无 PowerShell 7，且未签名脚本被拦 |

**结论：用 Python 写，用 `gh` 执行，JSON 走 stdin。**

## 做法（四步重建一个提交）

Git Data API 是「手工 git」：blob → tree → commit → 移动 ref。
只需要处理**本地相对远端多出来的那些提交所改的文件**。

```python
# 1) 取本地头与父提交，读远端分支
head   = run(["git", "rev-parse", "HEAD"])
parent = run(["git", "rev-parse", "HEAD^"])
remote = gh_api(["repos/{REPO}/branches/master"])["commit"]["sha"]

# 关键断言：远端必须等于本地父提交，否则中止（防覆盖他人提交）
assert remote == parent

# 2) 变更文件 → 建 blob（base64）
for path in run(["git","diff","--name-only",parent,head]).splitlines():
    content = base64.b64encode(open(path,"rb").read()).decode("ascii")
    blob = gh_api(["--method","POST","repos/{REPO}/git/blobs"],
                  {"content": content, "encoding": "base64"})

# 3) 建 tree（base_tree = 父提交的 tree）
tree = gh_api(["--method","POST","repos/{REPO}/git/trees"],
              {"base_tree": parent_tree, "tree": blob_items})

# 4) 建 commit 并移动 ref（force=False）
commit = gh_api(["--method","POST","repos/{REPO}/git/commits"],
                {"message": msg, "tree": tree["sha"],
                 "parents": [remote], "author": {...}})
gh_api(["--method","PATCH","repos/{REPO}/git/refs/heads/master"],
       {"sha": commit["sha"], "force": False})
```

### 关键实现细节

1. **内容必须从 git 对象库取，不能读工作区文件**（最容易踩的坑）：
   ```python
   raw = subprocess.run(["git", "cat-file", "blob", blob_sha], capture_output=True).stdout
   ```
   Windows 上工作区通常是 **CRLF**，而仓库对象里存的是 **LF**（`core.autocrlf`）。
   直接 `open(path,"rb").read()` 会把 CRLF 传上去 —— 文件「看起来一样」，
   但 tree sha 对不上，远端内容实际被改坏了。
   本次实测：工作区 401 行全是 CRLF，而 HEAD 里的 blob 是 LF，
   修复前 tree = `8487312`，改用 `git cat-file` 后 tree = `46fa2bd`，与本地完全一致。
2. **中文路径要加 `-c core.quotepath=false`**：否则 `git ls-tree -r` 会把
   `docs/adr/0001-调试链路.md` 输出成 `"docs/adr/0001-\350\260\203..."`，
   后续 `open()` 直接报 `OSError: [Errno 22] Invalid argument`。
3. **不要用 `git rev-list remote..head` 找待推提交**：API 建出的远端提交与本地
   不共享历史，`rev-list` 会报 `fatal: Invalid revision range`。
   正确做法是**按 tree 差异推**：递归拉两边文件清单，比对 blob sha，
   只上传「新增 / 内容不同」的路径，删除「远端有本地没有」的路径。
4. **Python 输出中文设 `PYTHONIOENCODING=utf-8`**，否则 Windows 控制台用 GBK 编码 stdout 会抛异常。
5. **JSON 用 stdin 传**：`gh api --input -`，Python 里 `subprocess.run(..., input=json_bytes)`。
   这能彻底避开 shell 引号转义和临时文件编码两个坑。
6. **`force: false`**：让远端自己拒绝非快进更新，多一层保险。
7. **比对 tree sha 判断内容一致**，不要比 commit sha —— 后者因 author 时间戳等元数据
   不同必然不同，这是正常的，不是失败。

## 验证

本次输出：

```
local head : 6aac437
parent     : aedfb64
remote     : aedfb64          ← 断言通过
changed    : app/src/main/java/com/nongyu/appplus/ui/timetable/TimetableGrid.kt
  blob ... -> f896ccc
tree       : 802435a
commit     : d16338a
master     -> d16338a
feat       -> d16338a
DONE
```

事后 `gh api repos/.../branches/master` 读到 `d16338a`，提交信息正确。
注意远端 sha `d16338a` 与本地 `6aac437` 不同 —— 元数据差异，内容一致。

## 适用边界

- 仅适用于「github.com 不通、api.github.com 通」这一组合；两个都不通则本方案无效。
- 需要 `gh` 已登录且有 repo 写权限。
- 分支开启保护规则时，`PATCH refs` 可能被拒，需先确认规则。
- 这是**应急通道**，网络恢复后仍应回到正常 `git push`。
