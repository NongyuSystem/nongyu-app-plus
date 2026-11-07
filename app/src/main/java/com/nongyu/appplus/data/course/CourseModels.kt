package com.nongyu.appplus.data.course

/**
 * 课表数据模型 —— 移植自旧项目 `nongyu-rn-app/src/modules/course/model/types.ts`。
 *
 * 保持字段名与语义一致，便于以后对接真实数据源（教务导入 / 后端同步）时
 * UI 层完全不用改。
 */

/** 教学周起止（含端点） */
data class WeekRange(val start: Int, val end: Int)

/**
 * 课表条目（教务课）。
 * @param day 1=周一 … 7=周日
 * @param startPeriod 1..10
 * @param weeksList 若存在且非空则优先于 [weeks] 范围
 */
data class CourseEntry(
    val id: String,
    val name: String,
    val teacher: String,
    val room: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: WeekRange,
    val weeksList: List<Int>? = null,
    val odd: Boolean = false,
    val even: Boolean = false,
)

/**
 * 自定义日程（独立实体，不复用 [CourseEntry]）。
 * @param weeksList 空列表视为全周有效
 * @param colorIndex 色板下标 0..7；null 表示默认样式
 */
data class ScheduleEntry(
    val id: String,
    val title: String,
    val content: String = "",
    val location: String = "",
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeksList: List<Int> = emptyList(),
    val colorIndex: Int? = null,
    val createdAt: String = "",
)

/** 考勤五态：签到 / 迟到 / 缺勤 / 请假 / 未考勤（老师未检查本节） */
enum class AttendanceStatus(val label: String, val shortLabel: String) {
    Present("签到", "签"),
    Late("迟到", "迟"),
    Absent("缺勤", "缺"),
    Leave("请假", "假"),
    NoCheck("未考勤", "未"),
}

/** 课程考勤（按课程实例：courseId + 教学周 + 星期） */
data class CourseAttendance(
    val id: String,
    val courseId: String,
    val week: Int,
    val day: Int,
    val status: AttendanceStatus,
)

/** 同格堆叠项：课程或日程 */
sealed interface StackItem {
    data class Course(val course: CourseEntry) : StackItem
    data class Schedule(val schedule: ScheduleEntry) : StackItem
}

/** 同格堆叠主格（有课则首项为课程；日程按 createdAt 升序） */
data class GridStack(
    val items: List<StackItem>,
    val spanRows: Int,
)

/** 被连堂占用的占位格 */
data class GridOccupied(val primaryRow: Int)

/** 单周矩阵单元格 */
sealed interface GridCell {
    data class Stack(val value: GridStack) : GridCell
    data class Occupied(val value: GridOccupied) : GridCell
}

/** 单周：5 行大课区间 × 7 列 */
typealias WeekGridData = List<List<GridCell?>>

/** 课表卡片配色 */
data class CourseColor(val bg: Long, val text: Long)
