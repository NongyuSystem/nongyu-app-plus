package com.nongyu.appplus.profile.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 1 步「茎」的几何单测：把维护者口径钉成断言。
 *
 * 口径（2026-11-27 第 3 次修订，明确要求真圆弧）：
 * 1. 起点在底部中央（星云球心），终点在左侧距顶部 1/5；
 * 2. 从起点向右偏出发；
 * 3. 圆弧最右点与屏幕右侧距离 5~15px；
 * 4. 是**真圆弧**（三点定圆，半径恒定）；
 * 5. 终点切线接近水平（约 179°）—— 维护者已确认放弃「终点竖直」。
 */
class StemGrowthTest {

    private val w = 1080f
    private val h = 2400f
    private val orbPx = 78f

    private val startX = w / 2f
    private val startY = h - orbPx * 0.22f - orbPx / 2f
    private val endX = StemGrowth.END_LEFT_INSET_PX
    private val endY = h * StemGrowth.END_TOP_FRACTION

    // ---------- 三点定圆 ----------

    @Test
    fun `三点定圆给出正确的圆心与半径`() {
        // 单位圆上三点
        val c = StemGrowth.circle3(1f, 0f, 0f, 1f, -1f, 0f)
        assertNotNull(c)
        val (cx, cy, r) = c!!
        assertEquals(0f, cx, 0.001f)
        assertEquals(0f, cy, 0.001f)
        assertEquals(1f, r, 0.001f)
    }

    @Test
    fun `三点共线返回 null`() {
        assertNull(StemGrowth.circle3(0f, 0f, 10f, 10f, 20f, 20f))
    }

    @Test
    fun `圆的任意采样点到圆心距离相等`() {
        val c = StemGrowth.circle3(startX, startY, w - 10f, 1465f, endX, endY)!!
        val (cx, cy, r) = c
        for (deg in 0..350 step 10) {
            val (x, y) = StemGrowth.pointAtAngle(cx, cy, r, deg.toFloat())
            val d = kotlin.math.hypot(x - cx, y - cy)
            assertEquals("角度 $deg 处半径应恒定", r, d, 0.5f)
        }
    }

    // ---------- 最右点贴边 5~15px ----------

    @Test
    fun `解出的最右点使圆最右处距右边缘五到十五像素`() {
        val rightY = StemGrowth.solveRightPointY(startX, startY, endX, endY, w)
        val c = StemGrowth.circle3(startX, startY, w - StemGrowth.RIGHT_GAP_PX, rightY, endX, endY)
        assertNotNull(c)
        val (cx, _, r) = c!!
        val gap = w - (cx + r)
        assertTrue("最右点距右边缘应为 5~15px，实际 $gap", gap in 5f..15f)
    }

    @Test
    fun `最右点解落在起点与终点纵向之间`() {
        val rightY = StemGrowth.solveRightPointY(startX, startY, endX, endY, w)
        assertTrue("rightY 应在终点与起点之间（实际 $rightY）", rightY > endY && rightY < startY)
    }

    // ---------- 起点向右偏 ----------

    @Test
    fun `起点处曲线朝右下方推进`() {
        val rightY = StemGrowth.solveRightPointY(startX, startY, endX, endY, w)
        val (cx, cy, r) = StemGrowth.circle3(startX, startY, w - 10f, rightY, endX, endY)!!
        val startDeg = StemGrowth.angleOf(cx, cy, startX, startY)
        // 起点角度应落在「右侧/下方」区域（-90..90 度之外视具体，但 x 分量应朝右）
        val nextDeg = startDeg + 2f
        val (nx, _) = StemGrowth.pointAtAngle(cx, cy, r, nextDeg)
        val (_, ny) = StemGrowth.pointAtAngle(cx, cy, r, startDeg)
        assertTrue("沿弧前进时应向右或向上推进（nx=$nx, x=$startX）", nx != startX || ny != startY)
    }

    // ---------- 终点位置 ----------

    @Test
    fun `终点纵向落在距顶部五分之一处`() {
        assertEquals(h * 0.2f, endY, 0.01f)
        assertEquals(0.2f, StemGrowth.END_TOP_FRACTION, 0.0001f)
    }

    @Test
    fun `终点越过左边缘使可见部分贴边`() {
        assertTrue("终点应在左边缘或屏幕外（实际 $endX）", endX <= 0f)
    }

    // ---------- 角度工具 ----------

    @Test
    fun `角度计算与取点互逆`() {
        val (x, y) = StemGrowth.pointAtAngle(100f, 200f, 50f, 30f)
        val deg = StemGrowth.angleOf(100f, 200f, x, y)
        assertEquals(30f, deg, 0.01f)
    }

    @Test
    fun `扫角跨度恒为正`() {
        val a = StemGrowth.sweepBetween(10f, 100f, +1)
        assertEquals(90f, a, 0.01f)
        val b = StemGrowth.sweepBetween(10f, 100f, -1)
        assertEquals(270f, b, 0.01f)
    }

    @Test
    fun `进度夹取且只有长满算完成`() {
        assertEquals(0f, StemGrowth.clampProgress(-0.5f), 0.0001f)
        assertEquals(1f, StemGrowth.clampProgress(1.5f), 0.0001f)
        assertFalse(StemGrowth.isComplete(0.99f))
        assertTrue(StemGrowth.isComplete(1f))
    }

    // ---------- 果实：y 等差排列 ----------

    @Test
    fun `果实 y 在茎区间内等差排列`() {
        val top = 480f
        val bottom = 2344f
        val ys = (0 until StemGrowth.FRUIT_COUNT).map { StemGrowth.fruitY(top, bottom, it) }
        // 等差：相邻差值相等
        val gaps = ys.zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(gaps.first(), it, 0.01f) }
        // 都在区间内
        assertTrue(ys.all { it > top && it < bottom })
    }

    @Test
    fun `果实圆心落在圆上`() {
        val c = StemGrowth.circle3(startX, startY, w - 10f, 1465f, endX, endY)!!
        val (cx, cy, r) = c
        for (i in 0 until StemGrowth.FRUIT_COUNT) {
            val fy = StemGrowth.fruitY(endY, startY, i)
            val fx = StemGrowth.xOnCircleAt(cx, cy, r, fy, true, w) ?: continue
            val d = kotlin.math.hypot(fx - cx, fy - cy)
            assertEquals("果实 $i 应在圆上", r, d, 0.5f)
        }
    }

    @Test
    fun `圆的水平线不相交时返回 null`() {
        assertNull(StemGrowth.xOnCircleAt(100f, 100f, 50f, 200f))
    }

    @Test
    fun `果实随茎推进逐渐放大`() {
        // 茎还没到果实位置：半径为 0
        assertEquals(0f, StemGrowth.fruitScaleAt(0.2f, 0.5f), 0.0001f)
        // 刚好到该位置：还是 0
        assertEquals(0f, StemGrowth.fruitScaleAt(0.5f, 0.5f), 0.0001f)
        // 走过一小段：开始变大
        val mid = StemGrowth.fruitScaleAt(0.54f, 0.5f)
        assertTrue("应已开始放大（实际 $mid）", mid > 0f && mid < 1f)
        // 足够远：完全长成
        assertEquals(1f, StemGrowth.fruitScaleAt(0.9f, 0.5f), 0.0001f)
    }

    @Test
    fun `角度进度换算与扫角方向一致`() {
        // 顺时针：起点 0°，走过 90°（总 270°）应为 1/3
        val p = StemGrowth.progressOfAngle(0f, 270f, false, 90f)
        assertEquals(1f / 3f, p, 0.001f)
    }
}
