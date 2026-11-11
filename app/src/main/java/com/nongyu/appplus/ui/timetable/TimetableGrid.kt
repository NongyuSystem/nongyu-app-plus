package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * 左侧**节次时间列可见盒子**相对列宽的比例（表头不受影响）。
 *
 * 维护者要求「第一列的课表时间变窄，表头不变，且定住左侧的头」——
 * 即只往右收、左边缘与表头盒严格对齐、7 个课程列起点不动。
 *
 * 实现用 `padding(end = leftColumnWidth * (1 - 该比例))` 收窄可见盒子，
 * 而不是改 Column 的基准宽度：那个宽度是右侧课程区的起点基准，
 * 真改会让课程列整体左移（就是上一轮修好的 13px 错位）。
 *
 * 0.82 = 时间盒可见宽度取列宽的 82%（收窄约 7dp，40dp → 32.8dp），
 * 仍能竖排放下「08:10 / 1 / 08:55」三行（最宽的 08:10 用 6.5sp）。
 */
private const val PERIOD_BOX_WIDTH_RATIO = 0.82f

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
 * @param courses 要渲染的课程块列表（已按天分组前的原始列表）。
 *   第 2 步（#13）起由这里接收数据；缺省为空，表格骨架仍可单独使用。
 */
@Composable
fun TimetableGrid(
    uiScale: Float,
    modifier: Modifier = Modifier,
    currentWeek: Int = 1,
    courses: List<CourseEntry> = emptyList(),
    /** 非 null = 按天模式，只渲染这一天（0..6），该列占满课程区宽度。 */
    onlyDay: Int? = null,
    /** 用户新增的日程（画成与课程卡类似的卡片）。 */
    schedules: List<ScheduleEntry> = emptyList(),
    onCourseClick: (CourseEntry) -> Unit = {},
    /** 点空白格：(day 0..6, period 1..10)。 */
    onBlankSlotClick: (day: Int, period: Int) -> Unit = { _, _ -> },
    /** 点日程卡 / 下拉横杠：开或关该日程的展开态。 */
    onScheduleClick: (ScheduleEntry) -> Unit = {},
    /**
     * 入场：课程卡出现进度 0..1（票 #60 需求 4.3「课程盒子一个一个出现」）。
     * 每张卡按**起始节次**错峰，同一列越靠上的课越早出现。
     */
    cardAppear: Float = 1f,
    /**
     * 滑动动画的目标状态（周, 天）。变化时**只让右侧课程区**左右滑动；
     * 左侧节次列与顶部表头保持不动（表头文字原地更新）。
     * 见 TimetableScreen 的调用点。
     */
    pageKey: Pair<Int, Int> = 0 to -1,
    /**
     * 上报「节次行的真实屏幕范围」（top, height，像素）。
     *
     * 时间条必须与这段区域严格对齐，而这段区域的位置受状态栏、工具条预留、
     * 表头、各种 padding 共同影响 —— 手算公式已经错过两次（漏底栏、漏工具条）。
     * 所以让节次列自己把实测值报出去，外部按它定位，从根上消掉算错的可能。
     */
    onPeriodColumnBounds: (topPx: Float, heightPx: Float) -> Unit = { _, _ -> },
    /**
     * 点左上角「第N周 / 共N周」表头：打开跳周（周模式）或跳天（天模式）的日历。
     *
     * 维护者要求：这个表头是**快速跳转入口**，点开已有日历直接选日期过去，
     * 比一周一周滑动快得多。
     */
    onWeekHeaderClick: () -> Unit = {},
) {
    val coursesByDay = remember(courses) { courses.groupedByDay() }
    // 课程名 → 色槽：保证**同屏课程必然不同色**（撞槽自动顺延），
    // 且结果只取决于课程名集合，与渲染顺序无关。
    val hueSlots = remember(courses) { CoursePalette.assignHueSlots(courses.map { it.name }) }
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
    // 表头「星期 / 日期」两行也必须用同一套行高裁切。
    //
    // 维护者反馈：真机上日期贴着盒子底部、不居中，而左上角「第N周/共N周」正常。
    // 根因是左上角两行写了显式 lineHeight，这里没写 —— Text 会用字体默认的
    // ascent/descent + 字体自带上下留白，不同机型字体度量不同，
    // 真机上日期行 descent 偏大，整行被往下推到贴底；蓝叠上恰好居中所以没暴露。
    // 统一成 Trim.Both + 显式 lineHeight 后，行高裁到字形边界，上下留白严格对称。
    val headerTextStyle = androidx.compose.ui.text.TextStyle(
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
    )
    val outerPadding = s(OUTER_PADDING)
    val cellCorner = s(CELL_CORNER)
    val headerCorner = s(HEADER_CORNER)

    // 「今天」= 系统当天的星期索引（0..6，周一为 0）。
    val todayIndex = remember {
        val today = LocalDate.now()
        ChronoUnit.DAYS.between(today.with(DayOfWeek.MONDAY), today).toInt().takeIf { it in 0..6 } ?: -1
    }
    // 本学期「本周」的周一（用于判断当前显示的是不是本周）。
    val thisWeekMonday = remember { LocalDate.now().with(DayOfWeek.MONDAY) }
    // 显示这一周的周一：跟着 currentWeek 走，日期才会随切周变化。
    // 之前写死 LocalDate.now()，导致切到别的周时表头日期不变、
    // 而且「今天」在别的周也照样高亮（维护者实测反馈）。
    val mondayOfWeek = remember(currentWeek) {
        thisWeekMonday.plusWeeks((currentWeek - weekOfTerm(LocalDate.now())).toLong())
    }
    // 只有「显示的这一周」就是本周时，才把今天高亮；切到别的周不高亮任何一天。
    val showingThisWeek = currentWeek == weekOfTerm(LocalDate.now())

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
            //
            // 注意：维护者要求「变窄的是下面的时间列，表头保持不变」，
            // 所以这里**不做任何收窄**，保持与时间列的基准宽度一致。
            Box(
                modifier = Modifier
                    .width(leftColumnWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(headerCorner))
                    .background(timeColStart)
                    // 点「第N周 / 共N周」打开日历快速跳转
                    .clickable(onClick = onWeekHeaderClick),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 第一行「第N周」加粗、字号略大；第二行「共N周」小字号弱化。
                    // 层级靠字号与字重表达，不靠把文字调浅到看不清（对比度会掉）。
                    //
                    // 字号从 9.5sp 收到 8.5sp：维护者反馈第一行偏大。
                    // 两行之间显式留 spacingBetween，不能再让它们紧贴——
                    // 之前 lineHeight 恰好抵掉，两行边界完全相接（实测 271/271），
                    // 视觉上挤成一团。
                    Text(
                        text = weekLabelCn(currentWeek),
                        color = colors.brand.colorBrand,
                        fontSize = (8.5f * uiScale).sp,
                        lineHeight = (10f * uiScale).sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                    // 行间距与「周一/周二」之间的水平间隔取同一个值（GRID_GAP），
                    // 这样两行看起来像同一套网格排出来的，而不是随手定的间距。
                    Spacer(modifier = Modifier.height(gap))
                    Text(
                        text = totalWeeksLabelCn(TOTAL_WEEKS),
                        color = colors.surface.colorTextSecondary.copy(alpha = 0.80f),
                        fontSize = (6.5f * uiScale).sp,
                        lineHeight = (8f * uiScale).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 七天：每天都独占一个圆角盒子，盒子之间用 Arrangement.spacedBy 隔开。
            // 表头与网格必须用**同一份**要渲染的天，否则按天模式会「表头七天、网格一天」，
            // 列宽也对不上（实测天模式下课程卡消失、七天表头还在）。
            val headerDays = onlyDay?.let { listOf(it) } ?: WEEKDAY_LABELS.indices.toList()
            headerDays.forEach { day ->
                val label = WEEKDAY_LABELS[day]
                // 只有「显示的是本周」时，今天才高亮；切到别的周一律不高亮。
                val isToday = showingThisWeek && day == todayIndex
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
                            lineHeight = (11f * uiScale).sp,
                            style = headerTextStyle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                        )
                        // 两行之间加一点空隙：与左上角「第N周/共N周」、以及
                        // 周一/周二之间的水平间隔取同一个 GRID_GAP，
                        // 看起来像同一套网格排出来的。
                        // 之前两行边界完全相接（实测 272/272），视觉上挤成一团。
                        Spacer(modifier = Modifier.height(gap))
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            color = if (isToday) colors.brand.colorBrand
                            else colors.surface.colorTextSecondary,
                            fontSize = (7f * uiScale).sp,
                            lineHeight = (9f * uiScale).sp,
                            style = headerTextStyle,
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

        // ---------- 网格主体 ----------
        //
        // 结构上分左右两块，各自负责不同的东西：
        // - 左侧时间列：逐行渲染，行高与行间距决定「整张表的时间标尺」；
        // - 右侧课程区：**按天分列**，每列内部按课程块垂直排布。
        //
        // 为什么右侧不能继续逐行渲染：
        // 需求 14 要求大课（如 123456 连堂）的卡片「一整个连在一起」。
        // 逐行渲染时每个格子互相独立，跨节只能靠横向合并格子实现，
        // 而 Compose 的 Row 里没有「跨行」概念 —— 必须换成「每列一个 Column，
        // 块用 weight(spanRows) 占多份高度」的排法，块天然跨行。
        //
        // 两侧如何对齐：课程区用与时间列**完全相同的行结构**（10 个 weight(1f)
        // + 同样的三档间距），所以第 n 行的上下边界在两侧一致，卡片才能正对节次。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = outerPadding, end = outerPadding),
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // -------- 左：节次时间列（逐行）--------
                Column(
                    modifier = Modifier
                        .width(leftColumnWidth)
                        // 把节次行的真实屏幕范围报出去（时间条据此对齐，不再手算）
                        //
                        // 本仓的 Compose 版本没有 boundsInRoot/positionInRoot，
                        // 用 localToRoot(Offset.Zero) 取左上角绝对坐标，高度用 size.height。
                        .onGloballyPositioned { coords ->
                            val topLeft = coords.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                            onPeriodColumnBounds(topLeft.y, coords.size.height.toFloat())
                        },
                ) {
                    for (period in 1..PERIOD_COUNT) {
                        val time = COURSE_TIMES[period - 1]
                        val rowRatio = (period - 1).toFloat() / (PERIOD_COUNT - 1).toFloat()
                        val timeCellColor = blend(timeColStart, timeColEnd, rowRatio)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                // 时间列**可见盒子**单独收窄，但左侧边缘定住、7 个课程列起点不变。
                                //
                                // 用 end padding 而不是改 Column 的宽度：
                                // 时间列的宽度是右侧课程区的起点基准，真改宽度会让课程列整体左移
                                // （就是上一轮修好的那个错位）。
                                // 收窄后外层占用不变，只在视觉上让时间盒变窄、课程往左多一点空间。
                                .padding(end = leftColumnWidth * (1f - PERIOD_BOX_WIDTH_RATIO))
                                .clip(RoundedCornerShape(cellCorner))
                                .background(timeCellColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TimeRowText(time.start, 6.5f, uiScale, colors.surface.colorTextSecondary,
                                    timeTextStyle, Modifier.weight(1f))
                                TimeRowText(period.toString(), 11f, uiScale, colors.surface.colorText,
                                    timeTextStyle, Modifier.weight(1f), extraBold = true)
                                TimeRowText(time.end, 6.5f, uiScale, colors.surface.colorTextSecondary,
                                    timeTextStyle, Modifier.weight(1f))
                            }
                        }

                        if (period < PERIOD_COUNT) {
                            Box(modifier = Modifier.height(s(gapAfter(period))))
                        }
                    }
                }

                // -------- 右：课程区（按天分列）--------
                //
                // 这里必须补一个 `padding(start = gap)`，否则课程列会比表头列
                // 整体左移一个 gap、且每列宽一点。
                //
                // 原因是两行的「子节点数」不同，而 spacedBy 只在子节点之间插缝：
                // - 表头行有 8 个子节点（左上角 + 7 天）→ 7 个缝，第一天从 leftColumnWidth + gap 开始；
                // - 网格行只有 2 个子节点（时间列 + 课程区），课程区内部的 Row 只有 7 个列 → 6 个缝，
                //   第一天从 leftColumnWidth 开始。
                // 少掉的那一个缝就是把课程整体往左推出去了。补在这里，两边缝数一致，列宽与起点才相同。
                //
                // 时间列单元格**不再**加 `padding(end = gap)`：间距统一由这里给出，
                // 避免出现「时间列与第一天 = 2×gap、天与天之间 = gap」的不均匀。
                // 课程区：**只有这一块参与切周滑动**。
                //
                // 维护者要求：左侧节次列（1..10 节）与左上角第N周保持不变、
                // 顶部表头原地换文字，只有右侧 7 列课程区左右滑动。
                // 所以 AnimatedContent 只包住这 7 列，不含时间列与表头。
                AnimatedContent(
                    targetState = pageKey,
                    transitionSpec = {
                        val forward = targetState.first > initialState.first ||
                            (targetState.first == initialState.first && targetState.second > initialState.second)
                        val dir = if (forward) 1 else -1
                        (
                            slideInHorizontally(tween(240)) { full -> dir * full / 3 } + fadeIn(tween(200))
                            ).togetherWith(
                            slideOutHorizontally(tween(200)) { full -> -dir * full / 3 } + fadeOut(tween(140)),
                            )
                    },
                    label = "courseArea",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { _ ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = gap),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        // 按天模式只渲染选中那一天（该列 weight(1f) 自然占满整宽）
                        val daysToRender = onlyDay?.let { listOf(it) } ?: WEEKDAY_LABELS.indices.toList()
                        for (day in daysToRender) {
                            CourseDayColumn(
                                courses = coursesByDay[day],
                                uiScale = uiScale,
                                gapOf = { gapAfter(it) },
                                gap = gap,
                                cardCorner = cellCorner,
                                schedules = schedules.filter { it.day == day },
                                onCourseClick = onCourseClick,
                                onBlankSlotClick = { period -> onBlankSlotClick(day, period) },
                                onScheduleClick = onScheduleClick,
                                cardAppear = cardAppear,
                                slotOf = { name ->
                                    hueSlots[name] ?: CoursePalette.preferredSlot(name)
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
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

/**
 * 时间列里的一行文字（起始时间 / 节次号 / 结束时间共用）。
 *
 * 为什么传 weight 修饰符进来而不是在这里写死：
 * 三行要各占 1/3 槽高，但 weight 只能在 Column 的直接子项上生效，
 * 所以由调用方把 `Modifier.weight(1f)` 传进来；居中仍由外层 Box 的
 * contentAlignment 负责（直接给 Text 加 weight 会让字形贴槽底）。
 */
@Composable
private fun TimeRowText(
    text: String,
    sizeSp: Float,
    uiScale: Float,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    extraBold: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color,
            fontSize = (sizeSp * uiScale).sp,
            lineHeight = (sizeSp * 1.15f * uiScale).sp,
            fontWeight = if (extraBold) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            style = style,
        )
    }
}

/**
 * 一天的课程列：**按课程块垂直排布**，块可跨多行。
 *
 * ## 为什么不是逐格画
 * 需求 14：大课（如 123456 连堂）的卡片要「一整个连在一起」。
 * 逐格画时每个格子互相独立，无法跨行；这里改成「一天一个 Column，
 * 每个课程块占多份高度」，块天然跨行、天然连成一片。
 *
 * ## 怎么保证与左侧节次对齐（这里踩过两次坑）
 *
 * 左侧时间列结构是：`10 × 行(weight 1) + 9 × 间距(固定 dp)`。
 * 因此每天这一列的**纵向内容**必须严格复刻同一个骨架，
 * 否则第 n 行在两侧的 y 范围会逐步漂移。
 *
 * 做法：**不合并行**，而是逐行生成 10 个 `weight(1f)` 槽位（与左侧一一对应），
 * 相邻槽位之间插入与左侧**同一个** `gapOf(p)` 固定间距。
 * 课程卡片不在槽位内画，而是作为一个**覆盖层**叠在对应的若干槽位上：
 * - 用 `Modifier.weight(spanRows)` 在**独立的 Column** 里占高度是不行的（间距算不准）；
 * - 正确做法是用 `Layout`/`Box` 把卡片按「首节槽位顶边」到「末节槽位底边」定位。
 *
 * 这里采用最简单的可靠写法：先按骨架依次排出「行槽位 + 间距」作为占位，
 * 再用一个 `Box` 覆盖层，靠 `onGloballyPositioned` 记录每个节次槽位的 y 范围，
 * 把卡片绝对定位到 `[首节顶, 末节底]`。骨架与左侧完全一致 ⇒ 永不漂移。
 */
@Composable
private fun CourseDayColumn(
    courses: List<CourseEntry>,
    uiScale: Float,
    gapOf: (Int) -> Dp,
    gap: Dp,
    cardCorner: Dp,
    slotOf: (String) -> Int,
    /** 该天用户新增的日程（画成与课程卡类似的卡片）。 */
    schedules: List<ScheduleEntry> = emptyList(),
    onCourseClick: (CourseEntry) -> Unit = {},
    /** 点日程卡：开/关展开态。 */
    onScheduleClick: (ScheduleEntry) -> Unit = {},
    /** 点空白槽位：(period 1..10)。是否真的空白由本列判断后被占据的节次过滤。 */
    onBlankSlotClick: (period: Int) -> Unit = {},
    /** 入场：课程卡出现进度 0..1，按起始节次错峰（票 #60）。 */
    cardAppear: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors

    // 记录每个节次槽位在**本列内**的 y 顶/底（像素），用于把卡片精确定位
    val slotTops = remember { FloatArray(PERIOD_COUNT + 1) }
    val slotBottoms = remember { FloatArray(PERIOD_COUNT + 1) }
    var layoutVersion by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        // 哪些节次已经被课程/日程占据：只有**真空位**才响应「点击添加」，
        // 否则点课程卡片会同时触发添加（课程卡的 clickable 声明更靠后，
        // 但这里先从数据层挡掉，行为更确定）。
        val occupiedPeriods = remember(courses, schedules) {
            buildSet {
                courses.forEach { c -> (c.startPeriod..c.endPeriod).forEach { add(it) } }
                schedules.forEach { s -> (s.startPeriod..s.endPeriod).forEach { add(it) } }
            }
        }

        // ---- 骨架：与左侧时间列逐行一一对应 ----
        Column(modifier = Modifier.fillMaxSize()) {
            for (period in 1..PERIOD_COUNT) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (period !in occupiedPeriods) {
                                // 点击水波纹必须是圆角矩形（与课程卡同一套 cardCorner）。
                                // 默认 Ripple 只在 clip 之后才会被裁圆；所以先 clip 再挂 indication，
                                // 并且 interactionSource 要 remember，否则每次重组都新建、波纹会断。
                                val rippleSource = remember { MutableInteractionSource() }
                                Modifier
                                    .clip(RoundedCornerShape(cardCorner))
                                    .clickable(
                                        interactionSource = rippleSource,
                                        indication = androidx.compose.material3.ripple(
                                            bounded = true,
                                        ),
                                    ) { onBlankSlotClick(period) }
                            } else {
                                Modifier
                            },
                        )
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInParent()
                            slotTops[period] = b.top
                            slotBottoms[period] = b.bottom
                            // 最后一节测完后触发一次覆盖层重算
                            if (period == PERIOD_COUNT) layoutVersion++
                        },
                )
                if (period < PERIOD_COUNT) {
                    Box(modifier = Modifier.height(gapOf(period)))
                }
            }
        }

        // ---- 覆盖层：把每张卡片放到它覆盖的节次范围内 ----
        // layoutVersion 变化时重组，保证用上最新的槽位坐标
        if (layoutVersion >= 0) {
            courses.forEach { block ->
                val top = slotTops[block.startPeriod]
                val bottom = slotBottoms[block.endPeriod]
                if (bottom > top) {
                    // 入场：**水位线扫过就淡入**（维护者 2026-11-11 定案）。
                    // 注意仍然是 alpha 渐变，不是跳变 —— 水位只决定"这张卡什么时候开始淡入"。
                    //
                    // cardAppear 是水位线在本列的纵向比例：1 = 列底，0 = 列顶。
                    // 水位从底部往上升，漫过某张卡的底边时开始淡入、漫过顶边时完全显示。
                    // span 用卡片自身高度归一化，避免连堂大课的淡入被拉得过慢。
                    val columnHeightPx = (slotBottoms[PERIOD_COUNT]).coerceAtLeast(1f)
                    val cardBottomFrac = (bottom / columnHeightPx).coerceIn(0f, 1f)
                    val cardTopFrac = (top / columnHeightPx).coerceIn(0f, 1f)
                    val span = (cardBottomFrac - cardTopFrac).coerceAtLeast(0.06f)
                    val local = ((cardBottomFrac - cardAppear) / span).coerceIn(0f, 1f)
                    CourseCard(
                        block = block,
                        slot = slotOf(block.name),
                        onClick = { onCourseClick(block) },
                        uiScale = uiScale,
                        corner = cardCorner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = local }
                            .height(with(LocalDensity.current) { (bottom - top).toDp() })
                            // 用 offset 而非 padding：偏移量是像素算出来的绝对位置
                            .offset(y = with(LocalDensity.current) { top.toDp() }),
                    )
                }
            }
            // 用户新增的日程：与课程卡同一套定位，样式近似但用虚线感底色区分
            schedules.forEach { sc ->
                val top = slotTops[sc.startPeriod]
                val bottom = slotBottoms[sc.endPeriod]
                if (bottom > top) {
                    // 日程卡与课程卡同一套水位线淡入规则
                    val colH = slotBottoms[PERIOD_COUNT].coerceAtLeast(1f)
                    val bFrac = (bottom / colH).coerceIn(0f, 1f)
                    val tFrac = (top / colH).coerceIn(0f, 1f)
                    val scSpan = (bFrac - tFrac).coerceAtLeast(0.06f)
                    val scLocal = ((bFrac - cardAppear) / scSpan).coerceIn(0f, 1f)
                    ScheduleCard(
                        entry = sc,
                        uiScale = uiScale,
                        corner = cardCorner,
                        onClick = { onScheduleClick(sc) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = scLocal }
                            .height(with(LocalDensity.current) { (bottom - top).toDp() })
                            .offset(y = with(LocalDensity.current) { top.toDp() }),
                    )
                }
            }
        }
    }
}

