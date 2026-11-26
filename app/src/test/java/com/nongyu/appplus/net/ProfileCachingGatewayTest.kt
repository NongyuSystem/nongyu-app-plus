package com.nongyu.appplus.net

import com.nongyu.appplus.net.api.IdentityApi
import com.nongyu.appplus.net.api.PresenceApi
import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.model.NetJson
import com.nongyu.appplus.net.profile.ProfileStore
import com.nongyu.appplus.profile.model.ProfileSnapshot
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
 * 画像「顺手落盘」的网关接缝测试（票 #112 / ADR-0011）。
 *
 * 断言三件事：成功写入、失败不写、写入失败不改变返回值（US24 硬约束）。
 */
class ProfileCachingGatewayTest {

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

    private class FakeStore(
        var failOnWrite: Boolean = false,
    ) : ProfileStore {
        val writes = mutableListOf<ProfileSnapshot>()
        override fun read(): ProfileSnapshot? = null
        override fun write(patch: ProfileSnapshot) {
            if (failOnWrite) throw IllegalStateException("storage broken")
            writes += patch
        }
        override fun clear() = Unit
    }

    private fun api(store: ProfileStore): NongyuGateWay {
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { "TOKEN" },
            sessionInvalidListener = SessionInvalidListener { },
            loggingEnabled = false,
        )
        val baseUrl = server.url("/").toString()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
        return NongyuGateWay(
            identity = retrofit.create(IdentityApi::class.java),
            presence = retrofit.create(PresenceApi::class.java),
            baseUrl = baseUrl,
            profileStore = store,
        )
    }

    @Test
    fun `me 成功时顺手把画像写入缓存，只打一次 me`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"name":"张三","avatarUrl":"url","qq":"123"}""",
            ),
        )
        val store = FakeStore()

        val outcome = api(store).me()

        assertTrue(outcome is ApiCall.Outcome.Success)
        assertEquals(1, store.writes.size)
        assertEquals("张三", store.writes[0].name)
        assertEquals("url", store.writes[0].avatarUrl)
        assertEquals("123", store.writes[0].qq)
        assertTrue(store.writes[0].hasMeResponse)
        assertEquals("/identity/me", server.takeRequest().path)
    }

    @Test
    fun `login 成功时把登录响应里的姓名学号过期时间写入缓存`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"T","expiresAt":9999,"user":{"studentNo":"20260001","name":"张三","major":"计算机"}}""",
            ),
        )
        val store = FakeStore()

        val outcome = api(store).login(
            """{"method":"password"}""".let { okhttp3.RequestBody.create(null, it) },
        )

        assertTrue(outcome is ApiCall.Outcome.Success)
        assertEquals(1, store.writes.size)
        assertEquals("20260001", store.writes[0].studentNo)
        assertEquals("张三", store.writes[0].name)
        assertEquals("计算机", store.writes[0].major)
        assertEquals(9999L, store.writes[0].expiresAtUnixSeconds)
    }

    @Test
    fun `me 失败时不写缓存`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"internal_error"}"""))
        val store = FakeStore()

        val outcome = api(store).me()

        assertTrue(outcome is ApiCall.Outcome.Failure)
        assertTrue("失败路径绝不能写缓存", store.writes.isEmpty())
    }

    @Test
    fun `画像落盘抛异常时 me 仍返回成功，不影响启动校验判定`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"name":"张三"}"""),
        )
        val store = FakeStore(failOnWrite = true)

        val outcome = api(store).me()

        assertTrue("缓存写失败绝不能改变 me 的成功判定", outcome is ApiCall.Outcome.Success)
        assertEquals("张三", (outcome as ApiCall.Outcome.Success).value.name)
    }
}
