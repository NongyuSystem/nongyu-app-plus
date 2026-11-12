package com.nongyu.appplus.shell.bar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nongyu.appplus.timetable.data.MOCK_COURSES
import com.nongyu.appplus.timetable.data.MockCourseCommentRepository
import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.ui.CourseDetailContent
import com.nongyu.appplus.timetable.ui.weeksLabelOf
import com.nongyu.appplus.shell.DetailStage

/**
 * 底部条的**课程详情卡内容**（`BarMode.Detail` 分支）。
 *
 * 从 `MainShell` 原样搬出，行为与交互一行未改；拆开的只是职责：
 * 本文件负责「详情卡的参数布线」，Shell 继续持有状态。
 *
 * ## 两条不能动的约束（来自实测事故）
 *
 * 1. **不要给本卡片挂「消费点击」的 clickable**。当初是为了防止点详情卡穿透到
 *    内容区的收起层，但收起层现在放在课表**之下**（白名单分层方案），
 *    与底栏里的详情卡根本不在同一命中链上，这层已多余；
 *    而且它会吃掉横杠的 pointerInput，导致第二次上划（进第四态）
 *    手势完全收不到（实测日志无 end 回调）。
 * 2. **拖到底不能立刻清 `detailCourse`**：那样 AnimatedContent 当帧就把内容
 *    换成导航栏、收拢动画根本来不及跑。吸附去向由 Shell 用
 *    `snapDetailStage` 判定后只置 `isCollapsing`。
 *
 * 折叠、写入持久层、吸附这些**规则**都不在这里，全部留在 Shell 或 `logic/`。
 */
@Composable
fun DetailCardContent(
    course: CourseEntry,
    detailStage: DetailStage,
    expandProgress: Float,
    isCollapsing: Boolean,
    uiScale: Float,
    location: String,
    teacher: String,
    attendance: Map<Int, Boolean>,
    note: String,
    comments: List<String>,
    /** 教师行内修改：调用方负责更新显示值与落盘。 */
    onTeacherChange: (String) -> Unit,
    /** 考勤点击：调用方负责翻转合并态与落盘。 */
    onAttendanceToggle: (Int) -> Unit,
    /** 备注输入：调用方负责更新本地真源（落盘由防抖 effect 负责）。 */
    onNoteChange: (String) -> Unit,
    /** 评论增删后重新拉取列表。 */
    onCommentChanged: () -> Unit,
    /** 删除这天的这节课（落盘由调用方负责）。 */
    onDelete: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onWeekEdit: () -> Unit,
    onLocationEdit: () -> Unit,
    onLocationNav: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    CourseDetailContent(
        course = course,
        uiScale = uiScale,
        fullStage = detailStage != DetailStage.Brief,
        // 高度进度 0..1，内容层据此做「胶囊 -> K-V 移动」与
        // 「评论/备注从盒底滑出」的分帧动画
        expandProgress = expandProgress,
        collapsing = isCollapsing,
        showDelete = detailStage == DetailStage.FullDelete,
        onDelete = {
            onDelete()
            onCollapse()
        },
        otherSlots = MOCK_COURSES.filter { it.name == course.name && it.id != course.id },
        onDismiss = onDismiss,
        location = location,
        teacher = teacher,
        onTeacherChange = onTeacherChange,
        weekText = weeksLabelOf(course),
        onWeekEdit = onWeekEdit,
        attendance = attendance,
        onAttendanceToggle = onAttendanceToggle,
        note = note,
        onNoteChange = onNoteChange,
        comments = comments,
        onCommentAdd = { text ->
            MockCourseCommentRepository.add(course.id, text)
            onCommentChanged()
        },
        onCommentRemove = { i ->
            MockCourseCommentRepository.remove(course.id, i)
            onCommentChanged()
        },
        onLocationEdit = onLocationEdit,
        onLocationNav = onLocationNav,
        onHandleDrag = { deltaDp ->
            onDragStart()
            onDragDelta(deltaDp)
        },
        onHandleDragEnd = { onDragEnd() },
        // 见类注释第 1 条：这里刻意不挂消费点击的 clickable。
        modifier = Modifier.fillMaxSize(),
    )
}
