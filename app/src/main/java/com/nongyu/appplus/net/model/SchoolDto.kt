package com.nongyu.appplus.net.model

import kotlinx.serialization.Serializable

/**
 * school 与 presence 域的 DTO（规格 §3.4）。
 *
 * ⚠️ **一期不构造这些端点对应的 interface 实例**（`#46` 证明 school 域语义与 RN 版完全不同，
 * 且「客户端直连学校」是 out of scope 的独立平面）。DTO 与 interface 先定义好，
 * 等 future effort 落地时直接可用。
 */

/** `GET /school/address`。 */
@Serializable
data class AddressDto(
    val target: String? = null,
    val address: String? = null,
)

/** `GET /school/captcha`。 */
@Serializable
data class CaptchaDto(
    val uuid: String? = null,
    /** base64 编码的图片。 */
    val image: String? = null,
)

/**
 * `POST /school/login` 的成功响应。
 *
 * ⚠️ **`expiresAt` 在本 DTO 是 RFC3339 字符串**，而 [LoginDto] 的同名字段是 Unix 秒 `Long`。
 * **同名不同型，绝不能合并成一个 DTO。**（`#46` 实测：`{"ticket":"","expiresAt":"2026-11-18T13:38:33.6860073+08:00"}`）
 */
@Serializable
data class SchoolLoginDto(
    val ticket: String? = null,
    /** ⚠️ RFC3339 字符串，不是 Unix 秒。 */
    val expiresAt: String? = null,
)

/** `POST /school/login/accel`。 */
@Serializable
data class AccelDto(
    val needCaptcha: Boolean? = null,
    val code: String? = null,
)

/**
 * `DELETE /school/logout` —— 返回 `{"status":"ok"}`。
 *
 * ⚠️ **键名是 `status`，而 identity 的 logout 是 `ok`**。不要复用同一个 DTO。
 */
@Serializable
data class SchoolLogoutAckDto(
    val status: String? = null,
)

/**
 * `POST /school/presence` 的成功响应。
 *
 * ⚠️ 键名是 **`userID`**（大写 ID），不是 `userId`。照抄后端，不加 `@SerialName`。
 */
@Serializable
data class PresenceReportDto(
    @Suppress("PropertyName")
    val userID: String? = null,
    val mode: String? = null,
)

/** `GET /school/presence`。 */
@Serializable
data class PresenceDecideDto(
    val mode: String? = null,
    val source: String? = null,
)
