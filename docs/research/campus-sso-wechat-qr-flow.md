# 川农统一身份认证「微信扫码」完整链路：二维码生成 / state 下发 / 回调感知 / 取 goAdminTgt

> **日期**：2026-11-12
> **起因**：`/wayfinder` 探索地图 **#76《登录页（跟后端对接）：统一身份认证 / 平台登录的完整探索地图》** 下的 research 票 **#84《微信扫码完整链路：二维码生成 / state 下发 / 回调感知 / 取 goAdminTgt》**，按 `/research` 技能规则做**仓内一手事实核查**。二维码 payload 来自承载票 **#74 第 8 条评论（2026-11-12 03:10:30Z「第十一轮：二维码实证」）** 的离线解码结果。
> **只读核查**：只新建本文一篇；未修改任何既有文件、未 git commit、未访问真实授权 URL（state 为一次性登录事务，访问会触发真实授权且需校园网/学校账号）、未记录任何真实凭据值（tgt / st / cookie / 一次性 state 串均不复写，appid 是公开应用标识非凭据）。
> **与既有笔记的关系（补充，不推翻）**：
> - `docs/research/campus-sso-wechat-direct-auth.md`（2026-11-12）：那篇的结论是「仓内没有任何材料记录学校 CAS 微信登录的形态」，并把 `/api/v1/cas/loginByQr` 标为【仓内未见】，§5.3 列出「要定论还缺什么」。**本文用一张离线解码的二维码把「形态」这一格补成【确证】**（网页授权 + CAS 回调），但**不推翻**它关于「旧客户端/后端源码里没有微信实现、没有直跳支撑、`wechatCode` 被丢弃」的全部结论；#74 评论 8 第 1 点已订正「端点存在」，本文沿用该订正并把它拆细到四环。它的 §5.3 清单本文逐项继承为「待真机/抓包」清单（§7）。
> - `docs/research/backend-plus-login-ui-facts.md` §2.4：那篇把「App 从哪取得 tgt / 能否用 WebView」列为【待核实】。**本文不回答这个问题**（后端源码确实不给答案），只在环 4 把「微信链路是否也下发 tgt」标为【待实测】，并把该篇 §1.2/§2.1 的硬约束接到环 5 的步骤清单里。
> - `docs/research/wechat-scan-direct-jump.md`、`mobile-oauth-landing.md`：微信**开放平台**一手核查，仅作形态对照与边界标注，**不适用**于本需求的学校 CAS 微信（#74 第 3 条评论 Q12=A 已锁定）。
> - `../nongyu-backend-plus/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md`、`实测-川农CAS真实链路-tgt万能凭据.md`：账密/短信链路的 `go-admin-tgt` 实测，是环 4「tgt 由 `Set-Cookie` 下发」的唯一一手来源；**不能外推**到微信链路。
> - 旧 RN 文档 `../../docs/nongyu-rn-app/tech/WebVPN探索.md`、`教务鉴权方案.md` 与旧客户端 `../nongyu-rn-app/`：仓内对学校登录页 `/wui/` 的唯一一手实测，以及旧 RN「微信格」的真实落地代码。
> - 跨工作区知识库条目 `notes/topics/nongyu/川农统一身份认证「微信扫码登录」实证形态：二维码解码出-loginByQr-网页授权链路.md`（solution, verified, 2026-11-12）：解码方法与结论的沉淀；本文是它在**仓内**按票 #84 四问展开的细化，不重复解码过程。

## 路径约定与确定程度

| 前缀 | 实际目录 |
| --- | --- |
| `AP/` | `apps/nongyu-app-plus/`（本工作区，笔记落点 `docs/research/`） |
| `BE/` | `apps/nongyu-backend-plus/` |
| `OLD/` | `apps/nongyu-rn-app/`（旧 RN 客户端） |
| `TOOL/` | `packages/nongyu-tool-jiaowu/`（旧 CAS 工具包） |
| `DOCS/` | `docs/`（monorepo 根下的旧 RN 文档） |
| `ROOT/` | `D:\IT\program\web-program\nongyu-system\`（monorepo 根） |

全文三档，与 `campus-sso-wechat-direct-auth.md` §0 的口径一致：

- **【确证】**：仓内源码 / 实测笔记 / 规格原文 / 票面一手记录可直接引用。
- **【仓内未见】**：对指定范围做过 grep 后零命中；**只说明仓内没有这份材料，不写成「官方没有」**。
- **【待实测】**：需真机 / 浏览器 / 抓包 / 校园网 / 学校平台官方文档才能定，本轮无此环境。

---

## 0. 一句话总览

**这张二维码把「学校 CAS 自带微信登录」的形态坐实了（微信网页授权 → 学校 CAS 回调 `loginByQr`），但「学校登录页怎么生成这张码、App 怎么拿到、扫码后 App 怎么知道完成、微信成功后 tgt 从哪来」四环在仓内全部零记录。** 唯一被仓内实测锁死的是终点：农屿后端只认 `go-admin-tgt`（及整套同域 cookie），`wechatCode` 被桥接层丢弃；因此无论中间四环怎么走，**客户端必须自己把 `go-admin-tgt` 取到手，再交 `POST /identity/login {method:"school", goAdminTgt, cookie}`**。四环的档位：环 1 =【确证（仅 payload 形态）】+【仓内未见（生成方/接口形状）】+【待实测（URL 是否每次变化）】；环 2 =【仓内未见】；环 3 =【仓内未见】；环 4 =【确证（账密链路的 tgt 由 `Set-Cookie` 下发、后端只收 tgt）】+【待实测（微信链路是否也下发 tgt、跳到哪）】。

---

## 1. 起点：已解出的二维码 payload（【确证】的唯一新事实）

一手来源：承载票 **#74 第 8 条评论**（离线 OpenCV 三路互证解码，308×308 标准黑白 QR），及知识库 solution 条目。逐字结构（**state 的一次性串按本轮约束脱敏不复写**）：

```text
https://open.weixin.qq.com/connect/oauth2/authorize
  ?appid=wx7c220ea873c845e0
  &redirect_uri=https%3A%2F%2Fauth.sicau.edu.cn%2Fapi%2Fv1%2Fcas%2FloginByQr%3Fappid%3Dywtb%26cascallback%3Dwxlogined
  &response_type=code
  &scope=snsapi_userinfo
  &state=loginByQr:<一次性事务串>       ← 本笔记不复写该串
  #wechat_redirect
