# 蓝叠（BlueStacks 5）能不能用来评估 AI 圆按钮的动效手感

> 只做事实调研，未改动任何业务代码、未执行任何 git 命令、未安装任何东西。
> 本机数据采集时间：2026-11-06。命令一律 PowerShell（可直接粘贴）。
> 前作 `docs/research/idea-android-debugging.md` 已覆盖 emulator / 真机 / IDEA 链路，本文只补蓝叠，不重复其 §3/§4/§6。

---

## 1. 结论摘要

1. **能用来评估 AI 圆按钮的动效，但要限定在「Tiramisu64（Android 13）」这个实例上。** 本机已装 `Pie64`（Android 9 / API 28）与 `Tiramisu64`（Android 13 / API 33）两个镜像；两者都 ≥ minSdk 26，但**只有 Tiramisu64 能跑 R2 路线**（`RuntimeShader`/AGSL 需要 API 33，见 §5.3）。Pie64 上 AGSL 直接跑不了。
2. **用 Tiramisu64 → 可以评估：** 曲线形状、时长、位移/缩放幅度、颜色过渡、点击反馈的先后顺序、`enableEdgeToEdge` 的布局是否被状态栏/导航条压住。
3. **不能评估：** 逐帧掉落与 jank、真实触控延迟、120Hz 高刷下的手感、GPU 峰值性能。本机配置 `enable_vsync="0"`、`enable_high_fps="0"`、`max_fps="60"`（见 §2.3），帧节奏是「尽量跑但不对齐 vsync」，与真机垂直同步的时序根本不同。
4. **本机 ADB 已经是开着的**（`bst.enable_adb_access="1"`），端口 `5555`，只绑本机（`enable_adb_remote_access="0"`）。所以不用去设置里找开关，直接 `adb connect` 即可（§3）。
5. **IDEA 2025.3.2 认不出蓝叠**（没装 Android 插件），只能走命令行：`adb connect` → `gradlew :app:installDebug` → `am start` → `logcat`（§6）。
6. **明确推荐：先用 Tiramisu64 跑动效评审（今天就能做），但「手感定稿」必须回真机。** 蓝叠的价值是「不用等真机就能看效果」，不是「替代真机」。

---

## 2. 本机蓝叠实际状态

### 2.1 查到的（一手，来自注册表 / 安装目录 / 配置文件 / 运行日志）

| 项 | 值 | 证据 |
| --- | --- | --- |
| 产品 | **BlueStacks 5 China**（蓝叠中国站版） | 卸载项 `DisplayName = BlueStacks 5 China`，`Publisher = now.gg, Inc.` |
| 版本 | **5.22.170.6509** | 卸载项 `DisplayVersion`；`HKLM\SOFTWARE\BlueStacks_nxt_cn\Version`；`C:\Program Files\BlueStacks_nxt_cn\tag.txt` = `_nxt_cn` |
| 版本代号 | 国内版，注册表键带 `_cn` 后缀 | `HKLM\SOFTWARE\BlueStacks_nxt_cn` |
| 安装目录 | `C:\Program Files\BlueStacks_nxt_cn\` | `HKLM\...\InstallDir` |
| 数据/引擎目录 | `D:\Program Files\BlueStacks_nxt_cn\Engine\` | `HKLM\...\DataDir`（注意在 **D 盘**，不在 `C:\ProgramData`） |
| 配置目录 | `D:\Program Files\BlueStacks_nxt_cn\`（含 `bluestacks.conf`） | `HKLM\...\UserDefinedDir` |
| 配置文件 | `D:\Program Files\BlueStacks_nxt_cn\bluestacks.conf` | 已读取 |
| 已装镜像 | **`Pie64`、`Tiramisu64`** | `bst.installed_images="Pie64,Tiramisu64"` |
| 实例 1 | `Pie64` → 显示名 **"BlueStacks 5"** | `bst.instance.Pie64.display_name` |
| 实例 2 | `Tiramisu64` → 显示名 **"BlueStacks 1"** | `bst.instance.Tiramisu64.display_name` |
| 自带 adb | `HD-Adb.exe`，**Android Debug Bridge 1.0.36** | `HD-Adb.exe version` 实测输出 |
| 系统 adb | `platform-tools/adb.exe`，**1.0.41 / 34.0.5** | 已知前提 + 本文 §3 实测路径 |

**Android 版本的直接证据**（这是回答「API level 够不够」的根）：

两个实例的镜像目录名与官方离线安装器参数完全对应：

- 官方离线安装器参数（一手）：`--defaultImageName Pie64` → **Pie 64-bit（Android 9）**、`--defaultImageName Tiramisu64` → **Android 13**、`--defaultImageName Rvc64` → **Android 11**、`--defaultImageName Nougat64` → Nougat 64-bit。
  来源：<https://support.bluestacks.com/hc/en-us/articles/4402611273485-BlueStacks-5-offline-installer>
- 官方 Android 版本说明（一手）：Nougat 32/64-bit、Pie 64-bit（Android 9）、Android 11、Android 13。
  来源：<https://support.bluestacks.com/hc/en-us/articles/360058931031>
- `Tiramisu` 即 Android 13 的 AOSP 代号（`Build.VERSION_CODES.TIRAMISU` = 33）。
  来源：<https://developer.android.com/tools/releases/platforms>、<https://developer.android.com/reference/android/os/Build.VERSION_CODES>

### 2.2 没查到的（本机未找到，不要当结论）

| 项 | 状态 |
| --- | --- |
| 实例内 `ro.build.version.sdk` 的实际回读值 | **未验证**。蓝叠当前未运行、adb server 未启动，无法 `adb shell getprop`。这是唯一能把「镜像名 → API level」从推断变成实测的动作，命令见 §3.5。 |
| 当前实例的 GUI 端口显示 | **未验证**。需要启动实例后在「设置 → 高级」读，命令见 §3.4。 |
| 蓝叠设置里国内版 ADB 开关的中文名（UI 实拍） | **部分确证、未目视**。见 §3.2：我从国内版安装目录的二进制资源里逐字抽到了 `Android调试(ADB)`，但没打开 UI 对过。 |
| `C:\ProgramData\BlueStacks_nxt` | **本机不存在**。国内版数据目录在 D 盘（见 §2.1），不要在 C 盘找。 |
| BlueStacks 是否已登录 Google 账号 / 能否装 Play 版本 App | 无关本票，未查。 |

### 2.3 与本票直接相关的配置项（`D:\Program Files\BlueStacks_nxt_cn\bluestacks.conf`）

```
bst.enable_adb_access="1"          # ADB 总开关，已开
bst.enable_adb_remote_access="0"   # 远程访问，关（只允许本机连）
bst.feature.rooting="1"            # 全局 root 特性已开
bst.installed_images="Pie64,Tiramisu64"

