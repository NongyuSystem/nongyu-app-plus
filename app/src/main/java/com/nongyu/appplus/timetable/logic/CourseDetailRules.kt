package com.nongyu.appplus.timetable.logic

import com.nongyu.appplus.timetable.model.CourseEntry

/**
 * 课程详情卡的**编辑态合并规则**（纯逻辑，零 Android / 零 Compose）。
 *
 * 这些规则原先以 `remember(courseId)` 的形式内联在 `MainShell` 里，
 * 是界面上最容易写错、又最难测的一段：
 *
 * 1. **备注防抖要落盘，但输入框不能被回读打架** —— 曾经直接 `collectAsState`
 *    喂给 TextField，写入与回读互相覆盖，真机重启后丢值。
 * 2. **考勤默认态 = 用户显式记录优先，否则按「该节是否已结束」** ——
 *    合并顺序写反会让用户点过的考勤被时间判定冲掉。
 * 3. **地点显示值三级回落**：今天按天覆盖 → 通用覆盖 → 课程原始 room。
 *
 * 本文件只做判定，不碰 DataStore；读写由 `timetable/data` 负责。
 */

/** 备注落盘前的防抖时长（毫秒）。 */
const val NOTE_DEBOUNCE_MS: Long = 250L

/**
 * 地点显示值：三级回落。
 *
 * @param locationForDay `locday_<courseId>_<今天>` 的值，null = 没按天设过
 * @param locationGeneral `loc_once_<courseId>` 的值，null = 没设过通用覆盖
 * @param roomOfCourse 课程原始地点
 */
fun resolveLocationLabel(
    locationForDay: String?,
    locationGeneral: String?,
    roomOfCourse: String,
): String = locationForDay ?: locationGeneral ?: roomOfCourse

/** 教师显示值：用户行内覆盖优先，否则课程的原始教师。 */
fun resolveTeacherLabel(teacherOverride: String?, teacherOfCourse: String): String =
    teacherOverride ?: teacherOfCourse

/**
 * 把「用户显式点过的考勤」与「按当前时间推算的默认值」合并成最终展示态。
 *
 * 优先级：`overrides` 里有的以 overrides 为准；没有的用 [defaultFor] 推算。
 * **不能反过来** —— 反了会让用户手动点的记录被时间判定覆盖。
 *
 * @param course 当前详情卡对应的课程（决定合法节次区间）；null 时返回空 Map
 * @param overrides 用户显式点过的节次 → 是否已考勤
 * @param defaultFor 无显式记录时的默认判定（生产环境传 `isPeriodEnded`）
 */
fun mergeAttendance(
    course: CourseEntry?,
    overrides: Map<Int, Boolean>,
    defaultFor: (Int) -> Boolean,
): Map<Int, Boolean> {
    if (course == null) return emptyMap()
    return (course.startPeriod..course.endPeriod).associateWith { p ->
        overrides[p] ?: defaultFor(p)
    }
}

/**
 * 点击某节课的考勤后，下一份 overrides 应该是什么。
 *
 * 语义是「翻转当前显示值」而不是「翻转存储值」：用户看到的状态就是他想要的反面。
 * 因此要把**当前生效值**写进 overrides，避免覆盖后仍与时间默认值纠缠。
 *
 * @param currentEffective [mergeAttendance] 的结果
 */
fun toggledAttendance(
    currentEffective: Map<Int, Boolean>,
    period: Int,
): Map<Int, Boolean> {
    val current = currentEffective[period] ?: return currentEffective
    val next = currentEffective.toMutableMap()
    next[period] = !current
    return next
}

/**
 * 判断当前备注是否**应当落盘**。
 *
 * 两个条件缺一不可：
 * - `ready`：初始化读取已完成。否则首次打开卡片时会把空串当成用户输入覆盖掉已存备注；
 * - `courseId` 非空：没有选中课程时不该写。
 */
fun shouldPersistNote(ready: Boolean, courseId: String?): Boolean =
    ready && courseId != null

/**
 * 备注文本是否与已落盘的值不同（避免无意义写入）。
 *
 * 与 [shouldPersistNote] 配合：相同时直接跳过，省一次 DataStore 写入与一次重组。
 */
fun noteChanged(persisted: String, current: String): Boolean = persisted != current
