package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ReviewText] 的单测。
 *
 * 评价在 UI 上是纯文本，评分靠 `[n/5]` 前缀传递；这个格式是发评价能否带对评分的关键。
 */
class ReviewTextTest {

    // ---- display ----

    @Test
    fun `有评分时显示前缀`() {
        assertEquals("[4/5] 老师讲得很清楚", ReviewText.display(4, "老师讲得很清楚"))
    }

    @Test
    fun `无评分时原样显示`() {
        assertEquals("老师讲得很清楚", ReviewText.display(null, "老师讲得很清楚"))
    }

    // ---- parse ----

    @Test
    fun `解析标准评分前缀`() {
        val p = ReviewText.parse("[4/5] 老师讲得很清楚")
        assertEquals(4, p.rating)
        assertEquals("老师讲得很清楚", p.body)
    }

    @Test
    fun `不带前缀时默认满分`() {
        val p = ReviewText.parse("老师讲得很清楚")
        assertEquals(ReviewText.DEFAULT_RATING, p.rating)
        assertEquals("老师讲得很清楚", p.body)
    }

    @Test
    fun `评分边界 1 和 5 都能解析`() {
        assertEquals(1, ReviewText.parse("[1/5] 差").rating)
        assertEquals(5, ReviewText.parse("[5/5] 好").rating)
    }

    @Test
    fun `越界评分当普通正文不误解析`() {
        // [0/5] 和 [6/5] 都不在 1-5，应原样作为正文、评分取默认
        val p0 = ReviewText.parse("[0/5] 很差")
        assertEquals(ReviewText.DEFAULT_RATING, p0.rating)
        assertEquals("[0/5] 很差", p0.body)

        val p6 = ReviewText.parse("[6/5] 超好")
        assertEquals(ReviewText.DEFAULT_RATING, p6.rating)
        assertEquals("[6/5] 超好", p6.body)
    }

    @Test
    fun `空正文不丢评分`() {
        val p = ReviewText.parse("[3/5]")
        assertEquals(3, p.rating)
        assertEquals("", p.body)
    }

    @Test
    fun `首尾空白被裁掉`() {
        val p = ReviewText.parse("  [2/5]   还行  ")
        assertEquals(2, p.rating)
        assertEquals("还行", p.body)
    }

    // ---- 往返一致 ----

    @Test
    fun `display 后 parse 能还原评分与正文`() {
        val original = ReviewText.Parsed(rating = 4, body = "讲得不错")
        val text = ReviewText.display(original.rating, original.body)
        val parsed = ReviewText.parse(text)
        assertEquals(original, parsed)
    }
}
