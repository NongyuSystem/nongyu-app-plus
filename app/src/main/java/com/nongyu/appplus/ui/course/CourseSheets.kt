package com.nongyu.appplus.ui.course

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.window.DialogProperties
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

/**
 * 底部抽屉外壳 —— 对齐旧版：从底部升起、顶部有一个抓手条、
 * 内容区圆角 20dp、背景是卡片色。
 *
 * 用 Dialog + usePlatformDefaultWidth=false 实现全宽贴底，
 * 避免引入额外依赖（BottomSheet 需要 material 实验 API）。
 */
@Composable
private fun BottomSheet(
    uiScale: Float,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(d: Dp): Dp = (d.value * uiScale).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            )
            // 抽屉
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = s(20.dp), topEnd = s(20.dp)))
                    .background(colors.surface.colorCard),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 抓手条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = s(8.dp), bottom = s(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = s(32.dp), height = s(4.dp))
                                .clip(RoundedCornerShape(s(2.dp)))
                                .background(colors.surface.colorOutline.copy(alpha = 0.5f)),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = s(560.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(start = s(18.dp), end = s(18.dp), bottom = s(22.dp)),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** 标签胶囊：旧版详情里那些「📍 10-A208」样式的小圆角标签 */
@Composable
private fun InfoPill(
    text: String,
    uiScale: Float,
    icon: String? = null,
) {
    val colors = NongyuTheme.colors
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(s(10.dp)))
            .background(colors.brand.colorBrand.copy(alpha = 0.09f))
            .padding(horizontal = s(9.dp), vertical = s(5.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Text(icon, fontSize = (10 * uiScale).sp, color = colors.brand.colorBrand)
            Spacer(Modifier.width(s(4.dp)))
        }
        Text(
            text = text,
            fontSize = (11 * uiScale).sp,
            color = colors.brand.colorBrand,
        )
    }
}

// ============================================================
// 课程详情
// ============================================================

