package com.nongyu.appplus.timetable.logic

import com.nongyu.appplus.timetable.ui.TimetableGrid

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

/**
 * 周次的中文写法：「第一周」「第十二周」「第二十周」。
 *
 * 表头左上角按维护者要求显示中文而不是 `1/16`，
 * 因为那个格子只有约 40dp 宽，`1/16` 这种写法要靠读者自己解析，
 * 而「第一周」一眼就知道是什么。
 *
 * 只覆盖 1..99：学期周次不会超出这个范围，
 * 超出时回退成阿拉伯数字，避免悄悄给出错误的汉字。
 */
fun weekLabelCn(week: Int): String {
    if (week !in 1..99) return "第${week}周"
    return "第${numberToCn(week)}周"
}

/**
 * 「共 N 周」的写法。
 *
 * 刻意**用阿拉伯数字**而不是中文：维护者原话就是「共16周」，
 * 且这一行字号很小（6.5sp）、格子只有约 40dp 宽，
 * 「共十六周」四个汉字会挤到换行或缩得很小，可读性反而差。
 * 第一行「第一周」用中文是因为那是主信息，字数少、有节奏感。
 */
fun totalWeeksLabelCn(total: Int): String = "共${total}周"

/**
 * 1..99 → 中文数字（「一」「十」「十二」「二十」「九十九」）。
 *
 * 规则与日常读数一致：
 * - 1..9：直接对应的汉字；
 * - 10：十（不说「一十」）；
 * - 11..19：十X；
 * - 20..99：X十 / X十Y。
 */
private fun numberToCn(n: Int): String {
    val digits = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    return when {
        n < 10 -> digits[n]
        n == 10 -> "十"
        n < 20 -> "十" + digits[n % 10]
        else -> {
            val tens = digits[n / 10] + "十"
            val ones = n % 10
            if (ones == 0) tens else tens + digits[ones]
        }
    }
}
