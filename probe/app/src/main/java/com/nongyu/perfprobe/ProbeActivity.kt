package com.nongyu.perfprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

/**
 * 过度渲染探针（wayfinder #30）。
 *
 * 用途：本工程真实启动路径几乎是空的，直接在正式工程上量「优化前后」只会测到噪声。
 * 这里人为造出四类可被优化的成本，且**刻意与主工程同构**：
 *
 *  - 负载①  首帧组合大量内容：模拟首屏 120 个 item，全是真实 composable。
 *  - 负载②  每帧重绘大区域：复刻 AI 球那段开销（每帧 11 个 radialGradient + 18 次三角函数），
 *           证据见主工程 `ui/ai/NongyuOrb.kt:88-96,99,108,125-160`。
 *  - 负载③  多层 drawBehind 软边羽化：模拟主题的「虚化边界」（7 层递减 alpha 描边）。
 *  - 负载④  大量嵌套布局：12 层嵌套 Row/Column，触发多次 measure。
 *
 * 固定状态：`FIXED_PHASE` 非空时相位不再随时间推进，用于「优化前后逐像素比对」。
 * 通过 intent extra `fixed_phase` 传入（如 0 / 1.57 / 3.14）。
 *
 * 说明：本工程 main-only，不区分 debug/release 源码集；
 * debug 与 release 的差异体现在构建配置（release 开 R8 + 资源收缩）而非代码。
 */
class ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val fixedPhase: Float? = intent
            ?.getStringExtra(EXTRA_FIXED_PHASE)
            ?.toFloatOrNull()

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAF8)),
            ) {
                ProbeScreen(fixedPhase = fixedPhase)
            }
        }
    }

    companion object {
        const val EXTRA_FIXED_PHASE = "fixed_phase"
    }
}

@Composable
private fun ProbeScreen(fixedPhase: Float?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        // 负载②：持续重绘的星云球（可选固定相位，用于截帧比对）
        NebulaProbe(fixedPhase = fixedPhase)

        // 负载③：多层软边羽化背景
        FeatheredPanel {
            Text("③ feathered 7-layer edge")
        }

        // 负载④：12 层嵌套布局
        NestedLayoutProbe(depth = 12)

        // 负载①：首帧组合 120 个 item
        ListProbe(count = 120)
    }
}

// ---------------------------------------------------------------- 负载②

/**
 * 与主工程 `NongyuOrb` 同构的重负载：每帧新建 11 个 radialGradient 并做 18 次三角函数。
 * 保留这一点是刻意的——它正是「待验证的优化对象」本身。
 */
@Composable
private fun NebulaProbe(fixedPhase: Float?) {
    var phase by remember { mutableFloatStateOf(fixedPhase ?: 0f) }

    if (fixedPhase == null) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                if (last != 0L) {
                    phase += (now - last) / 1_000_000_000f * 0.25f
                }
                last = now
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            drawNebula(phase = phase)
        }
    }
}

private data class Blob(
    val ox: Float,
    val oy: Float,
    val r: Float,
    val color: Color,
    val alpha: Float,
    val orbit: Float,
    val phaseOffset: Float,
)

/** 与主工程 `BLOBS` 同为 9 个偏心单峰色斑（绿 6 + 黄 3）。 */
private val BLOBS = listOf(
    Blob(-0.20f, -0.16f, 0.66f, Color(0xFF0A7C59), 0.27f, 0.20f, 0.0f),
    Blob(0.24f, 0.12f, 0.60f, Color(0xFF178A60), 0.23f, -0.16f, 1.1f),
    Blob(0.04f, 0.30f, 0.54f, Color(0xFF4FA97D), 0.20f, 0.13f, 2.3f),
    Blob(-0.30f, 0.20f, 0.50f, Color(0xFF0A7C59), 0.22f, -0.19f, 3.5f),
    Blob(0.28f, -0.28f, 0.46f, Color(0xFF178A60), 0.20f, 0.24f, 4.2f),
    Blob(-0.06f, -0.32f, 0.44f, Color(0xFF4FA97D), 0.16f, -0.11f, 5.1f),
    Blob(0.32f, 0.22f, 0.36f, Color(0xFFE9C46A), 0.22f, 0.15f, 0.7f),
    Blob(-0.34f, -0.24f, 0.32f, Color(0xFFE9C46A), 0.18f, -0.13f, 2.8f),
    Blob(0.10f, 0.36f, 0.28f, Color(0xFFE9C46A), 0.14f, 0.18f, 4.6f),
)

