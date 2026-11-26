package com.nongyu.appplus.profile.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.profile.logic.ProfileEntryGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 票 #118：土豆状画像入口果实按钮。
 *
 * ## 所见即所点
 * 绘制与命中都调用 [ProfileEntryGeometry.absoluteEdgeRadiusAt] 的同一份采样：
 * Canvas 用极坐标逐点连成封闭 [Path]；`pointerInput` 对触点做同样的极坐标判定。
 * 不存在「画的是土豆、点的是矩形代理」的分叉。
 *
 * ## 与 #117 上滑手势共存
 * 本按钮只用 `detectTapGestures`（点按语义），**不挂 clickable**，也不消费竖直拖动；
 * 父容器的 `detectVerticalDragGestures` 照常接收真正的上滑/下拉。触摸未越过 touch slop
 * 时孩子判为点按，越过后父容器接管，两者天然分流。
 *
 * ## 无障碍
 * 合并语义并显式给出 [Role.Button] 与 [contentDescription]，TalkBack 读出入口名称。
 * 按下态用缩放反馈（spring），刷新中显示进度环且不再响应重复点击。
 *
 * @param label 果实上显示的短文字（如「关于农屿」「校内信息」「刷新」）
 * @param accessibilityLabel TalkBack 读出的入口名称（如「刷新画像」）
 * @param angleOffset 土豆凸起的朝向（弧度），让三颗果实形态各异但不重叠
 * @param enabled 是否可点（刷新进行中为 false）
 * @param busy 是否显示刷新进度环
 */
@Composable
internal fun PotatoFruitButton(
    label: String,
    accessibilityLabel: String,
    uiScale: Float,
    angleOffset: Float,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    val density = LocalDensity.current
    val sizeDp: Dp = ProfileEntryGeometry.hotZoneSizeDp(uiScale).dp
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f),
        label = "potatoFruitPressScale",
    )
    val tolerancePx = with(density) { ProfileEntryGeometry.DEFAULT_HIT_TOLERANCE_PX.dp.toPx() }

    Box(
        modifier = modifier
            .size(sizeDp)
            .pointerInput(enabled, angleOffset, tolerancePx) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        }
                    },
                    onTap = { tapOffset ->
                        if (!enabled) return@detectTapGestures
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radiusX = centerX * SHAPE_SCALE
                        val radiusY = centerY * SHAPE_SCALE
                        val hit = ProfileEntryGeometry.isInsidePotatoWithTolerance(
                            x = tapOffset.x - centerX,
                            y = tapOffset.y - centerY,
                            radiusX = radiusX,
                            radiusY = radiusY,
                            angleOffset = angleOffset,
                            tolerancePx = tolerancePx,
                        )
                        if (hit) onClick()
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibilityLabel
                // TalkBack 双击激活走这里；触摸点击仍走上面的 pointerInput，二者共用 onClick 回调。
                semanticsOnClick {
                    if (enabled) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 缩放只作用在视觉层，不作用于 pointerInput 节点：命中坐标始终稳定。
        PotatoBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
            angleOffset = angleOffset,
            busy = busy,
            pressed = pressScale < 0.995f,
        )
        if (busy) {
            // 保持 #114 既有语义：刷新中既显示进度环，也显示文字说明。
            val (busyHalfWidth, _) = ProfileEntryGeometry.textSafeHalfExtent(
                radiusX = sizeDp.value / 2f,
                radiusY = sizeDp.value / 2f,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.widthIn(max = (busyHalfWidth * 2f).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size((14f * uiScale).dp),
                    color = colors.brand.colorOnBrand,
                    strokeWidth = (2f * uiScale).dp,
                )
                Text(
                    text = label,
                    color = colors.brand.colorOnBrand,
                    fontSize = (10f * uiScale).sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
            // 文字安全区按热区方块的内切矩形算（不按收缩后的绘制半径），
            // 四字标签在最小缩放下也落在安全区内；maxLines=1 + softWrap=false 保证不换行溢出。
            val (safeHalfWidth, _) = ProfileEntryGeometry.textSafeHalfExtent(
                radiusX = sizeDp.value / 2f,
                radiusY = sizeDp.value / 2f,
            )
            Text(
                text = label,
                color = colors.brand.colorOnBrand,
                fontSize = (12f * uiScale).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(max = (safeHalfWidth * 2f).dp),
            )
        }
    }
}

/** 绘制缩放：凸起峰值外扩约 18%，收缩后恰好不越出热区方块。 */
private const val SHAPE_SCALE = 0.84f

/** 轮廓采样点数：足够平滑，又不至于每帧生成过多路径段。 */
private const val OUTLINE_SAMPLES = 72

@Composable
private fun PotatoBackdrop(
    modifier: Modifier,
    angleOffset: Float,
    busy: Boolean,
    pressed: Boolean,
) {
    val colors = NongyuTheme.colors
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radiusX = centerX * SHAPE_SCALE
        val radiusY = centerY * SHAPE_SCALE
        val outline = Path().apply {
            for (i in 0 until OUTLINE_SAMPLES) {
                val angle = ((2.0 * PI * i) / OUTLINE_SAMPLES).toFloat()
                val r = ProfileEntryGeometry.absoluteEdgeRadiusAt(angle, angleOffset, radiusX, radiusY)
                // cos/sin 返回 Double，乘 Float 半径后仍是 Double；Path 需要 Float，显式收敛。
                val x = (centerX + cos(angle) * r).toFloat()
                val y = (centerY + sin(angle) * r).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = outline,
            color = if (busy) colors.brand.colorBrandMuted else colors.brand.colorBrand,
        )
        // 按下态叠一层描边，给除了缩放之外的第二重视觉反馈。
        if (pressed) {
            drawPath(
                path = outline,
                color = colors.surface.colorOutline,
                style = Stroke(width = 1.5f),
            )
        }
        // 芽点高光：一小块浅色圆，强化「土豆」的视觉语义，位置与凸起朝向一致。
        val sproutAngle = ProfileEntryGeometry.LOBE_PEAK_ANGLE + angleOffset
        val sproutR = ProfileEntryGeometry.absoluteEdgeRadiusAt(
            sproutAngle, angleOffset, radiusX, radiusY,
        ) * 0.72f
        drawCircle(
            color = colors.brand.colorOnBrand.copy(alpha = 0.18f),
            radius = centerX * 0.12f,
            center = Offset(
                (centerX + cos(sproutAngle) * sproutR).toFloat(),
                (centerY + sin(sproutAngle) * sproutR).toFloat(),
            ),
        )
    }
}