```

| 项 | 值 | 档位与依据 |
| --- | --- | --- |
| 授权形态 | 微信**网页授权**（`connect/oauth2/authorize` + `snsapi_userinfo` + `#wechat_redirect`），不是网站应用 `qrconnect/snsapi_login`，也不是移动应用 `SendAuth.Req` | 【确证】#74 评论 8；形态对照见 `AP/docs/research/wechat-scan-direct-jump.md:136-142` |
| 学校侧微信 appid | `wx7c220ea873c845e0` | 【确证】#74 评论 8；**仓内此前未记录**（全仓 grep `wx7c220ea873c845e0` 零命中）。旧 RN 分享用是另一个 `wx174b5a4d92447b73`（`campus-sso-wechat-direct-auth.md:122,179`） |
| 回调端点 | `auth.sicau.edu.cn/api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined` | 【确证·作为 redirect_uri 存在】#74 评论 8。**该端点的请求方法 / 参数 / 响应形状仓内未见**（§2.1） |
| state | `loginByQr:` + 一次性事务串 | 【确证·字面】#74 评论 8；「服务端生成、防 CSRF、一次性」是 OAuth2 通例（`BE/docs/tech/调研-五家OAuth官方接入事实.md:18,75`；`BE/docs/tech/调研-OAuth后端落点.md:132,205`），**学校平台具体由哪个接口下发 state 仓内未见** |
| fragment | `#wechat_redirect` | 【确证】#74 评论 8；微信内置浏览器要求（同上通例） |

**由此订正（补充，不推翻）**：`campus-sso-wechat-direct-auth.md:114,221,225` 把 `loginByQr` 与「学校 CAS 微信形态」标为【仓内未见 / 零命中】。这张码证明**该端点确实存在**（旧客户端/后端从没调用过，所以 grep 零命中），并给出形态。但那篇关于「无直跳支撑 / 旧 `cas.ts` 无微信 / `wechatCode` 被丢弃 / 教务首页是并列入口」的结论**全部继续有效**。

---

## 2. 环 1：二维码由学校登录页哪个接口/页面生成？`state` 从哪来？URL 是否每次变化？

### 2.1 【确证】仅到「payload 形态」，【仓内未见】到「生成方」

- **【确证】** 二维码内容 = 微信网页授权 URL（§1），回调落在 auth 平台的 `/api/v1/cas/loginByQr`。这证明学校平台**有**一个 `loginByQr` 端点、且登录页**确实会产出**这种码。
- **【仓内未见】** 学校登录页**哪个接口 / 哪段代码生成这张码**：
  - 全仓 grep `loginByQr|wxlogined|wx7c220ea873c845e0|connect/oauth2/authorize`（覆盖整个 monorepo，含 `*.ts/tsx/js/go/md/json/kt`）→ `loginByQr` 仅命中 `AP/docs/research/campus-sso-wechat-direct-auth.md:114,225` 两处「零命中」陈述本身；`wxlogined` / `wx7c220ea873c845e0` / `connect/oauth2/authorize` **零命中**。
  - 全仓 `*.ts` grep `qrconnect|WxLogin|wxLogin|oauth2/authorize|state=|showqrcode|qrcode/create|open.weixin.qq.com` → **无匹配**。
  - `/wui/` 前端 bundle **不在仓内**：`DOCS/nongyu-rn-app/tech/WebVPN探索.md:96,144` 只是实测记录「wui HTML 里微信 JSSDK 等外链同样编码」「登录页仅暴露统一认证 Tab（`only-auth`）」，`TOOL/core/login/cas.ts:732` 只是把 `"/wui/"` 当重定向判停条件；**没有任何 wui bundle 源码被 vendored**。仓内也没有「二维码图片接口」的请求形状。
