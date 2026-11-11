package com.nongyu.appplus.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme
import java.time.LocalDate

/**
 * 课表页容器（票 #15 建、#18 补齐交互）。
 *
 * 职责：算 `uiScale`、让出底部导航栏占位、承载切周/切天/滑动/添加日程这些交互状态。
 *
 * ## 底部避让为什么用「让出高度」而不是「按顶边坐标换算」
 *
 * 底部导航栏是自绘的（`MainShell.kt`，非 Scaffold）。一开始的做法是让 Shell 上报
 * 底栏**顶边 y 坐标**，再由本页算可用高度。这条路线**连续踩了两次坑**：
 * 1. 用 `positionInRoot` 取到的是 padding 之内的内容区，比真实顶边低了内边距，
 *    少让出空间 → 最后一行被导航栏压住（维护者在真机上看到的正是这个）；
 * 2. 改用 `boundsInRoot` 后含了 padding，但换算时又叠加了状态栏高度，
 *    变成多让出 → 底部空出一大块。
 *
 * 根因是「顶边坐标 + padding + 内容偏移」三者在不同坐标系里叠加，很容易错一次。
 * 现在改为让 Shell 直接上报**底栏节点自身的高度**（[navOccupiedPx]），
 * 本页只需让出这个高度，**不做任何坐标换算**，从结构上消除这类错误。
 *
 * @param navOccupiedPx 底栏占位高度（像素）。`null` = 首帧尚未测量完，
 *   此时先按「无底栏」铺满，下一帧马上修正（不会闪出明显错误，因为通常在首帧内测完）。
 */
