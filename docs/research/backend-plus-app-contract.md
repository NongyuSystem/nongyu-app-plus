# nongyu-backend-plus 对外契约快照（路由 / 形状 / 鉴权 / 缺口）

> 对应 issue #46（NongyuSystem/nongyu-app-plus）· 2026-11-09。
> 全部结论读自 `apps/nongyu-backend-plus` 源码（提交 `bb74cc7`），关键形状另经**实机探针**复核（见 §8.1）。
> 只记形状与缺口，不评价设计。

## 1. 结论速览

| 项 | 事实 | 依据 |
| --- | --- | --- |
| 路由总数 | **16 条**（13 条无条件注册 + 3 条仅在 `DATABASE_URL` 非空时注册） | §2 |
| 路径前缀 | **无**（`/school/*`、`/identity/*`、`/healthz` 直接挂在根） | `internal/app/app.go:23-37,335,342,345,371` |
| 成功响应 | **裸业务结构体**，**无统一信封**；16 条路由中 **11 条**裸结构体、**4 条** `gin.H` 直白、**1 条** `c.Data` 二进制 | §3.1 |
| 错误响应 | **字符串码**，两种形状并存：`{"code":"..."}`（identity/中间件）与 `{"code":"...","message":"..."}`（school） | §3.2 |
| 错误码字面量 | **23 个**（identity 10 + school 5 + presence 1 + 中间件 4 + handler 手写 3） | §3.3 |
| 鉴权头 | `Authorization: Bearer <token>`，**大小写敏感、必须带单个空格** | `internal/server/auth.go:98`、`revocable_auth.go:162` |
| 令牌载荷 | 可吊销模式只带 `{"sid","exp"}`；降级模式带 `{"sub","exp"}` | `revocable_auth.go:96-107`、`auth.go:39-50` |
| 会话 TTL | App **30 天**、Admin **12 小时**，**不滑动续期** | `internal/identity/session/service.go:24-27` |
| 覆盖度 | 对 node-server 14 组 `/api/app/*`：**12 组完全没有**、2 组部分有 | §7 |

三个必须让所有下游知道的硬结论：

1. **错误码是字符串，且没有 `data` 字段。** node-server 是 `{code:number, message, data}`。任何照抄 node-server 错误处理的客户端会读到 `undefined`。确证在 §3.2/§3.3。
2. **`POST /identity/login` 在可吊销装配下返回 401**，公开路径表写的是 `/identity/login/`（带尾斜杠，走前缀匹配），与实际注册路径 `/identity/login`（无尾斜杠、精确匹配失败）对不上。这是实机 + 中间件单测双重确认的（§4.3）。
3. **三个 `/identity/me*` 路由在未配 `DATABASE_URL` 时压根不注册**，返回 gin 默认 `404 page not found`（text/plain），而不是 401 或 200（§8.1 实测）。

## 2. 路由全表

装配入口 `internal/app/app.go`。三套 Register 的差别（`internal/school/jiaowu/handler.go`）：

- `Register`（L22-25）：2 条路由（#13/#14 形态）。
- `RegisterWithAuth`（L36-41）：先调 `Register`，再追加 3 条（#15 换票主路径）。
- `RegisterAccel`（L29-32）：先调 `RegisterWithAuth`，再追加 1 条（#17 加速路径）。**生产装配用的是它**（`app.go:335`）。

| # | method | path | 鉴权要求 | 注册函数 | 文件:行号 |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/healthz` | 无（且在 `Use` 之前注册，连 RequestID / 限流都不经过） | `server.New` | `internal/server/server.go:18` |
| 2 | GET | `/school/address` | Bearer（`school.CtxUserID` 由中间件写） | `jiaowu.Register` | `internal/school/jiaowu/handler.go:23` |
| 3 | GET | `/school/fetch` | Bearer（同上） | `jiaowu.Register` | `internal/school/jiaowu/handler.go:24` |
| 4 | GET | `/school/captcha` | Bearer（同上） | `RegisterWithAuth` | `internal/school/jiaowu/handler.go:38` |
| 5 | POST | `/school/login` | Bearer + 显式判空 401 | `RegisterWithAuth` | `internal/school/jiaowu/handler.go:39` |
| 6 | DELETE | `/school/logout` | Bearer + 显式判空 401 | `RegisterWithAuth` | `internal/school/jiaowu/handler.go:40` |
| 7 | POST | `/school/login/accel` | 中间件要求 Bearer；**handler 内部完全不读身份** | `RegisterAccel` | `internal/school/jiaowu/handler.go:31` |
| 8 | POST | `/school/presence` | Bearer + 显式判空 401 | `presence.Register` | `internal/school/presence/handler.go:32` |
| 9 | GET | `/school/presence` | Bearer + 显式判空 401 | `presence.Register` | `internal/school/presence/handler.go:33` |
| 10 | POST | `/identity/login` | **名义公开，实际 401**（§4.3） | `identityflow.Register` | `internal/identity/flow/handler.go:33` |
| 11 | POST | `/identity/login/admin` | **公开**（前缀匹配命中） | `identityflow.Register` | `internal/identity/flow/handler.go:34` |
| 12 | POST | `/identity/logout` | Bearer（需 `CtxSessionID`） | `identityflow.Register` | `internal/identity/flow/handler.go:35` |
| 13 | POST | `/identity/mailcode/send` | **公开**（前缀匹配命中） | `identityflow.Register` | `internal/identity/flow/handler.go:36` |
| 14 | GET | `/identity/me` | Bearer；**仅 `identityStore != nil` 时注册** | `identityuser.Register` | `internal/identity/user/handler.go:29` |
| 15 | PATCH | `/identity/me/profile` | Bearer；**仅 `identityStore != nil` 时注册** | `identityuser.Register` | `internal/identity/user/handler.go:30` |
| 16 | GET | `/identity/users/:studentNo` | Bearer；**仅 `identityStore != nil` 时注册**；注释称「需管理员」但**无角色校验代码** | `identityuser.Register` | `internal/identity/user/handler.go:31` |