- **【仓内未见】** `/api/v1/cas/loginByQr` 的**请求形状**（GET 还是 POST、带不带 `code`/`state`、响应是 302 还是 JSON、是否 `Set-Cookie`）：`campus-sso-wechat-direct-auth.md:209-221` 列出的仓内 `/api/v1/*` 端点共 5 条（captcha / cas/login / cas/login?appid / sms/send / loginByPhone），**不含 `loginByQr`**。按 OAuth2 通例授权服务器会原样把 `code` + `state` 带回 `redirect_uri`（`BE/docs/tech/调研-五家OAuth官方接入事实.md:18`、`调研-OAuth后端落点.md:132`），但**这是通例推断，不是学校平台的事实**，本笔记不据此臆测端点形状。
- **【仓内未见】** `state` 的**下发接口**：仓内没有任何「取 state / 取二维码 / 开扫码事务」的端点记录。字面 `loginByQr:` 前缀与 44 字符随机串符合「服务端生成的一次性事务句柄」（同 `调研-OAuth后端落点.md:132` 的 `OAuthStateStore.Put`/`Consume` 语义），但**学校平台由哪个接口生成、token 怎么绑到登录会话，仓内未见**。

### 2.2 【待实测】URL 是否每次变化

- 单张截图只能证明「这一时刻」的码长这样。`state` 是一次性事务串 → **推断**每次刷新/每次开新事务都会变，但仓内**没有两张码的对照**，不能写成确证。
- 一并待实测：`appid=ywtb`（名字字符串，与 `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:50-53` 的「appid 有两种形态」呼应）、`cascallback=wxlogined` 是否随入口变化；仓内 `cascallback` 只以 `cascallback=choose` 出现 4 处（`TOOL/core/login/cas.ts:732`、`WebVPN探索.md:20,267,325`），语义是「已识别登录态后的 SSO 路由/选账号」，**`wxlogined` 零命中**（详见环 3）。

**环 1 档位**：【确证】payload 形态与端点存在；【仓内未见】生成接口、二维码图片接口、wui bundle 生成代码、`loginByQr` 请求形状、`state` 下发接口；【待实测】URL 每次是否变化、`state` 生命周期与绑定方式。

---

## 3. 环 2：App 侧如何拿到这张码？

题目给的三种候选，逐项分档：

| 候选形态 | 档位 | 依据 |
| --- | --- | --- |
| A. 学校服务端下发二维码图片 | **【仓内未见】** | 全仓无二维码图片接口、无 `data:image/png;base64` 之外的扫码图记录（`教务鉴权方案.md:228` 的 base64 图是**图形验证码**，不是微信二维码） |
| B. App 自己拼 URL 用 QR 库生成 | **【仓内未见】** | 全仓 `*.kt` grep `qrcode\|QRCode\|二维码\|扫码\|zxing\|WebView\|CookieManager\|轮询\|WebSocket` 仅 1 命中：`OLD/modules/nongyu-webvpn-cookie/.../NongyuWebVpnCookieModule.kt:10`（注释「不用 android.webkit.CookieManager」），与登录无关。`AP/app/src/main` 下无任何二维码库、无登录 UI（glob 出的 `*.kt` 只有 `net/`、`skin/`、`ui/timetable/`、`ui/ai/`） |
| C. WebView 加载登录页、由页面渲染二维码 | **【仓内未见】** | app-plus 无 WebView 代码；`backend-plus-login-ui-facts.md:210-215` 明写「WebView 流程、如何从 WebView 的 cookie 里读 `go-admin-tgt`，源码未见」；`WebVPN探索.md:221-232` 的「方案 B — WebView」是**旧 RN 的 WebVPN 备选方案**，不是微信扫码的实现 |

**【确证·旁证，但对象不同】** 旧 RN 里唯一「微信扫码」落地代码是 `OLD/src/modules/jiaowu/components/JiaowuLoginPanel.tsx:360-390` 的 `WechatPane`：`Linking.openURL("https://jiaowu.sicau.edu.cn/web/web/web/index.asp")`（`:366-368`）用**外部浏览器**打开教务首页，`:373` 明写「二维码显示在浏览器页面里，不是 App 内贴二维码」，`:377` 要求用户「扫码确认后回到农屿，用自动登录或账密进入」，`:385-386` 写「人工确认流程，无法自动完成」。**但 #74 评论 8 第 5 点已明确：那是教务首页「川农微教务」公众号绑定入口，与本码的 auth 平台 `loginByQr` 网页授权是不同入口，不能混。**

**【确证·形态约束，不等于「官方不允许」】** 由于 `state` 是服务端下发的一次性串、URL 带 `#wechat_redirect` 且要求 `snsapi_userinfo`（需微信内置浏览器打开 + 用户确认），App 若走候选 B，前提是「App 能先取得服务端生成的一次性 state」——**这一点仓内未见任何接口记录**；若走候选 C，二维码由 wui 页面渲染则 App 无需拼 URL。两种都**待实测**。本轮**不把「仓内没记录 App 自拼 URL」写成「官方不支持 App 自拼 URL」**。

