package com.nongyu.appplus.data.course

/**
 * 周课表矩阵构建 —— 移植自旧项目
 * `nongyu-rn-app/src/modules/course/model/weekMatrix.ts`。
 *
 * 核心规则（与旧版一致，逐条对应）：
 * 1. 每天 5 个大课区间（MAJOR_SLOT_COUNT）× 7 天。
 * 2. 连堂课写入主格 `GridStack`，并把下方被占用的行标记为 `GridOccupied`。
 * 3. 课程优先置顶；自定义日程可与课程冲突并入同一 stack（课程在前）。
 * 4. 日程落在"连堂占位行"时跳过，避免破坏跨行结构。
 * 5. 同一主格已有课程时，后写入的课程被忽略（保留先写入，即 T1）。
 */

/** 某课在指定教学周是否有效 */
fun weekMatches(week: Int, course: CourseEntry): Boolean {
    val list = course.weeksList
    if (!list.isNullOrEmpty()) return list.contains(week)
    if (week < course.weeks.start || week > course.weeks.end) return false
    if (course.odd && week % 2 == 0) return false
    if (course.even && week % 2 == 1) return false
    return true
}

/** 某日程在指定教学周是否有效（weeksList 为空视为全周） */
fun scheduleMatches(week: Int, schedule: ScheduleEntry): Boolean {
    if (schedule.weeksList.isEmpty()) return true
    return schedule.weeksList.contains(week)
}

/** 由课程列表推最大周（至少 1） */
fun maxWeekFromCourses(courses: List<CourseEntry>): Int {
    var maxW = 1
    for (c in courses) {
        val list = c.weeksList
        if (!list.isNullOrEmpty()) {
            maxW = maxOf(maxW, list.max())
        } else {
            maxW = maxOf(maxW, c.weeks.end)
        }
    }
    return maxOf(1, maxW)
}

/** 由课程 + 日程推最大周（至少 1） */
fun maxWeekFromAll(courses: List<CourseEntry>, schedules: List<ScheduleEntry>): Int {
    var maxW = maxWeekFromCourses(courses)
    for (s in schedules) {
        if (s.weeksList.isNotEmpty()) maxW = maxOf(maxW, s.weeksList.max())
    }
    return maxOf(1, maxW)
}

private fun emptyGrid(): WeekGridData =
    List(MAJOR_SLOT_COUNT) { List<GridCell?>(7) { null } }

/** 课程在前、日程按 createdAt 升序 */
private fun sortStackItems(items: List<StackItem>): List<StackItem> {
    val courses = items.filterIsInstance<StackItem.Course>()
    val schedules = items.filterIsInstance<StackItem.Schedule>()
        .sortedBy { it.schedule.createdAt }
    return courses + schedules
}

/** 把连堂占用的下方行标记为 Occupied（不覆盖已有内容） */
private fun writeOccupied(
    grid: MutableList<MutableList<GridCell?>>,
    startRow: Int,
    col: Int,
    spanRows: Int,
) {
    for (s in 1 until spanRows) {
        val r = startRow + s
        if (r >= MAJOR_SLOT_COUNT) break
        if (grid[r][col] != null) break
        grid[r][col] = GridCell.Occupied(GridOccupied(startRow))
    }
}

/** 构建单周 5×7 矩阵（仅课程） */
fun buildWeekGrid(week: Int, courses: List<CourseEntry>): WeekGridData {
    val grid = emptyGrid().map { it.toMutableList() }.toMutableList()

    for (course in courses) {
        if (!weekMatches(week, course)) continue

        val col = (course.day.coerceIn(1, 7)) - 1
        val startRow = slotIndexFromPeriod(course.startPeriod)
        val spanRows = spanRowsFromPeriods(course.startPeriod, course.endPeriod)

        val existing = grid[startRow][col]
        // 同格多课：保留先写入的
        if (existing is GridCell.Stack || existing is GridCell.Occupied) continue

        grid[startRow][col] = GridCell.Stack(
            GridStack(items = listOf(StackItem.Course(course)), spanRows = spanRows),
        )
        writeOccupied(grid, startRow, col, spanRows)
    }

    return grid
}

/** 构建单周矩阵：课程优先置顶；日程可与课程冲突并入同一 stack */
fun buildWeekGridWithSchedules(
    week: Int,
    courses: List<CourseEntry>,
    schedules: List<ScheduleEntry>,
): WeekGridData {
    val grid = buildWeekGrid(week, courses).map { it.toMutableList() }.toMutableList()

    for (schedule in schedules) {
        if (!scheduleMatches(week, schedule)) continue

        val col = (schedule.day.coerceIn(1, 7)) - 1
        val startRow = slotIndexFromPeriod(schedule.startPeriod)
        val spanRows = spanRowsFromPeriods(schedule.startPeriod, schedule.endPeriod)

        when (val existing = grid[startRow][col]) {
            is GridCell.Occupied -> {
                // 落在连堂占位行：跳过，避免破坏跨行结构
                continue
            }

            is GridCell.Stack -> {
                val nextItems = sortStackItems(existing.value.items + StackItem.Schedule(schedule))
                val hasCourse = existing.value.items.any { it is StackItem.Course }
                // span 以课程为准；无课则取较大 span
                val courseSpan = if (hasCourse) {
                    existing.value.spanRows
                } else {
                    maxOf(existing.value.spanRows, spanRows)
                }
                grid[startRow][col] = GridCell.Stack(
                    GridStack(items = nextItems, spanRows = courseSpan),
                )
                if (courseSpan > existing.value.spanRows) {
                    writeOccupied(grid, startRow, col, courseSpan)
                }
            }

            null -> {
                grid[startRow][col] = GridCell.Stack(
                    GridStack(items = listOf(StackItem.Schedule(schedule)), spanRows = spanRows),
                )
                writeOccupied(grid, startRow, col, spanRows)
            }
        }
    }

    return grid
}

/** 取 stack 中的课程（若有） */
fun stackCourse(stack: GridStack): CourseEntry? =
    stack.items.firstOrNull { it is StackItem.Course }?.let { (it as StackItem.Course).course }

/** 取 stack 中所有日程 */
fun stackSchedules(stack: GridStack): List<ScheduleEntry> =
    stack.items.filterIsInstance<StackItem.Schedule>().map { it.schedule }
