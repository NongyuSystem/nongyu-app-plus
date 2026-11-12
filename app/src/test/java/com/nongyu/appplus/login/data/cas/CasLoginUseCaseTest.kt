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
 * [CasLoginUseCase] 单测（票 #95）：账密 / 短信两条路径各自独立拿到可用凭据，
 * 学校侧错误原样透出（不伪装成农屿错误码），验证码解密出的 data URI 能解析。
 *
 * 加密信封向量由独立第三方库预生成、写死为字面量，不在测试里用产品实现重新加密。
 */
class CasLoginUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var useCase: CasLoginUseCase

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val (api, jar) = CasApiFactory.create(server.url("/").toString())
        useCase = CasLoginUseCase(api, jar)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    companion object {
        private const val CAPTCHA_REQ = "11111111111111111111111111111111"
        private const val CAPTCHA_DATA =
            "MDEyMzQ1Njc4OWFiY2RlZn+wpvDTezcfRKBTxC54wGgP6GF7hPEokZKbhWHiz4Iq"
        private const val LOGIN_REQ = "22222222222222222222222222222222"
        private const val LOGIN_DATA =
            "MDEyMzQ1Njc4OWFiY2RlZvA7lSthDFs0omteV8j52rN0h2PFGgjHoNqNU8Fn1bJ/4eMrNpEXWw1KOGt+Xm2erw=="

        private fun captchaOk() = MockResponse().setResponseCode(200).setBody(
            """{"requestId":"$CAPTCHA_REQ","code":200,"id":"CAP-UUID","data":"$CAPTCHA_DATA"}""",
        )

        private fun loginOk() = MockResponse()
            .setResponseCode(200)
            .setBody("""{"requestId":"$LOGIN_REQ","code":200,"msg":"登录成功","data":"$LOGIN_DATA"}""")
            .addHeader("Set-Cookie", "go-admin-tgt=201690:TGT:TEST; Path=/; HttpOnly")
            .addHeader("Set-Cookie", "laravel_session=SESSION-TEST; Path=/; HttpOnly")

        private fun smsSendOk() = MockResponse().setResponseCode(200).setBody(
            """{"requestId":"r","code":200,"msg":"发送成功"}""",
        )
    }

    @Test
    fun `账密路径产出可用凭据`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(loginOk())

        val result = useCase.loginWithPassword(
            studentNo = "20231234",
            password = "secret",
            captchaCode = "a1b2",
        )

        assertTrue(result is CasLoginResult.Success)
        val cred = (result as CasLoginResult.Success).credential
        assertTrue(cred.isUsable)
        assertEquals("201690:TGT:TEST", cred.goAdminTgt)
        assertTrue(cred.cookieHeader.contains("laravel_session=SESSION-TEST"))
    }

    @Test
    fun `短信路径独立产出可用凭据`() = runTest {
        server.enqueue(captchaOk())      // 取图形码
        server.enqueue(smsSendOk())      // 发短信
        server.enqueue(loginOk())        // 短信登录（自带 tgt cookie）

        val result = useCase.loginWithPhone(
            phone = "13800000000",
            captchaCode = "a1b2",
            smsCode = "654321",
        )

        assertTrue(result is CasLoginResult.Success)
        assertTrue((result as CasLoginResult.Success).credential.isUsable)
    }

    @Test
    fun `验证码解密后得到 data URI`() = runTest {
        server.enqueue(captchaOk())
        val captcha = useCase.fetchCaptcha()
        assertTrue(captcha is CasCaptchaResult.Success)
        val c = captcha as CasCaptchaResult.Success
        assertEquals("CAP-UUID", c.uuid)
        assertTrue(c.image.startsWith("data:image"))
    }

    @Test
    fun `学校侧错误原样透出且不伪装成农屿错误码`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"r","code":500,"msg":"验证码错误","data":null}""",
            ),
        )
        val result = useCase.loginWithPassword("20231234", "secret", "wrong")
        assertTrue(result is CasLoginResult.Failure)
        val f = result as CasLoginResult.Failure
        assertEquals("验证码错误", f.message)
        assertTrue(f.message.contains("验证码"))
        // 不得出现农屿侧错误码字符串
        assertTrue(!f.message.contains("bad_credentials"))
    }

    @Test
    fun `captcha 取失败时登录不继续发请求`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"r","code":500,"msg":"系统繁忙","data":null}""",
            ),
        )
        val result = useCase.loginWithPassword("20231234", "secret", "a1b2")
        assertTrue(result is CasLoginResult.Failure)
        assertEquals("系统繁忙", (result as CasLoginResult.Failure).message)
        // 只发了 captcha 一次，没有后续 cas/login
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `短信发送失败不继续 loginByPhone`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"r","code":500,"msg":"操作频繁","data":null}""",
            ),
        )
        val result = useCase.loginWithPhone("13800000000", "a1b2", "654321")
        assertTrue(result is CasLoginResult.Failure)
        assertEquals("操作频繁", (result as CasLoginResult.Failure).message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `登录成功但只拿到单枚 tgt 判为不可用`() = runTest {
        server.enqueue(captchaOk())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"requestId":"r","code":200,"msg":"登录成功"}""")
                .addHeader("Set-Cookie", "go-admin-tgt=TGT-ONLY; Path=/; HttpOnly"),
        )
        val result = useCase.loginWithPassword("20231234", "secret", "a1b2")
        assertTrue(result is CasLoginResult.Failure)
        assertTrue((result as CasLoginResult.Failure).message.contains("整套"))
    }
}
