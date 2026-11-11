package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nongyu.appplus.ui.theme.NongyuTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * 「跳转到周次 / 某一天」日历子窗体（票 #60 之后的新需求）。
 *
 * ## 为什么复用 [CourseCalendar]
 *
 * 维护者早前就要求「日历子窗体做成可复用组件」，`CourseCalendar` 已经具备
 * 日视图 ↔ 12 月网格、翻月、选天、主题色高亮。这里**只加业务语义**：
 * - **周模式**：点某一天 → 换算成「该天是学期第几周」→ 跳过去；
 * - **天模式**：点某一天 → 直接跳到那一天。
 *
 * 组件本身不认识「周」，只吐一个 `LocalDate`，换算在调用方做。这样以后
 * 日程、导出等场景都能复用同一个日历。
 *
 * @param dayMode true = 天模式（跳某一天），false = 周模式（跳某周）
 * @param currentDate 当前显示周的某一天（周模式下用来定位默认月份）
 * @param classDays 需要高亮成主题色的天（这里是「有课的天」）
 * @param onPick 选中某一天后回调
 */
@Composable
fun JumpToDateDialog(
    dayMode: Boolean,
    currentDate: LocalDate,
    uiScale: Float,
    classDays: Set<LocalDate>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(14.dp)),
        ) {
            Text(
                text = if (dayMode) "跳到某一天" else "跳到某一周",
                color = colors.surface.colorText,
                fontSize = (13f * uiScale).sp,
                lineHeight = (16f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(3.dp)))
            Text(
                text = if (dayMode) "点日期直接跳过去" else "点日期跳到它所属的那一周",
                color = colors.surface.colorTextSecondary.copy(alpha = 0.8f),
                fontSize = (8.5f * uiScale).sp,
                lineHeight = (11f * uiScale).sp,
            )
            Spacer(Modifier.height(s(10.dp)))

            // 复用已有日历：单选、有课的天标主题色。
            //
            // 维护者要求「点日期即刻跳转」——所以选中回调里直接执行跳转并关闭，
            // 不再需要确认按钮。注意 CourseCalendar 本身不认识业务语义，
            // 这里只是把它的 onSelectionChange 当成"点了哪天"的信号使用。
            CourseCalendar(
                selectedDays = emptySet(),
                onSelectionChange = { days ->
                    days.lastOrNull()?.let {
                        onPick(it)
                        onDismiss()
                    }
                },
                uiScale = uiScale,
                classDays = classDays,
                multiSelect = false,
                initialMonth = YearMonth.from(currentDate),
            )
        }
    }
}
