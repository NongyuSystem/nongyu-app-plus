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

/*
 * ============================================================================
 * 本文件的 AGSL 算法衍生自 Mirage（AndroidPoet / Ranbir Singh），Apache-2.0：
 *   https://github.com/AndroidPoet/mirage   （0.1.0）
 * 上游 shader 算法又衍生自 Paper Shaders（Apache-2.0）：
 *   https://github.com/paper-design/shaders
 *
 * 说明：我们只移植 MeshGradient.kt 的**算法主体**（流动色斑 + 域扭曲 + swirl），
 * 未引入 Mirage 依赖（它与本仓「零新依赖」约束冲突）。
 * - 去掉了 SIZING_AGSL 的 fit/offset/rotation/world 一套 sizing uniform，
 *   固定为「以球心为原点、归一化到 [-0.5,0.5] 的正方形」；
 * - u_colors 由运行时 uniform 改为编译期常量（白 / 绿 / 少量黄）；
 * - 保留 hash21 / rotate / valueNoise / getPosition / 主循环的原样结构；
 * - 叠加了本仓要求的「中心打光 + 边缘羽化到透明」，去掉 3D 高光与暗角。
 * 署名与许可见 docs/research/ai-orb-reference-projects.md 与 LICENSE-NOTICE。
 * ============================================================================
 */

/**
 * 移植版 MeshGradient AGSL（API 33+）。
 * 关键：返回带 alpha 的染色，背景透明；中心与边缘都渐隐，不铺黑底。
 */
private const val MIRAGE_MESH_AGSL = """
uniform float2 iResolution;
uniform float iTime;

const float PI = 3.14159265358979323846;

float hash21(vec2 p) {
  p = fract(p * vec2(0.3183099, 0.3678794)) + 0.1;
  p += dot(p, p + 19.19);
  return fract(p.x * p.y);
}
vec2 rotate(vec2 uv, float th) {
  return mat2(cos(th), sin(th), -sin(th), cos(th)) * uv;
}
float valueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = hash21(i);
  float b = hash21(i + vec2(1.0, 0.0));
  float c = hash21(i + vec2(0.0, 1.0));
  float d = hash21(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
// 色斑轨迹（Mirage getPosition 原样）
vec2 getPosition(int i, float t) {
  float a = float(i) * 0.37;
  float b = 0.6 + fract(float(i) / 3.0) * 0.9;
  float c = 0.8 + fract(float(i + 1) / 4.0);
  float x = sin(t * b + a);
  float y = cos(t * c + a * 1.5);
  return 0.5 + 0.5 * vec2(x, y);
}

// 农屿配色：白主体 + 绿较多 + 少量黄（alpha 作为权重，绿权重最高）
const vec3 C0 = vec3(1.0, 1.0, 1.0);          // 白
const vec3 C1 = vec3(0.039, 0.486, 0.349);    // #0A7C59 绿
const vec3 C2 = vec3(0.914, 0.769, 0.416);    // #E9C46A 黄（少量）
const vec3 C3 = vec3(0.498, 0.749, 0.584);    // 浅绿
const vec3 C4 = vec3(1.0, 1.0, 1.0);          // 白

half4 main(vec2 fragCoord) {
  // 以球心为原点、归一化到 [-0.5, 0.5]
  vec2 uv = fragCoord / iResolution - 0.5;
  float radius = length(uv) * 2.0;            // 0(中心) .. 1(边缘)

  // 时间：u_time 与 Mirage 一致（+41.5 起始偏移）
  float t = 0.5 * (iTime + 41.5);
  float distortion = 0.8;
  float swirl = 0.35;

  // 域扭曲（Mirage 原样，center 越靠中心扰动越强）
  vec2 duv = uv + 0.5;
  float center = 1.0 - smoothstep(0.0, 1.0, length(uv));
  for (float i = 1.0; i <= 2.0; i++) {
    duv.x += distortion * center / i * sin(t + i * 0.4 * smoothstep(0.0, 1.0, duv.y))
             * cos(0.2 * t + i * 2.4 * smoothstep(0.0, 1.0, duv.y));
    duv.y += distortion * center / i * cos(t + i * 2.0 * smoothstep(0.0, 1.0, duv.x));
  }
  // swirl：绕中心旋转，强化「方向为圆、绕心流动」
  duv -= vec2(0.5);
  duv = rotate(duv, -3.0 * swirl * (radius * 0.5));
  duv += vec2(0.5);

  // 5 个色斑的加权混合（Mirage 主循环，u_colors 改为常量）
  vec3 acc = vec3(0.0);
  float accA = 0.0;
  float accW = 0.0;
  for (int i = 0; i < 5; i++) {
    vec2 pos = getPosition(i, t);
    vec3 col; float a;
    if (i == 0)      { col = C0; a = 1.00; }
    else if (i == 1) { col = C1; a = 0.95; }
    else if (i == 2) { col = C2; a = 0.28; }  // 黄：少量
    else if (i == 3) { col = C3; a = 0.80; }
    else             { col = C4; a = 1.00; }
    float dist = pow(length(duv - pos), 3.5);
    float w = 1.0 / (dist + 1e-3);
    acc  += col * a * w;
    accA += a * w;
    accW += w;
  }
  vec3 color = acc / max(1e-4, accW);
  float opacity = accA / max(1e-4, accW);

  // 本仓要求：主体白、中心打光、边缘羽化到透明、不铺黑底
  opacity = clamp(opacity, 0.0, 1.0);
  opacity *= smoothstep(0.02, 0.22, radius);       // 中心留白
  opacity *= 1.0 - smoothstep(0.72, 1.0, radius);  // 边缘渐隐
  return half4(color, opacity * 0.85);
}
"""

/** 移植版星云球：API 33+ 用 Mirage MeshGradient 算法；<33 退回本仓自研 Canvas 降级。 */
@SuppressLint("NewApi")
@Composable
fun MirageOrb(
    diameter: Dp = 120.dp,
    speed: Float = 0.25f,
    modifier: Modifier = Modifier,
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

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try { RuntimeShader(MIRAGE_MESH_AGSL) } catch (t: Throwable) { null }
        } else null
    }

    Canvas(modifier = modifier.size(diameter)) {
        val p = phase.floatValue
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 白球底：中心打光 + 边缘羽化（与自研版一致）
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
            try {
                shader.setFloatUniform("iResolution", size.width, size.height)
                shader.setFloatUniform("iTime", p)
                drawCircle(brush = ShaderBrush(shader), radius = radius, center = center)
            } catch (t: Throwable) {
                drawMirageFallback(p, radius, center, colors)
            }
        } else {
            drawMirageFallback(p, radius, center, colors)
        }
        // 中心亮核（不偏移、无暗角、无阴影）
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.45f),
                    0.38f to Color.White.copy(alpha = 0.18f),
                    0.72f to Color.Transparent,
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

/** <33 降级：沿用本仓自研的角向/径向渐变环（零依赖）。 */
private fun DrawScope.drawMirageFallback(
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
    }
}
