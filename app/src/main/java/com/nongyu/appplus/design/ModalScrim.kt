package com.nongyu.appplus.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 可复用的模态遮罩（票 #60 抽出）。
 *
 * ## 为什么抽成组件
 *
 * 「拖时间条要压暗页面，但不能压暗时间条本身」和星云球的处理是同一个问题：
 * 遮罩要盖住页面，却要**放行几个关键元素**。
 * 每次靠「谁声明在前」来调图层顺序很容易改乱（本项目已经因此返工多次），
 * 所以做成一个显式声明「哪几块不遮」的组件。
 *
 * ## 用法（两种，二选一）
 *
 * 1. **按边距挖空**：把要放行的元素放在页面边缘/固定区域时，用 [excludeTop] /
 *    [excludeBottom] / [excludeStart] / [excludeEnd] 给出四边留白，遮罩只覆盖中间那块。
 * 2. **显式层级**：把本组件声明在需要放行的元素**之前**，放行元素自然浮在遮罩之上
 *    （与星云球遮罩同一套做法）。
 *
 * 本组件不挂任何 clickable / pointerInput —— 纯装饰层，事件自然穿透，
 * 所以不会挡住底下控件的手势。
 *
 * @param visible 是否显示。false 时什么都不画（不占位、不消耗）。
 * @param color 遮罩颜色。默认黑色，与星云球遮罩同一套。
 * @param alpha 不透明度。
 * @param excludeTop 顶部让出的高度（该区域不遮）。
 * @param excludeBottom 底部让出的高度。
 * @param excludeStart 左侧让出的宽度。
 * @param excludeEnd 右侧让出的宽度。
 */
@Composable
fun ModalScrim(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    alpha: Float = 0.28f,
    /**
     * 淡入淡出时长（毫秒）。0 = 不做动画，立即显示/消失。
     *
     * 星云球输入态需要平滑淡入（原来用 animateFloatAsState），
     * 时间条拖拽则希望立即出现，所以做成参数。
     */
    fadeMillis: Int = 0,
    excludeTop: Dp = 0.dp,
    excludeBottom: Dp = 0.dp,
    excludeStart: Dp = 0.dp,
    excludeEnd: Dp = 0.dp,
) {
    val target = if (visible) 1f else 0f
    val progress = if (fadeMillis > 0) {
        val p by androidx.compose.animation.core.animateFloatAsState(
            targetValue = target,
            animationSpec = androidx.compose.animation.core.tween(fadeMillis),
            label = "scrimFade",
        )
        p
    } else {
        target
    }
    if (progress <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = excludeStart,
                top = excludeTop,
                end = excludeEnd,
                bottom = excludeBottom,
            )
            .background(color.copy(alpha = alpha * progress)),
    )
}
