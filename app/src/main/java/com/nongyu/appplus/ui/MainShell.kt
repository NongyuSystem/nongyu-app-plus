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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.skin.LocalSkinResolution
import com.nongyu.appplus.skin.LocalSkinSource
import com.nongyu.appplus.skin.NavIconKey
import com.nongyu.appplus.ui.ai.NongyuOrb
import com.nongyu.appplus.ui.icon.NavIconGeometry
import com.nongyu.appplus.ui.icon.NavIconStrokes
import com.nongyu.appplus.ui.icon.drawNavIcon
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

    // 图标线宽来自**皮肤资源段**（票 #38 / ADR-0005 Q1=A）：逐资源回落内置默认。
    // 内置默认皮肤下这四个值都是 BuiltInSkin.BUILT_IN_ICON_STROKE（0.085f），
    // 与改造前 drawNavIcon 里的 `s * 0.085f` 完全一致 —— 因此视觉不变。
    // 注意：这里**不再调用** skin.resolve()。启动路径上的解析只发生一次
    // （MainActivity 在 setContent 前完成，ADR-0006），此处只读取已解析好的结果。
    // 曾在此处再 resolve 一次，导致冷启动多做一遍磁盘读 + 一遍日志。
    val skin = LocalSkinSource.current
    val resolution = LocalSkinResolution.current
    val iconStrokes = remember(skin, resolution) {
        NavIconStrokes.resolve(
            payload = resolution.resourcePayload(),
            builtIn = skin.builtIn.resources,
        )
    }

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground),
    ) {
        // 尺寸自适应：固定 dp 在不同 dp 宽度的设备上观感差异极大
        // （蓝叠 1920x1080@240dpi = 1280dp 宽，手机 1080x2400@420dpi ≈ 411dp 宽，
        //   同一组固定 dp 在大屏上显得很小、在小屏上显得很大）。
        // 这里以 411dp（典型手机）为基准，按屏宽缩放，并夹在合理区间内。
        // 上限压到 1.25：手机上 1.0，720dp 的平板/模拟器上约 1.25，
        // 避免大画布上整套 UI 被等比放大得过于笨重（之前 1.55 明显过大）。
        val uiScale = (maxWidth.value / 411f).coerceIn(0.85f, 1.25f)
        fun s(dp: Dp): Dp = (dp.value * uiScale).dp

        val navHeight = s(62.dp)
        val navPadding = s(18.dp)
        val navCorner = s(20.dp)
        // 球体也必须参与缩放，否则其他元素变大、球固定 120dp 会显得变小
        // 球体也要跟随导航栏一起缩：导航栏已从 74dp 降到 62dp，
        // 球若仍保持 120dp 会接近导航栏高度的 2 倍，视觉上明显过大。
        // 96dp 视觉上仍偏大（光晕外扩使观感大于几何尺寸），再收到 78dp。
        val orbSize = s(78.dp)
        // 输入框与导航栏完全同步（高/圆角/边距全部跟随 uiScale）
        val inputHeight by animateDpAsState(
            targetValue = navHeight,
            animationSpec = tween(420),
            label = "inputHeight",
        )
        val inputCorner by animateDpAsState(
            targetValue = if (mode == OrbMode.Input) navCorner else s(66.dp),
            animationSpec = tween(420),
            label = "inputCorner",
        )
        val iconSize = s(30.dp)
        val labelSize = (8f * uiScale).sp

        // 底栏顶边的实测位置（根坐标系像素），交给内容页做底部避让（需求 15）。
        // 必须实测：底栏高度、内边距、球体凸起都乘了 uiScale，写死 dp 会随屏宽漂移。
        var navTopY by remember { mutableStateOf<Float?>(null) }

        // 四个页面：目前只有「课表」有实体内容（地图 #11），其余仍为占位。
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 1) {
                com.nongyu.appplus.ui.timetable.TimetableScreen(navTopY = navTopY)
            }
        }

        // 底栏：贴底、2+2、中间留空。边界用 featheredEdge 虚化（无硬边框）。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 垂直内边距也走 s()：此前写死 18.dp，大屏上栏体本身缩了、边距没缩，比例失真。
                .padding(horizontal = navPadding, vertical = s(18.dp))
                .fillMaxWidth()
                .height(navHeight)
                .alpha(navAlpha)
                .featheredEdge(colors.navBar.colorItem, navCorner)
                .clip(RoundedCornerShape(navCorner))
                .background(colors.navBar.colorContainer)
                // 上报栏体顶边：注意本 Row 自身已含竖直 padding，
                // 取其顶层（含 padding 的外框）才能把导航栏占位一起让出去。
                .onGloballyPositioned { coords ->
                    val top = coords.positionInRoot().y
                    if (top > 0f && top != navTopY) navTopY = top
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(TABS[0], NavIconKey.Grid, selectedTab == 0, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Grid), Modifier.weight(1f)) { selectedTab = 0 }
            NavItem(TABS[1], NavIconKey.Calendar, selectedTab == 1, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Calendar), Modifier.weight(1f)) { selectedTab = 1 }
            Spacer(modifier = Modifier.width(s(96.dp)))
            NavItem(TABS[2], NavIconKey.Compass, selectedTab == 2, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Compass), Modifier.weight(1f)) { selectedTab = 2 }
            NavItem(TABS[3], NavIconKey.Person, selectedTab == 3, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Person), Modifier.weight(1f)) { selectedTab = 3 }
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 凸起偏移量按球径比例：球小则凸起少，避免球浮在导航栏之上脱节
                .offset(y = (orbSize.value * -0.22f).dp)
                .size(orbSize)
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
                diameter = orbSize,
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
                    // 与导航栏用同一套虚化边界，二者观感一致
                    .featheredEdge(colors.navBar.colorItem, inputCorner)
                    .clip(RoundedCornerShape(inputCorner))
                    .background(colors.surface.colorCard)
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
 * 边界虚化 Modifier：在控件外侧画多层「逐圈外扩 + alpha 递减」的圆角描边，
 * 等效于把边界向外糊开约 6.dp，边界自然过渡到背景，没有生硬的 1.dp 线条。
 *
 * 注意：Compose 没有「背景毛玻璃 / backdrop blur」能力
 * （Modifier.blur / RenderEffect 模糊的是控件自身，不是背后内容，且 API<31 失效），
 * 所以这里用「多层柔边」做出虚化观感，而不是真正的背景模糊。
 */
