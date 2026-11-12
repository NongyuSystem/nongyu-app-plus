package com.nongyu.appplus.shell

import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.model.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课表点击的卡片互斥规则。
 *
 * 这条规则此前在 MainShell 里重复了两遍（点课程 / 点空白格），
 * 两段逻辑**并不相同**：点课程盒子在课程卡开着时是「切卡」，
 * 点空白格才是「退出」。抽出来时逐条锁定这个差异。
 */
class CardIntentTest {

    private fun course(id: String) = CourseEntry(
        id = id,
        name = "高数",
        teacher = "张老师",
        room = "1-101",
        day = 0,
        startPeriod = 1,
        endPeriod = 2,
        weeks = WeekRange(1, 16),
    )

    // ---- currentOpenCard 优先级 ----

    @Test
    fun `都没开时为 None`() {
        assertEquals(OpenCard.None, currentOpenCard(null, null, hasDetailCourse = false))
    }

    @Test
    fun `添加日程优先级最高`() {
        assertEquals(
            OpenCard.AddSchedule,
            currentOpenCard(Triple(1, 1, 1), "sc-1", hasDetailCourse = true),
        )
    }

    @Test
    fun `日程明细次之`() {
        assertEquals(OpenCard.ScheduleDetail, currentOpenCard(null, "sc-1", hasDetailCourse = true))
    }

    @Test
    fun `课程详情最后`() {
        assertEquals(OpenCard.DetailCourse, currentOpenCard(null, null, hasDetailCourse = true))
    }

    // ---- 点课程盒子 ----

    @Test
    fun `没有卡片时点课程打开详情`() {
        val c = course("c1")
        assertEquals(ClickIntent.OpenDetail(c), resolveCourseClick(OpenCard.None, c))
    }

    @Test
    fun `课程卡开着时点另一门课直接切换`() {
        // 与点空白格不同：这是切卡，不是退出（维护者既有行为）
        val c2 = course("c2")
        assertEquals(ClickIntent.OpenDetail(c2), resolveCourseClick(OpenCard.DetailCourse, c2))
    }

    @Test
    fun `日程明细开着时点课程只关闭明细`() {
        assertEquals(
            ClickIntent.Dismiss,
            resolveCourseClick(OpenCard.ScheduleDetail, course("c1")),
        )
    }

    @Test
    fun `添加表单开着时点课程只关闭表单`() {
        assertEquals(
            ClickIntent.Dismiss,
            resolveCourseClick(OpenCard.AddSchedule, course("c1")),
        )
    }

    // ---- 点空白格 ----

    @Test
    fun `没有卡片时点空白格打开添加日程`() {
        assertEquals(
            ClickIntent.OpenAddSchedule(day = 2, period = 3, week = 5),
            resolveBlankSlotClick(OpenCard.None, day = 2, period = 3, week = 5),
        )
    }

    @Test
    fun `课程卡开着时点空白格立即清空`() {
        // 走 DismissDetailImmediately（直接清），不是 Dismiss（收拢动画）
        assertEquals(
            ClickIntent.DismissDetailImmediately,
            resolveBlankSlotClick(OpenCard.DetailCourse, day = 1, period = 1, week = 1),
        )
    }

    @Test
    fun `日程明细开着时点空白格只关闭明细`() {
        assertEquals(
            ClickIntent.Dismiss,
            resolveBlankSlotClick(OpenCard.ScheduleDetail, day = 1, period = 1, week = 1),
        )
    }

    @Test
    fun `添加表单开着时点空白格只关闭表单`() {
        assertEquals(
            ClickIntent.Dismiss,
            resolveBlankSlotClick(OpenCard.AddSchedule, day = 1, period = 1, week = 1),
        )
    }

    @Test
    fun `点课程与点空白格在课程卡开着时行为不同`() {
        // 把这条差异单独钉一次：两者结果不能相同
        val byCourse = resolveCourseClick(OpenCard.DetailCourse, course("c9"))
        val byBlank = resolveBlankSlotClick(OpenCard.DetailCourse, day = 1, period = 1, week = 1)
        assertEquals(ClickIntent.OpenDetail(course("c9")), byCourse)
        assertEquals(ClickIntent.DismissDetailImmediately, byBlank)
    }
}
