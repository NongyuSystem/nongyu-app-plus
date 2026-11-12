package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.model.CacheDataType
import com.nongyu.appplus.net.model.CacheResult
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 回传缓存端点的端到端测试（规格 §3.6、§7.1）。
 *
 * 用 MockWebServer 回放后端的真实形状，重点验：
 * 1. GET **两条 200 形状**都能正确判别（命中 / `cache_miss`）；
 * 2. 路径拼装（无前导斜杠 + `:dataType` 来自受控词表）；
 * 3. 三条路由都挂 Bearer（后端 `requireUserID` 要求）；
 * 4. POST/DELETE 的响应解出 DTO；
 * 5. 429/400/413 等错误码走 `ApiFailure.Http` 而不是被当成命中。
 */
class CacheApiE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var api: CacheApi

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
            .create(CacheApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------- GET 双 200 形状 ----------

    @Test
    fun `GET 命中时判别为 Hit 且 payload 是数组`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"payload":[{"name":"高等数学"}],"success":true,
                   "schemaVersion":1,"fetchedAt":"2026-11-11T09:00:00Z","stale":false}""",
            ),
        )

        val response = api.get(CacheDataType.COURSE)
        assertTrue(response.isSuccessful)
        val result = response.body()!!.discriminate()

        assertTrue("应为 Hit，实际 $result", result is CacheResult.Hit)
        val e = (result as CacheResult.Hit).envelope
        assertEquals(1, e.schemaVersion)
        assertTrue(e.payload is kotlinx.serialization.json.JsonArray)

        val req = server.takeRequest()
        assertEquals("/school/cache/course", req.path)
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    @Test
    fun `GET 未命中时判别为 Miss（后端回 200 而非 404）`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":"cache_miss","message":"没有可用缓存",
                   "address":"https://encoded.example/jiaowu"}""",
            ),
        )

        val response = api.get(CacheDataType.SCORE)
        assertTrue("后端回的是 200，不是 404", response.isSuccessful)
        val result = response.body()!!.discriminate()

        assertTrue("应为 Miss，实际 $result", result is CacheResult.Miss)
        assertTrue((result as CacheResult.Miss).isCacheMiss())
        assertEquals("https://encoded.example/jiaowu", result.address)
    }

    @Test
    fun `fallback=true 会带上 fallback=1 query`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"payload":[],"schemaVersion":1,"fetchedAt":"t","stale":true}""",
            ),
        )

        api.get(CacheDataType.EXAM, fallback = true)

        val path = server.takeRequest().path!!
        assertTrue("应带 fallback=1，实际 $path", path.contains("fallback=1"))
        assertTrue(path.startsWith("/school/cache/exam"))
    }

    @Test
    fun `fallback=false 不带 query 参数`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"payload":[],"schemaVersion":1,"fetchedAt":"t","stale":false}""",
            ),
        )

        api.get(CacheDataType.PLAN)

        assertEquals("/school/cache/plan", server.takeRequest().path)
    }

    // ---------- POST ----------

    @Test
    fun `POST 回传 JSON 成功并解出 Envelope`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"payload":{"ok":true},"schemaVersion":2,
                   "fetchedAt":"2026-11-11T10:00:00Z","stale":false}""",
            ),
        )

        val body = """[{"name":"A"}]""".toRequestBody("application/json".toMediaType())
        val response = api.submit(CacheDataType.COURSE, body)

        assertTrue(response.isSuccessful)
        assertEquals(2, response.body()!!.schemaVersion)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/school/cache/course", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    // ---------- DELETE ----------

    @Test
    fun `DELETE 解出 invalidated`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"invalidated":true}"""))

        val response = api.invalidate(CacheDataType.KAIKE)

        assertTrue(response.isSuccessful)
        assertEquals(true, response.body()!!.invalidated)
        assertEquals("DELETE", server.takeRequest().method)
    }

    // ---------- 错误形状 ----------

    @Test
    fun `429 refresh_too_soon 是 Http 失败不是命中`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "60")
                .setBody("""{"code":"refresh_too_soon","message":"刷新过于频繁"}"""),
        )

        val response = api.invalidate(CacheDataType.SCORE)

        assertFalse(response.isSuccessful)
        assertEquals(429, response.code())
        assertEquals("60", response.headers()["Retry-After"])
    }

    @Test
    fun `400 schema_rejected 是 Http 失败`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"code":"schema_rejected","message":"回传体形状校验未通过"}"""),
        )

        val body = "not-json".toRequestBody("application/json".toMediaType())
        val response = api.submit(CacheDataType.PLAN, body)

        assertFalse(response.isSuccessful)
        assertEquals(400, response.code())
    }

    // ---------- 受控词表的 wire 值 ----------

    @Test
    fun `驼峰 dataType 原样进路径，不被改写`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"code":"cache_miss"}"""),
        )

        api.get(CacheDataType.TEACHING_NOTICE)

        assertEquals("/school/cache/teachingNotice", server.takeRequest().path)
    }
}
