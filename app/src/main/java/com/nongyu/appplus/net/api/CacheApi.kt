package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.model.CacheDataType
import com.nongyu.appplus.net.model.CacheGetResponse
import com.nongyu.appplus.net.model.CacheInvalidateDto
import com.nongyu.appplus.net.model.CacheSubmitResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 回传缓存子域（后端 `jiaowu.RegisterCache`，规格 §3.6）。
 *
 * ## 为什么不并入 [NongyuGateWay]
 * 两者鉴权口径、失败形状、数据用途都不同：
 * - `NongyuGateWay` 只管身份域 3 个公开端点，成功就是裸 DTO；
 * - 本接口的 GET **同一个 200 有两条形状**（命中 / `cache_miss`），
 *   必须靠 [CacheGetResponse.discriminate] 判别，不能套用 `ApiCall` 的常规分流。
 *
 * 独立成接口也避免把 `NongyuGateWay` 变成垃圾桶。装配由 `NetHolder.cache()` 提供，
 * 一期仍不构造 [SchoolApi] / [PresenceApi] 实例。
 *
 * ## 鉴权
 * 三条路由**都需要 Bearer**（后端 `requireUserID` 从中间件上下文取 userID，
 * 无票回 401 `unauthorized`）。所以本接口**没有** `@PublicEndpoint`。
 *
 * ## 路径
 * 不写前导斜杠（与 [IdentityApi] 同一规则）。`dataType` 用 [CacheDataType.wireValue]，
 * 受控词表，不许拼裸字符串。
 */
interface CacheApi {

    /**
     * `GET /school/cache/:dataType` —— 取缓存。
     *
     * ⚠️ **成功响应有两种 200 形状**：
     * - 命中：`{payload, success?, schemaVersion, fetchedAt, stale}`
     * - 未命中：`{code:"cache_miss", message, address?}`
     *
     * 统一解成 [CacheGetResponse]，调用方必须调
     * [CacheGetResponse.discriminate] 判定，**不要**只看 `isSuccessful`。
     *
     * @param dataType 受控词表里的名字
     * @param fallback 传 `1` 表示「客户端刚直连教务失败，请把旧数据给我」，
     *   只有这种调用后端才可能返回 `stale=true`。其余情况传 `null`。
     */
    @GET("school/cache/{dataType}")
    suspend fun get(
        @Path("dataType") dataType: String,
        @Query("fallback") fallback: String? = null,
    ): Response<CacheGetResponse>

    /**
     * `POST /school/cache/:dataType` —— 回传解析后的 JSON。
     *
     * 成功响应形状与 GET 命中时相同（[CacheSubmitResponse] 是 [CacheEnvelope] 的 typealias）。
     *
     * **1 MiB 上限由后端 `MaxBytesReader` 校验**（超了回 413 `payload_too_large`），
     * 客户端**不重复校**（维护者本轮拍板）。
     *
     * @param body 已序列化好的 JSON 文本（不是 `CacheDataType` 相关的 DTO —— payload 原样透传）
     */
    @POST("school/cache/{dataType}")
    suspend fun submit(
        @Path("dataType") dataType: String,
        @Body body: RequestBody,
    ): Response<CacheSubmitResponse>

    /**
     * `DELETE /school/cache/:dataType` —— 让本用户该类型的缓存失效。
     *
     * 后端有 **60 秒失效间隔**，过频回 429 `refresh_too_soon` + `Retry-After` 响应头。
     * 成功回 `{"invalidated":true}`。
     */
    @DELETE("school/cache/{dataType}")
    suspend fun invalidate(
        @Path("dataType") dataType: String,
    ): Response<CacheInvalidateDto>
}

/**
 * 便捷重载：用受控枚举而非裸字符串，编译期就限制住 15 个合法值。
 *
 * 单独放在这里而不是写进接口，是因为 Retrofit 的 `@Path` 需要 `String`；
 * 这层薄封装让**调用方**拿不到 String 入口。
 */
suspend fun CacheApi.get(type: CacheDataType, fallback: Boolean = false): Response<CacheGetResponse> =
    get(type.wireValue, if (fallback) "1" else null)

/** [CacheApi.submit] 的受控枚举重载。 */
suspend fun CacheApi.submit(type: CacheDataType, body: RequestBody): Response<CacheSubmitResponse> =
    submit(type.wireValue, body)

/** [CacheApi.invalidate] 的受控枚举重载。 */
suspend fun CacheApi.invalidate(type: CacheDataType): Response<CacheInvalidateDto> =
    invalidate(type.wireValue)
