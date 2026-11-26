package com.nongyu.appplus.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nongyu.appplus.timetable.data.CourseReviewRepository
import com.nongyu.appplus.timetable.data.CourseUserStore
import com.nongyu.appplus.timetable.data.LocationScope
import com.nongyu.appplus.timetable.data.MOCK_TERM_START
import com.nongyu.appplus.timetable.logic.AttendanceStats
import com.nongyu.appplus.timetable.logic.NOTE_DEBOUNCE_MS
import com.nongyu.appplus.timetable.logic.ReviewLine
import com.nongyu.appplus.timetable.logic.ReviewListDisplay
import com.nongyu.appplus.timetable.logic.ReviewText
import com.nongyu.appplus.timetable.logic.noteChanged
import com.nongyu.appplus.timetable.logic.shouldPersistNote
import com.nongyu.appplus.timetable.model.CourseEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 课程详情卡的全部**可修改数据**（地点 / 教师 / 周次例外 / 备注 / 考勤 / 评价）。
 *
 * 从 `MainShell` 原样搬出，行为未改。原先这约 190 行状态与 5 个 `LaunchedEffect`
 * 内联在壳里，是「详情卡数据源」，与路由、命中层级无关，故整体抽成 state-holder。
 *
 * 实测踩过的坑（详见 `docs/specs/界面层.md` §8.5 A / §8.5 C）：
 * - **备注不能用 `collectAsState` 直接喂 `TextField`**（写入与回读打架，重启丢值）。
 *   这里维持「本地 `mutableState` 做 UI 真源 + 打开卡片读一次 + 改动防抖落盘」，
 *   能否落盘的判据住在 `timetable/logic/CourseDetailRules.kt`（`shouldPersistNote` / `noteChanged`）。
 * - **考勤键是 ISO 日期，不是节次**。2026-11-23 维护者指正：原先 `period -> Boolean`
 *   让一门 1-16 周的课一学期只能记一次考勤；详情卡里的「应到 2」其实是单次课的节数。
 *   现在存储按日期，展示按 [AttendanceStats] 逐周推导（含单双周）。
 * - **评价来自真实后端**，不再是进程内 mock：按「老师 + 课程」查询，
 *   只显示前 3 条、第 4 条用于判断「还有更多」。
 * - 所有随课程切换要复位的状态都用 `remember(courseId)`（不是 `remember { }`），
 *   保证换课的**当帧**就拿到初始值，不会闪现上一门课的备注/考勤/评价。
 *
 * @property deletedIds 已删除课程 id 集合（贯穿切周链路过滤）。它**不随 courseId 复位**
 *   （始终 `collectAsState` 整个 store），课表页也要用它过滤，故暴露在此。
 * @property attendanceStats 学期课次统计（总课次 / 已上 / 剩余），无详情课时为 null。
 */
