package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.ApiErrorCode
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.http.OkHttpFactory
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 本轮补齐的 3 条接口（`POST /identity/login`、`POST /identity/logout/all`、
 * `GET /status`）的端到端测试，依据 `docs/后端接口与NY1签名对接契约.md` §2 / §3。
 */
class IdentityApiCompletionTest {

    private lateinit var server: MockWebServer
    private lateinit var api: IdentityApi

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { "TEST-TOKEN" },
            sessionInvalidListener = SessionInvalidListener { },
            loggingEnabled = false,
            logger = NongyuLogger.Noop,
        )
        api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
            .create(IdentityApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun body(text: String) = text.toRequestBody("application/json".toMediaType())

    // ---------- POST /identity/login ----------

    @Test
    fun `login 成功解出 LoginDto（expiresAt 是 Unix 秒 Long）`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"t","expiresAt":1763000000,"isNew":true,
                   "profileIncomplete":false,"user":{"id":1,"studentNo":"2023"}}""",
            ),
        )

        val response = api.login(body("""{"method":"school","goAdminTgt":"TGT"}"""))

        assertTrue(response.isSuccessful)
        val dto = response.body()!!
        assertEquals("t", dto.token)
        assertEquals(1763000000L, dto.expiresAt)
        assertEquals("2023", dto.user?.studentNo)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/identity/login", req.path)
        // login 是 @PublicEndpoint：不挂 Bearer
        assertNull(req.getHeader("Authorization"))
    }

    @Test
    fun `login 请求体字段可空，school 方式只填两个字段也能序列化`() {
        val dto = com.nongyu.appplus.net.model.LoginRequestDto(
            method = "school",
            goAdminTgt = "TGT-VALUE",
        )
        val encoded = json.encodeToString(
            com.nongyu.appplus.net.model.LoginRequestDto.serializer(), dto,
        )
        assertTrue(encoded.contains("goAdminTgt"))
        assertTrue(encoded.contains("TGT-VALUE"))
        // explicitNulls=false：未填字段不该出现在序列化结果里
        assertTrue(!encoded.contains("password"))
        assertNull(dto.password)
    }

    // ---------- POST /identity/logout/all ----------

    @Test
    fun `logoutAll 解出 ok 与 revoked`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"revoked":3}"""))

        val response = api.logoutAll()

        assertTrue(response.isSuccessful)
        assertEquals(true, response.body()!!.ok)
        assertEquals(3, response.body()!!.revoked)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/identity/logout/all", req.path)
        // 需 Bearer
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    @Test
    fun `logoutAll 与 logout 响应形状不同`() {
        // logout 是 {"ok":true}，logoutAll 是 {"ok":true,"revoked":n}，不要复用 DTO
        val one = json.decodeFromString(
            com.nongyu.appplus.net.model.LogoutAckDto.serializer(), """{"ok":true}""",
        )
        val all = json.decodeFromString(
            com.nongyu.appplus.net.model.LogoutAllAckDto.serializer(),
            """{"ok":true,"revoked":3}""",
        )
        assertEquals(true, one.ok)
        assertEquals(3, all.revoked)
    }

    // ---------- GET /status ----------

    @Test
    fun `status 解出五字段且状态码恒 200`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"process":"up","postgres":"up","redis":"down",
                   "breaker":"closed","storage":"postgres"}""",
            ),
        )

        val response = api.status()

        assertTrue(response.isSuccessful)
        val dto = response.body()!!
        assertEquals("up", dto.process)
        assertEquals("down", dto.redis)
        assertEquals("postgres", dto.storage)
        assertEquals("/status", server.takeRequest().path)
    }

    @Test
    fun `status 未配置探针时 unknown 不炸客户端`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"process":"up","postgres":"unknown","redis":"unknown",
                   "breaker":"unknown","storage":"mock"}""",
            ),
        )
        assertEquals("unknown", api.status().body()!!.redis)
    }

    @Test
    fun `status 与 healthz 是不同端点`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val h = api.healthz()
        assertEquals("ok", h.body()!!.status)
        assertEquals("/healthz", server.takeRequest().path)
    }

    // ---------- 错误码登记 ----------

    @Test
    fun `三个 NY1 签名错误码已登记`() {
        listOf(
            ApiErrorCode.APP_SIGN_INVALID,
            ApiErrorCode.APP_SIGN_TOO_LARGE,
            ApiErrorCode.APP_SIGN_UNAVAILABLE,
        ).forEach { assertTrue("$it 应已登记", ApiErrorCode.isKnown(it)) }
    }

    @Test
    fun `login 失败时错误信封可以无 message`() {
        val dto = json.decodeFromString(
            com.nongyu.appplus.net.model.ApiErrorDto.serializer(),
            """{"code":"need_school_auth"}""",
        )
        assertEquals("need_school_auth", dto.code)
        assertNull(dto.message)
    }
}
