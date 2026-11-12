package com.nongyu.appplus.timetable.model

import com.nongyu.appplus.timetable.logic.WEEKDAY_LABELS


/**
 * 课表**数据模型**（纯数据，不依赖 Android / Compose）。
 *
 * 口径来自票 #12（《定课程表数据模型与 mock 口径》）的三轮拍板，
 * 字段对齐旧农屿 `apps/nongyu-rn-app/src/modules/course/model/types.ts`。
 *
 * ## 为什么课程用「节次区间」而不是「每格一条」
 * 需求 14 要求大课（123456 连堂）连成一整块。区间模型让渲染层直接知道边界；
 * 若做成每格一条，就得反向推断「相邻格是否同一门课」，遇同名课或调课必错。
 *
 * ## 星期索引
 * 本仓统一 **0 = 周一 … 6 = 周日**（与 `WEEKDAY_LABELS` 的下标一致）。
 * 旧农屿是 1..7，转换只发生在解析教务数据的 mapping 层，模型内部不用旧口径。
 */

/** 教学周起止（含端点）。 */
data class WeekRange(val start: Int, val end: Int) {
    init {
        require(start in 1..TOTAL_WEEKS) { "周次起点必须在 1..$TOTAL_WEEKS，实际 $start" }
        require(end in start..TOTAL_WEEKS) { "周次终点必须在 $start..$TOTAL_WEEKS，实际 $end" }
    }

    /** 该区间是否覆盖第 [week] 周。 */
    fun covers(week: Int): Boolean = week in start..end
}

/**
 * 一门课在课表上的一个条目。
 *
 * @param day 周几，0 = 周一 … 6 = 周日。
 * @param startPeriod 起始节次（1 起）。
 * @param endPeriod 结束节次（含）。等于 [startPeriod] 表示单节。
 * @param weeks 教学周区间。
 * @param weeksList 若存在则**优先于** [weeks]（旧农屿语义，用于不规则周次）。
 * @param odd 单周有课。与 [even] 可同时为 true（表示全周）。
 * @param even 双周有课。
 */
data class CourseEntry(
    val id: String,
    val name: String,
    val teacher: String,
    val room: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: WeekRange,
    val weeksList: List<Int>? = null,
    val odd: Boolean = true,
    val even: Boolean = true,
) {
    init {
        require(day in 0..6) { "day 必须在 0..6，实际 $day" }
        require(startPeriod in 1..PERIOD_COUNT) { "startPeriod 必须在 1..$PERIOD_COUNT，实际 $startPeriod" }
        require(endPeriod in startPeriod..PERIOD_COUNT) {
            "endPeriod 必须在 $startPeriod..$PERIOD_COUNT，实际 $endPeriod"
        }
        require(name.isNotBlank()) { "课程名不能为空" }
        require(id.isNotBlank()) { "id 不能为空（详情卡与日程都靠它关联）" }
    }

    /** 该条目占的行数（单节 = 1，两节连堂 = 2…）。 */
    val spanRows: Int get() = endPeriod - startPeriod + 1

    /** 是否是一个「大课」（跨 2 节以上）。 */
    val isSpanning: Boolean get() = spanRows > 1

    /** 是否覆盖第 [period] 节。 */
    fun covers(period: Int): Boolean = period in startPeriod..endPeriod

    /**
     * 第 [week] 周是否要显示这门课。
     *
     * 判定顺序与旧农屿一致：
     * 1. [weeksList] 非空则以它为准（不规则周次）；
     * 2. 否则看 [weeks] 区间；
     * 3. 再看单双周：[odd] 与 [even] 都为 true 表示每周都有。
     */
    fun occursIn(week: Int): Boolean {
        val inWeeks = weeksList?.let { week in it } ?: weeks.covers(week)
        if (!inWeeks) return false
        if (odd && even) return true
        return when {
            week % 2 == 1 -> odd
            else -> even
        }
    }
}

/**
 * 自定义日程（需求 12/13 的「添加日程」）。
 *
 * 与 [CourseEntry] **完全分开、不复用**（旧农屿语义）：
 * 课程来自教务、日程是用户自建，两者的生命周期与字段都不该混。
 *
 * @param studentId 学生标识。本期是纯 mock、没有登录态，填 [MOCK_STUDENT_ID]；
 *   真接登录后由外部注入。
 * @param weeksList 出现在哪些周；空列表视为全周（旧农屿语义）。
 * @param colorIndex 色板下标；`null` 表示走默认配色。本期不启用，留给日程自选颜色。
 */
