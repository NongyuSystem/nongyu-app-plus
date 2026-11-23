package com.nongyu.appplus.timetable.logic

/**
 * 课程评价列表的**展示裁剪**（纯逻辑，可纯 JVM 单测）。
 *
 * ## 为什么请求 limit=4 而不是 3
 *
 * 后端只支持「精确匹配 + 游标分页」，没有 total 计数。
 * 前端要知道「是不是超过 3 条」（决定显不显示 more），就必须多取一条：
 *
 * ```
 * 请求 4 条 → 实际显示前 3 条 + 有第 4 条则显示 more
 * ```
 *
 * 只请求 3 条的话，拿到 3 条时无法区分「正好 3 条」和「还有更多」。
 */
object ReviewListDisplay {

    /** 实际展示的条数。 */
    const val VISIBLE_COUNT = 3

    /** 需要请求的条数（多一条用于探测 more）。 */
    const val FETCH_LIMIT = VISIBLE_COUNT + 1

    /**
     * 展示结果。
     *
     * @property visible 实际显示的前 3 条
     * @property hasMore 是否还有更多（拿到的条数 > 3）
     */
    data class Result<T>(
        val visible: List<T>,
        val hasMore: Boolean,
    )

    /** 从请求结果里拆出「前 3 条 + 是否有更多」。 */
    fun <T> split(items: List<T>): Result<T> = Result(
        visible = items.take(VISIBLE_COUNT),
        hasMore = items.size > VISIBLE_COUNT,
    )
}
