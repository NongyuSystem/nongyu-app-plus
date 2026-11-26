package com.nongyu.appplus.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nongyu.appplus.timetable.model.ScheduleEntry

/**
 * 「添加日程表单 + 日程明细卡」的状态。
 *
 * 从 `MainShell` 原样搬出，行为未改。
 *
 * - [adding] 非 null 即导航栏原地变形为表单卡，值 = `(day, startPeriod, week)`。
 *   工具条入口用「今天 + 第 1 节」，空白格 / 时间条入口用被点的那格。
 * - [detailId] 日程明细卡正在展示的日程 id；点日程卡打开、点卡外任意位置退出（开/关两态）。
 * - [addedSchedules] 点空白格添加的日程（#18 第 6 项）。本期内存态、不持久化
 *   （与 `MOCK_COURSES` 口径一致）。
 * - [dragDp] 表单横杠下拉累计位移（dp，正 = 向下），松手超阈值即关表单。
 */
class ScheduleFormState {
    val addedSchedules = mutableStateListOf<ScheduleEntry>()

    var dragDp by mutableFloatStateOf(0f)

    var detailId by mutableStateOf<String?>(null)

    var adding by mutableStateOf<Triple<Int, Int, Int>?>(null)

    /** 工具条入口：预填今天、第 1 节。 */
    fun openFromToolbar(week: Int, day: Int) {
        adding = Triple(day, 1, week)
    }

    /** 空白格 / 时间条入口：用被点的那格。 */
    fun openAt(day: Int, period: Int, week: Int) {
        adding = Triple(day, period, week)
    }

    /** 点日程卡：只有开/关两态，点同一条即关（toggle）。 */
    fun toggleDetail(id: String) {
        detailId = if (detailId == id) null else id
    }

    /**
     * 关闭表单、把新日程收进列表。
     *
     * ⚠️ 两个状态在**同一个方法里顺序写**是有意为之：调用方原先把它们写在同一个
     * `onSave` 回调里，导致 `AnimatedContent` 的 targetState 没有及时重算、
     * 表单保存后不关闭（实测）。
     */
    fun save(entry: ScheduleEntry) {
        adding = null
        addedSchedules.add(entry)
    }

    /** 取消表单（点取消 / 点表单外）。 */
    fun cancelAdd() {
        adding = null
    }

    /** 表单横杠下拉：跟手记录位移。 */
    fun onAddDrag(deltaDp: Float) {
        dragDp += deltaDp
    }

    /** 表单横杠松手：超过阈值（72 * uiScale）就关表单，并清零位移。 */
    fun onAddDragEnd(threshold: Float) {
        if (dragDp > threshold) adding = null
        dragDp = 0f
    }

    /** 明细卡行内编辑即时落库：替换列表里同 id 那条；detailId 不变，卡片不闪。 */
    fun updateEntry(updated: ScheduleEntry) {
        val idx = addedSchedules.indexOfFirst { it.id == updated.id }
        if (idx >= 0) addedSchedules[idx] = updated
    }

    /** 关闭明细卡。 */
    fun closeDetail() {
        detailId = null
    }
}

/** [ScheduleFormState] 的 Compose 工厂（无副作用，仅需 `remember` 固定身份）。 */
@Composable
fun rememberScheduleFormState(): ScheduleFormState = remember { ScheduleFormState() }
