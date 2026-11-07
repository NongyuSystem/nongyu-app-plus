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
 * 周视图课表 —— 版式对齐旧版 `农屿课表`：
 *
 * ```
 *  第9周   周一   周三   周四   周五   周六   周日
 *  共16周  11/2   11/4   11/5   11/6   11/7   11/8
 *  08:10 |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *    1   |  [课程卡]        [课程卡]
 *  08:55 |
 *  09:05 |
 *    2   |
 *  09:50 |
 *        ┊  ← 大节之间留空隙
 *  10:10 |
 *    3   |
 * ```
 * 特点：**10 行，每节一行**；左侧是「开始时间 / 大号节次 / 结束时间」三行文字。
 */
@Composable
fun WeekGrid(
    grid: WeekGridData,
    attendanceBySlot: Map<String, AttendanceStatus>,
    week: Int,
    rowHeight: Dp,
    maxWeek: Int,
    dateLabels: List<String>,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntry) -> Unit,
    onScheduleClick: (ScheduleEntry) -> Unit,
    onEmptyCellClick: (day: Int, startPeriod: Int) -> Unit,
) {
    val colors = NongyuTheme.colors
    val timeColWidth = 44.dp

    Column(modifier = modifier.fillMaxWidth()) {
        // ---------- 表头：第N周/共M周 + 星期 + 日期 ----------
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左上角两周文字
            Column(
                modifier = Modifier.width(timeColWidth + 8.dp).padding(start = 2.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "第${week}周",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.surface.colorText,
                )
                Text(
                    text = "共${maxWeek}周",
                    fontSize = 9.sp,
                    color = colors.surface.colorTextSecondary,
                )
            }

            WEEKDAY_LABELS.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.surface.colorText,
                    )
                    Text(
                        text = dateLabels.getOrElse(index) { "" },
                        fontSize = 9.sp,
                        color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
                    )
                }
            }
        }

        Box(modifier = Modifier.height(6.dp))

        // ---------- 网格主体 ----------
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧时间轴：每行显示 开始时间 / 大号节次 / 结束时间
            Column(modifier = Modifier.width(timeColWidth + 8.dp)) {
                for (row in 0 until PERIOD_ROW_COUNT) {
                    Box(
                        modifier = Modifier.height(rowHeight).fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                text = periodStart(row + 1),
                                fontSize = 8.sp,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.85f),
                            )
                            Text(
                                text = "${row + 1}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.surface.colorText,
                            )
                            Text(
                                text = periodEnd(row + 1),
                                fontSize = 8.sp,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            // 7 天
            for (day in 0 until 7) {
                Column(modifier = Modifier.weight(1f)) {
                    var row = 0
                    while (row < PERIOD_ROW_COUNT) {
                        val cell = grid.getOrNull(row)?.getOrNull(day)
                        when (cell) {
                            is GridCell.Stack -> {
                                val span = cell.value.spanRows.coerceAtMost(PERIOD_ROW_COUNT - row)
                                CourseBlock(
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
                                // 被上方连堂课覆盖
                                row += 1
                            }

                            null -> {
                                EmptyPeriod(
                                    height = rowHeight,
                                    onClick = { onEmptyCellClick(day, row + 1) },
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

/** 空节：只有一条极淡的底色，不干扰视觉 */
@Composable
private fun EmptyPeriod(height: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(height)
            .padding(horizontal = 1.5.dp, vertical = 0.5.dp)
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    )
}

/**
 * 课程 / 日程卡片。对齐旧版：纵向排列「课程名 / 地点 / 教师」，
 * 圆角较大、无边框、使用浅色底 + 深色字。
 */
@Composable
private fun CourseBlock(
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
        getPaletteColor(schedules.firstOrNull()?.colorIndex) ?: getCourseColor(primaryName)
    }

    val sessionStatus = course?.let { attendance["${it.id}:$week:${dayIndex + 1}"] }

    Box(
        modifier = Modifier
            .height(height)
            .padding(horizontal = 1.5.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(color.bg))
            .pointerInput(course?.id, schedules.firstOrNull()?.id) {
                detectTapGestures(
                    onTap = {
                        if (course != null) onClickCourse(course)
                        else schedules.firstOrNull()?.let(onClickSchedule)
                    },
                )
            }
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = primaryName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(color.text),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp,
            )
            // 地点
            val place = course?.room ?: schedules.firstOrNull()?.location
            if (!place.isNullOrBlank() && height > 52.dp) {
                Text(
                    text = place,
                    fontSize = 8.sp,
                    color = Color(color.text).copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 教师
            if (!course?.teacher.isNullOrBlank() && height > 64.dp) {
                Text(
                    text = course!!.teacher,
                    fontSize = 8.sp,
                    color = Color(color.text).copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 同格堆叠
        if (items.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(color.text).copy(alpha = 0.16f))
                    .padding(horizontal = 2.dp, vertical = 0.5.dp),
            ) {
                Text(items.size.toString(), fontSize = 7.sp, color = Color(color.text))
            }
        }

        // 本节考勤
        if (sessionStatus != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(color.text).copy(alpha = 0.18f))
                    .padding(horizontal = 2.dp, vertical = 0.5.dp),
            ) {
                Text(sessionStatus.shortLabel, fontSize = 7.sp, color = Color(color.text))
            }
        }
    }
}
