package com.nongyu.appplus.net.http

import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.sign.AppRequestSigner
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppSignInterceptorTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `发出的请求带 NY1 头且 body 不被吃掉`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val body = "{\"email\":\"a@b.c\"}"
        val signer = AppRequestSigner.dev()
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { null },
            sessionInvalidListener = SessionInvalidListener {},
            loggingEnabled = false,
            requestSigner = signer,
        )
        val req = Request.Builder()
            .url(server.url("/identity/login/admin"))
            .post(body.toRequestBody(null))
            .build()
        client.newCall(req).execute().close()

        val seen = server.takeRequest()
        assertEquals(body, seen.body.readUtf8())
        assertEquals("dev", seen.getHeader(AppRequestSigner.HEADER_KEY_ID))
        assertTrue(!seen.getHeader(AppRequestSigner.HEADER_SIGNATURE).isNullOrBlank())
        val again = signer.signAt(
            "POST",
            "/identity/login/admin",
            "",
            body.toByteArray(),
            seen.getHeader(AppRequestSigner.HEADER_TIMESTAMP)!!.toLong(),
            seen.getHeader(AppRequestSigner.HEADER_NONCE)!!,
        )
        assertEquals(again.signature, seen.getHeader(AppRequestSigner.HEADER_SIGNATURE))
    }

    @Test
    fun `日志不出现签名值`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val collected = mutableListOf<String>()
        val signer = AppRequestSigner(
            keyId = "dev",
            key = java.util.Base64.getUrlDecoder().decode(AppRequestSigner.DEV_KEY_B64),
            nowSeconds = { 1_760_000_000L },
            nextNonce = { "n-test-nonce-0001" },
        )
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { "TOKEN" },
            sessionInvalidListener = SessionInvalidListener {},
            loggingEnabled = true,
            logger = com.nongyu.appplus.net.core.NongyuLogger.of { _, message, _ -> collected += message },
            requestSigner = signer,
        )
        client.newCall(Request.Builder().url(server.url("/identity/me")).build()).execute().close()
        val expected = signer.sign("GET", "/identity/me", "", ByteArray(0)).signature
        val all = collected.joinToString("\n")
        assertFalse(all.contains(expected))
        assertTrue(all.contains("***"))
    }
}
