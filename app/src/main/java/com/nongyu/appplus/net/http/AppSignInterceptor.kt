package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.sign.AppRequestSigner
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * 给即将发出的请求挂 NY1 签名头。
 *
 * 放在 [AuthInterceptor] 之内、[SafeLoggingInterceptor] 之外：
 * 没 token 的鉴权端点在外层已经短路，不会白签；日志层看得到签名头并负责遮蔽。
 *
 * ## ⚠️ 签名豁免路径（现行契约 `docs/后端接口与NY1签名对接契约.md` §1.4）
 * **仅** `/healthz` 与 `/status` 免签名 —— 后端给探针留的门。
 * 其它一切请求都要签名，**包括登录、发验证码这些「公开」端点**：
 * 「公开」只是不需要 `Authorization: Bearer`，不等于免签名。
 *
 * 注意这与 [com.nongyu.appplus.net.http.PublicEndpoint] 是两套概念：
 * 那个注解只决定要不要挂令牌，不决定要不要签名。
 */
class AppSignInterceptor(
    private val signer: AppRequestSigner,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 唯二豁免：后端 appSign middleware 不校验这两条，客户端也不该挂签名头。
        // 用 encodedPath 精确匹配，不用前缀 —— 防止 /healthz/xxx 之类被误豁免。
        if (request.url.encodedPath in SIGN_EXEMPT_PATHS) {
            return chain.proceed(request)
        }

        val body = request.body
        val bytes = if (body == null) {
            ByteArray(0)
        } else {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        }
        val signed = signer.sign(
            method = request.method,
            path = request.url.encodedPath,
            rawQuery = request.url.encodedQuery ?: "",
            body = bytes,
        )
        val builder = request.newBuilder()
            .header(AppRequestSigner.HEADER_KEY_ID, signed.keyId)
            .header(AppRequestSigner.HEADER_TIMESTAMP, signed.timestamp)
            .header(AppRequestSigner.HEADER_NONCE, signed.nonce)
            .header(AppRequestSigner.HEADER_SIGNATURE, signed.signature)
        if (body != null) {
            builder.method(request.method, bytes.toRequestBody(body.contentType()))
        }
        return chain.proceed(builder.build())
    }

    private companion object {
        /** 与后端 `internal/server/appsign.go` 的豁免表逐字一致。 */
        val SIGN_EXEMPT_PATHS = setOf("/healthz", "/status")
    }
}
