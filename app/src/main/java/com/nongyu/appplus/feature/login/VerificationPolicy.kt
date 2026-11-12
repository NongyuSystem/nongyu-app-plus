package com.nongyu.appplus.feature.login

import com.nongyu.appplus.net.core.ApiErrorCode
import com.nongyu.appplus.net.core.ApiFailure

/**
 * 启动校验结果。`null` 表示请求成功（凭证合法）。
 *
 * 只有 [SESSION_ENDED] 会改变首帧去向；[INDETERMINATE] 一律乐观放行，
 * 不产生任何用户可见提示（票 #90 / 规格 #89）。
 */
enum class VerificationResult {
    /** 校验通过，App 会话凭证有效。 */
    VALID,

    /** App 会话真的结束了（identity 域 401 + 结束码）。 */
    SESSION_ENDED,

    /** 无法判定：服务暂不可用、限流、超时、无网、解析失败、未知码等。乐观放行。 */
    INDETERMINATE,
}

/**
 * 启动校验的**纯逻辑决策**（票 #90 单一主接缝，纯 JVM 可测）。
 *
 * ## 为什么独立成纯函数
 * 冷启动首帧必须直接落地，校验请求只在首帧之后由后台发起、事后纠正。
 * 因此「拿到校验结果后该不该改变去向」必须是一个不依赖 Android、不依赖网络、
 * 不依赖协程的决策，才能在纯 JVM 单测里把口径钉死。
 *
 * ## 判定口径（规格 #89 / Q14=A）
 * - 成功（[ApiFailure] 为 null）→ [VerificationResult.VALID]。
 * - 仅 `GET /identity/me` 的 401 且码为 `unauthorized` / `session_expired`
 *   → [VerificationResult.SESSION_ENDED]，由上层走既有失效分流 `clear()` 进登录中心。
 * - **其余一切**（503 / 429 / 超时 / 无网 / 降级装配 404 / 未知码 / 解析失败 /
 *   取消 / 业务拒绝）→ [VerificationResult.INDETERMINATE]：乐观放行进主界面、
 *   全程零提示，靠 `SessionInvalidInterceptor` 在下一个业务请求 401 时兜底。
 *
 * 这里刻意**不复用** `SessionInvalidRules`：那是网络层「任意请求失败是否清登录态」
 * 的判据（还管 school 域同名码），而本函数是「启动校验这一特定请求的去向」，
 * 语义更窄。两者都读 ApiFailure，但决策不同源，避免相互掩蔽。
 */
object VerificationPolicy {

    fun decide(failure: ApiFailure?): VerificationResult {
        if (failure == null) return VerificationResult.VALID

        return when (failure) {
            is ApiFailure.Http -> {
                val ended = failure.status == 401 &&
                    failure.code in SESSION_ENDED_CODES
                if (ended) VerificationResult.SESSION_ENDED else VerificationResult.INDETERMINATE
            }

            // 所有非 HTTP 失败一律无法判定，乐观放行。
            is ApiFailure.Network,
            is ApiFailure.Timeout,
            is ApiFailure.Parse,
            is ApiFailure.Business,
            ApiFailure.Cancelled,
            -> VerificationResult.INDETERMINATE
        }
    }

    private val SESSION_ENDED_CODES = setOf(
        ApiErrorCode.UNAUTHORIZED,
        ApiErrorCode.SESSION_EXPIRED,
    )
}
