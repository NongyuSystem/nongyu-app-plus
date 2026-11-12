package com.nongyu.appplus.net.api.http

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.NongyuLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 拦截器的纯 JVM 集成测试（规格 §7.1）—— 用 **MockWebServer 4.12.1**，不需要真机。
 *
 * 覆盖四件事：
 * 1. `AuthInterceptor`：有 token 挂头、公开端点不挂、无 token 时**不发请求**。
 * 2. `SafeLoggingInterceptor`：**断言输出里不出现明文密码/token**（这是本层唯一有真实泄漏风险的点）。
 * 3. `SessionInvalidInterceptor`：只对 identity 域回调，school 域不回调。
 * 4. `OkHttpFactory`：`retryOnConnectionFailure` 已关闭（POST 不会被 OkHttp 自己重发）。
 */
class InterceptorTest {

    private lateinit var server: MockWebServer
    private val collected = mutableListOf<String>()

    /** 收集日志的实现，用来断言脱敏是否生效。 */
    private val collector = NongyuLogger.of { level, message, throwable ->
        collected += "$level $message ${throwable ?: ""}"
    }

    private val noopListener = SessionInvalidListener { /* 测试里不关心 */ }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        collected.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        token: String? = "TOKEN-ABC",
        listener: SessionInvalidListener = noopListener,
        logging: Boolean = true,
    ): OkHttpClient = OkHttpFactory.build(
        tokenProvider = AuthTokenProvider { token },
        sessionInvalidListener = listener,
        loggingEnabled = logging,
        logger = collector,
    )

    // ---------- AuthInterceptor ----------

    @Test
    fun `有 token 时挂 Bearer 头`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val req = Request.Builder().url(server.url("/identity/me")).build()

        client().newCall(req).execute().close()

        // 非 Retrofit 请求没有 Invocation tag → 视为需鉴权
        assertEquals("Bearer TOKEN-ABC", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `无 token 时不发请求，本地直接失败`() {
        val req = Request.Builder().url(server.url("/identity/me")).build()

        val ex = runCatching {
            client(token = null).newCall(req).execute()
        }.exceptionOrNull()

        assertTrue("应抛 MissingCredentialException，实际 $ex", ex is MissingCredentialException)
        // 关键：服务端一次请求都没收到
        assertEquals(0, server.requestCount)
    }

    // ---------- SafeLoggingInterceptor 脱敏 ----------

    @Test
    fun `日志里不出现明文 Authorization`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val req = Request.Builder()
            .url(server.url("/identity/logout"))
            .header("Authorization", "Bearer SUPER-SECRET-TOKEN")
            .build()

        client().newCall(req).execute().close()

        val all = collected.joinToString("\n")
        assertTrue("日志里出现了明文 token！", !all.contains("SUPER-SECRET-TOKEN"))
        assertTrue("应出现遮蔽标记", all.contains("***"))
    }

    @Test
    fun `日志里不出现明文 password 与 ticket`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val body = """{"password":"P@ssw0rd!","ticket":"TICKET-XYZ"}"""
        val req = Request.Builder()
            .url(server.url("/other/endpoint"))
            .post(body.toRequestBody(null))
            .build()

        client().newCall(req).execute().close()

        val all = collected.joinToString("\n")
        assertTrue("日志里出现了明文密码！", !all.contains("P@ssw0rd!"))
        assertTrue("日志里出现了明文 ticket！", !all.contains("TICKET-XYZ"))
    }

    @Test
    fun `登录类端点整体不记 body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"sent":true}"""))
        val body = """{"phone":"13800000000","secretFieldNotInBlacklist":"LEAK-CHECK"}"""
        val req = Request.Builder()
            .url(server.url("/identity/mailcode/send"))
            .post(body.toRequestBody(null))
            .build()

        client().newCall(req).execute().close()

        val all = collected.joinToString("\n")
        // 路径白名单：即使字段不在黑名单里，也整体不打
        assertTrue("登录类端点的 body 不应被记录", !all.contains("LEAK-CHECK"))
        assertTrue("应显示 by path 标记", all.contains("redacted by path"))
    }

    @Test
    fun `日志里不出现 URL 的 query`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        val req = Request.Builder()
            .url(server.url("/school/address?target=jiaowu&ticket=SECRET-QUERY"))
            .build()

        client().newCall(req).execute().close()

        val all = collected.joinToString("\n")
        assertTrue("日志里出现了 query 值！", !all.contains("SECRET-QUERY"))
    }

    @Test
    fun `loggingEnabled 为 false 时完全不打日志`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        val req = Request.Builder().url(server.url("/x")).build()

        client(logging = false).newCall(req).execute().close()

        assertTrue("关闭日志后不应有任何输出", collected.isEmpty())
    }

    // ---------- SessionInvalidInterceptor ----------

    @Test
    fun `identity 域 401 session_expired 会回调`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"code":"session_expired"}"""),
        )
        var called = 0
        val listener = SessionInvalidListener { called++ }
        val req = Request.Builder().url(server.url("/identity/me")).build()

        client(listener = listener).newCall(req).execute().close()

        assertEquals("identity 域会话过期应回调", 1, called)
    }

    @Test
    fun `school 域 401 session_expired 不回调`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"code":"session_expired"}"""),
        )
        var called = 0
        val listener = SessionInvalidListener { called++ }
        val req = Request.Builder().url(server.url("/school/address")).build()

        client(listener = listener).newCall(req).execute().close()

        assertEquals("教务会话失效绝不能清 App 登录态", 0, called)
    }

    @Test
    fun `presence 的 invalid_presence 不回调`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"code":"invalid_presence","message":"x"}"""),
        )
        var called = 0
        val listener = SessionInvalidListener { called++ }
        val req = Request.Builder().url(server.url("/school/presence")).build()

        client(listener = listener).newCall(req).execute().close()

        assertEquals(0, called)
    }

    @Test
    fun `identity 域 503 auth_unavailable 不清登录态`() {
        server.enqueue(
            MockResponse().setResponseCode(503).setBody("""{"code":"auth_unavailable"}"""),
        )
        var called = 0
        val listener = SessionInvalidListener { called++ }
        val req = Request.Builder().url(server.url("/identity/me")).build()

        client(listener = listener).newCall(req).execute().close()

        assertEquals(0, called)
    }

    @Test
    fun `200 响应不会触发失效判定`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        var called = 0
        val listener = SessionInvalidListener { called++ }
        val req = Request.Builder().url(server.url("/identity/me")).build()

        client(listener = listener).newCall(req).execute().close()

        assertEquals(0, called)
    }

    // ---------- OkHttpFactory 超时配置 ----------

    @Test
    fun `超时与 callTimeout 按规格设置`() {
        val c = client()
        assertEquals(10_000, c.connectTimeoutMillis)
        assertEquals(15_000, c.readTimeoutMillis)
        assertEquals(15_000, c.writeTimeoutMillis)
        assertEquals(30_000, c.callTimeoutMillis)
    }

    @Test
    fun `retryOnConnectionFailure 已关闭`() {
        // 规格 §5.3 硬约束 2：OkHttp 默认 true，会让 POST 在连接层失败时被重发
        assertTrue(
            "retryOnConnectionFailure 必须为 false，否则 POST 可能被 OkHttp 自己重发",
            !client().retryOnConnectionFailure,
        )
    }
}