bst.instance.Pie64.adb_port="5555"
bst.instance.Pie64.status.adb_port="5555"
bst.instance.Pie64.graphics_engine="aga"      # 高级图形（Advanced Graphics）
bst.instance.Pie64.graphics_renderer="vlcn"   # Vulkan & OpenCL/OpenGL 组合
bst.instance.Pie64.enable_high_fps="0"        # 高帧率未开
bst.instance.Pie64.max_fps="60"               # 上限 60
bst.instance.Pie64.enable_vsync="0"           # 垂直同步关 ← 手感评估的关键限制
bst.instance.Pie64.fb_width="1920" fb_height="1080" dpi="240"
bst.instance.Pie64.abi_list="x86,x64,arm,arm64"
bst.instance.Pie64.enable_root_access="0"

bst.instance.Tiramisu64.adb_port="5555"
bst.instance.Tiramisu64.graphics_engine="aga"
bst.instance.Tiramisu64.graphics_renderer="vlcn"
bst.instance.Tiramisu64.enable_high_fps="0"
bst.instance.Tiramisu64.max_fps="60"
bst.instance.Tiramisu64.enable_vsync="0"
bst.instance.Tiramisu64.fb_width="1920" fb_height="1080" dpi="240"
bst.instance.Tiramisu64.abi_list="x86,x64,arm,arm64"
bst.instance.Tiramisu64.enable_root_access="1"  # 这个实例 root 是开的
```

> 两个实例都写着 `adb_port="5555"` 并不代表它们能同时用 5555 —— 见 §3.3 的运行日志证据。

**运行日志的关键行**（`D:\Program Files\BlueStacks_nxt_cn\Logs\Player.log`，本次会话前就存在）：

```
PLR [Initializing] I: Scanning for available host adb port...
PLR [Initializing] I: NAT forwarding applied up with remoteAccess false adb hostPort 5555
GCALL Tiramisu64 [Ready] I: gcallEnableAdbRpc(1)
HCALL Tiramisu64 [Ready] I: hcallOnEnableAdbCompletedClbk : status = 0
```

两条硬结论：
- 端口是实例**启动时动态扫描**出来的（不是写死 5555），所以「5555 / 5565 / 5575」不是硬编码规则；
- `remoteAccess false` → 只绑**本机回环**，所以远端机器连不上是设计行为，不是防火墙问题。

---

## 3. 开启 ADB 与连接步骤

### 3.1 设置路径（官方文档口径）

**国际版（BlueStacks 5 英文界面）** — 官方一手：
1. 启动 BlueStacks 5；
2. 点顶部**汉堡图标 → Settings**，或侧边工具栏**齿轮图标**；
3. 切到 **Advanced** 标签页；
4. 打开 **「Android Debug Bridge」** 旁边的开关；
5. 点 **Save changes**。

来源：<https://support.bluestacks.com/hc/en-us/articles/23925869130381-How-to-enable-Android-Debug-Bridge-on-BlueStacks-5>（原文：*"Within the Settings menu, click on the 'Advanced' tab and enable the toggle beside 'Android Debug Bridge'"*）

同页官方还给了**端口怎么读**：开关下方显示一串数字，**最后 4 位就是 ADB 端口**（官方在蓝叠 4→5 迁移文里明确写了 "note down the last 4 digits... This is the ADB port for BlueStacks 5"，示例为 `5555`）。
来源：<https://support.bluestacks.com/hc/en-us/articles/360061342631-How-to-transfer-your-apps-from-BlueStacks-4-to-BlueStacks-5>

**国内版（蓝叠中国站，即本机这个 `_nxt_cn`）** — 蓝叠官方没有出中文版这一页的支持文档，所以界面用词我用的是**本机安装目录里的官方语言资源**：

- 该开关在国内版二进制里逐字登记为 **`Android调试(ADB)`**。
  证据：`C:\Program Files\BlueStacks_nxt_cn\HD-Player.exe`（与 `HD-MultiInstanceManager.exe` 同一份资源）中的 UTF-16**BE** 字符串表含 `Android调试(ADB)`，紧邻其英文对 `Android Debug Bridge (ADB)`。
- **所在标签页名**：英文 `Advanced` 在国内版对应 **`高级`**（同一二进制里 `高级` 出现 3 次，语料中无独立的 "高级设置" 面板名）。
- **图形设置所在的标签页名**：官方英文文档叫 **Graphics**；国内版对应 **`引擎`**。这不是猜的 —— 本机官方语言包 `Engine\translations.json`（`zh-CN`）里，BlueStacks 自己的提示文案写的是「您可以通过转到 **BlueStacks 设置 -> 引擎** 恢复到当前设置」。
- 保存按钮：英文 **Save changes** → 国内版 **`保存更改`**。

> ⚠️ **「在国内版 UI 里眼睛看到的就是这几个字」属于未验证**（没打开界面拍图）。若你在界面里找不到 `Android调试(ADB)`，就往「设置 → 高级」里翻；图形相关一定在「设置 → 引擎」。

**本机已经是开着的**，所以你其实**不需要做这一步**：

```powershell
Select-String -Path 'D:\Program Files\BlueStacks_nxt_cn\bluestacks.conf' -Pattern 'enable_adb|adb_port'
# 期望：bst.enable_adb_access="1"
```

### 3.2 端口与多实例规则

| 事实 | 依据 |
| --- | --- |
| 首个实例的默认端口是 **5555** | 官方「最后 4 位」示例为 5555；本机 conf + 日志 `hostPort 5555` |
| 端口在**实例启动时动态扫描分配**，不是硬编码 | 本机 `Player.log`：`Scanning for available host adb port...` |
| 官方文档**没有**给出多实例的递增步长 | 我在官方支持站未找到任何写明「步长为 10」的页面 |
| 「5555 / 5565 / 5575 …（步长 10）」是**社区与自动化工具的候选列表**，不是官方规则 | MAA 文档把 BlueStacks 5 的连接地址列为 `127.0.0.1:5555/5556/5565/5575/5585/5595/5554`：<https://docs.maa.plus/zh-cn/manual/connection.html> |

**所以：不要背步长，去读/扫实际端口。**

读端口（三选一，从最可靠到最省事）：

```powershell
# A. 最可靠：在实例里看「设置 → 高级 → Android调试(ADB)」开关下方那串数字的最后 4 位
#    （官方口径，界面上直接有）

