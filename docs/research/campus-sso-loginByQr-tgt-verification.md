# loginByQr 回调后是否直接下发 go-admin-tgt：可复现实测方案 + 间接证据盘点

> **日期**：2026-11-12
> **起因**：`/wayfinder` 地图 #76《登录页（跟后端对接）：统一身份认证 / 平台登录的完整探索地图》的 research 子票 **#82「实测：loginByQr 回调后是否直接下发 go-admin-tgt」**（`wayfinder:research`，标题原文；票正文见 `deck_issue_get(82)`，票面把产出限定为「实测方案 + 待验证项」）。
> **⚠️ 本轮明确声明：未实测，只产出方案与间接证据。** 本轮**没有**真机 / 校园网 / 学校账号 / 抓包环境（`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:330` 记「真机验收仍需校园网环境：教务系统已『仅限校园网内访问与绑定』」）。因此本文**不是「实测结论」**，不回答「A 还是 B 成立」；只给出：① 仓内既有实测能/不能类推到 `loginByQr` 的间接证据盘点；② 区分形态 A/B 的可复现实测方案；③ 带脱敏口径的观察清单；④ 待验证项。**绝对不把推测写成结论**，不访问任何真实学校 / 微信 URL，不记录任何真实凭据值。
> **范围（只读核查）**：只新建本文一篇；未修改任何既有文件、未改代码、未 git commit、未访问网络、未抓包。

## 路径约定与确定程度

下文 `文件:行号` 的前缀（均为本机 real path）：

