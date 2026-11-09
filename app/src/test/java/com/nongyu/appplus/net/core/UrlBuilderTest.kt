package com.nongyu.appplus.net.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UrlBuilder] 的纯 JVM 单测（规格 §2.3、§6）。
 *
 * 两个重点：
 * 1. 补尾斜杠 —— Retrofit 的 `baseUrl()` 硬要求，漏了会让相对路径拼错。
 * 2. 剥 query —— 日志安全，不依赖「当前没有敏感 query」这个会变的假设。
 */
class UrlBuilderTest {

    // ---------- normalize ----------

    @Test
    fun `缺尾斜杠时补上`() {
        assertEquals("http://127.0.0.1:3000/", UrlBuilder.normalize("http://127.0.0.1:3000"))
    }

    @Test
    fun `已有尾斜杠时不变`() {
        assertEquals("http://127.0.0.1:3000/", UrlBuilder.normalize("http://127.0.0.1:3000/"))
    }

    @Test
    fun `有 path 的 base 也补斜杠`() {
        assertEquals("https://api.example.com/v1/", UrlBuilder.normalize("https://api.example.com/v1"))
    }

    @Test
    fun `空串必须显式报错，不能变静默坏 URL`() {
        // release 变体的 BASE_URL 就是空串，必须在这里炸出来而不是悄悄拼出坏地址
        val e = assertThrows(IllegalArgumentException::class.java) {
            UrlBuilder.normalize("")
        }
        assertTrue(e.message!!.contains("BASE_URL"))
    }

    @Test
    fun `只含空白的串也报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlBuilder.normalize("   ")
        }
    }

    @Test
    fun `缺 scheme 报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlBuilder.normalize("127.0.0.1:3000/")
        }
    }

    // ---------- stripQuery ----------

    @Test
    fun `剥掉 query`() {
        assertEquals(
            "http://h/school/address",
            UrlBuilder.stripQuery("http://h/school/address?target=jiaowu&path=/x"),
        )
    }

    @Test
    fun `无 query 时原样返回`() {
        assertEquals("/identity/me", UrlBuilder.stripQuery("/identity/me"))
    }

    @Test
    fun `只有问号也剥掉`() {
        assertEquals("/identity/me", UrlBuilder.stripQuery("/identity/me?"))
    }

    // ---------- join ----------

    @Test
    fun `拼相对路径不出现双斜杠`() {
        assertEquals(
            "http://h:3000/identity/me",
            UrlBuilder.join("http://h:3000", "/identity/me"),
        )
    }

    @Test
    fun `拼不带前导斜杠的路径`() {
        assertEquals(
            "http://h:3000/identity/me",
            UrlBuilder.join("http://h:3000/", "identity/me"),
        )
    }

    // ---------- forLog ----------

    @Test
    fun `日志用 URL 不含 query`() {
        val logged = UrlBuilder.forLog("http://h:3000/", "/school/address?target=jiaowu&ticket=SECRET")
        assertEquals("http://h:3000/school/address", logged)
        assertFalse(logged.contains("SECRET"))
        assertFalse(logged.contains("ticket"))
    }
}
