package com.nongyu.appplus.login.model

import com.nongyu.appplus.net.core.ApiErrorCode
import com.nongyu.appplus.net.core.ApiFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [LoginFailurePolicy] 的纯 JVM 单测（票 #92 / 规格 #89）。
 *
 * 钉死「平台登录账密」提交失败后的 UI 决策：
 * - `need_school_auth` → 自动切统一身份认证 tab + 固定文案；
 * - 6 种业务码 → 固定就地行内文案；
 * - `app_sign_*` / 5xx / 超时 / 无网 / 解析失败 / 未知码 → **零提示**。
 */
class LoginFailurePolicyTest {

    private fun http(status: Int, code: String?) = ApiFailure.Http(
        status = status,
        code = code,
        message = null,
        url = "https://example.invalid/identity/login",
    )

    @Test
    fun `need_school_auth 自动切统一身份认证 tab 并给引导文案`() {
        val action = LoginFailurePolicy.decide(http(400, ApiErrorCode.NEED_SCHOOL_AUTH))
        assertEquals(LoginFailureAction.SWITCH_TO_CAMPUS_SSO, action)
    }

    @Test
    fun `need_school_auth 的文案固定`() {
        assertEquals(
            "该账号尚未在农屿建立，请先完成学校统一身份认证",
            LoginFailurePolicy.messageOf(ApiErrorCode.NEED_SCHOOL_AUTH),
        )
    }

    @Test
    fun `bad_credentials 给行内文案`() {
        assertEquals(
            "账号或密码不正确，请重试",
            LoginFailurePolicy.messageOf(ApiErrorCode.BAD_CREDENTIALS),
        )
    }

    @Test
    fun `too_many_attempts 给行内文案`() {
        assertEquals(
            "尝试次数过多，请稍后再试",
            LoginFailurePolicy.messageOf(ApiErrorCode.TOO_MANY_ATTEMPTS),
        )
    }

    @Test
    fun `rate_limited 给行内文案`() {
        assertEquals(
            "操作过于频繁，请稍后再试",
            LoginFailurePolicy.messageOf(ApiErrorCode.RATE_LIMITED),
        )
    }

    @Test
    fun `login_method_unbound 给行内文案`() {
        assertEquals(
            "该登录方式尚未绑定，请先完成学校统一身份认证",
            LoginFailurePolicy.messageOf(ApiErrorCode.LOGIN_METHOD_UNBOUND),
        )
    }

    @Test
    fun `user_disabled 给行内文案`() {
        assertEquals(
            "账号暂不可用，请联系管理员",
            LoginFailurePolicy.messageOf(ApiErrorCode.USER_DISABLED),
        )
    }

    @Test
    fun `forbidden 与 user_disabled 同文案`() {
        assertEquals(
            "账号暂不可用，请联系管理员",
            LoginFailurePolicy.messageOf(ApiErrorCode.FORBIDDEN),
        )
    }

    @Test
    fun `invalid_request 给兜底文案`() {
        assertEquals(
            "登录失败，请稍后重试",
            LoginFailurePolicy.messageOf("invalid_request"),
        )
    }

    @Test
    fun `internal_error 给兜底文案`() {
        assertEquals(
            "登录失败，请稍后重试",
            LoginFailurePolicy.messageOf(ApiErrorCode.INTERNAL_ERROR),
        )
    }

    @Test
    fun `app_sign_invalid 零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(http(401, "app_sign_invalid")),
        )
    }

    @Test
    fun `app_sign_unavailable 零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(http(503, "app_sign_unavailable")),
        )
    }

    @Test
    fun `5xx 零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(http(500, ApiErrorCode.INTERNAL_ERROR)),
        )
    }

    @Test
    fun `超时零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(ApiFailure.Timeout(IOException("timeout"))),
        )
    }

    @Test
    fun `无网零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(ApiFailure.Network(IOException("offline"))),
        )
    }

    @Test
    fun `解析失败零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(ApiFailure.Parse(IllegalStateException("bad"), null)),
        )
    }

    @Test
    fun `未知码零提示`() {
        assertEquals(
            LoginFailureAction.SILENT,
            LoginFailurePolicy.decide(http(400, "some_new_code")),
        )
    }

    @Test
    fun `业务码决策不切 tab 且不清空输入`() {
        val action = LoginFailurePolicy.decide(http(401, ApiErrorCode.BAD_CREDENTIALS))
        assertEquals(LoginFailureAction.SHOW_MESSAGE, action)
    }

    @Test
    fun `need_school_auth 动作的文案可直接取到`() {
        val action = LoginFailurePolicy.decide(http(400, ApiErrorCode.NEED_SCHOOL_AUTH))
        assertTrue(action is LoginFailureAction.SWITCH_TO_CAMPUS_SSO)
        assertFalse(LoginFailurePolicy.messageOf(ApiErrorCode.NEED_SCHOOL_AUTH).isNullOrBlank())
    }
}