| 前缀 | 实际目录 |
| --- | --- |
| `AP/` | `apps/nongyu-app-plus/`（本工作区，本文所在 `docs/research/`） |
| `BE/` | `apps/nongyu-backend-plus/` |
| `DOCS/` | `docs/nongyu-rn-app/`（即任务里写的 `../../docs/nongyu-rn-app/`，旧 RN 版技术文档） |
| `TOOL/` | `packages/nongyu-tool-jiaowu/`（旧 CAS 工具包） |
| `ROOT/` | monorepo 根 `D:\IT\program\web-program\nongyu-system\` |

结论按两档、三个标签标注，全文不把「仓内没观测过」写成「官方不存在」，也不把「推测」写成「结论」：

- **【确证】**：仓内源码 / 实测笔记 / 规格原文可直接引用。
- **【间接证据】**：仓内已实测的真实行为，但**测的不是 `loginByQr` 本身**；只能用来界定「能 / 不能 / 部分类推」，不构成对 `loginByQr` 的结论。
- **【待实测】**：需真机 / 校园网 / 学校账号 / 微信内授权 / 抓包才能定，本轮无此环境。

> 前两档（【确证】/【间接证据】）合起来是「仓内已确立」，与【待实测】分档。

## 与既有笔记的关系（不推翻，只承接 / 深化）

| 既有笔记 | 关系 |
| --- | --- |
| `AP/docs/research/campus-sso-wechat-direct-auth.md` §4 / §5.3 | **本文直接承接其 §5.3 第 5 项**（「需要确认学校平台微信登录成功后是否会下发 `go-admin-tgt`；若不会，就必须新开规格把『微信凭证 → tgt』补进后端，不能靠现有 `wechatCode` 字段」）。其 §4.1 的 `/api/v1/*` 5 端点清单（`:209-221`）是本文的端点基线。**注意**：该篇 §4.2（`:225`）曾记 `loginByQr` 全仓 grep 零命中——这一「仓内未见」后来被地图讨论承载票 #74 第十一轮的二维码离线解码实证**推翻**（端点确实存在，形态 = 微信网页授权 `snsapi_userinfo`）；本文**不再重争「端点是否存在」**（已实证存在），只查「回调后的凭证形状」这一个未决问题。 |
| `AP/docs/research/backend-plus-login-ui-facts.md` | **补充，不推翻**。其 §0（`:27-31`）、§1（`:70-80,107-108`）、§2.4（`:210-215`）确证：农屿后端 school 分支只有一条真实通路 `go-admin-tgt`、`wechatCode` 被桥接层丢弃、后端无 CAS 登录页 URL、「App 从哪里取得 tgt」列为待核实。本文所有「对 T7 的影响」都以这些确证为落点。 |
| `BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md` | 间接证据主源之一（账密登录成功的 `Set-Cookie: go-admin-tgt` 记录）。 |
| `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md` | 间接证据主源之一（账密取 tgt、`/api/v1/cas/login?appid=<UUID>` 换票、appid 两种形态、`:472` 手机 App 只需交一张 tgt）。 |
| `BE/docs/tech/实测-门户appid免密换票-已证实.md` | 间接证据主源之一，且是**关键的反向约束**（单枚 tgt 不够、需整套同域 cookie、不跨 appid 外推），并作为本文「脱敏口径」的模板（`:3,38-42`）。 |
| `DOCS/nongyu-rn-app/tech/WebVPN探索.md` | `cascallback=choose` 的观测处（`:20,267,325`）、cookie 罐 / HttpOnly 坑（`:295-304,330`）、校园网与 ToS 边界（`:327,334`）。 |
| `AP/docs/后端接口与NY1签名对接契约.md` | 权威契约（唯一真源）：§3.1（`:145-154`）`POST /identity/login {method:"school", goAdminTgt}`、§4（`:193-203`）后端持 tgt 取数链路。 |
| 地图 #76 / #74 讨论承载票 | T7 = 子票 **#88**「微信格实现：网页授权扫码 → loginByQr → goAdminTgt → 农屿后端」（被 #82、#87 阻塞）；Q21 = 第八轮「6 格映射表：统一身份认证三格共用 `method=school` + `goAdminTgt`」；二维码实证见 #74 第十一轮评论。这些是票面事实（`deck_map_snapshot(76)`、`deck_issue_get(74, comments=9)`），非源码。 |

**题面给定的两个硬前提（本文不重复论证，只在其上推演）**：

1. 二维码解出 `https://auth.sicau.edu.cn/api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined` 作为微信网页授权的 `redirect_uri`（#74 第十一轮离线解码实证；本文不重放含 `state` 的完整 authorize URL，见 §3 脱敏口径）。
2. 农屿后端只认 `go-admin-tgt`（`BE/internal/app/schoolauth_bridge.go:28-47` 丢弃 `wechatCode`），且 `POST /identity/login {method:"school", goAdminTgt, cookie}` 是唯一通路（`AP/docs/后端接口与NY1签名对接契约.md:145-154`；`AP/docs/research/backend-plus-login-ui-facts.md:84-108`）。

---

## 1. 间接证据盘点：仓内所有已实测过的「登录成功 → go-admin-tgt」链路

### 1.1 链路清单（逐条给 文件:行号，均标注「测的是什么」）

| # | 已实测链路 | 请求 / 响应 | 与 tgt 的关系 | 一手来源（文件:行号） |
| --- | --- | --- | --- | --- |
| E1 | **账密 + 验证码登录成功 → 直接下发 tgt**【确证】 | `POST https://auth.sicau.edu.cn/api/v1/cas/login`，JSON `{appid, code, number, password, uuid}` → `{"code":200,"msg":"登录成功"}` → **响应 `Set-Cookie: go-admin-tgt=...`** | **产出 tgt**（auth 域会话 cookie） | `BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:16-17`（step 3 登录成功 / step 4 `Set-Cookie: go-admin-tgt`，记 57 字符）；`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:18-19`（「账密 + 验证码登录 … 响应 `Set-Cookie` 下发 `go-admin-tgt`（形如 `201690:TGT:xxx`）」）；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:230-232`（契约）；`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:322-324`（curl 形状）；`TOOL/TECH.md:27`、`TOOL/core/login/cas.ts:11,351,535,759`；`AP/docs/research/campus-sso-wechat-direct-auth.md:214`（端点清单转录） |
| E2 | **带 tgt 换票（任意已注册 UUID appid）**【确证】 | `GET https://auth.sicau.edu.cn/api/v1/cas/login?appid=<站点UUID>`，`Cookie: go-admin-tgt=<tgt>` → `{"code":200,...}` → 解密 `data` 得 `{callbackUrl, tgt, st, user, users, uuid}` | **消费 tgt → 换 st**（不产出新登录态，是先有 tgt 才能打） | `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:24-27,42-46`（教务 / 门户 appid 均 `code=200`；**随机 UUID → `code=500 未登录`**，说明 appid 必须已注册）；`BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:18-19`；`AP/docs/research/backend-plus-login-ui-facts.md:181,404`；`AP/docs/research/campus-sso-wechat-direct-auth.md:215` |
| E3 | **后端持 tgt 自取的完整取数链**【确证】 | `tgt → auth /api/v1/cas/login?appid=ywtb（换 st+userId） → xsgz cas/login → checkUserAndGetUserLevel → enterHall → student/detail` | **消费 tgt**，后端全权 | `AP/docs/后端接口与NY1签名对接契约.md:193-203`；`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:422-444`（端到端实测，`:472`「手机 App（原生、无 WebView）只需交一张 `go-admin-tgt`」）；`BE/internal/school/cas/exchange.go:158-168`、`BE/internal/school/xsgz/fetch.go:39-43`（后端出站常量）；`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:459-477`（落地代码 + 守卫测试） |
| E4 | **门户 appid 免密换票：单枚 tgt 不够，需整套同域 cookie**【确证 / 关键反向约束】 | `GET /api/v1/cas/login?appid=aea49a21-…(门户appid)`：只带单枚 `go-admin-tgt` → `code=500, 未登录`；带**真实浏览器会话整套同域 cookie** → `code=200, 登录成功` | **同样是消费 tgt**，但揭示「登录态 = 整套 cookie」而非单枚 tgt | `BE/docs/tech/实测-门户appid免密换票-已证实.md:30-32`（事实 1）；同篇 `:5-13,21-27`（判据与结果）、`:49-53`（未验证长期可用、**不跨 appid 外推**）；`BE/internal/school/schoolauth/tgt.go:108-116`（`TGTBundle` 注释：「实测换票必须带整套同域 cookie … 续期需 laravel_session / XSRF-TOKEN」）；`AP/docs/research/backend-plus-login-ui-facts.md:119-121,399` |
| E5 | **`appid=ywtb` 是名字字符串，`/api/v1/cas/login?appid=<UUID>` 必须 UUID**【确证】 | `/sso/login?appid=ywtb`（名字，前端 SSO 路由）vs `/api/v1/cas/login?appid=<UUID>`（已注册 UUID，后端换票）；8 个站名带 tgt 返回 `cascallback=choose` | 界定 `loginByQr?appid=ywtb` 里 `ywtb` 属哪一套 | `BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:46,48-53`；`AP/docs/research/campus-sso-wechat-direct-auth.md:123`；`DOCS/nongyu-rn-app/tech/WebVPN探索.md:20,267,325`（`cascallback=choose` 观测） |
| E6 | **农屿后端只接收 tgt、丢弃 wechatCode**【确证】 | `POST /identity/login {method:school, goAdminTgt, cookie}`；`schoolauth_bridge.go` 只搬运 `RawCookie/UserID/XSGZToken/GoAdminTgt`，`Method/WechatCode/Phone/Code/Password` 全未赋值 | 后端侧「凭证形状只有一种」 | `BE/internal/app/schoolauth_bridge.go:28-47`（`:29` 注释「凭据形态只有一种：客户端完成学校登录后交来的 go-admin-tgt」）；`AP/docs/research/backend-plus-login-ui-facts.md:54-60,74,107,141,150,157-172`；`AP/docs/research/campus-sso-wechat-direct-auth.md:126-133,271` |

### 1.2 这些能否类推到 `loginByQr` 回调？（能 / 不能 / 部分，给理由）

**总判定：部分（而且只能类推「拿到 tgt 之后」的半段，不能类推「回调直接下发 tgt」这个待决问题本身）。** 逐条：

- **能类推（有条件）**：E3 / E6 与「客户端怎么拿到 tgt」**无关**——它们确证「**一旦**客户端交来 `go-admin-tgt`（及整套同域 cookie），后端就能换票取数、且不认别的凭证」。所以**无论形态 A 还是 B，最终都要落到 `goAdminTgt` 这一种形状**；后端通路、`method=school` 映射、Q21 框架不因 tgt 的取得方式而变（`AP/docs/后端接口与NY1签名对接契约.md:145-154`、`AP/docs/research/backend-plus-login-ui-facts.md:27-31`）。`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:472` 进一步把这层钉死：「手机 App（原生、无 WebView）只需交一张 `go-admin-tgt`」。

- **不能类推**：E1 是**账密认证成功**（`number/password/code/uuid`）触发的 `Set-Cookie: go-admin-tgt`；`loginByQr` 是**微信网页授权 `code`** 作为输入的**不同端点、不同认证前端**（#74 第十一轮实证：`open.weixin.qq.com/connect/oauth2/authorize`、`scope=snsapi_userinfo`、`state=loginByQr:…`、`#wechat_redirect`）。仓内**没有任何一次实测**观测过 `loginByQr` 的响应头 / 响应体（`AP/docs/research/campus-sso-wechat-direct-auth.md:209-221` 的 5 端点清单无它、`:225` grep 零命中；#74 第十一轮只证明「码里写了这个 URL」「端点存在」，**未观测回调响应**）。所以「账密登录会 Set-Cookie tgt」**不能**推出「微信回调也会 Set-Cookie tgt」——这正是本票要实测的那个未知量，不能拿 E1 当答案。

- **部分 / 且构成反向约束**：
  1. E2 是 **tgt → st 换票**，**先要有 tgt 才能打**（随机 UUID/无登录态 → `code=500 未登录`，`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:45`、`BE/docs/tech/实测-门户appid免密换票-已证实.md:31`）。它**不能**把「微信 code」变成 tgt；若形态 B 下「还需另一次换票」，而换票又要求先有 tgt，就会形成「要 tgt 才能换 tgt」的死循环 → 这正是票 #82 所说「需新开规格补『微信凭证 → tgt』」的根由。
  2. E4 揭示**「登录态」在服务端眼里是整套同域 cookie，不是单枚 tgt**（单枚 tgt 打门户 appid 换票返回 `code=500 未登录`）。所以即便 `loginByQr` 回调下发了 `go-admin-tgt`，「形态 A」要求的『**及整套同域 cookie**』仍要单独验证，且 `实测-门户appid免密换票-已证实.md:53` 明写「本结论只覆盖门户 appid，不跨 appid 外推」——不能把门户 appid 的结论直接搬到 `appid=ywtb` 的 `loginByQr`。
  3. E5 提示 `loginByQr?appid=ywtb` 里的 `ywtb` 是**名字字符串**（前端 SSO 路由形态），而后端换票用的是已注册 UUID（`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:48-53`）。`cascallback=choose` 在 WebVPN 实测里代表「已识别登录态」（`DOCS/nongyu-rn-app/tech/WebVPN探索.md:20,267,325`），但 `cascallback=wxlogined` 是另一个值、语义未观测 → 【待实测】。

- **一条来自「App 可取性」的额外限制（不属 A/B 分界，但影响 A 的「客户端可直接交」）**：`DOCS/nongyu-rn-app/tech/WebVPN探索.md:295-304,330` 反复记到 HttpOnly / 同域 / 原生 RN 读不到 `Set-Cookie`、需 WebView 或 cookie 桥的坑（`:295`「HttpOnly，`cookieStore` 和原生 `document.cookie` 都看不到」、`:304`「ticket 是 HttpOnly，JS 读不走」、`:330`「RN Cookie 罐 ⚠ HttpOnly + 跨域」）。所以「服务端**下发** tgt」与「App **能取到** tgt」是两层，前者成立不代表后者成立——这一层也必须实测，见 §3、§4 第 5 项。

**结论（间接证据盘点）**：仓内所有「登录成功 → tgt」的实测都发生在**账密登录**（E1）或**已有 tgt 之后的换票**（E2/E3/E4）；**没有一条观测过微信 `code` 回调 `loginByQr` 后的响应形状**。因此这些证据足以说明「tgt 拿到后怎么用」「后端不认别的形状」「整套 cookie 与 appid 边界」，**不足以回答 `loginByQr` 回调是否直接下发 tgt**——该问题只能靠 §3 的实测定论（【待实测】）。

---

## 2. 形态 A / B 的判据、区分步骤与对 T7 的影响

### 2.1 两条形态（票 #82 原文口径）

- **形态 A**：微信带 `code` 回调 `loginByQr` 后，学校平台**直接**下发 `go-admin-tgt`（及整套同域 cookie）→ 客户端可直接交农屿后端 `method=school`（`AP/docs/后端接口与NY1签名对接契约.md:145-154`）。
- **形态 B**：只完成 CAS 会话 / 只回 `wxlogined` 页面，仍需另一次换票，或根本不下发 tgt → 需新开规格补「微信凭证 → tgt」，且**不能靠已被丢弃的 `wechatCode`**（`BE/internal/app/schoolauth_bridge.go:28-47`、`AP/docs/research/backend-plus-login-ui-facts.md:74,107,141,150`）。

> 两形态的真正分界不是「学校平台有没有建 CAS 会话」，而是：**`loginByQr` 回调这一跳的响应，有没有把 `go-admin-tgt`（及整套同域 cookie）直接 `Set-Cookie` 给客户端、且客户端能不能交得出去**。这一句必须靠抓包定，不能靠 CAS「登录成功即建 tgt」的常识推（§1.2）。

### 2.2 怎么实测区分 A/B（抓哪个请求、看哪个响应头/字段、判断依据）

**核心抓包对象**：微信内置浏览器里，用户扫码/确认后微信把控制权交回 `redirect_uri` 的那一次请求——即带 `code` 与 `state` 的 `loginByQr` 回调，以及它后续的 302 跳转链。

| 步 | 抓哪个请求 | 看哪个响应头 / 字段 | 判断依据 |
| --- | --- | --- | --- |
| S1 | `GET https://open.weixin.qq.com/connect/oauth2/authorize?...&redirect_uri=<encoded loginByQr>&response_type=code&scope=snsapi_userinfo&state=loginByQr:<一次性串>#wechat_redirect`（微信内置浏览器发起，#74 第十一轮实证形态） | 是否 302 回 `redirect_uri`，回调 query 里有没有 `code=<微信code>&state=loginByQr:<同一串>`；`state` 与发起时是否一致 | 确认微信网页授权这一跳走通、`code` 确实回带到学校侧；`state` 一致性是防 CSRF 判据（§4 第 3 项）。 |
| S2（**核心**） | `GET/POST https://auth.sicau.edu.cn/api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined&code=<微信code>&state=loginByQr:<串>` | ① **响应头 `Set-Cookie`**：有没有 `go-admin-tgt`？有没有 `laravel_session` / `XSRF-TOKEN` 等整套同域 cookie 名？各自 `Domain` / `HttpOnly` / `Secure` / `SameSite`？② 状态码：200 页面还是 302；③ 若 302，`Location` 跳到哪（路径形状、`wxlogined` 是页面还是状态、是否带 `ticket` / `st` / `userId`）；④ 响应体 / 加密 `data` 解密后有哪些**字段名**（是否含 `callbackUrl` / `st` / `tgt` / `user` / `users`） | **A 成立**：S2（或其 302 链里 auth 域的某个响应）的 `Set-Cookie` **含 `go-admin-tgt`**，且该 cookie 能在后续 E2 换票请求里被接受（`code=200`）。**B 成立**：S2 响应**无** `go-admin-tgt` 的 `Set-Cookie`，只回 `wxlogined` 页面 / 只建 CAS 会话 cookie / 只是前端跳转；要拿 tgt 还得另走别的登录-换票（而换票又要先有 tgt，见 §1.2 第 2 点死循环），或根本不下发。**中间态**：下发了 tgt 但只有单枚、缺整套同域 cookie → 形态 A 的「及整套同域 cookie」条件不满足（E4 约束），只算部分可用，按 B 的处置把「整套 cookie」列为待补。 |
| S3（验证 A 的「可直接交」） | 用 S2 拿到的 cookie 打 `GET https://auth.sicau.edu.cn/api/v1/cas/login?appid=<已注册UUID>`（E2 形状），或直接按 `POST /identity/login {method:school, goAdminTgt, cookie}` 交农屿后端 | 前者 `code` 是否 200、解密得 `st`；后者是否 200 拿到农屿 token / 401 `bad_credentials` | 把「服务端下发了 tgt」与「tgt 真的能被后端消费」连起来；单枚 tgt 打门户 appid 可能 `code=500 未登录`（E4），所以必须用**整套 cookie** 复测。 |
| S4（**App 可取性，A 的第二层**） | 在目标客户端形态（App 内 WebView / 系统浏览器 / 自定义 Tab）里重放 S1–S3 | App 侧能否读到 `go-admin-tgt` 与同域 cookie 的值 / 能否组出 `cookie` 头；`HttpOnly` 是否挡住读取 | 即便浏览器收到 `Set-Cookie`，App 读不到就等于形态 A 的「客户端可直接交」不成立（`DOCS/nongyu-rn-app/tech/WebVPN探索.md:295-304,330`）。按 `BE/internal/app/schoolauth_bridge.go:39-43`、`AP/docs/research/backend-plus-login-ui-facts.md:112-121`：客户端应始终填 `goAdminTgt` 字段，`cookie` 作为整套同域 cookie 补充传入。 |

**判据一句话**：S2 响应的 `Set-Cookie` 里有没有 `go-admin-tgt`（且 S3 能换票成功、S4 App 能取到）→ 有且三者皆通 = A，否则 = B 或 A 的部分态。只看「`wxlogined` 页面出现」不算数（B 也会出现该页面）。

### 2.3 各自对 T7（#88）的影响

> T7 = 地图 #76 子票 **#88「微信格实现：网页授权扫码 → loginByQr → goAdminTgt → 农屿后端」**，当前 `blockedBy: [#82, #87]`（`deck_map_snapshot(76)`）。#82 的结论是 #88 解除阻塞的前置之一；另一前置 #87「微信格 UI 承载形态（App 内贴码 / 系统浏览器 / 直跳微信）」独立阻塞。

- **若判为形态 A**：
  - #88 可直接按 Q21 既有映射落地：统一身份认证三格共用 `method=school` + `goAdminTgt`（#74 第八轮 Q21、第十一轮第 4 点「Q21 映射表框架不变，微信格只是客户端侧取 tgt 的方式不同」）。后端**零改动**（`schoolauth_bridge.go:28-47` 的 tgt 通路、`AP/docs/后端接口与NY1签名对接契约.md:145-154` 契约不动）。
  - #88 的工作量收窄为：① 二维码/授权入口怎么承载与 state 怎么感知（与 #87、#84 协作）；② 按 §2.2 S4 把微信 WebView 里的 `go-admin-tgt` + 整套同域 cookie 读出来，填 `goAdminTgt` 字段 + `cookie` 头（`AP/docs/research/backend-plus-login-ui-facts.md:119-121`）。
  - #82 关闭后 #88 仍受 #87 阻塞，但不再需要新规格。地图 fog 里「学校 CAS 微信登录是否需要『微信凭证 → tgt』后端补链路」这条（见 `deck_map_snapshot(76)` blocks.fog 第 4 条）可据 A 结论关闭。

- **若判为形态 B（含 A 的部分态）**：
  - **触发地图 fog 第 4 条毕业成新规格**：在农屿后端补一条「微信凭证 → `go-admin-tgt`」链路（或等价地把微信 code 换成学校登录态）。**且不能复用 `wechatCode` 字段**——它已被 `BE/internal/app/schoolauth_bridge.go:28-47` 丢弃、源码中仅单测构造过（`AP/docs/research/backend-plus-login-ui-facts.md:74,141,150`）；`AP/docs/research/campus-sso-wechat-direct-auth.md:271` §5.3 第 5 项与此完全一致。
  - Q21「三格共用 `method=school` + `goAdminTgt`」对微信格**不再直接成立**：要么新规格产出的仍是 tgt（则 #88 复用同一通路但多一个后端依赖），要么引入新的凭证形状（则 `schoolauth_bridge.go` / `schoolauth.Credential` / `POST /identity/login` 契约都要扩），#88 的范围、依赖边、验收判据都要重排，并可能与 #87 的 UI 形态结论互相牵动。
  - 这是 wayfinder 的「决策」产物，不是本票要执行的实现：按 /wayfinder 规则，#82 的 resolution 应把「需新开规格补微信凭证 → tgt」作为决策写进 #76 的 Decisions so far / 毕业 fog，由维护者手动 `/to-spec` 承接，本票不产规格、不产实施票。

---

## 3. 可复现实测方案（本轮**不执行**，只给方案）

### 3.1 需要的环境

| 项 | 要求 | 依据 |
| --- | --- | --- |
| 网络 | **校园网内**（或已连 WebVPN 的等效校内环境） | `DOCS/nongyu-rn-app/tech/教务鉴权方案.md:330`「仅限校园网内访问与绑定」；`DOCS/nongyu-rn-app/tech/WebVPN探索.md:327,334`（ToS / 频率风控） |
| 账号 | **本人学校账号**；账号仅限本人、避免连续批量操作触发风控 | `DOCS/nongyu-rn-app/tech/WebVPN探索.md:327,334` |
| 微信 | 一部已登录微信、能完成**学校 auth 平台 `loginByQr` 网页授权**（`snsapi_userinfo` 用户确认）的手机；授权在**微信内置浏览器**里发生（QR 带 `#wechat_redirect`） | #74 第十一轮（QR 解码：`open.weixin.qq.com/connect/oauth2/authorize`、`scope=snsapi_userinfo`、`#wechat_redirect`）；`AP/docs/research/wechat-scan-direct-jump.md:136-142`（网页授权 `redirect_uri?code=CODE&state=STATE` 形态） |
| **⚠️ 区分两个「微信」** | **① auth 平台自己的 `loginByQr`**：学校侧公众号/网页授权 appid `wx7c220ea873c845e0`（#74 第十一轮实证，仓内此前未记录）；**②「川农微教务」公众号**：教务**首页**扫码入口要求的账号绑定（`DOCS/nongyu-rn-app/tech/教务鉴权方案.md:408-417`、`OLD/src/modules/jiaowu/components/JiaowuLoginPanel.tsx:374-376`）。两者是**不同入口**，#74 第十一轮第 5 点已明确区别。**本票测的是 ①**，不要把「绑了川农微教务」当成 `loginByQr` 的前提；但「学校是否把 ①/② 打通 / ① 是否要求关注某公众号」属【待实测】 | `AP/docs/research/campus-sso-wechat-direct-auth.md:57-76,80-87`；#74 第十一轮评论第 5 点 |
| 抓包工具 | Charles / Fiddler / mitmproxy（需装 HTTPS 根证书以解 TLS），或桌面端 DevTools Network；能记录完整 **请求 query / 响应头 `Set-Cookie` / 302 链 / 响应体字段名** | `BE/docs/tech/实测-门户appid免密换票-已证实.md:36`（本机 curl TLS 可能失败，改用浏览器内取 JSON、内存解密、全程不落盘） |
| 二维码 | 学校平台当前有效的一次性二维码（含服务端生成的 `state`）；**不要用仓内 #74 那张历史解码出的 `state`**（一次性、可能已过期/已消费） | #74 第十一轮；§4 第 1、3 项 |

### 3.2 观察点（按 §2.2 的 S1–S4）

1. 微信授权发起与回跳：`code` / `state` 是否回带到 `loginByQr`。
2. **`loginByQr` 回调响应**：`Set-Cookie` 的 cookie **名字清单**（尤其 `go-admin-tgt`、`laravel_session`、`XSRF-TOKEN`）、状态码、`Location`（若 302）、响应体字段名（是否 `callbackUrl/st/tgt/user/users`）——**这是 A/B 的唯一决定性观察点**。
3. 用该套 cookie 打 E2 换票端点是否 `code=200` 解出 `st`（验证 tgt 可用）。
4. 目标客户端形态下 App 能否读出 `go-admin-tgt` 与同域 cookie（`HttpOnly` 影响），能否填进 `goAdminTgt` + `cookie`。
5. 顺带记录 `cascallback=wxlogined` 之后落到哪个页面 / 路径（§4 第 2 项）。

### 3.3 脱敏口径：**只把调用结果告诉 agent，不传任何凭据值**

参照 `BE/docs/tech/实测-门户appid免密换票-已证实.md:3,38-42` 的做法（「不记录任何凭据值」、`st`/`tgt` 一律 `<REDACTED>`、「只核对判据，不要记录或提交解密出的 `st`/`tgt`」、全程内存操作不落盘）。人（在校园网真机上自行操作）回报给 agent 的**只能是下列「是/否 + 名称/形状」**，**绝不包含任何值**：

**允许回传**：
- 端点是否存在、HTTP 状态码、业务 `code` 字段的值（如 `200` / `500` / `登录成功` / `未登录`）。
- `Set-Cookie` 里**有哪些 cookie 名字**（如「含 `go-admin-tgt`」「含 `laravel_session`」「含 3 枚」），以及每枚的 `Domain` / `HttpOnly` / `Secure` / `SameSite` 属性——**只报名字与属性，不报值**。
- 302 `Location` 的**路径形状 / 主机名 / 参数名**（如「跳到 `/xxx/wxlogined`，带 `ticket` 参数」）——**参数值一律略**。
- 解密 `data` 后**有哪些字段名**（如「含 `st`/`tgt`/`user`」）——**不传字段值**。
- 是否 401 / `bad_credentials` 等结果码。

**一律 `<REDACTED>`、不录入、不提交、不进本文**：
- `go-admin-tgt` 的值、`st` / `ticket` 的值、微信 `code`、`state` 一次性串、任何 cookie 的值、完整 authorize URL（含原 `state`）、学号 / 姓名 / 手机号 / 身份证 / 任何身份字段。
- 不在 agent 侧访问任何真实学校 / 微信 URL；授权、扫码、抓包全部由人在真机完成，agent 只接收脱敏后的判断材料。

> 本文自身也遵守该口径：§1 只写「`Set-Cookie: go-admin-tgt`」这类**名称**（引用既有实测笔记的写法，那些笔记本身已脱敏，如 `BE/docs/tech/实测-川农CAS真实链路-tgt万能凭据.md:4`「本文件不含任何凭据值」、`BE/docs/tech/实测-门户appid免密换票-已证实.md:3`），未复现任何真实凭据值。

---

## 4. 待验证项清单

> 全部标 **【待实测】**；本轮无真机 / 校园网 / 学校账号 / 抓包，逐项都不能定论。共 **5 项**。

| # | 待验证项 | 为什么要测 / 判据 | 关联依据 |
| --- | --- | --- | --- |
| V1 | **端点是否稳定**：`/api/v1/cas/loginByQr?appid=ywtb&cascallback=wxlogined` 当前是否可用、是否长期可用；它是仓内 5 端点清单之外的第 6 个端点；`appid=ywtb` 用**名字字符串**是否被该端点接受（换票端点必须 UUID，见 E5） | #74 第十一轮只证明「二维码里写了这个 URL」（端点存在实证），**未证明当前可用 / 未观测响应**；仓内 `/api/v1/*` 清单无它、全仓 grep 零命中 | `AP/docs/research/campus-sso-wechat-direct-auth.md:209-221,225`；`BE/docs/tech/实测-川农一次登录覆盖多站点的唯一解.md:48-53`；`BE/docs/tech/实测-门户appid免密换票-已证实.md:49-53`（长期可用/限流未验证、不跨 appid 外推） |
| V2 | **`cascallback=wxlogined` 之后跳转到哪**：`wxlogined` 是页面名还是状态标志；回调响应是 200 页面还是 302；302 落到哪个路径、带不带 `ticket`/`st`/`userId`；与 `cascallback=choose`（=已识别登录态）语义是否同族 | A/B 的分界就在这一跳的响应；只看「出现 wxlogined 页面」不能区分（B 也会出现） | `DOCS/nongyu-rn-app/tech/WebVPN探索.md:20,267,325`（`cascallback=choose` 观测）；#74 第十一轮（QR payload） |
| V3 | **`state` 过期与一次性**：`loginByQr:` + 44 字符一次性事务串（服务端生成、防 CSRF）的有效期、是否一次性、重放 / 过期 / 缺失时返回什么；App 侧如何拿到并回带 `state` | 决定二维码/事务怎么生成与感知、能否重扫、回调如何配对 | #74 第十一轮（`state=loginByQr:` + 44 字符，服务端生成、防 CSRF）；`AP/docs/research/wechat-scan-direct-jump.md:137,161`（网页授权 `redirect_uri?code=CODE&state=STATE`）；地图 #76 子票 #84「二维码生成 / state 下发 / 回调感知」 |
| V4 | **是否需整套 cookie（`laravel_session` 等）**：`loginByQr` 回调下发的是**单枚 `go-admin-tgt`** 还是**整套同域 cookie**；单枚够不够农屿后端换票 / 续期；`appid=ywtb` 是否也吃「单枚 tgt → `code=500 未登录`」这一约束 | E4 已实测「门户 appid 单枚 tgt 不够、需整套 cookie」，但该篇明写不跨 appid 外推；`AP/docs/research/backend-plus-login-ui-facts.md:119-121` 要求客户端 `goAdminTgt` + `cookie` 一并传 | `BE/docs/tech/实测-门户appid免密换票-已证实.md:30-32,53`；`BE/internal/school/schoolauth/tgt.go:108-116`；`AP/docs/research/backend-plus-login-ui-facts.md:112-121,399`；`AP/docs/后端接口与NY1签名对接契约.md:151` |
| V5 | **App 侧如何取到 tgt 与同域 cookie**：`go-admin-tgt` 是否 `HttpOnly`、原生 App / WebView 能否读出值与整套 `cookie` 头、要不要 cookie 桥；走 App 内 WebView 还是系统浏览器，回调怎么把控制权交回 App | 「服务端下发 tgt」≠「App 能交出去」；这一层不通，形态 A 的「客户端可直接交」仍不成立 | `DOCS/nongyu-rn-app/tech/WebVPN探索.md:295-304,330`（HttpOnly / RN Cookie 罐 / 直连读不到 Set-Cookie）；`AP/docs/research/backend-plus-login-ui-facts.md:210-215`（「App 从哪里取得 tgt / 能否用 WebView」源码未见、待核实）；`BE/internal/app/schoolauth_bridge.go:39-43`；`BE/internal/school/schoolauth/adapter.go:27-34` |

---

## 5. 本轮明确**没有**做的事（避免把边界写成结论）

- 未访问 `auth.sicau.edu.cn` / `open.weixin.qq.com` / `jiaowu.sicau.edu.cn` 或任何真实学校 / 微信授权 URL；未抓包、未扫码、未做微信授权。
- 未把「仓内已实测的账密登录 → `Set-Cookie: go-admin-tgt`」类推成「`loginByQr` 回调也直接下发 tgt」（§1.2）；未把「端点已实证存在」（#74 第十一轮）写成「回调凭证形状已定」。
- 未对形态 A / B 下结论、未给出倾向性实测判定；未把推测写成结论。
- 未记录任何真实凭据值（tgt / st / code / state / cookie 值 / 身份字段），未复现含 `state` 的完整 authorize URL。
- 未修改任何既有文件、未改代码、未 git commit；按只读核查只新建本文一篇。

> **给票 #82 的 resolution 口径建议**（/wayfinder「research 子票的产出是决策材料，不是实施」）：本票**不能**给出「A 或 B」的实测结论，只能把「可复现实测方案（§3）+ 间接证据盘点（§1）+ A/B 判据与 T7 影响（§2）+ 5 项待验证（§4）」作为答案；据此建议维护者把 #82 标为「待真机实测阻塞、已产出方案」，并让地图 #76 的 fog 第 4 条（「微信凭证 → tgt 后端补链路」）**保持未毕业**——它在 A/B 定论前无法拍板。A/B 一旦实测有果，再按 §2.3 决定是关闭 fog（A）还是毕业成新规格（B）。
