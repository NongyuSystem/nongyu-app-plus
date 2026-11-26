package com.nongyu.appplus.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.nongyu.appplus.settings.ui.settingsTween

/**
 * 全局设置面板的开关与齿轮动画状态。
 *
 * 从 `MainShell` 原样搬出，行为未改。
 *
 * - [open] 面板是否打开。
 * - [progress] 面板滑入进度与齿轮旋转**共用同一个 `Animatable`**，保证两者严格同步；
 *   滑出用对称 tween 反向跑。分成两个会失去同步。
 * - [gearCenterPx] 齿轮在整窗中的实测中心（由工具条上报），供面板做交接动画。
 * - [takenOver] 齿轮是否已被设置面板接管：由面板回报，工具条据此隐藏原图标
 *   （两者必须严格互斥，否则出现双图标）。
 * - [dayMode] / [showWeekends] / [quietRefresh] 课程表设置项的值。
 *   本期只做界面，先放内存，持久化留给后续票。
 */
class SettingsPanelState {
    var open by mutableStateOf(false)

    val progress = Animatable(0f)

    var gearCenterPx by mutableStateOf<Offset?>(null)

    var takenOver by mutableStateOf(false)

    var dayMode by mutableStateOf(false)

    var showWeekends by mutableStateOf(true)

    var quietRefresh by mutableStateOf(false)
}

/**
 * [SettingsPanelState] 的 Compose 工厂。
 *
 * 打开/关闭统一走同一个动画：progress 0→1 滑入，1→0 滑出
 * （反向动画由对称 tween 保证）。key 只盯 [SettingsPanelState.open]。
 */
@Composable
fun rememberSettingsPanelState(): SettingsPanelState {
    val state = remember { SettingsPanelState() }

    LaunchedEffect(state.open) {
        if (state.open) {
            state.progress.animateTo(1f, settingsTween())
        } else {
            state.progress.animateTo(0f, settingsTween())
        }
    }
    return state
}
