# nongyu-backend-plus 登录相关实现层事实（2026-11-12）

> **日期**：2026-11-12
> **起因**：`/grill-with-docs` 讨论登录页 UI，需要几个权威快照没写清的实现层事实，
> 用于判断登录页到底要渲染哪些输入项、能不能用 WebView 拿 `goAdminTgt`、
> 以及 token 失效后客户端该打哪个端点。
> **与 `docs/后端接口与NY1签名对接契约.md`（2026-11-12）的关系**：**补充，不推翻**。
> 该文档是接口契约与 NY1 签名的唯一权威快照；本文只读 `nongyu-backend-plus` 的
> 源码，回答它没写清的五个实现层问题。契约里的路由清单、签名规则、会话语义、
> 取数链路均继续有效。若本文与契约措辞有出入，在文末「与权威文档的对照」逐条列出，
> 均为「细化 / 澄清」，不是推翻。
> **范围**：只新建本文；未修改后端任何文件、未修改 app-plus 既有文件、未 git commit。

## 路径约定

下文 `文件:行号` 中的文件均相对后端源码根目录
`D:\IT\program\web-program\nongyu-system\apps\nongyu-backend-plus`，记作 **BE**。
例如 `internal/identity/login/service.go:326`。

结论按 **【确证】**（源码直接给出）/ **【源码未见】**（全仓 grep 无命中）/
**【待核实】**（源码只给到一半，需实测或问维护者）分开写。

---

## 0. 一句话总览（给登录页 UI）

**`school` 方式在后端只有一条真实通路：交一张 `go-admin-tgt`（及同域 cookie）。**
`schoolMethod` 的 `password` / `phone` / `wechat` 三个取值**没有任何学校侧认证分支**，
`wechatCode`、`phone`、`code`、`studentNo`、`password` 在 school 分支里全部被桥接层丢掉
（只有 `password` 顺带触发一次「农屿账密镜像」）。后端不提供「学校 CAS 统一身份认证
登录页」给 WebView 打开，`goAdminTgt` 完全由 App 自己从别处取得、后端只接收。
登录页 UI 若渲染「微信 / 短信 / 账密」三种 schoolMethod 单选项，需要知道它们当前
**不会改变后端行为**。

---

## 1. `LoginBySchool`、`schoolMethod` 与字段消费

### 1.1 【确证】`password` / `phone` / `wechat` 各自的实现情况

`LoginBySchool` 位于 `internal/identity/login/service.go:260-345`。它对 `cred.Method`
**只有一处**消费：

- `internal/identity/login/service.go:326`：
  `if cred.Method == "password" && cred.Password != "" && s.passwordBox != nil` →
  调 `s.mirrorPassword(...)`（`service.go:495-516`），把学校密码加密写进
  `login_methods` 的 `password` 条目的 `secret_sealed`，即「农屿账密镜像」。
  `service.go:324` 注释明确写「其它学校方式（微信/短信）不写」。

学校认证本身的调用只有一行，且**不按 Method 分流**：

- `internal/identity/login/service.go:264`：`ident, err := s.school.Authenticate(ctx, cred)`
- 生产装配把 `s.school` 注入为 `schoolauthBridge`（`internal/app/app.go:433`、
  `app.go:322-326`）。桥接实现 `internal/app/schoolauth_bridge.go:28-64` 把
  `login.SchoolCredential` 翻译成 `schoolauth.Credential` 时**只搬运 4 个字段**：
  `RawCookie = in.CookieHeader`（`:33`）、`UserID`（`:34`）、`XSGZToken`（`:36`）、
  以及 `GoAdminTgt` 转成一枚名为 `go-admin-tgt` 的 cookie（`:39-43`）。
  `Method` / `StudentNo` / `Password` / `Phone` / `Code` / `WechatCode` **全部没有
  出现在桥接的赋值里**，即被丢弃，根本到不了学校出站适配器
  `internal/school/schoolauth/*`。
- `schoolauth.Credential` 的字段定义也只有 4 个：`internal/school/schoolauth/adapter.go:35-46`
  （`Cookies` / `RawCookie` / `XSGZToken` / `UserID`），注释 `:29-31` 明写
  「客户端先用自己那套方式完成学校登录（账密/微信/短信，**都在客户端侧**），
  然后把拿到的 `go-admin-tgt` 交上来」。