**环 2 档位**：三种候选全部【仓内未见】；【确证】只有「旧 RN 微信格 = 外部浏览器引导面板、不渲染二维码、不回传凭证（且对象不同）」与「后端不提供任何登录页 URL / WebView 流程（`backend-plus-login-ui-facts.md:155-172,190-195,210-215`）」。

---

## 4. 环 3：用户扫码授权后，App 如何感知完成？

### 4.1 【仓内未见】轮询 / WebSocket / 长轮询 / 轮询 state 的任何记录

- 全仓 `*.kt` 的 `轮询|polling|poll|WebSocket|longPoll|长轮询` 零命中（唯一命中见 §3，是 CookieManager 注释）。
- `OLD/src` 的 `setInterval|setTimeout|while(true)` 共 25 处：`home/hooks/useTypewriter.ts:32`（打字机）、`center/api/postReplies.ts:47-48`（「我的新回复」轮询）、`center/hooks/useForegroundState.ts:7`（前台 gate 注释）、`telemetry/client.ts:53`、`agent/*`——**无一处登录态 / 授权结果轮询**。
- 旧 RN 的「自动登录」是 `apps/nongyu-node-server/src/modules/jiaowu-hosted/autoLogin.ts:73-124`：账密 + OCR 识别图形验证码的**重试循环**（`for attempt 1..maxAttempts`），不是扫码轮询；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:217` 更明写「因此**静默自动重登必须移除**」。`JiaowuLoginPanel.tsx:377` 要求扫码后用户**手动**回农屿另走账密/自动登录。

### 4.2 `cascallback=wxlogined` 是页面回调还是 App 可监听的？——【仓内未见】

- 全仓 `cascallback` 仅 4 命中，**全是 `cascallback=choose`**：`TOOL/core/login/cas.ts:732`（跟随重定向时遇到 `/wui/` 或 `cascallback=choose` 就停，交给第二次图形码）、`WebVPN探索.md:20,267,325`（真账号补验：门户登录后打开编码后的教务 `caslogin.asp` 落到 auth `wui`，出现 `cascallback=choose`，随后无第二次图形码自动进入教务）。**`wxlogined` 零命中。**
- 从这 4 处能确证的只有：`cascallback` 是**学校平台前端 SSO 路由的查询参数**，由页面/浏览器消费、用于指示「已识别登录态后的分支」，**不是**仓内记录过的 App 可监听回调。`cascallback=wxlogined` 具体触发什么（页面跳转？JS 回调？轮询状态变化？）、能否被 App 通过 URL 拦截 / JS bridge 感知，**仓内未见**。按同族参数推断它更像页面侧标记，但**不写成结论**。

### 4.3 【确证·边界标注】开放平台口径不能替代学校自建链路

`AP/docs/research/wechat-scan-direct-jump.md:151,163,167-172` 记录：微信官方三类二维码能力（移动应用 `Login_via_Scan` 的 SDK 回调、网站应用 `qrconnect` 的 `redirect_uri?code&state` 或 JS 回传、公众号带参二维码的事件推送）**都不是**「App 展示二维码 + 轮询后端」形态；`:137` 记网站应用靠重定向/JS 回传 code。**但**（同 `campus-sso-wechat-direct-auth.md:135-137,279` 的边界）这整套是微信**开放平台**口径，学校 CAS 是自建平台，**不能用它否定学校页面自建的轮询/长连接机制是否存在**。学校 wui 页面内部用什么机制感知扫码完成，只能抓包。

**环 3 档位**：【仓内未见】轮询端点、WebSocket、长轮询、轮询 state、App 可监听回调；【确证】`cascallback` 在仓内只作页面侧 SSO 路由参数（4 处 `choose`）、旧 RN「自动登录」是账密 OCR 重试而非扫码轮询；【待实测】`wxlogined` 之后页面/服务端到底发生什么、App 走 WebView 时能否感知。

---

## 5. 环 4：最终如何拿到 `go-admin-tgt`（含整套同域 cookie）？

### 5.1 【确证】仓内 `go-admin-tgt` 是 `Set-Cookie` 下发的 —— 但只在**账密/短信链路**实测过

| 事实 | 文件:行号 |
| --- | --- |
| 账密 + 图形验证码 `POST https://auth.sicau.edu.cn/api/v1/cas/login` → 响应 **`Set-Cookie: go-admin-tgt=<tgt>`** | `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:18-19`；`BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:16-18`（表格第 3→4 步，注 57 字符）；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:230-232` |
| 取验证码不绑定 cookie，登录响应才下发 tgt | `实测-川农CAS真实链路-tgt万能凭据.md:49` |
| 拿到 tgt 后 `GET /api/v1/cas/login?appid=<UUID>`（Cookie 只带 `go-admin-tgt`）即可换任意已注册站点的 `st` | `实测-川农一次登录覆盖多站点的唯一解.md:21-27,42-46` |
| 手机 App（原生、无 WebView）**只需交一张 `go-admin-tgt`**，token / enterHall / 取数全由后端完成 | `实测-川农一次登录覆盖多站点的唯一解.md:470-476` |
| 后端只接收 `go-admin-tgt` 及同域 cookie；`schoolMethod=wechat` / `wechatCode` 被桥接层丢弃 | `BE/internal/app/schoolauth_bridge.go:28-47`（`:29` 注释「凭据形态只有一种：客户端完成学校登录后交来的 go-admin-tgt」、`:39-43` 把 `GoAdminTgt` 转成 `go-admin-tgt` cookie）；`AP/docs/research/backend-plus-login-ui-facts.md:74,107,141,150` |
| 服务端续期需要**整套同域 cookie**（`laravel_session` / `XSRF-TOKEN` 等），建议客户端一并传 `cookie` 字段，且**应始终填 `goAdminTgt` 字段**（只走 `cookie` 内嵌时 `tgtSaver` 不保管 tgt） | `backend-plus-login-ui-facts.md:110-121,340-341`；`BE/internal/school/schoolauth/adapter.go:27-34,35-46`（`Credential` 注释「客户端先用自己那套方式完成学校登录……然后把拿到的 go-admin-tgt 交上来」） |

