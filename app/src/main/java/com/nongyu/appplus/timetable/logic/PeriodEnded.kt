package com.nongyu.appplus.timetable.logic

import java.time.LocalTime

/**
 * 第 [period] 节的**结束时刻**（当天分钟数）。节次越界或作息表格式异常时返回 `null`。
 *
 * 取 [COURSE_TIMES] 里该节的 `end`，例如第 1 节 `08:55` → 535。
 */
fun periodEndMinuteOf(period: Int): Int? {
    val end = COURSE_TIMES.getOrNull(period - 1)?.end ?: return null
    val parts = end.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/**
 * 第 [period] 节在 [now] 是否**已经结束**（用于考勤默认态）。
 *
 * 原先写在 `MainShell` 里，是 UI 层唯一一段纯判定逻辑，因此没有单测。
 * 下沉到这里后可在纯 JVM 覆盖：边界（恰好等于结束时刻算已结束）、越界节次、
 * 以及写错格式的作息项都返回 `false`（宁可「未结束」，不误判成已上过课）。
 *
 * @param now 当前时刻，默认取系统时间；单测必须显式传入以保证确定性。
 */
fun isPeriodEnded(period: Int, now: LocalTime = LocalTime.now()): Boolean {
    val endMin = periodEndMinuteOf(period) ?: return false
    return now.hour * 60 + now.minute >= endMin
}
