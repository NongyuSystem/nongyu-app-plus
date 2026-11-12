# 川农统一身份认证（CAS）自带「微信登录」形态与「直接跳转授权替代扫码」一手事实核查

> **日期**：2026-11-12。
> **起因**：`/grill-with-docs` 讨论农屿 Android 登录页。维护者 Q12′ 拍板「微信是**学校 CAS 自带的微信登录方式**（不是微信开放平台移动应用登录）」，并附加要求「**看看这个扫码能不能用直接跳转授权替代，不能就算了**」。本文只核查这一个问题：仓内一手材料里，学校统一身份认证平台的微信登录到底是什么形态，以及有没有「从 App 用 URL / scheme 直接拉起微信完成授权、不显示二维码」的现成支撑。
> **只查一个问题，只读核查**：只新建本文一篇；未修改任何既有文件、未改代码、未 git commit、未访问网络（学校平台真站 / 官方文档均不在本轮范围内）。
> **与 `docs/research/wechat-scan-direct-jump.md`（2026-11-11）的区别**：**那篇是微信「开放平台」层面**——第三方 App 自己去微信开放平台注册移动应用、用 Open SDK 的 `SendAuth.Req` / `Login_via_Scan` / 网站应用 `qrconnect` / 小程序 `weixin://dl/business/` 完成「微信登录」，结论是「官方无免 SDK 的跳转登录、跳微信 ≠ 登录」。**本文是「学校 CAS 自带的微信登录方式」**——身份提供方是川农统一身份认证平台 `auth.sicau.edu.cn`（自研 go-admin 平台），农屿并不持有用于登录的微信开放平台资质（仓内那个微信 AppID 只用于分享，见 §3.3）。两者**不是同一套授权体系**：前者的「官方没有免 SDK 直跳」不能当作「学校 CAS 不能直跳」的证明，反过来也不能证明「学校 CAS 能直跳」。本文不重复该篇全文，只引用其结论作边界标注。
> **与 `docs/research/backend-plus-login-ui-facts.md`（2026-11-12）的关系**：**补充，不推翻**。该篇 §2.1/§2.4 已确证「后端只接收 `go-admin-tgt`、不提供 CAS 登录页 URL、App 自己从别处取得 tgt」，并把「App 从哪里取得 tgt」列为待核实。本文正是去查这条 App 侧学校登录链路的**微信分支**：微信是什么形态、有没有可直跳的授权入口。该篇 §1.1 关于 `schoolMethod=wechat` / `wechatCode` 是占位、被桥接层丢弃的结论继续有效，本文 §3.4 引用它并补上「因此仓内也没有『App 拿微信 code 交后端换 tgt』这条路径」。

## 路径约定与确定程度

下文 `文件:行号` 中的路径前缀（均为本机 real path，见 §6 顺带回报）：

