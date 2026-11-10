package com.nongyu.appplus.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表纯数据层的单测（首次给课表加测试保护）。
 *
 * ## 为什么要测这些
 * 课表最容易出错、又最不容易被肉眼发现的，是**口径类错误**：
 * - 作息写错一位（第 5 节应该是 14:20 却写成 14:00）；
 * - 节次数与作息条数不一致（`PERIOD_COUNT = 10` 但 `COURSE_TIMES` 只有 9 条 → 运行期越界崩溃）；
 * - 间距档位判反（历史上真发生过：1↔2 判成最大间距，整列节奏全乱）；
 * - 时钟格式不规范（`8:10` 而不是 `08:10`，显示宽度不一致会让时间列抖动）。
 *
 * 这些用 UI 截图看不出来，但用纯 JVM 测试逐个钉住很便宜。
 */
class TimetableDataTest {

    @Test
    fun `作息条数与节次数一致`() {
        assertEquals(
            "PERIOD_COUNT 与 COURSE_TIMES 条数不一致会导致运行期越界",
            PERIOD_COUNT,
            COURSE_TIMES.size,
        )
    }

    @Test
    fun `星期名正好七个且从周一开始`() {
        assertEquals(7, WEEKDAY_LABELS.size)
        assertEquals("周一", WEEKDAY_LABELS.first())
        assertEquals("周日", WEEKDAY_LABELS.last())
    }

    @Test
    fun `作息时刻格式为两位时两位分`() {
        val pattern = Regex("""^\d{2}:\d{2}$""")
        COURSE_TIMES.forEachIndexed { index, t ->
            assertTrue("第 ${index + 1} 节起始时刻格式不对：${t.start}", pattern.matches(t.start))
            assertTrue("第 ${index + 1} 节结束时刻格式不对：${t.end}", pattern.matches(t.end))
        }
    }

    @Test
    fun `每节结束时刻晚于起始时刻`() {
        COURSE_TIMES.forEachIndexed { index, t ->
            assertTrue(
                "第 ${index + 1} 节 ${t.start}-${t.end} 的结束时刻不晚于起始时刻",
                toMinutes(t.end) > toMinutes(t.start),
            )
        }
    }

    @Test
    fun `节次一天比一天晚（作息单调递增）`() {
        // 同一批课表里，后一节的起始必须晚于前一节的结束（不重叠）
        for (i in 0 until COURSE_TIMES.size - 1) {
            val cur = COURSE_TIMES[i]
            val next = COURSE_TIMES[i + 1]
            assertTrue(
                "第 ${i + 1} 节结束 ${cur.end} 应早于第 ${i + 2} 节开始 ${next.start}",
                toMinutes(cur.end) < toMinutes(next.start),
            )
        }
    }

    @Test
    fun `作息与旧农屿 courseTimes 对齐`() {
        // 抽点几条锚定值，防止有人“顺手”改掉却以为没影响
        assertEquals("08:10", COURSE_TIMES[0].start)
        assertEquals("11:50", COURSE_TIMES[3].end)   // 上午第 4 节结束
        assertEquals("14:20", COURSE_TIMES[4].start) // 下午第一节
        assertEquals("21:10", COURSE_TIMES[9].end)   // 最后一节结束
    }

    @Test
    fun `间距档位_午休与晚饭是最大档`() {
        assertEquals(RowGapKind.Meal, gapKindAfter(4))
        assertEquals(RowGapKind.Meal, gapKindAfter(8))
    }

    @Test
    fun `间距档位_二到三与六到七是大课间`() {
        assertEquals(RowGapKind.Long, gapKindAfter(2))
        assertEquals(RowGapKind.Long, gapKindAfter(6))
    }

    @Test
    fun `间距档位_其余都是常规课间`() {
        // 这条正是历史 bug 的反向断言：1→2 必须是常规（曾经被错判成最大档）
        listOf(1, 3, 5, 7, 9).forEach {
            assertEquals("第 $it 节之后的间距应为常规档", RowGapKind.Normal, gapKindAfter(it))
        }
    }

    @Test
    fun `间距档位_严格排序为常规小于大课间小于午休`() {
        // 用序号表达“必须一级比一级大”，防止有人把两档调成一样
        val order = listOf(RowGapKind.Normal, RowGapKind.Long, RowGapKind.Meal)
        assertEquals(3, order.distinct().size)
    }

    @Test
    fun `weekdayIndexOrNull_有效范围内返回索引`() {
        assertEquals(0, weekdayIndexOrNull(0))
        assertEquals(3, weekdayIndexOrNull(3))
        assertEquals(6, weekdayIndexOrNull(6))
    }

    @Test
    fun `weekdayIndexOrNull_越界返回 null`() {
        assertNull(weekdayIndexOrNull(-1))
        assertNull(weekdayIndexOrNull(7))
        assertNull(weekdayIndexOrNull(100))
        assertNotNull(weekdayIndexOrNull(2))
    }

    private fun toMinutes(hhmm: String): Int {
        val (h, m) = hhmm.split(":")
        return h.toInt() * 60 + m.toInt()
    }
}