@Composable
fun TimetableScreen(
    navOccupiedPx: Float?,
    modifier: Modifier = Modifier,
    /** 已删除的课程 id 集合：贯穿切周链路过滤，避免切周后「复活」。 */
    deletedIds: Set<String> = emptySet(),
    onCourseClick: (CourseEntry) -> Unit = {},
    /** 用户新增的日程（状态提升到 Shell，本页只渲染）。 */
    schedules: List<ScheduleEntry> = emptyList(),
    /** 点空白格：回调 (星期索引 0..6, 节次 1..10, 当前周)，由 Shell 弹添加日程表单。 */
    onBlankSlotAdd: (day: Int, period: Int, week: Int) -> Unit = { _, _, _ -> },
    /** 工具条：设置（占位）。 */
    onSettings: () -> Unit = {},
    /** 工具条：更新课表（占位）。 */
    onUpdateSchedule: () -> Unit = {},
    /** 工具条：添加日程（不带格子位置）。 */
    onAddScheduleFromToolbar: (week: Int, day: Int) -> Unit = { _, _ -> },
    /** 点日程卡 / 下拉横杠：开或关该日程的展开态。 */
    onScheduleClick: (ScheduleEntry) -> Unit = {},
) {
    val colors = NongyuTheme.colors
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground),
    ) {
        val uiScale = (maxWidth.value / 411f).coerceIn(0.85f, 1.25f)
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val breathPx = with(density) { (BOTTOM_BREATH * uiScale).toPx() }
        val gridHeight: Dp = if (navOccupiedPx != null && navOccupiedPx > 0f) {
            with(density) {
                (constraints.maxHeight.toFloat() - statusBarPadding.toPx() - navOccupiedPx - breathPx)
                    .coerceAtLeast(0f)
                    .toDp()
            }
        } else {
            (maxHeight - statusBarPadding).coerceAtLeast(0.dp)
        }

        // ---- 切周 / 切天 状态（#18）----
        val today = remember { LocalDate.now() }
        val thisWeek = remember(today) { weekOfTerm(today) }
        var currentWeek by remember { mutableStateOf(thisWeek) }
        // true = 按天模式（一天占满宽）
        var dayMode by remember { mutableStateOf(false) }
        var currentDay by remember { mutableStateOf(today.dayOfWeek.value - 1) }

        val weekCourses = remember(currentWeek, deletedIds) {
            MOCK_COURSES
                .filter { it.id !in deletedIds }
                .coursesInWeek(currentWeek)
                .mergeAdjacent()
        }

        // ---- 左右滑动切周/切天（阈值 60px）----
        var dragAccum by remember { mutableStateOf(0f) }
        val swipeThresholdPx = with(density) { 60.dp.toPx() }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = statusBarPadding)
                .height(gridHeight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dayMode) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onDragEnd = {
                                when {
                                    dragAccum <= -swipeThresholdPx -> {
                                        // 左滑：下一周 / 下一天，夹在合法范围内
                                        if (dayMode) currentDay = (currentDay + 1) % 7
                                        else currentWeek = (currentWeek + 1).coerceAtMost(TOTAL_WEEKS)
                                    }
                                    dragAccum >= swipeThresholdPx -> {
                                        if (dayMode) currentDay = (currentDay + 6) % 7
                                        else currentWeek = (currentWeek - 1).coerceAtLeast(1)
                                    }
                                }
                                dragAccum = 0f
                            },
                            onHorizontalDrag = { _, dx -> dragAccum += dx },
                        )
                    },
            ) {
                // 切周/切天过渡：**交叉淡入淡出**为主，只保留很轻的方向位移。
                //
                // 之前是整页 220ms 平移 + 各自淡出，观感生硬：旧内容整块滑走、
                // 新内容整块滑入，像翻幻灯片。维护者要求「增添/去除的课要有淡入淡出」。
                // 现在：
                // - 淡入 260ms 比淡出 180ms 长，新旧内容在中间有交叠，看起来是「溶解」而非「切换」
                // - 位移只用屏宽的 6%（0.06f），只做方向暗示，不抢戏
                // - 淡出用线性起步、淡入用 tween 缓动，避免同时消失造成的闪空
                AnimatedContent(
                    targetState = currentWeek to (if (dayMode) currentDay else -1),
                    transitionSpec = {
                        val forward = targetState.first > initialState.first ||
                            (targetState.first == initialState.first && targetState.second > initialState.second)
                        val dir = if (forward) 1 else -1
                        (
                            fadeIn(tween(260, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                                slideInHorizontally(tween(260)) { full -> dir * (full * 0.06f).toInt() }
                            ).togetherWith(
                            fadeOut(tween(180)) +
                                slideOutHorizontally(tween(180)) { full -> -dir * (full * 0.06f).toInt() },
                            )
                    },
                    label = "weekPage",
                ) { (week, day) ->
                    TimetableGrid(
                        uiScale = uiScale,
                        currentWeek = week,
                        courses = weekCourses,
                        // 按天模式只渲染那一天；-1 表示按周（七天全显）
                        onlyDay = if (day >= 0) day else null,
                        schedules = schedules,
                        onCourseClick = onCourseClick,
                        onBlankSlotClick = { d, period ->
                            // 点空白：记下 (星期, 节次)，由 Shell 弹添加日程子窗体
                            onBlankSlotAdd(d, period, week)
                        },
                        onScheduleClick = onScheduleClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // ---- 顶部工具条：设置 / 更新课表 / 切换日周 / 添加日程（票 #14）----
            // 占据 TimetableGrid 里 TOOLBAR_RESERVE_HEIGHT 预留的空位。
            // 周/天 切换**只保留这一个入口**（原来的顶部胶囊已删除，避免两个控件表达同一件事）。
            TimetableToolbar(
                uiScale = uiScale,
                dayMode = dayMode,
                onSettings = onSettings,
                onUpdateSchedule = onUpdateSchedule,
                onToggleDayWeek = { dayMode = !dayMode },
                onAddSchedule = {
                    // 不带格子位置：由 Shell 弹「添加日程」表单（默认当前周的今天第 1 节）
                    onAddScheduleFromToolbar(currentWeek, today.dayOfWeek.value - 1)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding),
            )

            // ---- 非本周/非今日时的「回到本周」按钮（#16 决策：右侧贴边半圆）----
            val showBack = if (dayMode) {
                currentWeek != thisWeek || currentDay != (today.dayOfWeek.value - 1)
            } else {
                currentWeek != thisWeek
            }
            if (showBack) {
                // 必须在外层 Box 上做 align：Modifier.align 是 BoxScope 的扩展，
                // 传进自定义 Composable 内部再应用会失效（实测按钮完全不显示）。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = s(uiScale, 96.dp)),
                ) {
                    BackToWeekFab(
                        uiScale = uiScale,
                        onClick = {
                            currentWeek = thisWeek
                            currentDay = today.dayOfWeek.value - 1
                        },
                    )
                }
            }
        }
    }
}

/** 60dp 阈值等常量用 uiScale 缩放后的 Dp。 */
private fun s(uiScale: Float, dp: Dp): Dp = (dp.value * uiScale).dp

/**
 * 「回到本周/今天」按钮（票 #16 决策实现）：
 * 右侧贴边半圆（right:0、48×40、仅左侧圆角 20）、自绘箭头、淡入 160ms。
 *
 * 淡入用 graphicsLayer.alpha 由 0 动画到 1，**不用内嵌 AnimatedVisibility**：
 * 该组件本身就是「条件为真才组合」的，内嵌 visible 初值 false 再置 true
 * 在父级同帧重组时可能不触发（实测按钮不出现）。
 */
@Composable
private fun BackToWeekFab(
    uiScale: Float,
    onClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    // 用 animateFloatAsState 而不是 Animatable + LaunchedEffect：
    // 后者在父级频繁重组时可能被反复重启，导致透明度一直停在 0（实测按钮画出来了但全透明）。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "backFabAlpha",
    )
    Box(
        modifier = Modifier
            .width((48f * uiScale).dp)
            .height((40f * uiScale).dp)
            // 淡入用 graphicsLayer 且**放在 background 之前**：
            // Modifier.alpha 只影响它之后绘制的内容，若写在 background 之后
            // 会连底色一起透明；若 alpha 值恰为 0 则整个按钮看不见（实测踩到）。
            .graphicsLayer { this.alpha = alpha }
            // 只裁左侧圆角：贴右边，右边缘与屏幕齐平
            .clip(
                RoundedCornerShape(
                    topStart = (20f * uiScale).dp,
                    bottomStart = (20f * uiScale).dp,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp,
                ),
            )
            .background(colors.brand.colorBrand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size((18f * uiScale).dp),
        ) {
            // 自绘箭头（向上）：零依赖，不引图标库
            val w = size.width
            val h = size.height
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (2.2f * uiScale).dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, h * 0.82f)
                lineTo(w * 0.5f, h * 0.20f)
                moveTo(w * 0.20f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.18f)
                lineTo(w * 0.80f, h * 0.48f)
            }
            drawPath(path, color = colors.brand.colorOnBrand, style = stroke)
        }
    }
}

/**
 * 表格最底部与导航栏之间额外留出的呼吸位（视觉留白，不参与避让计算）。
 *
 * 维护者要求「多一点间隙」，由 10dp 提到 20dp（翻倍）。
 * 这个值只影响观感：避让本身由 [navOccupiedPx] 保证，所以调大它不会重新引发遮挡，
 * 只会在窄屏上略微压缩每行高度。
 */
private val BOTTOM_BREATH = 20.dp
