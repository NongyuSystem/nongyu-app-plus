package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.api.http.PublicEndpoint
import com.nongyu.appplus.net.model.AmapKeyDto
import com.nongyu.appplus.net.model.AmapKeyRequest
import com.nongyu.appplus.net.model.ClassroomBuildingEnvelopeDto
import com.nongyu.appplus.net.model.ClassroomBuildingListDto
import com.nongyu.appplus.net.model.ClassroomLookupDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 教室楼栋与导航（后端 `internal/classroom/`，2026-11-23 新增）。
 *
 * ## 三条查询都是**匿名可读**
 * 后端走 `RegisterPublic`，无 Bearer 也能查，故标 [PublicEndpoint]。
 *
 * ## 高德密钥是另一回事
 * `POST /keys/amap/acquire` 从**密钥池**取一个明文 Key，需要 Bearer。
 * 那条不在本接口，见 [ApiKeyApi]——因为它属另一个域（`internal/apikey`）。
 *
 * ## 响应形状各不相同（见 [com.nongyu.appplus.net.model.ClassroomDto]）
 * - lookup：**平铺对象**（不是信封）
 * - buildings 列表：`{"items":[...]}`
 * - building 详情：`{"data":{...}}`
 */
interface ClassroomApi {

    /**
     * `GET /classrooms/lookup?room=` —— 按教室名反查楼栋。
     *
     * @param room 教室名（如「一教A201」「A201」）。后端自行做别名匹配与房间号解析。
     */
    @PublicEndpoint
    @GET("classrooms/lookup")
    suspend fun lookup(@Query("room") room: String): Response<ClassroomLookupDto>

    /** `GET /classrooms/buildings` —— 全部已录入楼栋（不含 routes）。 */
    @PublicEndpoint
    @GET("classrooms/buildings")
    suspend fun buildings(): Response<ClassroomBuildingListDto>

    /** `GET /classrooms/buildings/:id` —— 单栋详情，**含 routes**。 */
    @PublicEndpoint
    @GET("classrooms/buildings/{id}")
    suspend fun building(@Path("id") id: Long): Response<ClassroomBuildingEnvelopeDto>
}

/**
 * 高德密钥池（后端 `internal/apikey/handler.go`）。
 *
 * ## ⚠️ 返回的是**明文 Key**
 * - 拿到后**只放在内存**，不要写 SharedPreferences / 文件 / 日志
 * - 一次一用：需要时再取，不要缓存到进程退出之后
 *
 * ## 需要 Bearer
 * 属密钥下发，走后端鉴权，故**没有** [PublicEndpoint]。
 */
interface ApiKeyApi {

    /**
     * `POST /keys/amap/acquire` —— 从池里取一个可用 Key。
     *
     * 请求体 `AmapKeyRequest(platform = "amap")`（`platform` 无默认值，必须显式传，
     * 否则 `encodeDefaults=false` 会序列化成 `{}`）。后端按 QPS 与启用状态挑一个。
     *
     * @return `{"platform","key","hint"}`；`key` 是明文，**不要落盘**。
     */
    @POST("keys/amap/acquire")
    suspend fun acquireAmap(@Body body: AmapKeyRequest): Response<AmapKeyDto>
}
