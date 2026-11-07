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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.data.course.AttendanceStatus
import com.nongyu.appplus.data.course.CourseEntry
import com.nongyu.appplus.data.course.MAJOR_SLOT_COUNT
import com.nongyu.appplus.data.course.ScheduleEntry
import com.nongyu.appplus.data.course.WEEKDAY_LABELS
import com.nongyu.appplus.data.course.WeekGridData
import com.nongyu.appplus.data.course.getCourseColor
import com.nongyu.appplus.data.course.getPaletteColor
import com.nongyu.appplus.data.course.periodRangeLabel
import com.nongyu.appplus.data.course.periodTimeLabel
import com.nongyu.appplus.data.course.stackCourse
import com.nongyu.appplus.data.course.stackSchedules
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 日视图：按星期横向切换，纵向列出当天 5 个大节。
 * 与周视图共用同一份 [WeekGridData]，不重复计算。
 */
@Composable
fun DayView(
    grid: WeekGridData,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    attendanceBySlot: Map<String, AttendanceStatus>,
    week: Int,
    modifier: Modifier = Modifier,
    onCourseClick: (CourseEntry) -> Unit,
    onScheduleClick: (ScheduleEntry) -> Unit,
    onEmptyCellClick: (day: Int, startPeriod: Int) -> Unit,
) {
    val colors = NongyuTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        // 星期选择条
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAY_LABELS.forEachIndexed { index, label ->
                val selected = index == selectedDay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) colors.brand.colorBrand.copy(alpha = 0.12f)
                            else colors.surface.colorCard,
                        )
                        .border(
                            1.dp,
                            if (selected) colors.brand.colorBrand else colors.surface.colorOutline.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp),
                        )
                        .pointerInput(index) { detectTapGestures(onTap = { onSelectDay(index) }) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.removePrefix("周"),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) colors.brand.colorBrand else colors.surface.colorTextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(MAJOR_SLOT_COUNT) { row ->
                val cell = grid.getOrNull(row)?.getOrNull(selectedDay)
                when (cell) {
                    is com.nongyu.appplus.data.course.GridCell.Stack -> {
                        val course = stackCourse(cell.value)
                        val schedules = stackSchedules(cell.value)
                        DayEntryCard(
                            rowLabel = periodRangeLabel(row),
                            timeLabel = periodTimeLabel(row),
                            course = course,
                            schedules = schedules,
                            status = course?.let { attendanceBySlot["${it.id}:$week:${selectedDay + 1}"] },
                            onClick = {
                                if (course != null) onCourseClick(course)
                                else schedules.firstOrNull()?.let(onScheduleClick)
                            },
                        )
                    }
                    is com.nongyu.appplus.data.course.GridCell.Occupied -> {
                        // 连堂占位：由上一个跨行卡片覆盖，此处留空
                        EmptyDaySlot(periodRangeLabel(row), periodTimeLabel(row)) {
                            onEmptyCellClick(selectedDay, row)
                        }
                    }
                    null -> {
                        EmptyDaySlot(periodRangeLabel(row), periodTimeLabel(row)) {
                            onEmptyCellClick(selectedDay, row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEntryCard(
    rowLabel: String,
    timeLabel: String,
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
            modifier = Modifier.width(56.dp).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(rowLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = colors.surface.colorText)
            Text(timeLabel, fontSize = 7.sp, color = colors.surface.colorTextSecondary.copy(alpha = 0.7f))
        }

        // 右：卡片
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(color.bg))
                .pointerInput(name) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column {
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
                    course?.teacher?.takeIf { it.isNotBlank() }?.let { add(it) }
                    (course?.room ?: schedules.firstOrNull()?.location)
                        ?.takeIf { it.isNotBlank() }?.let { add(it) }
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
}

@Composable
private fun EmptyDaySlot(rowLabel: String, timeLabel: String, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(56.dp).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(rowLabel, fontSize = 10.sp, color = colors.surface.colorTextSecondary)
            Text(timeLabel, fontSize = 7.sp, color = colors.surface.colorTextSecondary.copy(alpha = 0.6f))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface.colorCard)
                .border(0.5.dp, colors.surface.colorOutline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("空闲", fontSize = 10.sp, color = colors.surface.colorTextSecondary.copy(alpha = 0.5f))
        }
    }
}
