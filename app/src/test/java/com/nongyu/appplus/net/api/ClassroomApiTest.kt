package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 教室楼栋与导航（后端 `internal/classroom/`，2026-11-23 新增）。
 *
 * 三条查询**需要 Bearer**（后端 e934bb2 从公开路径移除）；
 * 响应形状各不同（lookup 平铺、列表 items、详情 data）。
 */
class ClassroomApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ClassroomApi
    private lateinit var keys: ApiKeyApi

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
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
        api = retrofit.create(ClassroomApi::class.java)
        keys = retrofit.create(ApiKeyApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------- lookup ----------

    @Test
    fun `lookup 成功时平铺返回楼栋`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"input":"一教A201","buildingKey":"yijiao","roomNo":"A201","found":true,
                   "building":{"id":1,"key":"yijiao","displayName":"第一教学楼",
                               "lat":29.98,"lng":103.0,
                               "routes":[{"id":9,"title":"从南门","recommended":true,
                                          "points":[{"seq":1,"lat":29.9,"lng":103.0,"label":"南门"},
                                                    {"seq":2,"lat":29.98,"lng":103.0}]}]}}""",
            ),
        )

        val r = api.lookup("一教A201")
        val body = r.body()!!

        assertEquals(true, body.found)
        assertEquals("yijiao", body.buildingKey)
        assertEquals("A201", body.roomNo)
        assertEquals("第一教学楼", body.building?.displayName)
        assertEquals(1, body.building?.routes?.size)
        assertEquals(2, body.building?.routes?.first()?.points?.size)

        val req = server.takeRequest()
        assertTrue("room 参数必须带上", req.path!!.startsWith("/classrooms/lookup?room="))
    }

    @Test
    fun `lookup 需要 Bearer（后端已移出公开路径）`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"input":"x","found":false,"reason":"unrecognized"}"""),
        )
        api.lookup("x")
        assertEquals(
            "Bearer TEST-TOKEN",
            server.takeRequest().getHeader("Authorization"),
        )
    }

    @Test
    fun `lookup 区分未识别与楼栋未录入`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"input":"??","found":false,"reason":"unrecognized"}"""),
        )
        val b1 = api.lookup("??").body()!!
        assertTrue(b1.isUnrecognized())
        assertFalse(b1.isBuildingNotConfigured())

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"input":"三教101","buildingKey":"sanjiao","found":false,
                   "reason":"building_not_configured"}""",
            ),
        )
        val b2 = api.lookup("三教101").body()!!
        assertTrue("能识别楼栋但没录入 —— 属运维问题", b2.isBuildingNotConfigured())
        assertEquals("sanjiao", b2.buildingKey)
    }

    // ---------- buildings ----------

    @Test
    fun `楼栋列表用 items 键`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":1,"key":"yijiao","displayName":"第一教学楼","enabled":true},
                            {"id":2,"key":"erjiao","displayName":"第二教学楼","enabled":true}]}""",
            ),
        )
        val r = api.buildings()
        assertEquals(2, r.body()?.items?.size)
        assertEquals("erjiao", r.body()?.items?.get(1)?.key)
        assertNull("列表不返回 routes", r.body()?.items?.first()?.routes)
        assertEquals("/classrooms/buildings", server.takeRequest().path)
    }

    @Test
    fun `楼栋详情用 data 信封且含 routes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":1,"key":"yijiao","displayName":"第一教学楼",
                   "routes":[{"id":9,"title":"从南门","startLabel":"南门"}]}}""",
            ),
        )
        val r = api.building(1L)
        assertEquals("第一教学楼", r.body()?.data?.displayName)
        assertEquals(1, r.body()?.data?.routes?.size)
        assertEquals("从南门", r.body()?.data?.routes?.first()?.title)
        assertEquals("/classrooms/buildings/1", server.takeRequest().path)
    }

    @Test
    fun `楼栋坐标可缺省`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"id":3,"key":"sanjiao","displayName":"三教","addressText":"东区"}}"""),
        )
        val b = api.building(3L).body()?.data!!
        assertNull("未标注坐标时为 null", b.lat)
        assertNull(b.lng)
        assertEquals("东区", b.addressText)
    }

    // ---------- 高德密钥 ----------

    @Test
    fun `取高德密钥成功且带 Bearer`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"platform":"amap","key":"plainkey123","hint":"剩余 3 个可用"}""",
            ),
        )

        val r = keys.acquireAmap(com.nongyu.appplus.net.model.AmapKeyRequest(platform = "amap"))

        assertEquals("plainkey123", r.body()?.key)
        assertEquals("amap", r.body()?.platform)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/keys/amap/acquire", req.path)
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    @Test
    fun `高德密钥请求体显式带 platform`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"key":"k"}"""),
        )
        keys.acquireAmap(com.nongyu.appplus.net.model.AmapKeyRequest(platform = "amap"))
        val sent = server.takeRequest().body.readUtf8()
        assertTrue("请求体必须含 platform=amap，实际：$sent", sent.contains("\"amap\""))
    }
}
