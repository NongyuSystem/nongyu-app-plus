package com.nongyu.appplus.login.session

import com.nongyu.appplus.login.data.cas.CasCredential
import com.nongyu.appplus.net.NongyuGateWay
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SchoolLoginUseCase] 纯 JVM 决策测试（票 #96）。
 *
 * 用可注入的假网关（不联网），验证：
 * - 请求体恰好 `method/deviceId/goAdminTgt/cookie` 四字段；
 * - `schoolMethod` / `wechatCode` 不出现（否则后端 401）；
 * - `CasCredential` 不可用时**不发请求**、直接失败；
 * - 成功落凭证并切主界面、失败原样透出 ApiFailure 交给 LoginFailurePolicy。
 */
class SchoolLoginUseCaseTest {

    /** 记录最后一次收到的请求体，按 ApiCall.Outcome 返回预设结果。 */
    private class FakeGateway : SchoolLoginGateway {
        var lastBody: String? = null
        var next: ApiCall.Outcome<LoginDto> =
            ApiCall.Outcome.Success(LoginDto(token = "T", expiresAt = 1763000000L))

        override suspend fun login(body: RequestBody): ApiCall.Outcome<LoginDto> {
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            lastBody = buffer.readUtf8()
            return next
        }
    }

    @Test
    fun `method=school 请求体恰好四字段且无禁用字段`() = runTest {
        val gw = FakeGateway()
        val cred = CasCredential(
            goAdminTgt = "201690:TGT:TEST",
            cookieHeader = "go-admin-tgt=201690:TGT:TEST; laravel_session=S",
            companionCookieNames = setOf("laravel_session"),
        )
        SchoolLoginUseCase(gw, FakeSession(), FakeNavigator()).invoke(cred, "dev-1")

        val sent = NetJson.instance.parseToJsonElement(gw.lastBody!!).jsonObject
        assertEquals(setOf("method", "deviceId", "goAdminTgt", "cookie"), sent.keys.toSet())
        assertEquals("school", sent["method"]?.jsonPrimitive?.content)
        assertTrue(!gw.lastBody!!.contains("schoolMethod"))
        assertTrue(!gw.lastBody!!.contains("wechatCode"))
    }

    @Test
    fun `单枚 tgt 的不可用凭据不发请求`() = runTest {
        val gw = FakeGateway()
        val cred = CasCredential("TGT-ONLY", "go-admin-tgt=TGT-ONLY", emptySet())
        val result = SchoolLoginUseCase(gw, FakeSession(), FakeNavigator()).invoke(cred, "dev-1")
        assertTrue(result is SchoolLoginResult.Failure)
        assertTrue((result as SchoolLoginResult.Failure).message.contains("整套"))
        assertEquals(null, gw.lastBody)   // 没发请求
    }

    @Test
    fun `成功落凭证并切主界面`() = runTest {
        val gw = FakeGateway()
        val session = FakeSession()
        val navigator = FakeNavigator()
        val cred = CasCredential("T", "go-admin-tgt=T; laravel_session=S", setOf("laravel_session"))
        val result = SchoolLoginUseCase(gw, session, navigator).invoke(cred, "dev-1")
        assertTrue(result is SchoolLoginResult.Success)
        assertEquals("T", session.writtenToken)
        assertEquals(1, navigator.signedInCount)
    }

    @Test
    fun `失败原样透出 ApiFailure 供上层决策`() = runTest {
        val gw = FakeGateway()
        gw.next = ApiCall.Outcome.Failure(
            ApiFailure.Http(401, "bad_credentials", null, "https://x/identity/login"),
        )
        val cred = CasCredential("T", "go-admin-tgt=T; laravel_session=S", setOf("laravel_session"))
        val result = SchoolLoginUseCase(gw, FakeSession(), FakeNavigator()).invoke(cred, "dev-1")
        assertTrue(result is SchoolLoginResult.Failure)
        assertTrue((result as SchoolLoginResult.Failure).failure is ApiFailure.Http)
    }

    /** 会话与导航用最小可注入替身，避免碰 Android Context / LoginSessionHolder 单例。 */
    private class FakeSession : SchoolLoginSession {
        var writtenToken: String? = null
        override fun writeToken(token: String) { writtenToken = token }
    }
    private class FakeNavigator : SchoolLoginNavigator {
        var signedInCount = 0
        override fun onSignedIn() { signedInCount++ }
    }
}
