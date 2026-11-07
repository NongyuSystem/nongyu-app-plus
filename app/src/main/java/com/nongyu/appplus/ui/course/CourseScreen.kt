package com.nongyu.appplus.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.data.course.AttendanceStatus
import com.nongyu.appplus.data.course.COURSE_COLOR_PALETTE
import com.nongyu.appplus.data.course.CourseAttendance
import com.nongyu.appplus.data.course.CourseEntry
import com.nongyu.appplus.data.course.CourseRepository
import com.nongyu.appplus.data.course.ScheduleEntry
import com.nongyu.appplus.data.course.buildWeekGridWithSchedules
import com.nongyu.appplus.data.course.countAttendanceByCourseId
import com.nongyu.appplus.data.course.formatAttendanceSummary
import com.nongyu.appplus.data.course.formatFullAttendanceSummary
import com.nongyu.appplus.data.course.countFullAttendanceForCourse
import com.nongyu.appplus.data.course.getCourseColor
import com.nongyu.appplus.data.course.indexAttendanceBySlot
import com.nongyu.appplus.data.course.listAttendancesForCourse
import com.nongyu.appplus.ui.theme.NongyuTheme

/** 视图模式 */
private enum class CourseViewMode { Week, Day }

/**
 * 课表页（周视图 / 日视图切换、周次切换、课程详情、日程增删改、考勤）。
 *
 * 数据全部来自 [CourseRepository]（当前为本地假数据）。
 * 本页与导航壳解耦：只依赖 uiScale 缩放系数与主题色。
 */
