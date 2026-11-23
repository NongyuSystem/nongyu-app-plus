package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RoomLineBreaker] 的单测。
 *
 * 维护者 2026-11-23 要求：教室宽度不够时，**有「-」就在横杠处换行，没有就在最大字数处换行**。
 * 这里覆盖真实教务数据里出现过的几种教室写法。
 */
class RoomLineBreakerTest {

    // ---- 含横杠：在第一个横杠处断 ----

    @Test
    fun `标准教室号在横杠处断成楼栋与房间`() {
        // 10-B201室 → "10-" / "B201室"
        assertEquals(
            listOf("10-", "B201室"),
            RoomLineBreaker.breakLines("10-B201室", maxChars = 20),
        )
    }

    @Test
    fun `带校区前缀时仍在第一个横杠处断`() {
        // 雅安校区-10-B201室 → "雅安校区-" / "10-B201室"
        // 断在第一个横杠：两段都还能读；断在最后一个只剩「室」字
        assertEquals(
            listOf("雅安校区-", "10-B201室"),
            RoomLineBreaker.breakLines("雅安校区-10-B201室", maxChars = 20),
        )
    }

    @Test
    fun `全角横杠也能断`() {
        assertEquals(
            listOf("10－", "B308室"),
            RoomLineBreaker.breakLines("10－B308室", maxChars = 20),
        )
    }

    @Test
    fun `有横杠时优先断横杠而不是按字数切`() {
        // 即使 maxChars 很小，横杠优先级更高
        val lines = RoomLineBreaker.breakLines("10-B201室", maxChars = 2)
        assertEquals(listOf("10-", "B201室"), lines)
    }

    // ---- 无横杠：按字数上限切 ----

    @Test
    fun `无横杠且不超长时保持一行`() {
        assertEquals(listOf("东操场"), RoomLineBreaker.breakLines("东操场", maxChars = 10))
    }

    @Test
    fun `无横杠且超长时按字数切`() {
        // 机房A栋301 共 7 字，上限 4 → "机房A栋" / "301"
        assertEquals(
            listOf("机房A栋", "301"),
            RoomLineBreaker.breakLines("机房A栋301", maxChars = 4),
        )
    }

    @Test
    fun `两行仍放不下时第二行补省略号`() {
        val room = "第一教学楼A区三层连廊教室"
        val lines = RoomLineBreaker.breakLines(room, maxChars = 4)
        assertEquals(2, lines.size)
        assertEquals("第一教学", lines[0])
        assertTrue("第二行应以省略号结尾", lines[1].endsWith("…"))
    }

    @Test
    fun `最多只返回两行`() {
        val room = "很长很长很长很长很长很长的教室名字"
        assertTrue(RoomLineBreaker.breakLines(room, maxChars = 3).size <= 2)
    }

    // ---- 边界 ----

    @Test
    fun `空输入返回空列表`() {
        assertTrue(RoomLineBreaker.breakLines("", maxChars = 5).isEmpty())
        assertTrue(RoomLineBreaker.breakLines("   ", maxChars = 5).isEmpty())
    }

    @Test
    fun `横杠在末尾时不断行`() {
        // "10-" 断开会得到空的后半段，没有意义
        assertEquals(listOf("10-"), RoomLineBreaker.breakLines("10-", maxChars = 10))
    }

    @Test
    fun `maxChars 非正数时视为不限长度`() {
        assertEquals(listOf("东操场"), RoomLineBreaker.breakLines("东操场", maxChars = 0))
        assertEquals(listOf("东操场"), RoomLineBreaker.breakLines("东操场", maxChars = -1))
    }

    @Test
    fun `前后空白会被裁掉`() {
        assertEquals(listOf("10-", "B201室"), RoomLineBreaker.breakLines("  10-B201室  ", maxChars = 20))
    }

    // ---- needsTwoLines ----

    @Test
    fun `needsTwoLines 与实际行数一致`() {
        assertTrue(RoomLineBreaker.needsTwoLines("10-B201室", maxChars = 20))
        assertFalse(RoomLineBreaker.needsTwoLines("东操场", maxChars = 20))
        assertFalse(RoomLineBreaker.needsTwoLines("", maxChars = 20))
    }

    // ---- displayLines（给 UI 用）----

    @Test
    fun `displayLines 保留原文的室字`() {
        // 维护者明确：不去掉「室」字，教室名原样保留
        assertEquals(listOf("10-", "B201室"), RoomLineBreaker.displayLines("10-B201室", maxChars = 20))
    }

    @Test
    fun `displayLines 最多两行`() {
        val room = "很长很长很长很长很长很长的教室名字"
        assertTrue(RoomLineBreaker.displayLines(room, maxChars = 3).size <= 2)
    }

    @Test
    fun `displayLines 去掉切分后两端的空格`() {
        // 横杠切断后后半段可能带空格
        val lines = RoomLineBreaker.displayLines("10- B201室", maxChars = 20)
        assertEquals(listOf("10-", "B201室"), lines)
    }

    @Test
    fun `displayLines 对空教室返回空列表`() {
        assertTrue(RoomLineBreaker.displayLines("", maxChars = 10).isEmpty())
    }
}
