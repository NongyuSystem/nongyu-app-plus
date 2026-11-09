package com.nongyu.appplus.net.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DefaultSessionInvalidListener] 的纯 JVM 单测（规格 §4.4）。
 *
 * 用假 [SessionStore] 避免依赖 Keystore —— 这正是把存储抽象成接口的目的。
 * 重点验证**并发去重**：同一屏并发 5 个 401 只能清理一次。
 */
class DefaultSessionInvalidListenerTest {

    /** 假存储：记录 clear 次数。 */
    private class FakeStore : SessionStore {
        var token: String? = "initial"
        var clearCount = 0

        override fun readToken(): String? = token
        override fun writeToken(token: String) { this.token = token }
        override fun clear() { clearCount++; token = null }
    }

    @Test
    fun `首次失效会清掉令牌`() {
        val store = FakeStore()
        val listener = DefaultSessionInvalidListener(store)

        listener.onSessionInvalid()

        assertEquals(1, store.clearCount)
        assertEquals(null, store.token)
    }

    @Test
    fun `并发多次失效只清一次`() {
        val store = FakeStore()
        val listener = DefaultSessionInvalidListener(store)

        // 模拟同一屏 5 个请求同时 401
        repeat(5) { listener.onSessionInvalid() }

        assertEquals("守门标志应让后续回调全部丢弃", 1, store.clearCount)
    }

    @Test
    fun `真并发也只清一次`() {
        val store = FakeStore()
        val listener = DefaultSessionInvalidListener(store)
        val threads = (1..16).map { Thread { listener.onSessionInvalid() } }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals("AtomicBoolean 应保证只有首个线程生效", 1, store.clearCount)
    }

    @Test
    fun `reset 之后可以再次触发`() {
        val store = FakeStore()
        val listener = DefaultSessionInvalidListener(store)

        listener.onSessionInvalid()
        assertEquals(1, store.clearCount)

        // 复位只在「清理完成 + 下次带新 token 的请求发起前」调用
        store.writeToken("new-token")
        listener.reset()

        listener.onSessionInvalid()
        assertEquals(2, store.clearCount)
    }

    @Test
    fun `isTriggered 反映守门状态`() {
        val store = FakeStore()
        val listener = DefaultSessionInvalidListener(store)

        assertFalse(listener.isTriggered())
        listener.onSessionInvalid()
        assertTrue(listener.isTriggered())
        listener.reset()
        assertFalse(listener.isTriggered())
    }

    @Test
    fun `store 抛异常时不向外传播`() {
        val throwing = object : SessionStore {
            override fun readToken(): String? = null
            override fun writeToken(token: String) = Unit
            override fun clear() = throw RuntimeException("store broken")
        }
        val listener = DefaultSessionInvalidListener(throwing)

        // 不应抛出去 —— 失效清理失败不能连带把调用方弄崩
        listener.onSessionInvalid()
    }

    @Test
    fun `清理完成后会回调 onCleared`() {
        val store = FakeStore()
        var cleared = 0
        val listener = DefaultSessionInvalidListener(store) { cleared++ }

        listener.onSessionInvalid()

        assertEquals(1, cleared)
    }
}
