package com.nongyu.appplus.login.data.cas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CasDecrypt] 纯 JVM 单测（票 #95）。
 *
 * 真源：后端 `internal/school/schoolauth/casdecrypt.go` `DecryptYWTB`：
 * - `key = MD5(requestId + ":sicau-cas")` 的 **digest 原始 16 字节**（不是 hex 字符串）
 * - `iv = base64(data)` 的**前 16 字节**，其余为密文
 * - AES-128-CBC + **PKCS7**（后端 `stripPKCS7`），不是旧 RN 工具包的自定义尾填充
 *
 * 测试向量由独立第三方库（pycryptodome）预先生成并**写死为字面量**，
 * 不在测试运行时用产品实现重新加密 —— 否则会变成自己验自己的同义反复。
 */
class CasDecryptTest {

    companion object {
        // 独立生成的固定向量：明文 {"callbackUrl":"https://example.invalid/cb","st":"ST-TEST","users":[{"id":42}]}
        private const val REQUEST_ID = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        private const val DATA_B64 =
            "MDEyMzQ1Njc4OWFiY2RlZtwOY50O+HNIZGNlw5uY4CN484HF+UOsCloQsqDIOrBE7N0kgyU8j4LmqI7lXA+ZYkH/n3jSrCPA3aA4Hs3b1nS0j9JXnyTWyHo2qST0IVgd"
        private const val PLAIN =
            """{"callbackUrl":"https://example.invalid/cb","st":"ST-TEST","users":[{"id":42}]}"""
    }

    @Test
    fun `解密后得到预期明文`() {
        val plain = CasDecrypt.decrypt(REQUEST_ID, DATA_B64)
        assertTrue(plain != null)
        assertEquals(PLAIN, String(plain!!, Charsets.UTF_8))
    }

    @Test
    fun `空 requestId 拒绝解密`() {
        assertTrue(CasDecrypt.decrypt("", DATA_B64) == null)
    }

    @Test
    fun `非法 base64 拒绝解密`() {
        assertNull(CasDecrypt.decrypt(REQUEST_ID, "not-base64!!!"))
    }

    @Test
    fun `密文过短拒绝解密（不足两个块）`() {
        assertNull(CasDecrypt.decrypt(REQUEST_ID, "AAAAAAAAAAA="))
    }

    @Test
    fun `密文长度不是块倍数时拒绝`() {
        // 16 字节 IV + 17 字节非对齐密文
        val raw = ByteArray(33) { 1 }
        val b64 = java.util.Base64.getEncoder().encodeToString(raw)
        assertNull(CasDecrypt.decrypt(REQUEST_ID, b64))
    }

    @Test
    fun `填充非法时拒绝而不是静默截断`() {
        // 合法长度但解密出来的末字节会指示非法填充长度
        val req = "00000000000000000000000000000000"
        val raw = ByteArray(32) { 7 }
        val b64 = java.util.Base64.getEncoder().encodeToString(raw)
        assertNull(CasDecrypt.decrypt(req, b64))
    }

    @Test
    fun `code 非 200 时信封 data 不解密`() {
        val envelope = CasEnvelope(requestId = REQUEST_ID, code = 500, msg = "未登录", data = DATA_B64, id = null)
        assertNull(CasDecrypt.unwrap(envelope))
    }

    @Test
    fun `code 200 且 data 为 null 时不解密`() {
        val envelope = CasEnvelope(requestId = REQUEST_ID, code = 200, msg = "登录成功", data = null, id = null)
        assertNull(CasDecrypt.unwrap(envelope))
    }

    @Test
    fun `unwrap 返回原始明文不擅自剥引号`() {
        val envelope = CasEnvelope(requestId = REQUEST_ID, code = 200, data = DATA_B64, id = null)
        assertEquals(PLAIN, CasDecrypt.unwrap(envelope))
    }
}