class CourseDetailState(
    val courseId: String?,
    /** 今天（按天覆盖）的地点，无覆盖时为 null。 */
    val locDay: String?,
    /** 通用覆盖地点，无覆盖时为 null。 */
    val locGeneral: String?,
    /** 教师覆盖，无覆盖时为 null。 */
    val teacherOverride: String?,
    val deletedIds: Set<String>,
    /** 按 ISO 日期记录的考勤（键 = 日期，值 = 是否已上）。 */
    val attendanceByDate: Map<String, Boolean>,
    /** 学期课次统计；无详情课时为 null。 */
    val attendanceStats: AttendanceStats.Stats?,
    /** 评价列表是否还有更多（第 4 条及以后）。 */
    val hasMoreReviews: Boolean,
    /** 评价列表（已按前 3 条截断）。 */
    val comments: List<ReviewLine>,
    /** 周次例外天（ISO 字符串，不上课的天）。 */
    val exceptionIsoDays: Set<String>,
    /** 备注 UI 真源（本地态，非 DataStore 回读）。 */
    val noteText: String,
    private val store: CourseUserStore,
    private val scope: CoroutineScope,
    private val noteTextState: MutableState<String>,
    private val attendanceByDateState: MutableState<Map<String, Boolean>>,
    private val commentsState: MutableState<List<ReviewLine>>,
    private val hasMoreReviewsState: MutableState<Boolean>,
    private val exceptionIsoDaysState: MutableState<Set<String>>,
    private val reviewRepo: CourseReviewRepository,
    private val detailCourse: CourseEntry?,
    private val reloadReviewsAction: suspend (CourseEntry) -> Unit,
) {
    /** 行内改教师：写 store，经 teacherFlow 回读生效。 */
    fun onTeacherChange(value: String) {
        val id = courseId ?: return
        scope.launch { store.setTeacherOverride(id, value) }
    }

    /** 翻转某一天的考勤：先改本地态（当帧生效），再落盘。 */
    fun onAttendanceToggle(isoDate: String) {
        val id = courseId ?: return
        val next = !(attendanceByDate[isoDate] ?: false)
        attendanceByDateState.value = attendanceByDate + (isoDate to next)
        scope.launch { store.setAttendance(id, isoDate, next) }
    }

    /** 备注 UI 真源更新（落盘由工厂里的防抖 effect 统一处理）。 */
    fun onNoteChange(text: String) {
        noteTextState.value = text
    }

    /** 新增评价：解析「评分 + 正文」，成功后重新拉取列表。 */
    fun onCommentAdd(text: String) {
        val c = detailCourse ?: return
        val parsed = ReviewText.parse(text)
        scope.launch {
            reviewRepo.add(
                teacherName = c.teacher,
                courseName = c.name,
                rating = parsed.rating,
                body = parsed.body,
            ).onSuccess { reloadReviewsAction(c) }
        }
    }

    /** 删除这门课：落盘到 deletedIds，切周后不再「复活」。 */
    fun onDelete() {
        val id = courseId ?: return
        scope.launch { store.setDeletedIds(deletedIds + id) }
    }

    /**
     * 地点编辑确认：按天覆盖优先；**未选任何天时退回通用覆盖**，保证至少有值生效。
     */
    fun onLocationConfirm(newLocation: String, days: Set<String>) {
        val id = courseId ?: return
        scope.launch {
            store.setLocationForDays(id, days, newLocation)
            if (days.isEmpty()) {
                store.setLocationOverride(id, LocationScope.Once, newLocation)
            }
        }
    }

    /** 周次编辑确认：先改本地例外天（当帧生效），再落盘。 */
    fun onWeekConfirm(days: Set<String>) {
        val id = courseId ?: return
        exceptionIsoDaysState.value = days
        scope.launch { store.setExceptionDays(id, days) }
    }
}

/**
 * [CourseDetailState] 的 Compose 工厂：把原先内联在 `MainShell` 的状态与副作用
 * 原样搬到这里（key 与顺序都未动）。`MainShell` 只调用它、读返回值。
 */
