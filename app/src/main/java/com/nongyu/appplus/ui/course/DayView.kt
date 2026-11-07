package com.nongyu.appplus.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.nongyu.appplus.data.course.WEEKDAY_LABELS
import com.nongyu.appplus.data.course.WeekGridData
import com.nongyu.appplus.data.course.getCourseColor
import com.nongyu.appplus.data.course.getPaletteColor
import com.nongyu.appplus.data.course.periodEnd
import com.nongyu.appplus.data.course.periodStart
import com.nongyu.appplus.data.course.stackCourse
import com.nongyu.appplus.data.course.stackSchedules
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 日视图：星期切换 + 当天 10 节卡片列表。
 * 与周视图共用同一份 [WeekGridData]，卡片风格统一（左侧色条 + 圆角）。
 */
@Composable
fun DayView(
    grid: WeekGridData,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    attendanceBySlot: Map<String, AttendanceStatus>,
    week: Int,
    dateLabels: List<String>,
    todayIndex: Int,
    uiScale: Float,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntry) -> Unit,
    onScheduleClick: (ScheduleEntry) -> Unit,
    onEmptyCellClick: (day: Int, startPeriod: Int) -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val colors = NongyuTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        // 星期切换条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(s(3.dp)),
        ) {
            WEEKDAY_LABELS.forEachIndexed { index, label ->
                val selected = index == selectedDay
                val isToday = index == todayIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(s(10.dp)))
                        .background(
                            when {
                                selected -> colors.brand.colorBrand
                                isToday -> colors.brand.colorBrand.copy(alpha = 0.12f)
                                else -> colors.surface.colorCard
                            },
                        )
                        .pointerInput(index) { detectTapGestures(onTap = { onSelectDay(index) }) }
                        .padding(vertical = s(6.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label.removePrefix("周"),
                        fontSize = (12 * uiScale).sp,
                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            selected -> Color.White
                            isToday -> colors.brand.colorBrand
                            else -> colors.surface.colorText
                        },
                    )
                    Text(
                        text = dateLabels.getOrElse(index) { "" },
                        fontSize = (8 * uiScale).sp,
                        color = when {
                            selected -> Color.White.copy(alpha = 0.8f)
                            isToday -> colors.brand.colorBrand.copy(alpha = 0.8f)
                            else -> colors.surface.colorTextSecondary
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(s(10.dp)))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(s(5.dp)),
        ) {
            for (row in 0 until PERIOD_ROW_COUNT) {
                val cell = grid.getOrNull(row)?.getOrNull(selectedDay)
                when (cell) {
                    is GridCell.Stack -> {
                        val course = stackCourse(cell.value)
                        val schedules = stackSchedules(cell.value)
                        DayCard(
                            startPeriod = row + 1,
                            endPeriod = (row + cell.value.spanRows).coerceAtMost(PERIOD_ROW_COUNT),
                            course = course,
                            schedules = schedules,
                            status = course?.let { attendanceBySlot["${it.id}:$week:${selectedDay + 1}"] },
                            uiScale = uiScale,
                            onClick = {
                                if (course != null) onCourseClick(course)
                                else schedules.firstOrNull()?.let(onScheduleClick)
                            },
                        )
                    }
                    else -> {
                        EmptyRow(row + 1, uiScale) { onEmptyCellClick(selectedDay, row + 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    startPeriod: Int,
    endPeriod: Int,
    course: CourseEntry?,
    schedules: List<ScheduleEntry>,
    status: AttendanceStatus?,
    uiScale: Float,
    onClick: () -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val name = course?.name ?: schedules.firstOrNull()?.title ?: return
    val color = if (course != null) {
        getCourseColor(course.name)
    } else {
        getPaletteColor(schedules.firstOrNull()?.colorIndex) ?: getCourseColor(name)
    }
    val accent = Color(color.text)
    val colors = NongyuTheme.colors

    Row(modifier = Modifier.fillMaxWidth()) {
        // 左：节次 + 时间
        Column(
            modifier = Modifier.width(s(48.dp)).padding(top = s(8.dp)),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = if (startPeriod == endPeriod) "$startPeriod" else "$startPeriod-$endPeriod",
                fontSize = (13 * uiScale).sp,
                fontWeight = FontWeight.Medium,
                color = colors.surface.colorText,
            )
            Text(
                text = periodStart(startPeriod),
                fontSize = (8 * uiScale).sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
            )
            Text(
                text = periodEnd(endPeriod),
                fontSize = (8 * uiScale).sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.width(s(7.dp)))

        // 右：卡片（左侧色条与周视图一致）
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(s(11.dp)))
                .background(Color(color.bg))
                .pointerInput(name) { detectTapGestures(onTap = { onClick() }) },
        ) {
            Box(
                modifier = Modifier
                    .width(s(3.dp))
                    .height(s(52.dp))
                    .padding(vertical = s(6.dp))
                    .clip(RoundedCornerShape(s(2.dp)))
                    .background(accent.copy(alpha = 0.85f)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = s(8.dp), end = s(10.dp), top = s(8.dp), bottom = s(8.dp)),
            ) {
                Text(
                    text = name,
                    fontSize = (13 * uiScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(s(2.dp)))
                val details = buildList {
                    (course?.room ?: schedules.firstOrNull()?.location)
                        ?.takeIf { it.isNotBlank() }?.let { add(it) }
                    course?.teacher?.takeIf { it.isNotBlank() }?.let { add(it) }
                    schedules.firstOrNull()?.content?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        fontSize = (10 * uiScale).sp,
                        color = accent.copy(alpha = 0.68f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status != null) {
                    Spacer(Modifier.height(s(4.dp)))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(s(5.dp)))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = s(5.dp), vertical = s(1.dp)),
                    ) {
                        Text(
                            text = status.label,
                            fontSize = (8 * uiScale).sp,
                            color = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRow(period: Int, uiScale: Float, onClick: () -> Unit) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val colors = NongyuTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(s(48.dp)).padding(top = s(6.dp)),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "$period",
                fontSize = (12 * uiScale).sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.6f),
            )
            Text(
                text = periodStart(period),
                fontSize = (8 * uiScale).sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.width(s(7.dp)))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(s(38.dp))
                .clip(RoundedCornerShape(s(11.dp)))
                .background(colors.surface.colorBackground)
                .pointerInput(period) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = s(10.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "空闲 · 点此添加日程",
                fontSize = (9 * uiScale).sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.45f),
            )
        }
    }
}