条件注册点：`app.go:339-347`（`if identityStore != nil`）。`identityStore` 只在 `cfg.DatabaseURL != ""` 时构造（`app.go:212`）。

**明确没有 HTTP 入口的包**（读代码后确认，避免误记）：

- `internal/identity/login`：`handler.go` 全文只有 9 行注释「登录子域**不提供 HTTP 入口**」（L1-9）。`LoginBySchool` / `LoginByPassword` / `LoginByMailCode` / `LoginByIDCard` 都是 Service 方法（`login/service.go:226,292,324,356`），由 flow 编排层调用，**不是端点**。
- `internal/identity/session`：`handler.go` 同样只有注释（L1-9）。`Issue` / `Authenticate` / `Revoke` / `RevokeAll` / `RequireRole` 都不是端点。
- `internal/school/schoolauth/*`：无任何 `gin` / `r.GET` 引用（grep 无命中），是纯出站适配器。

**没有任何 `NoRoute` / `HandleMethodNotAllowed` 设置**（grep `NoRoute` 无命中），所以未匹配路径与方法不匹配都落到 gin 默认输出，见 §3.4。

## 3. 响应形状

### 3.1 成功响应：裸业务结构体，无信封

「无统一信封」**在全部已注册路由范围内成立**：没有任何一处 `c.JSON(200, gin.H{"code":..., "data":...})`。逐条抄录定义：

**(a) `GET /school/address`** → `addressResponse`（`jiaowu/handler.go:44-47`，写于 L67-70）：

```go
type addressResponse struct {
	Target  string `json:"target"`
	Address string `json:"address"`
}
```

实机：`{"target":"jiaowu","address":"https://webvpn.sicau.edu.cn/https/777264.../"}`。
注意同一形状在 Service 层还重复定义了一次 `AddressResult`（`jiaowu/service.go:102-105`），字段与 tag 完全相同。

**(b) `GET /school/captcha`** → 直接 `c.JSON(200, result)`（`jiaowu/handler.go:136`）吐 `CaptchaResult`（`jiaowu/service.go:206-209`）：

```go
type CaptchaResult struct {
	UUID  string `json:"uuid"`
	Image string `json:"image"`
}
```

实机：`{"uuid":"cap-auth","image":""}`。

**(c) `POST /school/login`** → `LoginResult`（`jiaowu/service.go:212-215`）：

```go
type LoginResult struct {
	Ticket    string    `json:"ticket"`
	ExpiresAt time.Time `json:"expiresAt"`
}
```

实机：`{"ticket":"","expiresAt":"2026-11-18T13:38:33.6860073+08:00"}`。
**`expiresAt` 这里是 RFC3339 字符串**，而 `/identity/login` 的 `expiresAt` 是 Unix 秒整数（§3.1e）——同名不同型。

**(d) `DELETE /school/logout`** → `gin.H{"status":"ok"}`（`jiaowu/handler.go:178`）。唯一用 `"status"` 键的地方。

**(e) `POST /school/login/accel`** → `AccelResult`（`jiaowu/service.go:384-387`）：

```go
type AccelResult struct {
	NeedCaptcha bool   `json:"needCaptcha"`
	Code        string `json:"code,omitempty"`
}
```

实机（默认关闭）：`{"needCaptcha":true}`。回落时是 `{"needCaptcha":true,"code":"ticket_expired"}`（`service.go:445-451`）。

**(f) `POST /identity/login` 与 `POST /identity/login/admin`** → `loginResp`（`flow/handler.go:64-80`）：

```go
type loginResp struct {
	Token string `json:"token"`
	ExpiresAt int64 `json:"expiresAt"`   // Unix 秒
	IsNew     bool  `json:"isNew"`
	ProfileIncomplete bool `json:"profileIncomplete"`
	User              struct {
		ID        int64  `json:"id"`
		StudentNo string `json:"studentNo"`
		Name      string `json:"name"`
		College   string `json:"college"`
		Major     string `json:"major"`
		ClassName string `json:"className"`
		Grade     string `json:"grade"`
	} `json:"user"`
}
```

`User` 是**匿名字段类型**（不是命名类型），无法在别处复用。字段齐全性见 §5。

**(g) `POST /identity/logout`** → `gin.H{"ok":true}`（`flow/handler.go:139`）。
**(h) `POST /identity/mailcode/send`** → `gin.H{"sent":true}`（`flow/handler.go:156`）。

三个 `gin.H` 直白式回执（(d)(g)(h)）键名互不相同：`status` / `ok` / `sent`——不是统一的 `{success:true}`。

**(i) `GET /identity/me`、`PATCH /identity/me/profile`、`GET /identity/users/:studentNo`** → `profileResp`（`user/handler.go:38-49`）：

