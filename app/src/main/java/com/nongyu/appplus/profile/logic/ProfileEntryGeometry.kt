package com.nongyu.appplus.profile.logic

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 票 #118：画像入口「土豆状果实按钮」的几何判定接缝（纯 JVM 单测）。
 *
 * 全部函数确定、无副作用，不碰 Android / Compose / 时钟 / 网络。UI 侧用同一份
 * [absoluteEdgeRadiusAt] 采样 Canvas 轮廓，用 [isInsidePotatoWithTolerance] 做点击命中，
 * 两边共用一份数学定义，杜绝「画出来是土豆、点起来是椭圆」的分叉。
 *
 * ## 形状模型
 * 以椭圆为底，叠一个高斯凸起（lobe）形成不对称的土豆轮廓。
 * [edgeRadiusAt] 返回「形状边界半径 / 同方向椭圆半径」的倍率，恒 >= 1；
 * [absoluteEdgeRadiusAt] 把它乘回椭圆的极坐标半径，得到某方向上边界的绝对像素半径。
 * 因此椭圆内侧的点一定命中，凸起方向的外侧多出一块，视觉上就是土豆的芽。
 *
 * ## 术语边界（规格 #111）
 * 「画像入口」是领域词；「果实」「土豆」只是视觉实现词，不进领域命名。
 */
object ProfileEntryGeometry {

    /** Material 无障碍下限：任何可点热区的边长都不小于 48dp。 */
    const val MIN_TOUCH_TARGET_DP: Float = 48f

    /** 相邻果实之间至少留 12dp，避免视觉与热区重叠。 */
    const val MIN_FRUIT_SPACING_DP: Float = 12f

    /** 默认命中容差（像素）：只在形状边界附近放宽，避免边缘误落空。 */
    const val DEFAULT_HIT_TOLERANCE_PX: Float = 6f

    /** 基准缩放：uiScale = 1 时果实的边长（dp）。三果并排在窄屏仍留得下间距。 */
    private const val BASE_HOT_ZONE_DP: Float = 72f

    /** 凸起峰值相对基准的半径倍率（1 + 幅度）。 */
    private const val LOBE_AMPLITUDE: Float = 0.18f

    /** 凸起的角宽（弧度）；越小越尖，越大越圆钝。 */
    private const val LOBE_WIDTH: Float = 0.85f

    /** 凸起峰值方位（弧度）。单测直接引用，确认旋转语义。 */
    val LOBE_PEAK_ANGLE: Float = PI.toFloat() / 4f

    /**
     * 边界半径倍率：某方位角上土豆边界半径与同方向椭圆半径之比，恒 >= 1。
     *
     * 用高斯凸起：[angleOffset] 决定朝向，凸起中心在 `LOBE_PEAK_ANGLE + angleOffset`；
     * 用 `cos(角差)` 的对称高斯衰减保证任意 offset 下峰值高度一致。
     *
     * @param angle 方位角（弧度）
     * @param angleOffset 凸起朝向的旋转量（弧度）
     */
    fun edgeRadiusAt(angle: Float, angleOffset: Float): Float {
        val delta = normalizeAngle(angle - angleOffset - LOBE_PEAK_ANGLE)
        val normalized = delta / LOBE_WIDTH
        val lobe = LOBE_AMPLITUDE * kotlin.math.exp(-0.5f * normalized * normalized)
        return 1f + lobe
    }

    /**
     * 椭圆在 [angle] 方向的极坐标半径（绝对像素）：`1 / sqrt(cos²/rx² + sin²/ry²)`。
     * 半径非正时返回 0（退化形状）。
     */
    fun ellipseRadiusAt(angle: Float, radiusX: Float, radiusY: Float): Float {
        if (radiusX <= 0f || radiusY <= 0f) return 0f
        val cosA = cos(angle)
        val sinA = sin(angle)
        // sqrt 返回 Double；本函数契约是 Float，显式收敛类型。
        return (1.0 / sqrt(
            (cosA * cosA) / (radiusX * radiusX) + (sinA * sinA) / (radiusY * radiusY),
        )).toFloat()
    }

    /**
     * 土豆边界在 [angle] 方向的绝对像素半径。
     * Canvas 绘制与命中判定都调用本函数，保证所见即所点。
     */
    fun absoluteEdgeRadiusAt(
        angle: Float,
        angleOffset: Float,
        radiusX: Float,
        radiusY: Float,
    ): Float = ellipseRadiusAt(angle, radiusX, radiusY) * edgeRadiusAt(angle, angleOffset)

    /**
     * 严格命中判定：点 (x, y) 是否落在土豆轮廓内（不含容差）。
     *
     * 把点换算到相对椭圆中心的极坐标：半径 <= [absoluteEdgeRadiusAt] 即在形状内。
     * 非正半径返回 false（退化形状不接受任何点）。
     */
    fun isInsidePotato(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        angleOffset: Float,
    ): Boolean {
        if (radiusX <= 0f || radiusY <= 0f) return false
        val angle = atan2(y, x)
        val edge = absoluteEdgeRadiusAt(angle, angleOffset, radiusX, radiusY)
        return edge > 0f && hypot(x, y) <= edge
    }

    /**
     * 带容差的命中判定：在严格判定之外，边界外 [tolerancePx] 像素内也算命中。
     * 负容差按 0 处理（退化为严格判定）。半径退化时仍返回 false。
     */
    fun isInsidePotatoWithTolerance(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        angleOffset: Float,
        tolerancePx: Float,
    ): Boolean {
        if (radiusX <= 0f || radiusY <= 0f) return false
        if (isInsidePotato(x, y, radiusX, radiusY, angleOffset)) return true
        val tol = if (tolerancePx < 0f) 0f else tolerancePx
        if (tol == 0f) return false
        val angle = atan2(y, x)
        val edge = absoluteEdgeRadiusAt(angle, angleOffset, radiusX, radiusY)
        return edge > 0f && hypot(x, y) - edge <= tol
    }

    /**
     * 果实热区边长（dp）：不小于 [MIN_TOUCH_TARGET_DP]，随 UI 缩放单调不减。
     * 缩放非法（<= 0）时按下限处理。
     */
    fun hotZoneSizeDp(uiScale: Float): Float {
        val scale = if (uiScale < 0f) 0f else uiScale
        return max(MIN_TOUCH_TARGET_DP, scale * BASE_HOT_ZONE_DP)
    }

    /** 相邻果实间距（dp），恒为下限，不随缩放缩小。 */
    fun fruitSpacingDp(uiScale: Float): Float = MIN_FRUIT_SPACING_DP

    /**
     * 文字安全区半宽/半高，单位与传入的半径一致（UI 侧传 dp，测试传 px）。
     *
     * 按横纵半径分别取 0.72 / 0.58 倍：四角到椭圆边界仍有约 8% 余量，
     * 既容得下四字标签，又不会让文字贴到不规则边缘（由单测锁住）。
     */
    fun textSafeHalfExtent(radiusX: Float, radiusY: Float): Pair<Float, Float> {
        if (radiusX <= 0f || radiusY <= 0f) return 0f to 0f
        return radiusX * 0.72f to radiusY * 0.58f
    }

    /** 把任意弧度归一化到 [-PI, PI]，用于凸起角差的对称衰减。 */
    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        val twoPi = (2f * PI).toFloat()
        while (a > PI) a -= twoPi
        while (a < -PI) a += twoPi
        return a
    }
}
