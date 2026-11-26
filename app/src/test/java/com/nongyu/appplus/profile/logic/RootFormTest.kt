package com.nongyu.appplus.profile.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 #117：个人主页「根系形态」两态吸附的纯判定接缝单测（纯 JVM，不碰 Android / Compose）。
 *
 * 只断言外部行为：上滑/下拉的位移何时越过阈值、松手吸附到哪个形态、
 * 跟手位移如何夹取到合法区间。动画本体（显式 Animatable 的双路径）不写 UI 测试，
 * 靠 assembleRelease + 真机四步验收。
 */
class RootFormTest {

    private val threshold = 96f

    // ---------- 阈值推导 ----------

    @Test
    fun `吸附阈值按揭示高度比例线性缩放且非负`() {
        assertEquals(0f, RootForm.thresholdPxOf(0f), 0f)
        assertEquals(0f, RootForm.thresholdPxOf(-100f), 0f)
        assertEquals(400f * RootForm.THRESHOLD_FRACTION, RootForm.thresholdPxOf(400f), 0f)
        assertEquals(800f * RootForm.THRESHOLD_FRACTION, RootForm.thresholdPxOf(800f), 0f)
    }

    // ---------- 吸附目标 ----------

    @Test
    fun `跟手位移小于阈值时松手吸附到收起态`() {
        assertFalse(RootForm.isExpandedTarget(expanded = false, dragPx = threshold - 0.1f, thresholdPx = threshold))
    }

    @Test
    fun `跟手位移等于阈值时松手吸附到展开态`() {
        assertTrue(RootForm.isExpandedTarget(expanded = false, dragPx = threshold, thresholdPx = threshold))
    }

    @Test
    fun `跟手位移大于阈值时松手吸附到展开态`() {
        assertTrue(RootForm.isExpandedTarget(expanded = false, dragPx = threshold + 120f, thresholdPx = threshold))
    }

    @Test
    fun `下拉位移未超过阈值时松手保持展开态`() {
        assertTrue(RootForm.isExpandedTarget(expanded = true, dragPx = -(threshold - 1f), thresholdPx = threshold))
    }

    @Test
    fun `下拉位移达到阈值时松手吸附到收起态`() {
        assertFalse(RootForm.isExpandedTarget(expanded = true, dragPx = -threshold, thresholdPx = threshold))
    }

    @Test
    fun `下拉位移超过阈值时松手吸附到收起态`() {
        assertFalse(RootForm.isExpandedTarget(expanded = true, dragPx = -(threshold + 200f), thresholdPx = threshold))
    }

    @Test
    fun `零位移时松手保持原态`() {
        assertFalse(RootForm.isExpandedTarget(expanded = false, dragPx = 0f, thresholdPx = threshold))
        assertTrue(RootForm.isExpandedTarget(expanded = true, dragPx = 0f, thresholdPx = threshold))
    }

    @Test
    fun `无拖拽位移时 snapTo 保持当前形态`() {
        assertEquals(
            RootForm.COLLAPSED,
            RootForm.snapTo(expanded = false, dragPx = 0f, thresholdPx = threshold),
        )
        assertEquals(
            RootForm.EXPANDED,
            RootForm.snapTo(expanded = true, dragPx = 0f, thresholdPx = threshold),
        )
    }

    @Test
    fun `snapTo 结果与 isExpandedTarget 一致`() {
        listOf(false, true).forEach { expanded ->
            listOf(-threshold * 3f, -threshold, -1f, 0f, 1f, threshold, threshold * 3f).forEach { drag ->
                val expected = if (RootForm.isExpandedTarget(expanded, drag, threshold)) {
                    RootForm.EXPANDED
                } else {
                    RootForm.COLLAPSED
                }
                assertEquals("expanded=$expanded drag=$drag", expected, RootForm.snapTo(expanded, drag, threshold))
            }
        }
    }

    // ---------- 跟手位移夹取 ----------

    @Test
    fun `收起态上滑位移夹在零与展开高度之间`() {
        assertEquals(0f, RootForm.followDrift(expanded = false, dragPx = -50f, revealedPx = 400f), 0f)
        assertEquals(50f, RootForm.followDrift(expanded = false, dragPx = 50f, revealedPx = 400f), 0f)
        assertEquals(400f, RootForm.followDrift(expanded = false, dragPx = 900f, revealedPx = 400f), 0f)
    }

    @Test
    fun `展开态下滑位移夹在负的展开高度与零之间`() {
        assertEquals(0f, RootForm.followDrift(expanded = true, dragPx = 30f, revealedPx = 400f), 0f)
        assertEquals(-50f, RootForm.followDrift(expanded = true, dragPx = -50f, revealedPx = 400f), 0f)
        assertEquals(-400f, RootForm.followDrift(expanded = true, dragPx = -900f, revealedPx = 400f), 0f)
    }

    @Test
    fun `展开高度为零时跟手位移恒为零`() {
        assertEquals(0f, RootForm.followDrift(expanded = false, dragPx = 120f, revealedPx = 0f), 0f)
        assertEquals(0f, RootForm.followDrift(expanded = true, dragPx = -120f, revealedPx = 0f), 0f)
    }
}
