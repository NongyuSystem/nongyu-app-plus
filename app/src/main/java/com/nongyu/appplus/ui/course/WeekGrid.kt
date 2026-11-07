package com.nongyu.appplus.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nongyu.appplus.data.course.MAJOR_SLOT_COUNT
import com.nongyu.appplus.data.course.ScheduleEntry
import com.nongyu.appplus.data.course.StackItem
import com.nongyu.appplus.data.course.WEEKDAY_LABELS
import com.nongyu.appplus.data.course.WeekGridData
import com.nongyu.appplus.data.course.getCourseColor
import com.nongyu.appplus.data.course.getPaletteColor
import com.nongyu.appplus.data.course.periodRangeLabel
import com.nongyu.appplus.data.course.periodTimeLabel
import com.nongyu.appplus.data.course.stackCourse
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 周视图课表网格：左侧时间轴 + 7 天 × 5 大节。
 *
 * 渲染约定（与旧项目 WeekGrid 一致）：
 * - 连堂课跨行显示（stack 的 spanRows 决定高度）；
 * - 同格多课显示堆叠指示条；
 * - 有考勤记录的课在卡片右下角显示本节状态（签/迟/缺/假/未）。
 */
@Composable
fun WeekGrid(
    grid: WeekGridData,
    attendanceBySlot: Map<String, AttendanceStatus>,
    week: Int,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntry) -> Unit,
    onScheduleClick: (ScheduleEntry) -> Unit,
    onEmptyCellClick: (day: Int, startPeriod: Int) -> Unit,
) {
    val colors = NongyuTheme.colors
    val timeColWidth = 46.dp

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- 表头：周一到周日 + 日期 ----
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(timeColWidth), contentAlignment = Alignment.Center) {
                Text("周", fontSize = 10.sp, color = colors.surface.colorTextSecondary)
            }
            WEEKDAY_LABELS.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.removePrefix("周"),
                        fontSize = 11.sp,
                        color = colors.surface.colorTextSecondary,
                    )
                }
            }
        }

        Box(modifier = Modifier.height(4.dp))

        // ---- 网格主体 ----
        Row(modifier = Modifier.fillMaxWidth()) {
            // 时间轴
            Column(modifier = Modifier.width(timeColWidth)) {
                repeat(MAJOR_SLOT_COUNT) { row ->
                    Box(
                        modifier = Modifier.height(rowHeight).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = periodRangeLabel(row),
                                fontSize = 9.sp,
                                color = colors.surface.colorText,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = periodTimeLabel(row),
                                fontSize = 7.sp,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // 7 天
            for (day in 0 until 7) {
                Column(modifier = Modifier.weight(1f)) {
                    var row = 0
                    while (row < MAJOR_SLOT_COUNT) {
                        val cell = grid.getOrNull(row)?.getOrNull(day)
                        when (cell) {
                            is GridCell.Stack -> {
                                val span = cell.value.spanRows.coerceAtMost(MAJOR_SLOT_COUNT - row)
                                CourseCell(
                                    items = cell.value.items,
                                    attendance = attendanceBySlot,
                                    week = week,
                                    dayIndex = day,
                                    height = rowHeight * span,
                                    onClickCourse = onCourseClick,
                                    onClickSchedule = onScheduleClick,
                                )
                                row += span
                            }
                            is GridCell.Occupied -> {
                                // 连堂占位行：已被上方跨行块覆盖，跳过
                                row += 1
                            }
                            null -> {
                                EmptyCell(
                                    height = rowHeight,
                                    onClick = { onEmptyCellClick(day, row) },
                                )
                                row += 1
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 空白格：可点按（预留给"新建日程"） */
@Composable
private fun EmptyCell(height: Dp, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .height(height)
            .padding(1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface.colorCard)
            .border(0.5.dp, colors.surface.colorOutline.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    )
}

/** 课程 / 日程卡片（可能同格堆叠多个） */
@Composable
private fun CourseCell(
    items: List<StackItem>,
    attendance: Map<String, AttendanceStatus>,
    week: Int,
    dayIndex: Int,
    height: Dp,
    onClickCourse: (CourseEntry) -> Unit,
    onClickSchedule: (ScheduleEntry) -> Unit,
) {
    val course = items.filterIsInstance<StackItem.Course>().firstOrNull()?.course
    val schedules = items.filterIsInstance<StackItem.Schedule>().map { it.schedule }

    val primaryName = course?.name ?: schedules.firstOrNull()?.title ?: ""
    val color = if (course != null) {
        getCourseColor(course.name)
    } else {
        getPaletteColor(schedules.firstOrNull()?.colorIndex)
            ?: getCourseColor(primaryName)
    }

    val sessionStatus = course?.let {
        attendance["${it.id}:$week:${dayIndex + 1}"]
    }

    Box(
        modifier = Modifier
            .height(height)
            .padding(1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(color.bg))
            .pointerInput(course?.id, schedules.firstOrNull()?.id) {
                detectTapGestures(
                    onTap = {
                        if (course != null) onClickCourse(course)
                        else schedules.firstOrNull()?.let(onClickSchedule)
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = primaryName,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = Color(color.text),
                maxLines = if (height > 60.dp) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp,
            )
            val subtitle = course?.room ?: schedules.firstOrNull()?.location
            if (!subtitle.isNullOrBlank() && height > 52.dp) {
                Text(
                    text = subtitle,
                    fontSize = 7.sp,
                    color = Color(color.text).copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 同格堆叠指示：右上角小圆点带数量
        if (items.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(color.text).copy(alpha = 0.18f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = items.size.toString(),
                    fontSize = 7.sp,
                    color = Color(color.text),
                )
            }
        }

        // 本节考勤状态（右下角）
        if (sessionStatus != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(color.text).copy(alpha = 0.20f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = sessionStatus.shortLabel,
                    fontSize = 7.sp,
                    color = Color(color.text),
                )
            }
        }
    }
}