### 5.2 【仓内未见 / 待实测】微信授权成功后 tgt 从哪来

- **学校平台直接下发 tgt，还是只建 CAS 会话需另换票？** → **【仓内未见】**。全仓没有任何「微信授权成功 → tgt」的记录；#74 评论 8 第 4 点与知识库 solution 条目的「学校平台完成统一身份认证并下发 go-admin-tgt」是**推断**（同为「App 侧仍需拿到 go-admin-tgt」），二者都在「边界」里明写「回调后是否直接下发 go-admin-tgt……仍需真机/抓包实测」。
- **`cascallback=wxlogined` 之后跳转到哪？** → **【仓内未见】**。`wxlogined` 全仓零命中；仓内只有 `cascallback=choose` 的 SSO 路由记录（§4.2）。不臆测它是 302 到某页面带 `Set-Cookie`，还是回 wui 前端由 JS 写 cookie，还是只建会话后另走 `GET /api/v1/cas/login?appid=...` 换票。
- **账密链路的 `Set-Cookie` 事实不能外推到微信链路**：`实测-川农一次登录覆盖多站点的唯一解.md:18-19` 与 `实测-川农CAS真实链路-tgt万能凭据.md:17` 的 `Set-Cookie: go-admin-tgt` 是 `POST /api/v1/cas/login`（账密）的响应；`loginByQr` 是另一个端点，其响应头/形状仓内未见。
- **若走 WebView 承载**：`go-admin-tgt` 是否 HttpOnly、能否由 `CookieManager` 读出，仓内未见（同类风险见 `WebVPN探索.md:295`：`wengine_vpn_ticket` 是 HttpOnly，JS 读不到；但那是 webvpn 域，不是 auth 域）。`backend-plus-login-ui-facts.md:210-215` 已把「如何从 WebView 的 cookie 里读 go-admin-tgt」列为【待核实】。

**环 4 档位**：【确证】账密链路 tgt 由 `Set-Cookie` 下发（行号见 §5.1 表）、后端只收 tgt + 同域 cookie；【仓内未见】微信链路的 tgt 下发方式、`cascallback=wxlogined` 后的跳转目标、是否需要另换票；【待实测】以上各项 + WebView 取 cookie 可行性。

---

## 6. 环 5：与 `POST /identity/login {method:"school", ...}` 接起来 —— 客户端侧必须自己完成的步骤

### 6.1 【确证】终点契约（后端这一侧已锁死）

- 请求体：`AP/docs/后端接口与NY1签名对接契约.md:142-153` —— `method:"school"` ✅、`goAdminTgt` ✅、`deviceId` 建议、`xsgzToken` 可不传、`cookie` 可选（整套同域 cookie）、`schoolMethod`/`wechatCode` 等「视方式」。契约 `:153-154` 明写「**客户端只做一次统一登录，交一张 `goAdminTgt` 即可**」。
- 实现层订正：`backend-plus-login-ui-facts.md:107-108,137-151` —— school 分支里 `wechatCode` / `schoolMethod` 等**被 `schoolauth_bridge.go:32-43` 丢弃**，不传 tgt 只传 `wechatCode` → 401 `bad_credentials`。因此**「把微信 code 交后端换 tgt」这条路不存在**。
- 客户端已有映射：`AP/app/src/main/java/com/nongyu/appplus/net/api/IdentityApi.kt:60-72`（`@POST("identity/login")`）、`AP/app/src/main/java/com/nongyu/appplus/net/model/IdentityDto.kt:100-121`（`LoginRequestDto` 全字段可空，`:104-105` `goAdminTgt`、`:116` `cookie`、`:113` `wechatCode` 仅为兼容保留）。响应 `LoginDto`（`:63-70`）的 `expiresAt` 是 **Unix 秒 int64**。
- 签名：`IdentityApi.kt:35-38` —— 除 `/healthz`、`/status` 外**一切请求都要 NY1 签名**，`/identity/login` 是公开端点（不挂 Bearer）但仍要签名；契约 `:305-306` 给了验收步骤。
- `deviceId`：#74 第 5 条评论 Q20=A（一次生成 UUID、普通 SharedPreferences 独立 key）；`backend-plus-login-ui-facts.md:367-390` 确证后端只在**登录时**收、不按每次请求校验、不生成不下发。

