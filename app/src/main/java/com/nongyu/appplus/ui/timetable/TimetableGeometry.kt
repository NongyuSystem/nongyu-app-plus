package com.nongyu.appplus.ui.timetable

import java.time.LocalTime

/**
 * 课表「纵向几何」与「真实时钟」之间的**分段线性**映射（票 #60）。
 *
 * ## 为什么需要它
 *
 * 课表的行是「10 节**等高**行 + 若干固定 dp 的缝」：
 * - 每节课不管多少分钟，占的视觉高度都相同；
 * - 节与节之间的缝按固定 dp 给（常规 3 / 大课间 6 / 午休 10），
 *   但真实时间跨度差别巨大：11:50→14:20 是 150 分钟，却只占 10dp。
 *
 * 而时间轴（`TimetableAxis`）是按**真实分钟**等比的。
 * 两者密度完全不同，于是出现「流水/时间文字/松手节次 都对不上课程行」的问题。
 *
 * 这里把两边接起来：给出**每一段的边界**（视觉比例 ↔ 分钟），
 * 段内线性插值。于是：
 * - 45 分钟的一节课 → 摊在整行高度上（密度低，慢）；
 * - 150 分钟的午休 → 压在 10dp 的缝里（密度高，快）；
 * 视觉上就与右侧课程行严格对齐。
 *
 * ## 只影响换算，不影响尺子外观
 *
 * 刻度画法（4 短 1 长、等距）由 [TimetableRuler] 自己决定，不要用这里的分段。
 * 本类只负责「某个纵向比例对应几点」与「某点对应哪个比例」。
 */
object TimetableGeometry {

    /** 节与节之间的缝宽（dp），必须与 `TimetableGrid.gapAfter` 一致。 */
    fun gapDpAfter(period: Int): Float = when (period) {
        2, 6 -> 6f     // 大课间
        4, 8 -> 10f    // 午休 / 晚饭
        else -> 3f     // 常规
    }

    /** 一段：视觉比例区间 [fracStart, fracEnd] 对应分钟区间 [minuteStart, minuteEnd]。 */
    internal data class Segment(
        val fracStart: Float,
        val fracEnd: Float,
        val minuteStart: Int,
        val minuteEnd: Int,
    )

    /**
     * 按给定**总高度**（dp）构建映射。
     *
     * 行高由「总高 - 所有缝之和」再除以 10 得到；
     * 缝的视觉占比 = 缝 dp / 行高。这样与 Compose 用 `weight(1f)` + 固定 gap
     * 排出来的实际位置一致。
     *
     * @param totalHeightDp 时间条覆盖的总高度（与节次行区域等高）
     */
    fun build(totalHeightDp: Float): Mapper {
        val sumGap = (1 until PERIOD_COUNT).sumOf { gapDpAfter(it).toDouble() }.toFloat()
        val rowH = ((totalHeightDp - sumGap) / PERIOD_COUNT).coerceAtLeast(0.001f)

        // 视觉单位：一行 = 1.0，一条缝 = 缝dp / 行高
        val rowUnit = 1f
        val gapUnit = { p: Int -> gapDpAfter(p) / rowH }
        val totalUnit = PERIOD_COUNT * rowUnit +
            (1 until PERIOD_COUNT).sumOf { gapUnit(it).toDouble() }.toFloat()

        val segs = ArrayList<Segment>(PERIOD_COUNT * 2)
        var cursor = 0f
        for (p in 1..PERIOD_COUNT) {
            val t = COURSE_TIMES[p - 1]
            val s = hourMinuteToMinutes(t.start)
            val e = hourMinuteToMinutes(t.end)
            val f0 = cursor / totalUnit
            cursor += rowUnit
            val f1 = cursor / totalUnit
            segs += Segment(f0, f1, s, e)

            if (p < PERIOD_COUNT) {
                // 缝：从本节结束到下一节开始（真实分钟跨度可能很大）
                val next = COURSE_TIMES[p]
                val ns = hourMinuteToMinutes(next.start)
                val g0 = cursor / totalUnit
                cursor += gapUnit(p)
                val g1 = cursor / totalUnit
                segs += Segment(g0, g1, e, ns)
            }
        }
        return Mapper(segs)
    }

    internal fun hourMinuteToMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return 0
        val h = parts[0].toIntOrNull() ?: return 0
        val m = parts[1].toIntOrNull() ?: return 0
        return h * 60 + m
    }

    /** 构建好的映射器。两个方向互为逆运算（段内线性）。 */
    class Mapper internal constructor(private val segs: List<Segment>) {

        /** 纵向比例（0..1）→ 真实时刻。 */
        fun timeAtFraction(frac: Float): LocalTime {
            val f = frac.coerceIn(0f, 1f)
            val seg = segs.firstOrNull { f <= it.fracEnd + 1e-6f } ?: segs.last()
            val span = (seg.fracEnd - seg.fracStart).coerceAtLeast(1e-6f)
            val t = ((f - seg.fracStart) / span).coerceIn(0f, 1f)
            val minutes = seg.minuteStart + ((seg.minuteEnd - seg.minuteStart) * t).toInt()
            return LocalTime.of(minutes / 60, minutes % 60)
        }

        /** 真实时刻 → 纵向比例（0..1）。少于第一节之前 / 晚于最后一节之后会被夹住。 */
        fun fractionAtTime(time: LocalTime): Float {
            val m = time.hour * 60 + time.minute
            val seg = segs.firstOrNull { m <= it.minuteEnd } ?: segs.last()
            val span = (seg.minuteEnd - seg.minuteStart).coerceAtLeast(1)
            val t = ((m - seg.minuteStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            return seg.fracStart + (seg.fracEnd - seg.fracStart) * t
        }

        /**
         * 纵向比例 → 节次（1..10）。
         *
         * 落在缝里时归到**下一节**（"给接下来这节加日程"更符合直觉），
         * 已过最后一节则返回最后一节。
         */
        fun periodAtFraction(frac: Float): Int {
            val f = frac.coerceIn(0f, 1f)
            // 每一节在 segs 中的偶数下标（0,2,4,...）
            for (p in 1..PERIOD_COUNT) {
                val seg = segs[(p - 1) * 2]
                if (f <= seg.fracEnd + 1e-6f) return p
            }
            return PERIOD_COUNT
        }
    }
}
