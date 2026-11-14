package com.nongyu.appplus.login.model

/**
 * 登录成功转场的三段编排（票 #94，纯 JVM 可测）。
 *
 * ## 三段时序
 * ```
 * 阶段 1  BUTTON_OUT    登录按钮 alpha 1→0、scale 1→1.35
 * 阶段 2  CROSSFADE     按钮已基本透明，主界面 alpha 0→1 与之重叠（交叉溶解）
 * 阶段 3  CONTENT_IN    主界面内容再做一次入场淡入
 * ```
 * 三段的时长与相位由本类给出，UI 只负责把 [buttonAlpha] / [mainAlpha] /
 * [contentAlpha] 三个插值结果画出来。把编排留在纯逻辑层的原因是：
 * 「三阶段是否真的连续」「冷启动路径是否绝对不播」这两点必须能纯 JVM 断言，
 * 不能靠人眼看动画判断。
 *
 * ## 为什么不做成四个独立 animateFloatAsState
 * 独立动画各自的 spring/tween 起点不同步，容易出现「按钮还没淡完主界面就全亮」。
 * 这里用**一个**总进度 [progress]（0..1）派生三个 alpha，天然保证连续与重叠。
 *
 * ## 只在「用户主动登录成功」时播放
 * 冷启动（本地已有凭证）直接进主界面，[shouldPlay] 为 false。
 * 失败 / 取消 / 超时不会调用到本类（它们不改 destination），也就不会触发转场。
 */
object LoginTransition {

    /** 阶段 1 结束（按钮完全透明）的进度点。 */
    const val BUTTON_END = 0.36f

    /** 交叉溶解结束（主界面完全不透明）的进度点。 */
    const val MAIN_END = 0.72f

    /** 内容入场结束的进度点（= 1.0，总时长末尾）。 */
    const val CONTENT_END = 1.0f

    /** 整条转场总时长（毫秒）。沿用课表 160ms 淡入的节奏，分成三段后取 520ms。 */
    const val DURATION_MS = 520

    /** 按钮在阶段 1 的最终放大倍数。 */
    const val BUTTON_SCALE_END = 1.35f

    /**
     * 是否应播放转场。
     *
     * @param fromLoginCenter 转场发生前所在去向
     * @param toMain 转场发生后所在去向
     */
    fun shouldPlay(fromLoginCenter: LoginDestination, toMain: LoginDestination): Boolean =
        fromLoginCenter == LoginDestination.LOGIN_CENTER && toMain == LoginDestination.MAIN

    /**
     * 登录按钮 alpha：1 → 0，在 [BUTTON_END] 处归零。
     */
    fun buttonAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p >= BUTTON_END) 0f else 1f - (p / BUTTON_END)
    }

    /**
     * 登录按钮 scale：1 → [BUTTON_SCALE_END]，与 alpha 同步在 [BUTTON_END] 处到位。
     */
    fun buttonScale(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val t = (p / BUTTON_END).coerceIn(0f, 1f)
        return 1f + (BUTTON_SCALE_END - 1f) * t
    }

    /**
     * 主界面 alpha：在按钮淡出的**后半段**开始上升，到 [MAIN_END] 到 1。
     *
     * 起始点取 `BUTTON_END / 2` 是为了与按钮淡出重叠——这就是「交叉溶解」，
     * 否则会出现中间一帧两边都半透明、屏幕整体发灰的空档。
     */
    fun mainAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val start = BUTTON_END / 2f
        if (p <= start) return 0f
        if (p >= MAIN_END) return 1f
        return (p - start) / (MAIN_END - start)
    }

    /**
     * 主界面内容入场：在 [MAIN_END] 之后由 0 → 1 淡入。
     *
     * 主界面整体 alpha 已经到 1 之后再让内部内容淡入，形成「先出来、再显影」的层次，
     * 这也是课表里各区块入场用的同一套语言（graphicsLayer.alpha + tween）。
     */
    fun contentAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (p <= MAIN_END) return 0f
        return (p - MAIN_END) / (CONTENT_END - MAIN_END)
    }

    /** 当前处于哪一段，供测试与调试断言。 */
    fun stageOf(progress: Float): Stage {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p < BUTTON_END -> Stage.BUTTON_OUT
            p < MAIN_END -> Stage.CROSSFADE
            else -> Stage.CONTENT_IN
        }
    }

    /** 转场阶段。 */
    enum class Stage {
        /** 按钮淡出放大。 */
        BUTTON_OUT,

        /** 按钮与主界面交叉溶解。 */
        CROSSFADE,

        /** 主界面内容入场淡入。 */
        CONTENT_IN,
    }
}
