package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 课表「表格布局」——第 1 步（票 #15，维护者第二轮修订后）。
 *
 * 注意用词：这是**布局**，不是「表格」——**不画任何网格线**（维护者第 4 条要求）。
 * 分栏与分行只靠格子之间的间隔、以及每行/每列自带的小圆角矩形来区分。
 *
 * 维护者七条修订：
 * 1. 顶部留白抬高，给右上角 4 个按钮（设置 / 更新课表 / 切换日周 / 添加日程）留位置。
 * 2. 第一列加宽，每节显示「起始时间 / 节次号 / 结束时间」竖排（照抄旧农屿 `WeekGrid.tsx`）。
 * 3. 上下左右都留出空隙。
 * 4. **不画表格线**。
 * 5. 今天的颜色再淡一点。
 * 6. 星期日期与左侧时间列都改成**圆角矩形盒子**，彼此之间有间隔、不贴在一起；
 *    日期盒用淡绿，时间列用**淡绿 → 绿黄渐变**，且「一个圆角矩形用一个色」。
 * 7. 字体加粗。
 *
 * 结构约束（来自本项目已踩过的坑）：
 * 表头行与网格行都必须用「固定左侧列 + 7 × weight(1f)」同一基准，左侧占位宽度必须完全相等，
 * 否则表头会与每天的列整体错位一位。
 */
/*
 * 节次 / 周数 / 作息 / 星期名的**唯一真源**在 TimetableData.kt。
 * 这里不再重复定义 —— 之前两处各写一份，改一处就得记得改另一处，迟早不同步。
 */

/**
 * 节次列宽度。
 * 旧农屿 `WeekGrid.tsx` 的 `TIME_COL_WIDTH = 36`，这里取 40dp：
 * 维护者第 2 条要「加宽一点」，且要能竖排放下「08:10 / 1 / 08:55」三行。
 */
private val PERIOD_COLUMN_WIDTH = 40.dp

/** 表头（星期）行高。 */
private val HEADER_ROW_HEIGHT = 42.dp

/**
 * 顶部为右上角 4 个按钮预留的高度（维护者第 1 条）。
 * hotfix：维护者反馈「留的位置太少」，由 44dp 提到 66dp（+50%）。
 */
private val TOOLBAR_RESERVE_HEIGHT = 66.dp

/** 格子之间、盒子与边缘之间的呼吸间隔（维护者第 3、6 条）。 */
private val GRID_GAP = 4.dp
private val OUTER_PADDING = 10.dp

/**
 * 纵向间距：按「第 p 节**之后**」的空档长度取三档 dp。
 *
 * 依据旧农屿作息（`courseTimes.ts`）算出的真实空档，只有三种长度：
 * | 空档 | 分钟 | dp |
 * | --- | --- | --- |
 * | 1→2、3→4、5→6、7→8、9→10 | 10 | [GAP_NORMAL] |
 * | 2→3、6→7 | 20 | [GAP_LONG] |
 * | **4→5（午休 150 分）、8→9（晚饭 90 分）** | 长 | [GAP_MEAL] |
 *
 * 注意取值方向：`gapAfter(period)` 里的 period 是**前一节**的编号，
 * 上一版把 1↔2 判成大间距、2↔3 判成小间距，方向写反，整列节奏才显得乱。
 */
private val GAP_NORMAL = 3.dp
private val GAP_LONG = 6.dp
private val GAP_MEAL = 10.dp

/**
 * 第 [period] 节**之后**的纵向间距（period 是前一节编号，1..9 才有意义）。
 *
 * 判定「哪一档」交给数据层 [gapKindAfter]（唯一真源，可单测）；
 * 这里只负责把档位映射成 dp。之前是两边各写一份 when，
 * 改一处就会不同步。
 */
private fun gapAfter(period: Int): Dp = when (gapKindAfter(period)) {
    RowGapKind.Meal -> GAP_MEAL
    RowGapKind.Long -> GAP_LONG
    RowGapKind.Normal -> GAP_NORMAL
}

/**
 * 时间列三行之间的固定行距。
 *
 * 用固定 dp 而不是 `Arrangement.Center` 的自然堆叠：
 * 自然堆叠下三行的行高由各自字号决定（6.5 / 11 / 6.5sp），
 * 块的重心会偏移，表现为「首行离盒顶远、末行离盒底近」。
 * 固定行距 + 显式 lineHeight 后，上下留白严格对称。
 */
