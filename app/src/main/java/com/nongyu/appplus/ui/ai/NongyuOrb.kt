package com.nongyu.appplus.ui.ai

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.ui.theme.NongyuTheme
import kotlin.math.min

/**
 * 农小屿星云球（概念验证版 0.0.1test，v3 修正）。
 *
 * v3 修复（依据真机反馈「中间白 / 周围绿 / 最后变黑」）：
 * AGSL shader 原来返回不透明的 `half4(col, 1.0)`，而 col 从 0（黑）开始 →
 * 等于在黑底上画绿，叠在白球上就成了「中心白、环带绿、边缘黑」。
 * 现改为**返回带 alpha 的染色**：颜色是绿/黄，alpha 是星云浓度，
 * 背景完全透明，中心与边缘也渐隐到透明，因此是「白中透出绿黄晕染」，绝不发黑。
 *
 * 视觉来源：prototype/ai-orb-concept.html v2 —— 主体白色，绿多黄少，
 * 晕染沿圆周成环（乱而有序），绕心旋转；立体感由球面明暗承担，
 * 不画白色高光椭圆、不画白色描边圆环、不再画「球缘外一圈绿」。
 *
 * 技术路线（docs/research/compose-ai-orb-button.md 的 R1/R2）：
 * - API 33+：AGSL RuntimeShader，fBm 分形噪声 + 域扭曲。
 * - API < 33：Canvas 的 sweep/radial 渐变环降级，零新依赖。
 * - 两者都由 withFrameNanos 累加相位驱动，长按只改速度、不重建 animationSpec。
 * 动画相位只在 draw 阶段读取，不引发持续重组。
 */

private const val NEBULA_AGSL = """
uniform float2 iResolution;
uniform float iTime;

float hash(float2 p){
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
float noise(float2 p){
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f*f*(3.0-2.0*f);
    return mix(mix(hash(i), hash(i+float2(1.0,0.0)), u.x),
               mix(hash(i+float2(0.0,1.0)), hash(i+float2(1.0,1.0)), u.x), u.y);
}
float fbm(float2 p){
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}
half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5*iResolution) / (0.5*min(iResolution.x, iResolution.y));
    float r = length(uv);
    float t = iTime * 0.35;

    // 绕心旋转（有序）
    float c = cos(t), s = sin(t);
    float2 ruv = float2(uv.x*c - uv.y*s, uv.x*s + uv.y*c);

    // 域扭曲：把同心色带打散成星云丝缕（乱）
    float2 q = float2(fbm(ruv*3.0 + t*0.3),
                      fbm(ruv*3.0 + float2(5.2, 1.3) + t*0.3));
    float2 w = ruv + 0.45*(q - 0.5);
    float n = fbm(w*4.0 + t*0.2);

    // 环带：以半径为主，颜色方向为圆，不是散点
    float ring  = exp(-pow((r - 0.55)*3.2, 2.0));
    float ring2 = exp(-pow((r - 0.78)*4.5, 2.0));
    float density = ring*(0.55 + 0.75*n) + ring2*0.35*n;

    half3 green  = half3(0.039, 0.486, 0.349);   // #0A7C59
    half3 yellow = half3(0.914, 0.769, 0.416);   // #E9C46A
    // 少量黄色：只出现在噪声高值 + 偏外的窄带
    float yAmt = smoothstep(0.62, 0.90, n) * ring2 * 0.55;
    half3 col = mix(green, yellow, yAmt);

    // 关键：alpha = 星云浓度，背景透明。
    //  centre 与边缘都渐隐到全透明，让白球透出来，绝不铺黑底。
    float a = clamp(density * (0.5 + 0.9*n), 0.0, 0.82);
    a *= smoothstep(0.05, 0.30, r);          // 中心留白
    a *= 1.0 - smoothstep(0.86, 1.0, r);     // 边缘渐隐，不发黑
    return half4(col, a);
}
"""

/**
 * 星云球主体。
 *
 * @param diameter 球体直径
 * @param speed 相位推进速度（静态 ≈ 0.25，长按 ≈ 2.2，瞬间加速）
 */
