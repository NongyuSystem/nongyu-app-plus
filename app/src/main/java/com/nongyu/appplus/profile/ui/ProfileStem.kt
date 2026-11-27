package com.nongyu.appplus.profile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.profile.logic.StemGrowth

/**
 * 茎的线宽（dp）。
 *
 * 维护者 2026-11-27：「线条加粗」。3dp 像铅笔细线，8dp 才有「茎」的实体感。
 */
private const val STEM_WIDTH_DP = 8f

/** 果实半径（dp）。与 [StemGrowth.FRUIT_RADIUS_DP] 保持一致。 */
private const val FRUIT_RADIUS_DP_UI = StemGrowth.FRUIT_RADIUS_DP

/**
 * 第 1 步：开页「茎」的绘制（只做线，且是**真圆弧**）。
 *
 * 视觉（维护者 2026-11-27 第 3 次修订）：
 * - 起点：底部中央的星云球中心；
 * - 从起点向右偏出发，鼓成一段真正的圆弧（过起点、过最右点、过终点三点定圆）；
 * - 圆弧最右点距屏幕右侧 5~15px；
 * - 终点落在左侧、距屏幕顶部 1/5。
 *
 * 终点切线接近水平（约 179°）是几何必然，维护者已确认接受；详见 [StemGrowth] 类注释。
 *
 * 起点取星云球心：OrbHost 的球心公式是「水平居中，y = 容器高 − 0.22×球径 − 球径/2」。
 *
 * 驱动：单条显式 [Animatable]，只驱动扫角，逐帧画弧。
 */
@Composable
fun ProfileStem(
    modifier: Modifier = Modifier,
    /** 星云球直径（已乘 uiScale），用于定位起点。 */
    orbSize: Dp,
    durationMillis: Int = 1400,
) {
    val colors = NongyuTheme.colors
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 起点 = 星云球中心（与 OrbHost 同一公式）
        val orbPx = orbSize.toPx()
        val startX = w / 2f
        val startY = h - orbPx * 0.22f - orbPx / 2f

        // 终点：贴左屏幕边缘、距顶部 1/5
        val endX = StemGrowth.END_LEFT_INSET_PX
        val endY = h * StemGrowth.END_TOP_FRACTION

        // 求「过起点、最右贴边、过终点」的圆
        val rightY = StemGrowth.solveRightPointY(startX, startY, endX, endY, w)
        val circle = StemGrowth.circle3(startX, startY, w - StemGrowth.RIGHT_GAP_PX, rightY, endX, endY)
            ?: return@Canvas
        val (cx, cy, r) = circle

        val startDeg = StemGrowth.angleOf(cx, cy, startX, startY)
        val endDeg = StemGrowth.angleOf(cx, cy, endX, endY)

        // 从起点逆时针（屏幕上=角度减小？）到终点。取较短的那段方向。
        val sweepCcw = StemGrowth.sweepBetween(startDeg, endDeg, -1)
        val sweepCw = StemGrowth.sweepBetween(startDeg, endDeg, +1)
        val ccw = sweepCcw <= sweepCw
        val total = if (ccw) sweepCcw else sweepCw

        val growth = StemGrowth.clampProgress(progress.value)
        if (growth <= 0f) return@Canvas
        val sweepNow = total * growth

        // Compose drawArc 的角度：0° = 三点钟，顺时针为正。
        // 我们的角度同向，所以直接从 startDeg 起扫；逆时针则取负。
        drawArc(
            color = colors.brand.colorBrandFresh,
            startAngle = startDeg,
            sweepAngle = if (ccw) -sweepNow else sweepNow,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = STEM_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round),
        )

        // ---- 果实：沿茎按 y 等差排列，茎长到就变大显现 ----
        val fruitRadiusPx = FRUIT_RADIUS_DP_UI.dp.toPx()
        val insetPx = StemGrowth.FRUIT_INWARD_INSET_DP.dp.toPx()
        // 茎的可见纵向范围：从终点 y（顶部）到起点 y（底部）
        val topY = endY
        val bottomY = startY
        for (i in 0 until StemGrowth.FRUIT_COUNT) {
            val fy = StemGrowth.fruitY(topY, bottomY, i)
            val fx = StemGrowth.xOnCircleAt(cx, cy, r, fy, preferRight = true, screenWidth = w)
                ?: continue
            // 果实位置对应的扫角进度
            val fAngle = StemGrowth.angleOf(cx, cy, fx, fy)
            val fProgress = StemGrowth.progressOfAngle(startDeg, total, ccw, fAngle)
            val scale = StemGrowth.fruitScaleAt(growth, fProgress)
            if (scale <= 0f) continue

            // 沿半径朝圆心挪一点，避开右边缘裁切（维护者：往茎内侧偏移）。
            val toCenterX = cx - fx
            val toCenterY = cy - fy
            val len = kotlin.math.hypot(toCenterX, toCenterY).takeIf { it > 0f }
            val offsetX = if (len != null) toCenterX / len * insetPx else 0f
            val offsetY = if (len != null) toCenterY / len * insetPx else 0f

            drawCircle(
                color = colors.aiOrb.colorNebulaYellow,
                radius = fruitRadiusPx * scale,
                center = Offset(fx + offsetX, fy + offsetY),
            )
        }
    }
}
