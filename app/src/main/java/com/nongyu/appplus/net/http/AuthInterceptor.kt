package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.core.NongyuLogger
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException

/**
 * 鉴权拦截器：挂 `Authorization`，并在**无 token 时按分区短路**（规格 §4.2）。
 *
 * ## 分区判定
 * - 标了 [PublicEndpoint] 的方法 → **照发**，不挂 `Authorization`。
 * - 其余方法 → 没 token 就**不发**，直接抛 [MissingCredentialException]。
 *
 * 判据读的是 Retrofit 自动附加的 [Invocation] 上的方法注解，而**不是**路径前缀 ——
 * 路径前缀白名单会在后端改路径时静默失效。
 *
 * ## 为什么「不发」而不是「发了拿 401」
 * 一期没有登录流程，没有 token 是常态。让每个缺票请求都跑一趟网络只为拿 401，
 * 既浪费往返也污染日志。本地直接失败更快、更清晰。
 *
 * ## 抛出的异常归到哪
 * [MissingCredentialException] 是 `IOException` 子类，`ApiCall` 的 `catch (IOException)`
 * 会把它归到 `ApiFailure.Network`（message 不是 `"Canceled"`，所以不是 `Cancelled`）。
 */
class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
    private val logger: NongyuLogger = NongyuLogger.Noop,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Retrofit 会把 Invocation 挂到 request 的 tag 上；非 Retrofit 请求没有 tag，视为需鉴权
        val invocation = request.tag(Invocation::class.java)
        val isPublic = invocation?.method()?.isAnnotationPresent(PublicEndpoint::class.java) == true

        if (isPublic) {
            logger.log(NongyuLogger.Level.DEBUG, "公开端点，不挂票：${request.url.encodedPath}")
            return chain.proceed(request)
        }

        val token = tokenProvider.currentToken()
        if (token.isNullOrBlank()) {
            logger.log(NongyuLogger.Level.DEBUG, "鉴权端点无 token，本地短路：${request.url.encodedPath}")
            throw MissingCredentialException(request.url.encodedPath)
        }

        val authorized = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authorized)
    }
}

/** 鉴权端点缺少令牌。 */
class MissingCredentialException(path: String) :
    IOException("缺少凭证：鉴权端点需要 token，但 AuthTokenProvider 返回空（path=$path）")