- 具体出站源：`internal/school/schoolauth/forward.go:163-183`（`XSGZSource.FetchIdentity`）
  与 `internal/school/schoolauth/jiaowu.go:382-409`（`ExchangeForIdentity`），
  两者都只从 cookie 里取 `go-admin-tgt`（`forward.go:164`、`jiaowu.go:383`），
  没有 `password`/`wechat`/`phone` 分支。

| `schoolMethod` 取值 | 结论 | 依据 |
| --- | --- | --- |
| `password` | **仅占位（有副作用但非学校认证）**：只触发「农屿账密镜像」写入，学校认证仍走 tgt | `service.go:326`、`service.go:495-516` |
| `phone` | **无实现 / 仅占位**：全仓源码中该值没有任何分支消费；`Phone`、`Code` 在 school 分支被桥接层丢弃 | `schoolauth_bridge.go:32-43`、`adapter.go:35-46` |
| `wechat` | **无实现 / 仅占位**：`WechatCode` 在 school 分支被桥接层丢弃；源码中仅单测 `internal/identity/identity_test.go:232`、`internal/identity/flow/flow_test.go:304` 构造过该值（用于验证「不写密码镜像」） | `schoolauth_bridge.go:32-43`、`internal/identity/login/service.go:74` |

`internal/identity/login/service.go:37-40` 的注释仍写着「两种形态，
`"password"/"wechat"/"phone"`：直接给学校凭据，由后端发起认证」，
但 `:38` 同时标注 `"cookie"` 是「当前主路径」，`internal/school/schoolauth/adapter.go:428-438`
更明确记录「原 httpSource / NewHTTPSource 骨架实现已删除，零生产消费者」。
**注释是历史遗留，代码只有 tgt 一条路。**

### 1.2 【确证】`GoAdminTgt` 是必需还是可选

**必需 —— 以 `go-admin-tgt` cookie 的形式必需；`wechatCode` / `phone+code` /
`studentNo+password` 无法替代。**

判空 / 校验的代码链（按顺序）：

1. `internal/identity/login/service.go:261-263`：`s.school == nil` → `internal_error`。
2. `internal/school/schoolauth/adapter.go:278-281`：`Authenticator.Authenticate` 先判
   `cred.Empty()`，为空直接 `ErrAuthFailed`。`Empty()` 定义在
   `adapter.go:118-120`，判据是 `CookieHeader()` 是否为空。桥接层
   `schoolauth_bridge.go:33` 把 `cookie` 放进 `RawCookie`、
   `:39-43` 把 `goAdminTgt` 放进 `Cookies`，**两者任一非空即过这一关**。
3. `internal/school/schoolauth/forward.go:163-167`：`XSGZSource.FetchIdentity` 做
   `tgt := cookieValue(cred, "go-admin-tgt")`，空则
   `return ..., fmt.Errorf("schoolauth: 缺少 %s", goAdminTgtCookie)`。
4. `internal/school/xsgz/source.go:90-93`：`Source.FetchIdentity` 再判一次
   `if strings.TrimSpace(tgt) == ""` → `xsgz: 缺少 go-admin-tgt`。
5. `internal/school/schoolauth/jiaowu.go:383-397`（换票确认学号）与
   `internal/school/cas/exchange.go:148-150`（通用换票）同样在空 tgt 时拒绝。
6. 失败到客户端：`internal/identity/login/service.go:264-267` 把认证错误包成
   `CodeBadCredentials`（`bad_credentials`），
   `internal/identity/flow/handler.go:209-210, 223` 映射成 HTTP 401。
   `adapter.go:282-285` 也把 `FetchIdentity` 的错误统一转成 `ErrAuthFailed`。

**所以：客户端不传 tgt、只传 `wechatCode` / `phone+code` / `studentNo+password` →
走不通（401 `bad_credentials`）。** 这些字段在 school 分支里根本没有出站出口。

**两个载体都行，但有差别（客户端要注意）：**

- `goAdminTgt` 字段：桥接层 `schoolauth_bridge.go:39-43` 转成 cookie，**并且**
  `internal/identity/login/service.go:340-341` 的 `tgtSaver.Save(...)` 只在
  `cred.GoAdminTgt != ""` 时才执行（保管到服务端，供后续免密续期，
  见 `internal/school/schoolauth/tgt.go:118-133`）。
