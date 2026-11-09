package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.model.AccelDto
import com.nongyu.appplus.net.model.AddressDto
import com.nongyu.appplus.net.model.CaptchaDto
import com.nongyu.appplus.net.model.SchoolLoginDto
import com.nongyu.appplus.net.model.SchoolLogoutAckDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * school 域（5 条路由）。
 *
 * ## ⚠️ 一期不构造本接口实例
 * `#46` 已证明：Go 版的 school 端点与 RN 版的 jiaowu-hosted **语义完全不同**
 * —— 这里做的是「地址编码 + 换票」，不保管用户教务账密。
 * 「客户端直连学校」是 out of scope 的独立网络平面。
 *
 * 接口先定义好，等 future effort 落地时直接可用；`NongyuApi` 一期不持有它。
 *
 * ## 路径不写前导斜杠
 * 与 [IdentityApi] 同一规则：`@GET("school/address")`。
 */
interface SchoolApi {

    /**
     * `GET /school/address?target=<逻辑名>&path=<路径>`。
     *
     * **这是本仓唯一带 query 的端点。** 调用方**永不传入 host 或完整 URL**，
     * 只传逻辑目标名，由后端映射为具体 URL（这是排除 SSRF 的结构性做法）。
     */
    @GET("school/address")
    suspend fun address(
        @Query("target") target: String?,
        @Query("path") path: String?,
    ): Response<AddressDto>

    /**
     * `GET /school/fetch?target=...&path=...` —— 返回**二进制**，不是 JSON。
     *
     * 所以是 `Response<ResponseBody>` + `@Streaming`。
     * ⚠️ 注意：`@Streaming` 只对 `ResponseBody` 返回类型有效，
     * **对自定义 Converter 无效**（`#42` 已记录这个坑）。
     */
    @Streaming
    @GET("school/fetch")
    suspend fun fetch(
        @Query("target") target: String?,
        @Query("path") path: String?,
    ): Response<ResponseBody>

    /** `GET /school/captcha`。 */
    @GET("school/captcha")
    suspend fun captcha(): Response<CaptchaDto>

    /**
     * `POST /school/login`。
     *
     * ⚠️ 响应里的 `expiresAt` 是 **RFC3339 字符串**（与 identity 的 Unix 秒 Long 不同型）。
     */
    @POST("school/login")
    suspend fun login(@Body body: RequestBody): Response<SchoolLoginDto>

    /** `DELETE /school/logout` —— 返回 `{"status":"ok"}`（键是 `status`）。 */
    @DELETE("school/logout")
    suspend fun logout(): Response<SchoolLogoutAckDto>

    /** `POST /school/login/accel`。 */
    @POST("school/login/accel")
    suspend fun loginAccel(@Body body: RequestBody): Response<AccelDto>
}
