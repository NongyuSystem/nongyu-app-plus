package com.nongyu.appplus.profile.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * 票 #115：开页生长动画的纯判定接缝单测（纯 JVM，不碰 Android / Compose）。
 *
 * 只断言外部行为：五段的时间边界与顺序、各段内部进度的归一化、
 * 内容淡入 / 头像收尾的透明度曲线、减弱动效与低端机的降级判定、
 * 字符头像取字规则、果实等间距角度。动画本体靠 assembleRelease + 真机验收。
 */
class EnterAnimationTest {

    // ---------- 分段边界 ----------

    @Test
    fun `五段按时间顺序推进，边界落在下一段`() {
        val a = EnterAnimation
        assertEquals(EnterAnimation.Segment.BRANCH, a.segmentAt(a.progressAtTime(0L)))
        assertEquals(
            EnterAnimation.Segment.BRANCH,
            a.segmentAt(a.progressAtTime(a.durationOf(EnterAnimation.Segment.BRANCH) - 1L)),
        )
        assertEquals(
            EnterAnimation.Segment.ORBIT,
            a.segmentAt(a.progressAtTime(a.durationOf(EnterAnimation.Segment.BRANCH))),
        )
        assertEquals(
            EnterAnimation.Segment.ORBIT,
            a.segmentAt(
                a.progressAtTime(a.endTimeOf(EnterAnimation.Segment.BRANCH) + a.durationOf(EnterAnimation.Segment.ORBIT) - 1L),
            ),
        )
        assertEquals(
            EnterAnimation.Segment.FRUIT,
            a.segmentAt(a.progressAtTime(a.endTimeOf(EnterAnimation.Segment.ORBIT))),
        )
        assertEquals(
            EnterAnimation.Segment.FADE_IN,
            a.segmentAt(a.progressAtTime(a.endTimeOf(EnterAnimation.Segment.FRUIT))),
        )
        assertEquals(
            EnterAnimation.Segment.AVATAR,
            a.segmentAt(a.progressAtTime(a.endTimeOf(EnterAnimation.Segment.FADE_IN))),
        )
        assertEquals(EnterAnimation.Segment.AVATAR, a.segmentAt(a.progressAtTime(a.TOTAL_MS)))
    }

    @Test
    fun `进度越界被夹在零到一之间`() {
        assertEquals(EnterAnimation.Segment.BRANCH, EnterAnimation.segmentAt(-2f))
        assertEquals(EnterAnimation.Segment.AVATAR, EnterAnimation.segmentAt(3f))
        assertEquals(0f, EnterAnimation.progressAtTime(-100L), 0f)
        assertEquals(1f, EnterAnimation.progressAtTime(999_999L), 0f)
    }

    @Test
    fun `各段时间之和等于总时长且起止首尾闭合`() {
        val a = EnterAnimation
        val sum = enumValues<EnterAnimation.Segment>().sumOf { a.durationOf(it) }
        assertEquals(a.TOTAL_MS, sum)
        assertEquals(0L, a.startTimeOf(EnterAnimation.Segment.BRANCH))
        assertEquals(a.TOTAL_MS, a.endTimeOf(EnterAnimation.Segment.AVATAR))
        assertTrue(a.TOTAL_MS > 0L)
    }

    // ---------- 段内进度 ----------

    @Test
    fun `段内进度在段首为零段尾为一，未到为零已过为一`() {
        val a = EnterAnimation
        val branchStart = a.startTimeOf(EnterAnimation.Segment.BRANCH)
        val branchEnd = a.endTimeOf(EnterAnimation.Segment.BRANCH)

        assertEquals(0f, a.segmentProgress(a.progressAtTime(branchStart), EnterAnimation.Segment.BRANCH), 0f)
        assertEquals(1f, a.segmentProgress(a.progressAtTime(branchEnd), EnterAnimation.Segment.BRANCH), 0f)
        assertEquals(0.5f, a.segmentProgress(a.progressAtTime((branchStart + branchEnd) / 2L), EnterAnimation.Segment.BRANCH), 0.02f)
        // 还没轮到这一段：0；已经播完这一段：1
        assertEquals(0f, a.segmentProgress(a.progressAtTime(0L), EnterAnimation.Segment.FRUIT), 0f)
        assertEquals(1f, a.segmentProgress(a.progressAtTime(a.TOTAL_MS), EnterAnimation.Segment.BRANCH), 0f)
    }

    // ---------- 淡入 / 收尾透明度 ----------

