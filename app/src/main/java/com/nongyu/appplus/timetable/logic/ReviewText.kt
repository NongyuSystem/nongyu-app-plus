package com.nongyu.appplus.timetable.logic

/**
 * 课程评价文本的**显示与输入格式**（纯逻辑，可纯 JVM 单测）。
 *
 * ## 为什么要格式
 *
 * 后端评价是结构化的：`detail.rating`(1-5) + `body`(正文)。
 * 但现有评论 UI 只认 `List<String>` 纯文本，签名不改的前提下要传递评分，
 * 就用文本前缀：`[4/5] 老师讲得很清楚`。
 *
 * ## 格式
 *
 * ```
 * 显示：[4/5] 正文
 * 输入：[4/5] 正文 → 评分 4，正文「正文」
 *       正文       → 评分默认 5，正文「正文」
 * ```
 */
object ReviewText {

    /** 默认评分：用户没写 `[n/5]` 时按满分算（避免强制用户输入格式）。 */
    const val DEFAULT_RATING = 5

    /** 显示时给正文加评分前缀。老数据无评分时原样返回。 */
    fun display(rating: Int?, body: String): String =
        if (rating != null) "[$rating/5] $body" else body

    /**
     * 从输入文本解析出 (评分, 正文)。
     *
     * 只识别行首的 `[n/5] `；n 必须在 1-5，其它一律当普通正文（评分取默认）。
     * 这样用户输入「[0/5] 很差」不会被误当成评分语法，而是原样发出。
     */
    fun parse(input: String): Parsed {
        val text = input.trim()
        val match = Regex("^\\[(\\d+)/5\\]\\s*(.*)$").find(text)
        if (match != null) {
            val rating = match.groupValues[1].toIntOrNull()
            if (rating != null && rating in 1..5) {
                return Parsed(rating = rating, body = match.groupValues[2].trim())
            }
        }
        return Parsed(rating = DEFAULT_RATING, body = text)
    }

    data class Parsed(val rating: Int, val body: String)
}
