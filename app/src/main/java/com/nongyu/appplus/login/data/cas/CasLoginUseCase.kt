package com.nongyu.appplus.login.data.cas

import com.nongyu.appplus.net.core.NongyuLogger
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * 统一身份认证登录用例（票 #95）。
 *
 * ## 验证码一次性（知识库实证）
 * 取码会下发 uuid，重新取码即换 uuid、旧码立即作废。因此**取码与提交必须成对**：
 * 调用方先用 [fetchCaptcha] 拿 [CasCaptcha]（把 image 给用户看），再把**同一个**
 * [CasCaptcha.uuid] 连同用户输入的码一起交给 [loginWithPassword] / [sendSmsCode] /
 * [loginWithPhone]。本用例**不在登录方法内部重取验证码**（重取会让用户输入的码
 * 配上新 uuid，真站必回「验证码错误」）。
 *
 * ## 三条可独立调用的链路（供 UI 分别触发）
 * - [fetchCaptcha]：取图形验证码
 * - [sendSmsCode]：发短信码（UI 可单独做「发送中…／60s 倒计时／重新获取」）
 * - [loginWithPassword]（账密）/ [loginWithPhone]（短信）：各自完成登录并产出凭据
 *
 * 账面与短信两条路径各自从**自身登录响应的 Set-Cookie** 拿 `go-admin-tgt` +
 * 整套同域 cookie；每条路径开始前清空 cookie 罐，避免前次登录的残留串味。
 *
 * ## 失败口径
 * - `code != 200`：把统一身份认证平台 `msg` 原样透出，**不映射**成农屿错误码；
 * - 弱网 / 超时 / 空响应：`message` 为**空串**（规格 #89 User Story 21「弱网时登录失败不给任何提示」），
 *   不臆造中文文案。
 *
 * ## 不做什么
 * 不做 OCR、不写真站 baseUrl（由 [CasApiFactory] 注入）、不记录真实凭据值、
 * 不把单枚 tgt 当可用登录态（[CasCredential.isUsable] 判定）。
 */