```go
type profileResp struct {
	StudentNo string `json:"studentNo,omitempty"`
	Name      string `json:"name"`
	College   string `json:"college"`
	Major     string `json:"major"`
	ClassName string `json:"className"`
	Grade     string `json:"grade"`
	QQ        string `json:"qq"`
	AvatarURL string `json:"avatarUrl"`
	IDCardSuffix4 string `json:"idCardSuffix4,omitempty"`
}
```

`StudentNo` 带 `omitempty`：`GET /identity/me` 与 `PATCH /identity/me/profile` 的构造处（L63-66、L106-109）**都不填 StudentNo**，所以这两个端点响应里**没有 `studentNo` 字段**；只有管理台详情端点填（L131-134）。`IDCardSuffix4` 声明了但**三个 handler 无一填充**（死字段）。

**(j) `POST /school/presence`** → `reportResponse`（`presence/handler.go:43-46`）：

```go
type reportResponse struct {
	UserID string `json:"userID"`
	Mode   Mode   `json:"mode"`
}
```

注意键是 **`userID`（大写 ID）**，导出的是身份字符串（实机回 `"1"`）。

**(k) `GET /school/presence`** → `decideResponse`（`presence/handler.go:55-58`）：

```go
type decideResponse struct {
	Mode   Mode   `json:"mode"`
	Source string `json:"source"`
}
```

实机：`{"mode":"vpn","source":"client"}`。`Mode` 取值仅 `"direct"` / `"vpn"`（`presence/presence.go:35,37`）；`source` 取值 `client` / `backend`。

**(l) `GET /school/fetch`** → **不是 JSON**：`c.Data(result.Status, "application/octet-stream", result.Body)`（`jiaowu/handler.go:96`），上游状态码与响应体原样透传，`Set-Cookie` 被剥（L88-95）。

**(m) `GET /healthz`** → `gin.H{"status":"ok"}`（`server/server.go:25`）。实机确认，且响应头**没有 `X-Request-Id`**。

### 3.2 错误响应：两种并存形状

**(1) `{"code": "..."}` —— identity 域 + 全部中间件**（只有 `code`，无 `message`）：

- `internal/server/auth.go:99,104`（降级鉴权 401）、`auth.go:170`（429）
- `internal/server/revocable_auth.go:163,168`（401）、`:177`（401）、`:180`（503）
- `internal/identity/flow/handler.go:86,116,132,149,196`
- `internal/identity/user/handler.go:55,81,86,119,125,128,147`

**(2) `{"code": "...", "message": "..."}` —— school 域**（`jiaowu/handler.go:50-53`、`presence/handler.go:49-52`，两处独立重复定义）：

```go
type errorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
```

所以客户端对错误响应**不能假定有 `message`**：identity 域的 4xx/5xx 一个都没有。

### 3.3 全部 `Code` 字面量及其 HTTP 状态

#### (A) identity 常量表（`internal/identity/identity.go:33-54`）——10 个

| Code | HTTP | 映射位置 |
| --- | --- | --- |
| `unauthorized` | 401 | `flow/handler.go:183`、`user/handler.go:143` |
| `session_expired` | 401 | 同上 |
| `bad_credentials` | 401 | `flow/handler.go:182-183` |
| `need_school_auth` | 400 | `flow/handler.go:184-186`（注释明写「400 而不是 401」） |
| `forbidden` | 403 | `flow/handler.go:187-188`、`user/handler.go:144-145` |
| `user_disabled` | 403 | 同上 |
| `login_method_unbound` | 400 | `flow/handler.go:189-190` |
| `too_many_attempts` | 429 | `flow/handler.go:191-192` |
| `conflict` | 409 | `flow/handler.go:193-194` |
| `internal_error` | 500 | 默认分支（`flow/handler.go:180`、`user/handler.go:140`） |

`internal_error` 也是 `CodeOf()` 对任何非 `*identity.Error` 的兜底（`identity.go:82-88`）。

**常量表里有、但生产代码从不产出**：`conflict`（grep 非测试代码中无 `CodeConflict` 的 `NewError`/`WrapError` 调用；只在 `flow/handler.go:193` 被消费）。`forbidden` 仅由 `session.RequireRole` 产出（`session/service.go:214,220`），而它只在 `LoginAdmin` 里被调用（`flow/service.go:127`）——即 `forbidden` 只会出现在管理台登录响应。

#### (B) school 常量表（`internal/school/targets.go:44-50`）——5 个

| Code | HTTP | 映射位置 |
| --- | --- | --- |
| `target_denied` | 400 | `jiaowu/handler.go:110-111` |
| `blocked_redirect` | 502 | `jiaowu/handler.go:112-113` |
| `session_expired` | 401 | `jiaowu/handler.go:114-115`（与 identity 的 `session_expired` **同字面量、同状态，但语义是「上游教务会话失效」**） |
| `ticket_expired` | **500**（未在 switch 里，落默认） | `jiaowu/handler.go:106` 默认值 |
| `campus_unreachable` | **500**（同上） | 同上；且**生产代码无产出点**（grep 非测试无命中，全是死码） |

#### (C) presence 常量（`presence/handler.go:26`）——1 个

| Code | HTTP | 位置 |
| --- | --- | --- |
| `invalid_presence` | **400 与 401 都用它** | `presence/handler.go:99`；401 分支见 L66、L88 |

**`POST`/`GET /school/presence` 未带票时返回的 body 是 `{"code":"invalid_presence","message":"缺少已认证身份"}`，状态 401**（`writeError` 的 code 是常量，status 是参数）。这是全仓唯一「401 配非形状不匹配码」的地方。