private fun Modifier.featheredEdge(tint: Color, corner: Dp): Modifier = this.drawBehind {
    val cornerPx = corner.toPx()
    val layers = 7
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()               // 1.0(最外) .. 0.14(最内)
        val expand = t * 6.dp.toPx()               // 外扩距离
        val a = (1f - t) * 0.10f                   // 越外越淡
        drawRoundRect(
            color = tint.copy(alpha = a),
            topLeft = Offset(-expand, -expand * 0.7f),
            size = Size(size.width + expand * 2f, size.height + expand * 1.4f),
            cornerRadius = CornerRadius(cornerPx + expand, cornerPx + expand),
            style = Stroke(width = 1.2.dp.toPx() * (1f - t * 0.5f)),
        )
    }
}

/**
 * 单个导航项：图标 + 文案。
 * 尺寸：图标 26.dp（原 20.dp 偏小），文字 9.sp（原 10.sp 偏大）。
 * 选中态：图标与文字都用品牌色，未选中用低透明度中性色。
 */
@Composable
private fun NavItem(
    label: String,
    icon: NavIconKey,
    selected: Boolean,
    itemColor: Color,
    indicatorColor: Color,
    iconSize: Dp,
    labelSize: TextUnit,
    iconStroke: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) indicatorColor else itemColor
    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
        // 整体下移：导航栏本身是垂直居中，加一点顶部内边距把图标+文字压向下方。
        // 偏移量随图标尺寸等比，避免不同 uiScale 下偏得不一样。
        verticalArrangement = Arrangement.Center,
    ) {
        // 整体轻微下移；幅度取图标尺寸的 0.07（上一版 0.16 明显过头）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (iconSize.value * 0.07f).dp),
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                drawNavIcon(
                    geometry = NavIconGeometry.builtInOf(icon),
                    tint = tint,
                    strokeScale = iconStroke,
                )
            }
            // 图标与文字继续靠近：不留 Spacer，用压缩行高让文字紧贴图标下缘
            Text(
                text = label,
                color = tint,
                fontSize = labelSize,
                style = TextStyle(lineHeight = labelSize * 0.92f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainShellPreview() {
    NongyuAppPlusTheme { MainShell() }
}
