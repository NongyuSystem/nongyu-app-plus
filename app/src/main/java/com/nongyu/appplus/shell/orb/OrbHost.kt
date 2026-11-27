package com.nongyu.appplus.shell.orb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.shell.OrbMode

/**
 * 星云球宿主：**全屏 dim + 雷达波 + 正中凸起的球体 + 手势状态机**。
 *
 * 从 `MainShell` 原样搬出，行为一行未改。这块与底栏、卡片完全解耦，
 * 只依赖「球多大 / 缩到多少 / 透多少 / 当前哪一态」五个输入。
 *
 * ## 三条实测约束（改之前必读）
 *
 * 1. **不能 `clip(CircleShape)`**：星云球边缘在渲染层已羽化到透明，
 *    硬裁剪会把软边切成 3D 球的硬圆轮廓。
 * 2. **只在常态（[enabled]）接收触摸**：球浮在内容之上，否则它的手势区
 *    盖在课表底部，点/长按课程卡片会被判成星云球的操作（维护者反馈）。
 * 3. **松手必复位**：`onPress` 里 `tryAwaitRelease()` 之后若本轮判过长按，
 *    一定要回 Idle —— 否则长按态可能卡死。点按态（Input）要保留，不能复位。
 *
 * ## 层级顺序（不能调）
 *
 * ```text
 * dim（长按态全屏压暗，盖住底栏）
 * 雷达波（画在 dim 之上、球体之下）
 * 球体（最上层，激活态为最高层）
 * ```
 *
 * @param orbSize 球径（已乘 uiScale）
 * @param scale 缩放（含溶解缩小的合成值）
 * @param alpha 透明度（含溶解淡出的合成值）
 * @param dimAlpha 长按态全屏压暗的 0..1 进度
 * @param pulse 雷达波进度 0..1；不在 (0,1) 区间时不画
 * @param pulseExpand true = 向外扩散（进入激活态），false = 向内回收（退出）
 * @param mode 当前星云球状态
 * @param onModeChange 状态变更回调（由 Shell 写回 `mode`）
 * @param onPulse 触发一次雷达波（`pulseExpand` + `pulseKey++`）
 * @param enabled 是否接收手势（= 底栏处于常态）
 */
@Composable
fun OrbHost(
    orbSize: Dp,
    scale: Float,
    alpha: Float,
    dimAlpha: Float,
    pulse: Float,
    pulseExpand: Boolean,
    mode: OrbMode,
    onModeChange: (OrbMode) -> Unit,
    onPulse: (expand: Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // 长按态的全屏 dim：画在底栏之后，把整条导航栏也一起压暗
    if (dimAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(dimAlpha)
                .background(Color(0xFF0A140F)),
        )
    }

    // 全屏雷达波：进入时向外扩散、退出时向内回收。
    // 画在 dim 之后、球体之前 —— 压在暗色后景上、位于球体下层。
    if (pulse > 0f && pulse < 1f) {
        val pv = pulse
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            // 圆心与球心保持一致：用与球 Box 相同的偏移比例
            val cy = size.height - (orbSize.value * 0.22f).dp.toPx() - (orbSize / 2).toPx()
            val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
            val r0 = (orbSize / 2).toPx()              // 球体边缘
            // 浅绿波纹：暗底上不能用深品牌绿，否则显得很脏很沉。
            val ringColor = Color(0xFF7FD9A8)

            val r: Float
            val ringAlpha: Float
            if (pulseExpand) {
                // 扩散：球 → 整屏，随扩散淡出
                val ease = 1f - (1f - pv) * (1f - pv)  // easeOut，前段冲得快
                r = r0 + ease * maxR
                ringAlpha = (1f - pv) * 0.34f
            } else {
                // 回收：整屏 → 球，越靠近球体越亮，末段快速收敛
                val ease = pv * pv                     // easeIn，后段收得快
                r = r0 + (1f - ease) * maxR
                ringAlpha = pv * 0.34f
            }

            // 一圈亮环 + 一圈更宽更淡的伴随环，形成雷达波纹
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5.dp.toPx()),
            )
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha * 0.40f),
                radius = r * 0.86f,
                center = Offset(cx, cy),
                style = Stroke(width = 9.dp.toPx()),
            )
        }
    }

    // 正中凸起的农小屿星云球（激活态为最高层）。
    // 手势状态机：按下 → 超过长按阈值进入 Listening（加速 + 放大 + 全屏 dim）；
    // 手指抬起 / 手势取消时**必定**复位到 Idle（onPress 的 tryAwaitRelease 兜底）。
    // 短按才切换 Input（扩开为输入框）。二者互斥，避免长按态卡死。
    // 注意：外层 Box(fillMaxSize) 只用来承接调用方传入的 `align(BottomCenter)`，
    // 球本身的位置与偏移量与原实现一致，没有引入新的布局层。
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 凸起偏移量按球径比例：球小则凸起少，避免球浮在导航栏之上脱节
                .offset(y = (orbSize.value * -0.22f).dp)
                .size(orbSize)
                .scale(scale)
                .alpha(alpha)
                // 见类注释第 1 条：不 clip(CircleShape)
                // 见类注释第 2 条：只在常态接收触摸
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            // longPressed 记录本轮手势是否已判为长按，避免长按后又误触发 onTap。
                            var longPressed = false
                            detectTapGestures(
                                onPress = {
                                    longPressed = false
                                    // 按下后等待释放；抬手 / 移出 / 手势取消都会返回。
                                    // 只有长按态需要「松手后恢复」，点按态(Input)要保留。
                                    tryAwaitRelease()
                                    if (longPressed) {
                                        onModeChange(OrbMode.Idle)
                                        onPulse(false)   // 变亮：波向内回收
                                    }
                                },
                                onLongPress = {
                                    longPressed = true
                                    onModeChange(OrbMode.Listening)
                                    onPulse(true)        // 变黑：波向外扩散
                                },
                                onTap = {
                                    if (!longPressed) {
                                        onModeChange(
                                            if (mode == OrbMode.Input) OrbMode.Idle else OrbMode.Input,
                                        )
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            NongyuOrb(
                diameter = orbSize,
                speed = if (mode == OrbMode.Listening) 2.2f else 0.25f,
                // 激活态有全屏 dim，球边缘要消融进暗背景，不能留灰白亮边。
                dimmed = mode != OrbMode.Idle,
            )
        }
    }
}
