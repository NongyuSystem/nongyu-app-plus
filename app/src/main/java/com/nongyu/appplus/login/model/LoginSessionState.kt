package com.nongyu.appplus.login.model

import com.nongyu.appplus.login.model.VerificationResult

/**
 * 根节点渲染去向。只有两种：主界面或登录中心 —— **没有「检查中」态**（票 #90）。
 */
enum class LoginDestination {
    MAIN,
    LOGIN_CENTER,
}

/**
 * 登录会话状态（票 #90 单一主接缝，纯 JVM）。
 *
 * ## 为什么状态里没有「检查中」
 * 规格 #89 / Q13′/Q24=A：冷启动首帧直接落地，校验请求只在首帧之后由后台发起、
 * 事后纠正。若存在「检查中」态，首帧就会渲染一块中间界面（纯色底 / 加载指示），
 * 那正是被否掉的形态。所以初始态只由「本地有没有 App 会话凭证」二选一决定。
 *
 * ## 事后纠正
 * [onVerified] 只处理 [VerificationResult.SESSION_ENDED]（切回登录中心）；
 * [VerificationResult.VALID] 与 [VerificationResult.INDETERMINATE] 都**不动状态**，
 * 对应「乐观放行、全程零提示」。清凭证由既有失效分流负责，不在本状态里重复表达。
 */
data class LoginSessionState(
    val destination: LoginDestination,
) {
    companion object {
        /** 首帧落地：有 App 会话凭证进主界面，无凭证进登录中心。 */
        fun initial(hasCredential: Boolean): LoginSessionState =
            LoginSessionState(
                destination = if (hasCredential) LoginDestination.MAIN else LoginDestination.LOGIN_CENTER,
            )
    }

    /**
     * 首帧之后的校验结果回来时调用。
     *
     * SESSION_ENDED → 切回登录中心（清凭证由上层走既有失效分流）；
     * 其余结果 → 原样返回，乐观放行、零提示。
     */
    fun onVerified(result: VerificationResult): LoginSessionState =
        when (result) {
            VerificationResult.SESSION_ENDED ->
                copy(destination = LoginDestination.LOGIN_CENTER)

            VerificationResult.VALID,
            VerificationResult.INDETERMINATE,
            -> this
        }

    /** 登录成功后进主界面（供后续实施票复用）。 */
    fun onSignedIn(): LoginSessionState =
        copy(destination = LoginDestination.MAIN)
}
