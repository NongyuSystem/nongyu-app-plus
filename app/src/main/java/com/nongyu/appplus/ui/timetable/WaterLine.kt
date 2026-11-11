package com.nongyu.appplus.ui.timetable

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 「流水」——整屏宽的水面，表示当前时间在课表时间轴上的位置（票 #60 需求 3）。
 *
 * ## 视觉口径（维护者 2026-11-11 最终口径）
 * - **宽度是整屏宽**，不是时间条里那条 6dp 竖线；
 * - 水面从时间轴当前位置一直**铺到屏幕底部**；
 * - 有渐变（顶部浓、底部淡），水面前沿是波形。
 *
 * ## 物理
 *
 * 1. **惯性晃动**：不用物理引擎。给水面角速度 `omega`，每帧按阻尼衰减，
 *    相位按 `omega` 推进 —— 被"碰一下"之后会自己晃几下再停。
 * 2. **水平仪**：[tiltDegrees] 由 [rememberWaterTilt] 提供，直接给水面一个静态倾角。
 *
 * ## 为什么用 withInfiniteAnimationFrameNanos
 *
 * 跟随屏幕刷新、离开组合自动停，不留裸 `while(true)` 协程。
 */
@Composable
fun WaterLine(
    /** 水面顶部位置：占整屏高度的比例 0..1（0 = 顶部）。 */
    surfacePercent: Float,
    /** 水平仪倾斜角（度），0 = 水平。 */
    tiltDegrees: Float,
    modifier: Modifier = Modifier,
    /** 水面渐变的顶部（浓）与底部（淡）。 */
    topColor: Color = Color(0xFF0A7C59),
    bottomColor: Color = Color(0xFF0A7C59),
) {
    var phase by remember { mutableFloatStateOf(0f) }
    var omega by remember { mutableFloatStateOf(6f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                    // 阻尼：每秒衰减到约 0.55，几秒内自然停下
                    omega *= Math.pow(0.55, dt.toDouble()).toFloat()
                    if (omega < 0.02f) omega = 0f
                    phase += omega * dt
                    if (phase > 2f * PI.toFloat()) phase -= 2f * PI.toFloat()
                }
                last = now
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val surfaceY = (h * surfacePercent.coerceIn(0f, 1f))
        if (surfaceY >= h - 1f) return@Canvas

        // 晃动幅度：角速度越大波形越高；静止时仍有极小的呼吸感
        val amp = h * 0.010f * (0.35f + (omega / 6f).coerceIn(0f, 1f) * 0.65f)
        // 水平仪：左右两端高度差（±8° 内最多 2% 屏高）
        val tiltPx = (tiltDegrees / 8f) * h * 0.02f
        val steps = 48

        fun surfaceX(i: Int): Float = w * i / steps
        fun surfaceYAt(i: Int): Float {
            val x = surfaceX(i)
            val tiltAt = tiltPx * ((x / w) - 0.5f) * 2f
            val wave = amp * sin(phase + i.toFloat() / steps * 3f * PI.toFloat())
            return surfaceY + tiltAt + wave
        }

        // 水的主体：从水面到底部，顶部浓 → 底部淡
        val body = Path()
        body.moveTo(0f, surfaceYAt(0))
        for (i in 1..steps) body.lineTo(surfaceX(i), surfaceYAt(i))
        body.lineTo(w, h)
        body.lineTo(0f, h)
        body.close()

        drawPath(
            path = body,
            // 纵向颜色走向：**水面处最淡、往下逐渐加深**（维护者 2026-11-11 纠正）。
            //
            // 上一版是「上浓下淡」，越往下越接近白，看起来像水被抽干了。
            // 真实水体是越深越暗（光穿不透），所以这里反过来：
            //   水面 alpha 0.10 → 中段 0.26 → 底部 0.40
            brush = Brush.verticalGradient(
                colors = listOf(
                    topColor.copy(alpha = 0.10f),
                    topColor.copy(alpha = 0.26f),
                    bottomColor.copy(alpha = 0.40f),
                ),
                startY = surfaceY,
                endY = h,
            ),
        )

        // 虚化层已删除（维护者 2026-11-11：既然主体已经是「下深上浅」，
        // 顶部再叠一道过渡带反而显脏，直接由主体渐变自己收边即可）。

        // 水面附近的细碎波光（少量短线段，增强"水"的质感）
        if (omega > 0.5f) {
            for (i in 1 until steps step 6) {
                val x = surfaceX(i)
                val y = surfaceYAt(i) + h * 0.012f
                drawLine(
                    color = topColor.copy(alpha = 0.22f),
                    start = Offset(x, y),
                    end = Offset(x + w * 0.03f, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}