- `cookie` 字段里内嵌 `go-admin-tgt=...`：`cookieValue` 会从 `RawCookie` 里解析出来
  （`internal/school/schoolauth/jiaowu.go:518-536`），**认证能走通**，
  但因为 `GoAdminTgt` 字段为空，`tgtSaver` 不会保管 tgt。
- 结论：**客户端应始终填 `goAdminTgt` 字段**；`cookie` 作为「整套同域 cookie」
  补充传入（`internal/school/schoolauth/tgt.go:108-116`、`adapter.go:69-86` 记录
  续期需要 `laravel_session` / `XSRF-TOKEN` 等配套 cookie）。

### 1.3 【确证】`xsgzToken` 可选（顺带确证契约 §3.1）

`internal/school/xsgz/source.go:104-113`：token 为空时后端自己调
`FetchToken(ctx, doer, "", tgt, decrypt)` 用 tgt 换；
`internal/school/schoolauth/forward.go:87-90` 是该能力的转发入口。
与契约「不传也行，后端会自己用 tgt 换」一致。

### 1.4 【确证】各字段在哪个分支被消费 / 消费后做什么

`loginReq` 定义：`internal/identity/flow/handler.go:42-67`；
映射进 `Params`：`handler.go:94-111`；分支派发：`internal/identity/flow/service.go:104-115`。

| 请求字段 | school 分支 | 其它分支 | 消费后做了什么 |
| --- | --- | --- | --- |
| `schoolMethod` | → `SchoolCredential.Method`（`handler.go:101`） | 不用 | school 里**只**用于 `service.go:326` 判 `password` 触发密码镜像；桥接层丢弃 |
| `studentNo` | → `SchoolCredential.StudentNo`，桥接层丢弃 | `Params.StudentNo` | `password`/`idcard`/`login/admin` 用于查用户（`flow/service.go:108,112,127`、`login/service.go:350,414`） |
| `password` | → `SchoolCredential.Password`，桥接层丢弃 | `Params.Password` | school 里仅触发密码镜像（`service.go:326-331`）；`password`/`admin` 用于比对（`login/service.go:368-374`） |
| `phone` | → `SchoolCredential.Phone`，桥接层丢弃 | 不用 | **无任何消费**（含非 school 分支） |
| `wechatCode` | → `SchoolCredential.WechatCode`，桥接层丢弃 | 不用 | **无任何消费** |
| `code` | → `SchoolCredential.Code`，桥接层丢弃 | `Params.Code`（`handler.go:108`） | **`mailcode` 分支用于校验邮箱验证码**（`flow/service.go:110` → `login/service.go:390-396`）。school 分支不用 |
| `ticket` | **未映射进 `Params`** | 不用 | **死字段**：全仓 `internal/identity` 下仅 `handler.go:54` 一处声明，无消费者 |
| `cookie` | → `SchoolCredential.CookieHeader` → `schoolauth.Credential.RawCookie` | 不用 | 作为出站换票的 `Cookie` 头原样透传（`jiaowu.go:273-277`、`jiaowu.go:399`），并传给 `tgtSaver.Save`（`login/service.go:341` → `schoolauth_bridge.go:78-90`） |
| `goAdminTgt` | → `go-admin-tgt` cookie | 不用 | 换票取身份与取档案的唯一入口凭据；另触发服务端保管（`login/service.go:340-341`） |
| `xsgzToken` | → `Credential.XSGZToken` | 不用 | `forward.go:168,191` 直接用于 xsgz detail；为空则后端自换（`xsgz/source.go:105-113`） |
| `email` / `idCardSuffix4` / `nameInitials` | 不用 | `mailcode` / `idcard` | `flow/service.go:110,112` |

**「是否只是转交给学校侧出站适配器」**：`cookie` / `goAdminTgt` / `xsgzToken` 是；
其余（`schoolMethod` / `studentNo` / `password` / `phone` / `code` / `wechatCode`）
在 school 分支**连转交都没有**，桥接层 `schoolauth_bridge.go:32-43` 就丢掉了。

---

