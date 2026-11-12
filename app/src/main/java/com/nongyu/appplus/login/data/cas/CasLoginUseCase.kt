package com.nongyu.appplus.login.data.cas

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 统一身份认证登录用例（票 #95）。
 *
 * ## 两条独立路径
 * - 账密：`fetchCaptcha()` → `loginWithPassword(学号, 密码, 图形码)`
 * - 短信：`fetchCaptcha()` → `loginWithPhone(手机号, 图形码, 短信码)`（内部先 `sms/send` 再 `loginByPhone`）
 *
 * 两条路径都各自从登录响应的 `Set-Cookie` 拿 `go-admin-tgt` + 整套同域 cookie，
 * 产出 [CasCredential]，供 #96 交给 `POST /identity/login { method=school, goAdminTgt, cookie }`。
 *
 * ## 学校侧错误
 * `code != 200` 时直接把 `msg` 原样透出（如「验证码错误」「操作频繁」），
 * **不映射**成农屿的 `bad_credentials` / `rate_limited` 等码（验收判据）。
 *
 * ## 不做什么
 * - 不做 OCR（验证码靠人识别）
 * - 不访问真实学校 URL（baseUrl 由 [CasApiFactory] 注入，本类不含真站地址）
 * - 不记录任何真实凭据值
 * - 不把单枚 tgt 当可用登录态（[CasCredential.isUsable] 判定）
 */
class CasLoginUseCase(
    private val api: CasApi,
    private val jar: CasCookieJar,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {

    companion object {
        /**
         * 教务系统在统一身份认证平台注册的应用标识。
         * 真源：旧 RN 工具包 `core/login/cas.ts` `CAS_APPID`（来自 `caslogin.asp` 的 302 Location）。
         */
        const val CAS_APPID = "b54a2f27-f7ba-4b91-bbf0-3b2b12244d14"
    }

    /** 取一张图形验证码并解密出 data URI 与 uuid。 */
    suspend fun fetchCaptcha(): CasCaptchaResult {
        val envelope = call { api.captcha(CAS_APPID) } ?: return CasCaptchaResult.Failure("验证码获取失败，请重试")
        if (envelope.code != 200) {
            return CasCaptchaResult.Failure(envelope.msg ?: "验证码获取失败，请重试")
        }
        val uuid = envelope.id
        val image = CasDecrypt.unwrap(envelope, String::class)
        if (uuid.isNullOrBlank() || image == null || !image.startsWith("data:image")) {
            return CasCaptchaResult.Failure(envelope.msg ?: "验证码获取失败，请重试")
        }
        return CasCaptchaResult.Success(uuid, image)
    }

    /** 账密登录。先取验证码（本方法内部完成），再提交 `cas/login`。 */
    suspend fun loginWithPassword(
        studentNo: String,
        password: String,
        captchaCode: String,
    ): CasLoginResult {
        val captcha = fetchCaptcha()
        if (captcha is CasCaptchaResult.Failure) {
            return CasLoginResult.Failure(captcha.message)
        }
        val uuid = (captcha as CasCaptchaResult.Success).uuid

        val body = jsonBody(
            json.encodeToString(
                CasPasswordLoginBody.serializer(),
                CasPasswordLoginBody(CAS_APPID, captchaCode, studentNo, password, uuid),
            ),
        )
        val envelope = call { api.login(body) }
            ?: return CasLoginResult.Failure("统一身份认证登录失败")
        if (envelope.code != 200) {
            return CasLoginResult.Failure(
                envelope.msg ?: "统一身份认证登录失败",
                needRefreshCaptcha = true,
            )
        }
        return credentialOrFailure()
    }

    /** 短信登录：先发短信码，再用码登录；图形验证码由本方法内部先取。 */
    suspend fun loginWithPhone(
        phone: String,
        captchaCode: String,
        smsCode: String,
    ): CasLoginResult {
        val captcha = fetchCaptcha()
        if (captcha is CasCaptchaResult.Failure) {
            return CasLoginResult.Failure(captcha.message)
        }
        val uuid = (captcha as CasCaptchaResult.Success).uuid

        val sendBody = jsonBody(
            json.encodeToString(
                CasSmsSendBody.serializer(),
                CasSmsSendBody(uuid, captchaCode, phone = phone),
            ),
        )
        val send = call { api.sendSms(sendBody) }
            ?: return CasLoginResult.Failure("短信发送失败")
        if (send.code != 200) {
            return CasLoginResult.Failure(send.msg ?: "短信发送失败", needRefreshCaptcha = true)
        }

        val loginBody = jsonBody(
            json.encodeToString(
                CasPhoneLoginBody.serializer(),
                CasPhoneLoginBody(smsCode, phone, CAS_APPID),
            ),
        )
        val envelope = call { api.loginByPhone(loginBody) }
            ?: return CasLoginResult.Failure("短信登录失败")
        if (envelope.code != 200) {
            return CasLoginResult.Failure(envelope.msg ?: "短信登录失败", needRefreshCaptcha = true)
        }
        return credentialOrFailure()
    }

    // ------------------------------------------------------------------

    /** 收口：整套 cookie 齐备才算成功，单枚 tgt 明确失败。 */
    private fun credentialOrFailure(): CasLoginResult {
        val cred = jar.toCredential()
        return if (cred.isUsable) {
            CasLoginResult.Success(cred)
        } else {
            CasLoginResult.Failure(
                "统一身份认证未返回整套会话 cookie，请重试",
                needRefreshCaptcha = true,
            )
        }
    }

    /** 统一反序列化信封；失败返回 null。 */
    private suspend fun call(block: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): CasEnvelope? =
        try {
            val response = block()
            val text = response.body()?.string().orEmpty()
            if (text.isBlank()) null
            else json.decodeFromString(CasEnvelope.serializer(), text)
        } catch (e: Exception) {
            null
        }

    private fun jsonBody(text: String) =
        text.toRequestBody("application/json".toMediaType())
}