data class ScheduleEntry(
    val id: String,
    val studentId: String,
    val title: String,
    val content: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeksList: List<Int> = emptyList(),
    val colorIndex: Int? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    init {
        require(day in 0..6) { "day 必须在 0..6，实际 $day" }
        require(startPeriod in 1..PERIOD_COUNT)
        require(endPeriod in startPeriod..PERIOD_COUNT)
        require(title.isNotBlank()) { "日程标题不能为空" }
    }

    val spanRows: Int get() = endPeriod - startPeriod + 1

    /** 空 [weeksList] 视为全周。 */
    fun occursIn(week: Int): Boolean =
        if (weeksList.isEmpty()) true else week in weeksList
}

/** mock 期的学生标识占位（真接登录后由外部注入）。 */
const val MOCK_STUDENT_ID: String = "mock-student"

/**
 * 按周过滤课表：只留下第 [week] 周要显示的条目。
 *
 * 这是「切周」的核心逻辑，与渲染无关，所以放纯逻辑层并被单测覆盖。
 *
 * 注意函数名不能直接叫 `forWeek` 并同时给 [ScheduleEntry] 也定义一个同名扩展：
 * Kotlin 的扩展函数经 JVM 擦除后签名会变成 `forWeek(List, int)`，
 * 两个泛型擦除后完全相同 → 编译期报平台声明冲突。
 * 所以课程与日程各用一个明确的名字。
 */
fun List<CourseEntry>.coursesInWeek(week: Int): List<CourseEntry> =
    filter { it.occursIn(week) }

fun List<ScheduleEntry>.schedulesInWeek(week: Int): List<ScheduleEntry> =
    filter { it.occursIn(week) }

/**
 * 按天分组后的课表：`index = day`，值为该天的课程条目（已按起始节次排序）。
 *
 * 渲染层拿到这个结构后，每天一列、列内按块垂直排布，
 * 跨节块自然占据多行高度 —— 不需要在渲染时再做「哪些格属于同一块」的推断。
 */
fun List<CourseEntry>.groupedByDay(): List<List<CourseEntry>> {
    require(noOverlapOrThrow()) { "同一天的课程节次不能重叠（同格堆叠属 #18 之后再处理）" }
    return (0..6).map { day ->
        filter { it.day == day }.sortedBy { it.startPeriod }
    }
}

/** 检查没有「同一天、节次区间重叠」的两个条目（重叠会让两块画在同一位置）。 */
private fun List<CourseEntry>.noOverlapOrThrow(): Boolean {
    forEach { a ->
        forEach { b ->
            if (a !== b && a.day == b.day) {
                val overlap = a.startPeriod <= b.endPeriod && b.startPeriod <= a.endPeriod
                if (overlap) return false
            }
        }
    }
    return true
}

/**
 * 把「同天、同课名、同地点、节次相邻」的条目合并成一条。
 *
 * ## 口径（票 #12 第 6 条，维护者明确选择）
 * **只按节次相连合并，不限制跨午休**。
 * 也就是说第 4 节与第 5 节若是同一门课，会合并成一整块，
 * 卡片因而会跨过左侧时间列那道午饭大间距。
 * 这是维护者确认接受的视觉后果，不要「顺手优化」成不跨午休。
 *
 * 合并只发生在渲染前的数据准备阶段，不改原始数据。
 */
fun List<CourseEntry>.mergeAdjacent(): List<CourseEntry> {
    val result = mutableListOf<CourseEntry>()
    groupBy { it.day }.forEach { (_, sameDay) ->
        val sorted = sameDay.sortedBy { it.startPeriod }
        var current: CourseEntry? = null
        for (entry in sorted) {
            val c = current
            val canMerge = c != null &&
                c.name == entry.name &&
                c.room == entry.room &&
                c.weeks == entry.weeks &&
                c.weeksList == entry.weeksList &&
                c.odd == entry.odd &&
                c.even == entry.even &&
                entry.startPeriod == c.endPeriod + 1
            if (canMerge) {
                current = c!!.copy(endPeriod = entry.endPeriod)
            } else {
                c?.let { result += it }
                current = entry
            }
        }
        current?.let { result += it }
    }
    return result
}
