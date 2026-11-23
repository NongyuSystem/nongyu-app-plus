package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.model.SecondClassCacheResponse
import com.nongyu.appplus.net.model.SecondClassDataType
import com.nongyu.appplus.net.model.SecondClassRecipeDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 二课（第二课堂）缓存与配方（后端 `internal/school/secondclass/`，2026-11-23 接入）。
 *
 * ## 「读三 + 写一」四条路由
 * | 路由 | 用途 |
 * | --- | --- |
 * | `GET /school/secondclass/cache/:dataType` | 读副本；命中直返，未命中回 [SecondClassCacheResponse] 带 `recipe` |
 * | `POST /school/secondclass/cache/:dataType` | 客户端**自愿**回传（尽力而为，不经 schema 强校验） |
 * | `DELETE /school/secondclass/cache/:dataType` | 副本失效（60 秒节流） |
 * | `GET /school/secondclass/recipe/:recipeId` | **仅写类用** —— 写不经 GET，没有 miss 时机 |
 *
 * ## ⚠️ 与 [CacheApi]（jiaowu）的形状差异
 * jiaowu 的 `cache_miss` 把地址/方法/Cookie 平铺在顶层；二课把它们收进 `recipe` 对象。
 * 所以**不能复用** `CacheGetResponse`，见 [SecondClassCacheResponse]。
 *
 * ## 鉴权
 * 四条都需 Bearer（身份来自中间件），故**没有** `@PublicEndpoint`。
 */
interface SecondClassApi {

    /**
     * `GET /school/secondclass/cache/:dataType` —— 读副本。
     *
     * 成功响应是**并集容器**（[SecondClassCacheResponse]）：有 `code` 表示未命中/错误，
     * 无 `code` 表示命中。判别用 [SecondClassCacheResponse.isMiss]。
     *
     * @param fallback 传 `1` 表示「客户端刚直连失败，请给旧数据」，可能返回 `stale=true`
     */
    @GET("school/secondclass/cache/{dataType}")
    suspend fun get(
        @Path("dataType") dataType: String,
        @Query("fallback") fallback: String? = null,
    ): Response<SecondClassCacheResponse>

    /**
     * `POST /school/secondclass/cache/:dataType` —— 客户端自愿回传数据副本。
     *
     * 与 jiaowu 的「必须回传」不同：二课是**尽力而为**，失败不影响主流程。
     *
     * @param body 已序列化好的 JSON 文本
     */
    @POST("school/secondclass/cache/{dataType}")
    suspend fun submit(
        @Path("dataType") dataType: String,
        @Body body: RequestBody,
    ): Response<SecondClassCacheResponse>

    /** `DELETE /school/secondclass/cache/:dataType` —— 副本失效（过频回 429）。 */
    @DELETE("school/secondclass/cache/{dataType}")
    suspend fun invalidate(
        @Path("dataType") dataType: String,
    ): Response<SecondClassCacheResponse>

    /**
     * `GET /school/secondclass/recipe/:recipeId` —— 取写类配方。
     *
     * 写操作不经 GET 取数，所以没有 `cache_miss` 时机，需要单独取配方。
     */
    @GET("school/secondclass/recipe/{recipeId}")
    suspend fun recipe(@Path("recipeId") recipeId: String): Response<SecondClassRecipeDto>
}

// ---------- 受控枚举重载（让调用方拿不到裸 String 入口） ----------

/** [SecondClassApi.get] 的受控枚举重载。 */
suspend fun SecondClassApi.get(
    type: SecondClassDataType,
    fallback: Boolean = false,
): Response<SecondClassCacheResponse> =
    get(type.wireValue, if (fallback) "1" else null)

/** [SecondClassApi.submit] 的受控枚举重载。 */
suspend fun SecondClassApi.submit(
    type: SecondClassDataType,
    body: RequestBody,
): Response<SecondClassCacheResponse> = submit(type.wireValue, body)

/** [SecondClassApi.invalidate] 的受控枚举重载。 */
suspend fun SecondClassApi.invalidate(
    type: SecondClassDataType,
): Response<SecondClassCacheResponse> = invalidate(type.wireValue)
