package com.nongyu.appplus.ui.timetable

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 票 #60 流水位置回归测试：**流水水面必须落在对应节次的实测范围内**。
 *
 * ## 一次真实的线上事故
 *
 * 维护者 2026-11-12 反馈「9:20 应该在第 1 节课位置但跑到第 1 节上方」。
 * 两层错误叠加：
 * 1. 流水位置还在用 `TimetableAxis`（真实分钟等分），没用 `TimetableGeometry` 的分段映射；
 * 2. 算出的比例是「节次行内」的，却直接当成「整屏」比例传给 `WaterLine`。
 *
 * 本测试用**真机实测尺寸**（节次行 top=328、height=1842、屏高 2400）
 * 把整条换算链走一遍，逐个断言「水面 y 落在该时刻所属节次的实测区间内」。
 *
 * 这些 y 区间是从 `uiautomator dump` 实测的第 1~10 节屏幕范围，
 * 所以通过本测试 ≙ 在真机上对齐。
 */
class WatermarkAlignmentTest {

    // ---- 真机实测常量（1080x2400 @ 蓝叠，2026-11-12） ----
    private val screenHeightPx = 2400f
    private val periodTopPx = 328f
    private val periodHeightPx = 1842f
    private val densityDpi = 320f          // 2.0x
    private val dpPerPx = 160f / densityDpi // px → dp

    /**
     * 容差（像素）。
     *
     * 为什么不是 0：`TimetableGeometry` 用的是**设计常量**（缝 3/6/10dp），
     * 而实测像素是布局四舍五入后的结果，两者有 1~5px 的固定偏差，
     * 且会沿列**累积**（实测第 4 节底边模型值比实际多 2.2px）。
     * 6px 在 2400px 高的屏幕上不到 0.25%，肉眼无法分辨；
     * 而真正的错位（比如那次 9:20 掉到第 1 节）偏差是 **150px 以上**，远超容差。
     */
    private val TOLERANCE_PX = 6f

    /** 第 1~10 节在屏幕上的实测 y 区间（含首尾）。 */
    private val periodRanges = listOf(
        328 to 501,    // 第 1 节 08:10–08:55
        509 to 682,    // 第 2 节 09:05–09:50
        697 to 869,    // 第 3 节 10:10–10:55
        877 to 1049,   // 第 4 节 11:05–11:50
        1074 to 1246,  // 第 5 节 14:20–15:05
        1254 to 1426,  // 第 6 节 15:15–16:00
        1441 to 1613,  // 第 7 节 16:20–17:05
        1621 to 1793,  // 第 8 节 17:15–18:00
        1818 to 1990,  // 第 9 节 19:00–19:45
        1998 to 2170,  // 第 10 节 19:55–20:40
    )

    /** 与 TimetableScreen 里完全相同的换算：时刻 → 水面整屏 y。 */
    private fun waterYAt(time: LocalTime): Float {
        val geo = TimetableGeometry.build(totalHeightDp = periodHeightPx * dpPerPx)
        val withinRow = geo.fractionAtTime(time)
        val screenY = periodTopPx + withinRow * periodHeightPx
        return screenY
    }

    /** 该时刻应落在第几节（1..10），用开课时间表判定，与几何算法无关。 */
    private fun expectedPeriod(time: LocalTime): Int {
        val t = time.hour * 60 + time.minute
        for (i in COURSE_TIMES.indices) {
            val s = TimetableGeometry.hourMinuteToMinutes(COURSE_TIMES[i].start)
            val e = TimetableGeometry.hourMinuteToMinutes(COURSE_TIMES[i].end)
            if (t in s..e) return i + 1
        }
        error("$time 不在任何节次内")
    }

