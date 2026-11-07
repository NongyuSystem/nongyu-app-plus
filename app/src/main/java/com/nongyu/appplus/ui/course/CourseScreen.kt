package com.nongyu.appplus.ui.course

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.data.course.AttendanceStatus
import com.nongyu.appplus.data.course.CourseEntry
import com.nongyu.appplus.data.course.CourseRepository
import com.nongyu.appplus.data.course.PERIOD_ROW_COUNT
import com.nongyu.appplus.data.course.ScheduleEntry
import com.nongyu.appplus.data.course.buildWeekGridWithSchedules
import com.nongyu.appplus.data.course.indexAttendanceBySlot
import com.nongyu.appplus.ui.theme.NongyuTheme
import kotlinx.coroutines.launch

/** 视图模式 */
private enum class CourseViewMode { Week, Day }

/**
 * 课程/日程详情在**底部导航栏位置**要展示的信息。
 * MainShell 收到后会让导航栏原地变形为一排标签胶囊（而不是另开弹窗）。
 */
data class CourseDetailBar(
    val title: String,
    val pills: List<String>,
    val courseId: String? = null,
    val scheduleId: String? = null,
)

/**
 * 课表页。
 *
 * 本轮改造要点（对齐旧版并修版式）：
 * 1. 底部避让 [bottomInset]，避免被导航栏遮盖
 * 2. 左时间列收窄到 30dp
 * 3. 课程卡做成真正的卡片（圆角 + 内边距 + 轻阴影感）
 * 4. 左右滑动切周，去掉前后周按钮
 * 5. 周日列下方放「回本周」矢量按钮
 * 6. 周/日切换、切周都有过渡动画
 * 7. 点「第N周」弹出日历快速跳周
 * 8. 课程详情改为底部抽屉（导航栏变形为标签胶囊的形态）
 * 9. 今日日期在表头加深底色
 */