@Composable
fun rememberCourseDetailState(
    store: CourseUserStore,
    scope: CoroutineScope,
    detailCourse: CourseEntry?,
): CourseDetailState {
    val courseId = detailCourse?.id

    // 地点显示值：优先「按天覆盖」（今天），否则「通用覆盖」，否则原始 room。
    val todayIso = remember { LocalDate.now().toString() }
    val locDayFlow = remember(courseId, todayIso) {
        if (courseId != null) store.locationForDay(courseId, todayIso) else null
    }
    val locGeneralFlow = remember(courseId) {
        if (courseId != null) store.locationOverride(courseId, LocationScope.Once) else null
    }
    val locDay by (locDayFlow ?: flowOf(null)).collectAsState(initial = null)
    val locGeneral by (locGeneralFlow ?: flowOf(null)).collectAsState(initial = null)

    // 教师覆盖（行内输入）
    val teacherFlow = remember(courseId) {
        if (courseId != null) store.teacherOverride(courseId) else null
    }
    val teacherOverride by (teacherFlow ?: flowOf(null)).collectAsState(initial = null)

    // 已删除课程 id：贯穿切周链路过滤，防止切周后「复活」。始终收集，不随 courseId 复位。
    val deletedIds by store.deletedIds().collectAsState(initial = emptySet())

    // 周次复杂态：例外天（不上）
    val exceptionIsoDays = remember(courseId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(courseId) {
        exceptionIsoDays.value = if (courseId != null) {
            store.exceptionDays(courseId).first()
        } else emptySet()
    }

    // 备注三态：UI 真源 / 初始化完成标记 / 已落盘值（用于跳过无意义写入）。
    // 详见 docs/specs/界面层.md §8.5 A：不能用 collectAsState 直接喂 TextField。
    val noteText = remember(courseId) { mutableStateOf("") }
    val noteReady = remember(courseId) { mutableStateOf(false) }
    val notePersisted = remember(courseId) { mutableStateOf("") }
    LaunchedEffect(courseId) {
        if (courseId != null) {
            val loaded = store.noteOnce(courseId)
            noteText.value = loaded
            notePersisted.value = loaded
            noteReady.value = true
        } else {
            noteText.value = ""
            notePersisted.value = ""
            noteReady.value = false
        }
    }
    // 输入停止 NOTE_DEBOUNCE_MS 后统一落盘：既不会每个字符都起一个写协程（竞态），
    // 也不会因为 Flow 回读把输入框内容拉回去。
    LaunchedEffect(noteText.value, courseId, noteReady.value) {
        if (shouldPersistNote(noteReady.value, courseId) && noteChanged(notePersisted.value, noteText.value)) {
            delay(NOTE_DEBOUNCE_MS)
            store.setNote(courseId!!, noteText.value)
            notePersisted.value = noteText.value
        }
    }

    // 考勤：**按「哪一次课」记录**（ISO 日期 -> 是否已上），不再按节次。
    // 2026-11-23 维护者指正：原先 period -> Boolean 让一门 1-16 周的课一学期只能记一次；
    // 详情卡「应到 2」其实是单次课的节数。现在存储按日期，展示按学期课次统计。
    val attendanceByDate = remember(courseId) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }
    LaunchedEffect(courseId) {
        attendanceByDate.value = if (courseId != null) {
            store.attendanceAll(courseId)
        } else {
            emptyMap()
        }
    }
    // 学期统计：总课次 / 已上课次 / 剩余（可请假）次数
    val attendanceStats: AttendanceStats.Stats? =
        remember(courseId, attendanceByDate.value) {
            if (detailCourse == null) {
                null
            } else {
                AttendanceStats.compute(
                    course = detailCourse,
                    termStart = MOCK_TERM_START,
                    today = LocalDate.now(),
                )
            }
        }

    // 评价：从后端按「老师 + 课程」查询（真实数据，不再是 mock）。
    val comments = remember(courseId) { mutableStateOf<List<ReviewLine>>(emptyList()) }
    val hasMoreReviews = remember(courseId) { mutableStateOf(false) }
    val context = LocalContext.current
    val reviewRepo = remember { CourseReviewRepository(context) }
    // 课程上下文：从当前详情卡拿老师与课程名
    LaunchedEffect(detailCourse?.id) {
        val c = detailCourse
        if (c == null) {
            comments.value = emptyList()
            hasMoreReviews.value = false
        } else {
            reviewRepo.list(teacherName = c.teacher, courseName = c.name)
                .onSuccess { reviews ->
                    // 只显示前 3 条；第 4 条用于判断「还有更多」
                    val split = ReviewListDisplay.split(
                        reviews.map { ReviewLine(text = it.text, isMine = it.isMine) },
                    )
                    comments.value = split.visible
                    hasMoreReviews.value = split.hasMore
                }
                .onFailure {
                    comments.value = emptyList()
                    hasMoreReviews.value = false
                }
        }
    }

    // 增删评价后重新拉取列表：与上面的首次加载共用同一套映射逻辑。
    val reloadReviews: suspend (CourseEntry) -> Unit = { course ->
        reviewRepo.list(teacherName = course.teacher, courseName = course.name)
            .onSuccess { reviews ->
                val split = ReviewListDisplay.split(
                    reviews.map { ReviewLine(text = it.text, isMine = it.isMine) },
                )
                comments.value = split.visible
                hasMoreReviews.value = split.hasMore
            }
    }

    return CourseDetailState(
        courseId = courseId,
        locDay = locDay,
        locGeneral = locGeneral,
        teacherOverride = teacherOverride,
        deletedIds = deletedIds,
        attendanceByDate = attendanceByDate.value,
        attendanceStats = attendanceStats,
        hasMoreReviews = hasMoreReviews.value,
        comments = comments.value,
        exceptionIsoDays = exceptionIsoDays.value,
        noteText = noteText.value,
        store = store,
        scope = scope,
        noteTextState = noteText,
        attendanceByDateState = attendanceByDate,
        commentsState = comments,
        hasMoreReviewsState = hasMoreReviews,
        exceptionIsoDaysState = exceptionIsoDays,
        reviewRepo = reviewRepo,
        detailCourse = detailCourse,
        reloadReviewsAction = reloadReviews,
    )
}
