package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.http.PublicEndpoint
import com.nongyu.appplus.net.model.HealthzDto
import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.LogoutAckDto
import com.nongyu.appplus.net.model.LogoutAllAckDto
import com.nongyu.appplus.net.model.MailCodeAckDto
import com.nongyu.appplus.net.model.ProfileDto
import com.nongyu.appplus.net.model.StatusDto
import com.nongyu.appplus.net.model.UserDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * identity 域（**8 条路由**，现行契约见 `docs/后端接口与NY1签名对接契约.md` §2.1）。
 *
 * ## 两条硬约束
 * - **路径不写前导斜杠**：`@GET("identity/me")` 而不是 `@GET("/identity/me")`。
 *   带前导斜杠会解析到 host 根、丢掉 baseUrl 的 path 部分。当前 baseUrl 无 path，
 *   两种写法等价，但**规则只留一种**。
 * - **端点一律声明 `Response<T>`**，不是直接 `T`。成功取 `body()`，失败手工读 `errorBody()`
 *   —— 手工解析集中在 `net/core/ApiCall`，不散落到业务代码。
 *
 * ## ⚠️ 旧注释已过时（2026-11-12 更正）
 * 早期基于 `#46` 历史快照的注释说「`/identity/login` 恒 401」「无 DB 不注册
 * `/identity/me`」「6 条路由」——**均已不成立**。现行后端这 8 条都是正常路由：
 * 登录四种方式可用，`/identity/me` 一族需 Bearer，`/identity/logout/all` 是新增的。
 *
 * ## 「公开」与「免签名」是两件事
 * [PublicEndpoint] 只决定**不挂 `Authorization: Bearer`**，不代表免 NY1 签名。
 * 按现行契约 §1.4，**除 `/healthz`、`/status` 外一切请求都要签名**，
 * 登录、发验证码这些公开端点也不例外。
 *
 * ## 本文件只定义接口，不决定构造范围
 * 哪些端点在 [NongyuApi] 门面里实际暴露，由门面自己决定；这里保持完整映射。
 */
interface IdentityApi {

    /** `GET /healthz` —— 公开、**免签名**。响应 `{"status":"ok"}`。 */
    @PublicEndpoint
    @GET("healthz")
    suspend fun healthz(): Response<HealthzDto>

    /**
     * `GET /status` —— 运维依赖探针，**免签名**，状态码恒 200。
     *
     * 与 [healthz] 的分工：`/healthz` 只报进程可达；本端点如实上报 Postgres / Redis /
     * 熔断器 / 存储实现。监控侧读 body 判断，不要拿状态码判断依赖是否健康。
     */
    @PublicEndpoint
    @GET("status")
    suspend fun status(): Response<StatusDto>

    /**
     * `POST /identity/login` —— 四种登录方式：`school` / `password` / `mailcode` / `idcard`。
     *
     * **公开端点**（不挂 Bearer），但**仍要 NY1 签名**（见类注释）。
     * 只有 `school` 方式能注册；其余方式账号不存在时后端回 400 `need_school_auth`。
     *
     * 成功响应 [LoginDto]：`expiresAt` 是 **Unix 秒整数**。
     *
     * @param body 已序列化好的 [com.nongyu.appplus.net.model.LoginRequestDto]
     */
    @PublicEndpoint
    @POST("identity/login")
    suspend fun login(@Body body: RequestBody): Response<LoginDto>

    /**
     * `POST /identity/login/admin` —— 管理台登录，额外要求 `role >= 1`。
     *
     * 公开端点（不挂 Bearer），仍要签名。成功路径需要 `DATABASE_URL`。
     */
    @PublicEndpoint
    @POST("identity/login/admin")
    suspend fun loginAdmin(@Body body: RequestBody): Response<LoginDto>

    /** `POST /identity/mailcode/send` —— 公开（不挂 Bearer，但仍要 NY1 签名）。 */
    @PublicEndpoint
    @POST("identity/mailcode/send")
    suspend fun sendMailCode(@Body body: RequestBody): Response<MailCodeAckDto>

    /**
     * `POST /identity/logout` —— 吊销**当前**会话，需 Bearer + `CtxSessionID`。
     *
     * 返回 `{"ok":true}`（键是 `ok`，与 school 的 `status` 不同）。
     */
    @POST("identity/logout")
    suspend fun logout(): Response<LogoutAckDto>

    /**
     * `POST /identity/logout/all` —— 吊销该用户 **App 域全部会话**，需 Bearer。
     *
     * 返回 `{"ok":true,"revoked":n}`，`revoked` 是吊销的会话数。
     * 与 [logout] 不同：那个只吊销当前会话，这个是一次性全部下线。
     */
    @POST("identity/logout/all")
    suspend fun logoutAll(): Response<LogoutAllAckDto>

    /** `GET /identity/me` —— 需 Bearer；现行契约 §2.1「查自己画像」。 */
    @GET("identity/me")
    suspend fun me(): Response<ProfileDto>

    /** `PATCH /identity/me/profile` —— 需 Bearer；补充扩展字段（昵称等，不被学校数据覆盖）。 */
    @PATCH("identity/me/profile")
    suspend fun updateProfile(@Body body: RequestBody): Response<ProfileDto>

    /**
     * `GET /identity/users/{studentNo}` —— 需 Bearer；管理员按学号查公开画像
     * （**不含**身份证 / 家庭住址，现行契约 §2.1）。
     *
     * 客户端只做路径映射，角色校验由后端负责。
     */
    @GET("identity/users/{studentNo}")
    suspend fun user(@Path("studentNo") studentNo: String): Response<UserDto>
}
