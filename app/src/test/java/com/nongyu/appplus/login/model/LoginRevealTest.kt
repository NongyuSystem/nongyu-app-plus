package com.nongyu.appplus.login.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * [LoginReveal] 圆形揭示几何单测（票 #94 修订）。
 *
 * 除了常规边界，重点钉死维护者明确提的两条约束：
 * 1. 缺口**只扩不缩**（半径单调不减）；
 * 2. **位移不大于半径增长速度**——圆心上升量始终小于半径增量。
 */
class LoginRevealTest {

    // ---------- 仅主动登录成功才播 ----------

    @Test
    fun `登录中心到主界面才播放`() {
        assertTrue(LoginReveal.shouldPlay(LoginDestination.LOGIN_CENTER, LoginDestination.MAIN))
    }

    @Test
    fun `冷启动与其它跳变都不播放`() {
        assertFalse(LoginReveal.shouldPlay(LoginDestination.MAIN, LoginDestination.MAIN))
        assertFalse(LoginReveal.shouldPlay(LoginDestination.MAIN, LoginDestination.LOGIN_CENTER))
        assertFalse(LoginReveal.shouldPlay(LoginDestination.LOGIN_CENTER, LoginDestination.LOGIN_CENTER))
    }

    // ---------- 起止状态 ----------

    @Test
    fun `起点半径与按钮半径重合`() {
        assertEquals(
            LoginReveal.START_RADIUS_RATIO,
            LoginReveal.radiusRatio(0f),
            0.0001f,
        )
    }

    @Test
    fun `起点圆心在登录按钮位置`() {
        assertEquals(
            LoginReveal.START_CENTER_Y_RATIO,
            LoginReveal.centerYRatio(0f),
            0.0001f,
        )
    }

    @Test
    fun `终点圆心在屏幕中心且向上移动`() {
        assertEquals(LoginReveal.END_CENTER_Y_RATIO, LoginReveal.centerYRatio(1f), 0.0001f)
        assertTrue(
            "圆心应向上（y 变小）",
            LoginReveal.centerYRatio(1f) < LoginReveal.centerYRatio(0f),
        )
    }

    @Test
    fun `终点半径足以覆盖整屏最远角`() {
        val far = sqrt(0.5f * 0.5f + 0.5f * 0.5f)   // 屏幕中心到角
        assertTrue(
            "终点半径 ${LoginReveal.END_RADIUS_RATIO} 必须大于 $far",
            LoginReveal.END_RADIUS_RATIO > far,
        )
    }

    // ---------- 约束 1：缺口只扩不缩 ----------

    @Test
    fun `半径单调不减`() {
        var prev = -1f
        var p = 0f
        while (p <= 1.0001f) {
            val r = LoginReveal.radiusRatio(p)
            assertTrue("半径不应回退（p=$p）", r >= prev - 0.0001f)
            prev = r
            p += 0.01f
        }
    }

    // ---------- 约束 2：位移不大于半径增长 ----------

    @Test
    fun `圆心上升量与半径增量逐段比对_位移不超过半径增长`() {
        // 每步 0.02：比较「本步半径增量」与「本步位移（按屏幕宽归一化后）」
        // 注意圆心 y 比例是相对**高**、半径是相对**宽**，比较时统一到宽度。
        // 基准机宽高比 540:1200，故 y 比例的位移乘以 (宽/高) 才与半径同量纲。
        val widthOverHeight = 540f / 1200f

        var p = 0f
        var prevRadius = LoginReveal.radiusRatio(0f)
        var prevCenterY = LoginReveal.centerYRatio(0f)
        while (p < 1f) {
            val next = (p + 0.02f).coerceAtMost(1f)
            val radius = LoginReveal.radiusRatio(next)
            val centerY = LoginReveal.centerYRatio(next)

            val dRadius = radius - prevRadius
            val dShift = (prevCenterY - centerY) * widthOverHeight   // 向上为正

            assertTrue(
                "步进 $p→$next：位移 $dShift 不应大于半径增长 $dRadius",
                dShift <= dRadius + 0.0001f,
            )
            prevRadius = radius
            prevCenterY = centerY
            p = next
        }
    }

    @Test
    fun `圆心向上移动但不会移出屏幕上沿`() {
        var p = 0f
        while (p <= 1.0001f) {
            val y = LoginReveal.centerYRatio(p)
            assertTrue("圆心不应移出屏幕（y=$y）", y >= 0f)
            assertTrue("圆心不应移出屏幕（y=$y）", y <= 1f)
            p += 0.02f
        }
    }

    @Test
    fun `圆心单调向上`() {
        var prev = 2f
        var p = 0f
        while (p <= 1.0001f) {
            val y = LoginReveal.centerYRatio(p)
            assertTrue("圆心应持续向上（p=$p, y=$y）", y <= prev + 0.0001f)
            prev = y
            p += 0.01f
        }
    }

    // ---------- 收尾 ----------

    @Test
    fun `结束时应判定登录层可移除`() {
        assertTrue(LoginReveal.isLoginLayerGone(1f))
    }

    @Test
    fun `起点不判定登录层消失`() {
        assertFalse(LoginReveal.isLoginLayerGone(0f))
    }

    @Test
    fun `登录层消失的时刻在转场末尾之前或恰好末尾`() {
        // 找到消失点，断言它 <= 1.0（保证转场结束时一定已经覆盖全屏）
        var p = 0f
        var goneAt = 1.01f
        while (p <= 1.0001f) {
            if (LoginReveal.isLoginLayerGone(p)) {
                goneAt = p
                break
            }
            p += 0.005f
        }
        assertTrue("登录层应在转场结束前消失（goneAt=$goneAt）", goneAt <= 1.0f)
    }

    // ---------- 夹紧 ----------

    @Test
    fun `进度越界被夹紧`() {
        assertEquals(LoginReveal.radiusRatio(0f), LoginReveal.radiusRatio(-1f), 0.0001f)
        assertEquals(LoginReveal.radiusRatio(1f), LoginReveal.radiusRatio(2f), 0.0001f)
    }

    @Test
    fun `圆心横向始终居中`() {
        assertEquals(0.5f, LoginReveal.CENTER_X_RATIO, 0.0001f)
    }

    @Test
    fun `总时长是正数`() {
        assertTrue(LoginReveal.DURATION_MS > 0)
    }
}