class CasLoginUseCase(
    private val api: CasApi,
    private val jar: CasCookieJar,
    /** 从 Set-Cookie 抓 tgt 的拦截器（body 里没有 tgt）。 */
    private val tgtInterceptor: TgtCaptureInterceptor = TgtCaptureInterceptor(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    /** 日志接缝：纯 JVM 测试默认 Noop；生产传 AndroidLogger（不直接用 android.util.Log）。 */
    private val logger: NongyuLogger = NongyuLogger.Noop,
) {

    companion object {
        /** 教务系统在统一身份认证平台注册的应用标识（旧 RN 工具包 `CAS_APPID`）。 */
        const val CAS_APPID = "b54a2f27-f7ba-4b91-bbf0-3b2b12244d14"

        /**
         * 微信扫码用的 appid（门户）。
         *
         * 实测：`getLoginQr` 用 `ywtb` 才返回微信授权 URL（与知识库解码出的
         * `callback .../loginByQr?appid=ywtb` 一致）。**不能换成教务 appid**。
         */
        const val QR_APPID = "ywtb"
    }

    /** 取一张图形验证码，明文**剥掉 JSON 引号**后返回 data URI。 */
    suspend fun fetchCaptcha(): CasCaptchaResult {
        val envelope = call { api.captcha(CAS_APPID) }
            ?: return CasCaptchaResult.Failure("")
        if (envelope.code != 200) {
            return CasCaptchaResult.Failure(envelope.msg.orEmpty())
        }
        val uuid = envelope.id
        // 知识库「要剥两层」：解密明文是 "data:image/png;base64,..."，带 JSON 引号
        val raw = CasDecrypt.unwrap(envelope)
        val image = raw?.trim()?.trim('"')?.trim()
        if (uuid.isNullOrBlank() || image == null || !image.startsWith("data:image")) {
            return CasCaptchaResult.Failure(envelope.msg.orEmpty())
        }
        return CasCaptchaResult.Success(CasCaptcha(uuid, image))
    }

    /**
     * 发短信验证码。**独立步骤**：UI 拿到成功后自行做倒计时，再调 [loginWithPhone]。
     *
     * @param captcha 与用户当前所看图片对应的验证码（uuid 必须来自同一张图）
     * @param captchaCode 用户针对该图输入的图形码
     */
    suspend fun sendSmsCode(phone: String, captcha: CasCaptcha, captchaCode: String): CasSmsSendResult {
        logger.log(
            NongyuLogger.Level.DEBUG,
            "sms/send 入参 phoneEmpty=${phone.isBlank()} codeEmpty=${captchaCode.isBlank()} uuidEmpty=${captcha.uuid.isBlank()} uuidLen=${captcha.uuid.length}",
        )
        val body = jsonBody(
            json.encodeToString(
                CasSmsSendBody.serializer(),
                CasSmsSendBody(captcha.uuid, captchaCode, phone = phone),
            ),
        )
        val envelope = call { api.sendSms(body) }
            ?: return CasSmsSendResult.Failure("")
        return if (envelope.code == 200) {
            CasSmsSendResult.Success
        } else {
            CasSmsSendResult.Failure(
                envelope.msg.orEmpty(),
                needRefreshCaptcha = true,
            )
        }
    }

    /** 账密登录：用调用方传入的 [captcha]（同一 uuid）提交，不重取验证码。 */
    suspend fun loginWithPassword(
        studentNo: String,
        password: String,
        captcha: CasCaptcha,
        captchaCode: String,
    ): CasLoginResult {
        jar.clear()
        val body = jsonBody(
            json.encodeToString(
                CasPasswordLoginBody.serializer(),
                CasPasswordLoginBody(CAS_APPID, captchaCode, studentNo, password, captcha.uuid),
            ),
        )
        val envelope = call { api.login(body) }
            ?: return CasLoginResult.Failure("")
        if (envelope.code != 200) {
            return CasLoginResult.Failure(envelope.msg.orEmpty(), needRefreshCaptcha = true)
        }
        return credentialOrFailure()
    }

    /** 短信登录：只做 `loginByPhone`，不重取验证码、不重发短信。 */
    suspend fun loginWithPhone(phone: String, smsCode: String): CasLoginResult {
        jar.clear()
        val body = jsonBody(
            json.encodeToString(
                CasPhoneLoginBody.serializer(),
                CasPhoneLoginBody(smsCode, phone, CAS_APPID),
            ),
        )
        val envelope = call { api.loginByPhone(body) }
            ?: return CasLoginResult.Failure("")
        if (envelope.code != 200) {
            return CasLoginResult.Failure(envelope.msg.orEmpty(), needRefreshCaptcha = true)
        }
        return credentialOrFailure()
    }

    // ------------------------------------------------------------------
    // 微信扫码（真站实测 2026-11-13：POST + JSON，响应解密后是 url/state）

    /**
     * 取一张微信登录二维码。
     *
     * 返回的 [CasLoginQr.url] 需由 UI 渲染成二维码；[CasLoginQr.state] 用于轮询。
     */
    suspend fun fetchLoginQr(): CasQrFetchResult {
        val body = jsonBody(json.encodeToString(CasQrAppIdBody.serializer(), CasQrAppIdBody(QR_APPID)))
        val envelope = call { api.getLoginQr(body) }
            ?: return CasQrFetchResult.Failure("")
        if (envelope.code != 200) {
            return CasQrFetchResult.Failure(envelope.msg.orEmpty())
        }
        val plain = CasDecrypt.unwrap(envelope) ?: return CasQrFetchResult.Failure("")
        val qr = try {
            json.decodeFromString(CasLoginQr.serializer(), plain)
        } catch (e: Exception) {
            return CasQrFetchResult.Failure("")
        }
        if (qr.url.isNullOrBlank() || qr.state.isNullOrBlank()) {
            return CasQrFetchResult.Failure(envelope.msg.orEmpty())
        }
        return CasQrFetchResult.Success(qr)
    }

    /**
     * 轮询扫码结果（UI 每 1~2 秒调一次）。
     *
     * - 未扫码：返回 [CasQrPollResult.Pending]（解密结果是全空对象，**不是成功**）；
     * - 已扫码：返回 [CasQrPollResult.Scanned]，带 `tgt` 与整套 cookie 供 `method=school`。
     */
    suspend fun pollQrResult(state: String): CasQrPollResult {
        if (state.isBlank()) return CasQrPollResult.Failure("")
        val body = jsonBody(json.encodeToString(CasQrStateBody.serializer(), CasQrStateBody(state)))
        val envelope = call { api.getCasLoginQrRes(body) }
            ?: return CasQrPollResult.Failure("")
        if (envelope.code != 200) {
            return CasQrPollResult.Failure(envelope.msg.orEmpty())
        }
        val plain = CasDecrypt.unwrap(envelope) ?: return CasQrPollResult.Failure("")
        val result = try {
            json.decodeFromString(CasQrResult.serializer(), plain)
        } catch (e: Exception) {
            return CasQrPollResult.Failure("")
        }
        // 判定"已扫码"：以 Set-Cookie 里抓到合法 tgt 为准（权威文档 §1）
        val tgtFromCookie = tgtInterceptor.lastTgt()
        if (tgtFromCookie == null && !result.isScanned) return CasQrPollResult.Pending

        if (tgtFromCookie == null) {
            // 轮询响应里带了 tgt 字段但 Set-Cookie 没有：**不能直接当 cookie 用**。
            // 权威文档 §1.2 明确这是"把值来源搞错"的坑（会把非法值存进后端，
            // 表现为能登录但画像全空）。这里必须拒绝并报错，而不是静默使用。
            return CasQrPollResult.Failure(
                "未从响应头拿到有效的登录凭据，请刷新二维码重试",
            )
        }
        val cred = jar.toCredential()
        return CasQrPollResult.Scanned(
            CasCredential(
                goAdminTgt = tgtFromCookie,
                cookieHeader = cred.cookieHeader,
                companionCookieNames = cred.companionCookieNames,
            ),
        )
    }

    // ------------------------------------------------------------------

    /**
     * 收口：必须从 **`Set-Cookie` 头**拿到形态合法的 `go-admin-tgt`。
     *
     * 单枚 tgt 就够（权威文档 §1.1：`fetch.go:48` 只设一枚），
     * 配套 cookie 属"更保险但非必要"，有则一并透传供后端续期。
     * 拿不到合法 tgt 时要明确失败——否则后端会存进一个垃圾值，
     * 表现为「能登录、有画像姓名、其他字段全空」且日志零线索。
     */
    private fun credentialOrFailure(): CasLoginResult {
        val tgt = tgtInterceptor.lastTgt()
        if (tgt == null || !TgtValidator.looksLikeTgt(tgt)) {
            return CasLoginResult.Failure(
                "统一身份认证未返回有效的登录凭据（go-admin-tgt），请重试",
                needRefreshCaptcha = true,
            )
        }
        val cred = jar.toCredential()
        return CasLoginResult.Success(
            CasCredential(
                goAdminTgt = tgt,
                cookieHeader = cred.cookieHeader,
                companionCookieNames = cred.companionCookieNames,
            ),
        )
    }

    /** 统一反序列化信封；失败/空响应返回 null（由调用方产出「零提示」文案）。 */
    private suspend fun call(block: suspend () -> Response<ResponseBody>): CasEnvelope? =
        try {
            val response = block()
            val text = response.body()?.string().orEmpty()
            logger.log(NongyuLogger.Level.DEBUG, "CAS HTTP ${response.code()} bodyLen=${text.length}")
            if (text.isBlank()) null
            else json.decodeFromString(CasEnvelope.serializer(), text)
        } catch (e: Exception) {
            // 不静默吞：失败必须可在日志里看到（UI 仍按零提示处理）
            logger.log(NongyuLogger.Level.WARN, "CAS 请求失败", e)
            null
        }

    private fun jsonBody(text: String) =
        text.toRequestBody("application/json".toMediaType())
}
