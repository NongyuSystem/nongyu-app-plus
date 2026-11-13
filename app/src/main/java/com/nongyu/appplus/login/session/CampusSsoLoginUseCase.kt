package com.nongyu.appplus.login.session

import android.content.Context
import com.nongyu.appplus.login.data.cas.CasApiFactory
import com.nongyu.appplus.login.data.cas.CasCaptcha
import com.nongyu.appplus.login.data.cas.CasCaptchaResult
import com.nongyu.appplus.login.data.cas.CasCredential
import com.nongyu.appplus.login.data.cas.CasLoginResult
import com.nongyu.appplus.login.data.cas.CasLoginUseCase
import com.nongyu.appplus.login.data.cas.CasSmsSendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一身份认证（账密 / 短信）登录编排器（票 #96）。
 *
 * ## 链路
 * S6 [CasLoginUseCase] 直连学校统一身份认证平台取 `goAdminTgt` + 整套 cookie
 * → [SchoolLoginUseCase] 提交 `method=school` → 落凭证进主界面。
 * 账密与短信两条路径只是「取 tgt 的方式」不同，最后都走同一个 `method=school`。
 *
 * ## 验证码一次性
 * 取码与提交用同一个 [CasCaptcha]（uuid 配对，见 S6 code-review 修复）。
 * 图形码/短信码由 UI 表单传入；本编排器不重取验证码、不臆造输入。
 *
 * ## 三格不切后端 method
 * [SchoolLoginUseCase] 里不填 `schoolMethod`、不传 `wechatCode`。
 */
class CampusSsoLoginUseCase(
    private val cas: CasPort,
    private val school: SchoolPort,
) {

    /**
     * 账密：【用户已看着一张图输入 captchaCode】→ 用同一个 [captcha] 取 tgt → method=school。
     *
     * **本方法绝不重取验证码**（S6 实证：取码会换 uuid 并让旧码作废，
     * 重取会让用户输入的码配上新 uuid，真站必回「验证码错误」）。
     */
    suspend fun loginWithPassword(
        studentNo: String,
        password: String,
        captcha: CasCaptcha,
        captchaCode: String,
        deviceId: String,
    ): SchoolLoginResult = withContext(Dispatchers.IO) {
        val cred = when (
            val r = cas.loginWithPassword(studentNo, password, captcha, captchaCode)
        ) {
            is CasLoginResult.Success -> r.credential
            is CasLoginResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        school.submit(cred, deviceId)
    }

    /**
     * 短信：先 [sendSmsCode]（发码，独立动作），再用短信码登录。
     *
     * 同样不重取图形验证码：发码时用的 [captcha] 由调用方在上一步取好并已展示给用户。
     */
    suspend fun loginWithPhone(
        phone: String,
        smsCode: String,
        deviceId: String,
    ): SchoolLoginResult = withContext(Dispatchers.IO) {
        val cred = when (val r = cas.loginWithPhone(phone, smsCode)) {
            is CasLoginResult.Success -> r.credential
            is CasLoginResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        school.submit(cred, deviceId)
    }

    /** 发短信验证码（独立动作，UI 可单独做倒计时）。 */
    suspend fun sendSmsCode(
        phone: String,
        captcha: CasCaptcha,
        captchaCode: String,
    ): CasSmsSendResult = withContext(Dispatchers.IO) {
        cas.sendSmsCode(phone, captcha, captchaCode)
    }

    /** 取微信登录二维码（UI 展示用）。 */
    suspend fun fetchLoginQr() = withContext(Dispatchers.IO) { cas.fetchLoginQr() }

    /** 轮询扫码结果（UI 每 1~2 秒调一次）。 */
    suspend fun pollQrResult(state: String) = withContext(Dispatchers.IO) { cas.pollQrResult(state) }

    /** 用扫码拿到的凭据提交 method=school（供 UI 在轮询命中后调用）。 */
    suspend fun submitCredential(credential: CasCredential, deviceId: String): SchoolLoginResult =
        withContext(Dispatchers.IO) { school.submit(credential, deviceId) }

    /** 取图形验证码（UI 首步：拿到 [CasCaptcha] 把图展示给用户）。 */
    suspend fun fetchCaptcha(): CasCaptchaResult = withContext(Dispatchers.IO) {
        cas.fetchCaptcha()
    }

    /** S6 取 tgt 的能力接缝（纯 JVM 可假实现）。 */
    interface CasPort {
        suspend fun fetchCaptcha(): CasCaptchaResult
        suspend fun loginWithPassword(
            studentNo: String, password: String, captcha: CasCaptcha, captchaCode: String,
        ): CasLoginResult
        suspend fun sendSmsCode(
            phone: String, captcha: CasCaptcha, captchaCode: String,
        ): CasSmsSendResult
        suspend fun loginWithPhone(phone: String, smsCode: String): CasLoginResult

        /** 取微信登录二维码。 */
        suspend fun fetchLoginQr(): com.nongyu.appplus.login.data.cas.CasQrFetchResult

        /** 轮询扫码结果。 */
        suspend fun pollQrResult(state: String): com.nongyu.appplus.login.data.cas.CasQrPollResult
    }

    /** `method=school` 提交能力接缝（纯 JVM 可假实现）。 */
    interface SchoolPort {
        suspend fun submit(credential: CasCredential, deviceId: String): SchoolLoginResult
    }

    companion object {
        /** 生产装配：S6 直连真站 + `SchoolLoginUseCase` 提交农屿后端。 */
        fun production(context: Context): CampusSsoLoginUseCase {
            val client = CasApiFactory.create(CasApiFactory.REAL_BASE_URL)
            val casUseCase = CasLoginUseCase(
                api = client.api,
                jar = client.jar,
                logger = com.nongyu.appplus.net.api.http.AndroidLogger,
                tgtInterceptor = client.tgtInterceptor,
            )
            val schoolUseCase = SchoolLoginUseCase.production(context)
            val casPort = object : CasPort {
                override suspend fun fetchCaptcha(): CasCaptchaResult = casUseCase.fetchCaptcha()
                override suspend fun loginWithPassword(
                    studentNo: String, password: String, captcha: CasCaptcha, captchaCode: String,
                ): CasLoginResult = casUseCase.loginWithPassword(studentNo, password, captcha, captchaCode)
                override suspend fun sendSmsCode(
                    phone: String, captcha: CasCaptcha, captchaCode: String,
                ): CasSmsSendResult = casUseCase.sendSmsCode(phone, captcha, captchaCode)
                override suspend fun loginWithPhone(phone: String, smsCode: String): CasLoginResult =
                    casUseCase.loginWithPhone(phone, smsCode)
                override suspend fun fetchLoginQr() = casUseCase.fetchLoginQr()
                override suspend fun pollQrResult(state: String) = casUseCase.pollQrResult(state)
            }
            val schoolPort = object : SchoolPort {
                override suspend fun submit(credential: CasCredential, deviceId: String) =
                    schoolUseCase(credential, deviceId)
            }
            return CampusSsoLoginUseCase(casPort, schoolPort)
        }
    }
}