@SuppressLint("NewApi")
@Composable
fun NongyuOrb(
    diameter: Dp = 120.dp,
    speed: Float = 0.25f,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors.aiOrb

    // 相位：withFrameNanos 累加，长按只改 speed，不重建动画对象。
    // 只在 Canvas 的 draw lambda 里读 → 不触发重组。
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speed) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) {
                phase.floatValue += (now - last) / 1_000_000_000f * speed
            }
            last = now
        }
    }

    // AGSL 只在 API 33+ 创建；任何失败都降级到 Canvas 路径。
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(NEBULA_AGSL)
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
    }

    Canvas(modifier = modifier.size(diameter)) {
        val p = phase.floatValue
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 白球底：光从**中心**打（不再是左上高光），无暗角、无阴影。
        // 边缘 alpha 渐隐到全透明 → 「逐渐淡化」的羽化边界，不是硬边 3D 球。
        // 参考：Operit SiriBall 的「中心明亮核心 + 整体柔和外边界」、
        // Mirage 的 softness/falloff 思路；本实现仍是零依赖 Canvas。
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White,
                    0.55f to colors.colorCore,
                    0.80f to Color.White.copy(alpha = 0.55f),
                    1.00f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        if (shader != null) {
            drawNebulaAgsl(shader, p, size.width, size.height, radius, center, colors)
        } else {
            drawNebulaFallback(p, radius, center, colors)
        }
    }
}

/** AGSL 路径：透明染色叠在白球上。 */
@SuppressLint("NewApi")
private fun DrawScope.drawNebulaAgsl(
    shader: RuntimeShader,
    phase: Float,
    width: Float,
    height: Float,
    radius: Float,
    center: Offset,
    colors: com.nongyu.appplus.ui.theme.NongyuColors.AiOrbColors,
) {
    try {
        shader.setFloatUniform("iResolution", width, height)
        shader.setFloatUniform("iTime", phase)
        drawCircle(
            brush = ShaderBrush(shader),
            radius = radius,
            center = center,
        )
    } catch (t: Throwable) {
        // AGSL 字符串无编译期校验，运行时若炸则退回 Canvas，保证不白屏。
        drawNebulaFallback(phase, radius, center, colors)
    }
    drawSphereFinish(radius, center, colors)
}

/** minSdk 26 降级路径：sweep 角向渐变（绕心）+ radial 环带，仍是透明色带叠白球。 */
private fun DrawScope.drawNebulaFallback(
    phase: Float,
    radius: Float,
    center: Offset,
    colors: com.nongyu.appplus.ui.theme.NongyuColors.AiOrbColors,
) {
    val g = colors.colorNebulaGreen
    val y = colors.colorNebulaYellow
    rotate(degrees = phase * 42f, pivot = center) {
        drawCircle(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to g.copy(alpha = 0.04f),
                    0.18f to g.copy(alpha = 0.55f),
                    0.38f to g.copy(alpha = 0.16f),
                    0.58f to g.copy(alpha = 0.50f),
                    0.80f to g.copy(alpha = 0.22f),
                    1.00f to g.copy(alpha = 0.04f),
                ),
                center = center,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.52f to Color.Transparent,
                    0.70f to y.copy(alpha = 0.34f),
                    0.84f to y.copy(alpha = 0.12f),
                    1.00f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.30f to Color.Transparent,
                    0.50f to g.copy(alpha = 0.38f),
                    0.66f to g.copy(alpha = 0.10f),
                    0.80f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
    drawSphereFinish(radius, center, colors)
}

/**
 * 收尾层：光从**中心**打，边缘逐渐羽化到透明。
 * 不画左上高光、不画边缘暗角、不画任何阴影 —— 避免 3D 球感。
 * 参考同类实现：Operit SiriBall「中心明亮核心 + 整体柔和外边界」、
 * Mirage 的 softness/falloff 参数。
 */
private fun DrawScope.drawSphereFinish(
    radius: Float,
    center: Offset,
    colors: com.nongyu.appplus.ui.theme.NongyuColors.AiOrbColors,
) {
    // 中心柔和亮核（不偏移、不模拟光源方向）
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.60f),
                0.38f to Color.White.copy(alpha = 0.24f),
                0.72f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    // 边缘羽化：0.72→1.0 的环带由「极淡的绿」渐隐到全透明，
    // 形成「逐渐淡化」的软边，而不是一圈清晰硬边圆。
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.72f to Color.Transparent,
                0.86f to colors.colorGlow.copy(alpha = 0.10f),
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
