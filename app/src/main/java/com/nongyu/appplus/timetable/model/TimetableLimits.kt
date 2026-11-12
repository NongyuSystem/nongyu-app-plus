package com.nongyu.appplus.timetable.model

/**
 * 课表的领域上限常量（model 层，零依赖）。
 *
 * 这两个值原先放在 `logic/TimetableData.kt`，但 [CourseEntry]/[ScheduleEntry] 的
 * `require(...)` 校验一上来就要用它们 —— 等于 model 反向依赖 logic。
 * 下沉到这里后，依赖方向固定为 `model ← logic ← data ← ui`，model 无任何本域依赖。
 *
 * 改这两个值等于改课表的时间语义，**必须**同步确认旧农屿 RN 版是否也变。
 */

/** 一天有几节课。 */
const val PERIOD_COUNT: Int = 10

/** 学期总周数（左上角「第几周 / 共几周」的分母）。 */
const val TOTAL_WEEKS: Int = 16
