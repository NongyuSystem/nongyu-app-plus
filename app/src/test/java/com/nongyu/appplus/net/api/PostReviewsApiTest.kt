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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 评价专用查询（后端 `internal/content/post/review_handler.go`，2026-11-23 新增）。
 *
 * 两个端点都是**课程模块的固定入口**，不是通用搜索接口。
 * 响应比普通帖子列表多一个 `query` 字段，回显实际过滤条件。
 */
class PostReviewsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PostApi

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
            .create(PostApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `按老师查评价带回 query 回显`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":1,"title":"张老师的课","board":"review"}],
                   "query":{"teacherName":"张老师"}}""",
            ),
        )

        val r = api.teacherReviews("张老师")
        val body = r.body()!!

        assertEquals(1, body.items?.size)
        assertEquals("张老师的课", body.items?.first()?.title)
        assertEquals("张老师", body.query?.get("teacherName"))

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue("路径应为 /post-reviews/teacher，实际 ${req.path}", req.path!!.startsWith("/post-reviews/teacher?"))
        assertTrue("teacherName 必须编码进 query", req.path!!.contains("teacherName="))
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    @Test
    fun `按老师加课程查评价 query 回显两个条件`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[],"query":{"teacherName":"李老师","courseName":"数据结构"}}""",
            ),
        )

        val body = api.teacherCourseReviews("李老师", "数据结构").body()!!

        assertEquals("李老师", body.query?.get("teacherName"))
        assertEquals("数据结构", body.query?.get("courseName"))
        assertTrue(body.items!!.isEmpty())
    }

    @Test
    fun `游标与 limit 透传`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"items":[],"next":"c2","query":{"teacherName":"王"}}"""),
        )
        val body = api.teacherReviews("王", cursor = "c1", limit = 20).body()!!
        assertEquals("c2", body.next)

        val path = server.takeRequest().path!!
        assertTrue("应带 cursor，实际 $path", path.contains("cursor=c1"))
        assertTrue("应带 limit，实际 $path", path.contains("limit=20"))
    }

    @Test
    fun `没有 query 回显时也不炸`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        val body = api.teacherReviews("赵").body()!!
        assertNull("后端未给 query 时为 null", body.query)
        assertEquals(0, body.items?.size)
    }

    @Test
    fun `posts 列表支持 mine 参数`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"items":[{"id":1,"isMine":true}]}"""),
        )
        val body = api.list(mine = true).body()!!
        assertEquals(true, body.items?.first()?.isMine)
        assertTrue(server.takeRequest().path!!.contains("mine=true"))
    }

    @Test
    fun `不传 mine 时路径不带该参数`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        api.list()
        assertEquals("/posts", server.takeRequest().path)
    }
}
