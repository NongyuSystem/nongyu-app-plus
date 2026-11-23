package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ReviewText] 的单测。
 *
 * 维护者 2026-11-23：评分为 0 或负 => 没打分，不显示星；有分显示星星图标。
 */
class ReviewTextTest {

    // ---- display ----

    @Test
    fun `满星显示五颗实心星`() {
        assertEquals("★★★★★ 好课", ReviewText.display(5, "好课"))
    }

    @Test
    fun `三星显示三实两空`() {
        assertEquals("★★★☆☆ 还行", ReviewText.display(3, "还行"))
    }

    @Test
    fun `一星显示一实四空`() {
        assertEquals("★☆☆☆☆ 差", ReviewText.display(1, "差"))
    }

    @Test
    fun `零分不显示星（没打分）`() {
        assertEquals("随便说说", ReviewText.display(0, "随便说说"))
    }

    @Test
    fun `负分不显示星`() {
        assertEquals("随便说说", ReviewText.display(-1, "随便说说"))
    }

    @Test
    fun `无评分不显示星`() {
        assertEquals("随便说说", ReviewText.display(null, "随便说说"))
    }

    @Test
    fun `超出上限夹到五星`() {
        assertEquals("★★★★★ 好", ReviewText.display(99, "好"))
    }

    // ---- parse ----

    @Test
    fun `解析星号前缀`() {
        val p = ReviewText.parse("★★★☆ 讲得不错")
        assertEquals(3, p.rating)
        assertEquals("讲得不错", p.body)
    }

    @Test
    fun `解析五颗星`() {
        val p = ReviewText.parse("★★★★★ 好")
        assertEquals(5, p.rating)
        assertEquals("好", p.body)
    }

    @Test
    fun `兼容旧的中括号格式`() {
        assertEquals(4, ReviewText.parse("[4/5] 好").rating)
        assertEquals(4, ReviewText.parse("[4] 好").rating)
    }

    @Test
    fun `纯文本视为没打分`() {
        val p = ReviewText.parse("老师讲得很清楚")
        assertEquals(ReviewText.NO_RATING, p.rating)
        assertEquals("老师讲得很清楚", p.body)
    }

    @Test
    fun `越界的方括号评分当普通正文`() {
        val p = ReviewText.parse("[0/5] 很差")
        assertEquals(ReviewText.NO_RATING, p.rating)
        assertEquals("[0/5] 很差", p.body)
    }

    @Test
    fun `首尾空白被裁掉`() {
        val p = ReviewText.parse("  ★★  还行  ")
        assertEquals(2, p.rating)
        assertEquals("还行", p.body)
    }

    // ---- 往返一致 ----

    @Test
    fun `display 后 parse 能还原评分与正文`() {
        val original = ReviewText.Parsed(rating = 4, body = "讲得不错")
        val text = ReviewText.display(original.rating, original.body)
        assertEquals(original, ReviewText.parse(text))
    }

    @Test
    fun `没打分时 display 后 parse 仍是没打分`() {
        val text = ReviewText.display(ReviewText.NO_RATING, "随便说说")
        assertEquals("随便说说", text)
        assertEquals(ReviewText.NO_RATING, ReviewText.parse(text).rating)
    }
}
