package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.http.PublicEndpoint
import com.nongyu.appplus.net.model.HealthzDto
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.LogoutAckDto
import com.nongyu.appplus.net.model.MailCodeAckDto
import com.nongyu.appplus.net.model.ProfileDto
import com.nongyu.appplus.net.model.UserDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * identity 域（6 条路由）。
 *
 * ## 两条硬约束
 * - **路径不写前导斜杠**：`@GET("identity/me")` 而不是 `@GET("/identity/me")`。
 *   带前导斜杠会解析到 host 根、丢掉 baseUrl 的 path 部分。当前 baseUrl 无 path，
 *   两种写法等价，但**规则只留一种**。
 * - **端点一律声明 `Response<T>`**，不是直接 `T`。成功取 `body()`，失败手工读 `errorBody()`
 *   —— 手工解析集中在 `net/core/ApiCall`，不散落到业务代码。
 *
 * ## 一期构造的只有 3 条（标了 [PublicEndpoint]）
 * `/healthz`、`/identity/login/admin`、`/identity/mailcode/send`。
 * 其余因后端缺陷或需 DB 而不纳入一期：
 * - `POST /identity/login` —— **可吊销装配下恒 401**（尾斜杠不匹配，`#46` §4.4）
 * - `GET /identity/me` 一族 —— **无 DB 时后端根本不注册该路由** → 404 文本
 * - `GET /identity/users/{studentNo}` —— 注释称「需管理员」但无角色校验，属后端缺陷
 */
interface IdentityApi {

    /** `GET /healthz` —— 公开。响应 `{"status":"ok"}`。 */
    @PublicEndpoint
    @GET("healthz")
    suspend fun healthz(): Response<HealthzDto>

    /**
     * `POST /identity/login/admin` —— 公开。
     *
     * ⚠️ 「公开」只在可吊销装配（`DATABASE_URL` 非空）下成立；
     * 降级装配的 `AuthMiddleware` 只放行 `/healthz`，这条无票仍会 401。
     */
    @PublicEndpoint
    @POST("identity/login/admin")
    suspend fun loginAdmin(@Body body: RequestBody): Response<LoginDto>

    /** `POST /identity/mailcode/send` —— 公开（同上，仅可吊销装配下成立）。 */
    @PublicEndpoint
    @POST("identity/mailcode/send")
    suspend fun sendMailCode(@Body body: RequestBody): Response<MailCodeAckDto>

    /**
     * `POST /identity/logout` —— 需 Bearer + `CtxSessionID`。
     *
     * 返回 `{"ok":true}`（键是 `ok`，与 school 的 `status` 不同）。
     */
    @POST("identity/logout")
    suspend fun logout(): Response<LogoutAckDto>

    /** `GET /identity/me` —— 需 Bearer；无 DB 时后端不注册该路由。 */
    @GET("identity/me")
    suspend fun me(): Response<ProfileDto>

    /** `PATCH /identity/me/profile` —— 需 Bearer；同上。 */
    @PATCH("identity/me/profile")
    suspend fun updateProfile(@Body body: RequestBody): Response<ProfileDto>

    /**
     * `GET /identity/users/{studentNo}` —— 需 Bearer。
     *
     * ⚠️ 后端注释称「需管理员」，但装配层只挂 `ScopeApp`、**无任何角色判定**，
     * 且 `scope` 在 HTTP 链路被丢弃（`#46` 挖出的后端缺陷）。属后端问题，不属本层。
     */
    @GET("identity/users/{studentNo}")
    suspend fun user(@Path("studentNo") studentNo: String): Response<UserDto>
}