#### (D) 中间件字面量 —— 4 个

| Code | HTTP | 位置 |
| --- | --- | --- |
| `unauthorized` | 401 | `auth.go:99,104`、`revocable_auth.go:163,168` |
| `session_expired` | 401 | `revocable_auth.go:177` |
| `auth_unavailable` | 503 | `revocable_auth.go:180` |
| `rate_limited` | 429 | `auth.go:170` |

#### (E) handler 里手写、不在任何常量表里的 —— 3 个

| Code | HTTP | 位置 |
| --- | --- | --- |
| `invalid_request` | 400 | `flow/handler.go:86,116,149`、`user/handler.go:86,119` |
| `not_found` | 404 | `user/handler.go:125` |
| `bad_request` | 400 | `jiaowu/handler.go:155` |

`bad_request` 与 `invalid_request` **同义异名**，分属两个域，客户端需两个都认。

#### 汇总：23 个字面量

```
unauthorized, session_expired, bad_credentials, need_school_auth, forbidden,
user_disabled, login_method_unbound, too_many_attempts, conflict, internal_error,
target_denied, blocked_redirect, ticket_expired, campus_unreachable,
invalid_presence, auth_unavailable, rate_limited,
invalid_request, not_found, bad_request
```
（去重后 20 个不同字符串；分域计数 23 = identity 10 + school 5 + presence 1 + 中间件 4 + 手写 3，其中 `session_expired` 与 `unauthorized` 跨域重复。）

### 3.4 未匹配路径的形状

gin 默认 404：`Content-Type: text/plain`、body `404 page not found`（**不是 JSON**）。实机确认于 `GET /identity/me`（无 DB 装配）。方法不匹配同落此路径（未设 `HandleMethodNotAllowed`）。

## 4. 鉴权语义

### 4.1 `Authorization: Bearer` 的确切格式

两套中间件写法一致（`auth.go:97-102`、`revocable_auth.go:161-166`）：

```go
hdr := c.GetHeader("Authorization")
if !strings.HasPrefix(hdr, "Bearer ") { /* 401 unauthorized */ }
... strings.TrimPrefix(hdr, "Bearer ")
```

- **大小写敏感**：`bearer xxx` → 401（实机确认）。
- **必须有单个空格**：`Bearerabc` → 401（实机确认）。
- `TrimPrefix` 只去掉第一个 `Bearer `，后面原样交给验签。
- **只认 header**：query 里的 `token=` 一律无效（实机确认 401）。也不读 cookie。
- 缺失与非法**返回同一个 401 `{"code":"unauthorized"}`**，不区分。

### 4.2 令牌里带什么

**可吊销模式（identity 域可用时，生产形态）** —— `revocable_auth.go:96-107`：

```go
type sessionClaims struct {
	SessionID string `json:"sid"`
	ExpiresAt int64  `json:"exp"`   // Unix 秒
}
```

令牌 = `base64url(claims) + "." + base64url(HMAC-SHA256(payload))`，`RawURLEncoding`，两段（`auth.go:45-50`、`revocable_auth.go:103-107`）。**不带 userID、不带 role、不带 scope、不带 deviceID** —— 这些一律靠 `sid` 回查。

**降级模式（未配 `DATABASE_URL`）** —— `auth.go:39-42`：

```go
type tokenClaims struct {
	UserID    string `json:"sub"`
	ExpiresAt int64  `json:"exp"`
}
```

自包含，验签即放行。`app.go:321-324` 明确告警「登出与踢人不会生效，仅限本地开发」。

### 4.3 可吊销模式下验签后还要查什么

`SessionStoreResolver.ResolveSession`（`revocable_auth.go:213-241`），顺序：

1. `sessions.SessionByID(ctx, sid)` → 查不到 → `ErrSessionInactive`
2. `sess.Active(now)` → 已吊销或已过期 → `ErrSessionInactive`（`platform/identity.go:114-119`：`RevokedAt != nil` 或 `!now.Before(ExpiresAt)`）
3. `users.ByID(ctx, sess.UserID)` → 用户不存在 → `ErrSessionInactive`
4. `u.Status != platform.UserActive` → `ErrSessionInactive`（注释：「账号被禁用后，已签发的会话立刻失效」，L231-234）

**`ScopeApp` 是什么**：`server.Scope = platform.SessionScope`，取值 `"app"` / `"admin"`（`platform/identity.go:92-95`）。但 `RevocableAuthMiddleware` 的签名是 `func(..., _ Scope)`——**第三个参数是下划线丢弃的**（`revocable_auth.go:154`）。全仓查证：`Scope` 只在 `session/service.go:135` 的 `sess.Scope != scope` 里被比较，而那条路径（`session.Service.Authenticate`）**在生产代码中无调用者**（grep 仅有 `schoolauth` 的同名方法）。结论：**scope 在当前 HTTP 链路上不生效**，admin 会话与 app 会话在中间件层等价。

**未带票 / 票过期 / 被吊销 / 服务故障分别返回什么**：

| 情形 | HTTP | body | 位置 |
| --- | --- | --- | --- |
| 未带 `Authorization` | 401 | `{"code":"unauthorized"}` | `revocable_auth.go:163` |
| 令牌自身非法/被篡改/`exp` 已过 | 401 | `{"code":"unauthorized"}` | `revocable_auth.go:168`（`parseSession` 失败） |
| 会话不存在 / 已吊销 / 已过期 / 用户不存在 / 用户被禁用 | 401 | `{"code":"session_expired"}` | `revocable_auth.go:176-178` |
| 会话查询本身出错（DB/Redis 故障） | 503 | `{"code":"auth_unavailable"}` | `revocable_auth.go:180` |
| 超限流 | 429 | `{"code":"rate_limited"}` | `auth.go:170` |

