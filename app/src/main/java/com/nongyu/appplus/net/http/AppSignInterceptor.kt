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
 * 公开端点（登录、发验证码、healthz）也签名——后端只豁免探针，不豁免登录。
 */
class AppSignInterceptor(
    private val signer: AppRequestSigner,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
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
}
