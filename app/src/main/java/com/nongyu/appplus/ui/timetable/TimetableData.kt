package com.nongyu.appplus.ui.timetable

/**
 * 课表的**纯数据真源**（不依赖 Android / Compose，可在 JVM 单测里覆盖）。
 *
 * ## 为什么要单独抽出来
 * 原来「10 节次 / 16 周 / 作息时刻 / 星期名」全部硬编码散在 `TimetableGrid.kt` 里。
 * 这样有两个问题：
 * 1. **不同步**：第 2 步（课程卡片）要用同一套作息和节次，若各自再写一份，
 *    改一处就要记得改另一处，迟早对不上；
 * 2. **测不了**：UI 文件里的常量无法单测，算错也没人发现。
 *
 * 现在它们是唯一真源，UI 只负责画，数据口径全在这里。
 *
 * ## 口径来自哪里
 * 作息照抄旧农屿 `apps/nongyu-rn-app/src/modules/course/model/courseTimes.ts`
 * （川农常见作息）。改这里等于改课表的时间语义，**必须**同步确认旧版是否也变。
 */

/** 一天有几节课。 */
const val PERIOD_COUNT: Int = 10

/** 学期总周数（左上角「第几周 / 共几周」的分母）。 */
const val TOTAL_WEEKS: Int = 16

/**
 * 单节课的作息。
 *
 * @property start 起始时刻，形如 `08:10`
 * @property end 结束时刻，形如 `08:55`
 */
data class PeriodTime(val start: String, val end: String)

/**
 * 10 小节的起止时刻（下标 0 = 第 1 节）。
 *
 * 照抄旧农屿 `courseTimes.ts`。**顺序即节次**，不要重排。
 */
val COURSE_TIMES: List<PeriodTime> = listOf(
    PeriodTime("08:10", "08:55"),
    PeriodTime("09:05", "09:50"),
    PeriodTime("10:10", "10:55"),
    PeriodTime("11:05", "11:50"),
    PeriodTime("14:20", "15:05"),
    PeriodTime("15:15", "16:00"),
    PeriodTime("16:20", "17:05"),
    PeriodTime("17:15", "18:00"),
    PeriodTime("19:30", "20:15"),
    PeriodTime("20:25", "21:10"),
)

/** 周一..周日（下标 0 = 周一）。 */
val WEEKDAY_LABELS: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 第 [period] 节**之后**的空档分类（决定行间距档位）。
 *
 * 依据真实空档分钟数：
 * | 空档 | 分钟 |
 * | --- | --- |
 * | 1→2、3→4、5→6、7→8、9→10 | 10 |
 * | 2→3、6→7 | 20 |
 * | **4→5（午休）、8→9（晚饭）** | 150 / 90 |
 *
 * ⚠️ 传的是**前一节**的编号：`gapAfter(1)` 表示 1↔2 之间。
 * 历史 bug：曾写成 `period % 2 == 1 -> 大间距`，方向反了，
 * 导致 1↔2 最宽、2↔3 最窄，整列节奏失序。
 */
enum class RowGapKind {
    /** 10 分钟常规课间。 */
    Normal,

    /** 20 分钟大课间（2→3、6→7）。 */
    Long,

    /** 午休 / 晚饭（4→5、8→9）。 */
    Meal,
}

/** 第 [period] 节之后属于哪一档间距。 */
fun gapKindAfter(period: Int): RowGapKind = when (period) {
    4, 8 -> RowGapKind.Meal
    2, 6 -> RowGapKind.Long
    else -> RowGapKind.Normal
}

/**
 * 把 [LocalDate] 风格的工作日索引转成「今天是本周第几天」。
 *
 * @param mondayOffset 距离本周一的**天数**（周一 = 0，周日 = 6）。
 *   跨周或异常输入返回 `null`，调用方据此不高亮任何列。
 */
fun weekdayIndexOrNull(mondayOffset: Long): Int? =
    mondayOffset.takeIf { it in 0..6 }?.toInt()