即：**「票过期」与「票被吊销」在客户端侧无法区分**（都是 `session_expired`），而「令牌结构/签名问题」是 `unauthorized`。fail-closed 在注释里被明确承诺（L26-27、L174-176）。

### 4.4 公开路径表与实际的错位（必须让客户端知道）

`revocable_auth.go:139-153`：

```go
var publiclyAccessible = []string{
	"/healthz",
	"/identity/login/",    // 四种登录方式
	"/identity/mailcode/",
	"/identity/register",
}
func isPublicPath(path string) bool {
	for _, p := range publiclyAccessible {
		if p == path || strings.HasPrefix(path, p) { return true }
	}
	return false
}
```

实际注册的是 `r.POST("/identity/login", ...)`（`flow/handler.go:33`，**无尾斜杠**）。前缀匹配要求路径以 `/identity/login/` 开头，而 `/identity/login` 比它短一个字符，于是：

- `/identity/login` → **不公开 → 401**
- `/identity/login/admin` → 公开（以 `/identity/login/` 开头）
- `/identity/login/` → 公开，但 gin 307 重定向到 `/identity/login`，再吃 401
- `/identity/mailcode/send` → 公开
- `/identity/register` 在表里，但**没有任何路由注册它**（grep 全仓仅此一处出现），访问得 gin 默认 404

**实测三重确认**：
1. 真实中间件单测（§8.1）：`POST /identity/login` 无票 → `401 {"code":"unauthorized"}`；有票 → 200。
2. 编译后二进制实机（降级装配）：`POST /identity/login` 无票 → 401；`/identity/login/admin` 无票 → 401（因降级中间件没有公开路径表，`AuthMiddleware` 只放行 `/healthz`）。
3. 存量单测 `revocable_auth_test.go:158-196` 只测了 `/identity/login/password`，**恰好绕开了这个 bug**。

### 4.5 限流

`server.NewRateLimiter(clock, cfg.RateLimitPerMinute, time.Minute)`（`app.go:316`），默认 **60 次/分钟**（`config/config.go:94`，env `RATE_LIMIT_PER_MINUTE`）。

- 已认证按 **userID** 计数；未认证按 **`ip:<ClientIP>`** 计数（`auth.go:164-168`）。
- `/healthz` 不计入（`auth.go:159-163`）——且因为注册在 `Use` 之前，它根本不经过限流中间件（实机：limit=2 时连打 4 次仍全 200）。
- 进程内 `map[string][]time.Time`，**重启即清零、多实例不共享**。

## 5. 会话令牌语义

| 问题 | 答案 | 依据 |
| --- | --- | --- |
| App TTL | **30 天**（`DefaultAppTTL = 30 * 24 * time.Hour`） | `session/service.go:25` |
| Admin TTL | **12 小时** | `session/service.go:26` |
| 是否可配置 | **否**，`New()` 硬写进结构体，无 env 覆盖 | `session/service.go:41-49`；`config.go` 无对应项 |
| 续期方式 | **无滑动续期**。grep `ExtendSession` / `TouchSession` / `RefreshSession` / `滑动续期` 全仓零命中。令牌与 DB 会话的 `expiresAt` 在签发那一刻固定，只能重登 | 全仓 grep |
| `expiresAt` 谁决定 | `s.clock.Now().Add(ttl)`（`service.go:93`）；`loginResp.ExpiresAt = s.ExpiresAt.Unix()`（`flow/handler.go:163`） | 同左 |
| `/identity/logout` 吊销什么 | 只吊销**当前会话**（`sid` 来自中间件写入的 `CtxSessionID`）：`sessions.Revoke(ctx, sessionID)` → `UPDATE sessions SET revoked_at=now() WHERE id=$1 AND revoked_at IS NULL`，再给 Redis 打吊销标记 | `flow/service.go:144-146` → `session/service.go:189-197` → `pg_identity.go:279-283` |
| logout 是否幂等 | 是（`WHERE ... revoked_at IS NULL`，重复调用不报错） | 同上 |
| 单设备顶替 | **是，签发前先踢同 scope 全部旧会话**：`RevokeByUserScope(userID, scope)`（`UPDATE ... WHERE user_id=$1 AND scope=$2 AND revoked_at IS NULL`），然后才建新会话 | `session/service.go:80-83` → `pg_identity.go:287-296` |
| 顶替的粒度 | **按 scope 隔离**：App 登录不会踢掉 Admin 会话，反之亦然（注释 L65-66 明说） | `session/service.go:65-66,81` |
| 被顶替后旧票的表现 | 401 `{"code":"session_expired"}`（会话已 `revoked_at`，`Active()` 为 false） | `revocable_auth.go:176-178` |
| Admin 是否单设备 | **否**，`deviceID` 固定写 `"admin-web"`（`flow/service.go:133`），且 `Authenticate` 只在 `scope == ScopeApp` 时校验 device（`session/service.go:139`）；当前链路也不校验 scope（§4.3） | 同左 |
| 账号禁用是否即时 | **是**。每次请求都在 `ResolveSession` 里查 `users.ByID` 并判 `u.Status != UserActive` → `ErrSessionInactive` → 401 `session_expired`。代价是每请求多一次用户查询（无该查询的缓存） | `revocable_auth.go:224-234` |
| 但「谁来禁用」 | **本服务没有禁用端点**。`SetStatus` 只有接口定义与 PG 实现（`platform/identity.go:126`、`pg_identity.go:103`），**非测试代码无调用者** | 全仓 grep |
| 签发前的禁用拦截 | `Issue()` 也判一次，返回 `user_disabled`（403） | `session/service.go:75-78` |
| 会话标识 | 16 字节 `crypto/rand` → 32 位 hex | `session/service.go:225-231` |
| 令牌签名 | HMAC-SHA256，密钥 `cfg.AuthKey`；**为空则不签发令牌**（`tokenIssuer = nil`，`app.go:161-164`），flow 注册时传 nil → 登录必 panic 风险由装配前置条件规避 | `app.go:161-164,342` |

