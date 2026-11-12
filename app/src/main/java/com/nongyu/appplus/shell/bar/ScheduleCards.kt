package com.nongyu.appplus.shell.bar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nongyu.appplus.timetable.model.ScheduleEntry
import com.nongyu.appplus.timetable.ui.ScheduleDetailContent
import com.nongyu.appplus.timetable.ui.ScheduleFormContent

/**
 * 底部条的**添加日程表单**（`BarMode.AddSchedule` 分支）。
 *
 * 从 `MainShell` 原样搬出，行为未改。两条实测约束写在下面，改之前必读：
 *
 * 1. **先关表单、再收日程，两个状态分开写**。之前写在同一个回调里时，
 *    `AnimatedContent` 的 targetState 没有及时重算，导致表单保存后不关闭。
 * 2. **横杠下拉超过阈值就关**：阈值 `72f * uiScale`，与其他卡片
 *    「拉下去就是关了这个卡片」同一手感。
 */
@Composable
fun AddScheduleCardContent(
    day: Int,
    startPeriod: Int,
    week: Int,
    uiScale: Float,
    onSave: (ScheduleEntry) -> Unit,
    onCancel: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
) {
    ScheduleFormContent(
        initialDay = day,
        initialPeriod = startPeriod,
        week = week,
        uiScale = uiScale,
        onSave = onSave,
        onCancel = onCancel,
        onHandleDrag = onDrag,
        onHandleDragEnd = {
            onDragEnd(72f * uiScale)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * 底部条的**日程明细卡**（`BarMode.ScheduleDetail` 分支）。
 *
 * 从 `MainShell` 原样搬出，行为未改。
 * 行内编辑即时落库：直接替换列表里同 id 的那条，`scheduleDetailId` 不变，
 * 卡片保持打开状态不闪。
 */
@Composable
fun ScheduleDetailCardContent(
    entry: ScheduleEntry,
    uiScale: Float,
    onUpdate: (ScheduleEntry) -> Unit,
    onClose: () -> Unit,
) {
    ScheduleDetailContent(
        entry = entry,
        uiScale = uiScale,
        onUpdate = onUpdate,
        onClose = onClose,
        onHandleDragDown = onClose,
        modifier = Modifier.fillMaxSize(),
    )
}
