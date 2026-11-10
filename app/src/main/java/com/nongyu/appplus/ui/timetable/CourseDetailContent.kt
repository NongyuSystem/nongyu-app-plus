package com.nongyu.appplus.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 课程详情卡内容。
 *
 * ## 两个形态（维护者 2026-11-10 要求）
 * - **简介态**：课名 / 时间 / 地点·教师·周次 / 其他时段
 * - **详情态**：在简介态基础上多出「考勤 / 评论 / 备注」三块分区
 *
 * 顶部横杠是**真的可拖**：上拖吸附到详情态、下拖吸附回简介态、
 * 继续下拉到底则退出。手势只在横杠区域生效（避免与卡片内部抢手势）。
 *
 * ## 布局口径
 * 不用 emoji（维护者明确要求）。信息用「标签 + 值」两列对齐，
 * 而不是挤在一行胶囊里——窄卡片里胶囊会换行、显得零碎。
 */
@Composable
fun CourseDetailContent(
    course: CourseEntry,
    uiScale: Float,
    onDismiss: () -> Unit,
    /** 是否为详情态（由 Shell 根据拖拽吸附结果传入）。 */
    fullStage: Boolean,
    /** 横杠拖拽：deltaDp 为本帧位移（dp，正=上拖）。 */
    onHandleDrag: (deltaDp: Float) -> Unit,
    /** 松手：由 Shell 决定吸附到哪个形态或退出。 */
    onHandleDragEnd: () -> Unit,
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
            .padding(horizontal = s(18.dp), vertical = s(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- 可拖拽的横杠 ----
        // 命中区域比视觉横杠大（44dp 高），方便手指抓；视觉仍是 40×4。
        Box(
            modifier = Modifier
                .width(s(56.dp))
                .height(s(18.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dy ->
                            // dy 为像素，负=向上；转成 dp 并取反（正=拉高）
                            onHandleDrag(-dy / density.density)
                        },
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

        // ---- 课程名 ----
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

        // ---- 时间行 ----
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

        // ---- 信息两列：标签 + 值（不用 emoji）----
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(s(3.dp)),
        ) {
            InfoRow("地点", course.room, uiScale)
            InfoRow("教师", course.teacher, uiScale)
            InfoRow("周次", weeksLabel(course), uiScale)
            if (otherSlots.isNotEmpty()) {
                InfoRow(
                    "其他时段",
                    otherSlots.take(3).joinToString("、") { slotBrief(it) },
                    uiScale,
                )
            }
        }

        // ---- 详情态：考勤 / 评论 / 备注 ----
        // AnimatedVisibility 让两块形态切换有淡入淡出（维护者要求「同时有动画」）。
        AnimatedVisibility(
            visible = fullStage,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(s(10.dp)))
                DetailSection("考勤", "本节 · 第 ${course.weeks.start} 周 · 未标记", uiScale)
                Spacer(Modifier.height(s(8.dp)))
                DetailSection("评论", "暂无评论", uiScale)
                Spacer(Modifier.height(s(8.dp)))
                DetailSection("备注", "暂无备注", uiScale)
            }
        }
    }
}

/** 一行「标签 : 值」，左标签右值，无 emoji。 */
@Composable
private fun InfoRow(label: String, value: String, uiScale: Float) {
    if (value.isBlank()) return
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Row(modifier = Modifier.fillMaxWidth()) {
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
            color = colors.surface.colorText,
            fontSize = (8.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 详情态的一块分区：分隔线 + 标题 + 占位内容（本期 mock，不接真实数据源）。 */
@Composable
private fun DetailSection(title: String, placeholder: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(s(1.dp))
                .background(colors.surface.colorOutline.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.height(s(6.dp)))
        Text(
            text = title,
            color = colors.surface.colorText,
            fontSize = (10f * uiScale).sp,
            lineHeight = (13f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(s(3.dp)))
        Text(
            text = placeholder,
            color = colors.surface.colorTextSecondary.copy(alpha = 0.7f),
            fontSize = (8f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.Medium,
        )
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

/** 「周一 3-4 节」这种短标签，用于"本学期还有"。 */
private fun slotBrief(course: CourseEntry): String {
    val day = WEEKDAY_LABELS.getOrNull(course.day) ?: ""
    val periods = if (course.startPeriod == course.endPeriod) {
        "${course.startPeriod}"
    } else {
        "${course.startPeriod}-${course.endPeriod}"
    }
    return "$day$periods"
}

/** 周次描述：不规则周次优先，其次区间 + 单双周。 */
private fun weeksLabel(course: CourseEntry): String {
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
