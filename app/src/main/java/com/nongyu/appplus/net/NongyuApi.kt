package com.nongyu.appplus.net

import com.nongyu.appplus.net.api.IdentityApi
import com.nongyu.appplus.net.core.ApiCall
import com.nongyu.appplus.net.core.UrlBuilder
import com.nongyu.appplus.net.model.HealthzDto
import com.nongyu.appplus.net.model.MailCodeAckDto
import com.nongyu.appplus.net.model.NetJson
import okhttp3.RequestBody
import retrofit2.Response

/**
 * 网络层对外**唯一入口**（规格 §2.1、§3.2）。
 *
 * ## 为什么是门面，而不是直接暴露三个 interface
 * `#46` 已证明一期能打的只有 identity 域 3 条公开端点；直接暴露 `IdentityApi` /
 * `SchoolApi` / `PresenceApi` 会让调用方以为 school 域可用（那属「客户端直连学校」的
 * 独立平面，out of scope）。门面收拢「现在能用哪些」，将来换实现时调用点不动。
 *
 * ## 每个方法都返回 [ApiCall.Outcome]，不抛业务异常
 * 失败分类、重试、errorBody 解析全部集中在 `net/core/ApiCall` ——
 * 这是规格 §3.2 要求「错误分类的集中点」的唯一落点。
 *
 * `CancellationException` 是**唯一**会往外抛的东西（规格 §5.4 第 1 条：绝不吞取消）。
 *
 * ## 一期不接 UI
 * 规格 §2.4 守卫 1：**禁止在 Composable / Activity 上访问 [NetHolder]**。
 * 等加 ViewModel 后由 ViewModel 调本类。
 */
class NongyuApi internal constructor(
    private val identity: IdentityApi,
    private val baseUrl: String,
) {

    /**
     * `GET /healthz` —— 公开，无参数。
     *
     * 返回有 body（`{"status":"ok"}`），所以是 `HealthzDto` 而不是 `Unit`
     * —— `backend-plus` **没有真空 body 的成功响应**（规格 §5.5）。
     */
    suspend fun healthz(): ApiCall.Outcome<HealthzDto> =
        call("GET", "/healthz") { identity.healthz() }

    /**
     * `POST /identity/mailcode/send` —— 公开。
     *
     * @param body 已序列化好的 `RequestBody`
     */
    suspend fun sendMailCode(body: RequestBody): ApiCall.Outcome<MailCodeAckDto> =
        call("POST", "/identity/mailcode/send") { identity.sendMailCode(body) }

    /**
     * 统一的调用包装。三项集中处理，业务方法里不再重复：
     * 1. catch 顺序与失败分类（[ApiCall.execute]）
     * 2. 重试判定（`RetryPolicy`，写操作永不重试）
     * 3. errorBody → `(code, message)`（[NetJson.errorEnvelopeParser]）
     */
    private suspend fun <T> call(
        method: String,
        path: String,
        block: suspend () -> Response<T>,
    ): ApiCall.Outcome<T> = ApiCall.execute(
        method = method,
        url = UrlBuilder.join(baseUrl, path),
        errorEnvelopeParser = NetJson.errorEnvelopeParser(),
        block = block,
    )
}
