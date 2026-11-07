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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

    // 雷达波：状态切换时的圆环反馈。
    // - 进入激活态（变黑）：从球心**向外扩散**并淡出
    // - 退出激活态（变亮）：从外侧**向内回收**到球体（呼应"能量收回"，不是再释放一次）
    // pulseKey 每次自增重跑动画；pulseExpand 标记本轮方向。
    var pulseKey by remember { mutableIntStateOf(0) }
    var pulseExpand by remember { mutableStateOf(true) }
    val pulse = remember { Animatable(0f) }

    val active = mode != OrbMode.Idle

    // 退出激活态：返回键优先，其次再次点按。
    BackHandler(enabled = active) { mode = OrbMode.Idle }

    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, animationSpec = tween(if (pulseExpand) 520 else 460))
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
    // 输入框展开：宽度交给父布局（与导航栏同宽），只用 scaleX 做展开动画。
    val inputScale by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 1f else 0.18f,
        animationSpec = tween(420),
        label = "inputScale",
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

        // 底栏：贴底、2+2、中间留空。
        // 尺寸：高 74.dp、左右各 18.dp（比上一版的 10.dp 各缩 8dp，整体更窄）。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .fillMaxWidth()
                .height(74.dp)
                .alpha(navAlpha)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.navBar.colorContainer)
                .border(1.dp, colors.surface.colorOutline, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(TABS[0], NavIcon.Grid, selectedTab == 0, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 0 }
            NavItem(TABS[1], NavIcon.Calendar, selectedTab == 1, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 1 }
            Spacer(modifier = Modifier.width(96.dp))
            NavItem(TABS[2], NavIcon.Compass, selectedTab == 2, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 2 }
            NavItem(TABS[3], NavIcon.Person, selectedTab == 3, colors.navBar.colorItem, colors.navBar.colorIndicator, Modifier.weight(1f)) { selectedTab = 3 }
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

        // 全屏雷达波：进入时向外扩散、退出时向内回收。
        // 画在 dim 之后、球体之前 —— 压在暗色后景上、位于球体下层。
        if (pulse.value > 0f && pulse.value < 1f) {
            val pv = pulse.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height - 26.dp.toPx() - 60.dp.toPx()
                val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                val r0 = 60.dp.toPx()                      // 球体边缘
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
                                pulseExpand = false   // 变亮：波向内回收
                                pulseKey++
                            }
                        },
                        onLongPress = {
                            longPressed = true
                            mode = OrbMode.Listening
                            pulseExpand = true        // 变黑：波向外扩散
                            pulseKey++
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

        // 点按态输入框：与导航栏**同宽同位置**（同样的 horizontal=18.dp + fillMaxWidth）。
        // 关键：AnimatedVisibility 必须自己也 fillMaxWidth，否则它按内容测量，
        // 内部 Box 的 fillMaxWidth 会在收缩后的约束里再算一次，结果比导航栏窄。
        AnimatedVisibility(
            visible = mode == OrbMode.Input,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(inputHeight)
                    .graphicsLayer {
                        scaleX = inputScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
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

/**
 * 导航图标的种类。
 * 图标一律用 Canvas 手绘（零依赖、可随主题着色），不用 Material Icons
 * （那需要额外依赖，且与「皮肤」抽象冲突）。
 * 后续做皮肤时，替换这里各分支的绘制即可 —— 图标属于皮肤资源的一部分。
 */
private enum class NavIcon { Grid, Calendar, Compass, Person }

/**
 * 单个导航项：图标 + 文案。
 * 尺寸：图标 26.dp（原 20.dp 偏小），文字 9.sp（原 10.sp 偏大）。
 * 选中态：图标与文字都用品牌色，未选中用低透明度中性色。
 */
@Composable
private fun NavItem(
    label: String,
    icon: NavIcon,
    selected: Boolean,
    itemColor: Color,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) indicatorColor else itemColor
    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            drawNavIcon(icon, tint)
        }
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
        )
    }
}

/**
 * 绘制导航图标。全部按 26x26 的画布设计，用相对尺寸保证任意大小都清晰。
 * 统一线性风格（stroke 1.8dp），与「白绿简洁」的主题一致。
 */
private fun DrawScope.drawNavIcon(icon: NavIcon, tint: Color) {
    val s = size.minDimension
    val sw = s * 0.085f                       // 线宽随画布缩放
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (icon) {
        // 全部：四宫格（2x2 圆角方块）
        NavIcon.Grid -> {
            val gap = s * 0.12f
            val cell = (s - gap * 3f) / 2f
            val r = CornerRadius(cell * 0.28f, cell * 0.28f)
            for (i in 0..1) for (j in 0..1) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(gap + i * (cell + gap), gap + j * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = r,
                    style = stroke,
                )
            }
        }
        // 课表：日历（外框 + 两条挂钉 + 一条横向分隔）
        NavIcon.Calendar -> {
            val l = s * 0.16f
            val t = s * 0.22f
            val w = s - l * 2f
            val h = s - t - s * 0.16f
            drawRoundRect(
                color = tint,
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(s * 0.14f, s * 0.14f),
                style = stroke,
            )
            // 顶部两个挂钉
            drawLine(tint, Offset(s * 0.34f, s * 0.10f), Offset(s * 0.34f, s * 0.30f), sw, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.66f, s * 0.10f), Offset(s * 0.66f, s * 0.30f), sw, StrokeCap.Round)
            // 横向分隔线
            drawLine(tint, Offset(l, s * 0.44f), Offset(l + w, s * 0.44f), sw, StrokeCap.Round)
        }
        // 探索：指南针（外圆 + 内部指针菱形）
        NavIcon.Compass -> {
            val c = Offset(s / 2f, s / 2f)
            val r = s * 0.34f
            drawCircle(tint, r, c, style = stroke)
            val d = s * 0.20f
            drawLine(tint, Offset(c.x, c.y - d), Offset(c.x + d * 0.72f, c.y + d), sw, StrokeCap.Round)
            drawLine(tint, Offset(c.x + d * 0.72f, c.y + d), Offset(c.x, c.y + d * 0.10f), sw, StrokeCap.Round)
            drawLine(tint, Offset(c.x, c.y + d * 0.10f), Offset(c.x - d * 0.72f, c.y - d), sw, StrokeCap.Round)
            drawLine(tint, Offset(c.x - d * 0.72f, c.y - d), Offset(c.x, c.y - d), sw, StrokeCap.Round)
        }
        // 个人：人形（圆头 + 肩部圆弧，用 drawArc 避免 Path/drawPath 的依赖问题）
        NavIcon.Person -> {
            val headR = s * 0.15f
            drawCircle(tint, headR, Offset(s / 2f, s * 0.33f), style = stroke)
            // 肩：以画布下半部为中心的半圆弧
            val arcW = s * 0.62f
            val arcH = s * 0.46f
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset((s - arcW) / 2f, s * 0.52f),
                size = Size(arcW, arcH),
                style = stroke,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainShellPreview() {
    NongyuAppPlusTheme { MainShell() }
}
