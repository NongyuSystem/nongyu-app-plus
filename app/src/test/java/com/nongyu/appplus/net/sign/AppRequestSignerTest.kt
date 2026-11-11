package com.nongyu.appplus.net.sign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 与 `nongyu-backend-plus` `appsign_test.go` 的 vectorSig 必须相同。
 * 改协议先改两端常量，再改实现。
 */
class AppRequestSignerTest {

    @Test
    fun `NY1 向量与后端一致`() {
        val signer = AppRequestSigner.dev()
        val signed = signer.signAt(
            method = "POST",
            path = "/identity/login/admin",
            rawQuery = "",
            body = "{\"email\":\"a@b.c\"}".toByteArray(),
            timestamp = 1_760_000_000L,
            nonce = "n-test-nonce-0001",
        )
        assertEquals("dev", signed.keyId)
        assertEquals("1760000000", signed.timestamp)
        assertEquals(
            "tZEyLjSrwEfsM0907M4ZzF_WlN0DhvmFOfT4I0FdH0A",
            signed.signature,
        )
    }

    @Test
    fun `路径或 body 变了签名就变`() {
        val signer = AppRequestSigner.dev()
        val a = signer.signAt("POST", "/identity/login", "", ByteArray(0), 1_760_000_000L, "n-test-nonce-0001")
        val b = signer.signAt("POST", "/identity/me", "", ByteArray(0), 1_760_000_000L, "n-test-nonce-0001")
        val c = signer.signAt("POST", "/identity/login", "", byteArrayOf(1), 1_760_000_000L, "n-test-nonce-0001")
        assertNotEquals(a.signature, b.signature)
        assertNotEquals(a.signature, c.signature)
    }
}
