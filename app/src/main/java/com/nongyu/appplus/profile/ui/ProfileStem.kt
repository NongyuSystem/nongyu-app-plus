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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.profile.data.DemoProfileHolder
import com.nongyu.appplus.profile.logic.StemGrowth
import com.nongyu.appplus.profile.model.ProfileSnapshot

/** 茎的线宽（dp）。维护者「线条加粗」：8dp。 */
private const val STEM_WIDTH_DP = 8f

/** 果实半径（dp）。与 [StemGrowth.FRUIT_RADIUS_DP] 一致。 */
private const val FRUIT_RADIUS_DP_UI = StemGrowth.FRUIT_RADIUS_DP

/** 文字与果实之间的水平间隙（dp）。 */
private const val TEXT_GAP_DP = 12f

/**
 * 第 1~2 步：开页「茎」+ 4 颗绑定用户信息的果实。
 *
 * 视觉（维护者 2026-11-27 多轮修订后定稿）：
 * - 茎：真圆弧，起点=底部星云球心，最右点距右边缘 5~15px，终点贴左、距顶 1/5；
 * - 线宽 8dp，黄绿 #8BC34A；
 * - 4 颗黄色果实沿茎按 y 等差排列，往茎内侧偏移 14dp，茎长到位置时变大显现；
 * - **每颗果实绑定一条用户信息**（姓名 / 班级 / 学号 / 专业），果实出现时
 *   对应文字出现在果实**左侧**，与果实同步显现。
 *
 * 驱动：单条显式 [Animatable]，只驱动茎的扫角；果实缩放与文字透明度都由该进度推导，
 * 三者永远同步。进度只在 draw 阶段读，不触发父级重组。
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
    val textMeasurer = rememberTextMeasurer()
    // debug 源集提供演示画像；release 无 demo，显示占位「—」。
    val profile: ProfileSnapshot? = DemoProfileHolder.demo

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        )
    }

    val textStyle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        color = colors.surface.colorText,
    )

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

        val rightY = StemGrowth.solveRightPointY(startX, startY, endX, endY, w)
        val circle = StemGrowth.circle3(startX, startY, w - StemGrowth.RIGHT_GAP_PX, rightY, endX, endY)
            ?: return@Canvas
        val (cx, cy, r) = circle

        val startDeg = StemGrowth.angleOf(cx, cy, startX, startY)
        val rightDeg = StemGrowth.angleOf(cx, cy, w - StemGrowth.RIGHT_GAP_PX, rightY)
        val endDeg = StemGrowth.angleOf(cx, cy, endX, endY)
        val sweepCcw = StemGrowth.sweepBetween(startDeg, endDeg, -1)
        val sweepCw = StemGrowth.sweepBetween(startDeg, endDeg, +1)
        // 必须选经过最右点的那条弧（起点 → 最右点 → 终点），不能盲目取短弧
        val ccwToRight = StemGrowth.sweepBetween(startDeg, rightDeg, -1)
        val ccw = ccwToRight <= sweepCcw
        val total = if (ccw) sweepCcw else sweepCw

        val growth = StemGrowth.clampProgress(progress.value)
        if (growth <= 0f) return@Canvas
        val sweepNow = total * growth

        // 茎（黄绿真圆弧）
        drawArc(
            color = colors.brand.colorBrandFresh,
            startAngle = startDeg,
            sweepAngle = if (ccw) -sweepNow else sweepNow,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = STEM_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round),
        )

        // 果实 + 绑定文字
        val fruitRadiusPx = FRUIT_RADIUS_DP_UI.dp.toPx()
        val insetPx = StemGrowth.FRUIT_INWARD_INSET_DP.dp.toPx()
        val gapPx = TEXT_GAP_DP.dp.toPx()
        val topY = endY
        val bottomY = startY

        for (i in 0 until StemGrowth.FRUIT_COUNT) {
            val fy = StemGrowth.fruitY(topY, bottomY, i)
            val fx = StemGrowth.xOnCircleAt(cx, cy, r, fy, preferRight = true, screenWidth = w)
                ?: continue
            val fAngle = StemGrowth.angleOf(cx, cy, fx, fy)
            val fProgress = StemGrowth.progressOfAngle(startDeg, total, ccw, fAngle)
            val scale = StemGrowth.fruitScaleAt(growth, fProgress)
            if (scale <= 0f) continue

            // 沿半径朝圆心内偏，避开右边缘裁切
            val toCenterX = cx - fx
            val toCenterY = cy - fy
            val len = kotlin.math.hypot(toCenterX, toCenterY).takeIf { it > 0f }
            val fruitX = fx + (if (len != null) toCenterX / len * insetPx else 0f)
            val fruitY = fy + (if (len != null) toCenterY / len * insetPx else 0f)

            drawCircle(
                color = colors.aiOrb.colorNebulaYellow,
                radius = fruitRadiusPx * scale,
                center = Offset(fruitX, fruitY),
            )

            // 绑定文字：出现在果实左侧，与果实同步显现
            if (i < StemGrowth.FRUIT_FIELDS.size) {
                val field = StemGrowth.FRUIT_FIELDS[i]
                val text = StemGrowth.valueOf(field, profile)
                val layout = textMeasurer.measure(text, textStyle)
                val textX = (fruitX - fruitRadiusPx - gapPx - layout.size.width)
                    .coerceAtLeast(8f)
                val textY = fruitY - layout.size.height / 2f
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(textX, textY),
                    color = colors.surface.colorText,
                    alpha = scale,
                )
            }
        }
    }
}
