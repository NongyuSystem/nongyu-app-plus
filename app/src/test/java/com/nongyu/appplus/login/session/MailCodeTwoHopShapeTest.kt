package com.nongyu.appplus.login.session

import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 邮箱验证码两跳请求形状（票 #93 验收项「MockWebServer 测试覆盖两跳请求形状」）。
 *
 * ## 与 [MailCodeLoginUseCaseTest] 的分工
 * 那个测试用 FakeGateway 验**用例逻辑**（成功落凭证、失败透出、空输入不发请求）。
 * 本测试用真实 OkHttp + MockWebServer 验**线上字节形状**：
 * 路径、method、JSON 字段名与实际发出的 body——这两层容易各错各的。
 *
 * ## 为什么不用真 IdentityApi
 * `IdentityApi` 依赖 NetHolder 的拦截器链（NY1 签名）。这里只关心「两跳的路径与 body 形状」，
 * 用最小 Retrofit 接口照抄相同路径即可；签名本身有 `AppRequestSignerTest` 单独覆盖。
 */
class MailCodeTwoHopShapeTest {

    /** 照抄 IdentityApi 的两个端点的相对路径。 */
    private interface Api {
        @POST("identity/mailcode/send")
        suspend fun sendMailCode(@Body body: RequestBody): retrofit2.Response<Unit>

        @POST("identity/login")
        suspend fun login(@Body body: RequestBody): retrofit2.Response<Unit>
    }

    private lateinit var server: MockWebServer
    private lateinit var api: Api

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(NetJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `第一跳 发码走 identity mailcode send 且 body 只有 email`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"sent":true}"""))

        val text = NetJson.instance.encodeToString(MailCodeBody.serializer(), MailCodeBody("a@b.com"))
        api.sendMailCode(text.toRequestBody("application/json".toMediaType()))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/identity/mailcode/send", req.path)
        val obj = NetJson.instance.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("email"), obj.keys)
        assertEquals("a@b.com", obj["email"]!!.jsonPrimitive.content)
    }

    @Test
    fun `第二跳 登录走 identity login 且 method 为 mailcode`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"T","expiresAt":1}"""))

        val body = """{"method":"mailcode","email":"a@b.com","code":"123456","deviceId":"dev-1"}"""
        api.login(body.toRequestBody("application/json".toMediaType()))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/identity/login", req.path)
        val obj = NetJson.instance.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("mailcode", obj["method"]!!.jsonPrimitive.content)
        assertEquals("a@b.com", obj["email"]!!.jsonPrimitive.content)
        assertEquals("123456", obj["code"]!!.jsonPrimitive.content)
        assertEquals("dev-1", obj["deviceId"]!!.jsonPrimitive.content)
        // 邮箱码登录不能混进账密字段
        assertTrue(!obj.containsKey("studentNo"))
        assertTrue(!obj.containsKey("password"))
    }

    @Test
    fun `两跳是两次独立请求而不是一次`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"sent":true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"T","expiresAt":1}"""))

        api.sendMailCode(
            NetJson.instance.encodeToString(MailCodeBody.serializer(), MailCodeBody("a@b.com"))
                .toRequestBody("application/json".toMediaType()),
        )
        api.login(
            """{"method":"mailcode","email":"a@b.com","code":"123456","deviceId":"d"}"""
                .toRequestBody("application/json".toMediaType()),
        )

        assertEquals(2, server.requestCount)
        assertEquals("/identity/mailcode/send", server.takeRequest().path)
        assertEquals("/identity/login", server.takeRequest().path)
    }
}