/** 每帧 11 个圆 / 11 个 radialGradient / 18 次三角函数 —— 与主工程逐行对应。 */
private fun DrawScope.drawNebula(phase: Float) {
    val radius = min(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)

    // 1) 白色不透明底 + 最外缘羽化
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.White,
                0.62f to Color.White,
                0.90f to Color.White,
                0.97f to Color.White.copy(alpha = 0.70f),
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )

    // 2) 9 个色斑，各自公转
    BLOBS.forEach { b ->
        val a = phase * b.orbit + b.phaseOffset
        val c = cos(a)
        val s = sin(a)
        val ox = b.ox * c - b.oy * s
        val oy = b.ox * s + b.oy * c
        val bc = Offset(center.x + ox * radius, center.y + oy * radius)
        val br = b.r * radius
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to b.color.copy(alpha = b.alpha),
                    0.42f to b.color.copy(alpha = b.alpha * 0.55f),
                    0.75f to b.color.copy(alpha = b.alpha * 0.18f),
                    1.00f to Color.Transparent,
                ),
                center = bc,
                radius = br,
            ),
            radius = br,
            center = bc,
        )
    }

    // 3) 中心亮核
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

// ---------------------------------------------------------------- 负载③

/** 7 层逐圈外扩、alpha 递减的圆角描边 —— 模拟主工程「虚化边界」的合成成本。 */
@Composable
private fun FeatheredPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawFeatheredEdge(layers = 7),
    ) {
        Box(modifier = Modifier.padding(24.dp)) { content() }
    }
}

private fun Modifier.drawFeatheredEdge(layers: Int): Modifier = this.then(
    Modifier.drawBehind {
        // 注意：必须先把 DrawScope.size 取到局部变量。
        // 若直接写 drawRoundRect(size = Size(width = size.width ...))，那个命名参数 `size`
        // 会在实参表达式里遮蔽 DrawScope.size，导致 "Function invocation 'size(...)' expected"。
        val canvasW = size.width
        val canvasH = size.height
        val maxSpread = size.minDimension * 0.06f
        for (i in 0 until layers) {
            val t = i / (layers - 1f)
            val spread = maxSpread * t
            drawRoundRect(
                color = Color(0xFF0A7C59).copy(alpha = 0.16f * (1f - t)),
                topLeft = Offset(spread, spread),
                size = Size(
                    width = canvasW - spread * 2,
                    height = canvasH - spread * 2,
                ),
                cornerRadius = CornerRadius(20f),
                style = Stroke(width = maxSpread / layers),
            )
        }
    },
)

// ---------------------------------------------------------------- 负载④

/** 12 层嵌套 Row/Column：每层都带 padding，触发多次 measure。 */
@Composable
private fun NestedLayoutProbe(depth: Int) {
    NestNode(depth = depth)
}

@Composable
private fun NestNode(depth: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(1.dp)) {
        Column(modifier = Modifier.padding(1.dp)) {
            if (depth > 0) {
                NestNode(depth = depth - 1)
            } else {
                Text("④ innermost")
            }
        }
    }
}

// ---------------------------------------------------------------- 负载①

/** 首帧组合 120 个 item，每项都是真实 composable（非 Lazy，以便一次性计入首帧）。 */
@Composable
private fun ListProbe(count: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(vertical = 2.dp)
                    .background(Color(0xFFFFFFFF)),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("① item $i")
                }
            }
        }
    }
}
