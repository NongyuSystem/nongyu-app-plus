package com.nongyu.appplus.timetable.logic

import com.nongyu.appplus.timetable.model.PERIOD_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 考试勤默认态判定。
 *
 * 这段逻辑原先住在 `MainShell` 里（private fun isPeriodEnded），
 * UI 层没法单测 —— 但它是「这节课算不算上过」的唯一判据，
 * 错一分钟就会让考勤默认态整排错位。
 */
class PeriodEndedTest {

    private fun t(hhmm: String): LocalTime {
        val (h, m) = hhmm.split(":")
        return LocalTime.of(h.toInt(), m.toInt())
    }

    @Test
    fun `第1节结束时刻是08点55分`() {
        // 作息真源在 COURSE_TIMES，这里锁住第 1 节，防止有人改顺手改坏
        assertEquals(8 * 60 + 55, periodEndMinuteOf(1))
        assertEquals("08:10", COURSE_TIMES[0].start)
        assertEquals("08:55", COURSE_TIMES[0].end)
    }

    @Test
    fun `结束时刻之前算未结束`() {
        assertFalse(isPeriodEnded(1, t("08:54")))
    }

    @Test
    fun `恰好等于结束时刻算已结束`() {
        // 边界：>= 才算结束。改成 > 会让 08:55 整这一分钟仍显示未考勤。
        assertTrue(isPeriodEnded(1, t("08:55")))
    }

    @Test
    fun `结束时刻之后算已结束`() {
        assertTrue(isPeriodEnded(1, t("12:00")))
    }

    @Test
    fun `全部十节都按各自的结束时刻判定`() {
        COURSE_TIMES.forEachIndexed { idx, period ->
            val end = t(period.end)
            val before = end.minusMinutes(1)
            assertEquals("第 ${idx + 1} 节在结束前一分钟应未结束", false, isPeriodEnded(idx + 1, before))
            assertEquals("第 ${idx + 1} 节在结束时刻应已结束", true, isPeriodEnded(idx + 1, end))
        }
    }

    @Test
    fun `越界节次返回null且不算结束`() {
        assertNull(periodEndMinuteOf(0))
        assertNull(periodEndMinuteOf(PERIOD_COUNT + 1))
        // 宁可判成「未结束」也不误判成「已上过课」
        assertFalse(isPeriodEnded(0, t("23:59")))
        assertFalse(isPeriodEnded(PERIOD_COUNT + 1, t("23:59")))
    }
}