## 2. 「统一身份认证」的真实入口（WebView 拿 `goAdminTgt`）

### 2.1 【确证】后端只接收 tgt，不提供「学校 CAS 登录页」入口

- `internal/school/schoolauth/adapter.go:27-34`：`Credential` 注释「**只有这一种凭据形态**……
  客户端先用自己那套方式完成学校登录（都在客户端侧），然后把拿到的 `go-admin-tgt`
  交上来；本适配器用它换票取身份，**不接触学生密码**」。
- `internal/school/schoolauth/ywtb.go:16-18`：「客户端在学校统一身份认证登录后**只会拿到
  这张 cookie**，不会有 ywtb 的 Bearer token。后端拿它换票」。
- `internal/identity/flow/handler.go:56-57`：`goAdminTgt` 注释「统一身份认证的全局登录态
  cookie……由客户端在 auth 域登录后交来」（见 `login/service.go:50-58`）。
- `internal/app/app.go:246-250`：装配层注释「把**客户端交上来的** go-admin-tgt 换成学校身份」
  「客户端只做一次统一登录，之后交出 go-admin-tgt」。
- 契约 `docs/后端接口与NY1签名对接契约.md:153-154` 同样写「客户端只做一次统一登录，
  交一张 `goAdminTgt` 即可」。

即：**`goAdminTgt` 完全由 App 自己从别处取得，后端只接收**。后端没有任何
「打开这个 URL → 登录 → 拿到 tgt 回传」的端点。

### 2.2 【确证】grep 到的 CAS / goAdmin / webvpn / ywtb 相关常量与出站 URL

这些**全是后端出站换票用的 API，不是给 WebView 打开的登录页**：

| 常量 / URL | 位置 | 性质 |
| --- | --- | --- |
| `go-admin-tgt`（cookie 名） | `internal/school/schoolauth/jiaowu.go:49`、`internal/school/cas/exchange.go:23`、`internal/school/xsgz/detail.go:125` | cookie 名常量 |
| `https://auth.sicau.edu.cn/api/v1/cas/login?appid=<appid>` | `internal/school/cas/exchange.go:158-168`、`internal/school/schoolauth/jiaowu.go:266`、`internal/school/xsgz/fetch.go:39-43` | **后端出站换票**（拿 st / user / 新 tgt） |
| `appid=ywtb`、`/gateway/xg-agg-pc/cas/login` | `internal/school/xsgz/fetch.go:43,94` | 后端换 xsgz token |
| `JiaowuAppID = "b54a2f27-..."`、`authHost`、`jiaowuHost`、`casLoginPath`、`academicPagePath` | `internal/school/schoolauth/jiaowu.go:39-52` | 后端出站常量 |
| `SiteAppID`（jiaowu / webvpn / ywtb）与 `SiteProfiles` | `internal/school/cas/exchange.go:34-38,72-100` | 站点登记表，用于换票 appid |
| `/api/v1/captcha` | `internal/school/jiaowu/service.go:270` | 后端代取图形验证码（`GET /school/captcha`） |
| `webvpnOrigin = "https://webvpn.sicau.edu.cn"`、`webvpnKey` | `internal/school/encode.go:14-20` | 地址编码，不是登录页 |
| `targetHosts` = jiaowu / auth / webvpn 三个 host | `internal/school/targets.go:23-36` | 出站白名单逻辑目标名 |
| 出站白名单内建 host | `internal/app/app.go:664-671` | 同上 |

**没有任何「统一身份认证登录页 URL」常量**（如 `auth.sicau.edu.cn/login`、
CAS `/login?service=...`、`webvpn .../login` 作为可打开页面的配置项）。
出现 `/login` 字样的地方是**失败判定**：`internal/school/jiaowu/service.go:466`
（「200 的登录页 HTML 应回落」）、`internal/school/jiaowu/fetch_test.go:85-91,236-251`
（上游 302 指向 `https://webvpn.sicau.edu.cn/login` 时判 `session_expired`）。
它们把登录页当作「会话失效的信号」，不是「给客户端的入口」。

### 2.3 【确证】`/school/address` 与 `/school/login` 不是 WebView 登录入口

