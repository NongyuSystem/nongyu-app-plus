package com.nongyu.appplus.profile.ui

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.profile.logic.EnterAnimation
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 个人主页开页生长动画的进度句柄（票 #115）。
 *
 * 内部只有一条显式 [Animatable]，从 0 推到 1 用 `tween(TOTAL_MS)`。
 * [playOnce] 带 started 闸门：整个 MainShell 组合生命周期内只播一次，切走「个人」
 * Tab 再回来不重播（重复播放既打扰用户也浪费低端机帧预算）。
 *
 * 进度只在 Canvas draw 阶段与 Modifier.graphicsLayer 图层阶段读取，不参与组合，
 * 所以整段动画不触发父级重组。
 */
class ProfileEnterProgress internal constructor(
    private val animatable: Animatable<Float, AnimationVector1D>,
) {
    /** 当前总进度 0..1，仅应在 draw / graphicsLayer lambda 内读取。 */
    val value: Float get() = animatable.value

    /**
     * 首次调用推进整段动画；后续调用直接返回。
     * 降级时直接 snap 到终态，不跑任何逐帧动画。
     */
    suspend fun playOnce(skip: Boolean = false) {
        if (started) return
        started = true
        if (skip) {
            animatable.snapTo(1f)
        } else {
            animatable.animateTo(1f, tween(EnterAnimation.TOTAL_MS.toInt()))
        }
    }

    private var started: Boolean = false
}

/** 在 MainShell 层记住唯一的开页进度实例（切 Tab 不销毁）。 */
@Composable
fun rememberProfileEnterProgress(): ProfileEnterProgress = remember {
    ProfileEnterProgress(Animatable(0f))
}

/**
 * 降级判定所需的系统值只在 UI 层读；纯判定在 [EnterAnimation.shouldSkipAnimation]。
 *
 * - 减弱动效：`Settings.Global.ANIMATOR_DURATION_SCALE <= 0`；
 * - 低端机：`ActivityManager.isLowRamDevice`。
 * 读取失败一律按「不降级」处理，避免权限 / ROM 差异把动画永久关掉。
 */
@Composable
fun rememberProfileEnterSkip(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        } catch (t: SecurityException) {
            1f
        } catch (t: Settings.SettingNotFoundException) {
            1f
        }
        val lowEnd = try {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.isLowRamDevice == true
        } catch (t: RuntimeException) {
            false
        }
        EnterAnimation.shouldSkipAnimation(scale, lowEnd)
    }
}

/**
 * 在开页动画运行期间给页面内容套一层淡入 alpha。
 * alpha 在图层阶段算，不重组 [content] 子树；降级时恒为 1f（直接显示）。
 */
@Composable
fun ProfileEnterFade(
    progress: ProfileEnterProgress,
    skip: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (skip) 1f else EnterAnimation.contentAlpha(progress.value)
            },
    ) {
        content()
    }
}

/**
 * 开页生长动画浮层：枝条 → 绕圆 → 果实 → 字符头像收尾。
 *
 * 全程由单个 [progress] 驱动，Canvas 只在 draw 阶段读进度，浮层自身透明且不消费触摸，
 * 触摸穿透给下方农小屿球与页面（球的 enabled 判定完全不动）。
 *
 * ## 起点与层级
 * 枝条从农小屿球心起步：优先用 OrbHost 的 `onOrbCenterChange` 上报的整窗坐标；
 * 首帧未测完时回落为「水平居中、y = 容器高 − 0.72×球径」，与球 Box 的
 * `align(BottomCenter)` + `offset(y = -0.22×球径)` + `size(球径)` 公式一致。
 * 本浮层必须在 MainShell 中声明于 OrbHost **之后**，枝条才画在球之上。
 *
 * @param orbCenterPx 球心整窗坐标，null = 用回落公式
 * @param orbSizePx 球径像素（已乘 uiScale），仅回落策略使用
 * @param name 姓名，供字符头像取首字
 */
