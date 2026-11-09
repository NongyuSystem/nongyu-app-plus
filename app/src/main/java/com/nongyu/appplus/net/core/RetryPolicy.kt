package com.nongyu.appplus.net.core

/**
 * 重试判定（规格 §5.3）。**纯函数，纯 JVM 可测。**
 *
 * ## 硬约束：写操作永不重试
 * `POST` / `PATCH` / `DELETE` **一律不重试，无论状态码是什么**。
 * 这条没有例外 —— 重复提交比失败更糟。
 *
 * ## 为什么还要关 OkHttp 的开关
 * `OkHttpClient.Builder().retryOnConnectionFailure` **默认是 `true`**，
 * 它在「连接层失败、尚未收到任何响应」时会**重发请求，包括 POST**。
 * 也就是说即使本策略一个 POST 都不放行，OkHttp 自己仍可能把 POST 发两次。
 * 所以 `OkHttpFactory` 必须显式 `retryOnConnectionFailure(false)`，
 * 把重试权**完全**收进本策略。
 *
 * ## 不做指数退避
 * 只重试 1 次，退避没有意义。
 */
object RetryPolicy {

    /** 最多重试次数。 */
    const val MAX_RETRIES: Int = 1

    /** 重试间隔（毫秒）。 */
    const val RETRY_DELAY_MS: Long = 500L

    /**
     * 该 HTTP 方法是否**允许**在任何情况下重试。
     *
     * 只有幂等方法放行。判断大小写不敏感（`method` 由调用方传入，
     * 可能是 `"get"` 也可能是 `"GET"`）。
     */
    fun isRetryableMethod(method: String): Boolean = when (method.uppercase()) {
        "GET", "HEAD", "OPTIONS" -> true
        // 写操作一律不放行 —— 这条没有例外
        "POST", "PUT", "PATCH", "DELETE" -> false
        // 未知方法保守处理：不重试
        else -> false
    }

    /**
     * 综合判定：给定方法与失败类型，是否重试。
     *
     * @param method HTTP 方法，大小写不敏感
     * @param failure 上一次的失败
     */
    fun shouldRetry(method: String, failure: ApiFailure): Boolean {
        // ① 方法先否决：写操作无条件不重试，后面判什么都不看
        if (!isRetryableMethod(method)) return false

        // ② 再按失败类型
        return when (failure) {
            is ApiFailure.Network -> true
            is ApiFailure.Timeout -> true
            // 只有 5xx 值得重试；4xx 是客户端问题，重发也一样
            is ApiFailure.Http -> failure.status in 500..599
            // 业务拒绝、解析失败、已取消：重发没有意义（解析失败还会同样失败）
            is ApiFailure.Business -> false
            is ApiFailure.Parse -> false
            ApiFailure.Cancelled -> false
        }
    }
}