- `GET /school/address?target=<逻辑名>&path=...`：
  `internal/school/jiaowu/handler.go:23,57-72` → `service.go:121-140`，
  返回 `{"target","address"}`，`address` 是经 webvpn 编码的 URL。
  它只返回地址、零出站（`service.go:113-140`），**不定义任何登录页 path**，
  且出站闸口会剥离 `Set-Cookie`（`handler.go:88-91`）。
- `POST /school/login`：`internal/school/jiaowu/handler.go:39,145-165`，
  请求体只有 `{"code","uuid"}`（`:140-143`），要求**先有已认证身份**
  （`:148-151` 无 userID 即 401），返回 `{"ticket","expiresAt"}`
  （`service.go:251-255,349`）——是 `/school` 域自己的票据，**不是 `goAdminTgt`**，
  且 `service.go:306-307` 注明「骨架期不真的回传验证码事务」。

### 2.4 【待核实】App 从哪取得 tgt / 能否用 WebView

后端源码不给答案。「客户端自己完成一次统一登录」的具体 URL、WebView 流程、
如何从 WebView 的 cookie 里读 `go-admin-tgt`，**源码未见**，需向维护者或
`docs/tech/` 下的实测文档核实。本文能确证的只有：后端没有下发这个 URL，
也没有一个端点能帮你把登录页走完并把 tgt 交还。

---

## 3. 验证码发送

### 3.1 【确证】`POST /identity/mailcode/send` 请求体

- 路由：`internal/identity/flow/handler.go:39`（`r.POST("/identity/mailcode/send", ...)`）。
- 请求体：**只有一个字段 `email`**（`handler.go:170-177`）：
  ```json
  { "email": "..." }
  ```
  `ShouldBindJSON` 失败 → 400 `{"code":"invalid_request"}`（`handler.go:175-177`）。
  没有 `captcha` / `type` / `phone` 等其它字段。
- 成功：`200 {"sent": true}`（`handler.go:183`）。
- 业务约束（`internal/identity/login/service.go:455-489`）：
  - **邮箱必须已绑定某个账号**，否则不发送并返回 `need_school_auth`（`:461-463`），
    经 `flow/handler.go:211-213` 映射为 HTTP 400。目的是防匿名者拿它当邮件轰炸器
    （`service.go:457`）。
  - 冷却 / 有效期同为 5 分钟：`mailCodeTTL`（`service.go:491-492`）；
    冷却中返回 `CodeTooManyAttempts` → HTTP 429（`service.go:465-467`、`flow/handler.go:218-219`）。
  - 6 位数字码（`service.go:469`）；投递失败会把码作废（`service.go:480-487`）。
  - 依赖：Redis `RedisMailCodeStore` + `Mailer`（`internal/app/app.go:393-411,375-391`），
    未配置 Redis / SMTP 时该方式不可用或只写日志。

### 3.2 【源码未见】手机号验证码发送端点

**无。** 全仓 grep 以下关键词均无 Go 路由 / handler / 常量命中：
`/identity/smscode/send`、`smscode`、`sms/code`、`phone/code`、`sendSms`、
`SendSMS`、`mobileCode`、`短信验证码` 发送端点。
唯一命中的是历史文档 `BE/docs/tech/旧农屿WebVPN可重写契约清单.md:47-48,151`
（`sendJiaowuSmsCode`、`POST .../api/v1/sms/send`），那是**旧农屿客户端仓库**的
契约清单，不是当前 Go 后端的路由；当前后端 20 条路由里没有短信发送端点。

**给登录页 UI 的结论**：`schoolMethod: "phone"` 没有对应的后端发码端点，
`phone` + `code` 两个字段在 school 分支也不被消费（见 §1.4）。
若 UI 要画「手机号 + 验证码」登录，目前没有后端支撑。

---

## 4. token 校验 / 会话校验端点

### 4.1 【源码未见】专用校验端点

**无专用端点，最接近的是 `GET /identity/me`。**

逐个 grep（全仓 `*.go`）确认以下路径**均无路由注册、无 handler、无常量**：

| 候选路径 | 结果 |
| --- | --- |
| `/identity/verify` | 【源码未见】 |
| `/identity/session` | 【源码未见】（`internal/identity/session/` 是包名，不是路由；`identity/session` 字样只出现在 import 与测试里） |
| `/identity/token/verify` | 【源码未见】 |
| `/identity/token`、`/verify`（泛指） | 【源码未见】 |

