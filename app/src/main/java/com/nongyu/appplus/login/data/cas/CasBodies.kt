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

/** `POST /api/v1/sms/send` 请求体；`type` 固定 `"login"`。 */
@Serializable
data class CasSmsSendBody(
    val uuid: String,
    val code: String,
    val type: String = "login",
    val phone: String,
)

/** `POST /api/v1/cas/loginByPhone` 请求体。 */
@Serializable
data class CasPhoneLoginBody(
    val code: String,
    val phone: String,
    val appid: String,
)
