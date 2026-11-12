package com.nongyu.appplus.shell.dialogs

import androidx.compose.runtime.Composable
import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.ui.LocationEditDialog
import com.nongyu.appplus.timetable.ui.LocationNavDialog
import com.nongyu.appplus.timetable.ui.WeekEditDialog
import com.nongyu.appplus.timetable.ui.weeksLabelOf
import java.time.LocalDate

/**
 * 课程详情卡引出的三个子窗体：地点编辑 / 周次编辑 / 地点导航。
 *
 * 从 `MainShell` 原样搬出，行为未改。搬出的理由：
 *
 * 1. 三段各自都要做同一件事 —— 算出「当前地点」；原先
 *    `locDay ?: locGeneral ?: dlgCourse.room` 在 Shell 里写了两遍，
 *    改回落顺序要同时改两处。
 * 2. 它们只依赖 `course` / 当前地点 / 可上课的天 / 三个开关，
 *    与 Shell 的命中层级、底栏动画完全无关，属于可以安全外移的部分。
 *
 * 地点与周次共用 `CourseCalendar`：都先选「生效天」再改值；
 * 需上课的天由 `classDaysOf(course)` 算出并标主题色（在调用方算好传入）。
 */
@Composable
fun CourseLocationEditDialog(
    course: CourseEntry,
    currentLocation: String,
    uiScale: Float,
    classDays: Set<LocalDate>,
    onConfirm: (newLocation: String, days: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    LocationEditDialog(
        courseName = course.name,
        currentLocation = currentLocation,
        uiScale = uiScale,
        classDays = classDays,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun CourseWeekEditDialog(
    course: CourseEntry,
    uiScale: Float,
    classDays: Set<LocalDate>,
    initialExceptions: Set<LocalDate>,
    onConfirm: (days: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    WeekEditDialog(
        courseName = course.name,
        weekSummary = weeksLabelOf(course),
        uiScale = uiScale,
        classDays = classDays,
        initialExceptions = initialExceptions,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun CourseLocationNavDialog(
    course: CourseEntry,
    location: String,
    uiScale: Float,
    onDismiss: () -> Unit,
) {
    LocationNavDialog(
        courseName = course.name,
        location = location,
        uiScale = uiScale,
        onDismiss = onDismiss,
    )
}

/**
 * 地点显示值的唯一出处：按天覆盖 → 通用覆盖 → 原始 room。
 *
 * 与 `timetable/logic/CourseDetailRules.resolveLocationLabel` 同语义，
 * 这里只是把三个 null 的来源收成一个调用，避免 Shell 里重复写三元链。
 */
fun currentLocationOf(
    locationForDay: String?,
    locationGeneral: String?,
    course: CourseEntry,
): String = locationForDay ?: locationGeneral ?: course.room
