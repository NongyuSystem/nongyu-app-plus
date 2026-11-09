package com.nongyu.appplus.net.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * [ApiCall] 的纯 JVM 单测（规格 §5.1、§5.4）。
 *
 * 核心是验证 **catch 顺序**与**重试执行**：
 * - `CancellationException` 必须重抛，不能变成返回值；
 * - `SerializationException` → `Parse`；
 * - `IOException("Canceled")` → `Cancelled`（不是 Network，否则会触发重试）；
 * - `POST` 即使 500 也只发一次。
 */
class ApiCallTest {

    private val json = "application/json".toMediaType()

    private fun ok(body: String): Response<String> =
        Response.success(body)

    private fun err(status: Int, code: String?, message: String?): Response<String> =
        Response.error("".toResponseBody(json), okhttp3.Response.Builder()
            .code(status)
            .message("err")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://h/x").build())
            .build())

    // ---------- 成功路径 ----------

    @Test
    fun `2xx 返回 Success`() = runTest {
        val r = ApiCall.execute("GET", "/x") { ok("hello") }
        assertTrue(r is ApiCall.Outcome.Success)
        assertEquals("hello", (r as ApiCall.Outcome.Success).value)
    }

    @Test
    fun `2xx 但 body 为 null 归 Parse 而不是静默成功`() = runTest {
        val r = ApiCall.execute<String>("GET", "/x") { Response.success(null) }
        assertTrue(r is ApiCall.Outcome.Failure)
        assertTrue((r as ApiCall.Outcome.Failure).failure is ApiFailure.Parse)
    }

    // ---------- 错误信封 ----------

    @Test
    fun `非 2xx 走 Http 并且 errorBody 被解析`() = runTest {
        val parser = ApiCall.ErrorEnvelopeParser { ApiCall.ParsedEnvelope("unauthorized", "no token") }
        val r = ApiCall.execute("GET", "/x?secret=abc", parser) { err(401, "unauthorized", "no token") }

        val f = (r as ApiCall.Outcome.Failure).failure as ApiFailure.Http
        assertEquals(401, f.status)
        assertEquals("unauthorized", f.code)
        assertEquals("no token", f.message)
        // URL 必须已剥 query
        assertEquals("/x", f.url)
    }

    @Test
    fun `errorBody 解析不出时 code 与 message 为 null`() = runTest {
        val parser = ApiCall.ErrorEnvelopeParser { null }
        val r = ApiCall.execute("GET", "/x", parser) { err(404, null, null) }

        val f = (r as ApiCall.Outcome.Failure).failure as ApiFailure.Http
        assertEquals(404, f.status)
        assertEquals(null, f.code)
    }

    // ---------- catch 顺序 ----------

    @Test
    fun `SerializationException 归 Parse`() = runTest {
        val r = ApiCall.execute<String>("GET", "/x") { throw SerializationException("bad json") }
        val f = (r as ApiCall.Outcome.Failure).failure
        assertTrue("应为 Parse，实际 ${f::class.simpleName}", f is ApiFailure.Parse)
    }

    @Test
    fun `SocketTimeoutException 归 Timeout 而不是 Network`() = runTest {
        val r = ApiCall.execute<String>("GET", "/x") { throw SocketTimeoutException("read timeout") }
        val f = (r as ApiCall.Outcome.Failure).failure
        assertTrue("应为 Timeout，实际 ${f::class.simpleName}", f is ApiFailure.Timeout)
    }

    @Test
    fun `InterruptedIOException 归 Timeout`() = runTest {
        val r = ApiCall.execute<String>("GET", "/x") { throw InterruptedIOException("timeout") }
        val f = (r as ApiCall.Outcome.Failure).failure
        assertTrue("应为 Timeout，实际 ${f::class.simpleName}", f is ApiFailure.Timeout)
    }

    @Test
    fun `普通 IOException 归 Network`() = runTest {
        val r = ApiCall.execute<String>("GET", "/x") { throw IOException("connection refused") }
        val f = (r as ApiCall.Outcome.Failure).failure
        assertTrue("应为 Network，实际 ${f::class.simpleName}", f is ApiFailure.Network)
    }

    @Test
    fun `IOException Canceled 归 Cancelled 而不是 Network`() = runTest {
        // 这条关键：若归成 Network，RetryPolicy 会放行重试 → 取消后请求又发一次
        val r = ApiCall.execute<String>("GET", "/x") { throw IOException("Canceled") }
        val f = (r as ApiCall.Outcome.Failure).failure
        assertEquals("应为 Cancelled，实际 ${f::class.simpleName}", ApiFailure.Cancelled, f)
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException 必须重抛而不是变成返回值`() = runTest {
        ApiCall.execute<String>("GET", "/x") { throw CancellationException("cancelled by caller") }
    }

    @Test
    fun `未知异常兜底进 Parse，不崩`() = runTest {
        val r = ApiCall.execute<String>("GET", "/x") { throw IllegalStateException("weird") }
        val f = (r as ApiCall.Outcome.Failure).failure
        assertTrue("应为 Parse，实际 ${f::class.simpleName}", f is ApiFailure.Parse)
    }

    // ---------- 重试执行 ----------

    @Test
    fun `GET 网络失败会重试一次`() = runTest {
        var calls = 0
        val r = ApiCall.execute("GET", "/x") {
            calls++
            if (calls == 1) throw IOException("first fails") else ok("ok")
        }
        assertEquals("应重试一次后成功", 2, calls)
        assertTrue(r is ApiCall.Outcome.Success)
    }

    @Test
    fun `GET 一直失败时最多发两次`() = runTest {
        var calls = 0
        val r = ApiCall.execute<String>("GET", "/x") {
            calls++
            throw IOException("always fails")
        }
        assertEquals("首发 + 1 次重试 = 2", 2, calls)
        assertTrue(r is ApiCall.Outcome.Failure)
    }

    @Test
    fun `POST 网络失败也只发一次`() = runTest {
        var calls = 0
        ApiCall.execute<String>("POST", "/x") {
            calls++
            throw IOException("fails")
        }
        assertEquals("写操作绝不重试", 1, calls)
    }

    @Test
    fun `POST 500 也只发一次`() = runTest {
        var calls = 0
        ApiCall.execute<String>("POST", "/x") {
            calls++
            err(500, "internal_error", "boom")
        }
        assertEquals("写操作即使 5xx 也不重试", 1, calls)
    }

    @Test
    fun `GET 401 不重试`() = runTest {
        var calls = 0
        ApiCall.execute<String>("GET", "/x") {
            calls++
            err(401, "unauthorized", "no")
        }
        assertEquals("4xx 不重试", 1, calls)
    }
}
