package com.nongyu.appplus.login.session

import android.content.Context
import com.nongyu.appplus.login.data.cas.CasCredential
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.NongyuGateWay
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 统一身份认证三格共用的 `method=school` 登录用例（票 #96）。
 *
 * ## 链路
 * S6 的 [CasCredential]（`goAdminTgt` + 整套同域 cookie）→
 * `POST /identity/login { method=school, goAdminTgt, cookie, deviceId }` →
 * 成功复用既有会话接缝写凭证 + 切主界面。
 *
 * ## 三格不切后端 method
 * **不填 `schoolMethod`、不传 `wechatCode`**（后端 `schoolauth_bridge.go` 丢弃，
 * 只传会 401）。三格只是 UI 表单不同，请求形状完全一致。
 *
 * ## 为什么抽三个接缝
 * 纯 JVM 测试要能断言「请求形状 / 不可凭据不发请求 / 成功落凭证」而不碰
 * Android Context 与单例；生产装配由 [production] 工厂用 `NetHolder` /
 * `SessionStore` / `LoginSessionHolder` 接上。全程 [Dispatchers.IO]，
 * 网络与 `writeToken` 内部 `runBlocking`（Keystore）都不落主线程。
 */
class SchoolLoginUseCase(
    private val gateway: SchoolLoginGateway,
    private val session: SchoolLoginSession,
    private val navigator: SchoolLoginNavigator,
) {
    suspend operator fun invoke(credential: CasCredential, deviceId: String): SchoolLoginResult =
        withContext(Dispatchers.IO) {
            // 单枚 tgt 不是可用登录态（知识库实证：换票会 code=500 未登录）
            if (!credential.isUsable) {
                return@withContext SchoolLoginResult.Failure(
                    message = "统一身份认证未返回整套会话 cookie，请重试",
                )
            }
            val dto = LoginRequestDto(
                method = "school",
                deviceId = deviceId,
                goAdminTgt = credential.goAdminTgt,
                cookie = credential.cookieHeader,
            )
            val text = NetJson.instance.encodeToString(LoginRequestDto.serializer(), dto)
            val body: RequestBody = text.toRequestBody("application/json".toMediaType())

            when (val outcome = gateway.login(body)) {
                is ApiCall.Outcome.Success -> onSuccess(outcome.value)
                is ApiCall.Outcome.Failure ->
                    SchoolLoginResult.Failure(message = "", failure = outcome.failure)
            }
        }

    private fun onSuccess(dto: LoginDto): SchoolLoginResult {
        val token = dto.token
        if (token.isNullOrBlank()) {
            return SchoolLoginResult.Failure(
                message = "",
                failure = ApiFailure.Parse(
                    IllegalStateException("登录响应缺少 App 会话凭证"), null,
                ),
            )
        }
        session.writeToken(token)
        navigator.onSignedIn()
        return SchoolLoginResult.Success(
            profileIncomplete = dto.profileIncomplete == true,
            isNew = dto.isNew == true,
            expiresAtUnixSeconds = dto.expiresAt,
        )
    }

    companion object {
        /** 生产装配：接真 `NetHolder` / `SessionStore` / `LoginSessionHolder`。 */
        fun production(context: Context): SchoolLoginUseCase {
            val app = context.applicationContext
            val gw = object : SchoolLoginGateway {
                override suspend fun login(body: RequestBody): ApiCall.Outcome<LoginDto> =
                    NetHolder.api(app).login(body)
            }
            val session = object : SchoolLoginSession {
                override fun writeToken(token: String) =
                    NetHolder.sessionStore(app).writeToken(token)
            }
            val navigator = object : SchoolLoginNavigator {
                override fun onSignedIn() = LoginSessionHolder.onSignedIn()
            }
            return SchoolLoginUseCase(gw, session, navigator)
        }
    }
}

/** 网关接缝：只暴露 `login` 一个方法，便于纯 JVM 假实现。 */
interface SchoolLoginGateway {
    suspend fun login(body: RequestBody): ApiCall.Outcome<LoginDto>
}

/** 会话接缝：写 App 会话凭证。 */
interface SchoolLoginSession {
    fun writeToken(token: String)
}

/** 导航接缝：切主界面。 */
interface SchoolLoginNavigator {
    fun onSignedIn()
}

/** `method=school` 登录结果。 */
sealed interface SchoolLoginResult {
    data class Success(
        val profileIncomplete: Boolean,
        val isNew: Boolean,
        val expiresAtUnixSeconds: Long?,
    ) : SchoolLoginResult

    /**
     * @param message 面向用户的就地行内文案（当前为空，由上层 `LoginFailurePolicy` 按码决策）
     * @param failure 原样透出的 ApiFailure，供上层决策；凭据不可用时为 null
     */
    data class Failure(val message: String, val failure: ApiFailure? = null) : SchoolLoginResult
}
