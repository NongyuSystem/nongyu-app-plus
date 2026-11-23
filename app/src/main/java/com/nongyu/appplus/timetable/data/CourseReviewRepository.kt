package com.nongyu.appplus.timetable.data

import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.model.CreatePostRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 课程评价的**真实数据源**（替换 [MockCourseCommentRepository]）。
 *
 * ## 后端契约（2026-11-23）
 *
 * 查询是课程模块的固定入口，不是搜索：
 * ```
 * GET  /post-reviews/teacher-course?teacherName=&courseName=
 * POST /posts   board=course_review, detail={courseName, teacherName, rating}
 * ```
 * 删除走 `DELETE /posts/{id}`（只能删自己的，后端校验）。
 *
 * ## 评论在 UI 上是纯文本，但后端是结构化帖子
 *
 * `detail` 里的 `rating` 是 1-5 整数，展示时拼成 `「评分 4/5」正文`；
 * 回传时需要把它拆出来。这层映射集中在本类，UI 只认字符串。
 */
class CourseReviewRepository(
    private val context: android.content.Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * 一条评价（UI 展示用）。
     *
     * @param id 后端帖子 id，删除时要用
     * @param text 展示文本（含评分前缀）
     * @param rating 原始评分 1-5，null = 老数据无评分
     * @param rawBody 原始正文，编辑时用
     */
    data class Review(
        val id: Long,
        val text: String,
        val rating: Int?,
        val rawBody: String,
    )

    /**
     * 查询某位老师的某门课的评价。
     *
     * 只取 [FETCH_LIMIT] 条（= 显示 3 条 + 1 条用于探测 more）。
     * 后端暂按发布时间倒序；"按热度排"是后续后端的事。
     */
    suspend fun list(teacherName: String, courseName: String): Result<List<Review>> = runCatching {
        val resp = NetHolder.posts(context).teacherCourseReviews(
            teacherName = teacherName,
            courseName = courseName,
            limit = com.nongyu.appplus.timetable.logic.ReviewListDisplay.FETCH_LIMIT,
        )
        if (!resp.isSuccessful) error("查询评价失败：HTTP ${resp.code()}")
        val body = resp.body() ?: error("查询评价失败：响应体为空")
        (body.items.orEmpty()).mapNotNull { post ->
            val id = post.id ?: return@mapNotNull null
            val rating = post.detail?.get("rating") as? JsonPrimitive
            val r = rating?.content?.toIntOrNull()
            Review(
                id = id,
                text = com.nongyu.appplus.timetable.logic.ReviewText.display(r, post.body.orEmpty()),
                rating = r,
                rawBody = post.body.orEmpty(),
            )
        }
    }

    /**
     * 发一条评价。
     *
     * @param rating 1-5 = 打了星；<=0 = 没打分（不传 rating 字段）
     */
    suspend fun add(
        teacherName: String,
        courseName: String,
        rating: Int,
        body: String,
    ): Result<Unit> = runCatching {
        require(body.isNotBlank()) { "评价内容不能为空" }
        val detail = buildJsonObject {
            put("courseName", courseName)
            put("teacherName", teacherName)
            // 没打分就不带 rating：后端规则须为选填（本次一并改）
            if (rating in 1..5) put("rating", rating)
        }
        val req = CreatePostRequest(
            board = BOARD_COURSE_REVIEW,
            title = "课程评价：$courseName",
            body = body.trim(),
            detail = detail,
        )
        val resp = NetHolder.posts(context).create(req)
        if (!resp.isSuccessful) error("发表评价失败：HTTP ${resp.code()}")
    }

    /** 删除自己的评价。后端校验作者，删别人的会失败。 */
    suspend fun remove(reviewId: Long): Result<Unit> = runCatching {
        val resp = NetHolder.posts(context).delete(reviewId)
        if (!resp.isSuccessful) error("删除评价失败：HTTP ${resp.code()}")
    }

    private companion object {
        const val BOARD_COURSE_REVIEW = "course_review"
    }
}