全仓路由注册点只有 5 处（`Register*` 内），逐条列出以证「没有第六处」：

- `internal/identity/flow/handler.go:34-40`：`/identity/login`、`/identity/login/admin`、
  `/identity/logout`、`/identity/logout/all`、`/identity/mailcode/send`
- `internal/identity/user/handler.go:28-32`：`/identity/me`、`/identity/me/profile`、
  `/identity/users/:studentNo`
- `internal/school/jiaowu/handler.go:22-41`：`/school/address`、`/school/fetch`、
  `/school/captcha`、`/school/login`、`/school/login/accel`、`/school/logout`
- `internal/school/jiaowu/cachehandler.go:24-26`：`/school/cache/:dataType`（GET/POST/DELETE）
- `internal/school/presence/handler.go:32-33`：`/school/presence`（POST/GET）
- 探针：`internal/server/server.go:18` `/healthz`、`internal/app/status.go:38` `/status`

> 附注（不影响结论）：`internal/server/revocable_auth.go:144-150` 的 `publicPaths`
> 里列了 `/identity/register`（`:149`，注释「独立注册入口」），但全仓**没有注册该
> 路由**；命中它会得到 gin 默认 `text/plain` 404（与契约 §6 一致）。
> 它是公开路径白名单里的遗留项，不是可用的校验端点。

### 4.2 【确证】`GET /identity/me` 的响应形状

- 路由 / handler：`internal/identity/user/handler.go:29,51-68`。
- 响应结构 `profileResp`（`handler.go:38-49`）：
  ```json
  {
    "studentNo": "...",     // omitempty；meHandler 未填，实际响应里通常不出现
    "name": "...",
    "college": "...",
    "major": "...",
    "className": "...",
    "grade": "...",
    "qq": "...",
    "avatarUrl": "...",
    "idCardSuffix4": "..." // omitempty；仅管理台详情端点填充，/me 不返回
  }
  ```
  `meHandler` 实际只填 `Name / College / Major / ClassName / Grade / QQ / AvatarURL`
  （`handler.go:63-66`），**不填 `StudentNo`、`IDCardSuffix4`**。
  默认不含身份证号与密文（`handler.go:34-37`）。
- 身份来自 context（鉴权中间件写入），不读请求体/query（`handler.go:10,53`）。
- 该端点同时是最实用的「token 是否还有效」探针：200 即会话有效。

### 4.3 【确证】401 时的 code

生产装配的鉴权是 `RevocableAuthMiddleware`（`internal/app/app.go:453-463`）：

| 情形 | HTTP | code | 依据 |
| --- | --- | --- | --- |
| 缺 `Authorization: Bearer `、头格式不对、令牌签名/过期校验失败 | 401 | `unauthorized` | `internal/server/revocable_auth.go:171-180` |
| 会话不存在 / 已过期 / 已吊销 / 账号被禁用（`ErrSessionInactive`） | 401 | `session_expired` | `revocable_auth.go:182-190,223-244` |
| 解析器其它故障（fail-closed） | 503 | `auth_unavailable` | `revocable_auth.go:190` |
| `meHandler` 自身发现 context 无 userID | 401 | `unauthorized` | `user/handler.go:53-56` |

常量定义：`internal/identity/identity.go:35`（`unauthorized`）、`:41`（`session_expired`）。
统一错误响应形状 `{"code":"..."}`，**无 `message`**（`revocable_auth.go:173,178,187,190`）。
与契约 §3.3「401 码 `unauthorized`（令牌非法）/ `session_expired`（会话失效/被吊销/
账号禁用）」一致；补充一点：**账号被禁用**在 `ResolveSession` 里也被归为
`ErrSessionInactive` → `session_expired`（`revocable_auth.go:241-244`），
而不是 `user_disabled`。

---

## 5. `deviceId`

### 5.1 【确证】后端怎么用它

数据流：

- `loginReq.DeviceID`（`internal/identity/flow/handler.go:44`，`json:"deviceId"`）
  → `Params.DeviceID`（`handler.go:96`；定义 `internal/identity/flow/service.go:79-80`）
  → `s.sessions.Issue(ctx, res.User.ID, platform.ScopeApp, p.DeviceID)`
  （`flow/service.go:125`）。
