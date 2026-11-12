package com.nongyu.appplus.login.data.cas

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 统一身份认证响应体解密（票 #95，纯 JVM、`javax.crypto` 原生实现）。
 *
 * ## 真源
 * 后端 `internal/school/schoolauth/casdecrypt.go` `DecryptYWTB`（2026-11-09 实测协议）：
 *
 * ```
 * 响应：{"requestId":"<32位hex>","code":200,"msg":"...","data":"<加密 base64>"}
 * key  = MD5(requestId + ":sicau-cas") 的 digest 原始 16 字节（AES-128）
 * iv   = base64(data) 的前 16 字节
 * ct   = 其余；AES-CBC + PKCS7（后端 stripPKCS7）
 * ```
 *
 * ## 与旧 RN 工具包的差异（别抄错）
 * 旧 RN `core/login/cas.ts` 用 CryptoJS 的 `pad.NoPadding` + 「末字节 N 表示去掉 N 字节」
 * 自定义填充；后端实现是**标准 PKCS7**。两者对同一密文的尾部处理不同，
 * 本客户端以后端 `casdecrypt.go` 为准（票 #95 要求「与后端契约一致」）。
 *
 * ## 容错
 * 任何一步失败都返回 `null`，不抛异常 —— 学校侧响应格式不稳定，
 * 解密失败应表现为「拿不到验证码 / 登录失败」，而不是让协程崩掉。
 */
object CasDecrypt {

    private const val SALT = ":sicau-cas"
    private const val BLOCK = 16

    /** 解密 `data` 字段的 base64，返回明文字节；失败返回 null。 */
    fun decrypt(requestId: String?, dataB64: String?): ByteArray? {
        if (requestId.isNullOrEmpty() || dataB64.isNullOrEmpty()) return null
        return try {
            val raw = Base64.getDecoder().decode(dataB64)
            if (raw.size < BLOCK * 2) return null       // IV + 至少一个密文块

            val key = MessageDigest.getInstance("MD5")
                .digest((requestId + SALT).toByteArray(Charsets.UTF_8))
            require(key.size == BLOCK) { "MD5 digest must be 16 bytes" }

            val iv = raw.copyOfRange(0, BLOCK)
            val ct = raw.copyOfRange(BLOCK, raw.size)
            if (ct.size % BLOCK != 0) return null

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // Java 的 PKCS5Padding == PKCS7
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv),
            )
            cipher.doFinal(ct)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: java.security.GeneralSecurityException) {
            null
        }
    }

    /**
     * 拆信封：`code == 200` 且 `data` 非空才解密，否则返回 `null`。
     *
     * 泛型 `T` 仅为调用方标注期望类型（便于阅读），实际返回解密后的明文字串，
     * 由调用方自行用 `NetJson` 解析成具体 payload。这样解密层不依赖序列化框架细节。
     */
    fun <T : Any> unwrap(envelope: CasEnvelope, @Suppress("UNUSED_PARAMETER") type: kotlin.reflect.KClass<T>): String? {
        if (envelope.code != 200) return null
        val data = envelope.data ?: return null
        val plain = decrypt(envelope.requestId, data) ?: return null
        return String(plain, Charsets.UTF_8)
    }
}