@Composable
fun CourseScreen(
    uiScale: Float,
    bottomInset: Dp,
    /**
     * 详情状态上报：非 null 时底部导航栏会**变形为标签胶囊**（第 8 项要求），
     * 由 MainShell 绘制，因此这里把「标题 + 胶囊列表」传上去。
     */
    onDetailChange: (CourseDetailBar?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val colors = NongyuTheme.colors
    val state by CourseRepository.state.collectAsState()
    val scope = rememberCoroutineScope()

    var viewMode by rememberSaveable { mutableStateOf(CourseViewMode.Week) }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    var showWeekPicker by remember { mutableStateOf(false) }

    var detailCourse by remember { mutableStateOf<CourseEntry?>(null) }
    var detailSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }
    var creatingAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val week = state.selectedWeek
    val grid = remember(week, state.courses, state.schedules) {
        buildWeekGridWithSchedules(week, state.courses, state.schedules)
    }
    val attendanceBySlot = remember(state.attendances) {
        indexAttendanceBySlot(state.attendances)
    }

    // 本周每天日期与「今天」下标
    val todayWeek = CourseRepository.computeCurrentWeek(CourseRepository.semesterStart)
    val todayDayOfWeek = java.time.LocalDate.now().dayOfWeek.value - 1  // 0=周一

    val weekDates = remember(week) {
        val monday = CourseRepository.semesterStart.plusWeeks((week - 1).toLong())
        (0..6).map { monday.plusDays(it.toLong()) }
    }
    val dateLabels = weekDates.map { "${it.monthValue}/${it.dayOfMonth}" }
    val todayIndex = if (week == todayWeek) todayDayOfWeek else -1

    /** 切周：带动画方向 */
    var slideDir by remember { mutableIntStateOf(1) }
    fun goWeek(delta: Int) {
        val target = (week + delta).coerceIn(1, state.maxWeek)
        if (target != week) {
            slideDir = if (delta > 0) 1 else -1
            CourseRepository.selectWeek(target)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = s(4.dp), start = s(12.dp), end = s(12.dp))
            .padding(bottom = bottomInset),
    ) {
        // ================= 标题栏 =================
        Row(
            modifier = Modifier.fillMaxWidth().height(s(42.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "农屿课表",
                fontSize = (21 * uiScale).sp,
                fontWeight = FontWeight.Bold,
                color = colors.surface.colorText,
            )
            Spacer(modifier = Modifier.weight(1f))

            // 周/日切换（带滑块动画）
            ViewModeSwitch(mode = viewMode, uiScale = uiScale, onChange = { viewMode = it })
        }

        Spacer(modifier = Modifier.height(s(2.dp)))

        // ================= 表头：第N周(可点) + 星期 =================
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 左：第N周 / 共M周（点击弹日历）
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(s(9.dp)))
                    .background(colors.brand.colorBrand.copy(alpha = 0.09f))
                    .pointerInput(state.maxWeek) {
                        detectTapGestures(onTap = { showWeekPicker = true })
                    }
                    .padding(horizontal = s(8.dp), vertical = s(4.dp)),
            ) {
                Text(
                    text = "第${week}周",
                    fontSize = (12 * uiScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.brand.colorBrand,
                )
                Text(
                    text = "共${state.maxWeek}周",
                    fontSize = (8 * uiScale).sp,
                    color = colors.surface.colorTextSecondary,
                )
            }

            Spacer(modifier = Modifier.width(s(6.dp)))

            // 右：周一~周日 + 日期（今天加深）
            // 关键：表头必须与网格用**完全相同的左侧占位**（22dp 节次列 + 0.5dp 分隔线），
            // 否则每天列宽基准不同，表头和格子永远对不齐。
            Row(modifier = Modifier.weight(1f)) {
                // 占位：与 WeekGrid 的节次列等宽
                Spacer(modifier = Modifier.width(s(22.dp) + 0.5.dp))
                dayHeaders(
                    dateLabels = dateLabels,
                    todayIndex = todayIndex,
                    uiScale = uiScale,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(s(4.dp)))

        // ================= 课表主体（左右滑动切周 + 切换动画）=================
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(state.maxWeek) {
                    var accumulated = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                        onDragEnd = {
                            val threshold = 60f
                            if (accumulated < -threshold) goWeek(1)
                            else if (accumulated > threshold) goWeek(-1)
                            accumulated = 0f
                        },
                    )
                },
        ) {
            val rowH = ((maxHeight - s(4.dp)) / PERIOD_ROW_COUNT)
                .coerceAtLeast(s(24.dp))

            AnimatedContent(
                targetState = viewMode to week,
                transitionSpec = {
                    val forward = (targetState.second - initialState.second) >= 0
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(160)),
                        )
                },
                label = "courseSwap",
            ) { (mode, _) ->
                when (mode) {
                    CourseViewMode.Week -> WeekGrid(
                        grid = grid,
                        attendanceBySlot = attendanceBySlot,
                        week = week,
                        rowHeight = rowH,
                        maxWeek = state.maxWeek,
                        dateLabels = dateLabels,
                        todayIndex = todayIndex,
                        uiScale = uiScale,
                        isCurrentWeek = week == todayWeek,
                        onBackToToday = { CourseRepository.backToCurrentWeek() },
                        onCourseClick = { detailCourse = it },
                        onScheduleClick = { detailSchedule = it },
                        onEmptyCellClick = { day, period -> creatingAt = day to period },
                    )
                    CourseViewMode.Day -> DayView(
                        grid = grid,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                        attendanceBySlot = attendanceBySlot,
                        week = week,
                        dateLabels = dateLabels,
                        todayIndex = todayIndex,
                        uiScale = uiScale,
                        onCourseClick = { detailCourse = it },
                        onScheduleClick = { detailSchedule = it },
                        onEmptyCellClick = { day, period -> creatingAt = day to period },
                    )
                }
            }
        }
    }

    // ================= 弹层 =================
    if (showWeekPicker) {
        WeekPickerSheet(
            currentWeek = week,
            maxWeek = state.maxWeek,
            todayWeek = todayWeek,
            uiScale = uiScale,
            onPick = { w ->
                slideDir = if (w >= week) 1 else -1
                CourseRepository.selectWeek(w)
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
    }

    detailCourse?.let { course ->
        // 课程详情**不再弹窗**：上报给 MainShell，让底部导航栏原地变形为标签胶囊。
        androidx.compose.runtime.LaunchedEffect(course, week) {
            onDetailChange(
                CourseDetailBar(
                    title = course.name,
                    pills = buildList {
                        add("📍 ${course.room.ifBlank { "—" }}")
                        add("👤 ${course.teacher.ifBlank { "—" }}")
                        add(
                            "🕘 周${"一二三四五六日"[course.day - 1]} " +
                                "${course.startPeriod}-${course.endPeriod}节 " +
                                "${com.nongyu.appplus.data.course.periodStart(course.startPeriod)}" +
                                "-${com.nongyu.appplus.data.course.periodEnd(course.endPeriod)}",
                        )
                        add("📅 ${weekLabel(course)}")
                    },
                    courseId = course.id,
                ),
            )
        }
    }

    // 详情关闭时清掉上报
    androidx.compose.runtime.LaunchedEffect(detailCourse) {
        if (detailCourse == null) onDetailChange(null)
    }

    detailSchedule?.let { schedule ->
        ScheduleDetailSheet(
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
        ScheduleFormSheet(
            initial = schedule,
            uiScale = uiScale,
            onDismiss = { editingSchedule = null },
            onConfirm = {
                CourseRepository.updateSchedule(it)
                editingSchedule = null
            },
        )
    }

    creatingAt?.let { (day, period) ->
        val sp = period.coerceIn(1, 10)
        val ep = (sp + 1).coerceIn(sp, 10)
        ScheduleFormSheet(
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

/** 周次文案（本文件内用） */
private fun weekLabel(course: CourseEntry): String {
    val list = course.weeksList
    if (!list.isNullOrEmpty()) return list.sorted().joinToString(",") + "周"
    val base = "${course.weeks.start}-${course.weeks.end}周"
    return when {
        course.odd -> "$base（单）"
        course.even -> "$base（双）"
        else -> base
    }
}

/** 周一到周日表头：日期，今天加深底色 */
@Composable
private fun dayHeaders(
    dateLabels: List<String>,
    todayIndex: Int,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    Row(modifier = modifier) {
        com.nongyu.appplus.data.course.WEEKDAY_LABELS.forEachIndexed { i, label ->
            val isToday = i == todayIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape((7 * uiScale).dp))
                        .background(
                            if (isToday) colors.brand.colorBrand.copy(alpha = 0.14f)
                            else Color.Transparent,
                        )
                        .padding(horizontal = (5 * uiScale).dp, vertical = (2 * uiScale).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label.removePrefix("周"),
                            fontSize = (10 * uiScale).sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) colors.brand.colorBrand
                            else colors.surface.colorTextSecondary,
                        )
                        Text(
                            text = dateLabels.getOrElse(i) { "" },
                            fontSize = (8 * uiScale).sp,
                            color = if (isToday) colors.brand.colorBrand
                            else colors.surface.colorTextSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/** 周/日切换：带滑块动画的分段控件 */
@Composable
private fun ViewModeSwitch(
    mode: CourseViewMode,
    uiScale: Float,
    onChange: (CourseViewMode) -> Unit,
) {
    val colors = NongyuTheme.colors
    val isDay = mode == CourseViewMode.Day
    val bgAlpha by animateFloatAsState(
        targetValue = if (isDay) 1f else 0f,
        animationSpec = tween(220),
        label = "modeBg",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape((10 * uiScale).dp))
            .background(colors.brand.colorBrand.copy(alpha = 0.10f))
            .padding((2 * uiScale).dp),
    ) {
        listOf(CourseViewMode.Week to "周", CourseViewMode.Day to "日").forEach { (m, label) ->
            val selected = mode == m
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape((8 * uiScale).dp))
                    .background(
                        if (selected) colors.brand.colorBrand else Color.Transparent,
                    )
                    .pointerInput(m) { detectTapGestures(onTap = { onChange(m) }) }
                    .padding(horizontal = (14 * uiScale).dp, vertical = (5 * uiScale).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = (11 * uiScale).sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) Color.White else colors.surface.colorTextSecondary,
                )
            }
        }
    }
}

/** 周日列下方的「回到本周」矢量按钮 */
@Composable
fun BackToTodayButton(
    uiScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    val size = (22 * uiScale).dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.brand.colorBrand)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        // 矢量「回到今天」图标：圆环 + 中心点
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size * 0.58f)) {
            val r = size.toPx() * 0.58f / 2f
            val sw = r * 0.30f
            drawCircle(
                color = Color.White,
                radius = r * 0.72f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw),
            )
            drawCircle(color = Color.White, radius = r * 0.26f)
        }
    }
}
