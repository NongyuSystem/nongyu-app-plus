package com.nongyu.appplus.login.data.cas

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CasApi] 契约层测试（票 #95）：MockWebServer 验证四条 `/api/v1` 端点形状、
 * **不带农屿 NY1 签名头**、整套 cookie 收集、账密/短信两条路径各自产出 tgt。
 *
 * 不访问真实统一身份认证平台 URL、不含任何真实凭据值（验收判据最后一条）。
 */
class CasApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CasApi
    private lateinit var jar: CasCookieJar

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val (a, c) = CasApiFactory.create(server.url("/").toString())
        api = a
        jar = c
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun body(text: String) = text.toRequestBody("application/json".toMediaType())

    /** 构造一个 code=200 但 data 为空的信封（登录成功响应不需要解出 payload 即拿 cookie）。 */
    private fun okEnvelope() = """{"requestId":"req-test","code":200,"msg":"登录成功"}"""

    /** 带整套同域 Set-Cookie 的登录成功响应。 */
    private fun loginResponseWithCookies() = MockResponse()
        .setResponseCode(200)
        .setBody(okEnvelope())
        .addHeader("Set-Cookie", "go-admin-tgt=201690:TGT:TEST; Path=/; HttpOnly")
        .addHeader("Set-Cookie", "laravel_session=SESSION-TEST; Path=/; HttpOnly")
        .addHeader("Set-Cookie", "XSRF-TOKEN=XSRF-TEST; Path=/")

    // ---------- 请求形状 ----------

    @Test
    fun `captcha 请求是 GET 且带 appid query`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okEnvelope()))
        api.captcha("APPID-TEST")
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/v1/captcha?appid=APPID-TEST", req.path)
    }

    @Test
    fun `cas login 请求体恰好五个字段`() = runTest {
        server.enqueue(loginResponseWithCookies())
        api.login(
            body(
                """{"appid":"APPID-TEST","code":"a1b2","number":"20231234","password":"secret","uuid":"CAP-UUID"}""",
            ),
        )
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/cas/login", req.path)
        val obj = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(
            setOf("appid", "code", "number", "password", "uuid"),
            obj.keys.toSet(),
        )
        assertEquals("20231234", obj["number"]!!.jsonPrimitive.content)
        assertEquals("CAP-UUID", obj["uuid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sms send 请求体恰好四个字段且 type 为 login`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okEnvelope()))
        api.sendSms(body("""{"uuid":"CAP-UUID","code":"a1b2","type":"login","phone":"13800000000"}"""))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/sms/send", req.path)
        val obj = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("uuid", "code", "type", "phone"), obj.keys.toSet())
        assertEquals("login", obj["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `loginByPhone 请求体恰好三个字段`() = runTest {
        server.enqueue(loginResponseWithCookies())
        api.loginByPhone(body("""{"code":"654321","phone":"13800000000","appid":"APPID-TEST"}"""))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/cas/loginByPhone", req.path)
        val obj = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("code", "phone", "appid"), obj.keys.toSet())
    }

    // ---------- 与农屿后端隔离 ----------

    private fun assertNoNongyuHeaders(req: okhttp3.mockwebserver.RecordedRequest) {
        assertNull(req.getHeader("X-Nongyu-Key-Id"))
        assertNull(req.getHeader("X-Nongyu-Timestamp"))
        assertNull(req.getHeader("X-Nongyu-Nonce"))
        assertNull(req.getHeader("X-Nongyu-Signature"))
        assertNull(req.getHeader("Authorization"))
    }

    @Test
    fun `captcha 不带 NY1 签名头与 Bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okEnvelope()))
        api.captcha("APPID-TEST")
        assertNoNongyuHeaders(server.takeRequest())
    }

    @Test
    fun `cas login 不带 NY1 签名头与 Bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okEnvelope()))
        api.login(body("""{"appid":"A","code":"c","number":"n","password":"p","uuid":"u"}"""))
        assertNoNongyuHeaders(server.takeRequest())
    }

    @Test
    fun `sms send 不带 NY1 签名头与 Bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okEnvelope()))
        api.sendSms(body("""{"uuid":"u","code":"c","type":"login","phone":"p"}"""))
        assertNoNongyuHeaders(server.takeRequest())
    }

    @Test
    fun `loginByPhone 不带 NY1 签名头与 Bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(okEnvelope()))
        api.loginByPhone(body("""{"code":"c","phone":"p","appid":"A"}"""))
        assertNoNongyuHeaders(server.takeRequest())
    }

    // ---------- cookie 收集 / 两条路径各自拿 tgt ----------

    @Test
    fun `账密登录响应收集整套 cookie 并产出可用凭据`() = runTest {
        server.enqueue(loginResponseWithCookies())
        api.login(body("""{"appid":"A","code":"c","number":"n","password":"p","uuid":"u"}"""))
        server.takeRequest()

        val cred = jar.toCredential()
        assertTrue(cred.isUsable)
        assertEquals("201690:TGT:TEST", cred.goAdminTgt)
        assertTrue(cred.cookieHeader.contains("laravel_session=SESSION-TEST"))
        assertTrue(cred.cookieHeader.contains("XSRF-TOKEN=XSRF-TEST"))
    }

    @Test
    fun `短信登录响应独立产出整套 cookie 凭据`() = runTest {
        // 短信路径不依赖账密登录的任何前置状态
        server.enqueue(loginResponseWithCookies())
        api.loginByPhone(body("""{"code":"654321","phone":"13800000000","appid":"A"}"""))
        server.takeRequest()

        val cred = jar.toCredential()
        assertTrue(cred.isUsable)
        assertEquals("201690:TGT:TEST", cred.goAdminTgt)
        assertTrue(cred.companionCookieNames.contains("laravel_session"))
    }

    @Test
    fun `只有单枚 tgt 的响应也算可用登录态（真站不带 Set-Cookie）`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(okEnvelope())
                .addHeader("Set-Cookie", "go-admin-tgt=201690:TGT:ONLY; Path=/; Max-Age=864000; HttpOnly"),
        )
        api.login(body("""{"appid":"A","code":"c","number":"n","password":"p","uuid":"u"}"""))
        server.takeRequest()
        assertTrue(jar.toCredential().isUsable)
    }

    // ---------- 统一身份认证平台错误不伪装成农屿错误码 ----------

    @Test
    fun `code 非 200 时原样保留统一身份认证平台 msg`() = runTest {
        val envelope = """{"requestId":"r","code":500,"msg":"验证码错误","data":null}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(envelope))
        val response = api.captcha("APPID-TEST")
        val parsed = json.decodeFromString(
            CasEnvelope.serializer(),
            response.body()!!.string(),
        )
        assertEquals(500, parsed.code)
        assertEquals("验证码错误", parsed.msg)
        assertNull(parsed.data)
    }

    @Test
    fun `图形验证码 id 字段解析为 uuid`() = runTest {
        val envelope = """{"requestId":"r","code":200,"id":"CAP-UUID-X","data":"ENC"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(envelope))
        val parsed = json.decodeFromString(
            CasEnvelope.serializer(),
            api.captcha("APPID-TEST").body()!!.string(),
        )
        assertEquals("CAP-UUID-X", parsed.id)
        assertEquals("ENC", parsed.data)
    }
}
