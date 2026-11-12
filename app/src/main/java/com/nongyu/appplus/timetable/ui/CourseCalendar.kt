package com.nongyu.appplus.timetable.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.nongyu.appplus.design.NongyuTheme
import java.time.LocalDate
import java.time.YearMonth
import com.nongyu.appplus.timetable.model.TOTAL_WEEKS
import com.nongyu.appplus.timetable.data.MOCK_TERM_START
import com.nongyu.appplus.timetable.model.CourseEntry

/**
 * 按课程的固定周算出「需要上课的天」集合，供日历标主题色。
 *
 * 纯函数、不依赖 Android/Compose：从学期首周一开始，逐周判断
 * `occursIn(week)`，命中的周取该周对应星期几的日期。
 * 周次编辑与地点编辑共用这个集合（「有关联」的那部分）。
 */
fun classDaysOf(course: CourseEntry, termStartMonday: LocalDate = MOCK_TERM_START): Set<LocalDate> {
    val out = mutableSetOf<LocalDate>()
    for (week in 1..TOTAL_WEEKS) {
        if (!course.occursIn(week)) continue
        out += termStartMonday.plusWeeks((week - 1).toLong()).plusDays(course.day.toLong())
    }
    return out
}

/**
 * 可复用日历组件（本期给地点编辑 / 周次编辑共用，后面的票也会用）。
 *
 * ## 刻意做成「最小通用面」
 * 只负责：选天（单选/多选）、两级视图切换、翻月、按调用方给的集合高亮。
 * **不塞任何课程业务语义** —— 什么叫「需要上课的天」由调用方通过
 * [classDays] 传进来，组件只把它画成主题色。后面的票传不同集合即可复用。
 *
 * ## 两级视图（维护者拍板）
 * - 日视图：当月日历网格 + 顶部「‹ 2026年11月 ›」+ 左右翻页
 * - 月份网格：点顶部月份标题切换成 12 个月份卡片，点某个月切回该月日视图
 *
 * ## 风格
 * 白绿主题、圆角盒子、淡绿底、主题色选中、无 emoji；
 * 所有 Text 都给显式 lineHeight（真机字体度量居中口径，已验证）。
 *
 * @param selectedDays 当前选中的日期集合（单选时最多一个）
 * @param onSelectionChange 选中集合变化回调；组件本身不持有业务状态
 * @param classDays 需要高亮成主题色的天（「需要上课的天」语义由调用方定）
 * @param multiSelect 是否允许多选
 * @param initialMonth 初始月份
 */
@Composable
fun CourseCalendar(
    selectedDays: Set<LocalDate>,
    onSelectionChange: (Set<LocalDate>) -> Unit,
    uiScale: Float,
    modifier: Modifier = Modifier,
    classDays: Set<LocalDate> = emptySet(),
    multiSelect: Boolean = true,
    initialMonth: YearMonth = YearMonth.now(),
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp

    var month by remember { mutableStateOf(initialMonth) }
    // false = 日视图，true = 月份网格
    var showMonthGrid by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s(16.dp)))
            .background(colors.surface.colorCard)
            .padding(s(12.dp)),
    ) {
        // ---- 顶栏：月份标题（可点切月历）+ 左右翻页 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!showMonthGrid) {
                MonthArrow("‹", uiScale) { month = month.minusMonths(1) }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(s(8.dp)))
                    .clickable { showMonthGrid = !showMonthGrid }
                    .padding(vertical = s(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showMonthGrid) "选择月份" else "${month.year}年${month.monthValue}月",
                    color = colors.brand.colorBrand,
                    fontSize = (11f * uiScale).sp,
                    lineHeight = (14f * uiScale).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (!showMonthGrid) {
                MonthArrow("›", uiScale) { month = month.plusMonths(1) }
            }
        }

        Spacer(Modifier.height(s(8.dp)))

        if (showMonthGrid) {
            MonthGrid(month, uiScale) { picked ->
                month = picked
                showMonthGrid = false
            }
        } else {
            DayGrid(
                month = month,
                selectedDays = selectedDays,
                classDays = classDays,
                multiSelect = multiSelect,
                uiScale = uiScale,
                onSelectionChange = onSelectionChange,
            )
        }
    }
}

@Composable
private fun MonthArrow(label: String, uiScale: Float, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Box(
        modifier = Modifier
            .size(s(28.dp))
            .clip(RoundedCornerShape(s(8.dp)))
            .background(colors.surface.colorBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.brand.colorBrand,
            fontSize = (14f * uiScale).sp,
            lineHeight = (16f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 12 个月份卡片，点一个切回该月日视图。 */
@Composable
private fun MonthGrid(current: YearMonth, uiScale: Float, onPick: (YearMonth) -> Unit) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Column(verticalArrangement = Arrangement.spacedBy(s(6.dp))) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                for (col in 0..3) {
                    val m = row * 4 + col + 1
                    val ym = YearMonth.of(current.year, m)
                    val isThis = ym == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(s(10.dp)))
                            .background(
                                if (isThis) colors.brand.colorBrand
                                else colors.brand.colorBrandMuted.copy(alpha = 0.5f),
                            )
                            .clickable { onPick(ym) }
                            .padding(vertical = s(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${m}月",
                            color = if (isThis) colors.brand.colorOnBrand
                            else colors.surface.colorText,
                            fontSize = (9f * uiScale).sp,
                            lineHeight = (12f * uiScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 当月日历网格：周一..周日表头 + 6 行日期。 */
@Composable
private fun DayGrid(
    month: YearMonth,
    selectedDays: Set<LocalDate>,
    classDays: Set<LocalDate>,
    multiSelect: Boolean,
    uiScale: Float,
    onSelectionChange: (Set<LocalDate>) -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(verticalArrangement = Arrangement.spacedBy(s(4.dp))) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { d ->
                Text(
                    text = d,
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.7f),
                    fontSize = (8f * uiScale).sp,
                    lineHeight = (11f * uiScale).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(s(2.dp)))

        // 该月 1 号是周几（周一=0）
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val cellCount = ((offset + daysInMonth + 6) / 7) * 7

        for (week in 0 until cellCount / 7) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s(3.dp)),
            ) {
                for (dow in 0..6) {
                    val idx = week * 7 + dow
                    val dayNum = idx - offset + 1
                    if (dayNum !in 1..daysInMonth) {
                        Box(modifier = Modifier.weight(1f).size(s(28.dp)))
                        continue
                    }
                    val date = month.atDay(dayNum)
                    val selected = date in selectedDays
                    val isClassDay = date in classDays
                    val bg = when {
                        selected -> colors.brand.colorBrand
                        isClassDay -> colors.brand.colorBrandMuted.copy(alpha = 0.8f)
                        else -> colors.surface.colorBackground
                    }
                    val fg = when {
                        selected -> colors.brand.colorOnBrand
                        isClassDay -> colors.brand.colorBrand
                        else -> colors.surface.colorTextSecondary
                    }
                    DayCell(
                        label = "$dayNum",
                        bg = bg,
                        fg = fg,
                        uiScale = uiScale,
                        modifier = Modifier.weight(1f),
                    ) {
                        onSelectionChange(
                            if (multiSelect) {
                                if (selected) selectedDays - date else selectedDays + date
                            } else {
                                if (selected) emptySet() else setOf(date)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    uiScale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Box(
        modifier = modifier
            .size(s(28.dp))
            .clip(RoundedCornerShape(s(8.dp)))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = (9f * uiScale).sp,
            lineHeight = (12f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