# B. 从配置读（= 上次启动时用的端口，可能已过期）
Select-String -Path 'D:\Program Files\BlueStacks_nxt_cn\bluestacks.conf' -Pattern 'status\.adb_port'

# C. 扫正在监听的端口（实例必须已启动，这是「当下真实值」）
Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalPort -ge 5550 -and $_.LocalPort -le 5900 } |
  Select-Object LocalAddress, LocalPort, OwningProcess | Sort-Object LocalPort
# 再用 PID 反查是不是蓝叠：
Get-Process -Id <上面的 OwningProcess> | Select-Object Id, ProcessName
# 期望 ProcessName 形如 HD-Player

# D. 从日志确认（本机实测有这两行）
Select-String -Path 'D:\Program Files\BlueStacks_nxt_cn\Logs\Player.log' -Pattern 'adb hostPort'
```

### 3.3 从 PowerShell 接的完整序列

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# 0) 干净起手：先杀掉可能被蓝叠自带 adb 抢走的 server（原因见 §3.4）
& $adb kill-server

# 1) 连接（用 127.0.0.1，不要用 localhost —— 见下方「失败态」第 3 条）
& $adb connect 127.0.0.1:5555
# 期望输出：connected to 127.0.0.1:5555

# 2) 确认状态：必须是 device
& $adb devices -l
# 期望：127.0.0.1:5555    device    product:... model:... device:...

# 3) 拿到 serial 后，后面所有命令都加 -s（多实例/多设备时必须）
$serial = "127.0.0.1:5555"
& $adb -s $serial shell getprop ro.product.model
```

### 3.4 版本冲突：蓝叠自带 adb 与 platform-tools 抢 5037

**本机实测存在版本剪刀差**：蓝叠自带 `HD-Adb.exe` 是 **1.0.36**，SDK 的 `adb.exe` 是 **1.0.41**。两者都默认去占 **5037** 端口起 server，版本不一致时典型报错是：

```
adb server version (36) doesn't match this client (41); killing...
```

一旦互相 kill，表现就是「实例明明起来了，`adb devices` 却一直是 offline / 空」。这个坑在蓝叠自动化社区里有明确记录（system adb v1.0.41 与 HD-Adb v1.0.36 互杀）：
<https://github.com/Jordan231111/BluestacksRoot/commit/d601a2612eb4e7798e74780385e6cce0e8cf7e32>

**官方没有给出这一条的处理建议**（我在 BlueStacks 支持站没找到 adb 版本冲突的专门文章）——下面是按 adb 标准机制给的处置，不是官方指令：

```powershell
# 首选：只承认一套 adb。全流程只用 platform-tools 的 1.0.41，不要手动跑 HD-Adb.exe
& $adb kill-server
& $adb start-server
& $adb devices -l

# 如果必须同时用两套（例如别的工具硬绑 HD-Adb），用独立 server 端口隔离：
$env:ANDROID_ADB_SERVER_PORT = "15037"
& $adb -P 15037 start-server
& $adb devices -l
# 注意：设了 ANDROID_ADB_SERVER_PORT 之后，所有 adb 调用（包括 Gradle 里的）都要带同一个值
Remove-Item Env:ANDROID_ADB_SERVER_PORT   # 用完清掉，避免影响同窗口的 gradlew
```

> `ANDROID_ADB_SERVER_PORT` / `adb -P <port>` 是 adb 标准环境变量与参数（社区一手验证：<https://stackoverflow.com/questions/3836571/changing-default-port-i-e-5037-on-which-adb-server-runs>）。Google 官方 adb 文档只写了「所有 adb 客户端都用 5037 与 server 通信」，未列该环境变量：<https://developer.android.com/tools/adb>。

### 3.5 失败态逐个处理

| 现象 | 真正原因 | 处理 |
| --- | --- | --- |
| `cannot connect to 127.0.0.1:5555: 由于目标计算机积极拒绝，无法连接。(10061)` | ① 实例没启动完；② 端口不是 5555（去 §3.2 读真实端口）；③ `bst.enable_adb_access="0"`；④ 用了 `localhost` 而系统解析到 IPv6 `::1`，蓝叠只监听 IPv4 | 用 **`127.0.0.1`** 而非 `localhost`；等实例进入主界面再用 §3.2 的 `Get-NetTCPConnection` 取真实端口；确认 `enable_adb_access="1"` |
| `offline` | 通常是 **两个 adb server 打架**（§3.4），或实例还在开机 | `adb kill-server` → `adb start-server` → 重新 `connect`；仍 offline 就重启该实例 |
| `unauthorized` | adb 密钥未被接受 | 蓝叠一般不走授权弹窗（它直接开 adbd）；出现 unauthorized 说明连到了别的东西或密钥错乱：`Remove-Item "$env:USERPROFILE\.android\adbkey*"` 后 `adb kill-server` 重连 |
| 列表**完全空白** | 没 `connect`（TCP 设备不会自动出现）；或 ADB 开关被更新重置为关 | 先 `adb connect 127.0.0.1:<port>`，再 `adb devices -l`；确认 `enable_adb_access` |
| 能连上但 `adb shell` 卡死 / `error: closed` | server 与 adbd 版本/协议不匹配（§3.4） | 只保留一套 adb；`adb kill-server` 重来 |
| 远端机器连不上 | **设计如此**：`bst.enable_adb_remote_access="0"` → 只绑回环 | 需要远程时把 remote access 打开（对应 conf 键 `enable_adb_remote_access`；**UI 上那个开关的确切名称我没有查到，属于未验证**） |

> 「蓝叠默认只监听 IPv4 本机」这一条有本机一手证据：`enable_adb_remote_access="0"` + 日志 `remoteAccess false`。IPv6/`localhost` 解析坑是社区高频问题（<https://stackoverflow.com/questions/24598577/connect-bluestacks-to-android-studio>），本机未复现。

### 3.6 多开时选某个具体实例

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# 1) 把每个实例都连上（端口来自 §3.2 的实测值）
& $adb connect 127.0.0.1:5555
& $adb connect 127.0.0.1:5565

# 2) 看清单，用 -s 精确指定
& $adb devices -l
& $adb -s 127.0.0.1:5565 shell getprop ro.build.version.sdk

