package com.nongyu.appplus.timetable.logic

import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.model.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程详情卡的编辑态合并规则。
 *
 * 这些规则原先内联在 MainShell 的 `remember(courseId)` 里，一条都测不了，
 * 而它们恰好是「备注重启后丢值」「考勤点过又被冲掉」这类事故的直接来源。
 */
class CourseDetailRulesTest {

    private fun course(
        id: String = "c1",
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        room: String = "1-101",
        teacher: String = "张老师",
    ) = CourseEntry(
        id = id,
        name = "高数",
        teacher = teacher,
        room = room,
        weeks = WeekRange(1, 16),
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        day = 0,
    )

    // ---- 地点三级回落 ----

    @Test
    fun `按天覆盖优先于通用覆盖与原始值`() {
        assertEquals("A-201", resolveLocationLabel("A-201", "B-302", "1-101"))
    }

    @Test
    fun `没有按天覆盖时用通用覆盖`() {
        assertEquals("B-302", resolveLocationLabel(null, "B-302", "1-101"))
    }

    @Test
    fun `都没有覆盖时回落原始 room`() {
        assertEquals("1-101", resolveLocationLabel(null, null, "1-101"))
    }

    @Test
    fun `空串算作已设置而不是回落`() {
        // 用户把地点清空是有意义的操作，不该被当成「没设过」而显示回原值
        assertEquals("", resolveLocationLabel("", "B-302", "1-101"))
    }

    // ---- 教师回落 ----

    @Test
    fun `教师覆盖优先`() {
        assertEquals("李老师", resolveTeacherLabel("李老师", "张老师"))
    }

    @Test
    fun `没有教师覆盖时用原始教师`() {
        assertEquals("张老师", resolveTeacherLabel(null, "张老师"))
    }

    // ---- 考勤合并 ----

    @Test
    fun `显式考勤记录优先于时间默认值`() {
        // 用户点过「已考勤」，即使时间判定说没结束，也以用户为准
        val merged = mergeAttendance(
            course = course(startPeriod = 1, endPeriod = 2),
            overrides = mapOf(1 to true),
            defaultFor = { false },
        )
        assertEquals(mapOf(1 to true, 2 to false), merged)
    }

    @Test
    fun `没有显式记录时全部走默认判定`() {
        val merged = mergeAttendance(
            course = course(startPeriod = 1, endPeriod = 3),
            overrides = emptyMap(),
            defaultFor = { it <= 2 },
        )
        assertEquals(mapOf(1 to true, 2 to true, 3 to false), merged)
    }

    @Test
    fun `课程为空时不产生任何考勤项`() {
        assertTrue(mergeAttendance(null, mapOf(1 to true)) { true }.isEmpty())
    }

    @Test
    fun `只覆盖课程自身的节次区间`() {
        // 区间是 3..4，overrides 里 1、2、5 属于别的节次，不该带进来
        val merged = mergeAttendance(
            course = course(startPeriod = 3, endPeriod = 4),
            overrides = mapOf(1 to true, 2 to true, 3 to true, 5 to true),
            defaultFor = { false },
        )
        assertEquals(mapOf(3 to true, 4 to false), merged)
    }

    // ---- 考勤翻转 ----

    @Test
    fun `点击翻转的是当前生效值`() {
        val current = mapOf(1 to true, 2 to false)
        assertEquals(mapOf(1 to false, 2 to false), toggledAttendance(current, 1))
        assertEquals(mapOf(1 to true, 2 to true), toggledAttendance(current, 2))
    }

    @Test
    fun `翻转不存在的节次时原样返回`() {
        val current = mapOf(1 to true)
        assertEquals(current, toggledAttendance(current, 9))
    }

    // ---- 备注落盘判定 ----

    @Test
    fun `初始化未完成时不落盘`() {
        // 否则首次打开卡片会把空串覆盖掉已存备注（真机丢值事故）
        assertFalse(shouldPersistNote(ready = false, courseId = "c1"))
    }

    @Test
    fun `没有选中课程时不落盘`() {
        assertFalse(shouldPersistNote(ready = true, courseId = null))
    }

    @Test
    fun `初始化完成且有课程时才落盘`() {
        assertTrue(shouldPersistNote(ready = true, courseId = "c1"))
    }

    @Test
    fun `内容未变时跳过写入`() {
        assertFalse(noteChanged("已存的内容", "已存的内容"))
        assertTrue(noteChanged("旧内容", "新内容"))
    }
}
