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
 * **关键回归**（code-review Spec 轴第 1 条）：验证码一次性 —— 取码与提交必须用
 * 同一个 uuid，编排器**绝不**在登录方法内部重取验证码（重取会让用户输入配新 uuid，
 * 真站必回「验证码错误」）。
 */
class CampusSsoLoginUseCaseTest {

    private fun usableCred() = CasCredential(
        goAdminTgt = "201690:TGT:TEST",
        cookieHeader = "go-admin-tgt=201690:TGT:TEST; laravel_session=S",
        companionCookieNames = setOf("laravel_session"),
    )

    private val captcha = CasCaptcha("CAP-UUID", "data:image/png;base64,AAAA")

    private class FakeCas : CampusSsoLoginUseCase.CasPort {
        var captchaResult: CasCaptchaResult =
            CasCaptchaResult.Success(CasCaptcha("CAP-UUID", "data:image/png;base64,AAAA"))
        var passwordResult: CasLoginResult? = null
        var smsSendResult: CasSmsSendResult = CasSmsSendResult.Success
        var phoneResult: CasLoginResult? = null
        /** 记录每次调用，用于断言"没有多余的重取码"。 */
        val calls = mutableListOf<String>()
        var lastCaptchaUuid: String? = null

        override suspend fun fetchCaptcha(): CasCaptchaResult {
            calls += "captcha"; return captchaResult
        }
        override suspend fun loginWithPassword(
            studentNo: String, password: String, captcha: CasCaptcha, captchaCode: String,
        ): CasLoginResult {
            calls += "password"; lastCaptchaUuid = captcha.uuid
            return passwordResult ?: CasLoginResult.Success(usable())
        }
        override suspend fun sendSmsCode(
            phone: String, captcha: CasCaptcha, captchaCode: String,
        ): CasSmsSendResult {
            calls += "sendSms"; lastCaptchaUuid = captcha.uuid; return smsSendResult
        }
        override suspend fun loginWithPhone(phone: String, smsCode: String): CasLoginResult {
            calls += "phone"; return phoneResult ?: CasLoginResult.Success(usable())
        }
        override suspend fun fetchLoginQr() =
            com.nongyu.appplus.login.data.cas.CasQrFetchResult.Failure("")
        override suspend fun pollQrResult(state: String) =
            com.nongyu.appplus.login.data.cas.CasQrPollResult.Pending
        fun usable() = CasCredential(
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
    fun `账密登录不重取验证码 直接用传入的 uuid`() = runTest {
        val cas = FakeCas(); val school = FakeSchool()
        val result = CampusSsoLoginUseCase(cas, school).loginWithPassword(
            studentNo = "20231234", password = "pw",
            captcha = captcha, captchaCode = "a1b2", deviceId = "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Success)
        // 只调了 password 一次，没有 captcha（不重取）
        assertEquals(listOf("password"), cas.calls)
        assertEquals("CAP-UUID", cas.lastCaptchaUuid)
        assertTrue(school.calledWith!!.isUsable)
    }

    @Test
    fun `短信登录走 loginByPhone 不重取码`() = runTest {
        val cas = FakeCas(); val school = FakeSchool()
        val result = CampusSsoLoginUseCase(cas, school).loginWithPhone(
            phone = "13800000000", smsCode = "654321", deviceId = "dev-1",
        )
        assertTrue(result is SchoolLoginResult.Success)
        assertEquals(listOf("phone"), cas.calls)
        assertTrue(school.calledWith!!.isUsable)
    }

    @Test
    fun `发短信是独立动作 用同一 uuid`() = runTest {
        val cas = FakeCas()
        val useCase = CampusSsoLoginUseCase(cas, FakeSchool())
        val r = useCase.sendSmsCode("13800000000", captcha, "a1b2")
        assertTrue(r is CasSmsSendResult.Success)
        assertEquals(listOf("sendSms"), cas.calls)
        assertEquals("CAP-UUID", cas.lastCaptchaUuid)
    }

    @Test
    fun `取码失败时不提交方法`() = runTest {
        val cas = FakeCas()
        cas.captchaResult = CasCaptchaResult.Failure("验证码获取失败")
        val r = CampusSsoLoginUseCase(cas, FakeSchool()).fetchCaptcha()
        assertTrue(r is CasCaptchaResult.Failure)
        assertEquals("验证码获取失败", (r as CasCaptchaResult.Failure).message)
    }

    @Test
    fun `短信发送失败时后续登录不继续`() = runTest {
        val cas = FakeCas()
        cas.smsSendResult = CasSmsSendResult.Failure("操作频繁")
        val school = FakeSchool()
        val r = CampusSsoLoginUseCase(cas, school).sendSmsCode("13800000000", captcha, "a1b2")
        assertTrue(r is CasSmsSendResult.Failure)
        assertEquals(null, school.calledWith)
    }

    @Test
    fun `取 tgt 失败时 method=school 不提交`() = runTest {
        val cas = FakeCas()
        cas.passwordResult = CasLoginResult.Failure("验证码错误", needRefreshCaptcha = true)
        val school = FakeSchool()
        val r = CampusSsoLoginUseCase(cas, school).loginWithPassword(
            "n", "p", captcha, "c", "dev-1",
        )
        assertTrue(r is SchoolLoginResult.Failure)
        assertEquals("验证码错误", (r as SchoolLoginResult.Failure).message)
        assertEquals(null, school.calledWith)
    }
}