@Composable
fun CourseScreen(
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val colors = NongyuTheme.colors
    val state by CourseRepository.state.collectAsState()

    var viewMode by rememberSaveable { mutableStateOf(CourseViewMode.Week) }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }

    // 弹窗状态
    var detailCourse by remember { mutableStateOf<CourseEntry?>(null) }
    var detailSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }
    var creatingAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // 当前周的矩阵（周/日视图共用）
    val week = state.selectedWeek
    val grid = remember(week, state.courses, state.schedules) {
        buildWeekGridWithSchedules(week, state.courses, state.schedules)
    }
    val attendanceBySlot = remember(state.attendances) {
        indexAttendanceBySlot(state.attendances)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = s(10.dp), start = s(12.dp), end = s(12.dp)),
    ) {
        // ---------- 顶部栏：左=周次+切换，右=周/日视图切换 ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：‹  第 N 周(本周)  ›
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(s(12.dp)))
                    .background(colors.surface.colorCard)
                    .padding(horizontal = s(2.dp), vertical = s(2.dp)),
            ) {
                RoundIconButton("‹", enabled = week > 1, uiScale = uiScale) {
                    CourseRepository.selectWeek(week - 1)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(s(64.dp)),
                ) {
                    Text(
                        text = "第 $week 周",
                        fontSize = (13 * uiScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.surface.colorText,
                    )
                    Text(
                        text = if (week == state.currentWeek) "本周" else "共 ${state.maxWeek} 周",
                        fontSize = (8 * uiScale).sp,
                        color = if (week == state.currentWeek) colors.brand.colorBrand
                        else colors.surface.colorTextSecondary,
                    )
                }
                RoundIconButton("›", enabled = week < state.maxWeek, uiScale = uiScale) {
                    CourseRepository.selectWeek(week + 1)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 右：周/日切换
            ViewModeSwitch(
                mode = viewMode,
                uiScale = uiScale,
                onChange = { viewMode = it },
            )
        }

        // 不在本周时给一个轻量入口
        if (week != state.currentWeek) {
            Spacer(modifier = Modifier.height(s(6.dp)))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(s(8.dp)))
                    .background(colors.brand.colorBrand.copy(alpha = 0.10f))
                    .pointerInput(week) {
                        detectTapGestures(onTap = { CourseRepository.backToCurrentWeek() })
                    }
                    .padding(horizontal = s(10.dp), vertical = s(4.dp)),
            ) {
                Text(
                    text = "回到第 ${state.currentWeek} 周",
                    fontSize = (10 * uiScale).sp,
                    color = colors.brand.colorBrand,
                )
            }
            Spacer(modifier = Modifier.height(s(8.dp)))
        }

        // ---------- 课表主体：撑满剩余高度 ----------
        // 用 BoxWithConstraints 拿到可用高度，5 行均分，避免内容挤在上半屏。
        // 注意：这里不再套 verticalScroll —— 内层网格自己撑满即可，
        // 嵌套滚动会触发「无限高度约束」崩溃。
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // 表头约 22dp，其余给 5 行；每行不小于 46dp 以免文字挤在一起
            val headerAndGap = s(26.dp)
            val available = (maxHeight - headerAndGap).coerceAtLeast(s(46.dp * 5))
            val rowH = (available / 5)

            when (viewMode) {
                CourseViewMode.Week -> WeekGrid(
                    grid = grid,
                    attendanceBySlot = attendanceBySlot,
                    week = week,
                    rowHeight = rowH,
                    onCourseClick = { detailCourse = it },
                    onScheduleClick = { detailSchedule = it },
                    onEmptyCellClick = { day, row -> creatingAt = day to row },
                )
                CourseViewMode.Day -> DayView(
                    grid = grid,
                    selectedDay = selectedDay,
                    onSelectDay = { selectedDay = it },
                    attendanceBySlot = attendanceBySlot,
                    week = week,
                    onCourseClick = { detailCourse = it },
                    onScheduleClick = { detailSchedule = it },
                    onEmptyCellClick = { day, row -> creatingAt = day to row },
                )
            }
        }
    }

    // ---------- 弹窗 ----------
    detailCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            week = week,
            attendances = state.attendances,
            uiScale = uiScale,
            onDismiss = { detailCourse = null },
            onSetAttendance = { status ->
                CourseRepository.setAttendance(course.id, week, course.day, status)
            },
        )
    }

    detailSchedule?.let { schedule ->
        ScheduleDetailDialog(
            schedule = schedule,
            uiScale = uiScale,
            onDismiss = { detailSchedule = null },
            onEdit = {
                detailSchedule = null
                editingSchedule = schedule
            },
            onDelete = {
                CourseRepository.deleteSchedule(schedule.id)
                detailSchedule = null
            },
        )
    }

    editingSchedule?.let { schedule ->
        ScheduleFormDialog(
            initial = schedule,
            uiScale = uiScale,
            onDismiss = { editingSchedule = null },
            onConfirm = {
                CourseRepository.updateSchedule(it)
                editingSchedule = null
            },
        )
    }

    creatingAt?.let { (day, row) ->
        // 节次上限 10：第 5 行（9-10 节）不能再算成 11/12
        val sp = (row * 2 + 1).coerceIn(1, 10)
        val ep = (row * 2 + 2).coerceIn(sp, 10)
        ScheduleFormDialog(
            initial = ScheduleEntry(
                id = "s_${System.currentTimeMillis()}",
                title = "",
                day = day + 1,
                startPeriod = sp,
                endPeriod = ep,
                weeksList = listOf(week),
                createdAt = java.time.LocalDateTime.now().toString(),
            ),
            isNew = true,
            uiScale = uiScale,
            onDismiss = { creatingAt = null },
            onConfirm = {
                CourseRepository.addSchedule(it)
                creatingAt = null
            },
        )
    }
}

/** 圆形小按钮（上一周/下一周） */
@Composable
private fun RoundIconButton(label: String, enabled: Boolean, uiScale: Float, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    val size = (26 * uiScale).dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = (15 * uiScale).sp,
            color = if (enabled) colors.surface.colorText else colors.surface.colorTextSecondary.copy(alpha = 0.25f),
        )
    }
}

/** 周/日切换分段控件 */
@Composable
private fun ViewModeSwitch(
    mode: CourseViewMode,
    uiScale: Float,
    onChange: (CourseViewMode) -> Unit,
) {
    val colors = NongyuTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surface.colorCard)
            .border(0.5.dp, colors.surface.colorOutline.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        listOf(CourseViewMode.Week to "周", CourseViewMode.Day to "日").forEach { (m, label) ->
            val selected = mode == m
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) colors.brand.colorBrand else Color.Transparent)
                    .pointerInput(m) { detectTapGestures(onTap = { onChange(m) }) }
                    // 点击区域至少 36x26dp：原来只有 10x3dp padding，太小点不中
                    .padding(horizontal = (16 * uiScale).dp, vertical = (7 * uiScale).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = (11 * uiScale).sp,
                    color = if (selected) Color.White else colors.surface.colorTextSecondary,
                )
            }
        }
    }
}
