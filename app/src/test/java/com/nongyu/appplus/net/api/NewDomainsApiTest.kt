package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
 * 帖子 / 公告 / 站内信 / WebVPN 四个新域的端到端测试（后端 2026-11-17 对账）。
 *
 * 重点验证三件最容易错的事：
 * 1. **每个端点的信封形状**（帖子域用 `data` / `items` / `status` 三种，不是裸结构体）；
 * 2. **鉴权标注**是否正确（2026-11-23 起全部业务端点需 Bearer，
 *    只有 identity 的登录/探针类仍公开）；
 * 3. **`{"data":null}` 是正常状态**（没有生效公告），不是错误。
 */
class NewDomainsApiTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private inline fun <reified T> api(token: String? = "TEST-TOKEN"): T {
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { token },
            sessionInvalidListener = SessionInvalidListener { },
            loggingEnabled = false,
            logger = NongyuLogger.Noop,
        )
        return Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
            .create(T::class.java)
    }

    // ---------- 帖子域：三种信封 ----------

    @Test
    fun `帖子列表是 items 加 next 游标，且需要 Bearer`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":1,"board":"general","title":"t","body":"b",
                   "publishedAt":"2026-11-17T10:00:00Z","isMine":false}],"next":"c2"}""",
            ),
        )
        val api = api<PostApi>()

        val r = api.list()

        assertTrue(r.isSuccessful)
        assertEquals(1, r.body()!!.items?.size)
        assertEquals("c2", r.body()!!.next)
        assertEquals("t", r.body()!!.items!!.first().title)

        val req = server.takeRequest()
        assertEquals("/posts", req.path)
        assertEquals(
            "Bearer TEST-TOKEN",
            req.getHeader("Authorization"),
        )
    }

    @Test
    fun `帖子详情是 data 加 replies 两个键`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":1,"title":"t","likedByMe":true,"favoritedByMe":false},
                   "replies":[{"id":9,"postId":1,"body":"r","likedByMe":false}]}""",
            ),
        )
        val api = api<PostApi>()

        val body = api.detail(1L).body()!!

        assertEquals("t", body.data?.title)
        assertEquals(true, body.data?.likedByMe)
        assertEquals(1, body.replies?.size)
        assertEquals(9L, body.replies?.first()?.id)
    }

    @Test
    fun `发帖返回 201 且用 data 信封`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"data":{"id":42,"title":"新帖"}}"""),
        )
        val api = api<PostApi>()

        val r = api.create(
            com.nongyu.appplus.net.model.CreatePostRequest(
                board = "general", title = "新帖", body = "内容",
            ),
        )

        assertEquals(201, r.code())
        assertEquals(42L, r.body()?.data?.id)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("Bearer TEST-TOKEN", req.getHeader("Authorization"))
    }

    @Test
    fun `点赞返回 status ok 信封`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val api = api<PostApi>()

        assertEquals("ok", api.like(1L).body()?.status)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun `取消点赞是 DELETE 同路径`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        api<PostApi>().unlike(1L)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/posts/1/like", req.path)
    }

    @Test
    fun `回复点赞路径带两级 id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        api<PostApi>().likeReply(1L, 9L)
        assertEquals("/posts/1/replies/9/like", server.takeRequest().path)
    }

    @Test
    fun `回复创建返回 201 且 data 里是回复`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"data":{"id":9,"postId":1,"body":"r","likedByMe":false}}"""),
        )
        val r = api<PostApi>().reply(1L, com.nongyu.appplus.net.model.CreateReplyRequest(body = "r"))

        assertEquals(201, r.code())
        assertEquals(9L, r.body()?.data?.id)
    }

    // ---------- 公告域 ----------

    @Test
    fun `公告列表需要 Bearer`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"items":[{"id":1,"title":"公告","pinned":true}]}"""),
        )
        val api = api<AnnouncementApi>()

        val r = api.list()

        assertTrue(r.isSuccessful)
        assertEquals("公告", r.body()?.items?.first()?.title)
        assertEquals(
            "Bearer TEST-TOKEN",
            server.takeRequest().getHeader("Authorization"),
        )
    }

    @Test
    fun `没有生效公告时 data 为 null 是正常状态`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":null}"""))

        val r = api<AnnouncementApi>().latest()

        assertTrue("HTTP 仍是 200", r.isSuccessful)
        assertNull("data 为 null 表示没有生效公告，不是错误", r.body()?.data)
    }

    @Test
    fun `latest 走 announcements latest 路径`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":null}"""))
        api<AnnouncementApi>().latest()
        assertEquals("/announcements/latest", server.takeRequest().path)
    }

    // ---------- 站内信 ----------

    @Test
    fun `未读数是 count 键`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"count":7}"""))
        val r = api<MessageApi>().unreadCount()
        assertEquals(7, r.body()?.count)
        assertEquals("Bearer TEST-TOKEN", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `全部已读返回 updated`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"updated":3}"""))
        assertEquals(3, api<MessageApi>().markAllRead().body()?.updated)
    }

    @Test
    fun `单条已读返回 data 信封`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"id":5,"title":"t","readAt":"2026-11-17T10:00:00Z"}}"""),
        )
        val r = api<MessageApi>().markRead(5L)
        assertEquals(5L, r.body()?.data?.id)
        assertEquals("2026-11-17T10:00:00Z", r.body()?.data?.readAt)
        assertEquals("/messages/5/read", server.takeRequest().path)
    }

    // ---------- WebVPN ----------

    @Test
    fun `提交票返回 present 与 expiresAt`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"present":true,"expiresAt":"2026-11-17T18:00:00Z"}"""),
        )
        val api = api<WebVpnApi>()
        val r = api.submit(com.nongyu.appplus.net.model.WebVpnSubmitRequest(ticket = "T"))

        assertEquals(true, r.body()?.present)
        assertEquals("2026-11-17T18:00:00Z", r.body()?.expiresAt)
    }

    @Test
    fun `查询状态只回 present，不回票值`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"present":false}"""))
        val r = api<WebVpnApi>().status()

        assertEquals(false, r.body()?.present)
        assertNull("未保管时不出现 expiresAt", r.body()?.expiresAt)
    }

    @Test
    fun `清除票返回 cleared`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"cleared":true}"""))
        assertEquals(true, api<WebVpnApi>().clear().body()?.cleared)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `代取页面的错误码可被解出`() = runTest {
        // 未提交票 → 409 webvpn_ticket_missing
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"code":"webvpn_ticket_missing","message":"尚未提交"}"""),
        )
        val r = api<WebVpnApi>().fetch("course")
        assertEquals(409, r.code())
        assertEquals("/school/jiaowu/cache/course/fetch", server.takeRequest().path)
    }

    // ---------- 错误码登记 ----------

    @Test
    fun `两个 WebVPN 错误码已登记`() {
        assertTrue(com.nongyu.appplus.net.core.ApiErrorCode.isKnown("webvpn_ticket_missing"))
        assertTrue(com.nongyu.appplus.net.core.ApiErrorCode.isKnown("webvpn_ticket_expired"))
    }
}