### 6.2 客户端侧必须自己完成的步骤清单

> 标 ⚠️ 的步骤依赖环 1~4 的【待实测】结果，本轮**不给端点形状、不给轮询间隔、不给回调约定**；这些必须等抓包回来再定，别先写死。

1. **定承载形态**：按 #87 拍板结果（App 内贴码 / 系统浏览器 / 直跳微信）选定 UI 载体。⚠️ 二维码从哪来、是否用 WebView 加载学校登录页，环 2【仓内未见】，待实测。
2. **取得并展示这张码**：若走「服务端/登录页渲染」则直接展示；若走「App 自拼 URL 用 QR 库」，前提是先取得服务端下发的一次性 `state`（⚠️ 下发接口仓内未见）。**不要访问已解出的那张授权 URL**（state 一次性，访问即触发真实授权）。
3. **让用户用微信扫码并完成 `snsapi_userinfo` 授权确认**（微信内置浏览器打开该 URL，需校园网/学校账号、需已绑定关系）——这一步是**人工确认**，仓内两处都印证：`JiaowuLoginPanel.tsx:385-386` 「人工确认流程，无法自动完成」、#74 评论 8 第 3 点「要求 `snsapi_userinfo` 用户确认」。
4. **感知授权完成** ⚠️：轮询哪个端点 / `cascallback=wxlogined` 如何变成 App 可感知事件 / 是否 WebSocket —— 环 3【仓内未见】，待抓包。**不要按「App 轮询某个 state 端点」先写实现**。
5. **取到 `go-admin-tgt` 与整套同域 cookie** ⚠️：这是硬要求（契约 `:148,151`；`backend-plus-login-ui-facts.md:110-121`）。微信成功后学校平台是否直接 `Set-Cookie` 下发 tgt、还是只建 CAS 会话需另换票、`cascallback=wxlogined` 后跳到哪 —— 环 4【待实测】。若承载是 WebView，还要解决从 WebView cookie 罐读 `go-admin-tgt`（是否 HttpOnly 待实测）并组装成 `Cookie` 头。
6. **组装并发送登录请求**：`POST /identity/login`，`{"method":"school","goAdminTgt":"<tgt>","cookie":"<整套同域 cookie>","deviceId":"<稳定 UUID>"}`，带齐 NY1 签名头。**不要填 `schoolMethod:"wechat"` 或 `wechatCode` 指望后端换票**（被丢弃）；`xsgzToken` 可省略（后端自换）。
7. **处理响应并落会话**：成功取 `token` / `expiresAt`（Unix 秒）/ `isNew` / `user`，按 #74 第 3 条评论 Q8 决议复用 `MinimalSessionStore` / `SessionStore.writeToken()`；失败按 #74 第 8 条评论 Q16 的失败/边界 UI 表分流（含 `need_school_auth` 自动切统一身份认证 tab、`bad_credentials` 提示重新取 tgt）。
8. **全程不得把微信 `code` 当凭据交后端**，也不得把「仓内未见」当「官方没有」写进代码注释或规格。

### 6.3 一句话版文字链路时序图

> **已确证（两端）**：微信扫码 →（微信网页授权 `snsapi_userinfo` → 学校 `loginByQr?appid=ywtb&cascallback=wxlogined`）→ **〔中间四环仓内零记录：码怎么生成/App 怎么拿/怎么感知完成/tgt 怎么下发，全待抓包〕** → 客户端自己拿到 `go-admin-tgt` + 同域 cookie → `POST /identity/login {method:"school", goAdminTgt, cookie}` → 后端换票取 13 组档案 → 返回 App 会话 token。

**文字版时序图**（`[确证]` / `[仓内未见]` / `[待实测]` 逐段标注，箭头不代表已验证的调用顺序，中段用 `◇` 表示缺口）：

