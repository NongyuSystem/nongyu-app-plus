package com.nongyu.appplus.net.model

import kotlinx.serialization.Serializable

/**
 * identity 域的 DTO（规格 §3.4）。
 *
 * ## 四条硬规则（每条都来自 `#46` 的读码结论）
 * 1. **全部字段可空 + `= null` 默认值**。
 * 2. **客户端不区分「缺字段」与「显式 null」**，二者一律等价为 `null`。
 *    （Go 的 `omitempty` / 显式 null / 零值 `""` `0` 在 Kotlin 侧统一成可空类型。）
 * 3. **字段名照抄后端 camelCase**，**不加 `@SerialName`**。
 * 4. `Json` 配置固定 `ignoreUnknownKeys = true` + `explicitNulls = false`。
 */

/** `GET /healthz` —— 公开。注意它**有 JSON body**（`{"status":"ok"}`），不是真空响应。 */
@Serializable
data class HealthzDto(
    val status: String? = null,
)

/**
 * `GET /status` —— 运维依赖探针（现行契约 §2.3）。
 *
 * 与 [HealthzDto] 不同：`/healthz` 只报进程可达、不探下游；`/status` **如实上报**
 * Postgres / Redis / 缓存熔断器 / 当前存储实现。状态码恒 200，真实情况放 body 里。
 *
 * 全部字段可空：后端 `StatusProbe` 未配置的探针会返回 `"unknown"`，不会缺字段崩客户端。
 * 这是**唯二免 NY1 签名**的路径之一（另一个是 `/healthz`），见 `AppSignInterceptor`。
 */
@Serializable
data class StatusDto(
    val process: String? = null,
    val postgres: String? = null,
    val redis: String? = null,
    val breaker: String? = null,
    val storage: String? = null,
)

/** `POST /identity/mailcode/send` —— 公开。 */
@Serializable
data class MailCodeAckDto(
    val sent: Boolean? = null,
)

/**
 * `POST /identity/logout` —— 返回 `{"ok":true}`。
 *
 * ⚠️ **不要与 [SchoolLogoutAckDto] 合并**：identity 的键是 `ok`，school 的是 `status`。
 */
@Serializable
data class LogoutAckDto(
    val ok: Boolean? = null,
)

/**
 * `POST /identity/login/admin` 的成功响应。
 *
 * ⚠️ **`expiresAt` 在本 DTO 是 Unix 秒 int64**。`/school/login` 的同名字段是
 * RFC3339 **字符串** —— 同名不同型，必须两个 DTO，绝不能合并。
 */
@Serializable
data class LoginDto(
    val token: String? = null,
    val expiresAt: Long? = null,
    val isNew: Boolean? = null,
    /** 专业/班级/年级暂缺（教务源没抓到）时为真；登录本身成功。 */
    val profileIncomplete: Boolean? = null,
    val user: UserDto? = null,
)

/** `LoginDto.user`。字段来自后端 `loginResp.User`。 */
@Serializable
data class UserDto(
    val id: Long? = null,
    val studentNo: String? = null,
    val name: String? = null,
    val college: String? = null,
    val major: String? = null,
    val className: String? = null,
    val grade: String? = null,
)

/**
 * `POST /identity/login` 的请求体（现行契约 `docs/后端接口与NY1签名对接契约.md` §3.1）。
 *
 * ## 四种登录方式
 * `method` 取 `school` / `password` / `mailcode` / `idcard`。
 * **只有 `school` 能注册新账号**；其余三种账号不存在时后端回 400 `need_school_auth`，
 * 引导先走 `school`。
 *
 * ## 一期的主力字段：`goAdminTgt`
 * `school` 方式客户端只做一次统一登录、交一张 `goAdminTgt`（55 字符的统一身份认证
 * 全局登录态 cookie）即可，后端全权完成后续换票与 13 组档案取数。
 * `xsgzToken` 不传也行 —— 后端会自己用 tgt 换。
 *
 * 字段全部可空 + 默认 `null`：调用方只填本次方式需要的字段即可。
 * 不要给任何字段写非空类型，否则四种方式无法共用同一个 DTO。
 */
@Serializable
data class LoginRequestDto(
    val method: String? = null,
    val deviceId: String? = null,
    /** 统一身份认证的全局登录态 cookie；school 方式必填。 */
    val goAdminTgt: String? = null,
    /** 学工系统 access token；可选，后端会自己用 tgt 换。 */
    val xsgzToken: String? = null,
    val schoolMethod: String? = null,
    val studentNo: String? = null,
    val password: String? = null,
    val phone: String? = null,
    val code: String? = null,
    val wechatCode: String? = null,
    val email: String? = null,
    val ticket: String? = null,
    val cookie: String? = null,
    /** idcard 方式：身份证后四位。 */
    val idCardSuffix4: String? = null,
    /** idcard 方式：姓名首字母。 */
    val nameInitials: String? = null,
)

/**
 * `POST /identity/logout/all` 的响应：`{"ok":true,"revoked":n}`。
 *
 * 后端 `logoutAllHandler` 吊销该用户 **App 域**全部会话，`revoked` 是被吊销的会话数。
 * 与 `/identity/logout`（只吊销当前会话、`{"ok":true}`）不同，别复用 [LogoutAckDto]。
 */
@Serializable
data class LogoutAllAckDto(
    val ok: Boolean? = null,
    val revoked: Int? = null,
)

/**
 * `/identity/me` 一族（`GET /identity/me`、`PATCH /identity/me/profile`、`GET /identity/users/:studentNo`）。
 *
 * ⚠️ 两个陷阱（都来自 `#46` 读码）：
 * 1. **`studentNo` 在 `/identity/me` 上可能不存在**（`omitempty` + 构造处不填）。
 *    `explicitNulls = false` 只影响序列化、**不影响反序列化**，所以这里靠的是「字段可空」——
 *    不要写成非空类型。
 * 2. **`idCardSuffix4` 是死字段**：声明了但三个 handler 无一填充。保留（后端可能补），
 *    但**不要在任何逻辑里依赖它**。
 */
@Serializable
data class ProfileDto(
    val studentNo: String? = null,
    val name: String? = null,
    val college: String? = null,
    val major: String? = null,
    val className: String? = null,
    val grade: String? = null,
    val qq: String? = null,
    val avatarUrl: String? = null,
    /** ⚠️ 死字段：后端无产出点。保留但不依赖。 */
    val idCardSuffix4: String? = null,
)

/**
 * 所有非 2xx 的错误信封。
 *
 * 两种形状并存（`#46`）：
 * - `{"code":"...","message":"..."}`（school 域、部分 handler）
 * - `{"code":"..."}`（identity 域、中间件，**连 message 都没有**）
 *
 * 所以两个字段都必须可空。
 */
@Serializable
data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
)
