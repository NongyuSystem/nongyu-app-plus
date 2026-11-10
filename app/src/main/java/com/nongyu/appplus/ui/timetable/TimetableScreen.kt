package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import java.time.LocalDate
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 课表页容器（票 #15）。
 *
 * 职责三件：算 `uiScale`、让出底部导航栏的占位、把布局铺满剩下的可视区。
 *
 * ## 底部避让为什么用「让出高度」而不是「按顶边坐标换算」
 *
 * 底部导航栏是自绘的（`MainShell.kt`，非 Scaffold）。一开始的做法是让 Shell 上报
 * 底栏**顶边 y 坐标**，再由本页算可用高度。这条路线**连续踩了两次坑**：
 * 1. 用 `positionInRoot` 取到的是 padding 之内的内容区，比真实顶边低了内边距，
 *    少让出空间 → 最后一行被导航栏压住（维护者在真机上看到的正是这个）；
 * 2. 改用 `boundsInRoot` 后含了 padding，但换算时又叠加了状态栏高度，
 *    变成多让出 → 底部空出一大块。
 *
 * 根因是「顶边坐标 + padding + 内容偏移」三者在不同坐标系里叠加，很容易错一次。
 * 现在改为让 Shell 直接上报**底栏节点自身的高度**（[navOccupiedPx]），
 * 本页只需让出这个高度，**不做任何坐标换算**，从结构上消除这类错误。
 *
 * @param navOccupiedPx 底栏占位高度（像素）。`null` = 首帧尚未测量完，
 *   此时先按「无底栏」铺满，下一帧马上修正（不会闪出明显错误，因为通常在首帧内测完）。
 */
@Composable
fun TimetableScreen(
    navOccupiedPx: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground),
    ) {
        // 与 MainShell 完全同一套：以 411dp 为基准，夹在 0.85~1.25。
        val uiScale = (maxWidth.value / 411f).coerceIn(0.85f, 1.25f)
        val density = LocalDensity.current

        // 状态栏避让：MainActivity 开了 enableEdgeToEdge，内容顶到屏幕最上沿，
        // 不扣掉状态栏高度的话，顶部内容会被状态栏文字压住（实测表头 y=0..56 重叠）。
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        val bottomOccupied: Dp = if (navOccupiedPx != null && navOccupiedPx > 0f) {
            with(density) { navOccupiedPx.toDp() }
        } else {
            0.dp
        }

        // 表格占用「屏幕高 - 状态栏 - 底栏占位 - 底部呼吸位」。
        // 末项是视觉留白，避免最后一行贴着导航栏。
        val breathPx = with(density) { (BOTTOM_BREATH * uiScale).toPx() }
        val gridHeight: Dp = if (navOccupiedPx != null && navOccupiedPx > 0f) {
            with(density) {
                (constraints.maxHeight.toFloat() - statusBarPadding.toPx() - navOccupiedPx - breathPx)
                    .coerceAtLeast(0f)
                    .toDp()
            }
        } else {
            (maxHeight - statusBarPadding).coerceAtLeast(0.dp)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = statusBarPadding)
                .height(gridHeight),
        ) {
            // 切周链路（票 #12 第 3/5/6 条）：
            //   今天 → 学期第几周 → 按周过滤 → 合并连堂 → 按天分组渲染。
            //
            // 每个环节都是纯函数、各自有单测，所以这里只做组装，不写逻辑。
            // 学期首日目前是 mock 常量，将来从教务/设置注入时只需换这一处入参。
            val today = remember { LocalDate.now() }
            val currentWeek = remember(today) { weekOfTerm(today) }
            val weekCourses = remember(currentWeek) {
                MOCK_COURSES.coursesInWeek(currentWeek).mergeAdjacent()
            }

            TimetableGrid(
                uiScale = uiScale,
                currentWeek = currentWeek,
                courses = weekCourses,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 表格最底部与导航栏之间额外留出的呼吸位（视觉留白，不参与避让计算）。
 *
 * 维护者要求「多一点间隙」，由 10dp 提到 20dp（翻倍）。
 * 这个值只影响观感：避让本身由 [navOccupiedPx] 保证，所以调大它不会重新引发遮挡，
 * 只会在窄屏上略微压缩每行高度。
 */
private val BOTTOM_BREATH = 20.dp
