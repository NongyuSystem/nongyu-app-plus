package com.nongyu.appplus.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.timetable.logic.COURSE_TIMES
import com.nongyu.appplus.timetable.logic.WEEKDAY_LABELS
import com.nongyu.appplus.timetable.model.CourseEntry

/**
 * 课程详情卡内容。
 *
 * ## 两个形态（维护者 2026-11-10 拍板）
 * - **简介态**：课名 + 时间 + **横向三个胶囊标签**（地点 / 教师 / 周次）
 * - **详情态**：**左键右值**两列格式，且可修改：
 *   - 地点值可点 → 地点修改子窗体（三档范围）；旁边有自绘 SVG 导航按钮 → 导航占位子窗体
 *   - 考勤：按该课实际节次动态生成标签，默认已结束的课已考勤，点击可取消/恢复
 *   - 评论：mock 后端列表，可增删
 *   - 备注：多行输入，写本地持久层
 *
 * 顶部横杠可拖（仅横杠区），上拖进详情态、下拖回简介态、拖到底退出。
 * 全部不用 emoji。
 */
@Composable
fun CourseDetailContent(
    course: CourseEntry,
    uiScale: Float,
    onDismiss: () -> Unit,
    fullStage: Boolean,
    /** 高度进度 0..1：0 = 简介态，1 = 详情态满高。内容分帧动画据此驱动。 */
    expandProgress: Float,
    /** 是否正在收拢退出：此时两种形态都应淡出，不让胶囊重新浮现。 */
    collapsing: Boolean = false,
    /** 第四态标志：最底部显示「删除这天的这节课」。 */
    showDelete: Boolean = false,
    onDelete: () -> Unit = {},
    onHandleDrag: (deltaDp: Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    /** 地点覆盖值（已解析后的显示值）。 */
    location: String,
    /** 教师显示值（可被覆盖）。 */
    teacher: String,
    /** 教师行内编辑确认回调。 */
    onTeacherChange: (String) -> Unit,
    /** 周次显示值。 */
    weekText: String,
    /** 打开周次编辑子窗体（日历 + 固定周 + 例外天）。 */
    onWeekEdit: () -> Unit,
    /** 考勤状态：period -> 是否已考勤。 */
    attendance: Map<Int, Boolean>,
    onAttendanceToggle: (Int) -> Unit,
    /** 备注文本与写入回调。 */
    note: String,
    onNoteChange: (String) -> Unit,
    /** 评论列表与增删回调。 */
    comments: List<String>,
    onCommentAdd: (String) -> Unit,
    onCommentRemove: (Int) -> Unit,
    /** 打开地点修改子窗体。 */
    onLocationEdit: () -> Unit,
    /** 打开导航占位子窗体。 */
    onLocationNav: () -> Unit,
    otherSlots: List<CourseEntry> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    val density = LocalDensity.current
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    fun t(size: Float): androidx.compose.ui.unit.TextUnit = (size * uiScale).sp

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 「卡片内部不退出」的关键：卡片本身要**消费掉**落在它范围内的点击，
            // 否则点击会穿透到下面那层「卡片外退出层」，表现就是点卡片内部也退出。
            //
            // 之前删过一次这个消费层（当时是为了修「第二次上划收不到手势」），
            // 那次错的是「加在整卡根部、把横杠的 pointerInput 也盖住了」。
            // 现在横杠的 pointerInput 在子节点上、声明更靠内，
            // 命中测试会先给子节点，横杠照常可拖。
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) { /* 只消费，不动作 */ }
            .padding(horizontal = s(18.dp), vertical = s(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- 可拖拽的横杠（命中区 56×18，视觉 40×4）----
        Box(
            modifier = Modifier
                .width(s(56.dp))
                .height(s(18.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dy -> onHandleDrag(-dy / density.density) },
                        onDragEnd = onHandleDragEnd,
                        onDragCancel = onHandleDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(s(40.dp))
                    .height(s(4.dp))
                    .clip(RoundedCornerShape(s(2.dp)))
                    .background(colors.surface.colorOutline.copy(alpha = 0.55f)),
            )
        }

        Spacer(Modifier.height(s(4.dp)))

        // ---- 课名 ----
        Text(
            text = course.name,
            color = colors.surface.colorText,
            fontSize = t(15f),
            lineHeight = t(18f),
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(s(2.dp)))
        Text(
            text = timeLine(course),
            color = colors.brand.colorBrand,
            fontSize = t(9.5f),
            lineHeight = t(12f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(s(8.dp)))

        // ---- 形态切换：全部由 expandProgress 驱动（跟随高度的弹簧动画逐帧变化）----
        //
        // 为什么不用 AnimatedVisibility：那是一次性的淡入淡出，与高度的 spring
        // 不同步，视觉上「高度在动、内容啪一下换掉」。这里让内容的位置/透明度
        // 直接由高度进度算出来，做到「根据高度位置做动画帧」（维护者要求）。
        //
        // - briefProgress  = 1 - expand：简介态胶囊随展开淡出并轻微上移
        // - detailProgress = expand：详情态 K-V 从下方滑入
        // 两者在同一坐标系里交叉，读起来像「三个胶囊移动、摊开成左键右值」。
        // 收拢退出时高度降到 navHeight，expandProgress 会被 coerce 到 0，
        // 若不处理，胶囊会「重新浮现」一下再消失。这里统一乘一个退出淡出系数。
        val exitFade = if (collapsing) 0f else 1f
        val briefProgress = ((1f - expandProgress) * exitFade).coerceIn(0f, 1f)
        val detailProgress = (expandProgress * exitFade).coerceIn(0f, 1f)

        if (briefProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = briefProgress
                        translationY = -(1f - briefProgress) * 12f * density.density
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(s(6.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BriefChip(location, uiScale)
                    BriefChip(teacher, uiScale)
                    BriefChip(weekText, uiScale)
                }
            }
        }

        if (detailProgress > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 占满剩余高度但不强制撑高：否则内容按全高测量，
                    // 会把后面第四态的删除按钮挤出卡片可视区（实测看不到）。
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        alpha = detailProgress
                        // 从盒子底部滑入（局部位移，不是全屏）
                        translationY = (1f - detailProgress) * 28f * density.density
                    },
            ) {
                KeyValueRow("地点", location, uiScale, onClick = onLocationEdit) {
                    LocationNavButton(uiScale, onLocationNav)
                }
                TeacherRow(teacher, uiScale, onTeacherChange)
                KeyValueRow("周次", weekText, uiScale, onClick = onWeekEdit)
                if (otherSlots.isNotEmpty()) {
                    KeyValueRow(
                        "其他时段",
                        otherSlots.take(3).joinToString("、") { slotBrief(it) },
                        uiScale,
                    )
                }

                Spacer(Modifier.height(s(8.dp)))
                SectionDivider(uiScale)
                Spacer(Modifier.height(s(6.dp)))
                StagedSlideIn(progress = expandProgress, threshold = 0.30f, density = density) {
                    AttendanceSection(course, attendance, onAttendanceToggle, uiScale)
                }

                Spacer(Modifier.height(s(8.dp)))
                SectionDivider(uiScale)
                Spacer(Modifier.height(s(6.dp)))
                StagedSlideIn(progress = expandProgress, threshold = 0.52f, density = density) {
                    CommentSection(comments, onCommentAdd, onCommentRemove, uiScale)
                }

                Spacer(Modifier.height(s(8.dp)))
                SectionDivider(uiScale)
                Spacer(Modifier.height(s(6.dp)))
                StagedSlideIn(progress = expandProgress, threshold = 0.74f, density = density) {
                    NoteSection(note, onNoteChange, uiScale)
                }
            }
        }

        // 第四态删除按钮放在**滚动区之外**、卡片最底部固定位置。
        // 放进 verticalScroll 的 Column 里会被裁在可视区外（实测第四态看不到按钮），
        // 因为详情态内容本就超出容器高度、靠滚动查看。
        if (showDelete) {
            Spacer(Modifier.height(s(8.dp)))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(10.dp)))
                    .background(colors.brand.colorBrandMuted.copy(alpha = 0.45f))
                    .clickable(onClick = onDelete)
                    .padding(vertical = s(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "删除这天的这节课",
                    color = Color(0xFFB3261E),
                    fontSize = (9.5f * uiScale).sp,
                    lineHeight = (13f * uiScale).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 教师行内输入：点值原地变输入框，回车 / 失焦确认（维护者选「行内输入」，
 * 不做子窗体、不做三档范围，与地点/周次的编辑形态刻意区分）。
 */
@Composable
private fun TeacherRow(value: String, uiScale: Float, onConfirm: (String) -> Unit) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = s(2.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "教师",
            color = colors.surface.colorTextSecondary.copy(alpha = 0.65f),
            fontSize = (8f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(s(48.dp)),
        )
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(
                    color = colors.brand.colorBrand,
                    fontSize = (8.5f * uiScale).sp,
                ),
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        editing = false
                        onConfirm(draft)
                    },
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(s(6.dp)))
                    .background(colors.surface.colorBackground)
                    .padding(horizontal = s(6.dp), vertical = s(3.dp)),
            )
        } else {
            Text(
                text = value,
                color = colors.surface.colorText,
                fontSize = (8.5f * uiScale).sp,
                lineHeight = (11f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable { draft = value; editing = true },
            )
        }
    }
}

