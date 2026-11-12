package com.nongyu.appplus.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底部条形态推导与卡片外退出归属。
 *
 * 这两个 `when` 原先内联在 MainShell 里，顺序错了只会表现成
 * 「从工具条点添加日程，结果同时弹出了课程详情卡」这类难复现的交互 bug，
 * 没有单测根本守不住。抽成纯函数后在这里把优先级钉死。
 */
class ShellStateTest {

    @Test
    fun `什么都不开时是普通导航栏`() {
        assertEquals(
            BarMode.Normal,
            deriveBarMode(null, null, hasDetailCourse = false, orbMode = OrbMode.Idle),
        )
    }

    @Test
    fun `点按星云球进入输入态`() {
        assertEquals(
            BarMode.Input,
            deriveBarMode(null, null, hasDetailCourse = false, orbMode = OrbMode.Input),
        )
    }

    @Test
    fun `长按星云球不算底栏形态变化`() {
        // Listening 只压暗背景、球体放大，底栏保持普通导航栏
        assertEquals(
            BarMode.Normal,
            deriveBarMode(null, null, hasDetailCourse = false, orbMode = OrbMode.Listening),
        )
    }

    @Test
    fun `打开课程详情卡占用底栏`() {
        assertEquals(
            BarMode.Detail,
            deriveBarMode(null, null, hasDetailCourse = true, orbMode = OrbMode.Idle),
        )
    }

    @Test
    fun `添加日程优先于课程详情卡`() {
        // 从工具条点「添加日程」时详情卡可能还开着，此时必须是表单赢
        assertEquals(
            BarMode.AddSchedule,
            deriveBarMode(
                addingSchedule = Triple(1, 1, 1),
                scheduleDetailId = null,
                hasDetailCourse = true,
                orbMode = OrbMode.Idle,
            ),
        )
    }

    @Test
    fun `添加日程优先于日程明细`() {
        assertEquals(
            BarMode.AddSchedule,
            deriveBarMode(Triple(1, 1, 1), "sc-1", hasDetailCourse = false, orbMode = OrbMode.Idle),
        )
    }

    @Test
    fun `日程明细优先于课程详情卡`() {
        assertEquals(
            BarMode.ScheduleDetail,
            deriveBarMode(null, "sc-1", hasDetailCourse = true, orbMode = OrbMode.Idle),
        )
    }

    @Test
    fun `课程详情卡优先于输入态`() {
        assertEquals(
            BarMode.Detail,
            deriveBarMode(null, null, hasDetailCourse = true, orbMode = OrbMode.Input),
        )
    }

    @Test
    fun `只有详情与表单形态需要卡片外退出层`() {
        assertEquals(DismissTarget.Detail, dismissTargetOf(BarMode.Detail))
        assertEquals(DismissTarget.ScheduleDetail, dismissTargetOf(BarMode.ScheduleDetail))
        assertEquals(DismissTarget.AddSchedule, dismissTargetOf(BarMode.AddSchedule))
        assertEquals(DismissTarget.None, dismissTargetOf(BarMode.Normal))
        assertEquals(DismissTarget.None, dismissTargetOf(BarMode.Input))
    }
}