# 3) 想按「实例名」而不是端口来选，用 Multi-Instance Manager 的实例名对应端口做一张表
#    （实例名 = conf 里的 bst.instance.<NAME>.display_name）
Select-String -Path 'D:\Program Files\BlueStacks_nxt_cn\bluestacks.conf' `
  -Pattern 'instance\.(\w+)\.(display_name|status\.adb_port)'
```

多开管理器的启动入口本机存在：`C:\Program Files\BlueStacks_nxt_cn\HD-MultiInstanceManager.exe`。

---

## 4. API level 矩阵（对本工程 minSdk 26 的判定）

### 4.1 矩阵

| 蓝叠实例（镜像名） | Android 版本 | **API level** | ≥ 26？ | 能跑 AGSL(33+)？ | 来源 |
| --- | --- | --- | --- | --- | --- |
| `Nougat32` | Nougat **7.x**（社区普遍记 7.1.2） | **25**（若 7.0 则 24） | ❌ **否** | ❌ | 官方只写 "Nougat"，未写子版本（<https://support.bluestacks.com/hc/en-us/articles/360058931031>）；7.0=24 / 7.1=25 见 <https://developer.android.com/tools/releases/platforms> |
| `Nougat64` | 同上 | **25**（或 24） | ❌ **否** | ❌ | 同上；**5.22.288 起不再允许新建** |
| `Pie64` | Android **9** Pie | **28** | ✅ 是 | ❌ | 官方 "Pie 64-bit is Android 9"；9=28 见 platforms 页 |
| `Rvc64` | Android **11** | **30** | ✅ 是 | ❌ | 官方离线安装器参数 `--defaultImageName Rvc64` → Android 11；11=30 |
| `Tiramisu64` | Android **13** | **33** | ✅ 是 | ✅ **能** | `Tiramisu` = Android 13 代号；13=33 |
| （BlueStacks 16 / BlueStacks Desktop） | Android **16** | **36** | ✅ 是 | ✅ 能 | <https://support.bluestacks.com/hc/en-us/articles/49665096963085>、<https://bluestacks.com/blog/bluestacks-exclusives/bluestacks-16-en.html> |

**要点：**

- **本机两个实例都过线**（Pie64=28、Tiramisu64=33），minSdk 26 不是障碍。
- **只有 Nougat 系列不过线**（24/25 < 26）。Nougat 究竟是 7.0 还是 7.1 我没在官方文档里查到确切子版本，但**两种取值都 < 26，结论不受影响**。
- **没有 Android 14 镜像**：官方 Android 版本列表里最新是 Android 13（BlueStacks 5）。Android 16 属于**另一个产品**（BlueStacks Desktop / BlueStacks 16），不是 BlueStacks 5 的一个实例镜像。
- 官方 5.22.288 release notes 明确：**Nougat 64-bit 与 Android 11 自 5.22.288 起不能再新建**，已存在的可继续用。
  来源：<https://support.bluestacks.com/hc/en-us/articles/34359017537549-BlueStacks-5-22-Release-Notes>
  本机是 5.22.170.6509（旧于 288），当前两个镜像都可继续新建。

### 4.2 怎么在本机查「当前实例」的真实 API level

启动实例 → 连上 adb（§3.3）→ 粘这段：

```powershell
$adb    = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = "127.0.0.1:5555"      # 换成 §3.2 读到的真实端口

& $adb -s $serial shell getprop ro.build.version.sdk       # 期望：28 或 33
& $adb -s $serial shell getprop ro.build.version.release   # 期望：9 或 13
& $adb -s $serial shell getprop ro.product.cpu.abi         # 期望：x86_64
& $adb -s $serial shell getprop ro.product.model           # 蓝叠模拟的机型
& $adb -s $serial shell getprop ro.build.version.codename  # REL

# 一次看全（PowerShell 里逐行打印，避免 getprop 全量输出刷屏）
'ro.build.version.sdk','ro.build.version.release','ro.product.cpu.abi','ro.product.model','ro.build.version.codename' |
  ForEach-Object { "{0,-32} = {1}" -f $_, ((& $adb -s $serial shell getprop $_).Trim()) }

# 顺带确认 GPU / 渲染能力（与 §5.3 的 AGSL 判定直接相关）
& $adb -s $serial shell dumpsys SurfaceFlinger | Select-String -Pattern 'GLES|Vulkan'
& $adb -s $serial shell getprop ro.hardware.egl
& $adb -s $serial shell getprop ro.hardware.vulkan
```

> 这段在本机**未实跑**（实例未启动），但全部是 adb/Android 标准属性读取，无蓝叠特有语法。

---

## 5. Compose 与 AGSL 在蓝叠上能不能跑

### 5.1 工程侧没有障碍（已核实）

| 项 | 结论 | 证据 |
| --- | --- | --- |
| minSdk 26 | 蓝叠 Pie64(28)/Tiramisu64(33) 都满足 | §4 |
| 是否有 native 库 / ABI 限制 | **没有**。`app/build.gradle.kts` 无 `jniLibs`、无 `externalNativeBuild`、无 `abiFilters`；`app/src/main/` 只有 `AndroidManifest.xml`、`java`、`res` | 本机 grep 实测 |
| 所以 x86_64 蓝叠能直接跑纯 Kotlin/Compose 包 | 是 | 同上 |

### 5.2 渲染后端怎么切、对 Compose 有没有影响

官方路径（一手）：**设置（齿轮）→ Graphics 标签页 → Graphics renderer**（国内版标签页名为 **`引擎`**，见 §3.1）→ **Save changes** → **Restart now**。

- Android Pie / 11 / 13 实例的选项是：**`Vulkan & OpenGL (Recommended)`** 与 **`OpenGL Only`**。
- **Nougat 32/64-bit 例外**：选项是 **`OpenGL`** 与 **`DirectX`**（这两种实例还有 `Graphics engine mode: Compatibility / Performance`）。
  → 也就是说**「DirectX」在 Pie/11/13 上不是可选项**，这是很多人搞错的地方。
- 同页还有 **Interface renderer**（蓝叠自身 UI 的渲染器，出画面/FPS 问题时才切，**不影响你 App 的 Compose 渲染**）、**ASTC textures**、**GPU in use**（显示当前用哪块 GPU，可勾选强制用独显）。

来源：<https://support.bluestacks.com/hc/en-us/articles/360057389932-How-to-change-the-graphics-settings-on-BlueStacks-5>
多实例内的同一设置：<https://support.bluestacks.com/hc/en-us/articles/4422291276429>

