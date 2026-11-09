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
private const val PERIOD_COUNT = 10

/** 学期总周数（需求 9 的「共几周」）。 */
private const val TOTAL_WEEKS = 16

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

/** 第 [period] 节**之后**的纵向间距。period 是前一节编号（1..9 才有意义）。 */
private fun gapAfter(period: Int): Dp = when (period) {
    // 午休 4→5、晚饭 8→9：最长
    4, 8 -> GAP_MEAL
    // 20 分的大课间：2→3、6→7
    2, 6 -> GAP_LONG
    // 其余都是 10 分的常规课间
    else -> GAP_NORMAL
}

/** 圆角（维护者第 6 条要圆角矩形）。 */
private val CELL_CORNER = 10.dp
private val HEADER_CORNER = 12.dp

/**
 * 节次作息，照抄旧农屿 `apps/nongyu-rn-app/src/modules/course/model/courseTimes.ts`（川农常见作息）。
 * 索引 0 对应第 1 节。
 */
private val COURSE_TIMES = listOf(
    "08:10" to "08:55",
    "09:05" to "09:50",
    "10:10" to "10:55",
    "11:05" to "11:50",
    "14:20" to "15:05",
    "15:15" to "16:00",
    "16:20" to "17:05",
    "17:15" to "18:00",
    "19:30" to "20:15",
    "20:25" to "21:10",
)

private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

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
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = startTime,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.80f),
                                fontSize = (6.5f * uiScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = period.toString(),
                                color = colors.surface.colorText,
                                fontSize = (11f * uiScale).sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = endTime,
                                color = colors.surface.colorTextSecondary.copy(alpha = 0.80f),
                                fontSize = (6.5f * uiScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
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