```text
参与者： 用户/微信 │ 学校登录页(wui) │ auth.sicau.edu.cn │ 农屿 App │ 农屿后端

 ① 农屿 App            ── 打开登录页 / 取二维码 ──▶ 学校登录页(wui)        [仓内未见·承载形态待 #87]
 ② 学校登录页(wui)      ── 生成 QR(带一次性 state) ──▶ 用户/微信           [确证：payload 形态；生成接口仓内未见]
       QR = connect/oauth2/authorize?appid=wx7c220ea873c845e0
            &redirect_uri=.../api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined
            &response_type=code&scope=snsapi_userinfo&state=loginByQr:<一次性串>#wechat_redirect
 ③ 用户/微信            ── 扫码 + snsapi_userinfo 确认 ──▶ 微信授权服务器     [确证：形态；人工确认]
 ④ 微信授权服务器        ── 302 带 code + state ──▶ auth /api/v1/cas/loginByQr [确证：redirect_uri 指向它；
                                                                          请求/响应形状【仓内未见】]
 ⑤ auth 平台             ── cascallback=wxlogined 之后 …… ──▶ ◇◇◇ 缺口 ◇◇◇      [仓内未见 + 待实测]
        ◇ 是否 302 到某页面？是否 Set-Cookie 下发 go-admin-tgt？
        ◇ 还是只建 CAS 会话、需另走 GET /api/v1/cas/login?appid=<UUID> 换票？
        ◇ 页面侧靠什么感知扫码完成（轮询 state / WS / 页面跳转）？
 ⑥ 农屿 App            ── 感知"授权完成" ──▶ ◇◇◇ 缺口 ◇◇◇                [仓内未见 + 待实测]
        ◇ 轮询哪个端点？wxlogined 能否被 App 监听？WebView 能否 URL 拦截/JS bridge？
 ⑦ 农屿 App            ── 取得 go-admin-tgt + 整套同域 cookie ──▶ 本地      [确证：这是唯一硬要求；
                                                                          微信链路怎么拿到【待实测】]
 ⑧ 农屿 App            ── POST /identity/login ──▶ 农屿后端                 [确证：契约 §3.1 + NY1 签名]
        {method:"school", goAdminTgt, cookie, deviceId}
        注：schoolMethod / wechatCode 被 schoolauth_bridge.go 丢弃，填了也没用
 ⑨ 农屿后端             ── tgt 换票 / xsgz enterHall / 取 13 组档案 ──▶ DB    [确证：实测笔记已跑通]
 ⑩ 农屿后端             ── {token, expiresAt(Unix秒), isNew, user} ──▶ App   [确证：契约 §3.2 / IdentityDto]
 ⑪ 农屿 App            ── 写 MinimalSessionStore → 进主界面 ──▶ 用户        [确证：Q8 / Q21 决议]
```

---

## 7. 需要真机 / 抓包 / 校园网 / 学校平台官方文档才能定的项清单

按优先级，继承 `campus-sso-wechat-direct-auth.md:265-273` §5.3 并细化到本票四环：

1. **【最关键·环 1】学校登录页的二维码生成接口**：真机/浏览器打开 `auth.sicau.edu.cn/sso/login?appid=<教务 appid>&service=…` → `/wui/`，抓 Network 看是谁产出这张 QR（wui bundle 里拼的 URL？后端下发图片接口？），拿到「取 state / 开扫码事务」的端点与请求形状。`WebVPN探索.md:144` 那次 `only-auth` 实测用的是**门户 appid**（`:120`），不能代表教务 appid 或直连 auth，必须分别测（继承 §5.3 第 1 项）。
2. **【环 1】URL 是否每次变化**：连续刷新/开两次扫码事务，比对 `state` 与 `appid=ywtb` / `cascallback=wxlogined` 是否稳定；确认 state 的 TTL、一次性、与登录会话的绑定方式。
3. **【环 2】App 拿码形态**：确认是「学校服务端下发图片」「App 取 state 自拼 URL 用 QR 库」「WebView 加载登录页由页面渲染」中的哪一种；若走 WebView，确认学校登录页 URL 及 wui bundle 是否可被 App 内嵌（无直连/证书/WebVPN 改写问题）。注意 `WebVPN探索.md:221-232` 方案 B 只是旧 RN 备选，未验收。
4. **【环 3】完成感知机制**：抓一次完整扫码，按时间顺序看页面发出的所有请求——有没有「按 state 查状态」的轮询端点、WebSocket / 长轮询、或纯页面跳转；`cascallback=wxlogined` 是页面回调、前端路由标记还是可被 App 拦截的 URL；App 走 WebView 时能否通过 `shouldOverrideUrlLoading` / JS bridge 感知，或必须自己轮询。
5. **【环 4】微信成功后 tgt 的下发方式**：清空 cookie → 走完整 SSO → 用 DevTools Network 按时间顺序找**第一个带 `Set-Cookie: go-admin-tgt` 的响应**（方法参照 `实测-川农一次登录覆盖多站点的唯一解.md:403-405` 找 `main-app-access-token` 的做法）；确认 `cascallback=wxlogined` 后跳到哪、是直接下发 tgt 还是只建 CAS 会话需另换票、是否 HttpOnly、`cookie` 字段要带哪几枚同域 cookie 才能续期。
6. **【环 4】学校平台官方接口文档 / wui bundle**：确认 `/api/v1/cas/loginByQr` 的真实方法、参数、响应、`Set-Cookie` 语义；确认 `res.wx.qq.com` 的 `jweixin` 脚本在 wui 里到底被哪个功能调用（`WebVPN探索.md:68,96` 只证明被加载，未证明用于登录）；确认是否存在仓内没记录过的其它微信端点（继承 §5.3 第 3 项）。
7. **【环 1~4 前提】校园网/真机/学校账号**：`教务鉴权方案.md:330` 记「真机验收仍需校园网环境：教务系统已『仅限校园网内访问与绑定』」；本机无真机、无学校账号，本轮不具备实测条件（继承 §5.3 第 7 项）。
8. **【边界】「川农微教务」公众号链路**：`JiaowuLoginPanel.tsx:374-376` 只记「需先绑公众号」。它是公众号网页授权、带参二维码事件推送，还是仅账号绑定？与本码的 auth `loginByQr` 是否同一身份源？仓内无法定（继承 §5.3 第 4 项）。
9. **【终点·已确证但需回归验收】**：即便上面全查清，后端仍只认 `go-admin-tgt`（`schoolauth_bridge.go:28-47`）。若微信链路**不**直接下发 tgt，就必须新开规格把「微信凭证 → tgt」补进后端，**不能**靠现有 `wechatCode` 字段（它已被丢弃，`backend-plus-login-ui-facts.md:74,107,141`）（继承 §5.3 第 5 项）。
10. **【需维护者指认】**：Q12′ 里「学校 CAS 自带的微信登录方式」具体指哪个页面——教务首页扫码（`JiaowuLoginPanel.tsx:367`）还是 auth 登录页里的微信 Tab / 这张 `loginByQr` 码？仓内把两者写成并列入口（`教务鉴权方案.md:214`），#74 评论 8 第 5 点已区分，但指认错了后面所有实测目标都会偏（继承 §5.3 第 6 项）。

