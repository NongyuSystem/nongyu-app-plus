# 农屿 Android 客户端 ↔ Go 后端 对接契约（现行）

> 更新：2026-11-12。基于 `nongyu-backend-plus` 的 `main`（`7c7eb49` 之后）与
> `nongyu-app-plus` 的 `001807e`（NY1 签名）。
>
> **本文是当前唯一权威快照。** `docs/research/backend-plus-app-contract.md`
> 是 2026-11-09 基于 `bb74cc7` 的历史快照，其中「16 条路由」「`/identity/login`
> 返回 401 是 bug」「没有请求签名」等结论**均已过时**，保留仅作历史，不要照它写代码。

---

## 0. 一句话给客户端

**除了 `/healthz` 和 `/status`，每一个请求都要带 NY1 签名头；登录成功后带
`Authorization: Bearer <token>`。缺签名一律 401，缺令牌看具体端点。签名不替代令牌，
两者都要。**

---

## 1. 请求签名（NY1）—— 必读

### 1.1 为什么有这一层、为什么不加 JWT

- 目标：让 curl / 脚本无法仿造请求。
- **不加 JWT**：用户身份已由服务端既有的 **HMAC 会话令牌**（`Authorization: Bearer`）
  负责，它是「JWT 类似的方法」，再叠一层是重复。见后端 `docs/adr/0003-安卓请求签名.md`。
- 签名只证明「请求来自持有当前密钥的客户端」，**不证明用户是谁**。
- 主力机型无 GMS，Play Integrity / Key Attestation 不成立；密钥会进 APK、可被提取。
  设计目标是**可轮换、可吊销**（删掉 kid），不是「密钥不可提取」。字符串混淆不算保管。

### 1.2 四个请求头

| Header | 值 |
| --- | --- |
| `X-Nongyu-Key-Id` | 密钥 ID（`kid`），如 `dev` |
| `X-Nongyu-Timestamp` | Unix 秒（字符串） |
| `X-Nongyu-Nonce` | 随机串，base64url 无 padding，16 字节随机 |
| `X-Nongyu-Signature` | `base64url(HMAC-SHA256(key, canonical))`，无 padding |

### 1.3 规范化串（逐字节，两端必须完全一致）

```
NY1\n
<keyId>\n
<unixSeconds>\n
<nonce>\n
<METHOD 大写>\n
<escapedPath>\n
<rawQuery>\n
<lowercase-hex SHA-256(body)>\n
```

**最后一个字段后面也有 `\n`**（两端都有，别漏）。

- `escapedPath`：客户端 `request.url.encodedPath`（OkHttp）↔ 后端
  `c.Request.URL.EscapedPath()`（Go）。**是转义后的路径**，不是 raw path。
- `rawQuery`：客户端 `request.url.encodedQuery ?: ""` ↔ 后端 `URL.RawQuery`。无 query 时为空串。
- `body`：请求体**原始字节**的 SHA-256（小写 hex）。GET 无 body 时用空字节数组。
- `METHOD`：`GET`/`POST`/`PATCH`/`DELETE` 全大写。

实现位置：
- 客户端 `app/src/main/java/com/nongyu/appplus/net/sign/AppRequestSigner.kt`
  （`canonical()`）与 `net/http/AppSignInterceptor.kt`（挂头）
- 后端 `internal/server/appsign.go`（`canonicalAppSign()` / `verifyAppSign()`）

### 1.4 校验规则与失败响应

| 规则 | 值 |
| --- | --- |
| 时间窗 | ±5 分钟（`appSignWindow`），客户端服务端必须用同一个数 |
| nonce | **只可用一次**，TTL = 2×窗口 + 30s；重放即拒 |
| nonce 存储 | 有 Redis 走 Redis 原子 `SET NX`（多实例共享）；无 Redis 退化进程内存并告警 |
| body 上限 | 1 MiB，超限 413 |
| 豁免路径 | **仅** `/healthz`、`/status`（给探针留门） |
| 中间件顺序 | 签名在**用户鉴权之前**；登录、发验证码这些公开路径**也要签名** |