/**
 * 用户新增的日程卡片：与课程卡同一套尺寸/圆角/居中排版，
 * 但底色用「品牌淡底 + 描边」以示区别（不是从教务来的课）。
 */
@Composable
private fun ScheduleCard(
    entry: ScheduleEntry,
    uiScale: Float,
    corner: Dp,
    /** 点卡片任意位置：打开/关闭日程详情态（维护者要求「只有开关两态」）。 */
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    val bg = colors.brand.colorBrandMuted.copy(alpha = 0.75f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(bg)
            // 点日程卡任意位置都算「操作这张卡」，由 Shell 决定开还是关
            .clickable { onClick() }
            .padding(horizontal = (4f * uiScale).dp, vertical = (2f * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 课表上的日程小卡**不画滑动杠**（维护者 2026-11-11：小格子太窄，
            // 杠子只保留在展开态里）。这里点一下即可打开展开态。
            Text(
                text = entry.title,
                color = colors.brand.colorBrand,
                fontSize = (8.5f * uiScale).sp,
                lineHeight = (10f * uiScale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            if (entry.location.isNotBlank()) {
                Text(
                    text = entry.location,
                    color = colors.brand.colorBrand.copy(alpha = 0.85f),
                    fontSize = (6.5f * uiScale).sp,
                    lineHeight = (8f * uiScale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 课程卡片：整卡单色、明显圆角、内容居中，显示课程名 + 地点（需求 3 / 4）。
 *
 * ## 颜色
 * 颜色**由课程名哈希决定**（[CoursePalette]），不是固定色板：
 * 同名课永远同色，加课删课、换周次都不会让已有课程换色。
 * 色相随名字变，饱和度与亮度全表统一，所以整体仍是一套糖果色而非随机取色。
 * 跨节大课的底色比单节稍深（同一色相），让连堂一眼可辨。
 *
 * ## 视觉口径
 * 来自本项目已验证过的经验：
 * - **不要左侧竖色条**（会让窄格显得零碎），整卡一个底色；
 * - 内容**居中**而不是顶部对齐，否则块一高就头重脚轻；
 * - 圆角要够大（12dp 量级）才看得出是「卡片」而不是「一块色」。
 */
@Composable
private fun CourseCard(
    block: CourseEntry,
    slot: Int,
    uiScale: Float,
    corner: Dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bg = Color(CoursePalette.backgroundArgb(slot, block.isSpanning))
    val fg = Color(CoursePalette.textArgb(slot))
    val fgSecondary = Color(CoursePalette.secondaryTextArgb(slot))

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = (4f * uiScale).dp, vertical = (3f * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = block.name,
                color = fg,
                fontSize = (8.5f * uiScale).sp,
                lineHeight = (10f * uiScale).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = if (block.isSpanning) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = block.room,
                color = fgSecondary,
                fontSize = (6.5f * uiScale).sp,
                lineHeight = (8f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