---

## 8. 分档汇总表

| 环 | 问题 | 【确证】 | 【仓内未见】 | 【待实测】 |
| --- | --- | --- | --- | --- |
| 1 | 码由哪个接口/页面生成、state 从哪来、URL 每次变吗 | payload 形态、端点存在、`appid`/`scope`/`state` 字面（#74 评论 8；§1） | 生成接口、二维码图片接口、wui bundle 代码、`loginByQr` 请求形状、state 下发接口（全仓 grep 零命中；§2.1） | URL 每次是否变化、state 生命周期（§2.2） |
| 2 | App 怎么拿到这张码 | 后端不提供登录页 URL / WebView 流程；旧 RN 微信格是外部浏览器引导面板（且对象不同） | 三种候选（服务端图片 / App 自拼 / WebView 渲染）全部零命中（§3） | 实际是哪种承载、能否内嵌 wui |
| 3 | 扫码后 App 怎么感知完成 | `cascallback` 在仓内只作页面侧 SSO 路由参数（4 处 `choose`）；旧 RN「自动登录」是账密 OCR 重试 | 轮询端点 / WebSocket / 长轮询 / 轮询 state / `wxlogined` 语义 / App 可监听回调（§4.1-4.2） | wui 页面感知机制、App 能否 URL 拦截 / JS bridge 感知 |
| 4 | 怎么拿到 `go-admin-tgt` + 同域 cookie | 账密链路 `POST /api/v1/cas/login` 响应 `Set-Cookie: go-admin-tgt`（`实测-川农一次登录覆盖多站点的唯一解.md:18-19`、`实测-川农CAS真实链路-tgt万能凭据.md:17`）；后端只收 tgt + 同域 cookie（`schoolauth_bridge.go:28-47`） | 微信链路是否也下发 tgt、`cascallback=wxlogined` 后跳到哪、是否需另换票（§5.2） | 第一个 `Set-Cookie` 是哪个响应、是否 HttpOnly、WebView 能否读出、要带哪几枚 cookie |
| 5 | 与 `POST /identity/login` 接起来 | 契约 §3.1 请求体 + NY1 签名 + 后端丢弃 `wechatCode`（§6.1）；客户端步骤清单 §6.2 | ——（这一环是设计约束，不是仓内材料） | 步骤 1/2/4/5 依赖环 1~4 的实测结果 |

---

## 9. 本轮明确没有做的事（避免把边界写成结论）

- 未访问 `auth.sicau.edu.cn` / `open.weixin.qq.com` / 教务首页 / 微信开放平台任何页面，**未抓包**，未触发任何真实授权。
- 未把「仓内 grep 零命中」写成「学校平台官方没有该接口」：`loginByQr` 端点已被二维码实证存在，恰恰是「仓内未见 ≠ 不存在」的又一次印证（同 `campus-sso-wechat-direct-auth.md:227,278`）。
- 未臆测 `/api/v1/cas/loginByQr` 的方法 / 参数 / 响应 / `Set-Cookie` 形状，未臆测轮询端点、轮询间隔、回调约定。
- 未把账密链路的 `Set-Cookie: go-admin-tgt` 外推成「微信成功后直接下发 tgt」；未把 `AP/docs/research/wechat-scan-direct-jump.md` 的开放平台「无轮询形态」结论搬来否定学校自建链路。
- 未推翻 `campus-sso-wechat-direct-auth.md` / `backend-plus-login-ui-facts.md` 任何既有结论，只补齐「形态」一格并把 §5.3 清单细化为四环待实测项。
- 只读核查：只新建本文一篇，未改任何既有文件、未 git commit；未记录任何真实凭据值（tgt / st / cookie 值 / 一次性 state 串均不复写）。
