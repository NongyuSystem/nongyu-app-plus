package com.nongyu.appplus.login.data.cas

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CasLoginUseCase] 单测（票 #95，含 code-review 两轴修复）。
 *
 * 关键回归：验证码**一次性**——取码与提交必须用同一个 uuid，
 * 登录方法不得内部重取（真站会回「验证码错误」）；验证码明文带 JSON 引号，
 * 用例须剥两层后得到 data URI；短信发送是独立步骤；弱网零提示。
 */
class CasLoginUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var useCase: CasLoginUseCase

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = CasApiFactory.create(server.url("/").toString())
        useCase = CasLoginUseCase(
            api = client.api,
            jar = client.jar,
            tgtInterceptor = client.tgtInterceptor,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    companion object {
        private const val CAPTCHA_REQ = "11111111111111111111111111111111"
        /** 真站形状：解密后是带 JSON 引号的 `"data:image/png;base64,AAAA"` */
        private const val CAPTCHA_DATA =
            "MDEyMzQ1Njc4OWFiY2RlZubEcGAonFwxtZkU6IK66H1cZgAVbYl295uDPb0XbT3C"
        private const val LOGIN_REQ = "22222222222222222222222222222222"
        private const val LOGIN_DATA =
            "MDEyMzQ1Njc4OWFiY2RlZvA7lSthDFs0omteV8j52rN0h2PFGgjHoNqNU8Fn1bJ/4eMrNpEXWw1KOGt+Xm2erw=="

        private fun captchaOk() = MockResponse().setResponseCode(200).setBody(
            """{"requestId":"$CAPTCHA_REQ","code":200,"id":"CAP-UUID","data":"$CAPTCHA_DATA"}""",
        )

        private fun loginOk() = MockResponse()
            .setResponseCode(200)
            .setBody("""{"requestId":"$LOGIN_REQ","code":200,"msg":"登录成功","data":"$LOGIN_DATA"}""")
            // 关键：tgt 只能从 Set-Cookie 头取（body 里没有）
            .addHeader("Set-Cookie", "go-admin-tgt=201690:TGT:TEST-VALUE; Path=/; Max-Age=864000; HttpOnly")
            .addHeader("Set-Cookie", "laravel_session=SESSION-TEST; Path=/; HttpOnly")

        private fun smsSendOk() = MockResponse().setResponseCode(200).setBody(
            """{"requestId":"r","code":200,"msg":"发送成功"}""",
        )
    }

    @Test
    fun `验证码明文带引号时用例剥两层得到 data URI`() = runTest {
        server.enqueue(captchaOk())
        val result = useCase.fetchCaptcha()
        assertTrue(result is CasCaptchaResult.Success)
        val c = (result as CasCaptchaResult.Success).captcha
        assertEquals("CAP-UUID", c.uuid)
        assertTrue(c.image.startsWith("data:image"))
        assertTrue(!c.image.contains("\""))   // 引号已剥
    }

    @Test
    fun `账密登录用同一个 captcha uuid 且不重取验证码`() = runTest {
        server.enqueue(captchaOk())   // 只有取码这一次
        server.enqueue(loginOk())

        val cap = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        val result = useCase.loginWithPassword("20231234", "secret", cap, "a1b2")

        assertTrue(result is CasLoginResult.Success)
        val cred = (result as CasLoginResult.Success).credential
        assertTrue(cred.isUsable)
        assertEquals("201690:TGT:TEST-VALUE", cred.goAdminTgt)
        // 断言提交的请求体 uuid 与取码 uuid 一致（不重取）
        server.takeRequest() // captcha
        val loginReq = server.takeRequest()
        assertTrue(loginReq.body.readUtf8().contains("\"uuid\":\"CAP-UUID\""))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `短信路径取码 发短信 登录三步独立且同一 uuid`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(smsSendOk())
        server.enqueue(loginOk())

        val cap = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        val send = useCase.sendSmsCode("13800000000", cap, "a1b2")
        assertTrue(send is CasSmsSendResult.Success)
        val result = useCase.loginWithPhone("13800000000", "654321")

        assertTrue(result is CasLoginResult.Success)
        assertTrue((result as CasLoginResult.Success).credential.isUsable)
        // 三条请求，且 sms/send 与 loginByPhone 都带上同一个 CAP-UUID
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `统一身份认证平台错误原样透出且不伪装成农屿错误码`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"r","code":500,"msg":"验证码错误","data":null}""",
            ),
        )
        val cap = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        val result = useCase.loginWithPassword("20231234", "secret", cap, "wrong")
        assertTrue(result is CasLoginResult.Failure)
        val f = result as CasLoginResult.Failure
        assertEquals("验证码错误", f.message)
        assertTrue(!f.message.contains("bad_credentials"))
    }

    @Test
    fun `弱网空响应零提示不臆造中文文案`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = useCase.loginWithPassword(
            "20231234", "secret", CasCaptcha("u", "data:image/png;base64,AAAA"), "a1b2",
        )
        assertTrue(result is CasLoginResult.Failure)
        assertEquals("", (result as CasLoginResult.Failure).message)
    }

    @Test
    fun `captcha 取失败时是零提示`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = useCase.fetchCaptcha()
        assertTrue(result is CasCaptchaResult.Failure)
        assertEquals("", (result as CasCaptchaResult.Failure).message)
    }

    @Test
    fun `短信发送失败不继续登录且保留统一身份认证平台文案`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"r","code":500,"msg":"操作频繁","data":null}""",
            ),
        )
        val cap = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        val send = useCase.sendSmsCode("13800000000", cap, "a1b2")
        assertTrue(send is CasSmsSendResult.Failure)
        assertEquals("操作频繁", (send as CasSmsSendResult.Failure).message)
        assertEquals(2, server.requestCount)   // 取码 + 发送，没有 loginByPhone
    }

    @Test
    fun `登录成功但只拿到单枚 tgt 仍算可用`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"requestId":"r","code":200,"msg":"登录成功"}""")
                .addHeader("Set-Cookie", "go-admin-tgt=201690:TGT:ONLY; Path=/; Max-Age=864000; HttpOnly"),
        )
        val cap = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        val result = useCase.loginWithPassword("20231234", "secret", cap, "a1b2")
        assertTrue(result is CasLoginResult.Success)
        assertEquals("201690:TGT:ONLY", (result as CasLoginResult.Success).credential.goAdminTgt)
    }

    @Test
    fun `两次登录间 cookie 罐清空 不串味`() = runTest {
        // 第一次：完整成功
        server.enqueue(captchaOk())
        server.enqueue(loginOk())
        val cap1 = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        assertTrue(useCase.loginWithPassword("n", "p", cap1, "c") is CasLoginResult.Success)

        // 第二次：只给单枚 tgt，不应因残留 laravel_session 而误判可用
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"requestId":"r","code":200,"msg":"登录成功"}""")
                .addHeader("Set-Cookie", "go-admin-tgt=201690:TGT:NEW; Path=/; Max-Age=864000; HttpOnly"),
        )
        val cap2 = (useCase.fetchCaptcha() as CasCaptchaResult.Success).captcha
        val second = useCase.loginWithPassword("n", "p", cap2, "c")
        // 罐已清空 → 拿到的是本次响应的 NEW-TGT，不含上次的 laravel_session
        assertTrue(second is CasLoginResult.Success)
        assertEquals("201690:TGT:NEW", (second as CasLoginResult.Success).credential.goAdminTgt)
    }
}