**本机当前**：两个实例都是 `graphics_engine="aga"`（Advanced Graphics）、`graphics_renderer="vlcn"`（即 `Vulkan & OpenGL` 组合）→ **已经是官方推荐的 Vulkan 路径**，这对 §5.3 的 AGSL 判定有利。

对 Compose 的实际影响：Compose 走 Skia + HWUI，最终落到宿主 GPU；渲染器从 Vulkan&OpenGL 切到 OpenGL Only **不会改变你 Compose 代码的行为**，但会改变驱动路径，可能导致某些 shader/blur 结果差异。**具体差异本机未验证。**

### 5.3 AGSL / `RuntimeShader` 能不能在蓝叠跑（R2 路线的生死线）

**API 侧（硬门槛，官方一手）：**

- `RuntimeShader` 是 **Android 13（API 33）** 引入的 API，行为由 AGSL 定义。
  来源：<https://developer.android.com/about/versions/13/features#graphics>（"Android 13 adds support for programmable RuntimeShader objects, with behavior defined using the Android Graphics Shading Language (AGSL)"）
- 参考文档标注 `RuntimeShader` 为 **Added in API level 33**：<https://developer.android.com/reference/android/graphics/RuntimeShader>
- AGSL 总览：<https://developer.android.com/develop/ui/views/graphics/agsl>

**结论（分实例）：**

| 实例 | API | RuntimeShader / AGSL | 说明 |
| --- | --- | --- | --- |
| `Pie64` | 28 | ❌ **不可能** | 低于 33，API 不存在；必须 `Build.VERSION.SDK_INT >= 33` 兜底 |
| `Tiramisu64` | 33 | ✅ **API 存在** | **这是本机唯一能验证 R2 路线的实例** |

**GPU 侧（我此前担心的「是否需要 Vulkan 直通」）：**

- AGSL 本质是 Skia 的 **SkSL**，是 **fragment shader**，走的是普通图形管线，**不是 Vulkan compute**。Android 官方材料明确说 AGSL 就是 SkSL 改名（"AGSL is essentially SkSL"），且它是「插入到 Skia 渲染管线中间」，不是要求 Vulkan compute 能力。
  来源：<https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a>、<https://developer.android.com/develop/ui/views/graphics/agsl>
- 因此 **AGSL 不要求 Vulkan**；只要 HWUI 能硬件加速（GLES 2.0+ 即可）就能跑。本机两实例都跑在 `Vulkan & OpenGL` 且 `aga` 引擎上，条件足够。
- **但「实际跑出来的像素是否与真机一致」本机未验证** —— 这需要真跑一次。

**判据（跑一次就知道）：**

```powershell
# 1) 确认实例的 API 够
& $adb -s 127.0.0.1:5555 shell getprop ro.build.version.sdk    # 必须 >= 33

# 2) 确认 HWUI 硬件加速没被关掉（蓝叠是虚拟 GPU，理论上不会关，但要验）
& $adb -s 127.0.0.1:5555 shell dumpsys gfxinfo com.nongyu.appplus | Select-String -Pattern 'Render|Janky|Hwui'

# 3) 最直接的判据：装包后看 logcat 有没有 nCast/skia 相关报错
& $adb -s 127.0.0.1:5555 logcat -c
# （拉起应用后）
& $adb -s 127.0.0.1:5555 logcat -s AndroidRuntime:E libEGL:E OpenGLRenderer:E hwui:E
```

### 5.4 `Modifier.blur` / `RenderEffect`

官方 API 的版本门槛（一手，Compose 参考文档原文）：

> "A uniform radius renders on **Android 12 (API 31)** and above; **spatially-varying radii (gradients and custom shaders) require Android 13 (API 33)** and above. Below these versions the modifier is ignored."

来源：<https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/blur.modifier>

| 用法 | Pie64(28) | Tiramisu64(33) |
| --- | --- | --- |
| `Modifier.blur(radius)` 均匀半径 | ❌ 被静默忽略 | ✅ |
| `Modifier.blur` + 渐变/自定义 shader 半径 | ❌ | ✅ |
| `RenderEffect.createBlurEffect` | ❌（API 31+） | ✅ |

⚠️ 关键行为：**低于门槛时 `Modifier.blur` 是「静默忽略」，不报错**。所以在 Pie64 上评估「模糊质感」会得到完全错误的结论——这是必须写进评估流程的陷阱。

### 5.5 `enableEdgeToEdge`

- 官方语义：`enableEdgeToEdge()` 让内容画到系统栏之下，需要自己用 insets 避让；targetSdk ≥ 35 在 Android 15+ 上自动强制边到边。
  来源：<https://developer.android.com/develop/ui/views/layout/edge-to-edge>
- **蓝叠上会不会异常——本机未验证。** 已知相关事实：模拟器/模拟器类环境里 insets 可能**返回 0**（系统没有真实 cutout 时 `displayCutout` 就是 0），表现为「内容真的画到状态栏下面、没有 padding」，看起来像 bug，其实是环境没有 cutout。
  社区一手案例：<https://www.reddit.com/r/androiddev/comments/1m6ymhr/>
- 蓝叠是模拟机型（本机 `device_profile_code="sttu"` / `"stou"`，`dpi=240`，1920×1080），**是否会报告 cutout 取决于该 device profile**。

自查命令（跑一次就有答案）：

```powershell
$serial = "127.0.0.1:5555"
# 看有没有 cutout（空 / 全 0 = 没有刘海）
& $adb -s $serial shell dumpsys window displays | Select-String -Pattern 'cutout|DisplayCutout'
# 看屏幕真实尺寸与密度
& $adb -s $serial shell wm size
& $adb -s $serial shell wm density
# 想在蓝叠里人为制造一个 cutout 来验证避让逻辑（等价于开发者选项里的 Display cutout）：
#   1) 手动方向：蓝叠内 设置 → 关于/点版本号 7 次开开发者选项 → 开发者选项 → 显示剪切区域(Display cutout)
#   2) 命令方向（纯 AOSP 属性，本机未验证蓝叠是否响应）：
& $adb -s $serial shell cmd overlay list | Select-String -Pattern 'cutout'
```

> 注意：`adb shell settings put global overlay_display_devices` 是「叠加第二块虚拟屏幕」，**与刘海/cutout 无关**，不要用它来验证 edge-to-edge。

