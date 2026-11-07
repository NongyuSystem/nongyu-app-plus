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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nongyu.appplus.data.course.AttendanceStatus
import com.nongyu.appplus.data.course.COURSE_COLOR_PALETTE
import com.nongyu.appplus.data.course.CourseAttendance
import com.nongyu.appplus.data.course.CourseEntry
import com.nongyu.appplus.data.course.ScheduleEntry
import com.nongyu.appplus.data.course.countFullAttendanceForCourse
import com.nongyu.appplus.data.course.formatFullAttendanceSummary
import com.nongyu.appplus.data.course.getCourseColor
import com.nongyu.appplus.data.course.getPaletteColor
import com.nongyu.appplus.data.course.listAttendancesForCourse
import com.nongyu.appplus.ui.theme.NongyuTheme

/** 统一的底部弹窗外壳（居中卡片，遮罩点击关闭） */
@Composable
private fun SheetScaffold(
    uiScale: Float,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = NongyuTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((18 * uiScale).dp))
                .background(colors.surface.colorCard)
                .padding((16 * uiScale).dp),
        ) {
            Column { content() }
        }
    }
}

/** 课程详情：基本信息 + 全学期考勤 + 本节考勤设置 */
@Composable
fun CourseDetailDialog(
    course: CourseEntry,
    week: Int,
    attendances: List<CourseAttendance>,
    uiScale: Float,
    onDismiss: () -> Unit,
    onSetAttendance: (AttendanceStatus?) -> Unit,
) {
    val colors = NongyuTheme.colors
    val color = getCourseColor(course.name)
    val mine = listAttendancesForCourse(attendances, course.id)
    val full = countFullAttendanceForCourse(mine)
    val summary = formatFullAttendanceSummary(full)
    val current = mine.firstOrNull { it.week == week && it.day == course.day }?.status

    SheetScaffold(uiScale = uiScale, onDismiss = onDismiss) {
        // 标题
        Text(
            text = course.name,
            fontSize = (17 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = Color(color.text),
        )
        Spacer(Modifier.height((6 * uiScale).dp))
        InfoRow("教师", course.teacher.ifBlank { "—" }, uiScale)
        InfoRow("地点", course.room.ifBlank { "—" }, uiScale)
        InfoRow(
            "时间",
            "周${"一二三四五六日"[course.day - 1]} 第${course.startPeriod}-${course.endPeriod}节",
            uiScale,
        )
        InfoRow("周次", weekLabel(course), uiScale)

        Spacer(Modifier.height((12 * uiScale).dp))
        Divider(uiScale)
        Spacer(Modifier.height((10 * uiScale).dp))

        Text(
            text = "全学期考勤",
            fontSize = (12 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height((4 * uiScale).dp))
        Text(
            text = summary ?: "暂无记录",
            fontSize = (11 * uiScale).sp,
            color = colors.surface.colorTextSecondary,
        )

        Spacer(Modifier.height((12 * uiScale).dp))
        Text(
            text = "第 $week 周本节考勤",
            fontSize = (12 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height((6 * uiScale).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((5 * uiScale).dp)) {
            AttendanceStatus.entries.forEach { st ->
                val selected = current == st
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape((7 * uiScale).dp))
                        .background(
                            if (selected) colors.brand.colorBrand
                            else colors.surface.colorCard,
                        )
                        .border(
                            1.dp,
                            if (selected) colors.brand.colorBrand
                            else colors.surface.colorOutline.copy(alpha = 0.5f),
                            RoundedCornerShape((7 * uiScale).dp),
                        )
                        .pointerInput(st) {
                            detectTapGestures(onTap = {
                                onSetAttendance(if (selected) null else st)
                            })
                        }
                        .padding(horizontal = (7 * uiScale).dp, vertical = (3 * uiScale).dp),
                ) {
                    Text(
                        text = st.shortLabel,
                        fontSize = (10 * uiScale).sp,
                        color = if (selected) Color.White else colors.surface.colorTextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height((14 * uiScale).dp))
        PrimaryButton(text = "关闭", uiScale = uiScale, onClick = onDismiss)
    }
}

/** 日程详情 */
@Composable
fun ScheduleDetailDialog(
    schedule: ScheduleEntry,
    uiScale: Float,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NongyuTheme.colors
    val color = getPaletteColor(schedule.colorIndex)

    SheetScaffold(uiScale = uiScale, onDismiss = onDismiss) {
        Text(
            text = schedule.title.ifBlank { "未命名日程" },
            fontSize = (17 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = color?.let { Color(it.text) } ?: colors.surface.colorText,
        )
        Spacer(Modifier.height((6 * uiScale).dp))
        if (schedule.content.isNotBlank()) InfoRow("备注", schedule.content, uiScale)
        if (schedule.location.isNotBlank()) InfoRow("地点", schedule.location, uiScale)
        InfoRow(
            "时间",
            "周${"一二三四五六日"[schedule.day - 1]} 第${schedule.startPeriod}-${schedule.endPeriod}节",
            uiScale,
        )
        InfoRow(
            "周次",
            if (schedule.weeksList.isEmpty()) "每周" else schedule.weeksList.sorted().joinToString(","),
            uiScale,
        )

        Spacer(Modifier.height((14 * uiScale).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((8 * uiScale).dp)) {
            SecondaryButton(
                text = "删除",
                uiScale = uiScale,
                modifier = Modifier.weight(1f),
                danger = true,
                onClick = onDelete,
            )
            SecondaryButton(
                text = "编辑",
                uiScale = uiScale,
                modifier = Modifier.weight(1f),
                onClick = onEdit,
            )
        }
    }
}

/** 日程新建 / 编辑表单 */
@Composable
fun ScheduleFormDialog(
    initial: ScheduleEntry,
    isNew: Boolean = false,
    uiScale: Float,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntry) -> Unit,
) {
    val colors = NongyuTheme.colors
    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }
    var location by remember { mutableStateOf(initial.location) }
    var day by remember { mutableStateOf(initial.day) }
    var startPeriod by remember { mutableStateOf(initial.startPeriod) }
    var endPeriod by remember { mutableStateOf(initial.endPeriod) }
    var colorIndex by remember { mutableStateOf(initial.colorIndex) }

    SheetScaffold(uiScale = uiScale, onDismiss = onDismiss) {
        Text(
            text = if (isNew) "新建日程" else "编辑日程",
            fontSize = (16 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height((10 * uiScale).dp))

        FieldLabel("标题", uiScale)
        InputField(title, "如：小组讨论", uiScale) { title = it }

        Spacer(Modifier.height((8 * uiScale).dp))
        FieldLabel("地点", uiScale)
        InputField(location, "选填", uiScale) { location = it }

        Spacer(Modifier.height((8 * uiScale).dp))
        FieldLabel("备注", uiScale)
        InputField(content, "选填", uiScale) { content = it }

        Spacer(Modifier.height((10 * uiScale).dp))
        FieldLabel("星期", uiScale)
        Row(horizontalArrangement = Arrangement.spacedBy((4 * uiScale).dp)) {
            (1..7).forEach { d ->
                Chip(
                    text = "周${"一二三四五六日"[d - 1]}",
                    selected = day == d,
                    uiScale = uiScale,
                ) { day = d }
            }
        }

        Spacer(Modifier.height((10 * uiScale).dp))
        FieldLabel("节次", uiScale)
        Row(
            horizontalArrangement = Arrangement.spacedBy((4 * uiScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("第", fontSize = (11 * uiScale).sp, color = colors.surface.colorTextSecondary)
            PeriodStepper(startPeriod, uiScale) {
                startPeriod = it
                if (endPeriod < it) endPeriod = it
            }
            Text("到", fontSize = (11 * uiScale).sp, color = colors.surface.colorTextSecondary)
            PeriodStepper(endPeriod, uiScale) {
                endPeriod = it.coerceAtLeast(startPeriod)
            }
            Text("节", fontSize = (11 * uiScale).sp, color = colors.surface.colorTextSecondary)
        }

        Spacer(Modifier.height((10 * uiScale).dp))
        FieldLabel("颜色", uiScale)
        Row(horizontalArrangement = Arrangement.spacedBy((6 * uiScale).dp)) {
            COURSE_COLOR_PALETTE.forEachIndexed { idx, c ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape((6 * uiScale).dp))
                        .background(Color(c.bg))
                        .border(
                            if (colorIndex == idx) 2.dp else 0.5.dp,
                            if (colorIndex == idx) colors.brand.colorBrand
                            else colors.surface.colorOutline.copy(alpha = 0.5f),
                            RoundedCornerShape((6 * uiScale).dp),
                        )
                        .pointerInput(idx) {
                            detectTapGestures(onTap = {
                                colorIndex = if (colorIndex == idx) null else idx
                            })
                        }
                        .padding((7 * uiScale).dp),
                )
            }
        }

        Spacer(Modifier.height((14 * uiScale).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((8 * uiScale).dp)) {
            SecondaryButton(
                text = "取消",
                uiScale = uiScale,
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            PrimaryButton(
                text = if (isNew) "创建" else "保存",
                uiScale = uiScale,
                modifier = Modifier.weight(1f),
                enabled = title.isNotBlank(),
            ) {
                onConfirm(
                    initial.copy(
                        title = title.trim(),
                        content = content.trim(),
                        location = location.trim(),
                        day = day,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        colorIndex = colorIndex,
                    ),
                )
            }
        }
    }
}

// ---------------- 小部件 ----------------

@Composable
private fun InfoRow(label: String, value: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = (2 * uiScale).dp)) {
        Text(
            text = label,
            fontSize = (11 * uiScale).sp,
            color = colors.surface.colorTextSecondary,
            modifier = Modifier.width((46 * uiScale).dp),
        )
        Text(
            text = value,
            fontSize = (11 * uiScale).sp,
            color = colors.surface.colorText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Divider(uiScale: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(NongyuTheme.colors.surface.colorOutline.copy(alpha = 0.4f)),
    )
}

@Composable
private fun FieldLabel(text: String, uiScale: Float) {
    Text(
        text = text,
        fontSize = (11 * uiScale).sp,
        color = NongyuTheme.colors.surface.colorTextSecondary,
    )
    Spacer(Modifier.height((3 * uiScale).dp))
}

@Composable
private fun InputField(
    value: String,
    placeholder: String,
    uiScale: Float,
    onChange: (String) -> Unit,
) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((8 * uiScale).dp))
            .background(colors.surface.colorBackground)
            .border(0.5.dp, colors.surface.colorOutline.copy(alpha = 0.5f), RoundedCornerShape((8 * uiScale).dp))
            .padding(horizontal = (9 * uiScale).dp, vertical = (7 * uiScale).dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = (11 * uiScale).sp,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.45f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = colors.surface.colorText,
                fontSize = (11 * uiScale).sp,
            ),
            cursorBrush = SolidColor(colors.brand.colorBrand),
            singleLine = true,
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, uiScale: Float, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape((6 * uiScale).dp))
            .background(if (selected) colors.brand.colorBrand else colors.surface.colorBackground)
            .border(
                0.5.dp,
                if (selected) colors.brand.colorBrand else colors.surface.colorOutline.copy(alpha = 0.5f),
                RoundedCornerShape((6 * uiScale).dp),
            )
            .pointerInput(selected) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = (6 * uiScale).dp, vertical = (3 * uiScale).dp),
    ) {
        Text(
            text = text,
            fontSize = (9 * uiScale).sp,
            color = if (selected) Color.White else colors.surface.colorTextSecondary,
        )
    }
}

@Composable
private fun PeriodStepper(value: Int, uiScale: Float, onChange: (Int) -> Unit) {
    val colors = NongyuTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape((6 * uiScale).dp))
            .background(colors.surface.colorBackground)
            .border(0.5.dp, colors.surface.colorOutline.copy(alpha = 0.5f), RoundedCornerShape((6 * uiScale).dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .pointerInput(value) { detectTapGestures(onTap = { if (value > 1) onChange(value - 1) }) }
                .padding(horizontal = (6 * uiScale).dp, vertical = (2 * uiScale).dp),
        ) { Text("−", fontSize = (11 * uiScale).sp, color = colors.surface.colorTextSecondary) }
        Text(
            text = value.toString(),
            fontSize = (11 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = colors.surface.colorText,
        )
        Box(
            modifier = Modifier
                .pointerInput(value) { detectTapGestures(onTap = { if (value < 10) onChange(value + 1) }) }
                .padding(horizontal = (6 * uiScale).dp, vertical = (2 * uiScale).dp),
        ) { Text("+", fontSize = (11 * uiScale).sp, color = colors.surface.colorTextSecondary) }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    uiScale: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((9 * uiScale).dp))
            .background(
                if (enabled) colors.brand.colorBrand
                else colors.surface.colorOutline.copy(alpha = 0.3f),
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            }
            .padding(vertical = (8 * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = (12 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    uiScale: Float,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape((9 * uiScale).dp))
            .background(colors.surface.colorBackground)
            .border(
                0.5.dp,
                if (danger) Color(0xFFC0392B).copy(alpha = 0.6f)
                else colors.surface.colorOutline.copy(alpha = 0.5f),
                RoundedCornerShape((9 * uiScale).dp),
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = (8 * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = (12 * uiScale).sp,
            color = if (danger) Color(0xFFC0392B) else colors.surface.colorTextSecondary,
        )
    }
}

/** 周次文案：如「1-16周」「2-16双周」「1,3,5,7周」 */
private fun weekLabel(course: CourseEntry): String {
    val list = course.weeksList
    if (!list.isNullOrEmpty()) return list.sorted().joinToString(",") + "周"
    val base = "${course.weeks.start}-${course.weeks.end}周"
    return when {
        course.odd -> "$base（单周）"
        course.even -> "$base（双周）"
        else -> base
    }
}