/**
 * 按「高度位置」错峰滑出：卡片越往下长，越靠下的分区才依次从**盒子底部**滑入。
 *
 * 不是全屏滑入，而是每个分区各自一段短位移（24dp）＋淡入。
 * [threshold] 是该分区开始出现所需的展开进度；进度从 threshold 到 1 线性完成。
 */
@Composable
private fun StagedSlideIn(
    progress: Float,
    threshold: Float,
    density: androidx.compose.ui.unit.Density,
    content: @Composable () -> Unit,
) {
    val local = ((progress - threshold) / (1f - threshold)).coerceIn(0f, 1f)
    if (local <= 0f) return
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = local
            translationY = (1f - local) * 24f * density.density
        },
    ) {
        content()
    }
}

/** 简介态的一个胶囊标签。 */
@Composable
private fun BriefChip(text: String, uiScale: Float) {
    if (text.isBlank()) return
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(s(8.dp)))
            .background(colors.brand.colorBrandMuted.copy(alpha = 0.55f))
            .padding(horizontal = s(7.dp), vertical = s(3.dp)),
    ) {
        Text(
            text = text,
            color = colors.surface.colorTextSecondary,
            fontSize = (7.5f * uiScale).sp,
            lineHeight = (10f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 详情态的一行「左键 : 右值」，值可点击（改地点），右侧可挂导航按钮。 */
@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    uiScale: Float,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = s(2.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.surface.colorTextSecondary.copy(alpha = 0.65f),
            fontSize = (8f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(s(48.dp)),
        )
        Text(
            text = value,
            color = if (onClick != null) colors.brand.colorBrand else colors.surface.colorText,
            fontSize = (8.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
        if (trailing != null) {
            Spacer(Modifier.width(s(4.dp)))
            trailing()
        }
    }
}

@Composable
private fun SectionDivider(uiScale: Float) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((1f * uiScale).dp)
            .background(colors.surface.colorOutline.copy(alpha = 0.35f)),
    )
}

/**
 * 考勤：按课程实际覆盖的节次动态生成标签（编号用真实节次号），
 * 已考勤 = 主题深绿，未考勤 = 主题灰。点击切换。
 */
@Composable
private fun AttendanceSection(
    course: CourseEntry,
    attendance: Map<Int, Boolean>,
    onToggle: (Int) -> Unit,
    uiScale: Float,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val periods = course.startPeriod..course.endPeriod
    val attendedCount = periods.count { attendance[it] == true }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "考勤 · 已考勤 $attendedCount / 应到 ${periods.count()}",
            color = colors.surface.colorText,
            fontSize = (9.5f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(s(5.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(s(5.dp))) {
            periods.forEach { p ->
                val on = attendance[p] == true
                Box(
                    modifier = Modifier
                        .size(s(20.dp))
                        .clip(RoundedCornerShape(s(5.dp)))
                        .background(
                            if (on) colors.brand.colorBrand
                            else colors.surface.colorOutline.copy(alpha = 0.55f),
                        )
                        .clickable { onToggle(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$p",
                        color = if (on) colors.brand.colorOnBrand else colors.surface.colorTextSecondary,
                        fontSize = (8f * uiScale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 评论：mock 后端列表，可增删，底部一个输入框。 */
@Composable
private fun CommentSection(
    comments: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    uiScale: Float,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    var draft by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "评论",
            color = colors.surface.colorText,
            fontSize = (9.5f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(s(4.dp)))
        comments.forEachIndexed { i, c ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "· $c",
                    color = colors.surface.colorTextSecondary,
                    fontSize = (8f * uiScale).sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "删除",
                    color = colors.brand.colorBrand.copy(alpha = 0.8f),
                    fontSize = (7.5f * uiScale).sp,
                    modifier = Modifier.clickable { onRemove(i) },
                )
            }
            Spacer(Modifier.height(s(2.dp)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(s(8.dp)))
                    .background(colors.surface.colorBackground)
                    .padding(horizontal = s(8.dp), vertical = s(5.dp)),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (8f * uiScale).sp,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(s(6.dp)))
            Text(
                text = "添加",
                color = colors.brand.colorBrand,
                fontSize = (8f * uiScale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    onAdd(draft)
                    draft = ""
                },
            )
        }
    }
}

/** 备注：多行输入，改动即写持久层（由外部 onNoteChange 落盘）。 */
@Composable
private fun NoteSection(note: String, onNoteChange: (String) -> Unit, uiScale: Float) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "备注",
            color = colors.surface.colorText,
            fontSize = (9.5f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(s(4.dp)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(8.dp)))
                .background(colors.surface.colorBackground)
                .padding(s(8.dp)),
        ) {
            BasicTextField(
                value = note,
                onValueChange = onNoteChange,
                textStyle = TextStyle(
                    color = colors.surface.colorText,
                    fontSize = (8f * uiScale).sp,
                ),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 「周一 1-2 节 · 08:10-09:50」。 */
private fun timeLine(course: CourseEntry): String {
    val day = WEEKDAY_LABELS.getOrNull(course.day) ?: ""
    val periods = if (course.startPeriod == course.endPeriod) {
        "${course.startPeriod}"
    } else {
        "${course.startPeriod}-${course.endPeriod}"
    }
    val start = COURSE_TIMES.getOrNull(course.startPeriod - 1)?.start
    val end = COURSE_TIMES.getOrNull(course.endPeriod - 1)?.end
    val clock = if (start != null && end != null) " · $start-$end" else ""
    return "$day $periods 节$clock"
}

private fun slotBrief(course: CourseEntry): String {
    val day = WEEKDAY_LABELS.getOrNull(course.day) ?: ""
    val periods = if (course.startPeriod == course.endPeriod) {
        "${course.startPeriod}"
    } else {
        "${course.startPeriod}-${course.endPeriod}"
    }
    return "$day$periods"
}

/** 周次描述：不规则周次优先，其次区间 + 单双周。供详情卡与周次子窗体共用。 */
fun weeksLabelOf(course: CourseEntry): String {
    val list = course.weeksList
    if (!list.isNullOrEmpty()) {
        return if (list.size <= 4) {
            "第" + list.joinToString("、") + "周"
        } else {
            "第${list.first()}-${list.last()}周等${list.size}周"
        }
    }
    val base = "${course.weeks.start}-${course.weeks.end}周"
    return when {
        course.odd && !course.even -> "$base · 单周"
        !course.odd && course.even -> "$base · 双周"
        else -> base
    }
}
