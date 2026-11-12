package com.nongyu.appplus.shell

import com.nongyu.appplus.timetable.model.CourseEntry

/**
 * 课表页点击的**卡片互斥规则**（纯逻辑，可纯 JVM 测）。
 *
 * 维护者 2026-11-11 拍板：「课程卡开时，点课程盒子之外的区域都退出」，
 * 并且**任何卡片开着时，第一下点击都只承担「退出」**，不叠加新动作。
 *
 * 这条规则原先在 `MainShell` 里以两段几乎逐字重复的 `if/else if` 链出现
 * （`onCourseClick` 与 `onBlankSlotAdd` 各写一遍），而且各自的注释都在解释
 * 同一件事。任何一边改漏都会让「点哪都退出」出现不一致。
 *
 * 抽成纯函数后：
 * - 规则只有一份，有单测；
 * - 两个入口都先问 [resolveCardIntent]，拿到意图后再各自执行副作用。
 */
enum class OpenCard { None, DetailCourse, ScheduleDetail, AddSchedule }

/**
 * 一次点击的意图。`null` 表示「这一下无事发生」。
 */
sealed interface ClickIntent {
    /** 退出当前打开的卡片（不打开新的）。 */
    data object Dismiss : ClickIntent

    /** 打开/切换到某门课的详情卡。 */
    data class OpenDetail(val course: CourseEntry) : ClickIntent

    /** 在空白格上打开添加日程表单。 */
    data class OpenAddSchedule(val day: Int, val period: Int, val week: Int) : ClickIntent

    /**
     * 退出并清空课程卡（**不**走收拢动画）。
     *
     * 与 [Dismiss] 的区别：点**课程盒子**时走收拢动画（[Dismiss]，由调用方置
     * isCollapsing），点**空白格**时直接清掉。这是维护者既有行为，两者手感不同。
     */
    data object DismissDetailImmediately : ClickIntent
}

/**
 * 点**课程盒子**时该干什么。
 *
 * 语义（原有行为，逐条保留）：
 * - 添加表单开着 → 关表单（不切课）
 * - 日程明细开着 → 关明细（不切课）
 * - 课程卡开着 → **直接切到那门课**（维护者要求：已开着课程卡时点另一个课程盒子
 *   就是切卡，不要求先退出再点第二下）
 * - 都没开 → 打开详情
 */
fun resolveCourseClick(openCard: OpenCard, clicked: CourseEntry): ClickIntent = when (openCard) {
    OpenCard.AddSchedule, OpenCard.ScheduleDetail -> ClickIntent.Dismiss
    OpenCard.DetailCourse, OpenCard.None -> ClickIntent.OpenDetail(clicked)
}

/**
 * 点**空白格**时该干什么。
 *
 * 语义（原有行为，逐条保留），维护者 2026-11-11：
 * 「在课程时点击后面的除课程盒子外其他区域都是退出课程卡片」。
 * - 添加表单开着 → 关表单
 * - 日程明细开着 → 关明细
 * - 课程卡开着 → **直接清空**（不走收拢动画）
 * - 都没开 → 打开添加日程
 */
fun resolveBlankSlotClick(
    openCard: OpenCard,
    day: Int,
    period: Int,
    week: Int,
): ClickIntent = when (openCard) {
    OpenCard.AddSchedule, OpenCard.ScheduleDetail -> ClickIntent.Dismiss
    OpenCard.DetailCourse -> ClickIntent.DismissDetailImmediately
    OpenCard.None -> ClickIntent.OpenAddSchedule(day, period, week)
}

/** 当前开着哪张卡（优先级与 [deriveBarMode] 保持一致）。 */
fun currentOpenCard(
    addingSchedule: Any?,
    scheduleDetailId: String?,
    hasDetailCourse: Boolean,
): OpenCard = when {
    addingSchedule != null -> OpenCard.AddSchedule
    scheduleDetailId != null -> OpenCard.ScheduleDetail
    hasDetailCourse -> OpenCard.DetailCourse
    else -> OpenCard.None
}
