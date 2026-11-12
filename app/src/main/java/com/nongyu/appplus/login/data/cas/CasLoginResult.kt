package com.nongyu.appplus.login.data.cas

/** 图形验证码结果。 */
sealed interface CasCaptchaResult {
    data class Success(val uuid: String, val image: String) : CasCaptchaResult
    data class Failure(val message: String) : CasCaptchaResult
}

/** 登录（账密或短信）结果。失败文案是**学校侧**原文，不映射成农屿错误码。 */
sealed interface CasLoginResult {
    data class Success(val credential: CasCredential) : CasLoginResult
    data class Failure(val message: String, val needRefreshCaptcha: Boolean = false) : CasLoginResult
}