**建议的评估口径**：「`enableEdgeToEdge` 在你的真机上是否压住内容」不要指望蓝叠给答案 —— 蓝叠的 device profile 不是你的目标机型。蓝叠只能回答「代码逻辑有没有把 insets 用对」。

### 5.6 帧率与触控延迟能不能代表真机

**直接答：不能代表真机，只能做「看得见的动效对不对」，不能做「手感好不好」。**

本机一手配置造成三条硬限制：

| 因素 | 本机值 | 后果 |
| --- | --- | --- |
| `enable_vsync="0"` | 垂直同步**关** | 帧不受屏幕刷新节拍约束 → 动画的「节奏感」与真机不同，掉帧感受不可信 |
| `max_fps="60"` + `enable_high_fps="0"` | 上限 60，高帧率未开 | 无法评估 90/120Hz 机型上的动效手感 |
| 输入路径 | 鼠标/触控板 → 蓝叠输入映射 → guest 注入 | 触控到动画起帧的延迟远大于真机触屏；「按下即响应」这类主观感受**完全不可信** |

可以额外确认/调整的能力（官方一手）：蓝叠 5 支持 **60–240 FPS**，在 **设置 → Performance → 「Enable High Frame Rate」** 打开并把滑块拉到 120–240，保存后 **Restart now**。
来源：<https://support.bluestacks.com/hc/en-us/articles/38637938620685-How-to-enable-high-FPS-on-BlueStacks-5>

```powershell
# 建议在评估前把这个打开（本机当前是关的），至少把上限抬到 120：
#   设置 → Performance → Enable High Frame Rate → 拉到 120 → Save changes → Restart now
# 想量化帧率是否稳定，用 gfxinfo（不是靠肉眼看）：
& $adb -s 127.0.0.1:5555 shell dumpsys gfxinfo com.nongyu.appplus reset
# （操作一轮动效）
& $adb -s 127.0.0.1:5555 shell dumpsys gfxinfo com.nongyu.appplus
# 看 Janky frames / 90th/95th/99th percentile 三行
```

> 「蓝叠的实际触控延迟是多少 ms」**没有官方数据，我也不给编造的数字**。结论只到「显著大于真机、不可用于手感定稿」这个定性层面。

---

## 6. IDEA 识别与替代部署链路

### 6.1 IDEA 2025.3.2 能不能把蓝叠当设备用

**不能。** 理由（沿用前作已核实的事实，不重复论证）：`org.jetbrains.android` 插件未安装；设备列表 / Device Manager / Logcat 面板都随该插件提供。前作 §2.4/§2.6。

补充一条对本票有意义的：**即使装上了插件，蓝叠也只是一个「网络 adb 设备」（serial 形如 `127.0.0.1:5555`），插件对它的处理与真机一致**，没有蓝叠专有集成。这条属于机制推断，**未验证**（本机无插件）。

### 6.2 替代链路（当前唯一可行，今天就能跑）

```powershell
# ---- 0. 变量 ----
$sdk    = "$env:LOCALAPPDATA\Android\Sdk"
$adb    = "$sdk\platform-tools\adb.exe"
$serial = "127.0.0.1:5555"                     # 改成 §3.2 读到的真实端口
$pkg    = "com.nongyu.appplus"
$act    = "$pkg/.MainActivity"

# ---- 1. 连设备并确认 device（不是 offline/unauthorized）----
& $adb kill-server
& $adb connect $serial
& $adb devices -l

# ---- 2. 装包（关键：先设 ANDROID_SERIAL，否则会装到所有设备上）----
$env:ANDROID_SERIAL = $serial
.\gradlew.bat :app:installDebug
# 说明：AGP 的 InstallVariantTask 读 ANDROID_SERIAL；不设则对「所有兼容设备」各装一遍

# ---- 3. 拉起 ----
& $adb -s $serial shell am start -n $act

# ---- 4. 只看本进程日志 ----
$appPid = (& $adb -s $serial shell pidof -s $pkg).Trim()
& $adb -s $serial logcat --pid=$appPid

# ---- 5. 常用变体 ----
& $adb -s $serial logcat -c; & $adb -s $serial logcat -s AndroidRuntime:E
& $adb -s $serial uninstall $pkg
```

**`ANDROID_SERIAL` 这条有源码级依据**：AGP 的 `InstallVariantTask` 会读 `ANDROID_SERIAL`；未设时对所有兼容设备执行安装（同一行为也适用于 `uninstall` 与 `connectedAndroidTest`）。
来源（引用 AGP 源码）：<https://stackoverflow.com/questions/35825068/how-does-the-android-gradle-plugin-select-all-devices>
一个实际的踩坑印证（`--device` 只影响 gradle 后的 adb 步骤，gradle 自己选设备）：<https://github.com/react-native-community/cli/commit/396081e99ce2ddaa25aa29de03162276bd1ac740>

**PowerShell 注意**：`$env:ANDROID_SERIAL = ...` 只对**当前窗口**有效。装完想恢复：

```powershell
Remove-Item Env:ANDROID_SERIAL
```

### 6.3 断点调试（插件缺位时的兜底）

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = "127.0.0.1:5555"

