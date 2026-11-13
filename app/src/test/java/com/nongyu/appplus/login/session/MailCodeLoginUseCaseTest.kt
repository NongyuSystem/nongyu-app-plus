package com.nongyu.appplus.login.session

import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.MailCodeAckDto
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MailCodeLoginUseCase] 纯 JVM 单测（票 #93 / 邮箱验证码）。
 *
 * 钉死：`method=mailcode` 请求体恰好 email/code/deviceId/method 四字段，
 * 成功落凭证并切主界面，失败原样透出 ApiFailure。
 */
class MailCodeLoginUseCaseTest {

    private class FakeGateway : MailCodeLoginUseCase.Gateway {
        var lastBody: String? = null
        var sentEmail: String? = null
        var sendResult: ApiCall.Outcome<MailCodeAckDto> = ApiCall.Outcome.Success(MailCodeAckDto(true))
        var loginResult: ApiCall.Outcome<LoginDto> =
            ApiCall.Outcome.Success(LoginDto(token = "T", expiresAt = 1L))

        override suspend fun sendCode(email: String): ApiCall.Outcome<MailCodeAckDto> {
            sentEmail = email
            return sendResult
        }
        override suspend fun login(body: RequestBody): ApiCall.Outcome<LoginDto> {
            val buf = okio.Buffer(); body.writeTo(buf); lastBody = buf.readUtf8()
            return loginResult
        }
    }

    private class FakeSession : SchoolLoginSession {
        var token: String? = null
        override fun writeToken(token: String) { this.token = token }
    }

    private class FakeNav : SchoolLoginNavigator {
        var n = 0
        override fun onSignedIn() { n++ }
    }

    @Test
    fun `请求体恰好四字段且 method 为 mailcode`() = runTest {
        val gw = FakeGateway()
        MailCodeLoginUseCase(gw, FakeSession(), FakeNav()).invoke("a@b.com", "123456", "dev-1")
        val obj = NetJson.instance.parseToJsonElement(gw.lastBody!!).jsonObject
        assertEquals(setOf("method", "email", "code", "deviceId"), obj.keys)
        assertEquals("mailcode", obj["method"]!!.jsonPrimitive.content)
        assertEquals("a@b.com", obj["email"]!!.jsonPrimitive.content)
        assertEquals("123456", obj["code"]!!.jsonPrimitive.content)
        assertEquals("dev-1", obj["deviceId"]!!.jsonPrimitive.content)
        // 不应出现账密字段
        assertTrue(!gw.lastBody!!.contains("studentNo"))
        assertTrue(!gw.lastBody!!.contains("password"))
    }

    @Test
    fun `成功落凭证并切主界面`() = runTest {
        val s = FakeSession(); val nav = FakeNav()
        val r = MailCodeLoginUseCase(FakeGateway(), s, nav).invoke("a@b.com", "123456", "d")
        assertTrue(r is MailLoginResult.Success)
        assertEquals("T", s.token)
        assertEquals(1, nav.n)
    }

    @Test
    fun `失败原样透出 ApiFailure`() = runTest {
        val gw = FakeGateway()
        gw.loginResult = ApiCall.Outcome.Failure(
            ApiFailure.Http(400, "need_school_auth", null, "u"),
        )
        val r = MailCodeLoginUseCase(gw, FakeSession(), FakeNav()).invoke("a@b.com", "123456", "d")
        assertTrue(r is MailLoginResult.Failure)
        val f = (r as MailLoginResult.Failure).failure
        assertTrue(f is ApiFailure.Http)
        assertEquals("need_school_auth", (f as ApiFailure.Http).code)
    }

    @Test
    fun `邮箱或验证码为空时不发请求`() = runTest {
        val gw = FakeGateway()
        val uc = MailCodeLoginUseCase(gw, FakeSession(), FakeNav())
        assertTrue(uc.invoke("", "123456", "d") is MailLoginResult.Failure)
        assertTrue(uc.invoke("a@b.com", "", "d") is MailLoginResult.Failure)
        assertEquals(null, gw.lastBody)
    }

    @Test
    fun `发码成功与失败`() = runTest {
        val gw = FakeGateway()
        val uc = MailCodeLoginUseCase(gw, FakeSession(), FakeNav())
        assertTrue(uc.sendCode("a@b.com") is MailCodeSendResult.Success)
        assertEquals("a@b.com", gw.sentEmail)

        gw.sendResult = ApiCall.Outcome.Failure(ApiFailure.Http(400, "need_school_auth", null, "u"))
        assertTrue(uc.sendCode("a@b.com") is MailCodeSendResult.Failure)
    }

    @Test
    fun `邮箱为空时发码不发请求`() = runTest {
        val gw = FakeGateway()
        val r = MailCodeLoginUseCase(gw, FakeSession(), FakeNav()).sendCode("  ")
        assertTrue(r is MailCodeSendResult.Failure)
        assertEquals(null, gw.sentEmail)
    }

    @Test
    fun `响应缺 token 视为失败`() = runTest {
        val gw = FakeGateway()
        gw.loginResult = ApiCall.Outcome.Success(LoginDto(token = null, expiresAt = 1L))
        val r = MailCodeLoginUseCase(gw, FakeSession(), FakeNav()).invoke("a@b.com", "1", "d")
        assertTrue(r is MailLoginResult.Failure)
    }
}
