package com.nongyu.appplus.login.data.cas

/** 一张图形验证码（uuid 与用户所看的图必须成对，验证码一次性）。 */
data class CasCaptcha(
    val uuid: String,
    val image: String,
)

/** 图形验证码结果。 */
sealed interface CasCaptchaResult {
    data class Success(val captcha: CasCaptcha) : CasCaptchaResult
    data class Failure(val message: String) : CasCaptchaResult
}

/** 短信验证码发送结果。 */
sealed interface CasSmsSendResult {
    data object Success : CasSmsSendResult
    data class Failure(val message: String, val needRefreshCaptcha: Boolean = false) : CasSmsSendResult
}

/** 登录（账密或短信）结果。失败文案是**统一身份认证平台**原文，不映射成农屿错误码。 */
sealed interface CasLoginResult {
    data class Success(val credential: CasCredential) : CasLoginResult
    /**
     * @param message 统一身份认证平台错误原文；**弱网/超时/空响应时为空串**（零提示，
     *   规格 #89 User Story 21），由 UI 据此不显示任何文案。
     */
    data class Failure(val message: String, val needRefreshCaptcha: Boolean = false) : CasLoginResult
}
