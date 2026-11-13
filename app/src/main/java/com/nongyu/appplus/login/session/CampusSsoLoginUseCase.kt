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

    /** 账密：取码 → 取 tgt → method=school。 */
    suspend fun loginWithPassword(
        studentNo: String,
        password: String,
        captchaCode: String,
        deviceId: String,
    ): SchoolLoginResult = withContext(Dispatchers.IO) {
        val captcha = when (val r = cas.fetchCaptcha()) {
            is CasCaptchaResult.Success -> r.captcha
            is CasCaptchaResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        val cred = when (
            val r = cas.loginWithPassword(studentNo, password, captcha, captchaCode)
        ) {
            is CasLoginResult.Success -> r.credential
            is CasLoginResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        school.submit(cred, deviceId)
    }

    /** 短信：取码 → 发短信 → 取 tgt → method=school。 */
    suspend fun loginWithPhone(
        phone: String,
        captchaCode: String,
        smsCode: String,
        deviceId: String,
    ): SchoolLoginResult = withContext(Dispatchers.IO) {
        val captcha = when (val r = cas.fetchCaptcha()) {
            is CasCaptchaResult.Success -> r.captcha
            is CasCaptchaResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        when (val r = cas.sendSmsCode(phone, captcha, captchaCode)) {
            is CasSmsSendResult.Success -> Unit
            is CasSmsSendResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        val cred = when (val r = cas.loginWithPhone(phone, smsCode)) {
            is CasLoginResult.Success -> r.credential
            is CasLoginResult.Failure -> return@withContext SchoolLoginResult.Failure(r.message)
        }
        school.submit(cred, deviceId)
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
    }

    /** `method=school` 提交能力接缝（纯 JVM 可假实现）。 */
    interface SchoolPort {
        suspend fun submit(credential: CasCredential, deviceId: String): SchoolLoginResult
    }

    companion object {
        /** 生产装配：S6 直连真站 + `SchoolLoginUseCase` 提交农屿后端。 */
        fun production(context: Context): CampusSsoLoginUseCase {
            val (api, jar) = CasApiFactory.create(CasApiFactory.REAL_BASE_URL)
            val casUseCase = CasLoginUseCase(api, jar)
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
            }
            val schoolPort = object : SchoolPort {
                override suspend fun submit(credential: CasCredential, deviceId: String) =
                    schoolUseCase(credential, deviceId)
            }
            return CampusSsoLoginUseCase(casPort, schoolPort)
        }
    }
}
