package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.model.AnnouncementEnvelopeDto
import com.nongyu.appplus.net.model.AnnouncementListDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 公告域（后端 internal/content/announcement/，2 条路由；admin 5 条不接）。
 *
 * ## ⚠️ 2026-11-23 起全部需要 Bearer
 * 后端提交 `481afec` 清空了 `publicReadablePaths`，所有业务端点一律要求认证。
 * 公告虽仍按 viewer 的 audience 做可见范围过滤，但**必须先有会话**。
 *
 * ## 响应形状
 * - 列表：items + next
 * - 详情：data；**data 为 null 表示当前没有生效公告**（正常状态，不是错误）
 */
interface AnnouncementApi {

    /** GET /announcements?cursor=&limit= —— 列表，需 Bearer。 */
    @GET("announcements")
    suspend fun list(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<AnnouncementListDto>

    /**
     * GET /announcements/:id —— 详情，需 Bearer。
     *
     * 后端**把 /announcements/latest 也挂在这条路由上**（:id 取 latest 时返回最新生效公告），
     * 不是并列注册的独立路由。所以取「最新公告」应传 id = "latest"，用 [latest]。
     */
    @GET("announcements/{id}")
    suspend fun detail(@Path("id") id: String): Response<AnnouncementEnvelopeDto>

    /**
     * GET /announcements/latest —— 当前最新生效公告，需 Bearer。
     *
     * 走的是同一个 :id 路由（后端把它做成 :id 的特殊取值），
     * 所以这里把路径参数固定成 "latest"。
     * 没有生效公告时返回 data 为 null —— 属正常状态，不是错误。
     */
    @GET("announcements/latest")
    suspend fun latest(): Response<AnnouncementEnvelopeDto>
}
