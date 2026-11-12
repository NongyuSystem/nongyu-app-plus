package com.nongyu.appplus.shell.bar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.icon.NavIconGeometry
import com.nongyu.appplus.icon.drawNavIcon
import com.nongyu.appplus.skin.NavIconKey

/** 四个 Tab 的文案（与 `MainShell` 的路由索引一一对应）。 */
internal val TAB_LABELS: List<String> = listOf("全部", "课表", "探索", "个人")

/**
 * 底部条的**常态导航行**（`BarMode.Normal` 分支）：2+2 分列 + 中间留空给凸起的星云球。
 *
 * 从 `MainShell` 原样搬出，行为一行未改。中间那个 `Spacer(96dp)` 是给星云球让位的，
 * 宽度与球径（78dp 基值）耦合，改球径时要一起看。
 *
 * @param navAlpha 导航项淡出系数（输入态/详情态时容器不变、只有导航项淡出）
 * @param iconFillProgress 课表 Tab 的图标自下而上变绿进度（票 #60 需求 4.1）
 */
@Composable
fun NavRowContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    navAlpha: Float,
    iconFillProgress: Float,
    itemColor: Color,
    indicatorColor: Color,
    iconSize: Dp,
    labelSize: TextUnit,
    iconStrokes: Map<NavIconKey, Float>,
    s: (Dp) -> Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            // 导航项自己承担淡出：输入态/详情态时 navAlpha=0，
            // 但它们住在同一个容器里，不能让容器整体透明。
            .alpha(navAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(TAB_LABELS[0], NavIconKey.Grid, selectedTab == 0, itemColor, indicatorColor, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Grid), Modifier.weight(1f)) { onTabSelected(0) }
        NavItem(TAB_LABELS[1], NavIconKey.Calendar, selectedTab == 1, itemColor, indicatorColor, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Calendar), Modifier.weight(1f), fillProgress = if (selectedTab == 1) iconFillProgress else 0f) { onTabSelected(1) }
        Spacer(modifier = Modifier.width(s(96.dp)))
        NavItem(TAB_LABELS[2], NavIconKey.Compass, selectedTab == 2, itemColor, indicatorColor, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Compass), Modifier.weight(1f)) { onTabSelected(2) }
        NavItem(TAB_LABELS[3], NavIconKey.Person, selectedTab == 3, itemColor, indicatorColor, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Person), Modifier.weight(1f)) { onTabSelected(3) }
    }
}

/**
 * 单个导航项：图标 + 文案。
 * 尺寸：图标 26.dp（原 20.dp 偏小），文字 9.sp（原 10.sp 偏大）。
 * 选中态：图标与文字都用品牌色，未选中用低透明度中性色。
 */
@Composable
private fun NavItem(
    label: String,
    icon: NavIconKey,
    selected: Boolean,
    itemColor: Color,
    indicatorColor: Color,
    iconSize: Dp,
    labelSize: TextUnit,
    iconStroke: Float,
    modifier: Modifier = Modifier,
    /** 入场动画：图标从底部往上变绿的进度 0..1（票 #60）。默认 0f，只有课表 Tab 会传。 */
    fillProgress: Float = 0f,
    onClick: () -> Unit,
) {
    val tint = if (selected) indicatorColor else itemColor
    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
        // 整体下移：导航栏本身是垂直居中，加一点顶部内边距把图标+文字压向下方。
        // 偏移量随图标尺寸等比，避免不同 uiScale 下偏得不一样。
        verticalArrangement = Arrangement.Center,
    ) {
        // 整体轻微下移；幅度取图标尺寸的 0.07（上一版 0.16 明显过头）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (iconSize.value * 0.07f).dp),
        ) {
            Box(modifier = Modifier.size(iconSize)) {
                Canvas(modifier = Modifier.size(iconSize)) {
                    drawNavIcon(
                        geometry = NavIconGeometry.builtInOf(icon),
                        tint = tint,
                        strokeScale = iconStroke,
                    )
                }
                // 票 #60 需求 4.1：从底部往上变绿。绿色层按 fillProgress 裁切。
                if (fillProgress > 0f && selected) {
                    Canvas(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(BottomUpReveal(fillProgress)),
                    ) {
                        drawNavIcon(
                            geometry = NavIconGeometry.builtInOf(icon),
                            tint = indicatorColor,
                            strokeScale = iconStroke,
                        )
                    }
                }
            }
            // 图标与文字继续靠近：不留 Spacer，用压缩行高让文字紧贴图标下缘
            Text(
                text = label,
                color = tint,
                fontSize = labelSize,
                style = TextStyle(lineHeight = labelSize * 0.92f),
            )
        }
    }
}

/** 从底部往上揭开的裁剪形状（票 #60 需求 4.1「课表图标从底部往上变绿」）。 */
private class BottomUpReveal(private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val visible = size.height * progress.coerceIn(0f, 1f)
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = size.height - visible,
                right = size.width,
                bottom = size.height,
            ),
        )
    }
}

/**
 * 底部条容器的「羽化边缘」。
 *
 * 为什么不直接模糊：Compose 没有「模糊背后内容」的低成本做法
 * （Modifier.blur / RenderEffect 模糊的是控件自身，不是背后内容，且 API<31 失效），
 * 所以这里用「多层柔边」做出虚化观感，而不是真正的背景模糊。
 */
internal fun Modifier.featheredEdge(tint: Color, corner: Dp): Modifier = this.drawBehind {
    val cornerPx = corner.toPx()
    val layers = 7
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()               // 1.0(最外) .. 0.14(最内)
        val expand = t * 6.dp.toPx()               // 外扩距离
        val a = (1f - t) * 0.10f                   // 越外越淡
        drawRoundRect(
            color = tint.copy(alpha = a),
            topLeft = Offset(-expand, -expand * 0.7f),
            size = Size(size.width + expand * 2f, size.height + expand * 1.4f),
            cornerRadius = CornerRadius(cornerPx + expand, cornerPx + expand),
            style = Stroke(width = 1.2.dp.toPx() * (1f - t * 0.5f)),
        )
    }
}
