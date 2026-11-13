package com.nongyu.appplus.login.session

import android.content.Context
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.core.ApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** 邮箱验证码发送结果。 */
sealed interface MailCodeSendResult {
    data object Success : MailCodeSendResult
    /** @param failure 后端 ApiFailure，供上层按错误码给出准确文案（不再吞成空串）。 */
    data class Failure(
        val message: String,
        val failure: com.nongyu.appplus.net.core.ApiFailure? = null,
    ) : MailCodeSendResult
}

/**
 * 邮箱验证码发送（票 #89 / 本轮新增按钮）。
 *
 * 真源：后端 `internal/identity/flow/handler.go` `sendMailCodeHandler`，
 * 请求体**只有 `{"email": ...}`** 一个字段；端点 `POST /identity/mailcode/send`
 * 是公开端点但仍需 NY1 签名（由既有拦截器负责）。
 *
 * 与短信码的区别：这里**不需要图形验证码**。
 */
class MailCodeSender(
    private val gateway: Gateway,
) {
    /** 发送能力接缝，便于纯 JVM 假实现。 */
    interface Gateway {
        suspend fun sendMailCode(email: String): ApiCall.Outcome<*>
    }

    suspend fun send(email: String): MailCodeSendResult = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext MailCodeSendResult.Failure("")
        when (val outcome = gateway.sendMailCode(email.trim())) {
            is ApiCall.Outcome.Success -> MailCodeSendResult.Success
            // 保留 failure：上层要按 code 给准确文案（need_school_auth / too_many_attempts 等）
            is ApiCall.Outcome.Failure -> MailCodeSendResult.Failure("", outcome.failure)
        }
    }

    companion object {
        /** 生产装配：走农屿后端门面。 */
        fun production(context: Context): MailCodeSender {
            val app = context.applicationContext
            val gw = object : Gateway {
                override suspend fun sendMailCode(email: String): ApiCall.Outcome<*> {
                    val body = Json.encodeToString(
                        MailCodeBody.serializer(),
                        MailCodeBody(email),
                    ).toRequestBody("application/json".toMediaType())
                    return NetHolder.api(app).sendMailCode(body)
                }
            }
            return MailCodeSender(gw)
        }
    }
}

/** `POST /identity/mailcode/send` 请求体（只有一个字段）。 */
@Serializable
data class MailCodeBody(val email: String)
