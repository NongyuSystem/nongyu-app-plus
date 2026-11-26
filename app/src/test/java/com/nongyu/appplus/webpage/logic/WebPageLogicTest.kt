package com.nongyu.appplus.webpage.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 #119：WebView 白名单判定与 URL 归一化的纯 JVM 单测。
 * 只断言外部行为，覆盖协议、host 匹配、子域、非法 URL、尾斜杠与 fragment。
 */
class WebPageLogicTest {

    private val schoolHosts = setOf("*.sicau.edu.cn", "nongyu.site")

    @Test
    fun `归一化补尾斜杠去 fragment 并小写 scheme host`() {
        assertEquals("https://jiaowu.sicau.edu.cn/", UrlNormalizer.normalize("  HTTPS://Jiaowu.SICAU.edu.cn  "))
        assertEquals("https://a.sicau.edu.cn/x?y=1", UrlNormalizer.normalize("https://a.sicau.edu.cn/x?y=1#frag"))
        assertEquals("https://nongyu.site/", UrlNormalizer.normalize("https://nongyu.site"))
    }

    @Test
    fun `非法与相对 URL 返回 null`() {
        assertNull(UrlNormalizer.normalize(null))
        assertNull(UrlNormalizer.normalize(""))
        assertNull(UrlNormalizer.normalize("/relative/path"))
        assertNull(UrlNormalizer.normalize("not a url"))
    }

    @Test
    fun `hostOf 只取小写主机`() {
        assertEquals("jiaowu.sicau.edu.cn", UrlNormalizer.hostOf("https://Jiaowu.SICAU.edu.cn/x"))
        assertNull(UrlNormalizer.hostOf("garbage"))
    }

    @Test
    fun `白名单命中精确域与子域`() {
        assertTrue(WebPageAllowlist.isAllowed("https://nongyu.site/", schoolHosts))
        assertTrue(WebPageAllowlist.isAllowed("https://jiaowu.sicau.edu.cn/xuesheng/", schoolHosts))
        assertTrue(WebPageAllowlist.isAllowed("https://webvpn.sicau.edu.cn/login", schoolHosts))
        // `*.sicau.edu.cn` 也应匹配裸域本身
        assertTrue(WebPageAllowlist.isAllowed("https://sicau.edu.cn/", schoolHosts))
    }

    @Test
    fun `白名单拒绝非 https 与非白名单域`() {
        assertFalse(WebPageAllowlist.isAllowed("http://jiaowu.sicau.edu.cn/", schoolHosts))
        assertFalse(WebPageAllowlist.isAllowed("https://evil.com/", schoolHosts))
        assertFalse(WebPageAllowlist.isAllowed("https://sicau.edu.cn.evil.com/", schoolHosts))
        assertFalse(WebPageAllowlist.isAllowed("not a url", schoolHosts))
        assertFalse(WebPageAllowlist.isAllowed("https://jiaowu.sicau.edu.cn/", emptySet()))
    }

    @Test
    fun `带端口与 query 的合法 URL 仍放行`() {
        assertTrue(WebPageAllowlist.isAllowed("https://jiaowu.sicau.edu.cn:443/x?a=1#f", schoolHosts))
    }
}
