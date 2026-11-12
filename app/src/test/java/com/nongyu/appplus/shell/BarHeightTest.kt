package com.nongyu.appplus.shell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底部条目标高度。
 *
 * 这条规则错了的表现是「上划没有动画」「退回时课表弹一下」，
 * 都属于肉眼难稳定复现的问题，必须靠单测把每个形态的组合锁住。
 */
class BarHeightTest {

    private val metrics = BarMetrics(
        brief = 128.dp,
        full = 340.dp,
        fullDelete = 384.dp,
        addSchedule = 392.dp,
        scheduleDetail = 210.dp,
        exploration = 120.dp,
    )
    private val navHeight = 62.dp

    private fun target(
        barMode: BarMode,
        isCollapsing: Boolean = false,
        detailStage: DetailStage = DetailStage.Brief,
        dragDp: Float = 0f,
    ) = barTargetHeight(barMode, isCollapsing, detailStage, dragDp, metrics, navHeight)

    @Test
    fun `常态与输入态都是导航栏高度`() {
        assertEquals(navHeight, target(BarMode.Normal))
        assertEquals(navHeight, target(BarMode.Input))
    }

    @Test
    fun `表单卡与日程明细卡各有固定高度`() {
        assertEquals(392.dp, target(BarMode.AddSchedule))
        assertEquals(210.dp, target(BarMode.ScheduleDetail))
    }

    @Test
    fun `详情卡三档高度`() {
        assertEquals(128.dp, target(BarMode.Detail, detailStage = DetailStage.Brief))
        assertEquals(340.dp, target(BarMode.Detail, detailStage = DetailStage.Full))
        assertEquals(384.dp, target(BarMode.Detail, detailStage = DetailStage.FullDelete))
    }

    @Test
    fun `拖拽位移叠加在详情卡当前档位上`() {
        // 往上拉 40dp：简介态 128 + 40 = 168
        assertEquals(168.dp, target(BarMode.Detail, dragDp = 40f))
        // 详情态 340 + 40
        assertEquals(380.dp, target(BarMode.Detail, detailStage = DetailStage.Full, dragDp = 40f))
    }

    @Test
    fun `往下拖不低于导航栏高度`() {
        // 旧实现就是 coerceAtLeast(navHeight)，拖到最底会吸附到退出
        assertEquals(navHeight, target(BarMode.Detail, dragDp = -500f))
    }

    @Test
    fun `退出收拢时高度一定是导航栏`() {
        // 收拢期优先级最高：不论当前是哪个形态、拖到哪儿，目标都回落到 navHeight，
        // 这样动画走完才能安全清掉 detailCourse
        assertEquals(navHeight, target(BarMode.Detail, isCollapsing = true, detailStage = DetailStage.Full))
        assertEquals(navHeight, target(BarMode.AddSchedule, isCollapsing = true))
        assertEquals(navHeight, target(BarMode.ScheduleDetail, isCollapsing = true))
    }

    @Test
    fun `拖拽位移只在详情态生效`() {
        // 旧实现里 dragDp 只参与 Detail 分支；其他形态拖拽不会改变高度
        assertEquals(392.dp, target(BarMode.AddSchedule, dragDp = 100f))
        assertEquals(navHeight, target(BarMode.Normal, dragDp = 100f))
    }

    // ---- 三档吸附 ----

    @Test
    fun `简介态不拖时仍停在简介态`() {
        assertEquals(
            DetailSnap.To(DetailStage.Brief),
            snapDetailStage(DetailStage.Brief, 0f, metrics),
        )
    }

    @Test
    fun `从简介态上拉过两档中点就吸附到详情态`() {
        // 简介 128、详情 340，中点 = 234；128 + 120 = 248 > 234
        assertEquals(
            DetailSnap.To(DetailStage.Full),
            snapDetailStage(DetailStage.Brief, 120f, metrics),
        )
    }

    @Test
    fun `从详情态继续上拉吸附到删除档`() {
        // 详情 340、删除 384，中点 = 362；340 + 30 = 370 > 362
        assertEquals(
            DetailSnap.To(DetailStage.FullDelete),
            snapDetailStage(DetailStage.Full, 30f, metrics),
        )
    }

    @Test
    fun `从删除档下拉吸附回详情态`() {
        // 384 - 30 = 354，介于 234 与 362 之间 → Full
        assertEquals(
            DetailSnap.To(DetailStage.Full),
            snapDetailStage(DetailStage.FullDelete, -30f, metrics),
        )
    }

    @Test
    fun `拖到低于简介档55%就退出`() {
        // 128 * 0.55 = 70.4；128 - 60 = 68 < 70.4 → 退出
        assertEquals(DetailSnap.Collapse, snapDetailStage(DetailStage.Brief, -60f, metrics))
    }

    @Test
    fun `阈值边界内仍然吸附而不是退出`() {
        // 128 - 50 = 78 >= 70.4 → 仍在简介态，不退出
        assertEquals(
            DetailSnap.To(DetailStage.Brief),
            snapDetailStage(DetailStage.Brief, -50f, metrics),
        )
    }
}
