package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.NongyuGateWay
import com.nongyu.appplus.net.api.http.OkHttpFactory
import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionInvalidListener
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.NetJson
import com.nongyu.appplus.net.sign.AppRequestSigner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 票 #96 联调证据（可选）：真后端 NY1 签名可达性。
 *
 * ⚠️ 形状契约不在本类：由 `SchoolLoginShapeContractTest`（纯 JVM、必跑）承担。
 * 本类用 `assumeTrue` 在无后端时跳过，因此**不能**作为 CI 的契约断言依据。
 *
 * 按维护者要求**不用 MockWebServer，直连本地真后端**（`127.0.0.1:3000`）：
 * - 形状断言（恰好四字段、无 `schoolMethod`/`wechatCode`）检查序列化出的请求体；
 * - 网络验证：带 NY1 签名的 `method=school` 请求发到真后端，断言**没有被
 *   `app_sign_invalid` 拦下**（证明签名链路通、请求到达业务层）。
 *
 * 用 `assumeTrue` 保护：后端没起时整类跳过，不破坏无服务环境下的全套测试。
 * 假 tgt 不会登录成功（真站凭据需校园网），本测试只验「签名通、形状对」，
 * 不验成功落地。
 */
class SchoolRealBackendTest {

    companion object {
        /** 与 App `NetConfig.DEBUG_BASE_URL` 一致。 */
        private const val REAL_BACKEND = "http://127.0.0.1:3000/"
    }

    @Before
    fun requireBackend() {
        assumeTrue(
            "本地真后端未启动（127.0.0.1:3000），跳过真后端契约测试",
            backendReachable(),
        )
    }

    private fun backendReachable(): Boolean = try {
        OkHttpClient().newCall(
            Request.Builder().url(REAL_BACKEND + "healthz").get().build(),
        ).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    private fun api(): NongyuGateWay {
        val signer = AppRequestSigner.fromBase64(
            "dev",
            "bm9uZ3l1LWRldi1hcHAtc2lnbi1rZXktdjEtMzJiISE",
        )
        val client = OkHttpFactory.build(
            tokenProvider = AuthTokenProvider { null },
            sessionInvalidListener = SessionInvalidListener { },
            loggingEnabled = false,
            logger = NongyuLogger.Noop,
            requestSigner = signer,
        )
        return NongyuGateWay(
            Retrofit.Builder().baseUrl(REAL_BACKEND).client(client)
                .addConverterFactory(NetJson.converterFactory()).build()
                .create(IdentityApi::class.java),
            REAL_BACKEND,
        )
    }

    @Test
    fun `method=school 请求形状正确且真后端接受签名`() = runTest {
        // 由 S6 的 CasCredential 组装：goAdminTgt + 整套 cookie + deviceId
        val credTgt = "201690:TGT:TEST-PLACEHOLDER"
        val credCookie = "go-admin-tgt=$credTgt; laravel_session=S; XSRF-TOKEN=X"
        val dto = LoginRequestDto(
            method = "school",
            deviceId = "device-uuid-1",
            goAdminTgt = credTgt,
            cookie = credCookie,
        )
        val text = NetJson.instance.encodeToString(LoginRequestDto.serializer(), dto)

        // ① 形状：恰好四字段，三格不切后端 method 的禁用字段绝不出现
        val sent = NetJson.instance.parseToJsonElement(text).jsonObject
        assertEquals(setOf("method", "deviceId", "goAdminTgt", "cookie"), sent.keys.toSet())
        assertEquals("school", sent["method"]!!.jsonPrimitive.content)
        assertEquals(credTgt, sent["goAdminTgt"]!!.jsonPrimitive.content)
        assertTrue(sent["cookie"]!!.jsonPrimitive.content.contains("laravel_session=S"))
        assertFalse(text.contains("schoolMethod"))
        assertFalse(text.contains("wechatCode"))

        // ② 真后端：带 NY1 签名发过去，不能被签名中间件拦成 app_sign_invalid
        val outcome = api().login(text.toRequestBody("application/json".toMediaType()))
        if (outcome is ApiCall.Outcome.Failure) {
            val failure = outcome.failure
            if (failure is ApiFailure.Http) {
                assertNotEquals("app_sign_invalid", failure.code)
                assertNotEquals("app_sign_unavailable", failure.code)
            }
        }
        // 本测试只作联调证据：不承担形状断言（由 SchoolLoginShapeContractTest 必跑覆盖），
        // 也不断言"恒真"的结果类型。签名未被拒即视为链路可达。
    }
}
