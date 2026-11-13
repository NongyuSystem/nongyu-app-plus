package com.nongyu.appplus.login.data.cas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TgtValidator] 纯 JVM 单测（权威文档 §1.2）。
 *
 * 钉死：合法 tgt 必须含 `:`、可见 ASCII、无危险字符；
 * 302 HTML 之类的 body 会被判为非法（这正是「能登录但画像全空」的根因）。
 */
class TgtValidatorTest {

    @Test
    fun `合法 tgt 通过`() {
        assertTrue(TgtValidator.looksLikeTgt("201690:TGT:Hn9_fTHZJ-abcDEF123"))
        assertTrue(TgtValidator.looksLikeTgt("a1b2c3:TGT:x"))
    }

    @Test
    fun `302 HTML 被判非法`() {
        val html = """<a href="https://webvpn.sicau.edu.cn/login">Found</a>."""
        assertFalse("HTML 不能当 tgt", TgtValidator.looksLikeTgt(html))
    }

    @Test
    fun `缺冒号判非法`() {
        assertFalse(TgtValidator.looksLikeTgt("abcdefghij"))
    }

    @Test
    fun `过短过长判非法`() {
        assertFalse(TgtValidator.looksLikeTgt("a:b"))
        assertFalse(TgtValidator.looksLikeTgt("a:" + "x".repeat(520)))
    }

    @Test
    fun `含空白或非常见可见字符判非法`() {
        assertFalse(TgtValidator.looksLikeTgt("201690:TGT:a b"))
        assertFalse(TgtValidator.looksLikeTgt("201690:TGT:a\tb"))
        assertFalse(TgtValidator.looksLikeTgt("201690:TGT:a\"b"))
        assertFalse(TgtValidator.looksLikeTgt("201690:TGT:a\\b"))
    }

    @Test
    fun `空与null判非法`() {
        assertFalse(TgtValidator.looksLikeTgt(null))
        assertFalse(TgtValidator.looksLikeTgt(""))
        assertFalse(TgtValidator.looksLikeTgt("   "))
    }

    @Test
    fun `从 Set-Cookie 头提取合法 tgt`() {
        val headers = listOf(
            "laravel_session=abc; Path=/; HttpOnly",
            "go-admin-tgt=201690:TGT:Hn9_fTHZJ; Path=/; Max-Age=864000; HttpOnly",
        )
        assertEquals("201690:TGT:Hn9_fTHZJ", TgtValidator.extractFromSetCookie(headers))
    }

    @Test
    fun `Set-Cookie 里没有 tgt 返回 null`() {
        assertEquals(null, TgtValidator.extractFromSetCookie(listOf("laravel_session=abc")))
        assertEquals(null, TgtValidator.extractFromSetCookie(emptyList()))
    }

    @Test
    fun `Set-Cookie 里 tgt 值不合法返回 null`() {
        // 把 HTML 塞进 cookie 值：必须拒绝，不能当 tgt 用
        val bad = """go-admin-tgt=<a href="x">Found</a>.; Path=/"""
        assertNull(TgtValidator.extractFromSetCookie(listOf(bad)))
    }

    @Test
    fun `cookie 名必须是 go-admin-tgt 不是 TGC`() {
        val tgc = "TGC=201690:TGT:xxx; Path=/"
        assertNull("标准 CAS 的 TGC 名字不对", TgtValidator.extractFromSetCookie(listOf(tgc)))
        assertEquals("go-admin-tgt", TgtValidator.COOKIE_NAME)
    }
}
