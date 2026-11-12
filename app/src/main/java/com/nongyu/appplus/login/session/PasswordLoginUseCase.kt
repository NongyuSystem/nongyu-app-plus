package com.nongyu.appplus.login.session

import android.content.Context
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.ApiFailure
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.NetJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 平台登录 · 账密登录用例（票 #92）。
 *
 * ## 为什么单独成类（不写在 Composable 里）
 * `docs/specs/网络层.md §2.4` 守卫 1：**禁止在 Composable / Activity 路径上访问 [NetHolder]**。
 * 本用例是普通 Kotlin 对象，由 UI 通过 `rememberCoroutineScope` 在协程里调用，
 * 因此网络层的惰性装配不会跑到首帧路径上（ADR-0006）。
 *
 * ## 职责
 * 1. 用 [LoginRequestDto] 组装 `method=password` 请求体（含 [deviceId]）；
 * 2. 调 `NongyuGateWay.login()`（NY1 签名由 [com.nongyu.appplus.net.api.http.AppSignInterceptor] 负责）；
 * 3. 成功 → 复用既有会话接缝写凭证、通过 [LoginSessionHolder.onSignedIn] 切主界面；
 * 4. 失败 → 原样返回 [ApiFailure]，UI 交给 [com.nongyu.appplus.login.model.LoginFailurePolicy] 决策。
 *
 * 本用例**不做任何 UI 决策**（文案 / 切 tab 都在 UI 层），也不自己解析业务码。
 */
object PasswordLoginUseCase {

    /**
     * @param context 应用上下文（传 `applicationContext`，防泄漏）
     * @param studentNo 学号（账号）
     * @param password 密码
     * @param deviceId 一次生成的稳定设备标识（Q20=A）
     */
    suspend operator fun invoke(
        context: Context,
        studentNo: String,
        password: String,
        deviceId: String,
    ): PasswordLoginResult = withContext(Dispatchers.IO) {
        // 全程在 IO：网络请求 + MinimalSessionStore.writeToken 内部的 runBlocking（Keystore/
        // DataStore 磁盘 IO）都不落在主线程（NetHolder 守卫 2）。
        val appContext = context.applicationContext
        val dto = LoginRequestDto(
            method = "password",
            deviceId = deviceId,
            studentNo = studentNo,
            password = password,
        )
        val text = NetJson.instance.encodeToString(LoginRequestDto.serializer(), dto)
        val body = text.toRequestBody("application/json".toMediaType())

        when (val outcome = NetHolder.api(appContext).login(body)) {
            is ApiCall.Outcome.Success -> onSuccess(appContext, outcome.value)
            is ApiCall.Outcome.Failure -> PasswordLoginResult.Failure(outcome.failure)
        }
    }

    private fun onSuccess(context: Context, dto: LoginDto): PasswordLoginResult {
        val token = dto.token
        if (token.isNullOrBlank()) {
            // 2xx 但没有凭证：不能落库、不能进主界面
            return PasswordLoginResult.Failure(
                ApiFailure.Parse(IllegalStateException("登录响应缺少 App 会话凭证"), null),
            )
        }
        // 复用既有会话接缝（Q8=A / #43）：全仓只有一个凭证真源
        NetHolder.sessionStore(context).writeToken(token)
        LoginSessionHolder.onSignedIn()
        return PasswordLoginResult.Success(
            profileIncomplete = dto.profileIncomplete == true,
            isNew = dto.isNew == true,
            expiresAtUnixSeconds = dto.expiresAt,
        )
    }
}

/** 账密登录结果。 */
sealed interface PasswordLoginResult {
    /**
     * 登录成功，凭证已写入会话存储、根节点已切主界面。
     *
     * @param profileIncomplete 学籍信息暂缺（照常进主界面，只给一次轻提示）
     * @param isNew 新用户（本期不插页）
     * @param expiresAtUnixSeconds 后端签发的过期时间（Unix 秒，原样透出供后续使用）
     */
    data class Success(
        val profileIncomplete: Boolean,
        val isNew: Boolean,
        val expiresAtUnixSeconds: Long?,
    ) : PasswordLoginResult

    /** 登录失败，交由 [com.nongyu.appplus.login.model.LoginFailurePolicy] 决策。 */
    data class Failure(val failure: ApiFailure) : PasswordLoginResult
}
