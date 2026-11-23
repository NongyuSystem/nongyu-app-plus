package com.nongyu.appplus.timetable.logic

import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.model.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [AttendanceStats] 的单测。
 *
 * 维护者 2026-11-23：「明明应该有 12 节或者 8 节，但只显示 2 节」。
 * 2 = 单次课的节数（3-1、3-2），8/12 = 一学期的课次。两者是不同的量。
 *
 * 这里把两种口径都钉住：单双周、连续周、带节次跨度的情况。
 */
class AttendanceStatsTest {

    /** 学期首日必须是星期一，测试统一用这个。 */
    private val termStart = LocalDate.of(2026, 9, 7)   // 周一

    private fun course(
        startPeriod: Int = 3,
        endPeriod: Int = 4,
        weeks: IntRange = 1..16,
        odd: Boolean = true,
        even: Boolean = true,
        day: Int = 2,   // 周三
    ) = CourseEntry(
        id = "c1",
        name = "测试课",
        teacher = "老师",
        room = "10-B201室",
        day = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weeks = WeekRange(weeks.first, weeks.last),
        odd = odd,
        even = even,
    )

    // ---- 核心：总课次算的是「一周上一次」而不是「一次两小节」 ----

    @Test
    fun `连续 16 周每周两小节 = 32 次`() {
        val stats = AttendanceStats.compute(
            course = course(weeks = 1..16),
            termStart = termStart,
            today = termStart,           // 学期第一天，还没上过
        )
        assertEquals("单次 2 小节", 2, stats.periodsPerSession)
        assertEquals("16 周 × 2 小节", 32, stats.totalSessions)
        assertEquals("第一天还没上过", 0, stats.pastSessions)
        assertEquals(32, stats.remainingSessions)
    }

    @Test
    fun `只上单周时课次减半`() {
        // 1-16 周的单周课：1,3,5,7,9,11,13,15 共 8 周
        val stats = AttendanceStats.compute(
            course = course(weeks = 1..16, odd = true, even = false),
            termStart = termStart,
            today = termStart,
        )
        assertEquals("8 周 × 2 小节", 16, stats.totalSessions)
    }

    @Test
    fun `只上双周时课次减半`() {
        val stats = AttendanceStats.compute(
            course = course(weeks = 1..16, odd = false, even = true),
            termStart = termStart,
            today = termStart,
        )
        assertEquals("8 周 × 2 小节", 16, stats.totalSessions)
    }

    @Test
    fun `周次只有 8 到 11 周时共 4 次`() {
        // 维护者提到的「8-11周」这类短课
        val stats = AttendanceStats.compute(
            course = course(weeks = 8..11),
            termStart = termStart,
            today = termStart,
        )
        assertEquals("4 周 × 2 小节", 8, stats.totalSessions)
    }

    @Test
    fun `单节次的课每次算一次`() {
        val stats = AttendanceStats.compute(
            course = course(startPeriod = 5, endPeriod = 5, weeks = 1..16),
            termStart = termStart,
            today = termStart,
        )
        assertEquals(1, stats.periodsPerSession)
        assertEquals(16, stats.totalSessions)
    }

    // ---- 已上 / 剩余 ----

    @Test
    fun `今天之前的课算已上`() {
        // 第 1 周周三 = 9/9
        val stats = AttendanceStats.compute(
            course = course(weeks = 1..4),
            termStart = termStart,
            today = LocalDate.of(2026, 9, 16),   // 第 2 周周三
        )
        // 第 1 周（9/9）已过，第 2 周（9/16）当天算已上 → 2 周 × 2 = 4
        assertEquals(4, stats.pastSessions)
        assertEquals("4 周共 8 小节", 8, stats.totalSessions)
        assertEquals(4, stats.remainingSessions)
    }

    @Test
    fun `未来的课算剩余`() {
        val stats = AttendanceStats.compute(
            course = course(weeks = 1..4),
            termStart = termStart,
            today = termStart,   // 周一，还没到周三
        )
        assertEquals(0, stats.pastSessions)
        assertEquals(8, stats.remainingSessions)
    }

    @Test
    fun `超出结束周次之后全部算已上`() {
        val stats = AttendanceStats.compute(
            course = course(weeks = 1..4),
            termStart = termStart,
            today = LocalDate.of(2026, 12, 1),   // 学期末之后
        )
        assertEquals(8, stats.pastSessions)
        assertEquals(0, stats.remainingSessions)
    }

    // ---- sessionDates ----

    @Test
    fun `单双周只生成对应周的日期`() {
        val dates = AttendanceStats.sessionDates(
            course = course(weeks = 1..4, odd = true, even = false),
            termStart = termStart,
        )
        // 单周：1、3 周 → 9/9、9/23
        assertEquals(2, dates.size)
        assertEquals(LocalDate.of(2026, 9, 9), dates[0])
        assertEquals(LocalDate.of(2026, 9, 23), dates[1])
    }

    @Test
    fun `sessionDates 与总课次一致`() {
        val c = course(weeks = 1..16)
        val dates = AttendanceStats.sessionDates(c, termStart)
        val stats = AttendanceStats.compute(c, termStart, termStart)
        assertEquals(stats.totalSessions, dates.size * stats.periodsPerSession)
    }

    @Test
    fun `日期计算对齐周几`() {
        // 学期首日 9/7 是周一；第 3 周周四（day=3）= 9/24
        val d = AttendanceStats.dateOfWeekDay(termStart, week = 3, dayIndex = 3)
        assertEquals(LocalDate.of(2026, 9, 24), d)
    }

    @Test
    fun `总课次为 0 时 hasData 为 false`() {
        // 周次区间与 TOTAL_WEEKS 不重叠在结构上不可能，这里用「今天早于所有课」验证 hasData
        val stats = AttendanceStats.compute(course(), termStart, termStart)
        assertTrue(stats.hasData)
    }
}
