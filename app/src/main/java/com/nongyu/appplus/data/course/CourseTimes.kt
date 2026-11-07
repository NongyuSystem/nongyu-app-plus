package com.nongyu.appplus.data.course

/**
 * 课次时间与周次算法 —— 对齐旧项目
 * `nongyu-rn-app/src/modules/course/model/courseTimes.ts` 与 `semesterWeek.ts`。
 *
 * 注意：旧版 UI 是**每节一行，共 10 行**（不是 5 个大节合并），
 * 所以这里的 slot 直接等于节次本身。
 */

/** 川农常见作息：10 小节对应起止时刻（与旧版完全一致） */
val COURSE_TIMES: List<Pair<String, String>> = listOf(
    "08:10" to "08:55",
    "09:05" to "09:50",
    "10:10" to "10:55",
    "11:05" to "11:50",
    "14:20" to "15:05",
    "15:15" to "16:00",
    "16:20" to "17:05",
    "17:15" to "18:00",
    "19:30" to "20:15",
    "20:25" to "21:10",
)

val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 每天的行数：10 节，每节一行 */
const val PERIOD_ROW_COUNT = 10

/** 节次（1..10）→ 行下标（0..9），一一对应 */
fun rowFromPeriod(period: Int): Int = (period - 1).coerceIn(0, PERIOD_ROW_COUNT - 1)

/** 起止节次跨几行 */
fun spanRowsFromPeriods(startPeriod: Int, endPeriod: Int): Int {
    val s = rowFromPeriod(startPeriod)
    val e = rowFromPeriod(endPeriod)
    return maxOf(1, e - s + 1)
}

/** 第 period 节的开始时刻 */
fun periodStart(period: Int): String =
    COURSE_TIMES.getOrNull(period - 1)?.first ?: ""

/** 第 period 节的结束时刻 */
fun periodEnd(period: Int): String =
    COURSE_TIMES.getOrNull(period - 1)?.second ?: ""

/** 节次范围文案，如「3-4节」 */
fun periodRangeLabel(startPeriod: Int, endPeriod: Int): String =
    if (startPeriod == endPeriod) "${startPeriod}节" else "${startPeriod}-${endPeriod}节"

/** 时间范围文案，如「10:10-11:50」 */
fun timeRangeLabel(startPeriod: Int, endPeriod: Int): String =
    "${periodStart(startPeriod)}-${periodEnd(endPeriod)}"

/**
 * 该行是否应该画一条更明显的分隔（每 2 节之间留空隙：
 * 1-2 / 3-4 / 5-6 / 7-8 / 9-10 是 5 个大节）。
 */
fun isMajorSlotBoundary(row: Int): Boolean = row % 2 == 0 && row > 0
