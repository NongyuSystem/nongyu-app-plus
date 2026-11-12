package com.nongyu.appplus.feature.login

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuColors
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 登录中心界面组件（票 #91，按维护者视觉要求重做）。
 *
 * 布局自上而下：
 * 1. 上方「切换方式」：三个方式 tab，底下一条绿色圆角滑块用 spring 果冻动画
 *    向选中方向移动（[JellyTabRow]）；
 * 2. **中间固定登录位**：标题 + 输入卡片，高度固定，不随方式切换跳动；
 * 3. **中间偏下**两个圆角矩形按钮：登录 / 切换登录渠道（[VectorButton]）；
 * 4. 其余控件（按钮按下、滑块）都带 spring 动效。
 *
 * 所有图形（滑块、按钮底、图标、卡片描边）都用 Compose `Canvas`/`drawRoundRect`
 * 等**矢量绘制**，等价于 SVG path，不引任何图片/SVG 解析依赖。
 * 配色只取 [NongyuTheme.colors] 色板 token（Q6=A），无硬编码十六进制。
 */
@Composable
fun LoginCenterScreen() {
    val colors = NongyuTheme.colors
    var uiState by remember { mutableStateOf(LoginCenterUiState.initial()) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground)
            .padding(horizontal = 24.dp),
    ) {
        val maxW = maxWidth

        // ---- 上方：三方式切换（带果冻绿色滑块）----
        JellyTabRow(
            methods = uiState.methods,
            selected = uiState.selectedMethod,
            colors = colors,
            width = maxW,
            onSelect = { uiState = uiState.onMethodSelected(it) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )

        // ---- 中间固定登录位 ----
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-36).dp) // 固定在中间，不随内容跳动
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "登录中心",
                color = colors.surface.colorText,
                fontSize = 24.sp,
            )
            Spacer(Modifier.height(20.dp))
            LoginCard(
                label = LoginCenterUiState.labelOf(uiState.selectedMethod),
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- 中间偏下：两个圆角矩形按钮 ----
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 132.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VectorButton(
                text = "登录",
                icon = ButtonIcon.Login,
                colors = colors,
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* S3/S4/S7 接登录提交 */ },
            )
            Spacer(Modifier.height(14.dp))
            VectorButton(
                text = "切换登录渠道",
                icon = ButtonIcon.Switch,
                colors = colors,
                filled = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = { uiState = uiState.onRealmSwitched() },
            )
        }
    }
}

/** 按钮内的矢量图标种类。 */
private enum class ButtonIcon { Login, Switch }

/**
 * 三方式 tab：底部一条绿色滑块，用 spring（低阻尼 = 果冻）向选中方向移动。
 *
 * 滑块移动中额外做 scaleX 回弹，制造「果冻被拉长再弹回」的质感。
 * tab 文本本身不切后端 method（由 [LoginCenterUiState] 保证）。
 */
