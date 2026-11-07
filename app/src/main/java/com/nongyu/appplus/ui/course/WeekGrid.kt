package com.nongyu.appplus.ui.course

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.data.course.AttendanceStatus
import com.nongyu.appplus.data.course.CourseEntry
import com.nongyu.appplus.data.course.GridCell
import com.nongyu.appplus.data.course.PERIOD_ROW_COUNT
import com.nongyu.appplus.data.course.ScheduleEntry
import com.nongyu.appplus.data.course.StackItem
import com.nongyu.appplus.data.course.WEEKDAY_LABELS
import com.nongyu.appplus.data.course.WeekGridData
import com.nongyu.appplus.data.course.getCourseColor
import com.nongyu.appplus.data.course.getPaletteColor
import com.nongyu.appplus.data.course.periodEnd
import com.nongyu.appplus.data.course.periodStart
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 周视图课表网格。
 *
 * 版式要点：
 * - **时间列极窄**：只显示节次数字（1..10），宽 22dp；
 *   起止时间放到左侧悬浮（随行高变化，空间不够时不显示）。
 * - **课程是真卡片**：圆角 9dp、内边距、浅色底深色字、左上角小色条，
 *   不是"整块文字铺在格子里"。
 * - **今日列**整列有极淡底色，表头已在 CourseScreen 处理。
 * - **周日列底部**放「回到本周」矢量按钮（仅当不在本周时）。
 */
@Composable
fun WeekGrid(
    grid: WeekGridData,
    attendanceBySlot: Map<String, AttendanceStatus>,
    week: Int,
    rowHeight: Dp,
    maxWeek: Int,
    dateLabels: List<String>,
    todayIndex: Int,
    uiScale: Float,
    isCurrentWeek: Boolean,
    onBackToToday: () -> Unit,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntry) -> Unit,
    onScheduleClick: (ScheduleEntry) -> Unit,
    onEmptyCellClick: (day: Int, startPeriod: Int) -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val timeColWidth = s(22.dp)

    Row(modifier = modifier.fillMaxWidth()) {
        // ---------- 左侧：节次列 ----------
        Column(modifier = Modifier.width(timeColWidth)) {
            for (row in 0 until PERIOD_ROW_COUNT) {
                Box(
                    modifier = Modifier.height(rowHeight).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // 大号节次数字，顶部对齐（与课程卡起始对齐）
                    Text(
                        text = "${row + 1}",
                        fontSize = (11 * uiScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.surface.colorTextSecondary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = s(2.dp)),
                    )
                }
            }
        }

        // 时间列与网格之间的细分隔线
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(rowHeight * PERIOD_ROW_COUNT)
                .background(colors.surface.colorOutline.copy(alpha = 0.18f)),
        )

        // ---------- 7 天 ----------
        for (day in 0 until 7) {
            val isTodayCol = day == todayIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isTodayCol) Modifier.background(colors.brand.colorBrand.copy(alpha = 0.045f))
                        else Modifier,
                    ),
            ) {
                Column {
                    var row = 0
                    while (row < PERIOD_ROW_COUNT) {
                        val cell = grid.getOrNull(row)?.getOrNull(day)
                        when (cell) {
                            is GridCell.Stack -> {
                                val span = cell.value.spanRows.coerceAtMost(PERIOD_ROW_COUNT - row)
                                CourseCard(
                                    items = cell.value.items,
                                    attendance = attendanceBySlot,
                                    week = week,
                                    dayIndex = day,
                                    height = rowHeight * span,
                                    uiScale = uiScale,
                                    onClickCourse = onCourseClick,
                                    onClickSchedule = onScheduleClick,
                                )
                                row += span
                            }
                            is GridCell.Occupied -> row += 1
                            null -> {
                                EmptySlot(
                                    height = rowHeight,
                                    onClick = { onEmptyCellClick(day, row + 1) },
                                )
                                row += 1
                            }
                        }
                    }
                }

                // 周日列底部：「回到本周」矢量按钮
                if (day == 6 && !isCurrentWeek) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = s(10.dp)),
                    ) {
                        BackToTodayButton(uiScale = uiScale, onClick = onBackToToday)
                    }
                }
            }
        }
    }
}

/** 空格子：极淡的点击区，不做视觉装饰 */
@Composable
private fun EmptySlot(height: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(height)
            .padding(horizontal = 1.dp, vertical = 0.5.dp)
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    )
}

/**
 * 课程卡片。
 *
 * 按反馈简化：
 * - **整张卡一个底色**，不再有左侧独立色条；
 * - 内容**上下居中**（原来顶部对齐显得头重脚轻）；
 * - 圆角 8dp，内边距 4dp，课程名为主、地点教师为次。
 */
@Composable
private fun CourseCard(
    items: List<StackItem>,
    attendance: Map<String, AttendanceStatus>,
    week: Int,
    dayIndex: Int,
    height: Dp,
    uiScale: Float,
    onClickCourse: (CourseEntry) -> Unit,
    onClickSchedule: (ScheduleEntry) -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val course = items.filterIsInstance<StackItem.Course>().firstOrNull()?.course
    val schedules = items.filterIsInstance<StackItem.Schedule>().map { it.schedule }
    val name = course?.name ?: schedules.firstOrNull()?.title ?: return

    val color = if (course != null) {
        getCourseColor(course.name)
    } else {
        getPaletteColor(schedules.firstOrNull()?.colorIndex) ?: getCourseColor(name)
    }
    val accent = Color(color.text)
    val sessionStatus = course?.let { attendance["${it.id}:$week:${dayIndex + 1}"] }

    Box(
        modifier = Modifier
            .height(height)
            .padding(horizontal = s(1.5.dp), vertical = s(1.5.dp))
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(12.dp)))
            .background(Color(color.bg))
            .pointerInput(course?.id, schedules.firstOrNull()?.id) {
                detectTapGestures(
                    onTap = {
                        if (course != null) onClickCourse(course)
                        else schedules.firstOrNull()?.let(onClickSchedule)
                    },
                )
            }
            .padding(horizontal = s(4.dp), vertical = s(3.dp)),
        // 内容整体上下居中
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name,
                fontSize = (9.5f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = if (height > 60.dp) 4 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (11.5f * uiScale).sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            val place = course?.room ?: schedules.firstOrNull()?.location
            if (!place.isNullOrBlank() && height > 46.dp) {
                Spacer(Modifier.height(s(1.dp)))
                Text(
                    text = place,
                    fontSize = (8 * uiScale).sp,
                    color = accent.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (!course?.teacher.isNullOrBlank() && height > 66.dp) {
                Text(
                    text = course.teacher,
                    fontSize = (8 * uiScale).sp,
                    color = accent.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // 同格堆叠数量
        if (items.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(s(4.dp)))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = s(3.dp), vertical = s(0.5.dp)),
            ) {
                Text(
                    text = items.size.toString(),
                    fontSize = (7 * uiScale).sp,
                    color = accent,
                )
            }
        }

        // 本节考勤
        if (sessionStatus != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(s(4.dp)))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = s(3.dp), vertical = s(0.5.dp)),
            ) {
                Text(
                    text = sessionStatus.shortLabel,
                    fontSize = (7 * uiScale).sp,
                    color = accent,
                )
            }
        }
    }
}
