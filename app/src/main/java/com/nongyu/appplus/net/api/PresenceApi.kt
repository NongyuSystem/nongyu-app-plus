package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.model.PresenceDecideDto
import com.nongyu.appplus.net.model.PresenceReportDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * presence 域（2 条路由，挂在 `/school/presence` 下）。
 *
 * ## ⚠️ 一期不构造本接口实例
 * 与 [SchoolApi] 同理，属「客户端直连学校」的独立平面。
 *
 * ## 路径前缀的坑
 * `presence.Register` 挂在 `/school` 下，所以真实路径是 **`/school/presence`**，
 * 不是 `/presence`。失效判据里必须按「最长前缀优先」匹配（`/school/presence` → `/school/`），
 * 否则这条规则永远命不中（见 `net/core/SessionInvalidRules`）。
 */
interface PresenceApi {

    /** `POST /school/presence`。响应键是 `userID`（大写 ID）。 */
    @POST("school/presence")
    suspend fun report(@Body body: RequestBody): Response<PresenceReportDto>

    /** `GET /school/presence`。 */
    @GET("school/presence")
    suspend fun decide(): Response<PresenceDecideDto>
}
