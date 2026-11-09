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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 课表页容器（第 1 步，票 #15，维护者第二轮修订）。
 *
 * 职责：算 `uiScale`、算状态栏避让、算底部导航栏避让、把布局铺满剩下的可视区。
 * 顶部为右上角 4 个按钮预留的高度由 [TimetableGrid] 自己让出。
 *
 * ## 为什么底栏高度必须实测，不能写死
 * 底部导航栏是自绘的（`MainShell.kt`，非 Scaffold）。它在屏幕上占的 = 竖直内边距 × 2 + 栏高，
 * 还要再加中间星云球的凸起量；这些值**全部乘了 `uiScale`**。写死一个 dp 数在不同屏宽下必错，
 * 而错了的表现就是「最下面一行被导航栏盖住」（需求 15）。
 *
 * @param navTopY 底栏顶边在根坐标系中的 y（像素）。`null` = 尚未测量完（首帧），
 *   此时先按整屏铺满，下一帧再收缩。
 */
@Composable
fun TimetableScreen(
    navTopY: Float?,
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

        // 可用高度 = 底栏顶边 - 上段（状态栏 + 呼吸位）- 下段呼吸位。
        // 全部换算成同一坐标系（px）再折回 Dp，换设备 / 换 uiScale 自动正确。
        val bottomBreath = with(density) { (BOTTOM_BREATH * uiScale).toPx() }
        val availableHeight: Dp = if (navTopY != null && navTopY > 0f) {
            with(density) { (navTopY - bottomBreath).coerceAtLeast(0f).toDp() }
        } else {
            (maxHeight - statusBarPadding).coerceAtLeast(0.dp)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = statusBarPadding)
                .height(availableHeight),
        ) {
            TimetableGrid(
                uiScale = uiScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 布局底部与导航栏之间额外让出的呼吸位（维护者第 3 条）。
 * 维护者看完首版后要求「再多 0.5」，故由 14dp 提到 21dp（+50%）：
 * 实测第 10 行下沿与导航栏之间原为 123px，视觉上偏贴。
 */
private val BOTTOM_BREATH = 21.dp