    @Test
    fun `内容在淡入段从零升到一，前后保持夹取`() {
        val a = EnterAnimation
        val fadeStart = a.startTimeOf(EnterAnimation.Segment.FADE_IN)
        val fadeEnd = a.endTimeOf(EnterAnimation.Segment.FADE_IN)
        assertEquals(0f, a.contentAlpha(a.progressAtTime(0L)), 0f)
        assertEquals(0f, a.contentAlpha(a.progressAtTime(fadeStart)), 0f)
        assertEquals(1f, a.contentAlpha(a.progressAtTime(fadeEnd)), 0f)
        assertEquals(1f, a.contentAlpha(a.progressAtTime(a.TOTAL_MS)), 0f)
        val mid = a.contentAlpha(a.progressAtTime((fadeStart + fadeEnd) / 2L))
        assertTrue(mid > 0f && mid < 1f)
    }

    @Test
    fun `字符头像只在收尾段淡入`() {
        val a = EnterAnimation
        val avatarStart = a.startTimeOf(EnterAnimation.Segment.AVATAR)
        val avatarEnd = a.endTimeOf(EnterAnimation.Segment.AVATAR)
        assertEquals(0f, a.avatarAlpha(a.progressAtTime(avatarStart)), 0f)
        assertEquals(1f, a.avatarAlpha(a.progressAtTime(avatarEnd)), 0f)
        assertEquals(0f, a.avatarAlpha(a.progressAtTime(0L)), 0f)
        assertEquals(1f, a.avatarAlpha(1f), 0f)
    }

    // ---------- 降级判定 ----------

    @Test
    fun `系统动画时长缩放为零或负数即视为减弱动效`() {
        assertTrue(EnterAnimation.shouldReduceMotion(0f))
        assertTrue(EnterAnimation.shouldReduceMotion(-0.5f))
        assertFalse(EnterAnimation.shouldReduceMotion(0.5f))
        assertFalse(EnterAnimation.shouldReduceMotion(1f))
        assertFalse(EnterAnimation.shouldReduceMotion(2f))
    }

    @Test
    fun `减弱动效或低端机任一成立就跳过分段动画`() {
        assertTrue(EnterAnimation.shouldSkipAnimation(durationScale = 0f, lowEndDevice = false))
        assertTrue(EnterAnimation.shouldSkipAnimation(durationScale = 1f, lowEndDevice = true))
        assertTrue(EnterAnimation.shouldSkipAnimation(durationScale = 0f, lowEndDevice = true))
        assertFalse(EnterAnimation.shouldSkipAnimation(durationScale = 1f, lowEndDevice = false))
    }

    // ---------- 字符头像取字 ----------

    @Test
    fun `字符头像取姓名首字，空姓名回落到占位字`() {
        assertEquals("张", EnterAnimation.avatarInitial("张三"))
        assertEquals("李", EnterAnimation.avatarInitial(" 李四 "))
        assertEquals("农", EnterAnimation.avatarInitial(null))
        assertEquals("农", EnterAnimation.avatarInitial(""))
        assertEquals("农", EnterAnimation.avatarInitial("   "))
    }

    // ---------- 果实等间距 ----------

    @Test
    fun `果实个数固定且角度等间距，首枚在正上方`() {
        val angles = EnterAnimation.fruitAngles()
        assertEquals(EnterAnimation.FRUIT_COUNT, angles.size)
        assertEquals(-PI / 2.0, angles.first(), 0.0001)
        val step = 2.0 * PI / EnterAnimation.FRUIT_COUNT
        angles.forEachIndexed { index, angle ->
            assertEquals(-PI / 2.0 + index * step, angle, 0.0001)
        }
        // 末枚与第一枚绕回，间距同样是一步
        val wrap = (angles.first() + 2.0 * PI - angles.last())
        assertEquals(step, wrap, 0.0001)
    }

    @Test
    fun `单枚果实错峰出现，末枚最后出现`() {
        val a = EnterAnimation
        // 段首所有果实都还没长出来
        assertEquals(0f, a.fruitLocalProgress(index = 0, segmentProgress = 0f), 0f)
        assertEquals(0f, a.fruitLocalProgress(index = a.FRUIT_COUNT - 1, segmentProgress = 0f), 0f)
        // 段尾全部长满
        assertEquals(1f, a.fruitLocalProgress(index = 0, segmentProgress = 1f), 0f)
        assertEquals(1f, a.fruitLocalProgress(index = a.FRUIT_COUNT - 1, segmentProgress = 1f), 0f)
        // 中段：前面的先长，后面的还没开始
        val mid = 0.5f
        assertTrue(a.fruitLocalProgress(0, mid) > a.fruitLocalProgress(a.FRUIT_COUNT - 1, mid))
        assertTrue(abs(a.fruitLocalProgress(0, mid) - 1f) < 0.0001f)
    }
}
