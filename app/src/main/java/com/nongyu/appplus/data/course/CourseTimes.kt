package com.nongyu.appplus.data.course

/**
 * 课次时间与周次算法 —— 移植自旧项目
 * `nongyu-rn-app/src/modules/course/model/courseTimes.ts` 与 `semesterWeek.ts`。
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

/** 大课区间行数（每天 5 格） */
const val MAJOR_SLOT_COUNT = 5

/** 节次 → 大课区间行（0..4） */
fun slotIndexFromPeriod(period: Int): Int = when {
    period <= 2 -> 0
    period <= 4 -> 1
    period <= 6 -> 2
    period <= 8 -> 3
    else -> 4
}

/** 起止节次跨几个大课区间 */
fun spanRowsFromPeriods(startPeriod: Int, endPeriod: Int): Int {
    val startRow = slotIndexFromPeriod(startPeriod)
    val endRow = slotIndexFromPeriod(endPeriod)
    return maxOf(1, endRow - startRow + 1)
}

/** 大课区间对应的节次范围文案，如「1-2节」 */
fun periodRangeLabel(row: Int): String {
    val start = row * 2 + 1
    val end = row * 2 + 2
    return "$start-${end}节"
}

/** 大课区间对应的起止时刻文案 */
fun periodTimeLabel(row: Int): String {
    val startIdx = row * 2
    val endIdx = minOf(startIdx + 1, COURSE_TIMES.lastIndex)
    val start = COURSE_TIMES[startIdx].first
    val end = COURSE_TIMES[endIdx].second
    return "$start-$end"
}