- **单设备踢人依据**：`internal/identity/session/service.go:67-108`。
  - `:80-83`：签发前先 `s.sessions.RevokeByUserScope(ctx, userID, scope)`
    「先踢掉同 scope 的旧会话」（`:80` 注释、`flow/service.go:65` 注释）。
  - `:94-100`：把 `DeviceID` 原样写进 `platform.Session.DeviceID` 并存库。
  - 这是**按 userID + scope 全量吊销**，不是按 deviceID 精确匹配；
    也就是说「换设备登录」会顶掉该用户所有 App 会话。
- **每次请求的 device 匹配校验**（注意生产装配的差异，见下）：
  `session.Service.Authenticate` 在 `internal/identity/session/service.go:138-141`：
  `if scope == platform.ScopeApp && sess.DeviceID != deviceID` →
  `identity.NewError(identity.CodeSessionExpired, "会话已在其他设备登录")`。
  `:122-123` 注释「四项校验：未过期、未吊销、scope 匹配、App 侧 device 匹配」。
- **登录审计**：`flow/service.go:49-55,131` 的 `touchLogin` 把 `deviceId` 连同
  IP、时间一起写进 `UserRepo.TouchLogin`（失败只记日志不阻断登录）。
- **管理台不参与**：`flow/service.go:163-164` 用固定值 `"admin-web"` 签发，
  `session/service.go:138` 的 device 校验只对 `ScopeApp` 生效（`:138` 注释）。

### 5.2 【确证 / 需注意】生产中间件不按请求校验 deviceId

生产用的是 `server.RevocableAuthMiddleware` + `SessionStoreResolver`
（`internal/app/app.go:457-459`）。`SessionStoreResolver.ResolveSession` 虽然把
`sess.DeviceID` 放进 `SessionInfo`（`internal/server/revocable_auth.go:245-250`），
但中间件 `revocable_auth.go:164-200` **没有读任何每次请求携带的 device 头，
也没有做 device 比对**；`internal/server/auth.go:98` 也只取 `Authorization`。
`session.Service.Authenticate` 的 device 比对在源码中只被单测调用
（`internal/identity/identity_test.go:318-353`、`internal/identity/flow/flow_test.go:144,253`）。

**给客户端的结论**：`deviceId` 只在**登录时**需要提交，用于 (a) 单设备顶替、
(b) 写进会话记录、(c) 登录审计。**不需要在每次请求里带 deviceId 头**，
后端也没有这样的头或字段。会话失效由「签发时吊销旧会话」+ 中间件查会话实现。

### 5.3 【确证】客户端怎么生成 / 持久化：**后端不规定**

- 后端对 `deviceId` **没有任何格式校验**：`ShouldBindJSON` 只做 JSON 绑定
  （`flow/handler.go:90-93`），无长度/字符集/非空检查；`Params.DeviceID`
  是纯 `string`（`flow/service.go:80`）；`Issue` 直接把它塞进 `Session`
  （`session/service.go:98`），空串也照收（此时 `Session.DeviceID == ""`，
  `session.Service.Authenticate` 的 `:139` 比对仍成立，只要后续也传空串）。
- **后端不生成、不下发、不提供持久化 API**：全仓无 `deviceId` 生成函数、
  无 UUID/随机串生成用于 device、无「注册设备」端点。
- 因此客户端必须自己决定：
  1. **生成**：一个不透明稳定 ID（例如 `UUID.randomUUID().toString()`），
     不要放可直接识别个人/设备的敏感信息。
  2. **持久化**：存在 App 本地私有存储，跨进程/跨重启保持稳定；
     卸载重装或清数据后允许重新生成（下一次登录会顶掉旧会话，符合单设备语义）。
  3. **何时改**：只在「确实是新设备 / 用户主动重装」时改。同一台设备上
     频繁变化会让每次登录都把自己的上一个会话踢掉；但**不影响已签发令牌的
     每次请求**（见 §5.2）。
  4. **与令牌一起保管**：登录成功后把 `deviceId` 与 `token` 存在同一处，
     重登时复用同一个值。

---

