package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.NongyuLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient 装配（规格 §5.2、§5.3、§6.4）。
 *
 * **所有超时与拦截器只在这里定义，不得散落到调用点。**
 */
object OkHttpFactory {

    // ---------- 超时（规格 §5.2） ----------

    /** 连接超时：OkHttp 默认值，够用。 */
    const val CONNECT_TIMEOUT_SECONDS = 10L

    /** 读超时：给后端首次请求连库留余量。 */
    const val READ_TIMEOUT_SECONDS = 15L

    /** 写超时。 */
    const val WRITE_TIMEOUT_SECONDS = 15L

    /**
     * 整体调用超时（**必须设**）。
     *
     * OkHttp 默认 `callTimeout = 0`（不限总时长）—— 移动网络切换时，
     * 一个卡住的请求可能**永远不返回**。30s 是兜底上限。
     */
    const val CALL_TIMEOUT_SECONDS = 30L

    /**
     * 构建客户端。
     *
     * ## 拦截器顺序（由外到内）
     * ```
     * SessionInvalidInterceptor  最外：要判的是「最终响应」
     * AuthInterceptor            加 Authorization / 分区短路
     * SafeLoggingInterceptor     最内：只读拷贝
     * ```
     *
     * ## 为什么 `retryOnConnectionFailure(false)` 是硬约束
     * OkHttp 这个开关**默认是 `true`**，它在「连接层失败、尚未收到任何响应」时会
     * **重发请求，包括 POST**。也就是说即使 `RetryPolicy` 一个 POST 都不放行，
     * OkHttp 自己仍可能把 POST 发两次。要真的做到「POST 绝不重试」，
     * 必须全局关掉它，把重试权**完全**收进 `RetryPolicy`。
     *
     * @param loggingEnabled 是否挂日志拦截器。**由装配处按源集决定，不用运行时 `BuildConfig.DEBUG`**：
     *   debug 源集传 `true`，release 源集传 `false`（结构性隔离）。
     */
    fun build(
        tokenProvider: AuthTokenProvider,
        sessionInvalidListener: SessionInvalidListener,
        loggingEnabled: Boolean,
        logger: NongyuLogger = NongyuLogger.Noop,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 硬约束：关掉 OkHttp 自带的重试，否则 POST 仍可能被重发两次。
        .retryOnConnectionFailure(false)
        // 拦截器：由外到内
        .addInterceptor(SessionInvalidInterceptor(sessionInvalidListener, logger))
        .addInterceptor(AuthInterceptor(tokenProvider, logger))
        .addInterceptor(SafeLoggingInterceptor(enabled = loggingEnabled, logger = logger))
        .build()
}
