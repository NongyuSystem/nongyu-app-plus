package com.nongyu.appplus.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nongyu.appplus.data.course.periodRangeLabel
import com.nongyu.appplus.data.course.periodStart
import com.nongyu.appplus.data.course.stackCourse
import com.nongyu.appplus.data.course.stackSchedules
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 日视图：顶部星期切换条 + 当天 10 节课的纵向列表。
 * 与周视图共用同一份 [WeekGridData]。
 */
@Composable
fun DayView(
    grid: WeekGridData,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    attendanceBySlot: Map<String, AttendanceStatus>,
    week: Int,
    dateLabels: List<String>,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntry) -> Unit,
    onScheduleClick: (ScheduleEntry) -> Unit,
    onEmptyCellClick: (day: Int, startPeriod: Int) -> Unit,
) {
    val colors = NongyuTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        // 星期切换条
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            WEEKDAY_LABELS.forEachIndexed { index, label ->
                val selected = index == selectedDay
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) colors.brand.colorBrand
                            else colors.surface.colorCard,
                        )
                        .pointerInput(index) { detectTapGestures(onTap = { onSelectDay(index) }) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label.removePrefix("周"),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else colors.surface.colorText,
                    )
                    Text(
                        text = dateLabels.getOrElse(index) { "" },
                        fontSize = 8.sp,
                        color = if (selected) Color.White.copy(alpha = 0.8f)
                        else colors.surface.colorTextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in 0 until PERIOD_ROW_COUNT) {
                val cell = grid.getOrNull(row)?.getOrNull(selectedDay)
                when (cell) {
                    is GridCell.Stack -> {
                        val course = stackCourse(cell.value)
                        val schedules = stackSchedules(cell.value)
                        DayEntryCard(
                            startPeriod = row + 1,
                            endPeriod = (row + cell.value.spanRows).coerceAtMost(PERIOD_ROW_COUNT),
                            course = course,
                            schedules = schedules,
                            status = course?.let { attendanceBySlot["${it.id}:$week:${selectedDay + 1}"] },
                            onClick = {
                                if (course != null) onCourseClick(course)
                                else schedules.firstOrNull()?.let(onScheduleClick)
                            },
                        )
                    }

                    else -> {
                        // Occupied 由上方跨行卡片覆盖，null 直接给"空闲"
                        EmptyDaySlot(row + 1) { onEmptyCellClick(selectedDay, row + 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEntryCard(
    startPeriod: Int,
    endPeriod: Int,
    course: CourseEntry?,
    schedules: List<ScheduleEntry>,
    status: AttendanceStatus?,
    onClick: () -> Unit,
) {
    val name = course?.name ?: schedules.firstOrNull()?.title ?: return
    val color = if (course != null) {
        getCourseColor(course.name)
    } else {
        getPaletteColor(schedules.firstOrNull()?.colorIndex) ?: getCourseColor(name)
    }
    val colors = NongyuTheme.colors

    Row(modifier = Modifier.fillMaxWidth()) {
        // 左：节次 + 时间
        Column(
            modifier = Modifier.width(52.dp).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = periodStart(startPeriod),
                fontSize = 8.sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.85f),
            )
            Text(
                text = if (startPeriod == endPeriod) "$startPeriod"
                else "$startPeriod-$endPeriod",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.surface.colorText,
            )
            Text(
                text = periodEnd(endPeriod),
                fontSize = 8.sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.85f),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))

        // 右：卡片
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(color.bg))
                .pointerInput(name) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(color.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val details = buildList {
                (course?.room ?: schedules.firstOrNull()?.location)
                    ?.takeIf { it.isNotBlank() }?.let { add(it) }
                course?.teacher?.takeIf { it.isNotBlank() }?.let { add(it) }
                schedules.firstOrNull()?.content?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    fontSize = 10.sp,
                    color = Color(color.text).copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本节：${status.label}",
                    fontSize = 9.sp,
                    color = Color(color.text).copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun EmptyDaySlot(period: Int, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(52.dp).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = periodStart(period),
                fontSize = 8.sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.6f),
            )
            Text(
                text = "$period",
                fontSize = 13.sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
            )
            Text(
                text = periodEnd(period),
                fontSize = 8.sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.6f),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface.colorCard)
                .border(0.5.dp, colors.surface.colorOutline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "空闲 · 点此添加日程",
                fontSize = 9.sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.5f),
            )
        }
    }
}