& $adb -s $serial shell am start -D -n com.nongyu.appplus/.MainActivity
$appPid = (& $adb -s $serial shell pidof -s com.nongyu.appplus).Trim()
& $adb -s $serial forward tcp:8700 jdwp:$appPid
# IDEA：Run → Edit Configurations → + → Remote JVM Debug，Host localhost，Port 8700
```

（与前作 §3.6 同一套机制，本机**未实跑**。）

---

## 7. 与官方 emulator / 真机的取舍

### 7.1 端口冲突（两者都爱 5555）

事实基础：Android 官方 adb 文档说，adb server 会扫描 **5555–5585 的奇数端口**来找前 16 个模拟器。
来源：<https://developer.android.com/tools/adb>（"扫描 5555 到 5585 之间（该范围供前 16 个模拟器使用）的奇数号端口"）

- 官方 AVD 的第一个实例：console **5554**、adb **5555**。
- 蓝叠默认：adb **5555**。

→ **直接撞车。** 两者同时开时，`emulator-5554` 与 `127.0.0.1:5555` 会指向不同设备但抢同一端口语义，表现为 `adb devices` 里 `emulator-5554 offline` 或干脆少一个。

并存的两种做法：

```powershell
# 做法 A（推荐）：给官方 AVD 换端口，让蓝叠继续用 5555
$emu = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
Start-Process $emu -ArgumentList "-avd","pixel36","-no-snapshot","-port","5556"
# -port 5556 → console 5556、adb 5557，避开蓝叠的 5555
```

```powershell
# 做法 B：改蓝叠端口
#   在「设置 → 高级 → Android调试(ADB)」里改（端口是启动时扫描分配的，改法以 UI 为准 —— 本机未验证）
#   如果 UI 里不能改，最稳的是不同时开。
```

**「蓝叠会不会抢 adb server」**：不会抢 server，它做的是 **NAT 端口转发**（日志：`NAT forwarding applied up ... adb hostPort 5555`），本身不需要 adb server。真正会抢的是你手动跑 `HD-Adb.exe`（§3.4）。

### 7.2 对「评估 AI 圆按钮动效手感」这个具体目标

| 维度 | 蓝叠 Tiramisu64 | 官方 AVD (android-36) | USB 真机 |
| --- | --- | --- | --- |
| 今天能不能用 | ✅ **能**（已装、ADB 已开、只需启动） | ❌ 缺 `cmdline-tools`/`emulator`/镜像，要下 ≈2.4 GB | ⚠️ 取决于有没有手机 + 驱动 |
| API 是否够（minSdk 26） | ✅ 33 | ✅ 36 | ✅ |
| AGSL / `RuntimeShader` | ✅ API 33 满足（GPU 侧实测**未验证**） | ✅ | ✅ |
| `Modifier.blur` | ✅ | ✅ | ✅ |
| 能否代表真机帧时序 | ❌ `enable_vsync=0`、默认封 60 | ⚠️ 同样非真机（但可配 vsync/120Hz） | ✅ |
| 触控延迟 | ❌ 鼠标→注入，最差 | ⚠️ 比蓝叠好，仍非真机 | ✅ |
| 真实 GPU 性能/发热降频 | ❌ | ⚠️ 依赖宿主机 GPU | ✅ |
| 与目标机型的屏幕/刘海一致性 | ❌ 蓝叠用固定 device profile | ⚠️ 可选机型更细 | ✅ |
| 额外安装体积 | **0**（已装） | ≈2.4 GB | 0 |

**各自最真实的短板：**

- **蓝叠**：① 帧时序不可信（`vsync=0` + 60 上限）；② 触控延迟最大，凡是「按下瞬间的响应感」都别在这里下结论；③ device profile 不是你目标机型，`enableEdgeToEdge`/刘海类判断不能用它；④ 自带 adb 版本旧（1.0.36），会跟 platform-tools 打架。
- **官方 AVD**：① 要补 ≈2.4 GB 工具链（本机连 `cmdline-tools` 都没有）；② 同样不是真机，GPU 走宿主，帧率/延迟只比蓝叠稍好；③ 装完才谈得上「能用」。
- **USB 真机**：① 要有机器且能开 USB 调试；② 国产 ROM 驱动常需自备；③ 但**只有它能给「手感」定稿**，因为只有它有你目标机型的触控链路、真实刷新率与真实 GPU。

### 7.3 明确推荐

> **两步走：**
> **第一步（今天）** —— 启动 **`Tiramisu64`**（Android 13）实例，按 §3.3 连上，按 §6.2 装包，先把 **R2 路线（AGSL `RuntimeShader`）能不能跑通** 这件事验掉。这是蓝叠现在唯一不可替代的价值：**零下载、零安装，验证 API 与 shader 的可用性**。
> **第二步（定稿）** —— 任何关于「曲率是否顺滑、回弹是否自然、按下到起帧是否跟手、120Hz 上是否拖影」的判断，**必须回 USB 真机**。蓝叠的 `enable_vsync="0"` + 60 FPS 上限 + 鼠标输入，三个因素叠加，让它的手感结论不可外推。
>
> **不建议为了这个目标去装官方 AVD**：它要 2.4 GB，而它对「手感」的保真度并不比蓝叠高一档——真正高一档的是真机，而真机不需要下载。

---

## 8. 未验证清单（不要当结论用）

1. **实例内实际 API level 未回读**：蓝叠当前未运行，没跑 `adb shell getprop ro.build.version.sdk`。§4 的 28 / 33 是由「镜像名 ↔ 官方 Android 版本说明 + Android 官方 API level 表」推出的，不是本机实测输出。
2. **国内版 UI 中文用词未目视**：`Android调试(ADB)`、`高级`、`引擎`、`保存更改` 都来自本机安装目录的官方资源（`HD-Player.exe` 的 UTF-16BE 字符串表、`Engine\translations.json` 的 `zh-CN`），但没有打开界面逐字对照。
3. **多实例端口递增规则官方未定义**：我查到的官方文档只说「记下开关下方数字的最后 4 位」，没有步长；`5555/5565/5575` 来自社区/自动化工具列表。本机 `Player.log` 显示是「启动时扫描可用端口」，所以**不要假设固定步长**，按 §3.2 实测。
4. **ADB 「远程访问」开关在 UI 上的名称未查到**：只确证了 conf 键 `bst.enable_adb_remote_access` 与日志 `remoteAccess false`。
5. **`enableEdgeToEdge` 在蓝叠上的实际表现未验证**：没有实跑；蓝叠 device profile 是否会报告 cutout 未验证。
6. **AGSL 在蓝叠上的渲染结果未验证**：API 门槛（33）与「AGSL 不需要 Vulkan compute」是确定的，但「蓝叠虚拟 GPU 上跑出来的画面与真机是否一致」没有任何一手证据。
7. **「BlueStacks 高帧率 + vsync 打开后」能否接近真机手感未验证**：本机 `enable_high_fps="0"`、`enable_vsync="0"`，改进后的效果没测。
8. **蓝叠触控延迟的具体毫秒数**：没有任何官方或可靠一手数据，本文只给定性判断。
9. **`gradlew :app:installDebug` + `ANDROID_SERIAL` 在本机实跑效果**：机制有 AGP 源码依据，但本机没有连接设备，未实跑。
10. **IDEA 装上 Android 插件后能否列出 `127.0.0.1:5555`**：未装插件，未验证；按「网络 adb 设备与真机同路径」推断可列出。
11. **Nougat 镜像的确切 Android 子版本（7.0 vs 7.1）**：官方只写 "Nougat"；社区普遍记 7.1.2。两种取值的 API level（24/25）都低于 minSdk 26，不影响结论。
12. **改蓝叠 adb 端口的 UI 操作路径**：未验证（官方文档只教读端口，没教改端口）。

---

## 9. 参考资料

### BlueStacks 官方（primary source）

1. How to enable Android Debug Bridge on BlueStacks 5（Settings → Advanced → "Android Debug Bridge" → Save changes）— <https://support.bluestacks.com/hc/en-us/articles/23925869130381-How-to-enable-Android-Debug-Bridge-on-BlueStacks-5>
2. How to utilize the different Android versions available on BlueStacks 5（Nougat 32/64、Pie 64-bit=Android 9、Android 11、Android 13；5.22.288 起 Nougat64/Android 11 停止新建）— <https://support.bluestacks.com/hc/en-us/articles/360058931031>
3. BlueStacks 5.22 Release Notes（Android 13 Beta 进入 5.22.0；5.22.288 移除旧版本新建）— <https://support.bluestacks.com/hc/en-us/articles/34359017537549-BlueStacks-5-22-Release-Notes>
4. BlueStacks 5 offline installer（`--defaultImageName Pie64 / Rvc64 / Tiramisu64 / Nougat64` 与 Android 版本的对应）— <https://support.bluestacks.com/hc/en-us/articles/4402611273485-BlueStacks-5-offline-installer>
5. How to change the graphics settings on BlueStacks 5（Graphics 标签页；Pie/11/13 = Vulkan & OpenGL / OpenGL Only；Nougat = OpenGL / DirectX；Interface renderer；GPU in use）— <https://support.bluestacks.com/hc/en-us/articles/360057389932>
6. How to change settings in the Multi-instance Manager on BlueStacks 5（实例级 graphics 设置）— <https://support.bluestacks.com/hc/en-us/articles/4422291276429>
7. How to enable high FPS on BlueStacks 5（Performance → Enable High Frame Rate，60–240 FPS）— <https://support.bluestacks.com/hc/en-us/articles/38637938620685>
8. How to transfer your apps from BlueStacks 4 to BlueStacks 5（**"note down the last 4 digits... This is the ADB port for BlueStacks 5"**，示例 5555）— <https://support.bluestacks.com/hc/en-us/articles/360061342631>
9. How to use Pie 64-bit on BlueStacks 5 — <https://support.bluestacks.com/hc/en-us/articles/4406032772877>
10. Getting Started with BlueStacks Desktop（BlueStacks 16 = Android 16，注意是另一个产品）— <https://support.bluestacks.com/hc/en-us/articles/49665096963085>
11. BlueStacks 16 公告（Android 16 = 首个 PC 端 Android 16 模拟器）— <https://bluestacks.com/blog/bluestacks-exclusives/bluestacks-16-en.html>

### Android 官方（primary source）

12. SDK Platform release notes（Android 13=33、11=30、9=28、7.0=24）— <https://developer.android.com/tools/releases/platforms>
13. `Build.VERSION_CODES`（TIRAMISU=33 等）— <https://developer.android.com/reference/android/os/Build.VERSION_CODES>
14. Android 13 features — Graphics（`RuntimeShader` + AGSL 由 Android 13 引入）— <https://developer.android.com/about/versions/13/features#graphics>
15. `RuntimeShader` API 参考（Added in API level 33）— <https://developer.android.com/reference/android/graphics/RuntimeShader>
16. AGSL 总览 — <https://developer.android.com/develop/ui/views/graphics/agsl>
17. Android Developers Blog / Medium：AGSL Made in the Shade(r)（"AGSL is essentially SkSL"，是 fragment shader）— <https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a>
18. Android 13 is in AOSP（programmable shaders 段落）— <https://android-developers.googleblog.com/2022/08/android-13-is-in-aosp.html>
19. Compose `Modifier.blur` 参考（uniform 需 API 31+；渐变/custom shader 需 API 33+；低版本被忽略）— <https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/blur.modifier>
20. adb 文档（adb server 占 5037；扫描 5555–5585 奇数端口）— <https://developer.android.com/tools/adb>
21. Display content edge-to-edge in views（`enableEdgeToEdge`、insets、cutout）— <https://developer.android.com/develop/ui/views/layout/edge-to-edge>
22. Migrate from RenderScript（AGSL 用于 Android 13+ 的可编程 shader；Vulkan/GLES 3.1 用于 GPU compute）— <https://developer.android.com/guide/topics/renderscript/migrate>

### 社区一手（仅作旁证，不作结论）

23. MAA 连接设置（BlueStacks 5 地址候选 `5555/5556/5565/5575/5585/5595/5554`）— <https://docs.maa.plus/zh-cn/manual/connection.html>
24. AGP `InstallVariantTask` 读 `ANDROID_SERIAL`、未设则对所有设备安装 — <https://stackoverflow.com/questions/35825068/how-does-the-android-gradle-plugin-select-all-devices>
25. `--device` 只影响 gradle 之后的 adb 步骤（gradle 自己选设备）— <https://github.com/react-native-community/cli/commit/396081e99ce2ddaa25aa29de03162276bd1ac740>
26. `ANDROID_ADB_SERVER_PORT` / `adb -P`（隔离 adb server 端口）— <https://stackoverflow.com/questions/3836571/changing-default-port-i-e-5037-on-which-adb-server-runs>
27. BlueStacks HD-Adb v1.0.36 与 SDK adb v1.0.41 互杀 server 的具体记录 — <https://github.com/Jordan231111/BluestacksRoot/commit/d601a2612eb4e7798e74780385e6cce0e8cf7e32>
28. 模拟器上 insets 返回 0、需手动开 Display cutout 才生效 — <https://www.reddit.com/r/androiddev/comments/1m6ymhr/>
29. `localhost` 解析到 IPv6 导致 `cannot connect ... 10061`（改用 `127.0.0.1`）— <https://stackoverflow.com/questions/24598577/connect-bluestacks-to-android-studio>

### 本机一手数据（本文 §2 的来源）

- 注册表：`HKLM\SOFTWARE\BlueStacks_nxt_cn`、`HKCU\SOFTWARE\BlueStacks_nxt_cn`、`HKLM\SOFTWARE\BlueStacksInstaller`、卸载项 `BlueStacks 5 China`
- 安装目录：`C:\Program Files\BlueStacks_nxt_cn\`（`tag.txt` = `_nxt_cn`、`HD-Adb.exe`、`HD-Player.exe`、`HD-MultiInstanceManager.exe`）
- 数据目录：`D:\Program Files\BlueStacks_nxt_cn\`（`bluestacks.conf`、`Engine\{Pie64,Tiramisu64}\`、`Engine\translations.json`、`Logs\Player.log`）