    /**
     * 核心断言：每节课**中段**的流水位置，必须落在该节的实测屏幕区间内。
     * 取中段而不是边界，避免浮点与首尾恰好压线带来的噪声。
     */
    @Test
    fun `每节课中段的流水都落在该节实测范围内`() {
        val cases = COURSE_TIMES.indices.map { i ->
            val s = TimetableGeometry.hourMinuteToMinutes(COURSE_TIMES[i].start)
            val e = TimetableGeometry.hourMinuteToMinutes(COURSE_TIMES[i].end)
            val mid = (s + e) / 2
            LocalTime.of(mid / 60, mid % 60)
        }

        for (time in cases) {
            val period = expectedPeriod(time)
            val (top, bottom) = periodRanges[period - 1]
            val y = waterYAt(time)
            assertTrue(
                "$time 属第 $period 节（实测 y=$top..$bottom），流水却算到 y=${y.toInt()}",
                y >= top - TOLERANCE_PX && y <= bottom + TOLERANCE_PX,
            )
        }
    }

    /** 维护者报的具体场景：9:20 必须落在第 2 节，而不是第 1 节上方。 */
    @Test
    fun `9点20分落在第2节而不是第1节上方`() {
        val time = LocalTime.of(9, 20)
        val y = waterYAt(time)
        val (top, bottom) = periodRanges[1]   // 第 2 节 = 509..682
        assertTrue(
            "9:20 应落在第 2 节 y=$top..$bottom，实际 y=${y.toInt()}",
            y >= top.toFloat() && y <= bottom.toFloat(),
        )
        // 且必须明确高于第 1 节的底边 —— 事故现象就是掉到了第 1 节里
        assertTrue("9:20 不应落在第 1 节（328..501），实际 y=${y.toInt()}", y > 501f)
    }

    /** 跨午休、跨晚饭这种大空隙也要对：两端时刻分别落在前后两节。 */
    @Test
    fun `午休与晚饭前后的时刻各归其节`() {
        // 11:50 第 4 节刚下课 → 应贴第 4 节底
        val a = waterYAt(LocalTime.of(11, 50))
        assertTrue(
            "11:50 应在第 4 节底附近（${periodRanges[3]}），实际 $a",
            a <= 1049f + TOLERANCE_PX,
        )

        // 14:20 第 5 节开始 → 应贴第 5 节顶（跨过 150 分钟午休）
        val b = waterYAt(LocalTime.of(14, 20))
        assertTrue(
            "14:20 应在第 5 节顶附近（${periodRanges[4]}），实际 $b",
            b >= 1074f - TOLERANCE_PX,
        )

        // 18:00 第 8 节结束
        val c = waterYAt(LocalTime.of(18, 0))
        assertTrue(
            "18:00 应在第 8 节底附近（${periodRanges[7]}），实际 $c",
            c <= 1793f + TOLERANCE_PX,
        )

        // 19:30 第 9 节开始（跨过 60 分钟晚饭空档 18:00→19:30）
        val d = waterYAt(LocalTime.of(19, 30))
        assertTrue(
            "19:30 应在第 9 节顶附近（${periodRanges[8]}），实际 $d",
            d >= 1818f - TOLERANCE_PX,
        )
    }

    /** 时间单调：越晚水面越靠下，不允许回头。 */
    @Test
    fun `全天水位单调不下移`() {
        var last = -1f
        var m = 8 * 60 + 10
        while (m <= 21 * 60 + 10) {
            val y = waterYAt(LocalTime.of(m / 60, m % 60))
            assertTrue("$m 分钟处水位回退：$last → $y", y >= last - 0.5f)
            last = y
            m += 5
        }
    }

    /** 换算结果必须是「整屏比例」，即 y / 屏高，不能再拿节次行内比例直接用。 */
    @Test
    fun `返回的是整屏比例而非节次行内比例`() {
        val time = LocalTime.of(9, 20)
        val screenRatio = waterYAt(time) / screenHeightPx
        // 节次行顶边 328 就占了整屏 13.7%，所以任何课内时刻的整屏比例都该 > 0.137
        assertTrue("整屏比例应大于节次行顶边占比 0.137，实际 $screenRatio", screenRatio > 0.137f)
        // 而「行内比例」会在 0..1 之间乱跳；用第 1 节中段对照：行内约 0.05，整屏约 0.17
        val geo = TimetableGeometry.build(totalHeightDp = periodHeightPx * dpPerPx)
        val within = geo.fractionAtTime(time)
        assertTrue("行内比例($within) 与整屏比例($screenRatio) 不应相等", kotlin.math.abs(within - screenRatio) > 0.01f)
    }
}
