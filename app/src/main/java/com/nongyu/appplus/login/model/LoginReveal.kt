package com.nongyu.appplus.login.model

/**
 * 登录成功转场：**圆形揭示**（iris reveal）的几何编排（票 #94 修订，纯 JVM 可测）。
 *
 * ## 视觉目标（维护者原话）
 * 点击登录后**立刻渲染主界面**，登录页整层变透明；登录按钮所在位置留一个**圆形缺口**，
 * 透过缺口能看到下面的主界面（登录页背景此时不变），随后缺口扩大、圆心向上移动，
 * 直到把登录页整层吃掉。
 *
 * 约束（原话）：
 * - 背景不变 —— 缺口只是在登录页那层上挖洞，不改变登录页自身的背景绘制；
 * - 「向上移动速度大于向下移动」且「位移不大于半径增长速度」——
 *   即圆心上升量始终小于半径增量，缺口只扩不缩。
 *
 * ## 为什么用几何而不是 alpha
 * 整页 alpha 交叉溶解会让两层同时半透明、屏幕整体发灰；挖洞则是**硬边界**，
 * 缺口内外都是不透明的，视觉上就是「主界面从一个圆里涌出来」。
 *
 * ## 坐标口径
 * 全部用**屏幕宽高比例**（0..1）表达，UI 层再乘实际像素。这样纯 JVM 可测，
 * 也不会因为机型尺寸不同而错位。
 */
object LoginReveal {

    /** 转场总时长（毫秒）。 */
    const val DURATION_MS = 620

    /**
     * 起始半径：直接取登录按钮自身的半径（相对屏幕宽度）。
     *
     * 登录按钮 `size = 88dp`，蓝叠基准 540dp 宽 → 直径 0.163、半径约 0.081。
     * 取 0.082 与按钮圆重合，转场第一帧看起来就是「按钮本身变成洞口」。
     */
    const val START_RADIUS_RATIO = 0.082f

    /**
     * 起始圆心纵向位置（相对屏幕高度）：登录按钮圆心的位置。
     *
     * 按钮底距 148dp、按钮半径 44dp；基准高 1200dp →
     * `(1200 - 148 - 44) / 1200 = 0.84`。
     */
    const val START_CENTER_Y_RATIO = 0.84f

    /**
     * 结束时圆心纵向位置（相对屏幕高度）：向上移到屏幕中心。
     *
     * 位移 = 0.84 - 0.50 = 0.34（屏幕高的比例）。
     */
    const val END_CENTER_Y_RATIO = 0.50f

    /**
     * 结束半径：必须能覆盖整屏最远角。
     *
     * 圆心最终在屏幕中心，最远角距离 = sqrt(0.5² + 0.5²) ≈ 0.707（按宽度归一化）。
     * 取 0.80 留余量，避免四角露出登录页残留。
     */
    const val END_RADIUS_RATIO = 0.80f

    /**
     * 半径用「先慢后快」的缓动（easeIn），圆心上升用线性，两者叠加形成
     * 「圆在长大、同时整体往上漂」的手感。
     *
     * 约束校验见 [radiusRatio] 与 [centerYRatio] 的 KDoc。
     */
    fun progressOf(raw: Float): Float = raw.coerceIn(0f, 1f)

    /**
     * 当前半径（相对屏幕宽度的比例）。
     *
     * 用 easeIn 曲线：`t²` 起手慢、后段快，符合「洞一开始几乎不动，然后迅速吞掉全屏」。
     */
    fun radiusRatio(progress: Float): Float {
        val t = progressOf(progress)
        val eased = t * t
        return START_RADIUS_RATIO + (END_RADIUS_RATIO - START_RADIUS_RATIO) * eased
    }

    /**
     * 当前圆心纵向位置（相对屏幕高度的比例），从 [START_CENTER_Y_RATIO] 升到 [END_CENTER_Y_RATIO]。
     *
     * ## 为什么用 `t³`（比半径的 `t²` 更慢）
     * 维护者约束：**位移不大于半径增长速度**（圆只扩不缩）。
     * 若圆心用线性 `t`、半径用 `t²`，起手阶段半径几乎不动（t=0 时增量为 0）
     * 而圆心已在匀速上升，缺口会被「拽」成上窄下宽，视觉上像在缩小。
     *
     * 取 `t³` 后，起手阶段位移增量也趋于 0 且比 `t²` 更小，
     * 实测逐步比对：`dShift ≤ dRadius` 全程成立（见 LoginRevealTest）。
     */
    fun centerYRatio(progress: Float): Float {
        val t = progressOf(progress)
        val eased = t * t * t
        return START_CENTER_Y_RATIO + (END_CENTER_Y_RATIO - START_CENTER_Y_RATIO) * eased
    }

    /** 圆心横向位置：始终在屏幕水平中心（相对宽度 0.5）。 */
    const val CENTER_X_RATIO = 0.5f

    /**
     * 登录页（被挖洞那层）整体是否已完全消失。
     *
     * 半径超过覆盖全屏所需后即可移除该层，省掉一次全屏绘制。
     */
    fun isLoginLayerGone(progress: Float): Boolean =
        radiusRatio(progress) >= COVER_ALL_RADIUS

    /** 覆盖全屏所需的最小半径（相对宽度）：屏幕中心到最远角。 */
    private const val COVER_ALL_RADIUS = 0.7071f + 0.01f

    /**
     * 是否应播放转场：只有「登录中心 → 主界面」这一跳。
     *
     * @param fromLoginCenter 转场前所在去向
     * @param toMain 转场后所在去向
     */
    fun shouldPlay(fromLoginCenter: LoginDestination, toMain: LoginDestination): Boolean =
        fromLoginCenter == LoginDestination.LOGIN_CENTER && toMain == LoginDestination.MAIN
}