| 前缀 | 实际目录 |
| --- | --- |
| `AP/` | `apps/nongyu-app-plus/`（本工作区，`docs/research/` 下记笔记处） |
| `BE/` | `apps/nongyu-backend-plus/` |
| `ROOT/` | `D:\IT\program\web-program\nongyu-system\`（monorepo 根；后端契约清单里写作 `@OLD/`，见 `BE/docs/tech/旧农屿WebVPN可重写契约清单.md:14`） |
| `OLD/` | `ROOT/apps/nongyu-rn-app/`（旧 RN 版客户端，Q18=B「移植 CAS 链路」的源） |
| `TOOL/` | `ROOT/packages/nongyu-tool-jiaowu/`（旧 CAS 工具包，`core/login/cas.ts` 所在） |
| `DOCS/` | `ROOT/docs/`（旧 RN 版的规格 / 实测 / 技术文档） |

结论按三档标注，全文不把「仓内没找到」写成「官方不存在」：

- **【确证】**：仓内源码 / 实测笔记 / 规格原文可直接引用。
- **【仓内未见】**：对指定范围做过 grep 后零命中；只说明**仓内**没有这份材料。
- **【待实测】**：需真机 / 浏览器 / 抓包 / 学校平台官方文档才能定，本轮无此环境。

---

## 0. 一句话回答

**「这个扫码能不能用直接跳转授权替代」→【仓内未见】**：仓内**没有任何** URL / scheme / appid / 授权端点支撑「从 App 直接拉起微信完成学校 CAS 的微信授权而不显示二维码」；更根本的是，仓内**没有一份材料记录过「学校统一身份认证平台（auth.sicau.edu.cn）自带的微信登录」到底是什么形态**——仓内记到的「微信扫码」实际是**教务系统首页**的入口，与「统一身份认证登录」在教务通知里是**并列的两个入口**（`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:214`）。因此本轮既不能答「能」，也不能答「官方不能」，只能答「**仓内查不到，需对学校平台登录页做实测/抓包/查官方文档才能定论**」。

**【确证】的边界事实**（这些不依赖实测，仓内可直接引用）：

1. 旧 RN `TOOL/core/login/cas.ts`（805 行）**无微信实现**，只有账密 + 短信两条链路（§3.1）。
2. 仓内 `auth.sicau.edu.cn/api/v1/*` 实测端点清单里**没有任何微信相关端点**（§4）。
3. 后端不提供 CAS 登录页 URL、只收 `go-admin-tgt`；`wechatCode` 在生产链路被丢弃（§3.4）。
4. 仓内唯一微信开放平台 AppID 是**分享用**、规格明写「不做微信登录」（§3.3）。
5. 旧 RN 客户端里唯一与「微信登录」相关的可运行代码，是一个**打开外部浏览器教务首页 + 文字引导公众号绑定 + 人工扫码**的面板，**不渲染二维码、不轮询、不回传凭证、也不完成农屿登录**（§3.2）。

---

## 1. 仓内有没有记录「川农统一身份认证平台的微信登录」是什么形态？

### 1.1 【确证】仓内确实出现了「微信扫码」，但它被记为**教务系统首页**的入口，与「统一身份认证登录」并列

教务处《关于调整教务管理系统登录方式的通知》（2026-10-23）原文被多处仓内文档转录，逐字为：

> 「现将取消普通用户在**教务管理系统首页**直接输入账号、密码登录的方式，调整为『**微信扫码登录**』或『**统一身份认证登录**』方式。」
> — `DOCS/nongyu-rn-app/tech/教务鉴权方案.md:211-215`

同义转录：`DOCS/common/BugLog.md:873`、`TOOL/TECH.md:23`、`TOOL/core/login/cas.ts:4-6`、`TOOL/core/login/index.ts:358`。

**关键区分（不改写维护者拍板，只陈述仓内事实）**：这句原文里「微信扫码登录」与「统一身份认证登录」由「或」连接，是**教务系统首页上的两个并列入口**；仓内没有任何一处把「微信扫码」写成「统一身份认证平台 `auth.sicau.edu.cn` 内部的一个登录方式」。Q12′ 说的「学校 CAS 自带的微信登录方式」具体指「教务首页那个扫码」还是「auth 登录页里的微信 Tab」，仓内无法定点 → **【待实测】**（§5 第 6 项）。

### 1.2 【确证】仓内对「微信扫码」形态最具体的记录：浏览器页面 + 「川农微教务」公众号绑定 + 人工扫码

`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:408-417`「登录方式矩阵（用户自选）」逐行：

| 方式 | 用户操作 | 是否经过服务端存密码 | 是否免验证码 |
| --- | --- | --- | --- |
| 账密 | 每次输账密 + 读图形码 | 否 | 否 |
| 手机验证码 | 图形码 → 收短信 → 输短信码 | 否 | 否 |
| **微信扫码** | **浏览器打开教务系统扫码（需先绑「川农微教务」）** | 否 | **否（人工确认）** |

`OLD/src/modules/jiaowu/components/JiaowuLoginPanel.tsx:360-390` 是这段描述的落地代码（`WechatPane`），逐条给行号：

- `:366-368`：`openJiaowu = () => Linking.openURL("https://jiaowu.sicau.edu.cn/web/web/web/index.asp")` —— **用系统浏览器打开教务首页**，不是 App 内 WebView、不是拉起微信客户端。
- `:372`：标题「使用微信扫码登录」。
- `:373`：「1. 打开浏览器进入教务系统首页，点『扫码登录』」——**二维码显示在浏览器页面里，不是 App 内贴二维码**。
- `:374-376`：「2. 首次使用需在『**川农微教务**』**公众号**绑定教务账号（我的教务 → 学生账号绑定）」——指向**微信公众号**绑定，不是小程序、不是微信开放平台移动应用授权。
- `:377`：「3. **扫码确认后回到农屿，用「自动登录」或「账密」进入**」——**扫码本身不向农屿回传任何凭证**，App 侧仍要另走 CAS 账密 / 自动登录。
- `:385-386`：「微信扫码属于**人工确认流程，无法自动完成**；如需完全免验证码，请使用『自动登录』」。

同文件 `:29-35` 把方式枚举为 `LoginMethod = "auto" | "password" | "phone" | "wechat"`，其中 `{ key: "wechat", label: "微信", hint: "扫码登录" }`（`:35`）；`:48` 注释「微信扫码（川农微教务绑定后扫码，非自动）」。

另：`OLD/src/api/jiaowuHosted.ts:4`、`ROOT/apps/nongyu-node-server/src/modules/jiaowu-hosted/routes.ts:5`、`service.ts:9` 三处注释把「微信扫码」列为关闭教务托管后的回落方式之一，均无实现细节。

**按题目给的四种形态逐项对照（基于上述仓内记录）**：

| 候选形态 | 仓内记录 | 依据 |
| --- | --- | --- |
| App 内贴二维码让用户用微信扫 | **【仓内未见】**：无二维码渲染、无 `onAuthGotQrcode` 类回调 | `JiaowuLoginPanel.tsx:360-390` 全文只有 `Text` + `Pressable(openURL)`；`OLD/src` 全量 grep `qrcode/二维码/扫码` 仅命中这些注释与文案 |
| 跳微信客户端授权 | **【仓内未见】**：无 `weixin://` 登录 scheme、无 `SendAuth` / `sendReq` 调用 | §2、§3.3 |
| H5 / 浏览器页面里的微信登录 | **【确证，但对象是教务首页】**：浏览器打开 `jiaowu.sicau.edu.cn/.../index.asp` 点「扫码登录」 | `JiaowuLoginPanel.tsx:366-373`、`教务鉴权方案.md:417` |
| 微信公众号 / 小程序授权 | **【确证，但只到『公众号绑定』】**：需先绑「川农微教务」公众号；**无授权端点 / appid / code 回传记录** | `JiaowuLoginPanel.tsx:374-376` |

### 1.3 【确证 + 待实测】统一身份认证平台登录页（`/wui/`）只被记录到「加载了微信 JSSDK」，没有记录到微信登录 Tab

`DOCS/nongyu-rn-app/tech/WebVPN探索.md` 是对 `auth.sicau.edu.cn` 登录页的**仓内唯一一手实测**：

- `:128-129`：`GET …/wui/` → `200 <title>川农e认证 - 统一身份认证</title>`。
- `:68`：`res.wx.qq.com` 主机名密文为「（推导）`e2f252d2302826416f468aa395`」，备注「**与 wui 内 jweixin 脚本 URL 一致**」；`:96` 表格「实测 wui HTML」记「**微信 JSSDK 等外链同样编码**」。→ **【确证】**：wui 页确实加载了微信 JSSDK（`jweixin`）资源。
- **但** `:144` 同一次实测明确写：「登录页**仅暴露「统一认证」Tab（`only-auth`）**，另有本地表单相关端点符号 `/do-confirm-login`、`/do-second-login`（须知写明账号登录为本地账号，与 CAS 并列；农屿应只走 CAS）」。

即：**仓内实测到的那次 WebVPN 门户入口的 wui 登录页没有微信 Tab**；`jweixin` 被加载不等于存在微信登录入口（可能用于分享 / 其它能力），且这次实测用的是**门户 appid `aea49a21-…`**（`:120`），不能推广到教务 appid 或直连 `auth.sicau.edu.cn`。→ 学校 CAS 登录页到底有没有微信 Tab、是什么形态，**【待实测】**。

### 1.4 【确证】后端领域模型只把「学校微信」当作一个**名称**，没有形态定义

`BE/docs/tech/领域模型-用户与登录-消息.md:73-83` 表格第 1 行：「**学校统一身份认证** | 学校账密 / **学校微信** / 学校电话 | 否——它是**唯一注册所需**的方式」，`:83` 维护者原话「学校统一身份认证是唯一注册所需的」。`BE/docs/tech/架构落点-身份仓.md:104` 同样写「让学校确认学号（账密 / **微信** / 电话）」。

这两处只列出「微信」这个**方式名**，没有任何 URL / scheme / 端点 / 二维码 / 公众号 / 小程序信息 → **【确证，但不含形态】**。后端源码里的 `wechat` 字样同样是占位或画像字段，见 §3.4 与 §4.2。

---

## 2. 「直接跳转授权替代扫码」具体指什么？仓内有没有现成 URL / scheme / appid / 授权端点支撑？

**先把这句话拆成可核查的命题**：从 Android App 用一个 URL / scheme（或一个已登记的 appid + 授权端点）**直接拉起微信客户端**完成「学校 CAS 的微信授权」，把凭证回给 App，**全程不显示二维码让用户扫**。这要求仓内至少存在下列之一：① 学校侧给农屿的微信登录授权 URL / scheme；② 一个可用于学校 CAS 微信登录的微信 appid；③ `auth.sicau.edu.cn` 上一个「微信授权」端点；④ 一段把微信 code 换成 `go-admin-tgt` 的链路。

### 2.1 【仓内未见】学校侧微信登录 URL / scheme

- 全仓 grep `weixin://` 只命中 `AP/docs/research/wechat-scan-direct-jump.md:28-30` 一处，且原文结论是「`weixin://dl/business/` 是**打开小程序**、不是登录；Android 系统不支持直接识别该 scheme，需 H5 中转」——这是**微信开放平台小程序**能力，与学校 CAS 无关，也不能当授权入口。
- 全仓 grep `loginByWechat` / `loginByWeixin` / `wechat/` / `weixin/` / `wxLogin` / `qrcode/login` / `scan/login` / `loginByQr`：零命中（覆盖 `*.ts/tsx/js/go/md/json/kt`）。
- 仓内出现的、与「打开学校页面」有关的 URL 只有这些，**都不是微信授权入口**：
  - `https://jiaowu.sicau.edu.cn/web/web/web/index.asp`（教务首页，外部浏览器）— `OLD/src/modules/jiaowu/components/JiaowuLoginPanel.tsx:367`
  - `https://auth.sicau.edu.cn/sso/login?appid=<appid>&service=<service>` → 302 到 `/wui/#/?appid=…&service=…` — `DOCS/nongyu-rn-app/tech/教务鉴权方案.md:222-225`、`WebVPN探索.md:80,119,124-125`
  - `https://webvpn.sicau.edu.cn/login?cas_login=true` — `TOOL/core/login/cas.ts:682-683`（经 `BE/docs/tech/旧农屿WebVPN可重写契约清单.md:130` 转录）

### 2.2 【仓内未见】可用于学校 CAS 微信登录的 appid / 授权端点

- 仓内**微信开放平台** AppID 只有一个：`wx174b5a4d92447b73`（`OLD/src/modules/mine/constants/share.ts:6-7`、`OLD/app.json:8` 的回跳 scheme、`DOCS/nongyu-rn-app/specs/分享农屿.md:74,95,155`）。用途见 §3.3：**网页分享**，规格明写「不做微信登录」。
- 学校侧 appid 是 **UUID 形态**，且只有两个：`b54a2f27-f7ba-4b91-bbf0-3b2b12244d14`（教务）、`aea49a21-6b26-4e8f-bcfc-80e3de591c93`（门户 / WebVPN）— `TOOL/core/login/cas.ts:31-35,679-680`、`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:30-35`。`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:48-53` 明确区分「appid 有两种形态」：`/sso/login?appid=<名>` 是**名字字符串、用于前端 SSO 路由**；`/api/v1/cas/login?appid=<UUID>` 必须是**已注册 UUID、用于后端换票**。`:45` 实测「**随机 UUID → `code=500 未登录`**，说明 appid 必须已注册」。**没有任何微信形态的 appid 被登记在学校侧**。
- 仓内 `/api/v1/*` 端点清单见 §4，**无微信端点**。

### 2.3 【确证】没有「微信 code → go-admin-tgt」这条落地路径

即使将来实测拿到了某种微信授权 code，当前后端也没有接它的入口：

- `BE/internal/identity/flow/handler.go:47,52`：`loginReq` 声明了 `SchoolMethod string // password / wechat / phone` 与 `WechatCode string`。
- `BE/internal/app/schoolauth_bridge.go:28-47`：`Authenticate` 只搬运 `RawCookie` / `UserID` / `XSGZToken` / `GoAdminTgt`（转成 `go-admin-tgt` cookie），**`Method` / `WechatCode` / `Phone` / `Code` / `Password` 全未赋值**，即被丢弃（`:29` 注释「凭据形态只有一种：客户端完成学校登录后交来的 go-admin-tgt」）。
- `AP/docs/research/backend-plus-login-ui-facts.md:74` 表格确证：`wechat` =「**无实现 / 仅占位**：`WechatCode` 在 school 分支被桥接层丢弃；源码中仅单测构造过该值」；`:107` 确证「客户端不传 tgt、只传 `wechatCode` → 走不通（401 `bad_credentials`）」。
- `AP/docs/research/backend-plus-login-ui-facts.md:157-172,190-195`：后端**没有任何「统一身份认证登录页 URL」常量**，`/login` 字样只作为会话失效信号；`:210-215` §2.4 把「App 从哪取得 tgt / 能否用 WebView」列为【待核实】。

### 2.4 【确证】开放平台层面「直跳 ≠ 登录」的既有结论，不能越界引用

`AP/docs/research/wechat-scan-direct-jump.md:20,37,180,185` 已确证：官方没有给第三方 App 一个可手工拼装的免 SDK 登录 URL / scheme；移动应用微信登录靠 Open SDK `sendReq` 拉起微信，回跳 `WXEntryActivity`；`weixin://dl/business/` 是打开小程序。**但这整套讲的是农屿自己去接微信开放平台**，与「学校 CAS 自带的微信登录」不是一套身份提供方（见文首「区别」）。本文**不**用它来论证「学校 CAS 不能直跳」，只把它列为「若将来方案落到开放平台路径，这些约束会重新生效」的边界。

### 2.5 小结（命题逐项）

| 需要存在的东西 | 仓内结果 | 档位 |
| --- | --- | --- |
| 学校侧微信登录授权 URL / scheme（可拉起微信、不显示二维码） | 零命中；唯一 `weixin://` 是小程序链接且非登录 | 【仓内未见】 |
| 学校 CAS 微信登录可用的微信 appid | 只有分享用 `wx174b5a4d92447b73`；学校侧 appid 全是 UUID 且非微信 | 【仓内未见】 |
| `auth.sicau.edu.cn` 微信授权端点 | `/api/v1/*` 清单无；见 §4 | 【仓内未见】 |
| 微信 code → `go-admin-tgt` 的后端链路 | `wechatCode` 被桥接层丢弃、无消费者 | 【确证：不存在】 |
| 仓内任何「直接跳转授权」的实测 / 抓包 / 官方文档记录 | 零命中 | 【仓内未见】 |

---

## 3. 旧 RN 版 `cas.ts`（及其它旧客户端代码）里有没有微信登录的实现？

### 3.1 【确证】`TOOL/core/login/cas.ts`（805 行）**无微信实现**，只有账密与短信

`wc -l` 实测 805 行（见 §6）。全文件 grep `wechat|微信|扫码|二维码|qrcode|poll|轮询` **只命中 `:5` 一处注释**：

> `:4-6`：「背景：教务系统自 2026-10-23 起取消首页『账号 + 密码』直接登录，旧的 `check.asp` 口令提交路径失效，改为『微信扫码』或『统一身份认证』。**本模块实现后者**」

`:6` 明写「本模块实现后者」= 只实现统一身份认证（账密 + 验证码）。文件头链路注释 `:8-13` 与实际请求点：

| 步骤 | 方法 / URL | 行号 |
| --- | --- | --- |
| 取图形验证码 | `GET /api/v1/captcha?appid=…` | `:10`, `:238`（`axios.get`），`TOOL/TECH.md:25` |
| 账密登录换票 | `POST /api/v1/cas/login`，JSON `{appid, code, number:studentId, password, uuid}` | `:11`, `:535`（`jiaowuLoginByCas`），`BE/docs/tech/旧农屿WebVPN可重写契约清单.md:150` |
| 短信发送 | `POST /api/v1/sms/send`，`{uuid, code, type:"login", phone}` | `:615`，`旧农屿WebVPN可重写契约清单.md:151` |
| 短信登录换票 | `POST /api/v1/cas/loginByPhone`，`{code:smsCode, phone, appid}` | `:658`，`旧农屿WebVPN可重写契约清单.md:152` |
| 门户换票（WebVPN） | `POST /api/v1/cas/login` 用 `WEBVPN_PORTAL_APPID` | `:759`，`旧农屿WebVPN可重写契约清单.md:166` |

`BE/docs/tech/旧农屿WebVPN可重写契约清单.md:22` 记「`core/login/cas.ts`（805 行全读）」，`:45-51,147-153` 列出的导出与请求契约里**没有任何微信符号**。`TOOL/core/login/index.ts:358` 同样只是注释提到「微信扫码」，`:362-377` 的 `export {}` 清单列出 `getJiaowuCaptcha` / `jiaowuLoginByCas` / `jiaowuLoginByPhone` / `sendJiaowuSmsCode` / `prepareJiaowuTransport` / `loginWebVpnPortal` / `WEBVPN_PORTAL_APPID` 及类型，**无微信导出**。

→ **明确写：旧 RN `cas.ts` 无微信实现（只有账密与短信两条链路）**，也没有二维码渲染、轮询、微信 code 处理。

### 3.2 【确证】旧 RN 客户端里唯一「微信登录」相关代码 = 打开外部浏览器的引导面板，不是登录实现

`OLD/src/modules/jiaowu/components/JiaowuLoginPanel.tsx:360-390` 的 `WechatPane`（逐条见 §1.2）：只有 `Linking.openURL(教务首页)` + 三段文字 + 一个按钮，**无二维码、无轮询、无 SDK auth 调用、无凭证回传**，且 `:377` 要求用户「扫码确认后**回到农屿，用「自动登录」或「账密」进入**」——它不完成农屿登录。`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:447` 也只说「`JiaowuLoginPanel` 用 4 个 tab 承载四种方式」，`§11.3 :449-451` 的实现说明只覆盖自动登录 / 手机验证码面板。

### 3.3 【确证】`OLD/src/lib/wechat/` 是微信**分享**封装，不是登录

`OLD/src/lib/wechat/index.ts`（192 行）全文只有：`registerApp`（`:99-117`）、`isWechatInstalled`（`:120-134`）、`shareNongyuWebpage`（`:141-192`），底层 `expo-native-wechat`（`:48`）。`OLD/src/modules/mine/constants/share.ts:6-7` 给 `WECHAT_APP_ID = "wx174b5a4d92447b73"`、`WECHAT_UNIVERSAL_LINK = "https://nongyu.app/"`；`OLD/app.json:8` 配 `scheme: ["nongyu", "wx174b5a4d92447b73"]` 作微信回跳。

`DOCS/nongyu-rn-app/specs/分享农屿.md:33` 规格**逐字**：「**不做微信登录 / 支付 / 小程序分享**」；`:6,15,22-24` 说明这是「分享农屿」需求；`:95,155` 说明 `wx174b5a4d92447b73` scheme 是为分享回跳加的。`DOCS/forhuman/rawprds/nongyu-rn-app/MyPage/分享农屿PRD.md:34` 同样把「微信登录」列在**不做**项。

→ 这个 AppID / scheme **不能**作为「学校 CAS 微信登录」的支撑（不是学校侧登记、不是登录资质、规格明确排除登录）。

### 3.4 【确证】后端 Go 源码里的 `wechat` 字样全是占位 / 画像字段 / 注释，无微信登录实现

`BE/internal` 全量 grep `wechat|Wechat`：

- `BE/internal/identity/login/service.go:4,40,42,74,324`：`SchoolCredential.WechatCode` 字段与「账密 / 微信 / 电话」注释；`:324` 注释「其它学校方式（微信/短信）不写」密码镜像。
- `BE/internal/identity/flow/handler.go:47,52,102`：`schoolMethod` / `wechatCode` 声明与映射。
- `BE/internal/identity/flow/flow_test.go:44,301-306`、`BE/internal/identity/identity_test.go:230-234`：单测构造 `Method:"wechat"` 仅用于验证「不写密码镜像」。
- `BE/internal/app/schoolauth_bridge.go:28-47`：丢弃 `WechatCode`（§2.3）。
- `BE/internal/school/schoolauth/adapter.go:29`：注释「客户端先用自己那套方式完成学校登录（账密/微信/短信，**都在客户端侧**）」——**把微信放在客户端侧，但没有任何实现**。
- `BE/internal/school/xsgz/detail.go:71,201,247`、`BE/internal/school/cas/exchange.go:219`、`BE/internal/platform/store.go:1059,1171,1203,1222,1339,1378`：`wechat` 是**学生画像 / 联系方式字段**（微信号），不是登录方式。
- `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:94` 换票响应 JSON 里的 `"wechat":"..."` 同属画像字段。

`AP/docs/research/backend-plus-login-ui-facts.md:74,141,150,397` 已把这些逐项确证为「无实现 / 仅占位 / 无任何消费 / 桥接层丢弃」。

### 3.5 【仓内未见】旧 RN 侧二维码 / 轮询 / 微信授权码相关代码

`OLD/src` 全量 grep `qrcode|二维码|扫码|轮询|poll|wechatCode|SendAuth|sendAuth`：命中的「轮询」全在广场帖子 / 我的回复（`src/modules/center/api/postReplies.ts:4,47-48`、`src/modules/center/hooks/useForegroundState.ts:7`），与登录无关；「微信」命中全在分享模块与 §3.2 那个面板。**无一处登录态二维码或授权轮询**。

---

## 4. `auth.sicau.edu.cn/api/v1/*` 端点清单里有没有微信相关端点？

### 4.1 【确证】仓内实测 / 契约笔记里的完整 `/api/v1/*` 清单 —— **无微信端点**

逐条列出（这就是仓内全部已记录的 `api/v1` 端点）：

| 端点 | 用途 | 一手来源（文件:行号） |
| --- | --- | --- |
| `GET /api/v1/captcha?appid=<appid>` | 取图形验证码（加密响应体 `data` + `id`(uuid) + `requestId`） | `TOOL/TECH.md:25`；`TOOL/core/login/cas.ts:10,238`；`BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:14`；`DOCS/nongyu-rn-app/tech/WebVPN探索.md:16,77,160,345,351,370`；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:28,227` |
| `POST /api/v1/cas/login`（账密 + 验证码） | 登录换票，响应 `Set-Cookie: go-admin-tgt`；解密 `data` 得 `{callbackUrl, st, users:[{id}]}` | `TOOL/TECH.md:27`；`TOOL/core/login/cas.ts:11,351,535,759`；`BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:16-19`；`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:18-27`；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:230-232,322-324`；`DOCS/common/BugLog.md:876,881,885` |
| `GET /api/v1/cas/login?appid=<UUID>`（带 tgt 换票） | 后端用 `go-admin-tgt` 换任意已注册 appid 的 st | `BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:18`；`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:24,42-46,50-53`；`BE/docs/tech/实测-门户appid免密换票.md:5,10,13,24,45,79`；`AP/docs/research/backend-plus-login-ui-facts.md:181,404` |
| `POST /api/v1/sms/send` | 发送登录短信验证码 | `TOOL/core/login/cas.ts:615`；`BE/docs/tech/旧农屿WebVPN可重写契约清单.md:48,151`；`AP/docs/research/backend-plus-login-ui-facts.md:247` |
| `POST /api/v1/cas/loginByPhone` | 短信验证码登录换票 | `TOOL/core/login/cas.ts:658`；`BE/docs/tech/旧农屿WebVPN可重写契约清单.md:47,152` |

相邻但**不是 `/api/v1/`** 的页面 / 端点符号（一并列出以免漏读）：`/sso/login?appid=<名>&service=…`（前端 SSO 路由，`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:223-225`、`WebVPN探索.md:80,119,124`、`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:46,50`）、`/wui/#/?appid=…&service=…`（`WebVPN探索.md:125,128-129`）、`/do-confirm-login` 与 `/do-second-login`（本地表单端点符号，`WebVPN探索.md:144`）。

**结论【确证】**：仓内 `/api/v1/*` 端点清单共 5 条，全部是「账密 + 图形验证码 / 短信 / tgt 换票」，**没有任何 `/api/v1/wechat/*`、`/api/v1/cas/loginByWechat` 或任何微信相关端点**。

### 4.2 【仓内未见】微信端点 / 微信登录请求形状的 grep 结果

对全仓 `*.{ts,tsx,js,go,md,json,kt}` grep `loginByWechat|loginByWx|loginByWeixin|wechat/|weixin/|wxLogin|qrcode/login|scan/login|loginByQr|微信授权|微信登录`：**零命中**（命中的「微信登录」全部来自 `AP/docs/research/wechat-scan-direct-jump.md` 与 `AP/docs/research/mobile-oauth-landing.md` 两篇讲**微信开放平台**的笔记，以及 `DOCS/nongyu-rn-app/specs/分享农屿.md:33` 的「不做微信登录」）。

**【仓内未见】≠【官方不存在】**：仓内这份清单是「旧 RN 包 `cas.ts` 用到的 + 后端实测过的」端点集合，**不是学校平台的完整 API 枚举**。学校平台若另有微信端点而仓内从没调用过，仓内查不到是正常的；要定论必须实测 / 抓包 / 查官方文档（§5）。

### 4.3 易误读项澄清

- `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:94` 的 `"wechat":"..."`、`BE/internal/school/xsgz/detail.go:71,201,247` 的 `Wechat` 字段：是**换票响应 `user` 画像里的微信号联系方式**，不是登录端点、不是授权结果（§3.4）。
- `BE/docs/tech/架构落点-身份仓.md:104`、`BE/docs/tech/领域模型-用户与登录-消息.md:77`：是**模型里的登录方式名称**，不是端点清单（§1.4）。
- `BE/docs/tech/调研-OAuth后端落点.md` / `调研-OAuth汇总与开放问题.md` / `调研-五家OAuth官方接入事实.md` 里大量 `wechat`：全部是**微信开放平台五家 OAuth 调研**（`oauth:wechat` kind、`unionid`、AppSecret、`sns/oauth2/access_token`），与「学校 CAS 自带微信登录」无关，不作为本文结论依据。

---

## 5. 结论（三档）与「要定论还缺什么」

### 5.1 对维护者附加要求的直接回答

> 「看看这个扫码能不能用直接跳转授权替代，不能就算了」

**【仓内未见】**：仓内**没有任何**材料支撑「能」，也没有任何材料能证明「官方不能」。具体是两层缺失：

1. **形态缺失**：仓内没有一份材料记录「学校统一身份认证平台（`auth.sicau.edu.cn`）自带的微信登录」是什么形态。仓内记到的「微信扫码」是**教务系统首页**的浏览器入口 + 「川农微教务」公众号绑定 + 人工扫码（`JiaowuLoginPanel.tsx:366-387`、`教务鉴权方案.md:417`），且教务通知把它与「统一身份认证登录」写成**并列的两个入口**（`教务鉴权方案.md:214`）。唯一一次对 `auth` 登录页 `/wui/` 的实测只记到「仅暴露统一认证 Tab（`only-auth`）」和「加载了 jweixin 脚本」（`WebVPN探索.md:68,96,129,144`），**没有微信登录 Tab 的记录**。
2. **直跳路径缺失**：仓内无学校侧微信授权 URL / scheme、无可用的学校侧微信 appid、`/api/v1/*` 清单无微信端点、后端 `wechatCode` 被丢弃（§2、§3.4、§4）。

因此按 Q12′ 的「不能就算了」：**本轮无法给出「能」，应记为「仓内查不到，需 §5.3 的实测才能定」；在实测之前不要按「能直跳」做实现假设，也不要把「仓内未见」写成「官方不支持」**。

### 5.2 分档汇总

| # | 事实 | 档位 | 依据 |
| --- | --- | --- | --- |
| 1 | 教务通知把「微信扫码登录」与「统一身份认证登录」列为并列两入口 | 【确证】 | `DOCS/nongyu-rn-app/tech/教务鉴权方案.md:211-215`；`DOCS/common/BugLog.md:873`；`TOOL/TECH.md:23` |
| 2 | 仓内「微信扫码」形态 = 浏览器打开教务首页点扫码 + 绑「川农微教务」公众号 + 人工确认；二维码在浏览器页面，不在 App 内；扫码不向农屿回传凭证 | 【确证】 | `OLD/src/modules/jiaowu/components/JiaowuLoginPanel.tsx:366-387`；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:417` |
| 3 | `auth` 登录页 `/wui/` 实测只暴露「统一认证」Tab（`only-auth`）；页面加载了 `jweixin`（微信 JSSDK）但用途未记 | 【确证】+【待实测】 | `DOCS/nongyu-rn-app/tech/WebVPN探索.md:68,96,128-129,144` |
| 4 | 学校 CAS 平台自身微信登录的形态（App 内贴码 / 跳微信客户端 / H5 / 公众号 / 小程序） | **【仓内未见】** | §1 全节；全仓 grep 零命中 |
| 5 | 从 App 直跳微信完成学校 CAS 授权所需的 URL / scheme / appid / 端点 | **【仓内未见】** | §2；全仓 grep 零命中 |
| 6 | `auth.sicau.edu.cn/api/v1/*` 清单有无微信端点 | 【确证：无】（仓内清单共 5 条，均非微信）；完整枚举【待实测】 | §4.1、§4.2 |
| 7 | 旧 RN `cas.ts`（805 行）有无微信实现 | 【确证：无微信实现】，只有账密 + 短信 | §3.1；`TOOL/core/login/cas.ts:5,10-11,238,535,615,658,759` |
| 8 | 旧 RN 客户端其它代码有无微信登录实现 | 【确证：无】，仅一个外部浏览器引导面板 + 分享封装（规格明确不做登录） | §3.2、§3.3；`DOCS/nongyu-rn-app/specs/分享农屿.md:33` |
| 9 | 后端是否接 `wechatCode` / 是否提供 CAS 登录页 URL | 【确证：不接、不提供】，只收 `go-admin-tgt` | `BE/internal/app/schoolauth_bridge.go:28-47`；`BE/internal/identity/flow/handler.go:47,52`；`AP/docs/research/backend-plus-login-ui-facts.md:74,107,157-172,190-195` |
| 10 | 后端领域模型「学校微信」是否有形态定义 | 【确证：只有名称，无形态】 | `BE/docs/tech/领域模型-用户与登录-消息.md:77,83`；`BE/docs/tech/架构落点-身份仓.md:104` |

### 5.3 要定论还缺什么（按优先级）

1. **【待实测·最关键】学校统一身份认证平台登录页的微信入口实测**：用真机 / 浏览器打开 `https://auth.sicau.edu.cn/sso/login?appid=<教务 appid>&service=<…>` → `/wui/#/?appid=…`，看有没有「微信登录」Tab；若有，确认它是四种候选形态里的哪一种（App 内贴二维码 / 跳微信客户端授权 / H5 网页授权 / 公众号或小程序）。注意 `WebVPN探索.md:144` 那次 `only-auth` 实测用的是**门户 appid**（`:120`），不能代表教务 appid 或直连 auth，必须分别测。
2. **【待实测】对那个微信入口抓包**：拿到授权 URL / scheme / appid / 回调端点 / 凭证形状（是微信 `code`？还是直接下发 `go-admin-tgt`？），才能判断「能不能不显示二维码、直接拉起微信授权」。
3. **【待实测】学校平台官方接口文档或 wui 前端 bundle**：确认 `auth.sicau.edu.cn` 是否存在仓内没记录过的 `/api/v1/wechat/*` / `loginByWechat` 类端点，以及 `res.wx.qq.com` 的 `jweixin` 脚本在 wui 里到底被哪个功能调用（`WebVPN探索.md:68,96` 只证明被加载，未证明用于登录）。
4. **【待实测】「川农微教务」公众号链路**：确认它是公众号网页授权（`snsapi_base` / `snsapi_userinfo`）、带参二维码事件推送，还是仅「账号绑定」；是否存在可供 App 调用、能把扫码结果换成 `go-admin-tgt` 的接口。仓内只有「需先绑公众号」这一句（`JiaowuLoginPanel.tsx:374-376`）。
5. **【待实测】后端换 tgt 这一环**：即便拿到某种微信凭证，当前后端只认 `go-admin-tgt`（`AP/docs/research/backend-plus-login-ui-facts.md:84-108` 确证）。需要确认学校平台微信登录成功后是否会下发 `go-admin-tgt`；若不会，就必须新开规格把「微信凭证 → tgt」补进后端，不能靠现有 `wechatCode` 字段（它已被丢弃）。
6. **【待核实·需维护者指认】Q12′ 里「学校 CAS 自带的微信登录方式」具体指哪个页面**：教务首页的扫码（`JiaowuLoginPanel.tsx:367`）还是 auth 登录页里的微信 Tab？仓内把两者写成并列入口（`教务鉴权方案.md:214`），指认错了后面所有实测目标都会偏。
7. **【待实测】校园网/真机环境**：`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:330` 记「真机验收仍需校园网环境：教务系统已『仅限校园网内访问与绑定』」；本机无真机、无学校账号，本轮不具备实测条件。

### 5.4 本轮明确**没有**做的事（避免把边界写成结论）

- 未访问 `auth.sicau.edu.cn` / `jiaowu.sicau.edu.cn` / 微信开放平台任何页面，未抓包。
- 未把「仓内未见微信端点」写成「学校平台官方没有微信端点」：仓内清单只是旧客户端 + 后端实测用过的端点集合（§4.2）。
- 未把 `AP/docs/research/wechat-scan-direct-jump.md` 的「开放平台无免 SDK 直跳」结论搬来否定学校 CAS 直跳（文首「区别」、§2.4）。
- 未推翻 `AP/docs/research/backend-plus-login-ui-facts.md` 任何结论（文首「关系」）。
- 只读核查：只新建本文一篇，未改任何既有文件、未 git commit。

---

## 6. 顺带回报：旧 RN 版客户端源码在本机的实际路径（Q18=B 的事实前提）

**见到了**，且 `cas.ts` 805 行那个包在本机真实存在：

| 对象 | 绝对路径 | 核验 |
| --- | --- | --- |
| 旧 RN 版客户端 | `D:\IT\program\web-program\nongyu-system\apps\nongyu-rn-app\` | `ls` 实测存在（`app/`、`src/`、`modules/`、`app.json` 等齐全） |
| **`core/login/cas.ts`（805 行）** | **`D:\IT\program\web-program\nongyu-system\packages\nongyu-tool-jiaowu\core\login\cas.ts`** | `wc -l` = **805**；`find ../../ -name cas.ts` 全仓唯一命中；已读 `:1-40` 与契约清单转录全文 |
| 该工具包根 | `D:\IT\program\web-program\nongyu-system\packages\nongyu-tool-jiaowu\` | `TECH.md`、`core/login/index.ts`、`core/webvpn/`、`dist/index.d.ts` 齐全 |
| 旧 RN 版文档 | `D:\IT\program\web-program\nongyu-system\docs\nongyu-rn-app\` | `tech/教务鉴权方案.md`、`tech/WebVPN探索.md`、`specs/分享农屿.md` 等 |
| monorepo 根 | `D:\IT\program\web-program\nongyu-system\` | 后端契约清单 `BE/docs/tech/旧农屿WebVPN可重写契约清单.md:14` 把它记作 `@OLD/` |

**关于 `@OLD`**：本机**没有**名为 `@OLD` 的实体目录；`@OLD/` 只是 `BE/docs/tech/旧农屿WebVPN可重写契约清单.md:3,7,14` 里对「旧农屿 monorepo 根 `D:\IT\program\web-program\nongyu-system\`」的**引用别名**（该文 `:13-14` 明确定义 `@NEW/` = 新 Go 后端、`@OLD/` = 旧 monorepo）。全机 `find -iname "*OLD*"` 的命中全是 Gradle `build/intermediates/*JniLibFolders` 与 `node_modules` 里的 `oldarch` / `600SemiBold` 等无关目录。

→ 对 Q18=B「移植 CAS 链路」：**源码在本机、可移植**；但移植过来的是**账密 + 短信 + 验证码**链路（`cas.ts` 四条 `api/v1` 请求），**不含微信**（§3.1）。若 Q18=B 的意图包含「把学校微信登录一起移植」，那部分**没有旧代码可抄**，需按 §5.3 实测后新写。
