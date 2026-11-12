package com.nongyu.appplus.login.data.cas

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CasCredential] / [CasCookieJar] 纯 JVM 单测（票 #95）。
 *
 * 钉死知识库实证（`换票只带 go-admin-tgt 会报「未登录」`）：
 * - 登录成功必须保留 **整套同域 cookie**（`laravel_session` / `XSRF-TOKEN` 等），
 *   不能只留 `go-admin-tgt` 一枚；
 * - `goAdminTgt` 与 `cookieHeader` 两个载体都要产出，供 `method=school` 一起交；
 * - 只有单枚 tgt、没有配套会话 cookie 时，`isUsable` 必须为 false。
 */
class CasCookieJarTest {

    private val authUrl = "https://auth.sicau.edu.cn/api/v1/cas/login".toHttpUrl()

    private fun cookie(name: String, value: String) = Cookie.Builder()
        .name(name)
        .value(value)
        .domain("auth.sicau.edu.cn")
        .path("/")
        .build()

    @Test
    fun `收集整套同域 cookie 不丢 laravel_session 与 XSRF-TOKEN`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(
            authUrl,
            listOf(
                cookie("go-admin-tgt", "201690:TGT:TEST"),
                cookie("laravel_session", "SESSION-TEST"),
                cookie("XSRF-TOKEN", "XSRF-TEST"),
            ),
        )

        val header = jar.cookieHeader()
        assertTrue(header.contains("go-admin-tgt=201690:TGT:TEST"))
        assertTrue(header.contains("laravel_session=SESSION-TEST"))
        assertTrue(header.contains("XSRF-TOKEN=XSRF-TEST"))
    }

    @Test
    fun `goAdminTgt 从同名 cookie 提取`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(authUrl, listOf(cookie("go-admin-tgt", "TGT-VALUE")))
        assertEquals("TGT-VALUE", jar.goAdminTgt())
    }

    @Test
    fun `只有单枚 tgt 时 isUsable 为 false`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(authUrl, listOf(cookie("go-admin-tgt", "TGT-ONLY")))
        assertFalse(jar.toCredential().isUsable)
    }

    @Test
    fun `tgt 加配套会话 cookie 时 isUsable 为 true`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(
            authUrl,
            listOf(
                cookie("go-admin-tgt", "201690:TGT:TEST"),
                cookie("laravel_session", "SESSION-TEST"),
            ),
        )
        val cred = jar.toCredential()
        assertTrue(cred.isUsable)
        assertEquals("201690:TGT:TEST", cred.goAdminTgt)
        assertTrue(cred.cookieHeader.contains("laravel_session=SESSION-TEST"))
    }

    @Test
    fun `缺 tgt 时 isUsable 为 false`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(authUrl, listOf(cookie("laravel_session", "SESSION-TEST")))
        assertFalse(jar.toCredential().isUsable)
    }

    @Test
    fun `同名 cookie 以新值覆盖不重复`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(authUrl, listOf(cookie("laravel_session", "OLD")))
        jar.saveFromResponse(authUrl, listOf(cookie("laravel_session", "NEW")))
        val header = jar.cookieHeader()
        assertTrue(header.contains("laravel_session=NEW"))
        assertFalse(header.contains("laravel_session=OLD"))
    }

    @Test
    fun `loadForRequest 回送整套 cookie`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(
            authUrl,
            listOf(cookie("go-admin-tgt", "T"), cookie("laravel_session", "S")),
        )
        assertEquals(2, jar.loadForRequest(authUrl).size)
    }

    @Test
    fun `clear 清空全部 cookie`() {
        val jar = CasCookieJar()
        jar.saveFromResponse(authUrl, listOf(cookie("go-admin-tgt", "T"), cookie("laravel_session", "S")))
        jar.clear()
        assertTrue(jar.loadForRequest(authUrl).isEmpty())
        assertFalse(jar.toCredential().isUsable)
    }
}