失败响应形状统一为 `{"code":"..."}`（无 `message`）：

| HTTP | code | 触发 |
| --- | --- | --- |
| 401 | `app_sign_invalid` | 缺头 / 头格式错 / 未知 kid / 时间戳越窗 / 签名不匹配 / nonce 重放 / body 不可读 |
| 413 | `app_sign_too_large` | body > 1 MiB |
| 503 | `app_sign_unavailable` | nonce 存储不可用（**fail-closed，不放行**） |

### 1.5 密钥配置与轮换

- 后端：`APP_SIGNING_KEYS=kid:base64url[,kid2:base64url...]`。
  **prod 缺密钥拒绝启动**（不接受「接口可仿造」的形态）。
- 客户端：gradle 属性 `appSigningKey` / `appSigningKeyId` 注入；不注入时 debug/测试包
  用同一把开发密钥（`AppRequestSigner.DEV_KEY_ID` = `dev`，
  `DEV_KEY_B64` = `bm9uZ3l1LWRldi1hcHAtc2lnbi1rZXktdjEtMzJiISE`）。
- **开发密钥不是秘密**，两端一致。生产密钥不要提交进仓库。
- 轮换：后端加新 kid → 客户端发新 kid → 删旧 kid 即吊销。
- 换协议必须改版本串（`NY1` → `NY2`）并同时改两端向量测试。

---

## 2. 接口清单（现行 main）

### 2.1 身份域 `/identity`

| 方法 | 路径 | 说明 | 令牌 |
| --- | --- | --- | --- |
| POST | `/identity/login` | 四种登录：`school` / `password` / `mailcode` / `idcard` | 公开（仍要签名） |
| POST | `/identity/login/admin` | 管理台登录，额外要求 `role >= 1` | 公开 |
| POST | `/identity/logout` | 吊销当前会话 | Bearer |
| POST | `/identity/logout/all` | 吊销该用户 App 域全部会话 | Bearer |
| POST | `/identity/mailcode/send` | 发送邮箱验证码 | 公开 |
| GET | `/identity/me` | 查自己画像 | Bearer |
| PATCH | `/identity/me/profile` | 补充扩展字段（昵称等，不被学校数据覆盖） | Bearer |
| GET | `/identity/users/:studentNo` | 管理员按学号查公开画像（不含身份证/家庭住址） | Bearer |

### 2.2 学校域 `/school`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/school/captcha` | 取登录验证码 |
| POST | `/school/login` | 学校登录 |
| POST | `/school/login/accel` | 加速登录 |
| DELETE | `/school/logout` | 学校登出 |
| GET | `/school/address` | 站点地址 |
| GET | `/school/fetch` | 代抓任意教务页面（SSRF 防护、不跟随 3xx、白名单） |
| GET/POST/DELETE | `/school/cache/:dataType` | 回传缓存：查 / 提交 / 失效 |
| POST/GET | `/school/presence` | 在场状态上报与判定 |

### 2.3 运维

| 方法 | 路径 | 签名 | 说明 |
| --- | --- | --- | --- |
| GET | `/healthz` | **豁免** | 进程存活 |
| GET | `/status` | **豁免** | 真实依赖探针（PG / Redis / 装配能力如实上报） |

> 注意：`/healthz`、`/status` 是**唯二**免签名的路径。别把「公开」和「免签名」
> 混为一谈——登录端点公开但仍要签名。

---

## 3. 登录：`school` 方式要传什么

### 3.1 请求体

`POST /identity/login`，`method: "school"`：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `method` | ✅ | `"school"` |
| `deviceId` | 建议 | 单设备登录依据 |
| `goAdminTgt` | ✅ | 统一身份认证登录后拿到的全局登录态 cookie（55 字符） |
| `xsgzToken` | ❌ | 学工系统 access token；**不传也行，后端会自己用 tgt 换** |
| `schoolMethod` / `studentNo` / `password` / `phone` / `code` / `wechatCode` | 视方式 | 学校侧认证方式 |
| `cookie` | 可选 | 整套同域 cookie |

