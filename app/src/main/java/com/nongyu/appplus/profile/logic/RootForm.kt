package com.nongyu.appplus.profile.logic

/**
 * 个人主页「根系形态」两态吸附的纯判定接缝（票 #117，纯 JVM 单测）。
 *
 * 全部函数都是确定性的：不读系统时钟、不碰 Android / Compose、不碰网络。
 * 位移统一用像素（UI 侧用 LocalDensity 换算后传入），阈值与展开高度由调用方注入，
 * 便于纯 JVM 单测锁住边界，也便于不同屏幕密度下复用同一套规则。
 *
 * ## 术语边界（规格 #111）
 * 「根系形态」是领域词；「根」「果实」「枝条」只是视觉实现词，不进领域命名。
 * 本 object 只描述两态（收起 / 展开）的位移判定，不描述任何视觉形态。
 *
 * ## 为什么阈值与吸附目标是纯函数
 * 跟手阶段用显式动画对象「拖拽期 snapTo 直接跟随 / 松手 animateTo 按阈值吸附」，
 * 不能用声明式条件动画（会吞掉松手动画）。松手去向与跟手夹取这两件事
 * 错了只表现为「手势跳变 / 收起时占位逐帧抖动」，肉眼难稳定复现，故下沉本 object。
 */
object RootForm {

    /** 两态：收起（只显示画像卡）与展开（揭示画像入口区）。 */
    enum class State {
        COLLAPSED,
        EXPANDED,
    }

    /** 收起态的稳定目标（[snapTo] 的返回值之一）。 */
    val COLLAPSED: State = State.COLLAPSED

    /** 展开态的稳定目标（[snapTo] 的返回值之一）。 */
    val EXPANDED: State = State.EXPANDED

    /**
     * 吸附阈值占「揭示总高度」的比例。
     * 取 35%：上滑超过揭示高度的三分之一才展开，避免轻微误触；
     * 下拉超过三分之一才收起，避免内容区轻点被判成退出。
     */
    const val THRESHOLD_FRACTION: Float = 0.35f

    /**
     * 由揭示总高度推导吸附阈值（px）。负数按 0 处理，保证阈值恒非负。
     * 阈值随屏幕/内容高度线性缩放，不同密度下手感一致。
     */
    fun thresholdPxOf(revealedPx: Float): Float =
        if (revealedPx < 0f) 0f else revealedPx * THRESHOLD_FRACTION

    /**
     * 松手后的吸附目标。上滑位移达到正阈值即展开，下滑位移达到负阈值即收起，
     * 否则保持当前形态。阈值本身包含在「越过」内（等于阈值即吸附到对侧）。
     */
    fun isExpandedTarget(expanded: Boolean, dragPx: Float, thresholdPx: Float): Boolean {
        val threshold = if (thresholdPx < 0f) 0f else thresholdPx
        return if (expanded) dragPx > -threshold else dragPx >= threshold
    }

    /**
     * [isExpandedTarget] 的枚举出口，供 UI 侧直接传给动画目标值。
     * 语义完全一致，避免两个判定各写一遍产生分叉。
     */
    fun snapTo(expanded: Boolean, dragPx: Float, thresholdPx: Float): State =
        if (isExpandedTarget(expanded, dragPx, thresholdPx)) EXPANDED else COLLAPSED

    /**
     * 跟手阶段的位移夹取：收起态只接受上滑（正位移，0..展开高度），
     * 展开态只接受下滑（负位移，-展开高度..0），超出部分不继续跟随，
     * 避免画面被拖出两态之外造成跳变。
     *
     * @param revealedPx 展开态相对收起态多揭示的高度（px），由 UI 侧实测注入
     */
    fun followDrift(expanded: Boolean, dragPx: Float, revealedPx: Float): Float {
        val max = if (revealedPx < 0f) 0f else revealedPx
        return if (expanded) dragPx.coerceIn(-max, 0f) else dragPx.coerceIn(0f, max)
    }
}
