package com.nongyu.appplus.profile.logic

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 开页「茎」的几何（票 #111 个人主页，第 1 步：只做线）。
 *
 * 视觉依据（维护者 2026-11-27 第 3 次修订，明确要求「真圆弧」）：
 * - 起点：底部中央的星云球中心；
 * - 从起点向右偏出发，鼓成一段**真正的圆弧**；
 * - 圆弧最右点与屏幕右侧距离 5~15px；
 * - 终点落在左侧、距屏幕顶部 1/5 处。
 *
 * ## 为什么终点切线不可能是竖直的
 * 维护者一度要求「终点切线与竖直夹角 75°~105°」。实测：过起点、过最右点（贴右边缘）、
 * 过终点三点的圆弧，终点切线恒在 175° 附近（接近水平）——起点在底部中央、终点在左上，
 * 要让圆鼓到右边缘，圆必然很扁，终点处切线就接近水平。这条约束在「真圆弧」下无解，
 * 维护者已确认**放弃终点竖直，保留圆弧 + 最右贴边**。
 *
 * 实现用**三点定圆**（start / rightmost / end），保证严格是圆；绘制时按角度参数采样。
 *
 * 本文件只有纯函数，不碰 Android/Compose，纯 JVM 单测覆盖。
 */
object StemGrowth {

    /** 终点纵向：距顶部 1/5。 */
    const val END_TOP_FRACTION: Float = 0.2f

    /**
     * 终点横向：相对左屏幕边缘的位置（px）。
     *
     * 维护者口径「完全贴边，可以超过」。取 -40：弧线终点落在左边**屏幕外**，
     * 于是可见部分正好贴着左边被裁掉，视觉上就是"顶到左边"。
     * 用负值而不是 0，是为了避免圆头端点在边缘被看出一个半圆缺口。
     */
    const val END_LEFT_INSET_PX: Float = -40f

    /**
     * 最右目标点距屏幕右边缘的目标距离（px）。
     *
     * 维护者口径 5~15px，取中值 10px。
     */
    const val RIGHT_GAP_PX: Float = 10f

    /**
     * 求最右目标点的 y：让「过起点、过该点、过终点」的圆，其最右处正落目标 gap。
     *
     * 用二分：二点定圆后，圆心 + 半径 的最小 x 即圆最右处；gap 随 rightY 单调变化。
     * 纯函数、确定性，可单测。
     *
     * @return 最右目标点 y（像素），解不出时返回起终点 y 的中值。
     */
    fun solveRightPointY(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        screenWidth: Float,
        gapPx: Float = RIGHT_GAP_PX,
    ): Float {
        val targetRight = screenWidth - gapPx
        var lo = minOf(startY, endY)
        var hi = maxOf(startY, endY)
        var best = (lo + hi) / 2f
        var bestErr = Float.MAX_VALUE
        repeat(60) {
            val mid = (lo + hi) / 2f
            val circle = circle3(startX, startY, targetRight, mid, endX, endY)
            if (circle == null) {
                // 三点共线：缩小搜索到 mid 下方
                hi = mid
                return@repeat
            }
            val (cx, _, r) = circle
            val rightPoint = cx + r
            val err = rightPoint - targetRight
            if (kotlin.math.abs(err) < bestErr) {
                bestErr = kotlin.math.abs(err)
                best = mid
            }
            // rightPoint 偏右 -> err>0 -> 需要 rightY 更靠近起终点连线中部
            if (err > 0f) hi = mid else lo = mid
        }
        return best
    }

    /**
     * 三点定圆：返回 (圆心x, 圆心y, 半径)。三点共线返回 null。
     */
    fun circle3(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
    ): Triple<Float, Float, Float>? {
        val d = 2f * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
        if (kotlin.math.abs(d) < 1e-6f) return null
        val x1sq = x1 * x1 + y1 * y1
        val x2sq = x2 * x2 + y2 * y2
        val x3sq = x3 * x3 + y3 * y3
        val cx = (x1sq * (y2 - y3) + x2sq * (y3 - y1) + x3sq * (y1 - y2)) / d
        val cy = (x1sq * (x3 - x2) + x2sq * (x1 - x3) + x3sq * (x2 - x1)) / d
        val r = sqrt((x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy))
        return Triple(cx, cy, r)
    }

    /** 点相对圆心的角度（度，屏幕坐标：0°=右，90°=下）。 */
    fun angleOf(cx: Float, cy: Float, x: Float, y: Float): Float =
        Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()

