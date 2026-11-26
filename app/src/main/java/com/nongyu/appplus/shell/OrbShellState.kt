package com.nongyu.appplus.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 星云球的**动画参数团**：雷达波（pulse）、溶解/汇聚（dissolve）、以及随 [OrbMode]
 * 变化的各淡入淡出 / 缩放系数。
 *
 * 从 `MainShell` 原样搬出，行为未改。原先这约 60 行 `Animatable` + `animateFloatAsState`
 * 内联在壳里。它们只服务星云球的视觉，与路由、命中层级无关，故整体抽成 state-holder。
 *
 * 实测踩过的坑（详见 `docs/specs/界面层.md` §8.3）：
 * - **溶解/汇聚用 `Animatable` 区分正反两个时长**（溶解 280ms / 汇聚 420ms），收拢感更自然；
 *   用 `animateFloatAsState` 单一时长会失去这个方向差异。
 * - 雷达波方向语义：进入激活态从球心向外扩散（520ms），退出从外向内回收（460ms）。
 * - `dim` 只在长按态出现且只保留一层；输入态的压暗由独立的 [inputScrimAlpha] 负责，
 *   两者不可合并，否则长按进输入态会叠出两层暗。
 *
 * @property pulse 雷达波进度（0→1 一轮）。
 * @property pulseExpand 本轮雷达波方向（true = 向外扩散）。
 * @property dissolve 溶解进度（0 常态 / 1 完全溶解），驱动球体缩放与透明。
 * @property pulseValue [pulse] 的当前值快照，供 `OrbHost` 读取。
 */
class OrbShellState(
    val pulse: Animatable<Float, AnimationVector1D>,
    val pulseExpand: Boolean,
    val dissolve: Animatable<Float, AnimationVector1D>,
    val pulseValue: Float,
    val orbDissolveAlpha: Float,
    val orbDissolveScale: Float,
    val dimAlpha: Float,
    val inputScrimAlpha: Float,
    val orbScale: Float,
    val orbAlpha: Float,
    val navAlpha: Float,
    private val onPulseAction: (Boolean) -> Unit,
) {
    /** 触发一轮雷达波；[expand] 指定方向。 */
    fun onPulse(expand: Boolean) = onPulseAction(expand)
}

/**
 * [OrbShellState] 的 Compose 工厂。
 *
 * @param mode 星云球三态（驱动各淡入淡出 / 缩放）。
 * @param barMode 底栏形态（驱动溶解/汇聚：常态汇聚回球、变形态溶解）。
 */
@Composable
fun rememberOrbShellState(
    mode: OrbMode,
    barMode: BarMode,
): OrbShellState {
    // 详见 docs/specs/界面层.md §8.3 C 雷达波方向语义
    var pulseKey by remember { mutableIntStateOf(0) }
    var pulseExpand by remember { mutableStateOf(true) }
    val pulse = remember { Animatable(0f) }
    val dissolve = remember { Animatable(0f) }

    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, animationSpec = tween(if (pulseExpand) 520 else 460))
        }
    }
    // 溶解/汇聚：进入变形态溶解（280ms），回常态汇聚（420ms）。
    LaunchedEffect(barMode) {
        if (barMode == BarMode.Normal) {
            dissolve.animateTo(0f, tween(420))   // 汇聚
        } else {
            dissolve.animateTo(1f, tween(280))   // 溶解
        }
    }

    // §8.3 E dim 只在长按态出现，且只保留一层
    val dimAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Listening) 0.55f else 0f,
        animationSpec = tween(300),
        label = "dim",
    )
    // 输入态：只把**底栏以外**压暗（让输入栏保持明亮、成为视觉焦点）。
    val inputScrimAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 0.35f else 0f,
        animationSpec = tween(300),
        label = "inputScrim",
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

    // 球体可见度：溶解时同时缩小并变透明，配合粒子外散。
    val orbDissolveAlpha = 1f - dissolve.value
    val orbDissolveScale = 1f + dissolve.value * 0.55f

    return OrbShellState(
        pulse = pulse,
        pulseExpand = pulseExpand,
        dissolve = dissolve,
        pulseValue = pulse.value,
        orbDissolveAlpha = orbDissolveAlpha,
        orbDissolveScale = orbDissolveScale,
        dimAlpha = dimAlpha,
        inputScrimAlpha = inputScrimAlpha,
        orbScale = orbScale,
        orbAlpha = orbAlpha,
        navAlpha = navAlpha,
        onPulseAction = { expand ->
            pulseExpand = expand
            pulseKey++
        },
    )
}