## 6. 与 nongyu-node-server 的逐项差异表

node-server 事实取自 `apps/nongyu-node-server/src/app.ts`、`lib/response.ts`、`lib/errors.ts`、`middlewares/auth.ts`、`lib/jwt.ts`。

| 项 | nongyu-backend-plus | nongyu-node-server | 兼容? |
| --- | --- | --- | --- |
| 成功信封 | **无**，裸业务结构体 | `{code:number, message, data}`，`ok()` 固定 `code: 0`、HTTP 200（`response.ts:9-12`） | ✗ |
| 错误信封 | `{"code":string}` 或 `{"code":string,"message":string}`，**无 `data`** | `{code:number, message:string, data}`（`response.ts:14-22`） | ✗ |
| `code` 类型 | **string** | **number** | ✗ |
| 错误码集合 | 20 个字符串（§3.3） | 30 个数字（`errors.ts:1-34`），401 段 `40101/40102/40103/40104` | ✗ 完全无交集 |
| 路径前缀 | **无** | **`/api/app/*`**、`/api/admin/*`、`/api/internal/*` | ✗ |
| 业务路径命名 | `/identity/*` + `/school/*` | `/api/app/auth/*`、`/api/app/jiaowu/*`、`/api/app/users/*`… | ✗ |
| 鉴权头 | `Authorization: Bearer <2 段 base64url>` | `Authorization: Bearer <3 段 JWT>`（HS256，`jwt.ts:70`） | 头格式同，票格式不同 |
| 令牌载荷 | `{sid, exp}`（可吊销）/ `{sub, exp}`（降级） | `{uid, studentNo, tokenVersion, deviceId}`，`typ` 区分 app/admin（`jwt.ts:97,121`） | ✗ |
| 未带票 | 401 `{"code":"unauthorized"}` | 401 `{"code":40101,"message":"未认证","data":null}` | ✗ |
| 票过期 | 401 `{"code":"session_expired"}` | 401 `40103 TOKEN_EXPIRED` | ✗（且 backend-plus 无法区分过期/吊销） |
| 票被吊销 | 401 `{"code":"session_expired"}`（与过期同） | 401 `40104 TOKEN_REVOKED`（与过期不同） | ✗ |
| 账号禁用 | 401 `{"code":"session_expired"}` | 403 `40301 ACCOUNT_DISABLED` | ✗ **状态码都不同** |
| 限流 | 429 `{"code":"rate_limited"}` | 429 `42910/42911`（LLM 专用） | ✗ |
| 未知路径 | `text/plain` `404 page not found` | JSON 信封（经 `errorHandler`） | ✗ |
| 401 的触发位置 | 全局中间件 | 每个路由显式挂 `requireAppAuth`（`app.ts:75` 是唯一在 `app.use` 上挂的） | 语义同、位置不同 |
| 角色判定 | 无（`RequireRole` 只在 admin 登录用一次） | 每请求读库 role（`auth.ts:84`） | ✗ |
| 单设备 | 是，签发时踢同 scope 旧会话 | 是，`token_version` + `current_device_id` 双校验（`auth.ts:33-38`） | 机制不同 |
| TTL | App 30 天 / Admin 12 小时，硬编码 | `signAppToken` 的 `expiresIn`（另配） | 需逐项核对 |
| 会话续期 | 无 | 无（靠重登） | ≈ |
| `expiresAt` 格式 | `/identity/login` = Unix 秒 int64；`/school/login` = RFC3339 string | 登录响应**不含 `expiresAt`** | ✗ |
| 用户对象 | `{id, studentNo, name, college, major, className, grade}`（7 字段） | `{id, studentNo, name, major, college, className, grade, gender, hometown, campus, qq, role, createdAt}`（13 字段，`users/mapper.ts:23-39`） | ✗ 缺 6 字段 |
| 请求 id | 响应头 `X-Request-Id`（复用入参或 8 字节 hex），`/healthz` 除外 | `requestIdMiddleware` | 都靠 header，键名一致 |

## 7. 覆盖度缺口表（对照 node-server 的 14 个 `/api/app/*` 挂载点）

node-server 挂载清单：`apps/nongyu-node-server/src/app.ts:62-75`（连续 14 行）。

