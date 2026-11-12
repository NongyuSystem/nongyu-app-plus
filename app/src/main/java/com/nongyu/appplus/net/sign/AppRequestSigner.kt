package com.nongyu.appplus.net.sign

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NY1 请求签名。与 `nongyu-backend-plus` 的 `server.SignAppRequest` 逐字节对齐。
 *
 * 这层证明「请求来自持有当前密钥的客户端」，不证明用户是谁。用户令牌仍由
 * [com.nongyu.appplus.net.api.http.AuthInterceptor] 挂 `Authorization`。
 *
 * 无 GMS 设备做不到平台证明，这把密钥进 APK 后可被提取。目标是可轮换
 * （换 kid），不是不可提取。不要把字符串拆开当成保管手段。
 *
 * 规范化串以 `NY1` 开头，字段依次是 keyId、unix 秒、nonce、方法、路径、raw query、body 的 sha256 十六进制，各以换行分隔。
 */
class AppRequestSigner(
    private val keyId: String,
    private val key: ByteArray,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    private val nextNonce: () -> String = { randomNonce() },
) {
    init {
        require(keyId.isNotEmpty()) { "keyId 不能为空" }
        require(key.size >= 32) { "签名密钥至少 32 字节" }
    }

    fun sign(method: String, path: String, rawQuery: String, body: ByteArray): SignedRequest =
        signAt(method, path, rawQuery, body, nowSeconds(), nextNonce())

    /** 测试注入固定时间戳与 nonce，生产走 [sign]。 */
    fun signAt(
        method: String,
        path: String,
        rawQuery: String,
        body: ByteArray,
        timestamp: Long,
        nonce: String,
    ): SignedRequest {
        val canonical = canonical(keyId, timestamp.toString(), nonce, method, path, rawQuery, body)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val signature = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
        return SignedRequest(keyId, timestamp.toString(), nonce, signature)
    }

    data class SignedRequest(
        val keyId: String,
        val timestamp: String,
        val nonce: String,
        val signature: String,
    )

    companion object {
        const val HEADER_KEY_ID = "X-Nongyu-Key-Id"
        const val HEADER_TIMESTAMP = "X-Nongyu-Timestamp"
        const val HEADER_NONCE = "X-Nongyu-Nonce"
        const val HEADER_SIGNATURE = "X-Nongyu-Signature"

        /** 与后端 `.env.example` 的开发密钥是同一把。不是生产秘密。 */
        const val DEV_KEY_ID = "dev"
        const val DEV_KEY_B64 = "bm9uZ3l1LWRldi1hcHAtc2lnbi1rZXktdjEtMzJiISE"

        fun dev(): AppRequestSigner = fromBase64(DEV_KEY_ID, DEV_KEY_B64)

        fun fromBase64(keyId: String, keyB64: String): AppRequestSigner =
            AppRequestSigner(keyId, Base64.getUrlDecoder().decode(keyB64))

        fun canonical(
            keyId: String,
            timestamp: String,
            nonce: String,
            method: String,
            path: String,
            rawQuery: String,
            body: ByteArray,
        ): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(body)
            val hex = hash.joinToString("") { "%02x".format(it) }
            return "NY1\n$keyId\n$timestamp\n$nonce\n${method.uppercase()}\n$path\n$rawQuery\n$hex\n"
        }

        fun randomNonce(): String {
            val raw = ByteArray(16)
            SecureRandom().nextBytes(raw)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        }
    }
}
