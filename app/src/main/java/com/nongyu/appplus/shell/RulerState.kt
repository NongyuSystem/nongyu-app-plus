package com.nongyu.appplus.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 时间条（页面级 overlay）的状态。
 *
 * 从 `MainShell` 原样搬出，行为未改。时间条本体虽已搬到 `timetable/ui/TimetableRuler`，
 * 但它的定位数据、拖动遮罩、当前时刻刷新仍由 Shell 持有，故整体抽成 state-holder。
 *
 * 实测踩过的坑（详见 `docs/specs/界面层.md` §8.5 B / §8.5 D）：
 * - **坐标系必须和课表同一基准**。`TimetableGrid` 用 `localToRoot` 取到的是整窗根坐标
 *   （含状态栏一行）；而 Shell 的根 Box 已被 `windowInsetsPadding(systemBars)` 往里收过，
 *   时间条又在这个 Box 里用 `.padding(top = rulerTop)` 定位。两者原点相差一个状态栏高度
 *   —— 2026-11-23 加全局 systemBars 避让后，时间尺就整体偏下。
 *   所以先记下根 Box 自身的整窗偏移 [rootOffsetYPx]，收到上报时减掉，
 *   让 [periodTopPx] 变成「相对本 Box」的坐标。
 * - **时间文字清除 effect 的 key 只盯 [dragging]**。曾同时盯 [focus] 与 `dragging`，
 *   两者在 `onDragEnd` 里连续写，可能出现「focus 已 null 但 dragging 仍 true」的一帧，
 *   导致清除分支不触发、时间文字一直挂在屏幕上。
 */
class RulerState {
    /** 根 Box 在整窗中的纵向偏移（px，由 `onGloballyPositioned` 实测）。 */
    var rootOffsetYPx by mutableFloatStateOf(0f)

    /** 节次行顶边（px，相对根 Box；由 TimetableGrid 实测上报后换算）。 */
    var periodTopPx by mutableFloatStateOf(0f)
        private set

    /** 节次行高度（px，由 TimetableGrid 实测上报）。 */
    var periodHeightPx by mutableFloatStateOf(0f)
        private set

    /** 时间条是否正在拖动（驱动全屏遮罩 `design/ModalScrim`）。 */
    var dragging by mutableStateOf(false)

    /** 拖动中手指所在的时间轴比例（每帧更新）。 */
    var focus by mutableStateOf<Float?>(null)

    /** 「已停留」的比例：拖动中实时跟随 [focus]，松手后停留 1.6s 再清空。 */
    var focusHeld by mutableStateOf<Float?>(null)
        private set

    /** 当前时刻（用于水面高度）。每 30s 刷新一次即可。 */
    var nowMinute by mutableStateOf(LocalTime.now())
        private set

    /**
     * TimetableGrid 上报节次行实测范围。
     * 入参是整窗坐标，先减 [rootOffsetYPx] 换成相对坐标再存。
     */
    fun onPeriodColumnBounds(top: Float, height: Float) {
        periodTopPx = (top - rootOffsetYPx).coerceAtLeast(0f)
        periodHeightPx = height
    }

    /** 根 Box 整窗偏移上报。 */
    fun onRootOffset(y: Float) {
        rootOffsetYPx = y
    }

    /** 拖动中实时跟随手指（由 [rememberRulerState] 的 effect 调用）。 */
    fun followFocus() {
        focusHeld = focus
    }

    /** 松手后 1.6s 清除停留比例（由 [rememberRulerState] 的 effect 调用）。 */
    fun clearFocusHeld() {
        focusHeld = null
    }

    /** 刷新当前时刻（由 [rememberRulerState] 的 ticker effect 调用）。 */
    fun tickNow() {
        nowMinute = LocalTime.now()
    }
}

/**
 * [RulerState] 的 Compose 工厂：把原先内联在 `MainShell` 的三个 effect 原样搬到这里。
 */
@Composable
fun rememberRulerState(): RulerState {
    val state = remember { RulerState() }

    // 当前时刻每 30s 刷新一次（水面高度用，粒度到分钟足够）。
    LaunchedEffect(Unit) {
        while (true) {
            state.tickNow()
            delay(30_000L)
        }
    }
    // 详见 docs/specs/界面层.md §8.5 B：清除 effect 的 key 只盯 dragging。
    LaunchedEffect(state.dragging) {
        if (!state.dragging && state.focusHeld != null) {
            delay(1600)
            state.clearFocusHeld()
        }
    }
    // 拖动中实时跟随手指。
    LaunchedEffect(state.focus) {
        if (state.focus != null) state.followFocus()
    }
    return state
}