**客户端只做一次统一登录，交一张 `goAdminTgt` 即可。** 这是「一次登录、后端全权」
的落点。

### 3.2 响应

```json
{
  "token": "<session_id 的自包含令牌>",
  "expiresAt": 1234567890,
  "isNew": true,
  "profileIncomplete": false,
  "user": { "id": 1, "studentNo": "...", "name": "...",
            "college": "...", "major": "...", "className": "...", "grade": "..." }
}
```

`expiresAt` 是 **Unix 秒整数**（注意 `/school/login` 的同名 `expiresAt` 是 RFC3339
字符串，两者不同型）。

### 3.3 会话语义

| 项 | 值 |
| --- | --- |
| App TTL | 30 天，**不滑动续期**，过期只能重登 |
| 管理台 TTL | 12 小时 |
| 单设备 | 是，签发前踢掉同 scope 旧会话 |
| 令牌载荷 | 可吊销模式只带 `{"sid","exp"}`；靠 `sid` 回查会话 |
| 鉴权头 | `Authorization: Bearer <token>`，**大小写敏感、单个空格** |
| 401 码 | `unauthorized`（令牌非法）/ `session_expired`（会话失效/被吊销/账号禁用） |

### 3.4 另外三种登录方式

`password`（农屿账密）、`mailcode`（邮箱验证码）、`idcard`（学号 + 身份证后四位 +
姓名首字母）——**都不能建号**，账号不存在时返回 400 `need_school_auth`，引导先走
`school` 登录。只有 `school` 能注册。

---

## 4. 后端取数链路（客户端不必实现，但要理解）

**唯一数据源是学工系统 `xsgz.sicau.edu.cn`**。客户端交 `goAdminTgt` 后，后端全自动：

```
tgt
  → auth /api/v1/cas/login?appid=ywtb      （换 st + userId）
  → xsgz /gateway/xg-agg-pc/cas/login      （st:userId 换 access token）
  → user/checkUserAndGetUserLevel          （拿 level / 学院号）
  → user/enterHall                        ★ 缺这步 detail 必 500
  → student/detail/<学号>                  13 组档案
  → 落库 4 张表
```

### 4.1 关键坑（曾误导排查很久）

- **拿到 token 直接打 `student/detail` 会返回 500「验证权限异常」**，必须先
  `enterHall`。同一个 token 走完 enterHall 即 200。
- 之前误判为「服务端识别客户端环境 / TLS 指纹」，**已推翻**：根因就是漏了
  `enterHall`，与客户端类型无关。纯 curl 用正确 token 也能 200。
- 教务 `bjiben.asp` 的 GBK 解析、ywtb `getUserInfo` 取数**已 disable**——它们给不出
  身份证/家庭地址/紧急联系人。

### 4.2 一次拿到的 13 组数据

`baseDto`（含 18 位身份证）、`studentStatusDto`、`personalInfoDto`、
`contactInfoDto`、`emergencyContactInfoDto`、`examDto`、`familyDto`、`economyDto`、
`workUnitDto`、`familyMemberList`、`sociologyMemberList`、`resumeList`、`age`。

字段包括：学号、姓名、性别、学院、专业、班级、年级、身份证、生日、年龄、宿舍、
校区、手机、邮箱、家庭地址、紧急联系人、考生号、教育经历、家庭成员、经济情况。

### 4.3 8 个校内站点的真实性质

| 站点 | 性质 | 有无个人数据 |
| --- | --- | --- |
| jiaowu 教务 | CAS 换票 | 有 |
| ywtb 一网通办 | 自有 Bearer | 有 |
| **xsgz 学工** | SSO 网关 | **有（13 组，最全）** |
| xsc 学工部 | 资讯门户 | 无 |
| zjc 招就处 | 资讯门户 | 无 |
| cwc 财务 | 需额外授权 | 无 |
| lib 图书馆 | 资讯门户；其 opac 是独立账号体系 | opac 有但不认统一认证 |
| webvpn | VPN 网关 | 不提供数据 |
| ecology | 可达 | 未发现统一认证取数入口 |