    /**
     * 按角度在圆上取点。
     */
    fun pointAtAngle(cx: Float, cy: Float, r: Float, degrees: Float): Pair<Float, Float> {
        val rad = Math.toRadians(degrees.toDouble())
        return (cx + (r * kotlin.math.cos(rad)).toFloat()) to
            (cy + (r * kotlin.math.sin(rad)).toFloat())
    }

    /**
     * 从 [fromDeg] 沿 [dir] 方向走到 [toDeg] 的角度跨度（度，恒正）。
     *
     * [dir] = +1 顺时针（屏幕坐标角度增大），-1 逆时针。
     */
    fun sweepBetween(fromDeg: Float, toDeg: Float, dir: Int): Float {
        var delta = toDeg - fromDeg
        if (dir > 0) {
            while (delta < 0f) delta += 360f
        } else {
            while (delta > 0f) delta -= 360f
        }
        return kotlin.math.abs(delta)
    }

    fun clampProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

    fun isComplete(progress: Float): Boolean = progress >= 1f

    // ---------- 果实（茎上结出的多个圆） ----------

    /**
     * 果实数量。
     *
     * 维护者口径「多个果实」，按 y（高度）等差排列。
     */
    const val FRUIT_COUNT: Int = 4

    /** 果实完全长成时的半径（dp）。 */
    const val FRUIT_RADIUS_DP: Float = 20f

    /**
     * 果实圆心相对茎线的**内侧**偏移（dp）。
     *
     * 维护者 2026-11-27：「果实整体往茎内侧偏移一点」——因为茎是向右鼓的圆弧，
     * 果实正好压在最右点时会被屏幕右边缘切掉一角。沿半径朝圆心挪一点即可避开，
     * 同时果实仍与茎线重叠（看的出长在茎上）。
     */
    const val FRUIT_INWARD_INSET_DP: Float = 14f

    /** 果实「变大显现」的动画跨度：茎长过该位置后，再前进这段比例完成放大。 */
    const val FRUIT_GROW_SPAN: Float = 0.08f

    /**
     * 第 [index] 个果实的 y 坐标：在茎的可见 y 区间 [topY, bottomY] 内**等差**分布。
     *
     * 把区间 n+1 等分，取 n 个内点，因而相邻两颗的 y 间距恒为 (bottomY-topY)/(n+1)。
     * 维护者口径「y（高度）成等差排列」。
     */
    fun fruitY(topY: Float, bottomY: Float, index: Int, count: Int = FRUIT_COUNT): Float {
        if (count <= 0) return topY
        val step = (bottomY - topY) / (count + 1).toFloat()
        return topY + step * (index + 1)
    }

    /**
     * 给定 y，求茎（圆弧）在该高度上的 x。
     *
     * 取圆心正右/正左两个解中落在屏幕内、且与茎可见走向一致的那个。
     * 圆与水平线不相交（|y-cy| > r）时返回 null。
     *
     * @param preferRight true 时优先取较大的 x（茎在屏幕右侧鼓出时用）
     */
    fun xOnCircleAt(
        cx: Float, cy: Float, r: Float, y: Float,
        preferRight: Boolean = true,
        screenWidth: Float = Float.MAX_VALUE,
    ): Float? {
        val dy = y - cy
        if (kotlin.math.abs(dy) > r) return null
        val dx = sqrt(r * r - dy * dy)
        val left = cx - dx
        val right = cx + dx
        val candidates = if (preferRight) listOf(right, left) else listOf(left, right)
        return candidates.firstOrNull { it in 0f..screenWidth } ?: candidates.first()
    }

    /**
     * 茎上某角度相对起点、相对整段扫角的进度（0..1）。
     *
     * 用于判断「茎是否已长过该果实位置」，从而驱动果实放大。
     */
    fun progressOfAngle(
        startDeg: Float,
        sweepDegrees: Float,
        ccw: Boolean,
        angleDeg: Float,
    ): Float {
        if (sweepDegrees <= 0f) return 0f
        var delta = angleDeg - startDeg
        if (ccw) {
            while (delta > 0f) delta -= 360f
            delta = -delta
        } else {
            while (delta < 0f) delta += 360f
        }
        return (delta / sweepDegrees).coerceIn(0f, 1f)
    }

    /**
     * 果实当前半径相对最终半径的比例（0 = 还没出现，1 = 完全长成）。
     *
     * @param stemProgress 茎当前生长进度
     * @param fruitProgress 该果实在茎上的进度位置
     */
    fun fruitScaleAt(stemProgress: Float, fruitProgress: Float): Float {
        val p = stemProgress.coerceIn(0f, 1f)
        if (p <= fruitProgress) return 0f
        return ((p - fruitProgress) / FRUIT_GROW_SPAN).coerceIn(0f, 1f)
    }
}