| # | node-server 挂载点（行号） | 组内端点 | backend-plus 对应 | 判定 |
| --- | --- | --- | --- | --- |
| 1 | `/api/app/auth`（:62） | `POST /login`、`POST /logout`、`GET /me` | `POST /identity/login`、`POST /identity/logout`、`GET /identity/me` | **部分有**：3 条名义齐全，但①`/identity/login` 实际 401（§4.4）②`/me` 无 DB 时不注册③响应形状/令牌格式不兼容④`/identity/me` 不含 token 字段 |
| 2 | `/api/app/jiaowu`（:63） | `GET/POST/DELETE /hosted`、`POST /hosted/login`、`POST /hosted/invalidate` | 无。backend-plus 的 `/school/*` 是**地址编码/换票**语义，不保管用户教务账密 | **完全没有** |
| 3 | `/api/app/users/me/posts`（:64） | `GET /` | 无 | **完全没有** |
| 4 | `/api/app/users/me/post-replies`（:65） | `GET /new`、`/received`、`/sent` | 无 | **完全没有** |
| 5 | `/api/app/users`（:66） | `GET /me`、`PATCH /me` | `GET /identity/me`、`PATCH /identity/me/profile` | **部分有**：①路径不同②`PATCH` 只收 `qq`/`avatarUrl` 两个指针字段（`user/handler.go:70-75`），node 版只收 `qq`③响应 `profileResp` 比 node 的 13 字段少 6 个（无 `role`/`gender`/`hometown`/`campus`/`createdAt`），多 `avatarUrl`/`idCardSuffix4` |
| 6 | `/api/app/settings`（:67） | `GET /`、`PUT /` | 无 | **完全没有** |
| 7 | `/api/app/course-ext`（:68） | `schedules`/`notes`/`todos`/`attendances` 各 4 条 + `GET /tombstones` = 17 条 | 无 | **完全没有** |
| 8 | `/api/app/course-share`（:69） | `GET /me`、`PUT /me`、`GET /by-student/:studentNo` | 无 | **完全没有** |
| 9 | `/api/app/posts`（:70） | `GET /`、`GET /announcements/latest`、`GET /:id`、`POST /:id/comments`、`DELETE /:id/comments/:commentId`、`POST /`、`DELETE /:id` | 无 | **完全没有** |
| 10 | `/api/app/versions`（:71） | `GET /check` | 无 | **完全没有** |
| 11 | `/api/app/home/greeting`（:72） | `GET /` | 无 | **完全没有** |
| 12 | `/api/app/agent/chat-suggestions`（:73） | `GET /` | 无 | **完全没有** |
| 13 | `/api/app/track/sample-rate`（:74） | `GET /` | 无 | **完全没有** |
| 14 | `/api/app/llm/v1`（:75） | `POST /chat/completions`（唯一在 `app.use` 上挂 `requireAppAuth` 的组） | 无 | **完全没有** |

**统计：完全没有 12 组 / 部分有 2 组（#1、#5）/ 完全有 0 组。**

**backend-plus 有、node-server 没有的路由**（反向缺口，供规格决定是否新增）：

- `GET /school/address`、`GET /school/fetch`、`GET /school/captcha`、`POST /school/login`、`DELETE /school/logout`、`POST /school/login/accel`（WebVPN 地址编码与换票，2+3+1=6 条）
- `POST /school/presence`、`GET /school/presence`（校内/校外判定）
- `GET /healthz`（node 版是 `GET /health`，路径差一个字母）

即：**一期若只对 backend-plus，现在能发出去的只有「登录拿到 token」+「读自己画像」两件事，且登录还需先绕过 §4.4 的 401 问题。**

## 8. 待核实清单 + 引用

### 8.1 已实机复核（非纯读码）

复核手段（**未修改任何仓库文件**）：`go build -o /tmp/nysrv.exe ./cmd/server` + `curl` 探针；中间件行为用 `go test -overlay=` 注入临时测试文件（文件在系统 temp，不改仓库）。

| 事实 | 复核方式 | 结果 |
| --- | --- | --- |
| 降级装配（无 `DATABASE_URL`）下 `/identity/me` 不注册 | 实机 curl | `404 page not found`，`Content-Type: text/plain` |
| `/identity/login` 在可吊销中间件下 401 | 真实中间件 + overlay 单测 | 无票 401 `{"code":"unauthorized"}`；有票 200 |
| `/identity/login/admin`、`/identity/mailcode/send` 免票 | 同上 | 无票 200 |
| `/identity/login/`（尾斜杠） | 同上 | 307 重定向（gin `RedirectTrailingSlash`） |
| 未带票各端点 | 实机 curl | 全部 401 `{"code":"unauthorized"}` |
| `/healthz` 不经 RequestID/限流 | 实机 curl（limit=2 连打 4 次） | 全 200，响应头**无** `X-Request-Id` |
| 429 形状 | 实机（`RATE_LIMIT_PER_MINUTE=2`） | 第 3 次起 `429 {"code":"rate_limited"}` |
| 地址成功形状 | 实机（降级装配合法 token） | `{"target":"jiaowu","address":"https://webvpn.sicau.edu.cn/https/777264.../"}` |
| `target_denied` 形状 | 实机 `?target=http://evil.com` | `400 {"code":"target_denied","message":"target_denied: 未知逻辑目标名 ..."}` |
| presence 两种形状 | 实机 POST/GET | `{"userID":"1","mode":"vpn"}` / `{"mode":"vpn","source":"client"}` |
| `invalid_presence` | 实机 `{"mode":"bogus"}` | `400 {"code":"invalid_presence","message":"presence: 非法 mode（只允许 direct / vpn）"}` |
| `school/login` 响应 `expiresAt` 是 RFC3339 | 实机 | `{"ticket":"","expiresAt":"2026-11-18T13:38:33.6860073+08:00"}` |
| 大小写/空格/query 传票 | 实机 | 三种全部 401 `{"code":"unauthorized"}` |
| `/api/app/*` 前缀不存在 | 实机 `POST /api/app/auth/login` | 401（被中间件先拦，说明无此路由） |

