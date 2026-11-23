package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ReviewListDisplay] 的单测：从 4 条里拆出「3 条 + hasMore」。 */
class ReviewListDisplayTest {

    @Test
    fun `常量正确`() {
        assertEquals(3, ReviewListDisplay.VISIBLE_COUNT)
        assertEquals(4, ReviewListDisplay.FETCH_LIMIT)
    }

    @Test
    fun `三条以下不显示 more`() {
        val r = ReviewListDisplay.split(listOf("a", "b"))
        assertEquals(listOf("a", "b"), r.visible)
        assertFalse(r.hasMore)
    }

    @Test
    fun `正好三条不显示 more`() {
        val r = ReviewListDisplay.split(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), r.visible)
        assertFalse(r.hasMore)
    }

    @Test
    fun `四条显示前三条且有 more`() {
        val r = ReviewListDisplay.split(listOf("a", "b", "c", "d"))
        assertEquals(listOf("a", "b", "c"), r.visible)
        assertTrue(r.hasMore)
    }

    @Test
    fun `空列表`() {
        val r = ReviewListDisplay.split(emptyList<String>())
        assertTrue(r.visible.isEmpty())
        assertFalse(r.hasMore)
    }
}
