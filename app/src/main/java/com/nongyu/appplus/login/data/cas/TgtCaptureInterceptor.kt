package com.nongyu.appplus.login.data.cas

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 从响应的 **`Set-Cookie` 头**里抓 `go-admin-tgt`。
 *
 * ## 为什么必须是"响应头"而不是 body
 * 权威文档 §1：`go-admin-tgt` 是 **HttpOnly** cookie，
 * - 页面 JS 的 `document.cookie` 读不到；
 * - 响应 **body** 里也没有它（body 是 CAS 信封 `{requestId,code,msg,data}`）。
 *
 * 只能从 `Set-Cookie` 取。把 body 当 cookie 值是**已被明确记录的坑**：
 * 后端会把它加密存进 Redis，之后每次换票都 500「未登录」，
 * 表现为「能登录、有姓名、画像其他字段全空」，且日志零线索。
 *
 * ## 为什么单独一个拦截器
 * OkHttp 的 `CookieJar` 也会收到 `Set-Cookie`，但它不暴露「刚收到的原始头」；
 * 而登录成功的判定需要**立刻**知道这一步有没有拿到 tgt（决定要不要报错）。
 * 用一个只读拦截器捕获原始头最直接，且不影响 cookie 罐的正常工作。
 */
class TgtCaptureInterceptor : Interceptor {

    @Volatile
    private var captured: String? = null

    /** 最近一次捕捉到的合法 tgt（可能为 null）。 */
    fun lastTgt(): String? = captured

    /** 发新请求前清掉旧值，避免把上一次的结果误当本次。 */
    fun reset() {
        captured = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val headers = response.headers.values("Set-Cookie")
        if (headers.isNotEmpty()) {
            TgtValidator.extractFromSetCookie(headers)?.let { captured = it }
        }
        return response
    }
}
