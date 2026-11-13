package com.nongyu.appplus.login.data.cas

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CaptchaImageDecoder] 纯 JVM 单测（票 #96 缺口补齐）。
 *
 * 用一张 1×1 PNG 的最小真实 base64 作为独立真源，验证「data URI → PNG 字节」。
 */
class CaptchaImageDecoderTest {

    companion object {
        /** 1×1 透明 PNG 的 base64（独立于产品实现写死的字面量）。 */
        private const val PNG_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        private const val DATA_URI = "data:image/png;base64,$PNG_B64"

        /** PNG 魔数：89 50 4E 47。 */
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    }

    @Test
    fun `标准 data URI 解出 PNG 字节`() {
        val bytes = CaptchaImageDecoder.decode(DATA_URI)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 8)
        assertTrue(bytes.take(4).toByteArray().contentEquals(PNG_MAGIC))
    }

    @Test
    fun `带外层引号与空白也能解`() {
        // 真站解密产物带 JSON 引号（S6 已剥，这里兜底再防一次）
        val bytes = CaptchaImageDecoder.decode("  \"$DATA_URI\"  ")
        assertNotNull(bytes)
    }

    @Test
    fun `空输入返回 null`() {
        assertNull(CaptchaImageDecoder.decode(null))
        assertNull(CaptchaImageDecoder.decode(""))
        assertNull(CaptchaImageDecoder.decode("   "))
    }

    @Test
    fun `不是 data URI 返回 null`() {
        assertNull(CaptchaImageDecoder.decode("https://example.invalid/a.png"))
        assertNull(CaptchaImageDecoder.decode(PNG_B64))
    }

    @Test
    fun `缺少 base64 标记返回 null`() {
        assertNull(CaptchaImageDecoder.decode("data:image/png,$PNG_B64"))
    }

    @Test
    fun `base64 载荷为空返回 null`() {
        assertNull(CaptchaImageDecoder.decode("data:image/png;base64,"))
    }

    @Test
    fun `非法 base64 返回 null`() {
        assertNull(CaptchaImageDecoder.decode("data:image/png;base64,!!!not-base64!!!"))
    }
}
