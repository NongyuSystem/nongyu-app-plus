package com.nongyu.appplus.data.course

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 课表仓库（当前为纯本地假数据）。
 *
 * 设计要点：
 * - 只暴露 [courseState]，UI 层订阅 flow，不感知数据来源。
 *   以后换成 Room 数据库 / 教务导入 / 后端同步时，只需替换本类实现，Composables 不动。
 * - 术语与旧项目一致：course = 教务课，schedule = 自定义日程。
 */
object CourseRepository {

    /** 开学日（教学周 1 的周一所在的自然周）——假数据先固定 */
    val semesterStart: java.time.LocalDate = java.time.LocalDate.now()
        .with(java.time.DayOfWeek.MONDAY)
        .minusWeeks(5)

    data class State(
        val courses: List<CourseEntry> = emptyList(),
        val schedules: List<ScheduleEntry> = emptyList(),
        val attendances: List<CourseAttendance> = emptyList(),
        val currentWeek: Int = 1,
        val selectedWeek: Int = 1,
        val maxWeek: Int = 16,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val courses = mockCourses()
        val schedules = mockSchedules()
        val attendances = mockAttendances(courses)
        val cur = computeCurrentWeek(semesterStart)
        val maxW = maxWeekFromAll(courses, schedules)
        _state.value = State(
            courses = courses,
            schedules = schedules,
            attendances = attendances,
            currentWeek = cur,
            selectedWeek = cur.coerceIn(1, maxW),
            maxWeek = maxW,
        )
    }

    /** 由开学日算今天第几教学周（至少 1） */
    fun computeCurrentWeek(start: java.time.LocalDate, now: java.time.LocalDate = java.time.LocalDate.now()): Int {
        val firstMonday = start.with(java.time.DayOfWeek.MONDAY)
        val days = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, now)
        return maxOf(1, (days / 7).toInt() + 1)
    }

    fun selectWeek(week: Int) {
        _state.update { it.copy(selectedWeek = week.coerceIn(1, it.maxWeek)) }
    }

    fun backToCurrentWeek() {
        _state.update { it.copy(selectedWeek = it.currentWeek.coerceIn(1, it.maxWeek)) }
    }

    /** 新建日程 */
    fun addSchedule(entry: ScheduleEntry) {
        _state.update { st ->
            val next = st.schedules + entry
            st.copy(
                schedules = next,
                maxWeek = maxWeekFromAll(st.courses, next),
            )
        }
    }

    /** 更新日程 */
    fun updateSchedule(entry: ScheduleEntry) {
        _state.update { st ->
            val next = st.schedules.map { if (it.id == entry.id) entry else it }
            st.copy(
                schedules = next,
                maxWeek = maxWeekFromAll(st.courses, next),
            )
        }
    }

    /** 删除日程 */
    fun deleteSchedule(id: String) {
        _state.update { st -> st.copy(schedules = st.schedules.filterNot { it.id == id }) }
    }

    /** 写入 / 覆盖某节课的考勤 */
    fun setAttendance(courseId: String, week: Int, day: Int, status: AttendanceStatus?) {
        _state.update { st ->
            val key = attendanceSlotKey(courseId, week, day)
            val rest = st.attendances.filterNot {
                attendanceSlotKey(it.courseId, it.week, it.day) == key
            }
            val next = if (status == null) {
                rest
            } else {
                rest + CourseAttendance(
                    id = key,
                    courseId = courseId,
                    week = week,
                    day = day,
                    status = status,
                )
            }
            st.copy(attendances = next)
        }
    }

    // ---------------- 假数据 ----------------

    private fun wid(id: String, name: String, teacher: String, room: String, day: Int,
                    sp: Int, ep: Int, ws: Int = 1, we: Int = 16): CourseEntry =
        CourseEntry(
            id = id, name = name, teacher = teacher, room = room,
            day = day, startPeriod = sp, endPeriod = ep, weeks = WeekRange(ws, we),
        )

    private fun mockCourses(): List<CourseEntry> = listOf(
        wid("c1", "高等数学", "李明", "4-A101", 1, 1, 2),
        wid("c2", "大学英语", "Sarah", "3-B204", 1, 3, 4),
        wid("c3", "数据结构", "王强", "5-C301", 1, 5, 6),
        wid("c4", "线性代数", "赵敏", "4-A102", 2, 1, 2),
        wid("c5", "程序设计", "陈伟", "机房-2", 2, 3, 4),
        wid("c6", "体育", "刘勇", "东操场", 2, 7, 8),
        wid("c7", "离散数学", "孙丽", "4-A203", 3, 1, 2),
        wid("c8", "计算机网络", "周涛", "5-C202", 3, 3, 4),
        wid("c9", "马克思主义基本原理", "吴刚", "1-报告厅", 3, 5, 6),
        wid("c10", "数据结构", "王强", "机房-3", 3, 9, 10),
        wid("c11", "操作系统", "郑华", "5-C105", 4, 1, 2),
        wid("c12", "大学物理", "冯磊", "2-物理实验室", 4, 3, 4),
        wid("c13", "英语听说", "Sarah", "3-语音室", 4, 5, 6),
        wid("c14", "概率论", "何静", "4-A301", 5, 1, 2),
        wid("c15", "软件工程", "许峰", "5-C401", 5, 3, 4),
        // 第 5 周开始的短课，用于验证 weeks 过滤
        wid("c16", "创新创业", "钱老师", "1-报告厅", 5, 5, 6, ws = 5, we = 12),
    )

    private fun mockSchedules(): List<ScheduleEntry> = listOf(
        ScheduleEntry(
            id = "s1", title = "小组讨论", content = "数据结构课设分工",
            location = "图书馆研讨间", day = 2, startPeriod = 9, endPeriod = 10,
            weeksList = listOf(6, 7, 8), colorIndex = 2,
            createdAt = "2026-01-01T10:00:00",
        ),
        ScheduleEntry(
            id = "s2", title = "志愿活动", content = "社区服务",
            location = "南门广场", day = 6, startPeriod = 1, endPeriod = 2,
            weeksList = listOf(7), colorIndex = 3,
            createdAt = "2026-01-01T11:00:00",
        ),
    )

    private fun mockAttendances(courses: List<CourseEntry>): List<CourseAttendance> {
        fun a(courseId: String, week: Int, day: Int, status: AttendanceStatus) =
            CourseAttendance(id = attendanceSlotKey(courseId, week, day), courseId = courseId,
                week = week, day = day, status = status)
        return listOf(
            a("c1", 5, 1, AttendanceStatus.Present),
            a("c2", 5, 1, AttendanceStatus.Late),
            a("c3", 4, 1, AttendanceStatus.Present),
            a("c4", 5, 2, AttendanceStatus.Absent),
            a("c5", 5, 2, AttendanceStatus.Present),
            a("c7", 3, 3, AttendanceStatus.Leave),
            a("c8", 5, 3, AttendanceStatus.Present),
            a("c9", 2, 3, AttendanceStatus.NoCheck),
            a("c11", 4, 4, AttendanceStatus.Present),
            a("c12", 5, 4, AttendanceStatus.Late),
        )
    }
}
