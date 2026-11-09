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