private val TIME_LINE_GAP = 1.dp

/** 圆角（维护者第 6 条要圆角矩形）。 */
private val CELL_CORNER = 10.dp
private val HEADER_CORNER = 12.dp

/*
 * COURSE_TIMES / WEEKDAY_LABELS 见 TimetableData.kt（唯一真源）。
 */

/**
 * @param uiScale 屏宽自适应系数，由 MainShell 以 411dp 为基准算出（与导航栏同一套）。
 * @param currentWeek 当前显示第几周。**暂固定为 1**：学期起始日属于课表数据模型（票 #12 未拍板）。
 * @param cellContent 格子内容槽位。第 2 步（#13）往这里放课程卡片。
 */
@Composable
fun TimetableGrid(
    uiScale: Float,
    modifier: Modifier = Modifier,
    currentWeek: Int = 1,
    cellContent: @Composable (day: Int, period: Int) -> Unit = { _, _ -> },
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp

    val leftColumnWidth = s(PERIOD_COLUMN_WIDTH)
    val headerRowHeight = s(HEADER_ROW_HEIGHT)
    val gap = s(GRID_GAP)
    val timeLineGap = s(TIME_LINE_GAP)

    // 时间列三行共用同一份「行高裁切」策略。
    //
    // 为什么需要它：Text 默认行高 = 字号 × 字体默认倍数，且会在字形上下留白。
    // 三行字号不同（6.5 / 11 / 6.5sp），留白量也不同，实测 11sp 的节次号
    // 节点高 33px 而 6.5sp 的时间行只有 19px —— 结果三行的视觉间距里外不均
    // （首行→节次号 22px，节次号→末行仅 3px），看上去就是「上下不对称」。
    // Trim.Both 会把行高裁到字形边界，配合显式 lineHeight 让三行真正等高。
    val timeTextStyle = androidx.compose.ui.text.TextStyle(
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
    )
    val outerPadding = s(OUTER_PADDING)
    val cellCorner = s(CELL_CORNER)
    val headerCorner = s(HEADER_CORNER)

    // 今天 = 系统当天；日期与「第几周」的数据模型无关，等 #12 定口径后再参与周次推导。
    val todayIndex = remember {
        val today = LocalDate.now()
        ChronoUnit.DAYS.between(today.with(DayOfWeek.MONDAY), today).toInt().takeIf { it in 0..6 } ?: -1
    }
    val mondayOfWeek = remember { LocalDate.now().with(DayOfWeek.MONDAY) }

    // 维护者第 6 条：
    // - 日期盒：淡淡的品牌绿；今天是更明显一点的淡绿（第 5 条「再淡一点」——比旧版整列铺底淡得多）。
    // - 时间列：淡绿 → 绿黄渐变，且**一格一个纯色**（所以每格从渐变中按位置取一个色值，不跨格铺渐变）。
    val dateTint = colors.brand.colorBrand.copy(alpha = 0.06f)
    val todayTint = colors.brand.colorBrand.copy(alpha = 0.13f)
    val brandGreen = colors.brand.colorBrand
    val greenYellow = Color(0xFFB9CE3F)

    // 时间列渐变的起止色（淡绿 → 绿黄），按行位置在两者之间取色。
    val timeColStart = blend(colors.surface.colorCard, brandGreen, 0.10f)
    val timeColEnd = blend(colors.surface.colorCard, greenYellow, 0.34f)

    Column(modifier = modifier.fillMaxSize()) {
        // ---------- 顶部：给右上角 4 个按钮留位置（维护者第 1 条） ----------
        Box(modifier = Modifier.fillMaxWidth().height(s(TOOLBAR_RESERVE_HEIGHT)))

        // ---------- 表头行：星期 / 日期都做成独立圆角盒子（维护者第 6 条） ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerRowHeight)
                .padding(start = outerPadding, end = outerPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            // 左上角：第 N 周 / 共 M 周。宽度与网格节次列完全相等（不加间隔，靠 Row 的 spacedBy 统一给）。
            Box(
                modifier = Modifier
                    .width(leftColumnWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(headerCorner))
                    .background(timeColStart),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "$currentWeek/$TOTAL_WEEKS",
                        color = colors.brand.colorBrand,
                        fontSize = (9f * uiScale).sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "周",
                        color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
                        fontSize = (7f * uiScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 七天：每天都独占一个圆角盒子，盒子之间用 Arrangement.spacedBy 隔开。
            WEEKDAY_LABELS.forEachIndexed { day, label ->
                val isToday = day == todayIndex
                val date = mondayOfWeek.plusDays(day.toLong())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(headerCorner))
                        .background(if (isToday) todayTint else dateTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isToday) colors.brand.colorBrand else colors.surface.colorText,
                            fontSize = (9f * uiScale).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            color = if (isToday) colors.brand.colorBrand
                            else colors.surface.colorTextSecondary,
                            fontSize = (7f * uiScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // 表头与网格之间也留一道缝（维护者第 3、6 条）
        Box(modifier = Modifier.height(gap))

        // ---------- 网格主体：10 节逐行排布，行间用三档间距 ----------
        // 间距节奏（维护者 hotfix）：组内最紧 < 普通组界 < 午休/晚饭。
        // 旧农屿 `courseTimes.ts` 的 `MAJOR_SLOT_COUNT = 5` 提供了分组口径。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = outerPadding, end = outerPadding),
        ) {
            for (period in 1..PERIOD_COUNT) {
                val (startTime, endTime) = COURSE_TIMES[period - 1]
                // 时间列每格一个纯色：在「淡绿 → 绿黄」之间按节次位置取色，
                // 整列形成渐变，但单格内部是纯色（维护者第 6 条「一个圆角矩形用一个色」）。
                val rowRatio = (period - 1).toFloat() / (PERIOD_COUNT - 1).toFloat()
                val timeCellColor = blend(timeColStart, timeColEnd, rowRatio)

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // 节次时间列：起始时间 / 节次号 / 结束时间，竖排（照抄旧农屿 timeSlot 结构）
                    //
                    // 为什么三行都要显式 lineHeight：
                    // Text 的默认行高 = 字号 × 字体默认倍数，三行字号不同（6.5 / 11 / 6.5sp），
                    // 行高就各不相同，视觉块上下不对称 —— 实测出现过
                    // 「首行到盒顶 24px、末行到盒底 3px」这种肉眼可见的偏斜。
                    // 统一用 lineHeight = 字号的 1.15 倍（贴近紧凑排版），三行等高、重心居中。
                    Box(
                        modifier = Modifier
                            .width(leftColumnWidth)
                            .fillMaxHeight()
                            .padding(end = gap)
                            .clip(RoundedCornerShape(cellCorner))
                            .background(timeCellColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // 三行"各占一份等高槽位"：这是让上下留白对称的关键。
                            //
                            // 为什么不用 Arrangement.Center / spacedBy：
                            // Text 的布局盒高由字号与字体度量决定，三行字号不同
                            // （6.5 / 11 / 6.5sp），实测节次号盒高 33px、时间行 19px。
                            // 自然堆叠时盒子内部的间隔是「首行→节次号 22px、节次号→末行 3px」，
                            // 看上去就是上松下紧、与盒子边缘的距离不相等。
                            // 用 weight 均分后每行独占一份高度，三行间距由布局确定，不再受字体度量影响。
                        ) {
                            Text(
                                text = startTime,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.80f),
                                fontSize = (6.5f * uiScale).sp,
                                lineHeight = (7.5f * uiScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                style = timeTextStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                            Text(
                                text = period.toString(),
                                color = colors.surface.colorText,
                                fontSize = (11f * uiScale).sp,
                                lineHeight = (11f * uiScale).sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                style = timeTextStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                            Text(
                                text = endTime,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.80f),
                                fontSize = (6.5f * uiScale).sp,
                                lineHeight = (7.5f * uiScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                style = timeTextStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                        }
                    }

                    // 7 天格子：透明占位，无底色、无表格线（维护者第 4 条）
                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        for (day in 0..WEEKDAY_LABELS.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                // 第 1 步这里为空；第 2 步（#13）由调用方填课程卡片。
                                cellContent(day, period)
                            }
                        }
                    }
                }

                // 行与行之间：按「第 period 节**之后**」的真实空档取间距。
                if (period < PERIOD_COUNT) {
                    Box(modifier = Modifier.height(s(gapAfter(period))))
                }
            }
        }

        // 底部呼吸位
        Box(modifier = Modifier.height(outerPadding))
    }
}

/** 在 [from] 与 [to] 之间按 [ratio]（0..1）线性混色，返回不透明纯色。 */
private fun blend(from: Color, to: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * r,
        green = from.green + (to.green - from.green) * r,
        blue = from.blue + (to.blue - from.blue) * r,
        alpha = 1f,
    )
}
