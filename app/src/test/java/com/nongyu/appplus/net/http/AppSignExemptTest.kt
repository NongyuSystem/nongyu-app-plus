package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.sign.AppRequestSigner
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NY1 签名豁免路径的实测（现行契约 `docs/后端接口与NY1签名对接契约.md` §1.4）。
 *
 * 契约：**仅** `/healthz` 与 `/status` 免签名，**其它一切请求都要签名**，
 * 包括登录、发验证码这些「公开」端点。「公开」只表示不挂 Bearer，不等于免签名。
 */
class AppSignExemptTest {

    private lateinit var server: MockWebServer
    private val signer = AppRequestSigner.dev()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OkHttpFactory.build(
        tokenProvider = AuthTokenProvider { "T" },
        sessionInvalidListener = SessionInvalidListener { },
        loggingEnabled = false,
        logger = NongyuLogger.Noop,
        requestSigner = signer,
    )

    private fun fire(path: String): okhttp3.mockwebserver.RecordedRequest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        client().newCall(Request.Builder().url(server.url(path)).build()).execute().close()
        return server.takeRequest()
    }

    @Test
    fun `healthz 不带任何签名头`() {
        val req = fire("/healthz")
        assertNull(req.getHeader(AppRequestSigner.HEADER_KEY_ID))
        assertNull(req.getHeader(AppRequestSigner.HEADER_SIGNATURE))
        assertNull(req.getHeader(AppRequestSigner.HEADER_NONCE))
        assertNull(req.getHeader(AppRequestSigner.HEADER_TIMESTAMP))
    }

    @Test
    fun `status 不带任何签名头`() {
        val req = fire("/status")
        assertNull(req.getHeader(AppRequestSigner.HEADER_KEY_ID))
        assertNull(req.getHeader(AppRequestSigner.HEADER_SIGNATURE))
    }

    @Test
    fun `登录端点虽公开但仍要签名`() {
        val req = fire("/identity/login")
        assertEquals(AppRequestSigner.DEV_KEY_ID, req.getHeader(AppRequestSigner.HEADER_KEY_ID))
        assertTrue(!req.getHeader(AppRequestSigner.HEADER_SIGNATURE).isNullOrEmpty())
        assertTrue(!req.getHeader(AppRequestSigner.HEADER_NONCE).isNullOrEmpty())
        assertTrue(!req.getHeader(AppRequestSigner.HEADER_TIMESTAMP).isNullOrEmpty())
    }

    @Test
    fun `豁免是精确匹配，不是前缀匹配`() {
        // 防止 /healthz/xxx 之类被误豁免
        val req = fire("/healthz/extra")
        assertEquals(AppRequestSigner.DEV_KEY_ID, req.getHeader(AppRequestSigner.HEADER_KEY_ID))
    }

    @Test
    fun `带 query 的普通端点也要签名`() {
        val req = fire("/school/address?target=jiaowu")
        assertEquals(AppRequestSigner.DEV_KEY_ID, req.getHeader(AppRequestSigner.HEADER_KEY_ID))
    }
}
