package com.nongyu.appplus.timetable.logic

import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.model.TOTAL_WEEKS
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 一门课的**学期课次统计**（纯逻辑，无 Android / Compose 依赖，可纯 JVM 单测）。
 *
 * ## 需求（维护者 2026-11-23）
 *
 * 课程详情里的考勤应当回答三个问题：
 *
 * ```
 * 这门课一学期总共要上多少次？
 * 到现在已经上了多少次？
 * 还剩几次（能请假几次）？
 * ```
 *
 * ## 原实现为什么是错的
 *
 * 详情卡用 `course.startPeriod..course.endPeriod` 生成格子 —— 那是**单次上课的节次**。
 * 一门「3-1、3-2」的课永远只生成 2 个格子，与「一学期 8 次 / 12 次」无关。
 * 节次是「一次课里有几小节」，课次是「一学期上几个这样的日子」，两者是不同的量。
 *
 * ## 课次的定义
 *
 * ```
 * 总课次   = 周次范围内，实际排课的天数 × 单次节数
 * 已上课次 = 上述天数中，已经过去（或今天已结束）的天数 × 单次节数
 * ```
 *
 * 「实际排课的天数」必须逐周判断，因为单双周会让某些周跳过：
 * 例如 1-16 周的单周课实际只上 8 次，而不是 16 次。
 */
object AttendanceStats {

    /**
     * 学期课次统计结果。
     *
     * @property totalSessions 一学期总课次
     * @property pastSessions 截至今天已发生的课次（含今天已结束的）
     * @property remainingSessions 尚未发生的课次（= total - past）
     * @property periodsPerSession 单次课包含几小节（如 3-1、3-2 → 2）
     */
    data class Stats(
        val totalSessions: Int,
        val pastSessions: Int,
        val remainingSessions: Int,
        val periodsPerSession: Int,
    ) {
        /** 是否已经有统计意义（总课次为 0 时不显示统计）。 */
        val hasData: Boolean get() = totalSessions > 0
    }

    /**
     * 计算统计。
     *
     * @param course 课程条目（决定周次、单双周、节次跨度）
     * @param termStart 学期首日（星期一）
     * @param today 今天
     */
    fun compute(
        course: CourseEntry,
        termStart: LocalDate,
        today: LocalDate,
    ): Stats {
        val periodsPerSession = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
        val dates = sessionDates(course, termStart)
        val pastDays = dates.count { it.isBefore(today) || it.isEqual(today) }
        val total = dates.size * periodsPerSession
        val past = pastDays * periodsPerSession
        return Stats(
            totalSessions = total,
            pastSessions = past,
            remainingSessions = (total - past).coerceAtLeast(0),
            periodsPerSession = periodsPerSession,
        )
    }

    /**
     * 这门课一学期**实际要上的所有日期**（按时间升序）。
     *
     * 逐周遍历周次范围，只保留 `occursIn(week)` 为真的周 ——
     * 单双周课因此只出现在该出现的周里。
     *
     * UI 用它画出每一次课的格子，用户按日期勾选。
     */
    fun sessionDates(course: CourseEntry, termStart: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        for (week in course.weeks.start..course.weeks.end) {
            if (week !in 1..TOTAL_WEEKS) continue
            if (!course.occursIn(week)) continue
            result.add(dateOfWeekDay(termStart, week, course.day))
        }
        return result
    }

    /**
     * 学期第 [week] 周、周几（0=周一）对应的日期。
     *
     * @param termStart 学期首日，**必须是星期一**
     */
    fun dateOfWeekDay(termStart: LocalDate, week: Int, dayIndex: Int): LocalDate =
        termStart.plusDays((week - 1) * 7L + dayIndex.toLong())

    /** 两个日期相差几周（供 UI 显示"第 N 周"等用）。 */
    fun weeksBetween(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).toInt() / 7
}
