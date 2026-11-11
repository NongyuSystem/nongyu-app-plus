package com.nongyu.appplus.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 票 #60：课表几何 ↔ 真实时钟的分段线性映射。
 *
 * ## 这套测试要证明的三件事
 *
 * 1. **首尾对齐**：比例 0 = 第 1 节开始（08:10），比例 1 = 第 10 节结束（21:10）；
 * 2. **密度不同**：45 分钟的一节课占满一整行高度，而 150 分钟的午休只占一条缝，
 *    所以单位"比例"对应的分钟数在两者之间差一个数量级 —— 这正是
 *    「尺子等距、但滑动时时间跳得快慢不同」的来源；
 * 3. **双向互逆**：比例 → 时刻 → 比例 能还原（分钟级误差）。
 */
class TimetableGeometryTest {

    // 用真实量级的 800dp（课表时间轴大约这么高）构建
    private val mapper = TimetableGeometry.build(totalHeightDp = 800f)

    @Test
    fun `比例 0 是第一节开始，比例 1 是第十节结束`() {
        assertEquals(LocalTime.of(8, 10), mapper.timeAtFraction(0f))
        assertEquals(LocalTime.of(21, 10), mapper.timeAtFraction(1f))
    }

    @Test
    fun `比例与时刻双向互逆`() {
        val times = listOf(
            LocalTime.of(8, 10), LocalTime.of(9, 30), LocalTime.of(10, 30),
            LocalTime.of(13, 0), LocalTime.of(17, 30), LocalTime.of(21, 0),
        )
        for (t in times) {
            val f = mapper.fractionAtTime(t)
            val back = mapper.timeAtFraction(f)
            val diff = kotlin.math.abs(back.toSecondOfDay() - t.toSecondOfDay())
            assertTrue("$t → f=$f → $back（差 ${diff}s，应 ≤ 90s）", diff <= 90)
        }
    }

    @Test
    fun `第一节 45 分钟占满一整行`() {
        val f0 = mapper.fractionAtTime(LocalTime.of(8, 10))
        val f1 = mapper.fractionAtTime(LocalTime.of(8, 55))
        val rowSpan = f1 - f0
        // 行高约 1/10 多，允许缝的影响，量级应显著大于一条缝
        assertTrue("第一节占据比例 $rowSpan 应接近一行高度", rowSpan > 0.07f)
    }

    @Test
    fun `午休 150 分钟只占一条缝，密度远高于正课`() {
        // 正课：45 分钟摊在一整行
        val perMinuteInClass = run {
            val a = mapper.fractionAtTime(LocalTime.of(8, 10))
            val b = mapper.fractionAtTime(LocalTime.of(8, 55))
            (b - a) / 45f
        }
        // 午休：11:50 → 14:20 共 150 分钟，压在一缝里
        val perMinuteInBreak = run {
            val a = mapper.fractionAtTime(LocalTime.of(11, 50))
            val b = mapper.fractionAtTime(LocalTime.of(14, 20))
            (b - a) / 150f
        }
        assertTrue(
            "午休每分钟占比（$perMinuteInBreak）应远小于正课（$perMinuteInClass）",
            perMinuteInBreak < perMinuteInClass * 0.2f,
        )
    }

    @Test
    fun `比例单调递增：时间不走回头路`() {
        var last = -1
        var f = 0f
        while (f <= 1f) {
            val m = mapper.timeAtFraction(f).toSecondOfDay()
            assertTrue("f=$f 时刻应不早于上一采样点", m >= last)
            last = m
            f += 0.01f
        }
    }

    @Test
    fun `比例归到节次：落在课内归本节`() {
        val f = mapper.fractionAtTime(LocalTime.of(10, 30))   // 第 3 节内
        assertEquals(3, mapper.periodAtFraction(f))
    }

    @Test
    fun `比例归到节次：落在午休缝里归下一节`() {
        val f = mapper.fractionAtTime(LocalTime.of(13, 0))    // 午休中
        assertEquals(5, mapper.periodAtFraction(f))
    }

    @Test
    fun `超出范围会被夹住`() {
        assertEquals(LocalTime.of(8, 10), mapper.timeAtFraction(-5f))
        assertEquals(LocalTime.of(21, 10), mapper.timeAtFraction(5f))
        assertEquals(PERIOD_COUNT, mapper.periodAtFraction(1f))
    }
}
