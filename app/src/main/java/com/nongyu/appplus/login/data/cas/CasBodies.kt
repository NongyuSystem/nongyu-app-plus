package com.nongyu.appplus.login.data.cas

import kotlinx.serialization.Serializable

/** `POST /api/v1/cas/login` 请求体（旧 RN 工具包形状）。 */
@Serializable
data class CasPasswordLoginBody(
    val appid: String,
    val code: String,
    val number: String,
    val password: String,
    val uuid: String,
)

/**
 * `POST /api/v1/sms/send` 请求体。
 *
 * ## `type` 必须显式传，不能只靠默认值
 * `NetJson` 没有开 `encodeDefaults`，**带默认值的字段不会被序列化**。
 * 若写成 `type: String = "login"` 且构造时不传，JSON 里就**没有 type 字段**，
 * 学校侧会回 `500 验证码错误`（实测：缺 type 时即使 uuid/code 都正确也失败）。
 * 因此这里的 `type` 不给默认值，强制调用点显式传入 [SMS_TYPE_LOGIN]。
 */
@Serializable
data class CasSmsSendBody(
    val uuid: String,
    val code: String,
    val type: String,
    val phone: String,
) {
    companion object {
        /** 登录用途的短信验证码。 */
        const val SMS_TYPE_LOGIN = "login"
    }
}

/** `POST /api/v1/cas/loginByPhone` 请求体。 */
@Serializable
data class CasPhoneLoginBody(
    val code: String,
    val phone: String,
    val appid: String,
)

/** `POST /api/v1/cas/getLoginQr` 请求体（实测只有一个 appid）。 */
@Serializable
data class CasQrAppIdBody(val appid: String)

/** `POST /api/v1/cas/getCasLoginQrRes` 请求体（实测只有一个 state）。 */
@Serializable
data class CasQrStateBody(val state: String)
