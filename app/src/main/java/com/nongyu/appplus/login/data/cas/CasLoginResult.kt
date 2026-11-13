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

/** 取微信登录二维码的结果。 */
sealed interface CasQrFetchResult {
    data class Success(val qr: CasLoginQr) : CasQrFetchResult
    /** 文案为空串表示零提示（弱网/解析失败）。 */
    data class Failure(val message: String) : CasQrFetchResult
}

/** 轮询扫码结果。 */
sealed interface CasQrPollResult {
    /** 尚未扫码（服务端返回 code=200 但解密结果全空）。 */
    data object Pending : CasQrPollResult
    /** 已扫码，拿到 tgt 与整套 cookie。 */
    data class Scanned(val credential: CasCredential) : CasQrPollResult
    data class Failure(val message: String) : CasQrPollResult
}
