package com.nongyu.appplus.login.session

import com.nongyu.appplus.login.data.cas.CasCaptcha
import com.nongyu.appplus.login.data.cas.CasCaptchaResult
import com.nongyu.appplus.login.data.cas.CasCredential
import com.nongyu.appplus.login.data.cas.CasLoginResult
import com.nongyu.appplus.login.data.cas.CasSmsSendResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CampusSsoLoginUseCase] 纯 JVM 决策测试（票 #96）。
 *
 * 用可注入的假 S6 用例 / 假 `method=school` 用例，验证编排：
 * - 账密：先取验证码 → 用同一个 captcha + 用户图形码取 tgt → 交 method=school；
 * - 短信：先取验证码 → 发短信 → 短信登录取 tgt → 交 method=school；
 * - 任一步失败都原样停下，不继续后续调用（不臆造提示）。
 */
class CampusSsoLoginUseCaseTest {

    private val usableCred = CasCredential(
        goAdminTgt = "201690:TGT:TEST",
        cookieHeader = "go-admin-tgt=201690:TGT:TEST; laravel_session=S",
        companionCookieNames = setOf("laravel_session"),
    )

    private class FakeCas : CampusSsoLoginUseCase.CasPort {
        var captchaResult: CasCaptchaResult = CasCaptchaResult.Success(
            CasCaptcha("CAP-UUID", "data:image/png;base64,AAAA"),
        )
        var passwordResult: CasLoginResult = CasLoginResult.Success(usableCred())
        var smsSendResult: CasSmsSendResult = CasSmsSendResult.Success
        var phoneResult: CasLoginResult = CasLoginResult.Success(usableCred())
        val calls = mutableListOf<String>()

        override suspend fun fetchCaptcha(): CasCaptchaResult {
            calls += "captcha"; return captchaResult
        }
        override suspend fun loginWithPassword(
            studentNo: String, password: String, captcha: CasCaptcha, captchaCode: String,
        ): CasLoginResult { calls += "password"; return passwordResult }
        override suspend fun sendSmsCode(
            phone: String, captcha: CasCaptcha, captchaCode: String,
        ): CasSmsSendResult { calls += "sendSms"; return smsSendResult }
        override suspend fun loginWithPhone(phone: String, smsCode: String): CasLoginResult {
            calls += "phone"; return phoneResult
        }
        private fun usableCred() = CasCredential(
            "201690:TGT:TEST",
            "go-admin-tgt=201690:TGT:TEST; laravel_session=S",
            setOf("laravel_session"),
        )
    }

    private class FakeSchool : CampusSsoLoginUseCase.SchoolPort {
        var calledWith: CasCredential? = null
        override suspend fun submit(credential: CasCredential, deviceId: String): SchoolLoginResult {
            calledWith = credential
            return SchoolLoginResult.Success(false, false, 1763000000L)
        }
    }

    @Test
    fun `账密路径 取码 取tgt 提交method=school 依次调用`() = runTest {
        val cas = FakeCas(); val school = FakeSchool()
        val useCase = CampusSsoLoginUseCase(cas, school)
        val result = useCase.loginWithPassword(
            studentNo = "20231234", password = "pw",
            captchaCode = "a1b2", deviceId = "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Success)
        assertEquals(listOf("captcha", "password"), cas.calls)
        assertTrue(school.calledWith!!.isUsable)
    }

    @Test
    fun `短信路径 取码 发短信 取tgt 提交method=school 依次调用`() = runTest {
        val cas = FakeCas(); val school = FakeSchool()
        val useCase = CampusSsoLoginUseCase(cas, school)
        val result = useCase.loginWithPhone(
            phone = "13800000000", captchaCode = "a1b2",
            smsCode = "654321", deviceId = "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Success)
        assertEquals(listOf("captcha", "sendSms", "phone"), cas.calls)
        assertTrue(school.calledWith!!.isUsable)
    }

    @Test
    fun `取验证码失败时账密路径不继续`() = runTest {
        val cas = FakeCas()
        cas.captchaResult = CasCaptchaResult.Failure("验证码获取失败")
        val school = FakeSchool()
        val result = CampusSsoLoginUseCase(cas, school).loginWithPassword(
            "n", "p", "c", "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Failure)
        assertEquals(listOf("captcha"), cas.calls)
        assertEquals(null, school.calledWith)
    }

    @Test
    fun `短信发送失败不继续登录`() = runTest {
        val cas = FakeCas()
        cas.smsSendResult = CasSmsSendResult.Failure("操作频繁")
        val school = FakeSchool()
        val result = CampusSsoLoginUseCase(cas, school).loginWithPhone(
            "13800000000", "a1b2", "654321", "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Failure)
        assertEquals(listOf("captcha", "sendSms"), cas.calls)
        assertEquals(null, school.calledWith)
    }

    @Test
    fun `取tgt失败时method=school不提交`() = runTest {
        val cas = FakeCas()
        cas.passwordResult = CasLoginResult.Failure("验证码错误", needRefreshCaptcha = true)
        val school = FakeSchool()
        val result = CampusSsoLoginUseCase(cas, school).loginWithPassword(
            "n", "p", "c", "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Failure)
        assertEquals(listOf("captcha", "password"), cas.calls)
        assertEquals(null, school.calledWith)
    }
}
