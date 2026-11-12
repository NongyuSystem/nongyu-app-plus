package com.nongyu.appplus.shell.orb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.design.NongyuTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 农小屿星云球（0.0.11test：**统一渲染通道**，砍掉 AGSL）。
 *
 * 【为什么重写】
 * 此前用 `if (SDK_INT >= 33) AGSL else Canvas` 分了两条算法完全不同的通道：
 *   - API 33+ 跑 AGSL fbm 噪声 → 单峰 alpha 导致「绿色甜甜圈」、无晕染
 *   - API 28 跑 Canvas 多个偏心色斑 → 云团感较好
 * 两条永远调不齐，也是「怎么都做不到 HTML 版」的根因。
 *
 * 现在**只保留一条 Canvas 路径**（多偏心软色斑叠成云团）：
 *   - 所有 Android 版本渲染完全一致，一处调参两端同步；
 *   - 没有任何「沿半径的多峰/单峰环带」，因此不会出现同心圆环或甜甜圈；
 *   - 深浅与位置由多个错位色斑 + 各自独立相位决定，形成不规则晕染。
 *
 * 保留：中心打光、边缘羽化到透明、无 3D 高光、无阴影、无黑底。
 * 动画：withFrameNanos 累加相位，长按只改速度；相位只在 draw 阶段读，不引发重组。
 */

/** 单个星云色斑：相对球心的偏移、半径、颜色、透明度、公转速度与相位。 */
private data class Blob(
    val ox: Float,
    val oy: Float,
    val r: Float,
    val colorHex: Long,
    val alpha: Float,
    val orbit: Float,
    val phaseOffset: Float,
)

/**
 * 9 个色斑：绿色 6 个（深浅交错）、黄色 3 个（少量点缀）。
 * 全部为单峰软斑（中心浓→边缘全透明），彼此错位并可各自公转，
 * 叠加后形成不规则云团，不会画出规整圆环。
 *
 * 0.0.13test 调整：恢复原始半径，浓度取原始值的 0.9 倍。
 * （此前误把「降到 6 成」连续应用两次，实际只剩 0.36 成，已纠正。）
 */
private val BLOBS = listOf(
    // 绿色（主体）：原始半径 + 原始浓度的 0.9 倍
    Blob(-0.20f, -0.16f, 0.66f, 0xFF0A7C59, 0.27f, 0.20f, 0.0f),
    Blob(0.24f, 0.12f, 0.60f, 0xFF178A60, 0.23f, -0.16f, 1.1f),
    Blob(0.04f, 0.30f, 0.54f, 0xFF4FA97D, 0.20f, 0.13f, 2.3f),
    Blob(-0.30f, 0.20f, 0.50f, 0xFF0A7C59, 0.22f, -0.19f, 3.5f),
    Blob(0.28f, -0.28f, 0.46f, 0xFF178A60, 0.20f, 0.24f, 4.2f),
    Blob(-0.06f, -0.32f, 0.44f, 0xFF4FA97D, 0.16f, -0.11f, 5.1f),
    // 黄色（少量）：3 个小斑，偏外侧，稳定可见但不抢主体
    Blob(0.32f, 0.22f, 0.36f, 0xFFE9C46A, 0.22f, 0.15f, 0.7f),
    Blob(-0.34f, -0.24f, 0.32f, 0xFFE9C46A, 0.18f, -0.13f, 2.8f),
    Blob(0.10f, 0.36f, 0.28f, 0xFFE9C46A, 0.14f, 0.18f, 4.6f),
)

@Composable
fun NongyuOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    speed: Float = 0.25f,
    /**
     * 是否处于暗背景（长按/点按激活态有全屏 dim）。
     * 暗底上若仍把边缘羽化到「半透明白」会变成一圈灰白亮边，很突兀；
     * 此时把最外缘压得更透、过渡带拉得更宽，让球自然消融进暗背景。
     */
    dimmed: Boolean = false,
) {
    val colors = NongyuTheme.colors.aiOrb

    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speed) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) phase.floatValue += (now - last) / 1_000_000_000f * speed
            last = now
        }
    }

    Canvas(modifier = modifier.size(diameter)) {
        val p = phase.floatValue
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1) 白色不透明底。亮底：0~0.90 全白，最外缘羽化。
        //    暗底：过渡带提前到 0.72 并压到接近全透，避免灰白亮边。
        val edgeStart = if (dimmed) 0.72f else 0.90f
        val edgeMid = if (dimmed) 0.86f else 0.97f
        val edgeMidAlpha = if (dimmed) 0.16f else 0.70f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White,
                    0.62f to colors.colorCore,
                    edgeStart to Color.White,
                    edgeMid to Color.White.copy(alpha = edgeMidAlpha),
                    1.00f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        // 2) 星云云团：多个偏心单峰软斑叠加。每个斑独立公转 → 不规则晕染。
        BLOBS.forEach { b ->
            val a = p * b.orbit + b.phaseOffset
            val c = cos(a)
            val s = sin(a)
            val ox = b.ox * c - b.oy * s
            val oy = b.ox * s + b.oy * c
            val bc = Offset(center.x + ox * radius, center.y + oy * radius)
            val br = b.r * radius
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(b.colorHex).copy(alpha = b.alpha),
                        0.42f to Color(b.colorHex).copy(alpha = b.alpha * 0.55f),
                        0.75f to Color(b.colorHex).copy(alpha = b.alpha * 0.18f),
                        1.00f to Color.Transparent,
                    ),
                    center = bc,
                    radius = br,
                ),
                radius = br,
                center = bc,
            )
        }

        // 3) 中心极弱亮核（不偏移、无暗角、无阴影）。
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.30f),
                    0.35f to Color.White.copy(alpha = 0.10f),
                    0.70f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
