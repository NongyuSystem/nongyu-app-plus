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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.nongyu.appplus.design.NongyuAppPlusTheme
import com.nongyu.appplus.login.model.LoginDestination
import com.nongyu.appplus.login.model.LoginTransition
import com.nongyu.appplus.login.session.LoginSessionHolder
import com.nongyu.appplus.login.ui.LoginCenterScreen
import com.nongyu.appplus.shell.MainShell

/**
 * 应用根组件。
 *
 * 票 #90 起：根节点用 `when(会话状态)` 在「主界面 / 登录中心」之间切换（Q9=A），
 * 不引导航库。状态由 [LoginSessionHolder]（单一主接缝）持有：
 * - 首帧前 MainActivity 用会话存储判定落地去向，无「检查中」态；
 * - 首帧之后 `SessionStartupVerifier` 的校验结果只做事后纠正。
 *
 * 票 #94 起：**用户主动登录成功**时播放三段转场（见 [LoginTransition]）：
 * 登录按钮淡出放大 → 与主界面交叉溶解 → 主界面内容入场淡入。
 * 冷启动（本地已有凭证）直接渲染主界面，不播转场——由 [LoginTransition.shouldPlay]
 * 的 "LOGIN_CENTER → MAIN" 判据保证。
 *
 * ## 为什么用 [Animatable] 主动推进入场进度，而不是 animateFloatAsState
 * `animateFloatAsState` 只在**目标值变化**时启动动画。转场的触发时机是
 * 「destination 刚刚变成 MAIN」，此时若把目标值设为 0、再改 1，
 * 会因为同一帧内合并而在某些时序下不启动，表现为「登录成功后停在登录页」。
 * 这里改成显式 `snapTo(0)` + `animateTo(1)`：进度由我们驱动，时序确定。
 *
 * 本组件**不发起网络请求**，也不访问 NetHolder（ADR-0006）。
 */
@Composable
fun NongyuApp() {
    val state = LoginSessionHolder.state.value

    // 转场总进度：0 = 纯登录中心，1 = 纯主界面。
    val progress = remember { Animatable(if (state.destination == LoginDestination.MAIN) 1f else 0f) }
    var playing by remember { mutableStateOf(false) }
    var previousDestination by remember { mutableStateOf(state.destination) }

    LaunchedEffect(state.destination) {
        val from = previousDestination
        val to = state.destination
        previousDestination = to

        if (LoginTransition.shouldPlay(from, to)) {
            // 主动播放：先归零再推进，保证动画一定启动
            playing = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(LoginTransition.DURATION_MS),
            )
            playing = false
        } else {
            // 冷启动 / 会话失效 / 原地不动：直接落位，不播动画
            progress.snapTo(if (to == LoginDestination.MAIN) 1f else 0f)
            playing = false
        }
    }

    val p = progress.value
    val buttonAlpha = LoginTransition.buttonAlpha(p)
    val buttonScale = LoginTransition.buttonScale(p)
    val mainAlpha = LoginTransition.mainAlpha(p)
    val contentAlpha = LoginTransition.contentAlpha(p)

    Box(modifier = Modifier.fillMaxSize()) {
        // 登录中心：转场期间**保留在下面**，只让它的登录按钮淡出放大。
        // 不能因为是 MAIN 就立刻移除——否则主界面没有「从下面渐显」的对象，
        // 交叉溶解会变成「凭空出现」。
        if (state.destination == LoginDestination.LOGIN_CENTER || playing) {
            LoginCenterScreen(
                arrowAlphaOverride = if (playing) buttonAlpha else null,
                arrowScaleOverride = if (playing) buttonScale else null,
            )
        }

        // 主界面：覆盖在登录中心之上，按交叉溶解 + 内容入场两级透明度显影。
        if (state.destination == LoginDestination.MAIN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // mainAlpha 控制与登录按钮交叉溶解的整屏透明度；
                        // contentAlpha 控制内容再显影一次，两者相乘即最终透明度。
                        alpha = (mainAlpha * contentAlpha).coerceIn(0f, 1f)
                    },
            ) {
                MainShell()
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
