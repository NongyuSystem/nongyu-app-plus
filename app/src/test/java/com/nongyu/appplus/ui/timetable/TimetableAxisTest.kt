package com.nongyu.appplus.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 票 #60 的时间轴映射单测。
 *
 * 重点验证「不均匀的时间轴」：课与课之间的午休/大课间也占真实时间，
 * 所以第 N 节的位置不能按等分算。这里的断言值都按 [COURSE_TIMES] 现算，
 * 换 mock 数据时会一起失败，不会静默漂移。
 */
class TimetableAxisTest {

    @Test
    fun `轴起点终点来自第一节与最后一节`() {
        assertEquals(LocalTime.of(8, 10), TimetableAxis.axisStart)
        assertEquals(LocalTime.of(21, 10), TimetableAxis.axisEnd)
        // 08:10 -> 21:10 共 13 小时 = 780 分钟
        assertEquals(780, TimetableAxis.totalMinutes)
    }

    @Test
    fun `轴起点映射到 0，终点映射到 1`() {
        assertEquals(0f, TimetableAxis.fractionOf(LocalTime.of(8, 10)), 1e-6f)
        assertEquals(1f, TimetableAxis.fractionOf(LocalTime.of(21, 10)), 1e-6f)
    }

    @Test
    fun `早于起点与晚于终点会被夹到边界`() {
        assertEquals(0f, TimetableAxis.fractionOf(LocalTime.of(6, 0)), 1e-6f)
        assertEquals(1f, TimetableAxis.fractionOf(LocalTime.of(23, 59)), 1e-6f)
    }

    @Test
    fun `10点半落在第3节的区间内而不是等分位置`() {
        // 第 3 节 10:10-10:55；10:30 应落在该节区间内部
        val r = TimetableAxis.periodFraction(3)
        val f = TimetableAxis.fractionOf(LocalTime.of(10, 30))
        assertTrue("10:30 应落在第3节区间 $r 内，实际 $f", f > r.start && f < r.endInclusive)
    }

    @Test
    fun `午休时段按真实时间计，不是等分`() {
        // 第 4 节结束 11:50、第 5 节开始 14:20，中间 150 分钟空档必须占轴上的位置
        val endOfFourth = TimetableAxis.fractionOf(LocalTime.of(11, 50))
        val startOfFifth = TimetableAxis.fractionOf(LocalTime.of(14, 20))
        val gapFraction = startOfFifth - endOfFourth
        // 150 / 780 ≈ 0.1923；若按等分算这个空档不会存在
        assertEquals(150f / 780f, gapFraction, 1e-4f)
    }

    @Test
    fun `比例与时刻互为逆运算`() {
        val t = LocalTime.of(15, 40)
        val f = TimetableAxis.fractionOf(t)
        val back = TimetableAxis.timeAtFraction(f)
        // 只允许 1 分钟取整误差
        assertTrue(
            "往返应还原到分钟级，得到 $back",
            kotlin.math.abs(back.toSecondOfDay() - t.toSecondOfDay()) <= 60,
        )
    }

    @Test
    fun `周期区间起止按真实时刻且递增`() {
        var lastEnd = -1f
        for (p in 1..PERIOD_COUNT) {
            val r = TimetableAxis.periodFraction(p)
            assertTrue("第 $p 节起点应小于终点", r.start < r.endInclusive)
            assertTrue("第 $p 节起点应不小于上一节终点", r.start >= lastEnd - 1e-6f)
            lastEnd = r.endInclusive
        }
    }

    @Test
    fun `手指落在课间空档时归到下一节`() {
        // 11:50-14:20 是空档，取 12:30 应归到第 5 节（接下来的那节）
        val f = TimetableAxis.fractionOf(LocalTime.of(12, 30))
        assertEquals(5, TimetableAxis.periodAtFraction(f))
    }

    @Test
    fun `手指落在某节内部时归到该节`() {
        val f = TimetableAxis.fractionOf(LocalTime.of(10, 30))
        assertEquals(3, TimetableAxis.periodAtFraction(f))
    }

    @Test
    fun `超出末端归到最后一节`() {
        assertEquals(PERIOD_COUNT, TimetableAxis.periodAtFraction(1f))
    }

    @Test
    fun `时间格式化补零`() {
        assertEquals("08:05", formatHhMm(LocalTime.of(8, 5)))
        assertEquals("21:10", formatHhMm(LocalTime.of(21, 10)))
    }
}