## 6. 与权威文档的对照（补充 / 逐条澄清，不推翻）

| # | 契约原文 | 源码事实 | 性质 |
| --- | --- | --- | --- |
| 1 | §3.1 表格列 `schoolMethod` / `studentNo` / `password` / `phone` / `code` / `wechatCode` 为「视方式」 | 在 `school` 分支里这些字段被 `schoolauth_bridge.go:32-43` 丢弃；只有 `password` 顺带触发密码镜像（`login/service.go:326`）。`code` 只在 `mailcode` 分支消费。**后端不按 `schoolMethod` 分流** | **细化 / 重要澄清**：UI 画三种方式不会改变后端行为；契约正文 §3.1:153-154「交一张 goAdminTgt 即可」才是准确落点 |
| 2 | §3.1 `goAdminTgt` ✅ 必填 | 确证必需（`forward.go:164-167`、`xsgz/source.go:91-93`），但**载体可以是 `goAdminTgt` 字段，也可以是 `cookie` 里内嵌 `go-admin-tgt=`**；只走 `cookie` 时 `tgtSaver` 不保管 tgt（`login/service.go:340`） | 补充实现细节 |
| 3 | §3.1 `cookie` 标注「可选」 | 确证可选，但 `tgt.go:108-116` 记录服务端续期需要整套同域 cookie（laravel_session 等），建议一并传 | 补充 |
| 4 | §3.1 `xsgzToken` ❌ 不传也行 | 确证：`xsgz/source.go:104-113` 后端自换 | 一致 |
| 5 | §3.1 请求体未列 `ticket` | `loginReq` 有 `ticket`（`flow/handler.go:54`）但**未映射、无消费者**，是死字段 | 补充（契约不列是对的） |
| 6 | §3.3 单设备「签发前踢掉同 scope 旧会话」 | 确证 `session/service.go:80-83`；补充「按 userID+scope 全量吊销，不是按 deviceID 精确匹配」，以及生产中间件不按请求校验 deviceId | 一致 + 补充 |
| 7 | §3.3 401 码 `unauthorized` / `session_expired` | 确证 `revocable_auth.go:173,178,187`；补充「账号禁用也归 `session_expired`」「另有 503 `auth_unavailable`」 | 一致 + 补充 |
| 8 | §4 `auth /api/v1/cas/login?appid=ywtb` | 确证 `internal/school/xsgz/fetch.go:43` | 一致 |
| 9 | §2 20 条路由 | 逐条 grep 路由注册点（§4.1）确证**没有** token 校验专用端点；最接近 `GET /identity/me` | 一致 + 回答 Q4 |
| 10 | §3.1 称 `goAdminTgt` 为「55 字符」 | 源码**没有长度/格式校验**（`cookieValue` 只按名取值，`jiaowu.go:518-536`） | 【待核实】：55 字符是实测观察值，不是后端约束，客户端不要据此做长度校验 |
| 11 | §2.1 `POST /identity/mailcode/send` | 确证请求体只有 `{"email"}`，成功 `{"sent":true}`，已绑定才发、5 分钟冷却 | 补充 |
| 12 | §6 未匹配路径是 `text/plain` 404 | 顺带发现 `publicPaths` 里的 `/identity/register` 无对应路由（`revocable_auth.go:149`） | 补充，不影响契约 |

---

## 7. 【待核实】汇总

1. **App 从哪里取得 `goAdminTgt`**：统一身份认证登录页的真实 URL、WebView 流程、
   如何从 WebView cookie 读 `go-admin-tgt`，后端源码未见（§2.4）。
   这是登录页 UI 能否落地 WebView 方案的**唯一外部依赖**，需问维护者。
2. **`goAdminTgt` 长度 55**：源码无校验，属实测观察值（§6 #10）。
3. **`schoolMethod` 三种取值是否计划实装**：源码注释（`login/service.go:37-40`）
   仍写着「由后端发起认证」，但实现已被删除（`adapter.go:428-438`）。
   注释与代码不一致，若要恢复需新规格，别照注释写 UI 逻辑。
4. **`/school/address` 能否拼出可给 WebView 打开的 auth 登录页**：
   后端只返回经 webvpn 编码的地址、且出站剥离 `Set-Cookie`，源码未证明可行（§2.3）。
