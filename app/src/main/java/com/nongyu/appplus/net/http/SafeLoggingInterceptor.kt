package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.core.NongyuLogger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets

/**
 * 脱敏日志拦截器（规格 §6.3、§6.4）。
 *
 * ## 为什么需要自写（`#56` 交叉核对的核心结论之一）
 * `#49` Q3 原写「脱敏拦截器必须挂在日志拦截器之前」——**这条对请求头不成立、对请求体成立**。
 *
 * - **头**：`HttpLoggingInterceptor` 4.12.1 自带 `redactHeader()`（大小写不敏感，
 *   只改日志输出、不改真实请求），所以头脱敏**不存在顺序问题**。本类的头脱敏是**双保险**
 *   （官方内置遮蔽不含 `Proxy-Authorization`）。
 * - **体**：同一个 `Request` 对象既被记录又被真正发送。若在**外层**拦截器改它，
 *   `Authorization` 会变成 `***` 导致服务端 401；若不改，日志里就是原始敏感值。
 *   **两个拦截器串联在 BODY 级必然二选一失败**。
 *   所以体必须由**一个**拦截器在**拷贝**上处理、只打印不改原对象。
 *
 * ## 三条实现纪律
 * 1. **绝不修改传入的 request / response** —— 只在副本上读。
 * 2. **读 body 用缓冲，不消费原 body** —— 请求体在 `Interceptor` 里只能读一次；
 *    响应体必须用 `peekBody()`，否则下游拿不到 body。
 * 3. **路径白名单优先于字段黑名单** —— 黑名单对后端新增字段天然失效
 *    （`#46` 证明后端仍在密集重写），登录类端点整体不记 body 是唯一对未来免疫的做法。
 *
 * ## 只在 debug 挂
 * 由装配处传 `enabled`，不靠运行时 `BuildConfig.DEBUG`（规格 §6.3）。
 *
 * @param logger 日志出口。测试可传收集器断言「输出里不含明文」。
 */
class SafeLoggingInterceptor(
    private val enabled: Boolean,
    private val logger: NongyuLogger = NongyuLogger.Noop,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())

        val request = chain.request()
        val path = request.url.encodedPath

        // ---- 请求行 + 头（头已脱敏） ----
        d("request: ${request.method} ${redactUrl(request.url.toString())}")
        logHeaders(request.headers)

        // ---- 请求体（路径白名单优先） ----
        val requestBody = readRequestBodySafely(request)
        if (requestBody != null) {
            d("request body: ${renderBody(path, requestBody)}")
        }

        val response = chain.proceed(request)
        d("response: ${response.code} ${request.method} ${redactUrl(request.url.toString())}")
        logHeaders(response.headers)

        // ---- 响应体（peekBody，不消费） ----
        if (!shouldLogBody(path)) {
            d("response body: <redacted by path>")
        } else {
            val preview = try {
                response.peekBody(MAX_BODY_BYTES).string()
            } catch (e: Exception) {
                "<unreadable: ${e::class.simpleName}>"
            }
            if (preview.isNotEmpty()) {
                d("response body: ${redactJsonKeys(preview).take(MAX_LOG_CHARS)}")
            }
        }

        return response
    }

    private fun d(message: String) {
        logger.log(NongyuLogger.Level.DEBUG, message)
    }

    // ---------- 头 ----------

    private fun logHeaders(headers: Headers) {
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = if (isSensitiveHeader(name)) MASK else headers.value(i)
            d("  $name: $value")
        }
    }

    // ---------- 体 ----------

    /**
     * 读请求体。
     *
     * 请求体只能读一次，所以这里用 `Buffer` 把内容复制出来（不消费原 body ——
     * `Buffer` 是从 `body.writeTo(buffer)` 写出来的副本，原 body 仍可被下游写出）。
     */
    private fun readRequestBodySafely(request: Request): String? {
        val body = request.body ?: return null
        if (!shouldLogBody(request.url.encodedPath)) return "<redacted by path>"
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            if (buffer.size == 0L) return null
            buffer.readString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "<unreadable: ${e::class.simpleName}>"
        }
    }

    /** 白名单命中时整体不打；否则按 JSON key 脱敏并截断。 */
    private fun renderBody(path: String, body: String): String =
        if (!shouldLogBody(path)) "<redacted by path>" else redactJsonKeys(body).take(MAX_LOG_CHARS)

    // ---------- 判据 ----------

    private fun isSensitiveHeader(name: String): Boolean = name.lowercase() in SENSITIVE_HEADERS

    /**
     * 该路径是否「整体不记 body」。
     *
     * 登录类端点整体不记 —— 这是唯一对未来新增敏感字段免疫的做法。
     */
    private fun shouldLogBody(path: String): Boolean =
        BODY_BLACKLIST_PATHS.none { path.startsWith(it) }

    /** URL 里的 query 统一剥掉（规格 §6：日志可带 URL，但剥 query）。 */
    private fun redactUrl(url: String): String {
        val q = url.indexOf('?')
        return if (q >= 0) url.substring(0, q) else url
    }

    /** 按 JSON key 粗粒度遮蔽。不追求完美 JSON 解析 —— 这里只是日志。 */
    private fun redactJsonKeys(body: String): String {
        var result = body
        for (key in SENSITIVE_JSON_KEYS) {
            result = result.replace(
                Regex("\"$key\"\\s*:\\s*\"[^\"]*\""),
                "\"$key\":\"$MASK\"",
            )
        }
        return result
    }

    companion object {
        private const val MASK = "***"
        private const val MAX_BODY_BYTES = 64L * 1024
        private const val MAX_LOG_CHARS = 200

        private val SENSITIVE_HEADERS = setOf(
            "authorization", "cookie", "set-cookie", "proxy-authorization",
            "x-nongyu-signature",
        )

        private val SENSITIVE_JSON_KEYS = listOf(
            "password", "wechatCode", "ticket", "cookie", "goAdminTgt",
            "idCardSuffix4", "token", "accessToken", "refreshToken",
        )

        /** 整体不记 body 的路径（前缀匹配）。 */
        private val BODY_BLACKLIST_PATHS = listOf(
            "/identity/login",
            "/identity/login/admin",
            "/identity/mailcode/send",
        )
    }
}
