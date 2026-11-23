package com.nongyu.appplus.net.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 帖子域 DTO（后端 `internal/content/post/handler.go`，2026-11-17 逐 handler 对账）。
 *
 * ## ⚠️ 本域的响应形状与 identity 域**不同**，不要套用裸结构体
 * identity 域成功时直接吐业务结构体（`c.JSON(200, addressResponse{...})`），
 * **帖子域用信封**，且有三种：
 *
 * | 形状 | 用在 |
 * | --- | --- |
 * | `{"items":[...],"next":"..."}` | `GET /posts` 列表 |
 * | `{"data":{...},"replies":[...]}` | `GET /posts/:id` 详情 |
 * | `{"data":{...}}` | `POST /posts`、`PATCH /posts/:id`（**HTTP 201 / 200**） |
 * | `{"status":"ok"}` | 删除、点赞、收藏、回复点赞等无载荷操作 |
 *
 * 所以每个端点的 DTO **必须按它自己的信封建**，不能共用一个 `PostDto`。
 *
 * ## 时间字段
 * `publishedAt` / `editedAt` 是 Go `time.Time`，序列化成 **RFC3339 字符串**，
 * 不是 Unix 秒。与 identity 的 `expiresAt`（Long）不同型。
 */

/**
 * 帖子本体（后端 `publicView`）。
 *
 * ⚠️ **`likedByMe` / `favoritedByMe` 只在详情里有值**：后端注释明写
 * 「这两个字段不进列表响应（列表不逐条查反应，成本高且用途低）」。
 * 列表里恒为 `null` 不是 bug。
 */
@Serializable
data class PostDto(
    val id: Long? = null,
    val board: String? = null,
    val title: String? = null,
    val body: String? = null,
    /** `omitempty`：无附件时字段不出现。 */
    val attachments: List<String>? = null,
    /** `omitempty`：无附件时字段不出现。 */
    val attachmentUrls: List<String>? = null,
    /** RFC3339 字符串。 */
    val publishedAt: String? = null,
    /** 未编辑为 `null`。 */
    val editedAt: String? = null,
    val isMine: Boolean? = null,
    /** 仅详情有值。 */
    val likedByMe: Boolean? = null,
    /** 仅详情有值。 */
    val favoritedByMe: Boolean? = null,
    /**
     * 结构化字段（评价贴的 teacherName / courseName / rating）。
     *
     * 后端 `publicView.Detail` 带 `omitempty`：普通帖子没有该字段 → null。
     * #109 只补了 `CreatePostRequest.detail`，漏了响应侧，这里补上，
     * 否则评价查询拿不到评分（UI 只能显示纯文本）。
     */
    val detail: Map<String, JsonElement>? = null,
)

/**
 * `GET /posts` 列表响应：`{"items":[...],"next":"..."}`。
 *
 * `next` 是**游标**（不是页码），`omitempty`，为空表示没有下一页。
 */
@Serializable
data class PostListDto(
    val items: List<PostDto>? = null,
    val next: String? = null,
)

/** 回复（后端 `replyView`）。作者匿名不下发，但 `likedByMe` 要下发以便渲染已选中态。 */
@Serializable
data class PostReplyDto(
    val id: Long? = null,
    val postId: Long? = null,
    val body: String? = null,
    /** RFC3339 字符串。 */
    val publishedAt: String? = null,
    val likedByMe: Boolean? = null,
)

/**
 * `GET /posts/:id` 详情响应：`{"data":{publicView},"replies":[replyView...]}`。
 *
 * ⚠️ 详情**不是**直接把 `publicView` 铺平返回，而是包在 `data` 里、回复另放 `replies`。
 */
@Serializable
data class PostDetailDto(
    val data: PostDto? = null,
    val replies: List<PostReplyDto>? = null,
)

/**
 * 带单个帖子的信封：`{"data":{publicView}}`。
 *
 * 用在 `POST /posts`（201）与 `PATCH /posts/:id`（200）。
 */
@Serializable
data class PostEnvelopeDto(
    val data: PostDto? = null,
)

/** 无载荷操作的成功响应：`{"status":"ok"}`。 */
@Serializable
data class StatusOkDto(
    val status: String? = null,
)

/**
 * 评价列表响应（后端 `reviewListResponse`，2026-11-23 新增的两个评价查询专用）。
 *
 * 形状与普通帖子列表**不同**：多了 `query` 字段回显本次实际使用的过滤条件
 * （后端已 trim）。它用来让 UI 显示「按 张老师 筛选」这类说明，并避免
 * 客户端自己维护一份筛选状态导致回显与后端不一致。
 *
 * `items` 复用 [PostDto]：后端 `toPublic` 出的就是同一种 `publicView`。
 */
@Serializable
data class ReviewListDto(
    val items: List<PostDto>? = null,
    /** 游标，`omitempty`，为空表示没有下一页。 */
    val next: String? = null,
    /**
     * 本次实际使用的过滤条件（已 trim）。
     *
     * 键名由后端决定：按老师查是 `teacherName`，按老师+课程查是 `teacherName` + `courseName`。
     * 用 `Map` 承接而不是固定字段，是为了后端将来加条件时客户端不改代码。
     */
    val query: Map<String, String>? = null,
)

/**
 * `POST /posts` 的请求体（后端 `createRequest`）。
 *
 * `attachments` 是**先调 `/posts/attachments` 上传拿到的标识列表**，不是文件本身。
 */
@Serializable
data class CreatePostRequest(
    val board: String? = null,
    val title: String? = null,
    val body: String? = null,
    val attachments: List<String>? = null,
    /**
     * 结构化字段（评价贴必填）。
     *
     * 课程评价 `board = course_review` 需：
     * - `courseName`（必填，1-128）
     * - `teacherName`（选填，1-64）
     * - `rating`（必填，1-5 整数）
     *
     * 用 `JsonElement` 而不是 `Map<String, Any>`：kotlinx.serialization 无法序列化 Any。
     */
    val detail: Map<String, JsonElement>? = null,
)

/** `PATCH /posts/:id` 的请求体（部分更新，未传的字段不动）。 */
@Serializable
data class UpdatePostRequest(
    val title: String? = null,
    val body: String? = null,
    val attachments: List<String>? = null,
)

/** `POST /posts/:id/replies` 的请求体。 */
@Serializable
data class CreateReplyRequest(
    val body: String? = null,
)

/**
 * `POST /posts/:id/replies` 的成功响应：`{"data":{replyView}}`（HTTP 201）。
 *
 * 与帖子的 `PostEnvelopeDto` 分开：`data` 里是回复，不是帖子。
 */
@Serializable
data class PostReplyEnvelopeDto(
    val data: PostReplyDto? = null,
)

/**
 * `POST /posts/attachments` 的上传响应。
 *
 * 上传成功后把返回的标识填进 [CreatePostRequest] 的 `attachments`。
 */
@Serializable
data class UploadAttachmentDto(
    val id: String? = null,
    val url: String? = null,
)