**「认证层全通」不等于「业务数据可取」**，代码里 `SiteProfiles` 有登记并有测试锁住。

### 4.4 tgt 定时续期（后端自动，客户端无感）

- 每 5 分钟扫一轮，挑「即将过期」的候选换票；观测驱动周期（不硬编码 TTL）。
- 16 槽位错峰 + 随机抖动（`slotGap 2s` + `jitter 0-3s`），避免 TPS 尖峰/机器检测。
- 用户注册后 tgt 稳定保存在后端。
- 遗留：`xsgz` 的 access token 每次现换、未缓存；`SetTrustedProxies` 未配置，
  上反向代理后登录 IP 会记成代理地址。

---

## 5. 回传缓存子域（15 类 dataType）

`GET/POST/DELETE /school/cache/:dataType`，POST 提交时做 **JSON Schema 校验**，
命中直返、stale 回落、失效节流。

| dataType | 含义 | dataType | 含义 |
| --- | --- | --- | --- |
| `course` | 课表 | `notice` | 教学+竞赛通知（合并） |
| `score` | 成绩 | `teachingNotice` | 仅教学通知 |
| `exam` | 考试安排 | `competition` | 仅竞赛通知 |
| `progress` | 学业进度 | `classroomInfo` | 教室搜索列表 |
| `rank` | 加权排名 | `teacherInfo` | 教师信息 |
| `plan` | 培养方案 | `teacherCourse` | 教师课表 |
| `personal` | 学籍信息 | `classroomCourse` | 教室课表 |
| `kaike` | 开课目录 | | |

---

## 6. 错误码速查

| 域 | 形状 | 常见码 |
| --- | --- | --- |
| identity / 中间件 | `{"code":"..."}`（**无 message**） | `unauthorized` / `session_expired` / `bad_credentials` / `need_school_auth` / `forbidden` / `user_disabled` / `rate_limited` / `internal_error` |
| school | `{"code":"...","message":"..."}` | `target_denied` / `blocked_redirect` / `ticket_expired` |
| 签名 | `{"code":"..."}`（**无 message**） | `app_sign_invalid` / `app_sign_too_large` / `app_sign_unavailable` |

**客户端不能假定错误响应一定有 `message`。** 未匹配路径是 gin 默认
`text/plain` 的 `404 page not found`（不是 JSON）。

---

## 7. 数据落地（后端，客户端不直接碰）

9 张表：`audit_log`、`users`、`user_profiles`、`login_methods`、`sessions`、
`student_profiles`（48 列）、`student_profile_groups`（13 组 jsonb 原文）、
`student_family_members`、`student_resumes`。

迁移 `0001`–`0004`，`//go:embed`，启动时幂等执行。身份证加密存储，明文不落库。

---

## 8. 客户端仓库整理备注

- `.worktrees/timetable-step2/` 是一份**历史 worktree 副本**（含全套 `net/` 代码），
  与主 `app/src/main/...` 内容重复。**它是 git worktree，不要当普通目录删除**；
  若确认无人使用，用 `git worktree remove` 正规处理。本文不改动它。
- 主源码：`app/src/main/java/com/nongyu/appplus/net/`，签名实现在
  `sign/AppRequestSigner.kt` 与 `http/AppSignInterceptor.kt`。

---

## 9. 联调检查清单

1. 本地起后端时配 `APP_SIGNING_KEYS=dev:<与 DEV_KEY_B64 同一把>`，否则签名中间件不挂，
   联调不到验签路径。
2. 用 `AppRequestSigner.dev()` 构造 signer；确认 `AppSignInterceptor` 已挂进 OkHttp 链。
3. 打 `/healthz` 应 200 且不需要任何头；打 `/identity/login` **不带签名头应 401**。
4. 带齐 4 个签名头再打 `/identity/login`，`goAdminTgt` 填真实值，应拿到 `token`。
5. 后续请求同时带 4 个签名头 + `Authorization: Bearer <token>`。
6. 同一个 nonce 用两次，第二次应 401 `app_sign_invalid`（重放防护生效）。
7. 时间戳偏移超过 5 分钟，应 401。
