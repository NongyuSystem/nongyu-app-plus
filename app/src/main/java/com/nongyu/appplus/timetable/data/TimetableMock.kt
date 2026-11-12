package com.nongyu.appplus.timetable.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.nongyu.appplus.timetable.model.MOCK_STUDENT_ID
import com.nongyu.appplus.timetable.model.TOTAL_WEEKS
import com.nongyu.appplus.timetable.model.WeekRange
import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.model.ScheduleEntry

/**
 * 课表的 **mock 数据与学期锚点**（票 #12 第 3、7 条拍板）。
 *
 * ## 学期锚点
 * 学期首日取一个固定的周一常量 [MOCK_TERM_START]，并做成**可注入**：
 * 真实学期起始日将来从教务或设置读，而不是散落在 UI 里。
 *
 * 之所以要真锚点：需求 8 要求「切到非本周/本日后，能点一个按钮回到今天」。
 * 若 `currentWeek` 永远写死为 1，这个功能根本无法验证。
 */

/**
 * mock 学期首日：2026-09-07（周一）。
 *
 * 选一个明确的周一，避免「首日不是周一」时算周次还要先对齐到周一。
 */
val MOCK_TERM_START: LocalDate = LocalDate.of(2026, 9, 7)

/**
 * 计算 [today] 属于学期第几周（1 起）。
 *
 * 学期开始前返回 1（钳制，不返回 0 或负数）；超过总周数时钳到 [TOTAL_WEEKS]。
 * 这样 `currentWeek` 永远落在合法范围内，切周逻辑不用到处判空。
 */
fun weekOfTerm(today: LocalDate, termStart: LocalDate = MOCK_TERM_START): Int {
    val days = ChronoUnit.DAYS.between(termStart, today)
    // 负数（开学前）向下取整会得到 -1，所以要 floorDiv 而不是整数除
    val week = Math.floorDiv(days, 7L).toInt() + 1
    return week.coerceIn(1, TOTAL_WEEKS)
}

/**
 * 本周周一。用于表头显示日期，以及「回到今天」时定位到哪一周。
 */
fun mondayOfWeek(today: LocalDate): LocalDate =
    today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

/**
 * mock 课表数据。
 *
 * 覆盖票 #12 第 7 条要求的四种形态，好让「切周」的差异真的看得出来：
 * - **全周课**：1-16 周、单双周都上；
 * - **单周课**：只上奇数周；
 * - **双周课**：只上偶数周；
 * - **短课**：只上前 8 周（期末前结束）。
 *
 * 另外保留：
 * - **四节大课**（周三 1-4 节「设计基础」）：验证需求 14 跨节连成一块；
 * - 周日留空：验证空白区观感。
 */
val MOCK_COURSES: List<CourseEntry> = listOf(
    // ---------- 周一 ----------
    CourseEntry(
        id = "c-mon-1", name = "高等数学", teacher = "刘勇", room = "1-101",
        day = 0, startPeriod = 1, endPeriod = 2, weeks = WeekRange(1, 16),
    ),
    CourseEntry(
        id = "c-mon-2", name = "大学英语", teacher = "王芳", room = "2-305",
        day = 0, startPeriod = 3, endPeriod = 4, weeks = WeekRange(1, 16),
    ),
    // 单周课：只有奇数周有
    CourseEntry(
        id = "c-mon-3", name = "体育（单周）", teacher = "陈刚", room = "东操场",
        day = 0, startPeriod = 7, endPeriod = 7, weeks = WeekRange(1, 16),
        odd = true, even = false,
    ),

    // ---------- 周二 ----------
    CourseEntry(
        id = "c-tue-1", name = "线性代数", teacher = "张伟", room = "1-204",
        day = 1, startPeriod = 1, endPeriod = 2, weeks = WeekRange(1, 16),
    ),
    CourseEntry(
        id = "c-tue-2", name = "程序设计基础", teacher = "李强", room = "机房 A",
        day = 1, startPeriod = 5, endPeriod = 6, weeks = WeekRange(1, 16),
    ),
    // 双周课
    CourseEntry(
        id = "c-tue-3", name = "形势与政策（双周）", teacher = "赵敏", room = "3-201",
        day = 1, startPeriod = 9, endPeriod = 10, weeks = WeekRange(1, 16),
        odd = false, even = true,
    ),

    // ---------- 周三：四节大课，验证跨节连成一块 ----------
    CourseEntry(
        id = "c-wed-1", name = "设计基础", teacher = "孙丽", room = "艺术楼 3F",
        day = 2, startPeriod = 1, endPeriod = 4, weeks = WeekRange(1, 16),
    ),
    // 短课：只上前 8 周
    CourseEntry(
        id = "c-wed-2", name = "数据结构", teacher = "周杰", room = "1-308",
        day = 2, startPeriod = 5, endPeriod = 6, weeks = WeekRange(1, 8),
    ),

    // ---------- 周四 ----------
    CourseEntry(
        id = "c-thu-1", name = "计算机网络", teacher = "吴涛", room = "2-410",
        day = 3, startPeriod = 3, endPeriod = 4, weeks = WeekRange(1, 16),
    ),
    CourseEntry(
        id = "c-thu-2", name = "心理健康", teacher = "郑爽", room = "3-102",
        day = 3, startPeriod = 6, endPeriod = 7, weeks = WeekRange(1, 16),
    ),

    // ---------- 周五 ----------
    CourseEntry(
        id = "c-fri-1", name = "操作系统", teacher = "冯雷", room = "1-502",
        day = 4, startPeriod = 1, endPeriod = 2, weeks = WeekRange(1, 16),
    ),
    // 用 weeksList 表达不规则周次（优先于 weeks 区间）
    CourseEntry(
        id = "c-fri-2", name = "概率论", teacher = "许静", room = "2-201",
        day = 4, startPeriod = 4, endPeriod = 5, weeks = WeekRange(1, 16),
        weeksList = listOf(1, 2, 3, 5, 8, 13),
    ),

    // ---------- 周六 ----------
    CourseEntry(
        id = "c-sat-1", name = "创新创业", teacher = "何明", room = "创客空间",
        day = 5, startPeriod = 2, endPeriod = 3, weeks = WeekRange(1, 16),
    ),

    // ---------- 周日：留空，验证空白区观感 ----------
)

/**
 * mock 日程（验证需求 12/13 的形态，本期不渲染到网格，仅供 #18 使用）。
 */
val MOCK_SCHEDULES: List<ScheduleEntry> = listOf(
    ScheduleEntry(
        id = "s-1",
        studentId = MOCK_STUDENT_ID,
        title = "社团例会",
        content = "每周三晚例会",
        location = "学生活动中心 201",
        day = 2,
        startPeriod = 9,
        endPeriod = 10,
        weeksList = emptyList(),   // 空 = 全周
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
    ),
)
