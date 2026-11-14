package com.nongyu.appplus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.nongyu.appplus.design.NongyuAppPlusTheme
import com.nongyu.appplus.login.model.LoginDestination
import com.nongyu.appplus.login.model.LoginReveal
import com.nongyu.appplus.login.session.LoginSessionHolder
import com.nongyu.appplus.login.ui.LoginCenterScreen
import com.nongyu.appplus.shell.MainShell

/**
 * 应用根组件。
 *
 * 票 #90 起：根节点在「主界面 / 登录中心」之间切换（Q9=A），不引导航库。
 * 状态由 [LoginSessionHolder]（单一主接缝）持有。
 *
 * 票 #94（修订）：登录成功播放**圆形揭示**转场（见 [LoginReveal]）。
 *
 * ## 转场怎么画
 * 点击登录后**主界面立刻渲染在最底层**；登录中心盖在它上面，并用
 * [BlendMode.DstOut] 在自身绘制层上挖掉一个圆 —— 于是从前往外看，
 * 登录页中间有一个**圆形缺口**，透过缺口看到下面的主界面。
 * 随后缺口半径按 `t²` 扩大、圆心按 `t³` 向上移动，直到把登录页整层吃掉。
 *
 * 为什么不是「两层做 alpha 交叉溶解」：那会让两层同时半透明，屏幕整体发灰，
 * 且看不到「一个圆」的形状。挖洞是硬边界，缺口内外都实心。
 *
 * ## 实现要点
 * - 挖洞必须在**独立图层**上做：`graphicsLayer { compositingStrategy = Offscreen }`，
 *   否则 `DstOut` 会把下面的主界面一起擦掉。
 * - 遮罩用 `drawWithContent`：先 `drawContent()` 画登录页，再用 `DstOut` 画圆擦掉。
 *
 * 本组件**不发起网络请求**，也不访问 NetHolder（ADR-0006）。
 */
@Composable
fun NongyuApp() {
    val state = LoginSessionHolder.state.value

    // 转场进度：0 = 只有登录页（缺口等于按钮本身），1 = 登录页完全消失。
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    var previousDestination by remember { mutableStateOf(state.destination) }

    LaunchedEffect(state.destination) {
        val from = previousDestination
        val to = state.destination
        previousDestination = to

        if (LoginReveal.shouldPlay(from, to)) {
            // 主动驱动：先归零再推进，保证动画一定启动
            playing = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(LoginReveal.DURATION_MS),
            )
            playing = false
        } else {
            // 冷启动 / 会话失效 / 原地不动：直接落位，不播动画
            progress.snapTo(if (to == LoginDestination.MAIN) 1f else 0f)
            playing = false
        }
    }

    val p = progress.value
    val revealRadiusRatio = LoginReveal.radiusRatio(p)
    val revealCenterYRatio = LoginReveal.centerYRatio(p)
    val loginGone = LoginReveal.isLoginLayerGone(p)

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- 底层：主界面 ----
        // 转场一开始就渲染，这样圆洞里透出的就是它（不是空白）
        if (state.destination == LoginDestination.MAIN) {
            MainShell()
        }

        // ---- 上层：登录中心 + 挖洞 ----
        // 转场期间保留；一旦缺口覆盖全屏就可以整层移除，省一次全屏绘制
        if (state.destination == LoginDestination.LOGIN_CENTER || (playing && !loginGone)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 必须在独立图层里挖洞，否则 DstOut 会擦到下面的主界面
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (playing) {
                            // DstOut：把已画内容中「这个圆内」的部分擦成透明
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.Black,
                                radius = revealRadiusRatio * size.width,
                                center = Offset(
                                    x = LoginReveal.CENTER_X_RATIO * size.width,
                                    y = revealCenterYRatio * size.height,
                                ),
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    },
            ) {
                LoginCenterScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NongyuAppPreview() {
    NongyuAppPlusTheme {
        NongyuApp()
    }
}