@Composable
fun CourseDetailSheet(
    course: CourseEntry,
    week: Int,
    attendances: List<CourseAttendance>,
    uiScale: Float,
    onDismiss: () -> Unit,
    onSetAttendance: (AttendanceStatus?) -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val colors = NongyuTheme.colors
    val mine = listAttendancesForCourse(attendances, course.id)
    val full = countFullAttendanceForCourse(mine)
    val summary = formatFullAttendanceSummary(full)
    val current = mine.firstOrNull { it.week == week && it.day == course.day }?.status

    BottomSheet(uiScale = uiScale, onDismiss = onDismiss) {
        // 标题
        Text(
            text = course.name,
            fontSize = (22 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height(s(10.dp)))

        // 标签胶囊：地点 / 教师 / 节次时间 / 周次 / 星期
        Column(verticalArrangement = Arrangement.spacedBy(s(6.dp))) {
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                InfoPill("📍 ${course.room.ifBlank { "—" }}", uiScale)
                InfoPill("👤 ${course.teacher.ifBlank { "—" }}", uiScale)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                InfoPill(
                    "🕘 周${"一二三四五六日"[course.day - 1]} " +
                        "${course.startPeriod}-${course.endPeriod}节 " +
                        "${com.nongyu.appplus.data.course.periodStart(course.startPeriod)}" +
                        "-${com.nongyu.appplus.data.course.periodEnd(course.endPeriod)}",
                    uiScale,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                InfoPill("📅 ${weekLabel(course)}", uiScale)
                InfoPill("周${"一二三四五六日"[course.day - 1]}", uiScale)
            }
        }

        Spacer(Modifier.height(s(16.dp)))
        SectionDivider(uiScale)
        Spacer(Modifier.height(s(12.dp)))

        // 考勤
        Text(
            text = "考勤",
            fontSize = (15 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height(s(4.dp)))
        Text(
            text = "本节 · 第${week}周 · 周${"一二三四五六日"[course.day - 1]} · " +
                (current?.label ?: "未标记"),
            fontSize = (11 * uiScale).sp,
            color = colors.surface.colorTextSecondary,
        )
        Spacer(Modifier.height(s(8.dp)))

        // 五态按钮
        Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
            AttendanceStatus.entries.forEach { st ->
                val selected = current == st
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(s(10.dp)))
                        .background(
                            if (selected) colors.brand.colorBrand
                            else colors.surface.colorBackground,
                        )
                        .pointerInput(st) {
                            detectTapGestures(onTap = {
                                onSetAttendance(if (selected) null else st)
                            })
                        }
                        .padding(vertical = s(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = st.label,
                        fontSize = (10 * uiScale).sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) Color.White else colors.surface.colorTextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(s(14.dp)))
        Text(
            text = "本学期考勤记录",
            fontSize = (13 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height(s(4.dp)))
        Text(
            text = summary ?: "本学期暂无考勤记录",
            fontSize = (11 * uiScale).sp,
            color = colors.surface.colorTextSecondary,
        )
    }
}

// ============================================================
// 日程详情
// ============================================================

@Composable
fun ScheduleDetailSheet(
    schedule: ScheduleEntry,
    uiScale: Float,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val colors = NongyuTheme.colors

    BottomSheet(uiScale = uiScale, onDismiss = onDismiss) {
        Text(
            text = schedule.title.ifBlank { "未命名日程" },
            fontSize = (22 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height(s(10.dp)))

        Column(verticalArrangement = Arrangement.spacedBy(s(6.dp))) {
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                if (schedule.location.isNotBlank()) {
                    InfoPill("📍 ${schedule.location}", uiScale)
                }
                InfoPill(
                    "🕘 周${"一二三四五六日"[schedule.day - 1]} " +
                        "${schedule.startPeriod}-${schedule.endPeriod}节",
                    uiScale,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                InfoPill(
                    "📅 " + if (schedule.weeksList.isEmpty()) "每周"
                    else schedule.weeksList.sorted().joinToString(",") + "周",
                    uiScale,
                )
            }
            if (schedule.content.isNotBlank()) {
                Spacer(Modifier.height(s(6.dp)))
                Text(
                    text = schedule.content,
                    fontSize = (12 * uiScale).sp,
                    color = colors.surface.colorTextSecondary,
                )
            }
        }

        Spacer(Modifier.height(s(18.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
            SheetButton("删除", uiScale, Modifier.weight(1f), danger = true, onClick = onDelete)
            SheetButton("编辑", uiScale, Modifier.weight(1f), onClick = onEdit)
        }
    }
}

// ============================================================
// 日程表单
// ============================================================

@Composable
fun ScheduleFormSheet(
    initial: ScheduleEntry,
    isNew: Boolean = false,
    uiScale: Float,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntry) -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val colors = NongyuTheme.colors

    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }
    var location by remember { mutableStateOf(initial.location) }
    var day by remember { mutableStateOf(initial.day) }
    var startPeriod by remember { mutableStateOf(initial.startPeriod) }
    var endPeriod by remember { mutableStateOf(initial.endPeriod) }
    var colorIndex by remember { mutableStateOf(initial.colorIndex) }

    BottomSheet(uiScale = uiScale, onDismiss = onDismiss) {
        Text(
            text = if (isNew) "新建日程" else "编辑日程",
            fontSize = (20 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height(s(12.dp)))

        FieldLabel("标题", uiScale)
        InputField(title, "如：小组讨论", uiScale) { title = it }

        Spacer(Modifier.height(s(10.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("地点", uiScale)
                InputField(location, "选填", uiScale) { location = it }
            }
        }

        Spacer(Modifier.height(s(10.dp)))
        FieldLabel("备注", uiScale)
        InputField(content, "选填", uiScale) { content = it }

        Spacer(Modifier.height(s(12.dp)))
        FieldLabel("星期", uiScale)
        Row(horizontalArrangement = Arrangement.spacedBy(s(4.dp))) {
            (1..7).forEach { d ->
                Chip("周${"一二三四五六日"[d - 1]}", day == d, uiScale) { day = d }
            }
        }

        Spacer(Modifier.height(s(12.dp)))
        FieldLabel("节次", uiScale)
        Row(
            horizontalArrangement = Arrangement.spacedBy(s(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PeriodStepper(startPeriod, uiScale) {
                startPeriod = it
                if (endPeriod < it) endPeriod = it
            }
            Text("→", fontSize = (12 * uiScale).sp, color = colors.surface.colorTextSecondary)
            PeriodStepper(endPeriod, uiScale) { endPeriod = it.coerceAtLeast(startPeriod) }
        }

        Spacer(Modifier.height(s(12.dp)))
        FieldLabel("颜色", uiScale)
        Row(horizontalArrangement = Arrangement.spacedBy(s(7.dp))) {
            COURSE_COLOR_PALETTE.forEachIndexed { idx, c ->
                Box(
                    modifier = Modifier
                        .size(s(26.dp))
                        .clip(RoundedCornerShape(s(8.dp)))
                        .background(Color(c.bg))
                        .border(
                            if (colorIndex == idx) 2.dp else 0.5.dp,
                            if (colorIndex == idx) colors.brand.colorBrand
                            else colors.surface.colorOutline.copy(alpha = 0.5f),
                            RoundedCornerShape(s(8.dp)),
                        )
                        .pointerInput(idx) {
                            detectTapGestures(onTap = {
                                colorIndex = if (colorIndex == idx) null else idx
                            })
                        },
                )
            }
        }

        Spacer(Modifier.height(s(20.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
            SheetButton("取消", uiScale, Modifier.weight(1f), onClick = onDismiss)
            PrimarySheetButton(
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

// ============================================================
// 周选择器（日历）
// ============================================================

@Composable
fun WeekPickerSheet(
    currentWeek: Int,
    maxWeek: Int,
    todayWeek: Int,
    uiScale: Float,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    val colors = NongyuTheme.colors

    BottomSheet(uiScale = uiScale, onDismiss = onDismiss) {
        Text(
            text = "选择周次",
            fontSize = (20 * uiScale).sp,
            fontWeight = FontWeight.Bold,
            color = colors.surface.colorText,
        )
        Spacer(Modifier.height(s(4.dp)))
        Text(
            text = "共 $maxWeek 周 · 当前第 $currentWeek 周",
            fontSize = (11 * uiScale).sp,
            color = colors.surface.colorTextSecondary,
        )
        Spacer(Modifier.height(s(14.dp)))

        // 周次网格：每行 4 个
        val rows = (maxWeek + 3) / 4
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = s(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
            ) {
                for (c in 0 until 4) {
                    val w = r * 4 + c + 1
                    if (w > maxWeek) {
                        Spacer(modifier = Modifier.weight(1f))
                        continue
                    }
                    val isCurrent = w == currentWeek
                    val isToday = w == todayWeek
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(s(12.dp)))
                            .background(
                                when {
                                    isCurrent -> colors.brand.colorBrand
                                    isToday -> colors.brand.colorBrand.copy(alpha = 0.12f)
                                    else -> colors.surface.colorBackground
                                },
                            )
                            .pointerInput(w) { detectTapGestures(onTap = { onPick(w) }) }
                            .padding(vertical = s(9.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "第$w",
                            fontSize = (13 * uiScale).sp,
                            fontWeight = if (isCurrent || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isCurrent -> Color.White
                                isToday -> colors.brand.colorBrand
                                else -> colors.surface.colorText
                            },
                        )
                        if (isToday) {
                            Text(
                                text = "本周",
                                fontSize = (8 * uiScale).sp,
                                color = if (isCurrent) Color.White.copy(alpha = 0.85f)
                                else colors.brand.colorBrand,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 通用小部件
// ============================================================

@Composable
private fun SectionDivider(uiScale: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(NongyuTheme.colors.surface.colorOutline.copy(alpha = 0.35f)),
    )
}

@Composable
private fun FieldLabel(text: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Text(
        text = text,
        fontSize = (11 * uiScale).sp,
        color = colors.surface.colorTextSecondary,
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
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(10.dp)))
            .background(colors.surface.colorBackground)
            .padding(horizontal = s(10.dp), vertical = s(9.dp)),
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
                fontSize = (12 * uiScale).sp,
            ),
            cursorBrush = SolidColor(colors.brand.colorBrand),
            singleLine = true,
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, uiScale: Float, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(s(8.dp)))
            .background(if (selected) colors.brand.colorBrand else colors.surface.colorBackground)
            .pointerInput(selected) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = s(6.dp), vertical = s(5.dp)),
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
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(s(10.dp)))
            .background(colors.surface.colorBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .pointerInput(value) { detectTapGestures(onTap = { if (value > 1) onChange(value - 1) }) }
                .padding(horizontal = s(9.dp), vertical = s(6.dp)),
        ) { Text("−", fontSize = (13 * uiScale).sp, color = colors.surface.colorTextSecondary) }
        Text(
            text = value.toString(),
            fontSize = (13 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = colors.surface.colorText,
        )
        Box(
            modifier = Modifier
                .pointerInput(value) { detectTapGestures(onTap = { if (value < 10) onChange(value + 1) }) }
                .padding(horizontal = s(9.dp), vertical = s(6.dp)),
        ) { Text("+", fontSize = (13 * uiScale).sp, color = colors.surface.colorTextSecondary) }
    }
}

@Composable
private fun SheetButton(
    text: String,
    uiScale: Float,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s(12.dp)))
            .background(colors.surface.colorBackground)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = s(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = (13 * uiScale).sp,
            color = if (danger) Color(0xFFC0392B) else colors.surface.colorTextSecondary,
        )
    }
}

@Composable
private fun PrimarySheetButton(
    text: String,
    uiScale: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(d: Dp): Dp = (d.value * uiScale).dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s(12.dp)))
            .background(
                if (enabled) colors.brand.colorBrand
                else colors.surface.colorOutline.copy(alpha = 0.3f),
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            }
            .padding(vertical = s(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = (13 * uiScale).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

/** 周次文案 */
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
