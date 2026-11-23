package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.model.NetJson
import com.nongyu.appplus.net.model.SecondClassDataType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 二课（secondclass）缓存与配方 —— 2026-11-23 新增域。
 *
 * 重点钉住与 jiaowu 域的**形状差异**：二课把取数信息收进 `recipe` 对象，
 * 而不是平铺在顶层。混用会静默解析成空。
 */
class SecondClassApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SecondClassApi
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
            .create(SecondClassApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------- 命中 / 未命中 ----------

    @Test
    fun `命中时无 code，payload 是数组`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"payload":[{"name":"志愿服务"}],"schemaVersion":1,
                   "fetchedAt":"2026-11-23T10:00:00Z","stale":false}""",
            ),
        )

        val r = api.get(SecondClassDataType.ACT_LIST)

        assertTrue(r.isSuccessful)
        val body = r.body()!!
        assertFalse("命中不该被判成 miss", body.isMiss())
        assertTrue(body.payload!!.jsonArray.isNotEmpty())
        assertEquals(1, body.schemaVersion)
    }

    @Test
    fun `未命中时带嵌套 recipe 对象`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":"cache_miss","message":"没有可用缓存",
                   "recipe":{"id":"act-list-read","target":"secondclass",
                             "method":"GET","path":"/api/act/list",
                             "contentType":"application/json",
                             "auth":{"in":"header","name":"Authorization","from":"session"},
                             "params":[{"name":"page","required":false,"origin":"client_local"}],
                             "fields":[],"headers":[{"name":"Accept","value":"application/json"}]}}""",
            ),
        )

        val body = api.get(SecondClassDataType.ACT_LIST).body()!!

        assertTrue(body.isCacheMiss())
        val recipe = body.recipe!!
        assertEquals("act-list-read", recipe.id)
        assertEquals("GET", recipe.method)
        assertEquals("/api/act/list", recipe.path)
        assertEquals("header", recipe.auth?.`in`)
        assertEquals(1, recipe.params?.size)
        assertEquals("client_local", recipe.params?.first()?.origin)
    }

    @Test
    fun `未命中不带 recipe 时也不炸`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":"cache_miss"}"""))
        val body = api.get(SecondClassDataType.RANKING).body()!!
        assertTrue(body.isCacheMiss())
        assertNull(body.recipe)
    }

    // ---------- 多步配方 params_from ----------

    @Test
    fun `多步配方的 params_from 用 snake_case 映射`() {
        val raw = """{"id":"multi","steps":[
            {"ref":"step1"},
            {"ref":"step2","params_from":{"actId":"data.id","token":"data.token"}}
        ]}"""
        val recipe = json.decodeFromString(
            com.nongyu.appplus.net.model.SecondClassRecipeDto.serializer(), raw,
        )
        assertEquals(2, recipe.steps?.size)
        val step2 = recipe.steps!![1]
        assertNotNull("params_from 必须被正确映射，否则会静默为 null", step2.paramsFrom)
        assertEquals("data.id", step2.paramsFrom!!["actId"])
    }

    @Test
    fun `单步配方不返回 steps`() {
        val recipe = json.decodeFromString(
            com.nongyu.appplus.net.model.SecondClassRecipeDto.serializer(),
            """{"id":"single","method":"GET"}""",
        )
        assertNull(recipe.steps)
    }

    // ---------- 路径与鉴权 ----------

    @Test
    fun `get 路径正确且带 Bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"schemaVersion":1}"""))
        api.get(SecondClassDataType.CREDIT_RECORD_DETAIL)
        val req = server.takeRequest()
        assertEquals("/school/secondclass/cache/creditRecordDetail", req.path)
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    @Test
    fun `驼峰 dataType 原样进路径`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"schemaVersion":1}"""))
        api.get(SecondClassDataType.ACT_EVALUATE_TAG_DICT)
        assertEquals(
            "/school/secondclass/cache/actEvaluateTagDict",
            server.takeRequest().path,
        )
    }

    @Test
    fun `fallback=true 带 fallback=1`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"stale":true}"""))
        api.get(SecondClassDataType.ACT_LIST, fallback = true)
        assertTrue(server.takeRequest().path!!.contains("fallback=1"))
    }

    @Test
    fun `submit 是 POST 且带 JSON`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"schemaVersion":1}"""))
        val body = """{"payload":[]}""".toRequestBody("application/json".toMediaType())
        api.submit(SecondClassDataType.ACT_LIST, body)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/school/secondclass/cache/actList", req.path)
    }

    @Test
    fun `invalidate 是 DELETE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"schemaVersion":1}"""))
        api.invalidate(SecondClassDataType.RANKING)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/school/secondclass/cache/ranking", req.path)
    }

    @Test
    fun `recipe 端点单独可取写类配方`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"act-apply","method":"POST","path":"/api/act/apply"}""",
            ),
        )
        val r = api.recipe("act-apply")
        assertEquals("act-apply", r.body()?.id)
        assertEquals("/school/secondclass/recipe/act-apply", server.takeRequest().path)
    }

    // ---------- 词表 ----------

    @Test
    fun `词表恰好 36 个且无重复`() {
        assertEquals(36, SecondClassDataType.ALL.size)
        assertEquals(36, SecondClassDataType.ALL.map { it.wireValue }.toSet().size)
    }

    @Test
    fun `fromWire 精确匹配，大小写不符拒绝`() {
        assertEquals(
            SecondClassDataType.ACT_LIST,
            SecondClassDataType.fromWire("actList"),
        )
        assertNull(SecondClassDataType.fromWire("actlist"))
        assertNull(SecondClassDataType.fromWire("ACTLIST"))
        assertNull(SecondClassDataType.fromWire(""))
        assertNull(SecondClassDataType.fromWire(null))
    }

    @Test
    fun `未知名字不合法`() {
        assertFalse(SecondClassDataType.isValid("notARealType"))
        assertTrue(SecondClassDataType.isValid("creditRule"))
    }
}
