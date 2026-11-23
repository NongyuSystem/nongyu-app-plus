package com.nongyu.appplus.timetable.logic

/**
 * 课程评价文本的**显示与输入格式**（纯逻辑，可纯 JVM 单测）。
 *
 * ## 为什么要格式
 *
 * 后端评价是结构化的：`detail.rating` + `body`(正文)。
 * 但现有评论 UI 只认 `List<String>` 纯文本，签名不改的前提下要传递评分，
 * 就用文本前缀：`★★★☆☆ 老师讲得很清楚`。
 *
 * ## 格式
 *
 * ```
 * 显示：★★★☆☆ 正文   （rating=3）
 *       正文          （rating<=0，没打分 → 不显示星）
 * 输入：★★★ 正文      → 3 星；不打分 → 0（正文原样）
 * ```
 *
 * 维护者 2026-11-23：星星形式；**没打分（评分 <= 0）就完全不显示星**，
 * 不要出现 `[0/5]` 或空星这种噪音。
 */
object ReviewText {

    /**
     * 没打分时的评分值（0 或负数）。
     *
     * 显示层据此不画星；发送层据此不带 rating 字段。
     * 后端 `course_review.rating` 已改为选填（0011 迁移），
     * 所以不打星的评价能正常发出去，detail 里不含 rating。
     */
    const val NO_RATING = 0

    /** 满星数。 */
    const val MAX_STARS = 5

    /**
     * 显示时给正文加星星前缀。
     *
     * rating <= 0 → 只有正文，没有星（维护者要求）。
     * rating 超范围时夹到 1..5，避免画出非法数量的星。
     */
    fun display(rating: Int?, body: String): String {
        if (rating == null || rating <= 0) return body
        val filled = rating.coerceIn(1, MAX_STARS)
        val stars = "★".repeat(filled) + "☆".repeat(MAX_STARS - filled)
        return "$stars $body"
    }

    /**
     * 从输入文本解析出 (评分, 正文)。
     *
     * 支持行首的 `[n/5]`、`[n]`、以及连续星号 `★★★` 三种写法；
     * n 必须在 1-5。没有任何评分标记 → rating = [NO_RATING]（没打分）。
     */
    fun parse(input: String): Parsed {
        val text = input.trim()

        // ① 星号开头：★★★☆ 正文
        val stars = text.takeWhile { it == '★' || it == '☆' }
        if (stars.isNotEmpty()) {
            val rating = stars.count { it == '★' }
            val body = text.removePrefix(stars).trim()
            if (rating in 1..MAX_STARS) return Parsed(rating, body)
        }

        // ② [n/5] 或 [n] 开头（兼容旧格式）
        val match = Regex("^\\[(\\d+)(?:/5)?\\]\\s*(.*)$").find(text)
        if (match != null) {
            val rating = match.groupValues[1].toIntOrNull()
            if (rating != null && rating in 1..MAX_STARS) {
                return Parsed(rating, match.groupValues[2].trim())
            }
        }

        // ③ 没打分
        return Parsed(NO_RATING, text)
    }

    data class Parsed(val rating: Int, val body: String)
}
