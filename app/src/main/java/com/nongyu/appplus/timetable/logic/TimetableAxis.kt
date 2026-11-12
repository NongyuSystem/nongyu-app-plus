package com.nongyu.appplus.timetable.logic

import java.time.LocalTime
import com.nongyu.appplus.timetable.model.PERIOD_COUNT
import com.nongyu.appplus.timetable.ui.TimetableRuler
import com.nongyu.appplus.timetable.ui.WaterLine

/**
 * 课表时间轴的纯逻辑映射（票 #60 需求 2 / 3 / 5）。
 *
 * 为什么单独抽成一个文件：
 * 「真实时钟 → 时间条位置」和「手指 y → 时刻」这两条映射同时被
 * 刻度条（`TimetableRuler`）和流水（`WaterLine`）使用，而且都要有单测。
 * 把它们放在 UI 文件里会导致纯 JVM 单测跑不起来（UI 文件带 Compose 依赖）。
 *
 * ## 时间轴不是等分的
 *
 * 10 节课之间有午休与大课间（12:00~14:20、18:00~19:30），
 * 所以「第 N 节在表里的位置」不能直接用 `period / PERIOD_COUNT`，
 * 必须按**真实时刻的连续时间轴**来算，否则流水会在午休时突然跳一段。
 *
 * 这里把整张表定义成一个 [axisStart, axisEnd] 的真实时间区间：
 * 从第 1 节开始（08:10）到第 10 节结束（21:10），
 * 课与课的间隙也占真实时间，于是：
 * - 流水按真实时钟线性落在轴上；
 * - 刻度条按轴上的时间等距画刻度；
 * - 两者天然对齐（需求 5）。
 */
object TimetableAxis {

    /** 轴起点：第 1 节开始。 */
    val axisStart: LocalTime = parse(COURSE_TIMES.first().start)

    /** 轴终点：第 10 节结束。 */
    val axisEnd: LocalTime = parse(COURSE_TIMES.last().end)

    /** 轴的分钟总数（约 780 分钟）。 */
    val totalMinutes: Int = minutesBetween(axisStart, axisEnd)

    /**
     * 把一天中的时刻换算成轴上的比例 0..1。
     *
     * 早于开始 / 晚于结束会被夹到 0 / 1 —— 调用方（流水）不需要再判边界。
     */
    fun fractionOf(time: LocalTime): Float {
        val m = minutesBetween(axisStart, time)
        if (totalMinutes <= 0) return 0f
        return (m.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 轴上比例 0..1 反算成时刻（手指位置 → 时间文字）。
     */
    fun timeAtFraction(fraction: Float): LocalTime {
        val f = fraction.coerceIn(0f, 1f)
        val mins = (totalMinutes * f).toInt()
        return axisStart.plusMinutes(mins.toLong())
    }

    /**
     * 第 [period]（1..10）占的轴区间，返回 (起, 止) 的比例。
     *
     * 用于把一节课画在时间条上：起止来自 [COURSE_TIMES]，不假设等分。
     */
    fun periodFraction(period: Int): ClosedFloatingPointRange<Float> {
        val p = COURSE_TIMES.getOrNull(period - 1) ?: return 0f..0f
        val s = fractionOf(parse(p.start))
        val e = fractionOf(parse(p.end))
        return s..e
    }

    /**
     * 轴比例 → 最接近的节次（1..10）。
     *
     * 手指落在课间空档时，返回**下一节**（更符合"我要给接下来这节加日程"的直觉），
     * 若已过最后一节则返回最后一节。
     */
    fun periodAtFraction(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        for (p in 1..PERIOD_COUNT) {
            val r = periodFraction(p)
            if (f <= r.endInclusive + 1e-6f) return p
        }
        return PERIOD_COUNT
    }

    /** "HH:mm" → LocalTime。格式非法时回落到 [axisStart]（mock 数据保证合法）。 */
    private fun parse(s: String): LocalTime =
        runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(8, 10))

    private fun minutesBetween(from: LocalTime, to: LocalTime): Int {
        val a = from.hour * 60 + from.minute
        val b = to.hour * 60 + to.minute
        return (b - a).coerceAtLeast(0)
    }
}

/** "HH:mm" 格式化（时间气泡用）。 */
fun formatHhMm(time: LocalTime): String =
    "%02d:%02d".format(time.hour, time.minute)
