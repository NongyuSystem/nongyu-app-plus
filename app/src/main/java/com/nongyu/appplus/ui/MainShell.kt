package com.nongyu.appplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.ai.NongyuOrb
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 主框架与导航壳（概念验证版 0.0.1test）。
 *
 * 依据 #5 已答结论 + #10 农小屿三态定义：
 * - 底栏贴底、2+2 分列（全部/课表 · 探索/个人），中间留空给正中凸起的 AI 圆。
 * - 四个页面全白留空；冷启动默认「全部」，用零依赖 rememberSaveable 记住当前 Tab。
 * - 长按圆球：黄绿流动瞬间加速 + 球体微放大 + 后景全屏 dim，激活态最高层。
 * - 点按圆球：扩开覆盖原导航栏 → 四按钮淡出 → 变形为输入框（无左侧圆点），
 *   退出（返回键 / 再次点按）反向动画。
 *
 * 每个导航按钮的差异化动画按用户要求推迟，本版不做。
 * 语音转文字 / 后端 agent / 网络层不在本原型范围（用户「之后的事后面会说」）。
 */

private val TABS = listOf("全部", "课表", "探索", "个人")

private enum class OrbMode { Idle, Listening, Input }

@Composable
fun MainShell() {
    val colors = NongyuTheme.colors
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(OrbMode.Idle) }
    var inputText by rememberSaveable { mutableStateOf("") }

    // 雷达波：进入/退出激活态（变黑 / 变亮）时各发一圈快速扩散并淡出的圆环。
    // pulseKey 每次自增就重新跑一遍动画；pulse 从 0→1 驱动半径与透明度。
    var pulseKey by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }

    val active = mode != OrbMode.Idle

    // 退出激活态：返回键优先，其次再次点按。
    BackHandler(enabled = active) { mode = OrbMode.Idle }

    // 激活态切换时触发一次雷达波（变黑与变亮都发）。
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, animationSpec = tween(520))
        }
    }

    // 全屏 dim（第 2、3 激活态）
    val dimAlpha by animateFloatAsState(
        targetValue = if (active) 0.55f else 0f,
        animationSpec = tween(300),
        label = "dim",
    )

    // 点按态：圆球缩没、输入框展开
    val orbScale by animateFloatAsState(
        targetValue = when (mode) {
            OrbMode.Idle -> 1f
            OrbMode.Listening -> 1.08f
            OrbMode.Input -> 0.4f
        },
        animationSpec = tween(420),
        label = "orbScale",
    )
    val orbAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 0f else 1f,
        animationSpec = tween(300),
        label = "orbAlpha",
    )
    val navAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 0f else 1f,
        animationSpec = tween(280),
        label = "navAlpha",
    )
    val inputWidth by animateDpAsState(
        targetValue = if (mode == OrbMode.Input) 336.dp else 132.dp,
        animationSpec = tween(420),
        label = "inputWidth",
    )
    val inputHeight by animateDpAsState(
        targetValue = 64.dp,
        animationSpec = tween(420),
        label = "inputHeight",
    )
    val inputCorner by animateDpAsState(
        targetValue = if (mode == OrbMode.Input) 22.dp else 66.dp,
        animationSpec = tween(420),
        label = "inputCorner",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground),
    ) {
        // 四个页面：全白留空
        Box(modifier = Modifier.fillMaxSize()) {
            // 仅作占位，无任何业务内容
        }

        // 底栏：贴底、2+2、中间留空
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 18.dp)
                .fillMaxWidth()
                .height(64.dp)
                .alpha(navAlpha)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.navBar.colorContainer)
                .border(1.dp, colors.surface.colorOutline, RoundedCornerShape(22.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(TABS[0], selectedTab == 0, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 0 }
            NavItem(TABS[1], selectedTab == 1, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 1 }
            Spacer(modifier = Modifier.width(96.dp))
            NavItem(TABS[2], selectedTab == 2, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 2 }
            NavItem(TABS[3], selectedTab == 3, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 3 }
        }

        // 全屏 dim：画在底栏**之后**，才能把导航栏一起压暗。
        // （此前画在导航栏之前，导航栏永远不变暗。）
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(dimAlpha)
                    .background(Color(0xFF0A140F)),
            )
        }

        // 全屏雷达波：从球心（屏幕底部中央）向外扩散到整屏并淡出。
        // 画在 dim 之后、球体之前 —— 压在暗色后景上、位于球体下层。
        if (pulse.value > 0f && pulse.value < 1f) {
            val pv = pulse.value
            // 扩散半径：从球半径一直覆盖到整屏对角线，保证「全屏」。
            val ease = 1f - (1f - pv) * (1f - pv)   // easeOut，前段冲得快
            val ringAlpha = (1f - pv) * 0.55f        // 随扩散淡出
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height - 26.dp.toPx() - 60.dp.toPx()
                val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                val r = 60.dp.toPx() + ease * maxR
                // 一圈亮环 + 一圈更宽更淡的伴随环，形成雷达波纹
                drawCircle(
                    color = colors.brand.colorBrand.copy(alpha = ringAlpha),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = colors.brand.colorBrand.copy(alpha = ringAlpha * 0.45f),
                    radius = r * 0.86f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 10.dp.toPx()),
                )
            }
        }

        // 正中凸起的农小屿星云球（激活态为最高层）。
        // 手势状态机：按下 → 超过长按阈值进入 Listening（加速 + 放大 + 全屏 dim）；
        // 手指抬起 / 手势取消时**必定**复位到 Idle（onPress 的 tryAwaitRelease 兜底）。
        // 短按才切换 Input（扩开为输入框）。二者互斥，避免长按态卡死。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-26).dp)
                .size(120.dp)
                .scale(orbScale)
                .alpha(orbAlpha)
                // 不 clip(CircleShape)：星云球边缘在渲染层已羽化到透明，
                // 硬裁剪会把软边切成 3D 球的硬圆轮廓。
                .pointerInput(Unit) {
                    // longPressed 记录本轮手势是否已判为长按，避免长按后又误触发 onTap。
                    var longPressed = false
                    detectTapGestures(
                        onPress = {
                            longPressed = false
                            // 按下后等待释放；抬手 / 移出 / 手势取消都会返回。
                            // 只有长按态需要「松手后恢复」，点按态(Input)要保留。
                            tryAwaitRelease()
                            if (longPressed) {
                                mode = OrbMode.Idle
                                pulseKey++   // 变亮时再发一圈雷达波
                            }
                        },
                        onLongPress = {
                            longPressed = true
                            mode = OrbMode.Listening
                            pulseKey++       // 变黑时发一圈雷达波
                        },
                        onTap = {
                            if (!longPressed) {
                                mode = if (mode == OrbMode.Input) OrbMode.Idle else OrbMode.Input
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            NongyuOrb(
                diameter = 120.dp,
                speed = if (mode == OrbMode.Listening) 2.2f else 0.25f,
                // 激活态有全屏 dim，球边缘要消融进暗背景，不能留灰白亮边。
                dimmed = active,
            )
        }

        // 点按态输入框：圆球扩开覆盖原导航栏后变形而来；无左侧圆点
        AnimatedVisibility(
            visible = mode == OrbMode.Input,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 18.dp),
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                modifier = Modifier
                    .width(inputWidth)
                    .height(inputHeight)
                    .clip(RoundedCornerShape(inputCorner))
                    .background(colors.surface.colorCard)
                    .border(1.dp, colors.surface.colorOutline, RoundedCornerShape(inputCorner))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "输入想问农小屿的问题…",
                        color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = SolidColor(colors.brand.colorBrand),
                    singleLine = true,
                )
            }
        }
    }
}

/** 单个导航项：图标占位 + 文案；选中态用品牌色（两套独立行为中的「导航项」那一套）。 */
@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    itemColor: Color,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) indicatorColor else itemColor.copy(alpha = 0.35f)),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) indicatorColor else itemColor,
            fontSize = 10.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainShellPreview() {
    NongyuAppPlusTheme { MainShell() }
}
