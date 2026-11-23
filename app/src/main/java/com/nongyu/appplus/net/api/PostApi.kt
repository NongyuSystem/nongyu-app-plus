package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.api.http.PublicEndpoint
import com.nongyu.appplus.net.model.CreatePostRequest
import com.nongyu.appplus.net.model.CreateReplyRequest
import com.nongyu.appplus.net.model.PostDetailDto
import com.nongyu.appplus.net.model.PostEnvelopeDto
import com.nongyu.appplus.net.model.PostListDto
import com.nongyu.appplus.net.model.PostReplyEnvelopeDto
import com.nongyu.appplus.net.model.ReviewListDto
import com.nongyu.appplus.net.model.StatusOkDto
import com.nongyu.appplus.net.model.UpdatePostRequest
import com.nongyu.appplus.net.model.UploadAttachmentDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 帖子域（后端 internal/content/post/，12 条用户侧路由；admin 3 条不接）。
 *
 * ## 响应形状（与 identity 域不同，必须按端点分开）
 *
 * | 端点 | 形状 |
 * | --- | --- |
 * | GET /posts | items + next |
 * | GET /posts/:id | data + replies |
 * | POST /posts | HTTP 201 加 data |
 * | PATCH /posts/:id | data |
 * | POST /posts/:id/replies | HTTP 201 加 data |
 * | 删除 / 点赞 / 收藏 / 回复点赞 | status |
 * | post-reviews 两条 | items + next + query |
 *
 * ## 鉴权
 * 只有 GET /posts 与 GET /posts/:id 匿名可读（后端 RegisterPublic 的 userIDOf
 * 允许为 nil）；其余全部需 Bearer（RegisterSession）。
 * 匿名请求下 isMine / likedByMe / favoritedByMe 恒 false —— 这是正常的，不是漏数据。
 *
 * ## 路径不写前导斜杠
 * 与 IdentityApi 同一规则。
 */
interface PostApi {

    /**
     * GET /posts?board=&cursor=&limit=&mine= —— 列表，**匿名可读**。
     *
     * @param board 板块过滤（可选）
     * @param cursor 上一页返回的 next 游标（可选）
     * @param mine 为 true 时只看自己发的帖子（后端 2026-11-23 新增）。
     *   注意：mine=true 需要 Bearer，匿名调用时后端忽略该参数。
     */
    @PublicEndpoint
    @GET("posts")
    suspend fun list(
        @Query("board") board: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("mine") mine: Boolean? = null,
    ): Response<PostListDto>

    /** GET /posts/:id —— 详情，**匿名可读**。回复在 replies，不在 data 里。 */
    @PublicEndpoint
    @GET("posts/{id}")
    suspend fun detail(@Path("id") id: Long): Response<PostDetailDto>

    /** POST /posts —— 发帖（201）。需 Bearer。 */
    @POST("posts")
    suspend fun create(@Body body: CreatePostRequest): Response<PostEnvelopeDto>

    /** PATCH /posts/:id —— 编辑（部分更新）。需 Bearer，且只能改自己的。 */
    @PATCH("posts/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UpdatePostRequest,
    ): Response<PostEnvelopeDto>

    /** DELETE /posts/:id —— 删除。需 Bearer。 */
    @DELETE("posts/{id}")
    suspend fun delete(@Path("id") id: Long): Response<StatusOkDto>

    /**
     * POST /posts/attachments —— 上传单个附件，返回标识。
     *
     * 拿到标识后填进 CreatePostRequest.attachments 再发帖。
     */
    @Multipart
    @POST("posts/attachments")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): Response<UploadAttachmentDto>

    /** POST /posts/:id/replies —— 回复（201）。需 Bearer。 */
    @POST("posts/{id}/replies")
    suspend fun reply(
        @Path("id") id: Long,
        @Body body: CreateReplyRequest,
    ): Response<PostReplyEnvelopeDto>

    /** POST /posts/:id/like —— 点赞。需 Bearer。 */
    @POST("posts/{id}/like")
    suspend fun like(@Path("id") id: Long): Response<StatusOkDto>

    /** DELETE /posts/:id/like —— 取消点赞。需 Bearer。 */
    @DELETE("posts/{id}/like")
    suspend fun unlike(@Path("id") id: Long): Response<StatusOkDto>

    /** POST /posts/:id/favorite —— 收藏。需 Bearer。 */
    @POST("posts/{id}/favorite")
    suspend fun favorite(@Path("id") id: Long): Response<StatusOkDto>

    /** DELETE /posts/:id/favorite —— 取消收藏。需 Bearer。 */
    @DELETE("posts/{id}/favorite")
    suspend fun unfavorite(@Path("id") id: Long): Response<StatusOkDto>

    /** POST /posts/:id/replies/:replyId/like —— 给回复点赞。需 Bearer。 */
    @POST("posts/{id}/replies/{replyId}/like")
    suspend fun likeReply(
        @Path("id") id: Long,
        @Path("replyId") replyId: Long,
    ): Response<StatusOkDto>

    /** DELETE /posts/:id/replies/:replyId/like —— 取消回复点赞。需 Bearer。 */
    @DELETE("posts/{id}/replies/{replyId}/like")
    suspend fun unlikeReply(
        @Path("id") id: Long,
        @Path("replyId") replyId: Long,
    ): Response<StatusOkDto>

    // ---------- 评价专用查询（后端 2026-11-23 新增） ----------

    /**
     * GET /post-reviews/teacher?teacherName=&cursor=&limit= —— 按老师查评价。
     *
     * 这是**课程模块的固定入口**，不是通用搜索接口（后端已明确澄清）。
     * 需 Bearer。
     *
     * 响应 ReviewListDto 比普通帖子列表多一个 query 字段，回显实际过滤条件。
     */
    @GET("post-reviews/teacher")
    suspend fun teacherReviews(
        @Query("teacherName") teacherName: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ReviewListDto>

    /**
     * GET /post-reviews/teacher-course?teacherName=&courseName=&cursor=&limit= ——
     * 按「老师 + 课程」查评价。
     *
     * 需 Bearer。同样含 query 回显。
     */
    @GET("post-reviews/teacher-course")
    suspend fun teacherCourseReviews(
        @Query("teacherName") teacherName: String,
        @Query("courseName") courseName: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ReviewListDto>
}
