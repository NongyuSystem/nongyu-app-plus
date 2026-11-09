package com.nongyu.appplus.net.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * [RetryPolicy] 的纯 JVM 单测（规格 §7.1）。
 *
 * 重点覆盖两条硬约束：
 * 1. 写操作（POST/PATCH/DELETE）**无论状态码是什么**都不重试。
 * 2. 只有 5xx 值得重试，4xx 不重试。
 */
class RetryPolicyTest {

    // ---------- 硬约束：写操作永不重试 ----------

    @Test
    fun `POST 永不重试，即使 500`() {
        val f = ApiFailure.Http(500, "internal_error", "boom", "/x")
        assertFalse(RetryPolicy.shouldRetry("POST", f))
    }

    @Test
    fun `POST 永不重试，即使网络失败`() {
        assertFalse(RetryPolicy.shouldRetry("POST", ApiFailure.Network(IOException("net"))))
    }

    @Test
    fun `POST 永不重试，即使超时`() {
        assertFalse(RetryPolicy.shouldRetry("POST", ApiFailure.Timeout(IOException("to"))))
    }

    @Test
    fun `PATCH 与 DELETE 同样永不重试`() {
        val f = ApiFailure.Network(IOException("net"))
        assertFalse(RetryPolicy.shouldRetry("PATCH", f))
        assertFalse(RetryPolicy.shouldRetry("DELETE", f))
    }

    @Test
    fun `方法名大小写不敏感`() {
        val f = ApiFailure.Network(IOException("net"))
        assertFalse(RetryPolicy.shouldRetry("post", f))
        assertTrue(RetryPolicy.shouldRetry("get", f))
    }

    // ---------- GET 按失败类型分级 ----------

    @Test
    fun `GET 网络失败可重试`() {
        assertTrue(RetryPolicy.shouldRetry("GET", ApiFailure.Network(IOException("net"))))
    }

    @Test
    fun `GET 超时可重试`() {
        assertTrue(RetryPolicy.shouldRetry("GET", ApiFailure.Timeout(SocketTimeoutException())))
    }

    @Test
    fun `GET 5xx 可重试，4xx 不可`() {
        assertTrue(RetryPolicy.shouldRetry("GET", ApiFailure.Http(500, null, null, "/x")))
        assertTrue(RetryPolicy.shouldRetry("GET", ApiFailure.Http(599, null, null, "/x")))
        assertFalse(RetryPolicy.shouldRetry("GET", ApiFailure.Http(400, null, null, "/x")))
        assertFalse(RetryPolicy.shouldRetry("GET", ApiFailure.Http(401, null, null, "/x")))
        assertFalse(RetryPolicy.shouldRetry("GET", ApiFailure.Http(404, null, null, "/x")))
    }

    @Test
    fun `GET 解析失败不重试`() {
        // 重发只会得到同样的坏响应，还会掩盖「后端改了字段」这个真实问题
        assertFalse(RetryPolicy.shouldRetry("GET", ApiFailure.Parse(RuntimeException(), null)))
    }

    @Test
    fun `GET 业务拒绝不重试`() {
        assertFalse(RetryPolicy.shouldRetry("GET", ApiFailure.Business("x", "y")))
    }

    @Test
    fun `已取消不重试`() {
        assertFalse(RetryPolicy.shouldRetry("GET", ApiFailure.Cancelled))
    }

    @Test
    fun `未知方法保守不重试`() {
        assertFalse(RetryPolicy.shouldRetry("TRACE", ApiFailure.Network(IOException("net"))))
    }

    @Test
    fun `常量值是规格钉的 1 次 500ms`() {
        assertEquals(1, RetryPolicy.MAX_RETRIES)
        assertEquals(500L, RetryPolicy.RETRY_DELAY_MS)
    }
}
