package com.nongyu.appplus.login.session

import android.content.Context
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.NongyuGateWay
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.MailCodeAckDto
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 平台登录 · 邮箱验证码登录（`method=mailcode`）。
 *
 * ## 两段式
 * 1. 发码：[sendCode] → `POST /identity/mailcode/send {email}`
 *    （后端不需要图形码，防刷靠服务端 Cooldown；未绑定邮箱会回 `need_school_auth`）
 * 2. 登录：[invoke] → `POST /identity/login {method:"mailcode", email, code, deviceId}`
 *
 * ## 与账密登录的差异
 * 不发 `studentNo`/`password`，只发 `email` + `code`（后端按邮箱查 `login_methods`）。
 *
 * ## 接缝
 * 与 [PasswordLoginUseCase] 同构：三个可注入依赖让纯 JVM 可测；
 * 生产装配用 [production]。
 */
class MailCodeLoginUseCase(
    private val gateway: Gateway,
    private val session: SchoolLoginSession,
    private val navigator: SchoolLoginNavigator,
) {
    /** 发码能力接缝。 */
    interface Gateway {
        suspend fun sendCode(email: String): ApiCall.Outcome<MailCodeAckDto>
        suspend fun login(body: RequestBody): ApiCall.Outcome<LoginDto>
    }

    /** 发送邮箱验证码。 */
    suspend fun sendCode(email: String): MailCodeSendResult = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext MailCodeSendResult.Failure("")
        when (val r = gateway.sendCode(email.trim())) {
            is ApiCall.Outcome.Success -> MailCodeSendResult.Success
            is ApiCall.Outcome.Failure -> MailCodeSendResult.Failure("")
        }
    }

    /** 用邮箱 + 验证码登录。 */
    suspend operator fun invoke(email: String, code: String, deviceId: String): MailLoginResult =
        withContext(Dispatchers.IO) {
            if (email.isBlank() || code.isBlank()) {
                return@withContext MailLoginResult.Failure("", null)
            }
            val dto = LoginRequestDto(
                method = "mailcode",
                email = email.trim(),
                code = code.trim(),
                deviceId = deviceId,
            )
            val text = NetJson.instance.encodeToString(LoginRequestDto.serializer(), dto)
            when (val outcome = gateway.login(text.toRequestBody("application/json".toMediaType()))) {
                is ApiCall.Outcome.Success -> {
                    val token = outcome.value.token
                    if (token.isNullOrBlank()) {
                        MailLoginResult.Failure(
                            "",
                            ApiFailure.Parse(IllegalStateException("登录响应缺少 App 会话凭证"), null),
                        )
                    } else {
                        session.writeToken(token)
                        navigator.onSignedIn()
                        MailLoginResult.Success(
                            profileIncomplete = outcome.value.profileIncomplete == true,
                            isNew = outcome.value.isNew == true,
                        )
                    }
                }
                is ApiCall.Outcome.Failure -> MailLoginResult.Failure("", outcome.failure)
            }
        }

    companion object {
        /** 生产装配：走农屿后端门面。 */
        fun production(context: Context): MailCodeLoginUseCase {
            val app = context.applicationContext
            val gateway = object : Gateway {
                override suspend fun sendCode(email: String): ApiCall.Outcome<MailCodeAckDto> {
                    val body = NetJson.instance.encodeToString(
                        MailCodeBody.serializer(), MailCodeBody(email),
                    ).toRequestBody("application/json".toMediaType())
                    return NetHolder.api(app).sendMailCode(body)
                }
                override suspend fun login(body: RequestBody): ApiCall.Outcome<LoginDto> =
                    NetHolder.api(app).login(body)
            }
            val session = object : SchoolLoginSession {
                override fun writeToken(token: String) = NetHolder.sessionStore(app).writeToken(token)
            }
            val navigator = object : SchoolLoginNavigator {
                override fun onSignedIn() = LoginSessionHolder.onSignedIn()
            }
            return MailCodeLoginUseCase(gateway, session, navigator)
        }
    }
}

/** 邮箱验证码登录结果。 */
sealed interface MailLoginResult {
    data class Success(val profileIncomplete: Boolean, val isNew: Boolean) : MailLoginResult
    /** 文案为空表示零提示；非空时由上层按 [failure] 决策或直接显示。 */
    data class Failure(val message: String, val failure: ApiFailure?) : MailLoginResult
}