@Composable
private fun JellyTabRow(
    methods: List<LoginMethod>,
    selected: LoginMethod,
    colors: NongyuColors,
    width: Dp,
    onSelect: (LoginMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tabCount = methods.size.coerceAtLeast(1)
    val tabWidth = width / tabCount
    val selectedIndex = methods.indexOf(selected).coerceAtLeast(0)

    // 果冻位移：dampingRatio 低 + 中等 stiffness → 过冲回弹
    val jellySpec = spring<Dp>(dampingRatio = 0.5f, stiffness = 320f)
    val sliderOffset by animateDpAsState(
        targetValue = tabWidth * selectedIndex,
        animationSpec = jellySpec,
        label = "tabSlider",
    )
    // 移动中的横向拉伸（果冻质感）
    val sliderStretch by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 260f),
        label = "tabStretch",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.colorCard)
            .border(1.dp, colors.surface.colorOutline, RoundedCornerShape(16.dp)),
    ) {
        // 绿色滑块（矢量绘制的圆角矩形，位于 tab 下层）
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .offset(x = sliderOffset)
                .width(tabWidth)
                .height(56.dp)
                .graphicsLayer {
                    scaleX = sliderStretch
                    // 以左侧为锚点拉伸，避免整块漂移
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .padding(4.dp),
        ) {
            drawRoundRect(
                color = colors.brand.colorBrand,
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                size = Size(size.width, size.height),
            )
        }

        // 三个 tab 文本（矢量滑块上层）
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
            methods.forEach { method ->
                val enabled = LoginCenterUiState.isEnabled(method)
                val isSelected = method == selected
                val textColor = when {
                    !enabled -> colors.surface.colorTextSecondary.copy(alpha = 0.45f)
                    isSelected -> colors.brand.colorOnBrand
                    else -> colors.surface.colorTextSecondary
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (enabled) Modifier.clickable { onSelect(method) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = LoginCenterUiState.labelOf(method),
                        color = textColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 中间固定的登录卡片（矢量圆角矩形 + 描边），高度固定不跳动。 */
@Composable
private fun LoginCard(
    label: String,
    colors: NongyuColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.colorCard)
            .border(1.dp, colors.surface.colorOutline, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 卡片内的矢量占位图标（小圆角方块）
        androidx.compose.foundation.Canvas(modifier = Modifier.size(40.dp)) {
            drawRoundRect(
                color = colors.brand.colorBrandMuted,
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )
            drawRoundRect(
                color = colors.brand.colorBrand,
                topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                size = Size(20.dp.toPx(), 20.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }
        Text(
            text = label,
            color = colors.surface.colorTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.offset(y = 40.dp),
        )
    }
}

/**
 * 圆角矩形按钮：背景与左侧图标都用矢量绘制；按下有 spring 缩放动效。
 * filled = 主按钮（品牌绿实心）；否则为描边次按钮。
 */
@Composable
private fun VectorButton(
    text: String,
    icon: ButtonIcon,
    colors: NongyuColors,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "buttonScale",
    )
    val bg = if (filled) colors.brand.colorBrand else colors.surface.colorCard
    val contentColor = if (filled) colors.brand.colorOnBrand else colors.surface.colorText
    val borderColor = if (filled) colors.brand.colorBrand else colors.surface.colorOutline

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                drawButtonIcon(icon, contentColor)
            }
            Spacer(Modifier.width(10.dp))
            Text(text = text, color = contentColor, fontSize = 14.sp)
        }
    }
}

/** 矢量图标：登录（箭头进门）/ 切换（双向箭头），等价于 SVG path。 */
private fun DrawScope.drawButtonIcon(icon: ButtonIcon, color: Color) {
    val s = size.minDimension
    when (icon) {
        ButtonIcon.Login -> {
            // 门框 + 向右箭头
            drawRoundRect(
                color = color,
                topLeft = Offset(s * 0.18f, s * 0.12f),
                size = Size(s * 0.40f, s * 0.76f),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.07f),
            )
            drawLine(
                color = color,
                start = Offset(s * 0.52f, s * 0.5f),
                end = Offset(s * 0.88f, s * 0.5f),
                strokeWidth = s * 0.07f,
            )
            drawLine(
                color = color,
                start = Offset(s * 0.74f, s * 0.38f),
                end = Offset(s * 0.88f, s * 0.5f),
                strokeWidth = s * 0.07f,
            )
            drawLine(
                color = color,
                start = Offset(s * 0.74f, s * 0.62f),
                end = Offset(s * 0.88f, s * 0.5f),
                strokeWidth = s * 0.07f,
            )
        }
        ButtonIcon.Switch -> {
            // 上下两条反向箭头
            drawLine(color, Offset(s * 0.15f, s * 0.34f), Offset(s * 0.85f, s * 0.34f), s * 0.07f)
            drawLine(color, Offset(s * 0.70f, s * 0.20f), Offset(s * 0.85f, s * 0.34f), s * 0.07f)
            drawLine(color, Offset(s * 0.85f, s * 0.66f), Offset(s * 0.15f, s * 0.66f), s * 0.07f)
            drawLine(color, Offset(s * 0.30f, s * 0.80f), Offset(s * 0.15f, s * 0.66f), s * 0.07f)
        }
    }
}
