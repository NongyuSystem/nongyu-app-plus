package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 课程详情卡内容（占据导航栏变形后的位置）。
 *
 * ## 为什么是「导航栏变形」而不是 BottomSheet
 * 票 #16 已拍板：**采纳需求原文**，导航栏原地拉高成卡片，
 * 中间农小屿星云球溶解淡出。不跟旧农屿的 BottomSheet、也不跟它的全屏宽。
 *
 * 外框（圆角、虚化边界、拉高动画）由 [com.nongyu.appplus.ui.MainShell] 负责，
 * 本组件只画内容——这样卡片与导航栏共用同一套视觉，不会割裂。
 *
 * ## 信息量（维护者反馈「很多信息都没显示」后补全）
 * 现在展示 **9 项**，分三层：
 *
 * 1. 标题行：课程名（加粗大字号）
 * 2. 时间行：星期 + 起止节次 + **具体时刻**（如 `08:10 - 09:50`）——这是之前漏掉的
 * 3. 胶囊行：地点 / 教师 / 周次
 * 4. 底部：**该课程本学期其他时段**（同课不同天/不同节次），这条最能解释"信息量不够"
 *
 * ## 顶部横杠
 * 票 #17 查清它是 BottomSheet 的拖动抓手条（`40×4`/圆角 2）。
 * 这里沿用其尺寸与居中对齐，作为「可下拉收起」的视觉提示。
 */
@Composable
fun CourseDetailContent(
    course: CourseEntry,
    uiScale: Float,
    onDismiss: () -> Unit,
    /** 同一门课在本学期的其他时段（由调用方从全量课表里筛出来）。 */
    otherSlots: List<CourseEntry> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    fun t(size: Float): androidx.compose.ui.unit.TextUnit = (size * uiScale).sp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s(16.dp), vertical = s(5.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部横杠（沿用旧农屿 40×4/圆角2）
        Box(
            modifier = Modifier
                .width(s(40.dp))
                .height(s(4.dp))
                .clip(RoundedCornerShape(s(2.dp)))
                .background(colors.surface.colorOutline.copy(alpha = 0.55f)),
        )

        Spacer(Modifier.height(s(6.dp)))

        // ---- 第一层：课程名 ----
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

        Spacer(Modifier.height(s(3.dp)))

        // ---- 第二层：时间（星期 + 节次 + 具体时刻）----
        // 具体时刻是之前漏掉的关键信息：光看「1-2 节」不知道几点上课。
        Text(
            text = timeLine(course),
            color = colors.brand.colorBrand,
            fontSize = t(9.5f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(s(5.dp)))

        // ---- 第三层：地点 / 教师 / 周次 ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(s(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip("📍 " + course.room, uiScale)
            InfoChip("👤 " + course.teacher, uiScale)
            InfoChip(weeksLabel(course), uiScale)
        }

        // ---- 第四层：该课程本学期其他时段 ----
        if (otherSlots.isNotEmpty()) {
            Spacer(Modifier.height(s(6.dp)))
            Text(
                text = "本学期还有：" + otherSlots.take(3).joinToString("、") { slotBrief(it) },
                color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
                fontSize = t(8f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 一个小胶囊标签（地点 / 教师 / 周次共用）。 */
@Composable
private fun InfoChip(text: String, uiScale: Float) {
    if (text.isBlank()) return
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(s(8.dp)))
            .background(colors.brand.colorBrandMuted.copy(alpha = 0.55f))
            .padding(horizontal = s(6.dp), vertical = s(2.5f.dp)),
    ) {
        Text(
            text = text,
            color = colors.surface.colorTextSecondary,
            fontSize = (7.5f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
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
        course.odd && !course.even -> "$base·单"
        !course.odd && course.even -> "$base·双"
        else -> base
    }
}
