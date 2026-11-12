package com.nongyu.appplus.login.data.cas

import kotlinx.serialization.Serializable

/**
 * 统一身份认证平台 `/api/v1/` 下各端点的**公共响应信封**（票 #95）。
 *
 * 真源：后端 `internal/school/schoolauth/casdecrypt.go` 的 `ywtbEnvelope`，
 * 与旧 RN 工具包 `core/login/cas.ts` 的 `CasEnvelope` 同形。
 *
 * - `requestId`：32 位 hex，参与派生解密密钥
 * - `code`：业务码，**只有 200 才解密 data**；非 200 时 data 通常为空
 * - `msg`：统一身份认证平台错误文案（如「验证码错误」「未登录」），**原样透出**，不映射成农屿错误码
 * - `data`：加密体的 base64 字符串；`code != 200` 或 null 时不解密
 * - `id`：仅 `GET /api/v1/captcha` 返回，是图形验证码的 uuid
 *
 * 字段全部可空 + 默认值：后端偶尔缺字段，不能让反序列化炸掉（ignoreUnknownKeys 由 NetJson 管）。
 */
@Serializable
data class CasEnvelope(
    val requestId: String? = null,
    val code: Int? = null,
    val msg: String? = null,
    val data: String? = null,
    val id: String? = null,
)
