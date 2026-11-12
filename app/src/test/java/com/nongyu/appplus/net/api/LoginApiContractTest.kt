package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.NongyuGateWay
import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.NetJson
import com.nongyu.appplus.net.sign.AppRequestSigner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 票 #92 契约层测试：**门面 [NongyuGateWay.login] → 拦截器 → MockWebServer**。
 *
 * 与 [IdentityApiCompletionTest] 的分工：那个测 Retrofit interface 的形状；
 * 本测试测「平台登录账密」这一具体调用经过**完整装配链路**后：
 * - 请求体正好是 `method=password` + `studentNo` + `password` + `deviceId`；
 * - 4 个 NY1 签名头齐全（公开端点 ≠ 免签名）；
 * - 不挂 `Authorization`（[com.nongyu.appplus.net.api.http.PublicEndpoint]）；
 * - 成功解出 `LoginDto`（`expiresAt` 是 Unix 秒 Long）。
 */
class LoginApiContractTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** 造一个带真实签名器的 NongyuGateWay：走完整拦截器链。 */
    private fun api(): NongyuGateWay {
        val signer = AppRequestSigner.fromBase64(
            "dev",
            "bm9uZ3l1LWRldi1hcHAtc2lnbi1rZXktdjEtMzJiISE",
        )
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { null },
            sessionInvalidListener = SessionInvalidListener { },
            loggingEnabled = false,
            logger = NongyuLogger.Noop,
            requestSigner = signer,
        )
        val baseUrl = server.url("/").toString()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
        return NongyuGateWay(retrofit.create(IdentityApi::class.java), baseUrl)
    }

    /** 平台账密登录的请求体（票 #92 验收判据）。 */
    private fun passwordBody(): okhttp3.RequestBody {
        val dto = LoginRequestDto(
            method = "password",
            studentNo = "20231234",
            password = "secret",
            deviceId = "device-uuid-1",
        )
        val text = NetJson.instance.encodeToString(LoginRequestDto.serializer(), dto)
        return text.toRequestBody("application/json".toMediaType())
    }

    @Test
    fun `账密登录请求形状正确且带齐四个 NY1 签名头`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"SESSION-TOKEN","expiresAt":1763000000,"isNew":false,"profileIncomplete":false}""",
            ),
        )

        val outcome = api().login(passwordBody())

        assertTrue(outcome is ApiCall.Outcome.Success<*>)
        val dto = (outcome as ApiCall.Outcome.Success<*>).value as com.nongyu.appplus.net.model.LoginDto
        assertEquals("SESSION-TOKEN", dto.token)
        assertEquals(1763000000L, dto.expiresAt)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/identity/login", req.path)

        // 请求体：**恰好 4 个字段**，值正确，且不出现 schoolMethod / wechatCode
        val sent = req.body.readUtf8()
        val sentJson = NetJson.instance.parseToJsonElement(sent).jsonObject
        assertEquals(setOf("method", "studentNo", "password", "deviceId"), sentJson.keys.toSet())
        assertEquals("password", sentJson["method"]!!.jsonPrimitive.content)
        assertEquals("20231234", sentJson["studentNo"]!!.jsonPrimitive.content)
        assertEquals("secret", sentJson["password"]!!.jsonPrimitive.content)
        assertEquals("device-uuid-1", sentJson["deviceId"]!!.jsonPrimitive.content)

        // 公开端点：不挂 Bearer
        assertEquals(null, req.getHeader("Authorization"))

        // 公开 ≠ 免签名：四个 NY1 头齐全
        assertNotNull(req.getHeader(AppRequestSigner.HEADER_KEY_ID))
        assertNotNull(req.getHeader(AppRequestSigner.HEADER_TIMESTAMP))
        assertNotNull(req.getHeader(AppRequestSigner.HEADER_NONCE))
        assertNotNull(req.getHeader(AppRequestSigner.HEADER_SIGNATURE))
        assertEquals("dev", req.getHeader(AppRequestSigner.HEADER_KEY_ID))
    }


    // ---------- 失败码分流：门面把各类失败原样分类成 ApiFailure.Http ----------

    private fun assertFailureCode(status: Int, body: String, expectedStatus: Int, expectedCode: String) = runTest {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
        val outcome = api().login(passwordBody())
        assertTrue(outcome is ApiCall.Outcome.Failure)
        val failure = (outcome as ApiCall.Outcome.Failure).failure
        assertTrue(failure is com.nongyu.appplus.net.core.ApiFailure.Http)
        failure as com.nongyu.appplus.net.core.ApiFailure.Http
        assertEquals(expectedStatus, failure.status)
        assertEquals(expectedCode, failure.code)
    }

    @Test
    fun `bad_credentials 分流`() = assertFailureCode(401, """{"code":"bad_credentials"}""", 401, "bad_credentials")

    @Test
    fun `too_many_attempts 分流`() = assertFailureCode(429, """{"code":"too_many_attempts"}""", 429, "too_many_attempts")

    @Test
    fun `rate_limited 分流`() = assertFailureCode(429, """{"code":"rate_limited"}""", 429, "rate_limited")

    @Test
    fun `login_method_unbound 分流`() = assertFailureCode(400, """{"code":"login_method_unbound"}""", 400, "login_method_unbound")

    @Test
    fun `user_disabled 分流`() = assertFailureCode(403, """{"code":"user_disabled"}""", 403, "user_disabled")

    @Test
    fun `invalid_request 分流`() = assertFailureCode(400, """{"code":"invalid_request"}""", 400, "invalid_request")

    @Test
    fun `need_school_auth 分流`() = assertFailureCode(400, """{"code":"need_school_auth"}""", 400, "need_school_auth")

    @Test
    fun `5xx 分流为 Http 且保留 internal_error`() = assertFailureCode(500, """{"code":"internal_error"}""", 500, "internal_error")

    @Test
    fun `app_sign_invalid 分流`() = assertFailureCode(401, """{"code":"app_sign_invalid"}""", 401, "app_sign_invalid")
}
