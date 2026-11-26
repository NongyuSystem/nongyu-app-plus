package com.nongyu.appplus.profile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.profile.logic.RootForm
import kotlinx.coroutines.launch

/**
 * 个人主页「根系形态」的揭示进度句柄（票 #117）。
 *
 * 内部只有一条显式 [Animatable]（单位 px），与界面层规格 §8.2 A 的底栏高度动画同构：
 * - 拖拽期由手势直接 `snapTo` 跟手，零延迟；
 * - 松手后 `animateTo` 按阈值吸附到两态之一，走弹簧过渡。
 *
 * 不用 `animateDpAsState(targetValue = 条件)`：那种声明式写法每帧重读 spec，
 * 拖拽期把 spec 设成 0 会让松手那一帧瞬间跳变，正是 §8.2 A 记录过的坑。
 */
class ProfileRootFormProgress internal constructor(
    private val animatable: Animatable<Float, AnimationVector1D>,
) {
    /** 当前揭示高度（px）。在组合中读取会订阅重组，与 MainShell 的 barHeight 同口径。 */
    val value: Float get() = animatable.value

    /** 拖拽期跟手：直接跳到目标，不跑动画。 */
    suspend fun snapTo(px: Float) {
        animatable.snapTo(px.coerceAtLeast(0f))
    }

    /** 松手 / 取消后的吸附：弹簧过渡，起点就是拖拽末帧，不会跳变。 */
    suspend fun animateTo(px: Float) {
        animatable.animateTo(
            targetValue = px.coerceAtLeast(0f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
}

/** 在个人主页组合内记住唯一的揭示进度（切子页不销毁）。 */
@Composable
fun rememberProfileRootFormProgress(): ProfileRootFormProgress = remember {
    ProfileRootFormProgress(Animatable(0f))
}

/**
 * 两态揭示容器：上半 [card] 位置与高度恒定，下半 [entries] 从底边长出。
 *
 * ## 跟手路径
 * 只在容器本体挂 `pointerInput` + [detectVerticalDragGestures]，**不挂任何 clickable** ——
 * 界面层规格 §8.5 的「占位 clickable 吞手势」和 §8.3 D 都记过：一旦容器有消费点击的层，
 * 子节点的点击就收不到。`detectVerticalDragGestures` 在越过 touch slop 前不消费事件，
 * 于是画像字段与画像入口的普通点击照常命中；只有真正的竖直拖拽才被本层接管。
 * 刷新果实是 clickable（点按语义），上滑是竖直拖拽（形态语义），两者天然分流。
 *
 * ## 高度与占位
 * 卡片是本 Column 的第一个子节点、top 对齐，揭示区高度变化不会挤压卡片。
 * 揭示高度是本页内部状态，不向上抛给 MainShell 的 `navOccupiedHeight` —— 那条占位只在
 * 常态 + 高度稳定时上报（§8.4 C），本页复用它做底部留白，不逐帧写中间值，也不影响其它 Tab。
 *
 * @param revealHeight 展开态揭示区的固定高度（dp），由调用方按 uiScale 注入
 * @param uiScale 屏宽自适应系数，用于间距与圆角（与页面其余元素同口径）
 */
@Composable
fun ProfileRootForm(
    progress: ProfileRootFormProgress,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    revealHeight: Dp,
    uiScale: Float,
    card: @Composable () -> Unit,
    entries: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealedPx = with(density) { revealHeight.toPx() }
    val gestureModifier = Modifier.profileRootFormGesture(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        progress = progress,
        revealedPx = revealedPx,
        thresholdPx = RootForm.thresholdPxOf(revealedPx),
    )

    Column(
        modifier = Modifier.fillMaxWidth().then(gestureModifier),
        verticalArrangement = Arrangement.spacedBy((12f * uiScale).dp),
    ) {
        card()
        // 外层高度动画 = 揭示量；内层固定高度承载内容，动画期间从底部被裁掉。
        // 卡片始终在上方原位，"根系" 从下方长出而不是把卡片顶走。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { progress.value.toDp() })
                .clip(RoundedCornerShape((18f * uiScale).dp)),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(revealHeight)) {
                entries()
            }
        }
    }
}

/**
 * 竖直拖拽手势：跟手 snapTo / 松手 animateTo 两条路径都在这里显式分流。
 *
 * 用 rememberCoroutineScope 而不是在 `onDragEnd` 里直接挂起：Compose 的
 * `detectVerticalDragGestures` 只有 `onVerticalDrag` 是 suspend，结束/取消回调不是。
 */
@Composable
private fun Modifier.profileRootFormGesture(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    progress: ProfileRootFormProgress,
    revealedPx: Float,
    thresholdPx: Float,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(expanded, revealedPx, thresholdPx) {
        var totalDragPx = 0f
        detectVerticalDragGestures(
            onDragStart = { totalDragPx = 0f },
            onVerticalDrag = { _, dy ->
                // dy 是 Float 像素，Compose 的 y 轴向下为正；RootForm 约定上滑为正，故取负号。
                // onVerticalDrag 在本 Compose 版本不是 suspend 回调，跟手 snap 用 scope 投递，
                // 与 MainShell 的显式 Animatable 双路径同口径（拖拽期直接跟随，不跑动画）。
                totalDragPx -= dy
                val base = if (expanded) revealedPx else 0f
                val drift = RootForm.followDrift(expanded, totalDragPx, revealedPx)
                scope.launch { progress.snapTo(base + drift) }
            },
            onDragEnd = {
                val target = RootForm.snapTo(expanded, totalDragPx, thresholdPx)
                val nextExpanded = target == RootForm.EXPANDED
                if (nextExpanded != expanded) onExpandedChange(nextExpanded)
                // 吸附动画从拖拽末帧起跑：拖拽已 snapTo 到跟手位置，故无跳变。
                scope.launch { progress.animateTo(if (nextExpanded) revealedPx else 0f) }
            },
            onDragCancel = {
                // 手势被系统打断（如来电话）：回到当前稳定态，不改变两态语义。
                scope.launch { progress.animateTo(if (expanded) revealedPx else 0f) }
            },
        )
    }
}
