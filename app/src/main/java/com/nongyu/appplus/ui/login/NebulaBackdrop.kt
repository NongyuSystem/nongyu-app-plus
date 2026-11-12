package com.nongyu.appplus.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 登录页背景：**大面积流动星云光晕**（票 #91）。
 *
 * ## 视觉
 * 铺满整个屏幕的柔和光斑，像「农小屿星云球」但放大到全屏：
 * - 每个光斑是**单一颜色**的放射渐变（一片是一种颜色，不在同一区域内混色）；
 * - 颜色构成：**绿**（主）、**黄**（点缀）、**白**（提亮）、**微量蓝**（冷点）；
 * - 每个光斑沿自己的轨道缓慢公转 + 半径呼吸，形成**流动质感**；
 * - 光斑之间靠 `BlendMode.Screen` 叠加，保持透亮不发灰。
 *
 * ## 为什么不用图片
 * 全部 `Canvas` + `radialGradient` 矢量绘制，无图片资源、无第三方库；
 * 颜色由调用方传入（来自 [com.nongyu.appplus.design.NongyuColors] 的色板）。
 *
 * ## 性能
 * 光斑数量固定（[NEBULA_BLOBS] 常量），每帧只是几次 `drawCircle`，
 * 且动画是单一 `infiniteTransition` 驱动的相位值，不会引起重组，只重绘。
 */
@Composable
fun NebulaBackdrop(
    modifier: Modifier = Modifier,
) {
    // 相位：0→1 无限循环，驱动所有光斑公转
    val transition = rememberInfiniteTransition(label = "nebula")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nebulaPhase",
    )
    // 第二个更慢的相位，用于半径呼吸，避免与公转同频显得机械
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 17_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "nebulaBreathe",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val base = minOf(w, h)

        NEBULA_BLOBS.forEach { blob ->
            // 公转：每个光斑角速度不同，形成相对流动
            val angle = phase * 2f * Math.PI.toFloat() * blob.orbit + blob.phase
            val ox = cos(angle) * blob.orbitR * w
            val oy = sin(angle) * blob.orbitR * h
            val cx = w * blob.cx + ox
            val cy = h * blob.cy + oy

            // 半径呼吸
            val breatheScale = 0.86f + 0.14f * breathe
            val radius = base * blob.r * breatheScale

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to blob.color.copy(alpha = blob.alpha),
                        0.28f to blob.color.copy(alpha = blob.alpha * 0.72f),
                        0.55f to blob.color.copy(alpha = blob.alpha * 0.34f),
                        0.80f to blob.color.copy(alpha = blob.alpha * 0.10f),
                        1.00f to Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}

/**
 * 一个星云光斑的定义。
 *
 * @param cx/cy 基准中心（相对屏宽/屏高，0..1）
 * @param r 半径（相对屏幕短边）
 * @param color 光斑唯一颜色（一片一种颜色）
 * @param alpha 峰值透明度
 * @param orbit 公转角速度倍率（不同值 → 相对流动）
 * @param orbitR 公转半径（相对屏宽/屏高）
 * @param phase 初始相位（让光斑分布错开）
 */
private data class NebulaBlob(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val color: Color,
    val alpha: Float,
    val orbit: Float,
    val orbitR: Float,
    val phase: Float,
)

/**
 * 光斑清单。
 *
 * 颜色分配遵循维护者要求：**绿为主、黄点缀、白提亮、微量蓝**，
 * 且每个光斑只用一个颜色（不在同一片区域内混两种色）。
 */
private val NEBULA_BLOBS: List<NebulaBlob> = listOf(
    // ---- 绿：主调，占多数且面积最大 ----
    NebulaBlob(cx = 0.20f, cy = 0.16f, r = 0.78f, color = Color(0xFF0A7C59), alpha = 0.18f, orbit = 1.0f, orbitR = 0.030f, phase = 0.0f),
    NebulaBlob(cx = 0.82f, cy = 0.30f, r = 0.70f, color = Color(0xFF12A06F), alpha = 0.28f, orbit = -0.7f, orbitR = 0.026f, phase = 1.9f),
    NebulaBlob(cx = 0.28f, cy = 0.78f, r = 0.66f, color = Color(0xFF0A7C59), alpha = 0.27f, orbit = 0.5f, orbitR = 0.022f, phase = 3.4f),
    NebulaBlob(cx = 0.72f, cy = 0.90f, r = 0.62f, color = Color(0xFF1FB183), alpha = 0.24f, orbit = -1.2f, orbitR = 0.020f, phase = 5.1f),

    // ---- 黄：点缀，面积小、数量少 ----
    NebulaBlob(cx = 0.88f, cy = 0.62f, r = 0.44f, color = Color(0xFFE9C46A), alpha = 0.28f, orbit = 0.9f, orbitR = 0.034f, phase = 2.2f),
    NebulaBlob(cx = 0.12f, cy = 0.54f, r = 0.36f, color = Color(0xFFF2D488), alpha = 0.30f, orbit = -0.6f, orbitR = 0.028f, phase = 4.3f),

    // ---- 白：提亮核心，压在中心附近 ----
    NebulaBlob(cx = 0.50f, cy = 0.42f, r = 0.52f, color = Color.White, alpha = 0.30f, orbit = 0.3f, orbitR = 0.014f, phase = 0.8f),
    NebulaBlob(cx = 0.44f, cy = 0.70f, r = 0.34f, color = Color.White, alpha = 0.28f, orbit = -0.4f, orbitR = 0.018f, phase = 3.0f),

    // ---- 蓝：微量，只两个小点作冷色平衡 ----
    NebulaBlob(cx = 0.66f, cy = 0.10f, r = 0.24f, color = Color(0xFF6FB7E8), alpha = 0.20f, orbit = 1.4f, orbitR = 0.036f, phase = 1.2f),
    NebulaBlob(cx = 0.18f, cy = 0.94f, r = 0.20f, color = Color(0xFF8FCBEF), alpha = 0.18f, orbit = -1.6f, orbitR = 0.030f, phase = 5.6f),
)
