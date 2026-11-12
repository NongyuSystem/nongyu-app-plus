package com.nongyu.appplus.ui.login

import com.nongyu.appplus.net.core.ApiErrorCode
import com.nongyu.appplus.net.core.ApiFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * [VerificationPolicy] 的纯 JVM 单测（规格 #89 / 票 #90）。
 *
 * 这里钉死的是「启动校验」的分类口径，不是网络层自己的失败分类：
 * - 只有 identity 域 401 且码为 unauthorized / session_expired 才判「会话真的结束」；
 * - 其余一切（成功、503、429、超时、无网、404、未知码）一律 [VerificationResult.INDETERMINATE]
 *   —— 对应「乐观放行、全程零提示」，由下一个业务请求的失效分流兜底。
 */
class VerificationPolicyTest {

    private fun http(status: Int, code: String?) = ApiFailure.Http(
        status = status,
        code = code,
        message = null,
        url = "https://example.invalid/identity/me",
    )

    @Test
    fun `请求成功判合法`() {
        assertEquals(VerificationResult.VALID, VerificationPolicy.decide(null))
    }

    @Test
    fun `401 unauthorized 判会话结束`() {
        assertEquals(
            VerificationResult.SESSION_ENDED,
            VerificationPolicy.decide(http(401, ApiErrorCode.UNAUTHORIZED)),
        )
    }

    @Test
    fun `401 session_expired 判会话结束`() {
        assertEquals(
            VerificationResult.SESSION_ENDED,
            VerificationPolicy.decide(http(401, ApiErrorCode.SESSION_EXPIRED)),
        )
    }

    @Test
    fun `401 但码不认识时乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(http(401, "some_new_code")),
        )
    }

    @Test
    fun `401 且无码时乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(http(401, null)),
        )
    }

    @Test
    fun `503 auth_unavailable 乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(http(503, ApiErrorCode.AUTH_UNAVAILABLE)),
        )
    }

    @Test
    fun `429 rate_limited 乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(http(429, ApiErrorCode.RATE_LIMITED)),
        )
    }

    @Test
    fun `降级装配 404 文本页乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(http(404, null)),
        )
    }

    @Test
    fun `5xx 乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(http(500, ApiErrorCode.INTERNAL_ERROR)),
        )
    }

    @Test
    fun `超时乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(ApiFailure.Timeout(IOException("timeout"))),
        )
    }

    @Test
    fun `无网乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(ApiFailure.Network(IOException("offline"))),
        )
    }

    @Test
    fun `解析失败乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(ApiFailure.Parse(IllegalStateException("bad body"), null)),
        )
    }

    @Test
    fun `请求被取消乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(ApiFailure.Cancelled),
        )
    }

    @Test
    fun `业务拒绝乐观放行`() {
        assertEquals(
            VerificationResult.INDETERMINATE,
            VerificationPolicy.decide(ApiFailure.Business("whatever", "whatever")),
        )
    }
}
