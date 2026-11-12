package com.nongyu.appplus.login.model

import com.nongyu.appplus.net.core.ApiErrorCode
import com.nongyu.appplus.net.core.ApiFailure

/**
 * 登录提交失败后的 UI 动作（票 #92，纯 JVM）。
 *
 * 与 [VerificationPolicy] 的分工：那个管「启动校验」的去向（合法/会话结束/无法判定），
 * 本类型管「主动点登录」失败后的表现（提示 / 切认证域 / 零提示）。
 */
sealed interface LoginFailureAction {
    /** 就地显示一条行内文案，不清空输入、不弹窗、不切认证域。 */
    data object SHOW_MESSAGE : LoginFailureAction

    /** 自动切到统一身份认证 tab，并显示固定引导文案。 */
    data object SWITCH_TO_CAMPUS_SSO : LoginFailureAction

    /** 零提示（乐观放行口径）：签名错、5xx、超时、无网、解析失败、未知码。 */
    data object SILENT : LoginFailureAction
}

/**
 * 平台登录账密提交失败的**纯决策**（票 #92 单一主接缝的纯 JVM 部分）。
 *
 * ## 判定口径（规格 #89 文案表 / 票 #92 验收判据）
 * - `need_school_auth` → [LoginFailureAction.SWITCH_TO_CAMPUS_SSO]；
 * - [MESSAGES] 表内的业务码 → [LoginFailureAction.SHOW_MESSAGE] + 对应固定文案；
 * - **其余一切**（5xx、`app_sign_*`、超时、无网、解析失败、取消、未知码）
 *   → [LoginFailureAction.SILENT]，零提示。
 *
 * ## 单一真源
 * [MESSAGES] 是「哪些码要出声 + 出什么声」的唯一表；[MESSAGE_CODES] 由它的 keys 派生，
 * 不存在两处各列一遍的重复（code-review 两轴都点过这个 smell）。
 *
 * ## 为什么独立成纯函数
 * 文案映射与「哪类失败该出声」是易错且会反复改的策略，必须能纯 JVM 测；
 * 网络层 [ApiCall] 只负责把失败分类成 [ApiFailure]，不掺 UI 决策。
 */
object LoginFailurePolicy {

    /** 「业务码 → 就地行内文案」唯一映射表。文案来自规格 #89 的对照表，是独立真源。 */
    private val MESSAGES: Map<String, String> = mapOf(
        ApiErrorCode.BAD_CREDENTIALS to "账号或密码不正确，请重试",
        ApiErrorCode.TOO_MANY_ATTEMPTS to "尝试次数过多，请稍后再试",
        ApiErrorCode.RATE_LIMITED to "操作过于频繁，请稍后再试",
        ApiErrorCode.LOGIN_METHOD_UNBOUND to "该登录方式尚未绑定，请先完成学校统一身份认证",
        ApiErrorCode.USER_DISABLED to "账号暂不可用，请联系管理员",
        ApiErrorCode.FORBIDDEN to "账号暂不可用，请联系管理员",
        ApiErrorCode.INTERNAL_ERROR to "登录失败，请稍后重试",
        ApiErrorCode.INVALID_REQUEST to "登录失败，请稍后重试",
    )

    /** `need_school_auth` 单独一条：它要切认证域，不归「出声」一类。 */
    private const val NEED_SCHOOL_AUTH_MESSAGE =
        "该账号尚未在农屿建立，请先完成学校统一身份认证"

    private val MESSAGE_CODES: Set<String> get() = MESSAGES.keys

    /** 签名层错误码：一律零提示（不是用户能改的错误）。 */
    private val APP_SIGN_CODES = setOf(
        ApiErrorCode.APP_SIGN_INVALID,
        ApiErrorCode.APP_SIGN_TOO_LARGE,
        ApiErrorCode.APP_SIGN_UNAVAILABLE,
    )

    /** 把一次登录失败映射成 UI 动作。 */
    fun decide(failure: ApiFailure): LoginFailureAction = when (failure) {
        is ApiFailure.Http -> when {
            // ① need_school_auth：自动切统一身份认证 tab（最高优先）
            failure.code == ApiErrorCode.NEED_SCHOOL_AUTH ->
                LoginFailureAction.SWITCH_TO_CAMPUS_SSO

            // ② 5xx / 签名类 / 未知码：零提示（优先级高于业务码）
            failure.status >= 500 -> LoginFailureAction.SILENT
            failure.code in APP_SIGN_CODES -> LoginFailureAction.SILENT
            failure.code == null || failure.code !in MESSAGE_CODES -> LoginFailureAction.SILENT

            // ③ 业务码：就地行内文案
            else -> LoginFailureAction.SHOW_MESSAGE
        }

        // 非 HTTP 失败（超时/无网/解析/取消/业务拒绝）一律零提示
        is ApiFailure.Network,
        is ApiFailure.Timeout,
        is ApiFailure.Parse,
        is ApiFailure.Business,
        ApiFailure.Cancelled,
        -> LoginFailureAction.SILENT
    }

    /**
     * 按 code 返回固定文案；不在表内返回 `null`（调用方据此判定是否显示）。
     * `need_school_auth` 也有文案（切 tab 时一并显示），但它不进 [MESSAGES]。
     */
    fun messageOf(code: String?): String? = when (code) {
        ApiErrorCode.NEED_SCHOOL_AUTH -> NEED_SCHOOL_AUTH_MESSAGE
        else -> MESSAGES[code]
    }
}