### 8.2 未能核实（需后续票）

1. **`DATABASE_URL` 分支下的真实响应从未观测过。** 本机 Postgres 18 在跑，但 `postgres` 用户口令未知（试过 `postgres/root/123456/admin/nongyu/1234/password` 全失败），因此所有 `/identity/me`、`/identity/users/:studentNo`、登录成功响应、`session_expired` 401、`auth_unavailable` 503 的**实机形状均未验证**，仅为读码结论。`internal/server/revocable_auth_test.go:199` 那段真实存储测试也因此 `t.Skip`。
2. **`auth_unavailable` (503) 的触发条件**：`ResolveSession` 返回非 `ErrSessionInactive` 错误，即 `sessionByID`/`ByID` 的**非 NotFound** 错误（§`revocable_auth.go:218-220,228-230`）。具体哪些驱动错误会走到这里未实测。
3. **`conflict` (409) 是否有真实路径**：常量在，消费在 `flow/handler.go:193`，但生产代码无产出点。是否留待后续登录方式绑定时启用，代码里无注释说明。
4. **`campus_unreachable` 是死码**：`targets.go:47` 定义，全仓非测试无处产出。原意（是否该校出站不可达时映射）未在注释里写。
5. **`internal/app/schoolauth_bridge.go` 的 `Authenticate`** 与 `server` 的同名方法不同源，未展开其返回码映射；若 `school/*` 之外的路径将来复用它，需重新核对错误码。
6. **管理台端点**：`/identity/users/:studentNo` 注释写「需管理员，鉴权由装配层中间件保证」（`user/handler.go:114`），但装配层 `app.go:319` 只挂了 `RevocableAuthMiddleware(..., ScopeApp)`，**没有任何角色判定**。实机无法验证（无 DB）。这是**最需要优先确认的契约缺口**：该端点当前是否等价于「任何登录用户可查任意学号画像」。
7. **`TokenVerifier` 的 `exp` 与 DB `expires_at` 谁先生效**：令牌 `exp` 与 `sess.ExpiresAt` 在 `IssueSession(s.SessionID, s.ExpiresAt)` 处取同一值（`flow/handler.go:162`），应同时到期；但两者一旦因时钟源不同漂移，谁先 401 未实测（`auth.go:76` 用注入 `clock`，PG 用 `now()`）。
8. **限流的多实例语义**：进程内 map，多副本部署时实际配额 = N × 配置值。部署形态未知。

### 8.3 引用汇总（文件:行号）

backend-plus：

- 装配与中间件顺序：`internal/app/app.go:129-133`（RequestID）、`:314-325`（鉴权/限流）、`:331-335`（jiaowu `RegisterAccel`）、`:339-347`（identity 条件注册）、`:365-371`（presence）
- 存活探针：`internal/server/server.go:13-20`
- 鉴权：`internal/server/auth.go:39-50`（claims）、`:45-50`（Issue）、`:59-80`（Verify）、`:90-110`（AuthMiddleware）、`:157-175`（限流）
- 可吊销鉴权：`internal/server/revocable_auth.go:96-107`（sessionClaims）、`:111-132`（parseSession）、`:139-153`（公开路径表）、`:154-190`（中间件）、`:213-241`（ResolveSession）、`:92-94`（Scope 别名）
- 身份错误码：`internal/identity/identity.go:33-54`、`:82-88`
- 登录编排：`internal/identity/flow/handler.go:32-37`（Register）、`:39-62`（loginReq）、`:64-80`（loginResp）、`:178-197`（错误映射）
- 用户：`internal/identity/user/handler.go:28-32`（Register）、`:38-49`（profileResp）、`:51-136`（三个 handler）
- 会话：`internal/identity/session/service.go:24-27`（TTL）、`:67-109`（Issue）、`:124-162`（Authenticate）、`:189-197`（Revoke）
- 教务：`internal/school/jiaowu/handler.go:22-41`（三套 Register）、`:44-53`（两个响应结构）、`:103-121`（writeError）
- 教务结果类型：`internal/school/jiaowu/service.go:102-105,150-155,206-215,384-387`
- 错误码来源：`internal/school/targets.go:44-50`、`internal/school/jiaowu/gateway.go:18-32`
- presence：`internal/school/presence/handler.go:24-34,43-58,98-100`、`internal/school/presence/presence.go:35-37,173-195`
- 配置默认值：`internal/config/config.go:88-118`、`:130-140`（prod 强校验）
- TGT 保管：`internal/school/schoolauth/tgt.go:54-55,74-75`（默认 9 天）
- 观测窗口：`internal/school/jiaowu/scheduler.go:21`（`observationWindow = 9 * 24h`）
- 无入口声明：`internal/identity/login/handler.go:1-9`、`internal/identity/session/handler.go:1-9`

nongyu-node-server：

- 挂载点：`src/app.ts:62-75`
- 信封：`src/lib/response.ts:9-12`（ok）、`:14-22`（fail）
- 错误码：`src/lib/errors.ts:1-34`
- 鉴权：`src/middlewares/auth.ts:8-14`（extractBearer）、`:16-40`（requireAppAuth）
- 令牌载荷：`src/lib/jwt.ts:7-11,97,121`
- 用户 DTO：`src/modules/users/mapper.ts:23-39`
