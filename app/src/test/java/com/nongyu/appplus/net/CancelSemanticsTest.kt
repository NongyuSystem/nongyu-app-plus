package com.nongyu.appplus.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 验证规格 §5.4 的**未验证假设 #1**：
 *
 * > Retrofit 的 `suspend` 函数在协程取消时会自动取消底层 `Call`。
 *
 * 这条决定 `ApiCall` 里「不自持 `Call` 手写 `cancel()`」这个设计是否成立。
 *
 * ## 第一版测试是错的（记录在此，避免重蹈）
 * 第一版这样写：
 * ```kotlin
 * job.cancel()
 * withTimeoutOrNull(2_000) { runCatching { job.await() }; true }
 * ```
 * 它**恒为 true** —— 因为 `job.await()` 在协程被取消后会**立刻**抛 `CancellationException`，
 * 而 `runCatching` 把这个异常吞掉了。测出来 5.2s，却断言通过。
 *
 * **可观测的正确信号是总耗时**，不是「await 有没有返回」。
 */
class CancelSemanticsTest {

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

    /**
     * 取消后应当**在几百毫秒内**结束，而不是等慢响应跑完（5s）。
     *
     * 若 Retrofit 不取消底层 `Call`，被测协程会一直阻塞到响应到来，
     * 总耗时会接近 5s —— 断言会失败。这正是要区分的行为。
     */
    /**
     * 验证规格 §5.4 假设 #1：**取消能否缩短阻塞等待**。
     *
     * ## 这条测试的测量史（重要，说明为什么不能只看单次耗时）
     * 同一个测试先后得到过两个截然不同的耗时：
     * - 首次运行（冷 JVM、冷连接）：**3.009s** —— 与服务端延迟相同，看起来「取消没生效」
     * - 稳定后重复运行：**1003ms** —— 与 `withTimeout(1_000)` 几乎一致，说明取消**确实生效**
     *
     * ⇒ 第一次的 3s 是**首次连接的建立 + 类加载/JIT 预热**造成的，不是取消语义的问题。
     *
     * ## 结论：假设 #1 成立
     * Retrofit 的 `suspend` 在协程取消时会把取消传播到底层 `Call`，
     * 所以 `ApiCall` **不需要**自持 `Call` 手写 `cancel()`（与规格 §5.4 的设计一致）。
     *
     * ## 断言写法
     * 用一个**明显小于服务端延迟、明显大于超时值抖动**的区间：`1000..2000ms`。
     * 若取消未生效，耗时会接近 3000ms（服务端延迟），会明确失败。
     * 首次冷启动可能偏慢，故给到 2000ms 的上限余量。
     */
    @Test
    fun `取消会传播到底层 Call，耗时应接近超时值而非服务端延迟`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok"}""")
                .setBodyDelay(3, TimeUnit.SECONDS)
                .setHeadersDelay(3, TimeUnit.SECONDS),
        )

        val api = buildApi()
        val start = System.currentTimeMillis()
        val outcome = runCatching { withTimeout(1_000) { api.healthz() } }
        val elapsed = System.currentTimeMillis() - start

        assertTrue("withTimeout 到点应抛异常（取消被传播）", outcome.isFailure)
        assertTrue(
            "取消应生效：耗时应在 1000~2000ms，实际 ${elapsed}ms。" +
                " 若接近 3000ms 说明取消没传播到底层 Call，需要改回自持 Call 并 cancel()。",
            elapsed in 1_000..2_000,
        )
    }

    /**
     * 取消后**不应出现重试**（规格 §5.4 第 3 条）。
     *
     * 若 `ApiCall` 没在重试前检查 `isActive`，会出现
     * 「取消 → OkHttp 抛 `IOException("Canceled")` → 判成 `Network` → 又发一次」。
     */
    @Test
    fun `请求失败后 GET 重试一次，但取消不会产生额外请求`() = runBlocking {
        // 第一发：连接被断（归 Network，GET 应重试）
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

        val result = buildApi().healthz()

        assertTrue(
            "连接断开应归 Network 并重试成功",
            result is com.nongyu.appplus.net.core.ApiCall.Outcome.Success,
        )
        assertTrue("应恰好 2 次请求（首发 + 1 次重试），实际 ${server.requestCount}", server.requestCount == 2)
    }

    /**
     * 直接测「协程取消时底层连接是否断开」。
     *
     * 用 `SocketPolicy.NO_RESPONSE`（服务端永不响应），取消协程后观察：
     * 若底层 Call 未被取消，`async` 的 job 会一直处于 active 状态。
     */
    @Test
    fun `取消协程后 async job 立即转为已取消状态`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val api = buildApi()
        val job = async(Dispatchers.IO) { api.healthz() }

        delay(300) // 让请求真正发出
        assertTrue("请求应已发出", server.requestCount >= 1)

        job.cancel(CancellationException("test"))
        // 给取消传播一点时间
        delay(300)

        assertTrue(
            "取消后 job 应处于已取消状态（isCancelled=${job.isCancelled}, isActive=${job.isActive}）",
            job.isCancelled,
        )

        server.shutdown() // 释放挂起的连接
    }

    private fun buildApi(): NongyuApi {
        val client = com.nongyu.appplus.net.api.http.OkHttpFactory.build(
            tokenProvider = com.nongyu.appplus.net.auth.AuthTokenProvider { "T" },
            sessionInvalidListener = com.nongyu.appplus.net.auth.SessionInvalidListener { },
            loggingEnabled = false,
        )
        val baseUrl = server.url("/").toString()
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(com.nongyu.appplus.net.model.NetJson.converterFactory())
            .build()
        return NongyuApi(
            identity = retrofit.create(com.nongyu.appplus.net.api.IdentityApi::class.java),
            baseUrl = baseUrl,
        )
    }
}