@Composable
fun ProfileEnterAnimationOverlay(
    progress: ProfileEnterProgress,
    orbCenterPx: Offset?,
    orbSizePx: Float,
    name: String?,
    uiScale: Float,
    skip: Boolean,
    modifier: Modifier = Modifier,
) {
    if (skip) return

    val colors = NongyuTheme.colors
    val density = LocalDensity.current
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    // 几何只在尺寸 / 球心变化时重算，不随动画帧重组。
    val w = sizePx.width.toFloat()
    val h = sizePx.height.toFloat()
    val radius = if (w <= 0f || h <= 0f) 0f else {
        (min(w, h) * 0.16f).coerceIn(56f * uiScale, 104f * uiScale)
    }
    val circleCenter = Offset(w / 2f, maxOf(h * 0.30f, radius + 24f * uiScale))
    val start: Offset = if (orbCenterPx != null) {
        Offset(orbCenterPx.x - rootOrigin.x, orbCenterPx.y - rootOrigin.y)
    } else {
        Offset(w / 2f, h - 0.72f * orbSizePx)
    }
    // 头像徽标直径 = 圆半径的 1.05 倍（居中坐在圆里，比整圆略小）。
    // Dp 尺寸与 px 偏移必须用同一个直径，否则圆点与徽标会错位。
    val avatarDiameterPx = radius * 1.05f
    val avatarDp = (avatarDiameterPx / density.density).dp
    val avatarOffset = IntOffset(
        circleCenter.x.toInt() - avatarDiameterPx.toInt() / 2,
        circleCenter.y.toInt() - avatarDiameterPx.toInt() / 2,
    )
    val fruitAngles = remember { EnterAnimation.fruitAngles() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootOrigin = coords.localToRoot(Offset.Zero)
                if (coords.size != sizePx) sizePx = coords.size
            },
    ) {
        if (radius > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = progress.value
                val branch = EnterAnimation.branchGrowth(p)
                val orbit = EnterAnimation.orbitTravel(p)
                val fruit = EnterAnimation.segmentProgress(p, EnterAnimation.Segment.FRUIT)

                if (branch > 0f) {
                    drawBranch(
                        start = start,
                        end = Offset(circleCenter.x, circleCenter.y + radius),
                        growth = branch,
                        color = colors.brand.colorBrand,
                        strokePx = 3.2f * uiScale,
                    )
                }
                if (orbit > 0f) {
                    drawArc(
                        color = colors.brand.colorBrand,
                        startAngle = -90f,
                        sweepAngle = 360f * orbit,
                        useCenter = false,
                        topLeft = Offset(circleCenter.x - radius, circleCenter.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 3.2f * uiScale),
                    )
                }
                if (fruit > 0f) {
                    val fruitRadius = 6.5f * uiScale
                    fruitAngles.forEachIndexed { index, angle ->
                        val local = EnterAnimation.fruitLocalProgress(index, fruit)
                        if (local <= 0f) return@forEachIndexed
                        drawCircle(
                            color = colors.brand.colorBrand.copy(alpha = local),
                            radius = fruitRadius * local,
                            center = Offset(
                                circleCenter.x + radius * cos(angle).toFloat(),
                                circleCenter.y + radius * sin(angle).toFloat(),
                            ),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset { avatarOffset }
                    .size(avatarDp)
                    .graphicsLayer {
                        alpha = EnterAnimation.avatarAlpha(progress.value)
                    },
            ) {
                // 收尾帧渲染点：票 #120 只替换这一处，前四段与进度驱动不受影响。
                ProfileAvatarFinale(name = name, uiScale = uiScale)
            }
        }
    }
}

/**
 * 字符头像收尾帧的**唯一渲染接缝**（票 #115 → #120）。
 *
 * 当前实现：品牌色圆形底 + 姓名首字，零网络、零外部依赖。
 * 票 #120 引入 Coil 时只需替换本函数实现（签名可扩展 avatarUrl 等参数），
 * 枝条 → 绕圆 → 果实 → 页面淡入四段与进度逻辑完全不用动。
 */
@Composable
fun ProfileAvatarFinale(
    name: String?,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(colors.brand.colorBrand),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = EnterAnimation.avatarInitial(name),
            color = Color.White,
            fontSize = (30f * uiScale).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = NongyuTheme.fontFamily,
        )
    }
}

/** 二次贝塞尔逐点采样：Canvas 没有 partial-path API，按 growth 画到增长位置。 */
private fun DrawScope.drawBranch(
    start: Offset,
    end: Offset,
    growth: Float,
    color: Color,
    strokePx: Float,
) {
    val control = Offset(start.x - (end.x - start.x) * 0.35f, (start.y + end.y) / 2f)
    val t = growth.coerceIn(0f, 1f)
    val steps = 32
    val path = Path().apply { moveTo(start.x, start.y) }
    for (i in 1..steps) {
        val u = (i.toFloat() / steps) * t
        val inv = 1f - u
        path.lineTo(
            inv * inv * start.x + 2f * inv * u * control.x + u * u * end.x,
            inv * inv * start.y + 2f * inv * u * control.y + u * u * end.y,
        )
    }
    drawPath(path, color = color, style = Stroke(width = strokePx))
}
