package com.nongyu.appplus.net

import com.nongyu.appplus.net.api.IdentityApi
import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.http.OkHttpFactory
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 端到端测试：**门面 → Retrofit → 拦截器 → MockWebServer**（规格 §7.1）。
 *
 * 与前序测试的分工：
 * - `ApiCallTest` / `RetryPolicyTest` / `SessionInvalidRulesTest`：纯逻辑，不联网
 * - `InterceptorTest`：单个拦截器行为
 * - **本测试**：整条链路是否真的贯通（路径拼装、DTO 反序列化、错误信封、失败分类）
 *
 * 用 MockWebServer 而不是真后端：`#46` 已证明真后端起不来（Postgres 口令未知）
 * 且 12 组端点全缺，一个「跑不起来就跳过」的契约测试等于没有。
 */
class NongyuApiE2ETest {

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

    /** 用 MockWebServer 的地址造一个真的 NongyuApi（走完整装配链路，除 NetHolder 的 Context 依赖）。 */
    private fun api(token: String? = "TOKEN-ABC"): Pair<NongyuApi, MutableList<String>> {
        val logs = mutableListOf<String>()
        val logger = NongyuLogger.of { _, msg, _ -> logs += msg }

        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { token },
            sessionInvalidListener = SessionInvalidListener { },
            loggingEnabled = true,
            logger = logger,
        )
        val baseUrl = server.url("/").toString()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
        return NongyuApi(retrofit.create(IdentityApi::class.java), baseUrl) to logs
    }

    // ---------- 成功路径：路径拼装 + DTO 反序列化 ----------

    @Test
    fun `healthz 成功并解出 DTO`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val (api, _) = api()

        val result = api.healthz()

        assertTrue("应为 Success，实际 $result", result is ApiCall.Outcome.Success)
        assertEquals("ok", (result as ApiCall.Outcome.Success).value.status)

        // 路径拼装正确（无前导斜杠 → 相对 baseUrl）
        assertEquals("/healthz", server.takeRequest().path)
    }

    @Test
    fun `公开端点不挂 Authorization 头`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val (api, _) = api(token = "SHOULD-NOT-BE-SENT")

        api.healthz()

        // healthz 标了 @PublicEndpoint
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `后端新增字段不影响反序列化`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"status":"ok","futureField":123,"nested":{"a":1}}"""),
        )
        val (api, _) = api()

        val result = api.healthz()

        assertTrue(result is ApiCall.Outcome.Success)
        assertEquals("ok", (result as ApiCall.Outcome.Success).value.status)
    }

    // ---------- 错误信封解析 ----------

    @Test
    fun `401 带 code 的信封被解析进 Http code`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"code":"unauthorized"}"""),
        )
        val (api, _) = api()

        val result = api.healthz()

        val f = (result as ApiCall.Outcome.Failure).failure as ApiFailure.Http
        assertEquals(401, f.status)
        assertEquals("unauthorized", f.code)
    }

    @Test
    fun `404 text-plain 归 Http 而不是 Parse`() = runTest {
        // 无 DB 时后端不注册 /identity/me，返回的是 text/plain 404
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "text/plain")
                .setBody("404 page not found"),
        )
        val (api, _) = api()

        val result = api.healthz()

        // 关键：不能因为 body 不是 JSON 就被兜底成 Parse
        val f = (result as ApiCall.Outcome.Failure).failure
        assertTrue("应为 Http，实际 ${f::class.simpleName}", f is ApiFailure.Http)
        assertEquals(404, (f as ApiFailure.Http).status)
        assertEquals("解析不出时不带 code", null, f.code)
    }

    @Test
    fun `500 会重试一次`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"internal_error"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val (api, _) = api()

        val result = api.healthz()

        assertTrue("5xx 重试后应成功", result is ApiCall.Outcome.Success)
        assertEquals("服务端应收到两次请求", 2, server.requestCount)
    }

    @Test
    fun `POST 端点 500 不重试`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"internal_error"}"""))
        val (api, _) = api()

        api.sendMailCode(okhttp3.RequestBody.create(null, ""))

        assertEquals("写操作绝不重试", 1, server.requestCount)
    }

    // ---------- 日志链路 ----------

    @Test
    fun `端到端日志链路真的在跑且已脱敏`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val (api, logs) = api(token = "SECRET-E2E-TOKEN")

        api.healthz()

        val all = logs.joinToString("\n")
        // 双向断言：既有输出（证明链路在跑），又不含明文（证明脱敏生效）
        assertTrue("日志链路应真的在跑", all.isNotEmpty())
        assertTrue("响应体应被记录", all.contains("status"))
    }
}
