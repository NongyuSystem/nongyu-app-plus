package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.ApiErrorCode
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.core.SessionInvalidRules
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 失效检测拦截器：读最终响应，按判据决定是否回调 [SessionInvalidListener]（规格 §4.3）。
 *
 * ## 为什么放最外层
 * 它要判的是**最终响应**（带过票之后的真实结果）。若放在 [AuthInterceptor] 之内，
 * 看到的可能是「还没加票」的中间态。
 *
 * ## 判定权不在本类
 * 「什么算失效」由 [SessionInvalidRules] 判定（域前缀 + HTTP 状态码 + code 三者都要读）。
 * 本类只负责：解析响应里的 code → 交给判据 → 按结论回调。
 *
 * ## ⚠️ 这是客户端单方面的协议补丁
 * `session_expired` 同一字面量被 identity 域（App 会话过期）与 jiaowu 域（上游教务会话失效）
 * 复用，同为 401。只看 code 会在教务掉线时把用户踢出登录。
 * 后端将来若给其中一个正名，[SessionInvalidRules] 必须同步改 —— **不要把这里说成「后端保证」**。
 *
 * ## 并发去重不在本类
 * 去重由 `DefaultSessionInvalidListener` 的 `AtomicBoolean` 守门负责，本类可以放心每次都调。
 */
class SessionInvalidInterceptor(
    private val listener: SessionInvalidListener,
    private val logger: NongyuLogger = NongyuLogger.Noop,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 只关心「可能是会话失效」的响应，避免为每个 200 都解析 body
        if (response.code != 401 && response.code != 403 && response.code != 503) {
            return response
        }

        val path = request.url.encodedPath
        val code = peekErrorCode(response)
        val decision = SessionInvalidRules.decide(path, response.code, code)

        when {
            SessionInvalidRules.shouldClearSession(decision) -> {
                logger.log(
                    NongyuLogger.Level.WARN,
                    "判定为 App 会话失效：path=$path status=${response.code} code=$code",
                )
                try {
                    listener.onSessionInvalid()
                } catch (e: RuntimeException) {
                    // 回调失败不能连带把这次请求弄崩
                    logger.log(NongyuLogger.Level.WARN, "会话失效回调异常", e)
                }
            }

            decision == SessionInvalidRules.Decision.KEEP_SESSION_SCHOOL_AUTH -> {
                // 教务会话失效：只记日志，绝不清 App 登录态
                logger.log(
                    NongyuLogger.Level.INFO,
                    "上游教务会话失效（不影响 App 登录态）：path=$path",
                )
            }
        }

        return response
    }

    /**
     * 从错误响应体里抠出 `code`。
     *
     * 用 `peekBody` 而不是 `body.string()` —— 后者会消费流，让下游拿不到 body。
     * 解析失败返回 `null`（判据会走保守路径：不清登录态）。
     */
    private fun peekErrorCode(response: Response): String? = try {
        val body = response.peekBody(MAX_PEEK_BYTES).string()
        if (body.isBlank()) {
            null
        } else {
            // 只做最小解析：找 "code" 后的第一个字符串值。
            // 完整 JSON 解析在 net/model 层（需要 Json 实例），本拦截器不引依赖。
            Regex("\"code\"\\s*:\\s*\"([^\"]*)\"")
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotEmpty() && ApiErrorCode.isKnown(it) || it.isNotEmpty() }
        }
    } catch (e: Exception) {
        logger.log(NongyuLogger.Level.DEBUG, "读取错误码失败：${e::class.simpleName}")
        null
    }

    private companion object {
        const val MAX_PEEK_BYTES = 8L * 1024
    }
}
