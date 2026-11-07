package com.nongyu.appplus.data.course

/**
 * 课表配色与考勤汇总 —— 移植自旧项目
 * `model/colors.ts` 与 `model/attendanceSummary.ts`。
 */

/** 课表卡片颜色池（与旧版 COURSE_COLOR_PALETTE 完全一致） */
val COURSE_COLOR_PALETTE: List<CourseColor> = listOf(
    CourseColor(bg = 0xFFE8F1FF, text = 0xFF2B5797),
    CourseColor(bg = 0xFFFDF2F4, text = 0xFFB8325E),
    CourseColor(bg = 0xFFE6F7F3, text = 0xFF00856F),
    CourseColor(bg = 0xFFFFF7E6, text = 0xFFB36B00),
    CourseColor(bg = 0xFFF2F0FA, text = 0xFF5B4FA2),
    CourseColor(bg = 0xFFEBF9F9, text = 0xFF007C89),
    CourseColor(bg = 0xFFFFF9E6, text = 0xFF997B00),
    CourseColor(bg = 0xFFF0F4F8, text = 0xFF3E5463),
)

/** 合法色板下标 → 颜色；非法 / null → null（日程默认样式） */
fun getPaletteColor(index: Int?): CourseColor? {
    if (index == null) return null
    return COURSE_COLOR_PALETTE.getOrNull(index)
}

/** 课程名 → 稳定颜色（同名同色，与旧版哈希算法一致） */
fun getCourseColor(name: String): CourseColor {
    var hash = 0
    for (ch in name) {
        hash = ch.code + ((hash shl 5) - hash)
    }
    val index = kotlin.math.abs(hash) % COURSE_COLOR_PALETTE.size
    return COURSE_COLOR_PALETTE[index]
}

// ---------------- 考勤汇总 ----------------

/** 全学期迟到/缺勤/请假/未考勤次数（不含签到） */
data class AttendanceCounts(
    var late: Int = 0,
    var absent: Int = 0,
    var leave: Int = 0,
    var noCheck: Int = 0,
)

/** 详情用：含签到的全量计数 */
data class AttendanceFullCounts(
    var present: Int = 0,
    var late: Int = 0,
    var absent: Int = 0,
    var leave: Int = 0,
    var noCheck: Int = 0,
)

/** 本节考勤索引键：courseId:week:day */
fun attendanceSlotKey(courseId: String, week: Int, day: Int): String = "$courseId:$week:$day"

/** 按 courseId 汇总（不含签到） */
fun countAttendanceByCourseId(
    attendances: List<CourseAttendance>,
): Map<String, AttendanceCounts> {
    val map = mutableMapOf<String, AttendanceCounts>()
    for (a in attendances) {
        if (a.status == AttendanceStatus.Present) continue
        val row = map.getOrPut(a.courseId) { AttendanceCounts() }
        when (a.status) {
            AttendanceStatus.Late -> row.late++
            AttendanceStatus.Absent -> row.absent++
            AttendanceStatus.Leave -> row.leave++
            AttendanceStatus.NoCheck -> row.noCheck++
            AttendanceStatus.Present -> Unit
        }
    }
    return map
}

/** 单门课五态全量计数 */
fun countFullAttendanceForCourse(attendances: List<CourseAttendance>): AttendanceFullCounts {
    val row = AttendanceFullCounts()
    for (a in attendances) {
        when (a.status) {
            AttendanceStatus.Present -> row.present++
            AttendanceStatus.Late -> row.late++
            AttendanceStatus.Absent -> row.absent++
            AttendanceStatus.Leave -> row.leave++
            AttendanceStatus.NoCheck -> row.noCheck++
        }
    }
    return row
}

/** 紧凑学期汇总，如「缺2·迟1·未1」；全零返回 null */
fun formatAttendanceSummary(counts: AttendanceCounts?): String? {
    if (counts == null) return null
    val parts = buildList {
        if (counts.absent > 0) add("缺${counts.absent}")
        if (counts.late > 0) add("迟${counts.late}")
        if (counts.leave > 0) add("假${counts.leave}")
        if (counts.noCheck > 0) add("未${counts.noCheck}")
    }
    return if (parts.isEmpty()) null else parts.joinToString("·")
}

/** 详情全量汇总，如「签到 3 · 迟到 1 · 未考勤 2」 */
fun formatFullAttendanceSummary(counts: AttendanceFullCounts?): String? {
    if (counts == null) return null
    val parts = buildList {
        if (counts.present > 0) add("签到 ${counts.present}")
        if (counts.absent > 0) add("缺勤 ${counts.absent}")
        if (counts.late > 0) add("迟到 ${counts.late}")
        if (counts.leave > 0) add("请假 ${counts.leave}")
        if (counts.noCheck > 0) add("未考勤 ${counts.noCheck}")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** courseId:week:day → status */
fun indexAttendanceBySlot(
    attendances: List<CourseAttendance>,
): Map<String, AttendanceStatus> {
    val map = mutableMapOf<String, AttendanceStatus>()
    for (a in attendances) {
        map[attendanceSlotKey(a.courseId, a.week, a.day)] = a.status
    }
    return map
}

/** 某课全学期记录，按周、日升序 */
fun listAttendancesForCourse(
    attendances: List<CourseAttendance>,
    courseId: String,
): List<CourseAttendance> =
    attendances.filter { it.courseId == courseId }
        .sortedWith(compareBy({ it.week }, { it.day }))
