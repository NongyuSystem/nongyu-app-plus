package com.nongyu.appplus.shell

import com.nongyu.appplus.timetable.data.CourseRepository
import com.nongyu.appplus.design.uiScaleFor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import com.nongyu.appplus.design.ModalScrim
import com.nongyu.appplus.timetable.ui.TimetableRuler
import com.nongyu.appplus.timetable.ui.rememberWaterTilt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import com.nongyu.appplus.settings.logic.SettingsBuilders
import com.nongyu.appplus.settings.logic.TimetableSettings
import com.nongyu.appplus.settings.ui.SettingsPanel
import com.nongyu.appplus.settings.ui.SETTINGS_GEAR_DEGREES
import com.nongyu.appplus.settings.ui.settingsTween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.skin.LocalSkinResolution
import com.nongyu.appplus.skin.LocalSkinSource
import com.nongyu.appplus.icon.NavIconStrokes
import com.nongyu.appplus.design.NongyuAppPlusTheme
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.design.showTextToast
import com.nongyu.appplus.timetable.model.CourseEntry
import com.nongyu.appplus.timetable.logic.isPeriodEnded
import com.nongyu.appplus.timetable.logic.NOTE_DEBOUNCE_MS
import com.nongyu.appplus.timetable.logic.mergeAttendance
import com.nongyu.appplus.timetable.logic.noteChanged
import com.nongyu.appplus.timetable.logic.shouldPersistNote
import com.nongyu.appplus.timetable.logic.toggledAttendance
import com.nongyu.appplus.shell.bar.DetailCardContent
import com.nongyu.appplus.shell.bar.AddScheduleCardContent
import com.nongyu.appplus.shell.bar.ScheduleDetailCardContent
import com.nongyu.appplus.shell.bar.InputBarContent
import com.nongyu.appplus.shell.bar.NavRowContent
import com.nongyu.appplus.shell.bar.featheredEdge
import com.nongyu.appplus.shell.orb.OrbHost
import com.nongyu.appplus.shell.BarMetrics
import com.nongyu.appplus.shell.BarMode
import com.nongyu.appplus.shell.barTargetHeight
import com.nongyu.appplus.shell.dialogs.CourseLocationEditDialog
import com.nongyu.appplus.shell.dialogs.CourseLocationNavDialog
import com.nongyu.appplus.shell.dialogs.CourseWeekEditDialog
import com.nongyu.appplus.shell.dialogs.currentLocationOf
import com.nongyu.appplus.timetable.data.LocationScope
import com.nongyu.appplus.shell.DetailSnap
import com.nongyu.appplus.shell.ClickIntent
import com.nongyu.appplus.shell.OpenCard
import com.nongyu.appplus.shell.currentOpenCard
import com.nongyu.appplus.shell.resolveBlankSlotClick
import com.nongyu.appplus.shell.resolveCourseClick
import com.nongyu.appplus.shell.snapDetailStage
import com.nongyu.appplus.shell.DetailStage
import com.nongyu.appplus.shell.DismissTarget
import com.nongyu.appplus.shell.OrbMode
import com.nongyu.appplus.shell.deriveBarMode
import com.nongyu.appplus.shell.dismissTargetOf
import com.nongyu.appplus.timetable.logic.TimetableGeometry
import com.nongyu.appplus.timetable.data.MockCourseCommentRepository
import com.nongyu.appplus.timetable.data.CourseUserStore
import com.nongyu.appplus.timetable.data.weekOfTerm
import com.nongyu.appplus.timetable.ui.classDaysOf
import com.nongyu.appplus.timetable.ui.weeksLabelOf
import com.nongyu.appplus.timetable.ui.LocationEditDialog
import com.nongyu.appplus.timetable.ui.WeekEditDialog
import com.nongyu.appplus.timetable.ui.LocationNavDialog
import com.nongyu.appplus.timetable.ui.TimetableScreen

/**
 * 主框架与导航壳。
 *
 * 只做「读状态 → 传参 → 调回调」的**组合**，不实现任何具体功能：
 * 路由、底栏五形态、命中层级顺序、星云球状态、时间条置顶，都在这里接线；
 * 规则与内容分别在 `shell/ShellState.kt`、`shell/BarHeight.kt`、`shell/CardIntent.kt`、
 * `shell/bar/`、`shell/orb/`、`shell/dialogs/` 与 `timetable/logic/`。
 *
 * 改动前必读 `docs/specs/界面层.md` §4（边界）与 §8（事故档案）。
 */
private val TABS = listOf("全部", "课表", "探索", "个人")

// 考勤默认态的判定已下沉到 timetable/logic/PeriodEnded.kt（可单测），
// 此处直接用，不再保留 UI 层副本。

@Composable
fun MainShell() {
    val colors = NongyuTheme.colors
    val context = LocalContext.current
    val courseUserStore = remember { com.nongyu.appplus.timetable.data.CourseUserStore(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(OrbMode.Idle) }
    var inputText by rememberSaveable { mutableStateOf("") }

    // 详见 docs/specs/界面层.md §8.5 状态与副作用 / 为何课程详情状态必须提升到 Shell
    var detailCourse by remember { mutableStateOf<CourseEntry?>(null) }

    // 详情卡形态（简介 / 详情），拖横杠在两者间吸附。每次打开卡片回到简介态。
    var detailStage by remember { mutableStateOf(DetailStage.Brief) }
    // 拖拽中的实时高度偏移（dp，正值=往上拉高）。松手后清零，由动画接管。
    var detailDragDp by remember { mutableStateOf(0f) }
    // 是否正在拖拽。**独立于 detailDragDp**：拖拽结束时两者在同一次重组里都变，
    // 若用 `detailDragDp != 0f` 当动画开关，松手那帧取到的仍是拖拽期状态，
    // 导致吸附动画被 tween(0) 吞掉 —— 这就是「上划/下降没有动画」的根因。
    var isDragging by remember { mutableStateOf(false) }
    // 退出时先收拢高度、再切内容；为 true 期间卡片仍在但目标高度降到 navHeight。
    var isCollapsing by remember { mutableStateOf(false) }
    // 打开课程时复位到简介态
    LaunchedEffect(detailCourse?.id) {
        if (detailCourse != null) {
            detailStage = DetailStage.Brief
            detailDragDp = 0f
            isDragging = false
            isCollapsing = false
        }
    }

    /** 统一的退出：先收拢高度，动画走完再切内容。点空白、拖到底、返回都走它。 */
    fun collapseDetail() {
        if (detailCourse != null && !isCollapsing) isCollapsing = true
    }

    // 点空白格添加的日程（#18 第 6 项）：状态放 Shell，TimetableScreen 只负责渲染。
    // 本期内存态、不持久化（与 MOCK_COURSES 口径一致）。
    val addedSchedules = remember {
        androidx.compose.runtime.mutableStateListOf<com.nongyu.appplus.timetable.model.ScheduleEntry>()
    }

    // 添加日程表单的横杠下拉累计位移（dp，正=向下）
    var scheduleDragDp by remember { mutableFloatStateOf(0f) }
    // 日程卡「展开态」：点日程卡拉杆打开，再点卡外任意位置退出（开/关两态）
    var scheduleDetailId by remember { mutableStateOf<String?>(null) }

    // ---- 详情卡的可修改数据 ----
    val courseId = detailCourse?.id

    // 地点显示值：优先「按天覆盖」（今天），否则「通用覆盖」，否则原始 room。
    val todayIso = remember { java.time.LocalDate.now().toString() }
    val locDayFlow = remember(courseId, todayIso) {
        if (courseId != null) courseUserStore.locationForDay(courseId, todayIso) else null
    }
    val locGeneralFlow = remember(courseId) {
        if (courseId != null) {
            courseUserStore.locationOverride(
                courseId,
                com.nongyu.appplus.timetable.data.LocationScope.Once,
            )
        } else null
    }
    val locDay by (locDayFlow ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsState(initial = null)
    val locGeneral by (locGeneralFlow ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsState(initial = null)

    // 教师覆盖（行内输入）
    val teacherFlow = remember(courseId) {
        if (courseId != null) courseUserStore.teacherOverride(courseId) else null
    }
    val teacherOverride by (teacherFlow ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsState(initial = null)

    // 周次复杂态：例外天（不上）
    var exceptionIsoDays by remember(courseId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(courseId) {
        exceptionIsoDays = if (courseId != null) {
            courseUserStore.exceptionDays(courseId).first()
        } else emptySet()
    }

    // 已删除课程 id：贯穿切周链路过滤，防止切周后「复活」
    val deletedIds by courseUserStore.deletedIds().collectAsState(initial = emptySet())

    // 详见 docs/specs/界面层.md §8.5 A 备注不能用 collectAsState 直接喂 TextField
    var noteText by remember(courseId) { mutableStateOf("") }
    // true = 初始化读取已完成，之后的改动才允许落盘（避免把空串覆盖掉已存的值）。
    var noteReady by remember(courseId) { mutableStateOf(false) }
    // 已落盘的备注值，用于跳过无意义写入。
    var notePersisted by remember(courseId) { mutableStateOf("") }
    LaunchedEffect(courseId) {
        if (courseId != null) {
            val loaded = courseUserStore.noteOnce(courseId)
            noteText = loaded
            notePersisted = loaded
            noteReady = true
        } else {
            noteText = ""
            notePersisted = ""
            noteReady = false
        }
    }
    // 输入停止 250ms 后统一落盘：既不会每个字符都起一个写协程（竞态），
    // 也不会因为 Flow 回读把输入框内容拉回去。
    LaunchedEffect(noteText, courseId, noteReady) {
        if (shouldPersistNote(noteReady, courseId) && noteChanged(notePersisted, noteText)) {
            delay(NOTE_DEBOUNCE_MS)
            courseUserStore.setNote(courseId!!, noteText)
            notePersisted = noteText
        }
    }

    // 考勤：**按「哪一次课」记录**（ISO 日期 → 是否已上），不再按节次。
    //
    // 2026-11-23 维护者指正：原来用 `period -> Boolean`，一门 1-16 周的课每周都上，
    // 键却只有「课程 + 节次」，于一学期只能记一次考勤；而详情卡里显示的
    // 「应到 2」其实是**单次课的节数**，不是学期课次（12/8 次）。
    //
    // 现在：存储按日期，展示按「学期课次统计」（AttendanceStats 逐周推导，含单双周）。
    var attendanceByDate by remember(courseId) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }
    // 首次打开时从持久层读出该课程的全部考勤
    LaunchedEffect(courseId) {
        attendanceByDate = if (courseId != null) {
            courseUserStore.attendanceAll(courseId)
        } else {
            emptyMap()
        }
    }
    // 学期统计：总课次 / 已上课次 / 剩余（可请假）次数
    val attendanceStats: com.nongyu.appplus.timetable.logic.AttendanceStats.Stats? =
        remember(courseId, attendanceByDate) {
            val c = detailCourse
            if (c == null) {
                null
            } else {
                com.nongyu.appplus.timetable.logic.AttendanceStats.compute(
                    course = c,
                    termStart = com.nongyu.appplus.timetable.data.MOCK_TERM_START,
                    today = java.time.LocalDate.now(),
                )
            }
        }

    // 评论：从后端按「老师 + 课程」查询评价（真实数据，不再是 mock）。
    // UI 仍只认 List<String>，这里负责把 Review 映射成带评分前缀的文本，
    // 删除时再按下标把 id 找回来。
    var comments by remember(courseId) { mutableStateOf<List<String>>(emptyList()) }
    var hasMoreReviews by remember(courseId) { mutableStateOf(false) }
    val reviewRepo = remember { com.nongyu.appplus.timetable.data.CourseReviewRepository(context) }
    // 课程上下文：从当前详情卡拿老师与课程名
    LaunchedEffect(detailCourse?.id) {
        val c = detailCourse
        if (c == null) {
            comments = emptyList()
            hasMoreReviews = false
        } else {
            reviewRepo.list(teacherName = c.teacher, courseName = c.name)
                .onSuccess { reviews ->
                    // 只显示前 3 条；第 4 条用于判断「还有更多」
                    val split = com.nongyu.appplus.timetable.logic.ReviewListDisplay.split(
                        reviews.map { it.text },
                    )
                    comments = split.visible
                    hasMoreReviews = split.hasMore
                }
                .onFailure {
                    comments = emptyList()
                    hasMoreReviews = false
                }
        }
    }

    // 增删评价后重新拉取列表：与上面的首次加载共用同一套映射逻辑。
    suspend fun reloadReviews(course: com.nongyu.appplus.timetable.model.CourseEntry) {
        reviewRepo.list(teacherName = course.teacher, courseName = course.name)
            .onSuccess { reviews ->
                val split = com.nongyu.appplus.timetable.logic.ReviewListDisplay.split(
                    reviews.map { it.text },
                )
                comments = split.visible
                hasMoreReviews = split.hasMore
            }
    }

    // 三个子窗体的开关
    var showLocationEdit by remember { mutableStateOf(false) }
    var showLocationNav by remember { mutableStateOf(false) }
    var showWeekEdit by remember { mutableStateOf(false) }
    // 添加日程表单（#14）：非 null 即导航栏原地变形为表单卡。
    // (day, startPeriod, week) —— 工具条入口用「今天 + 第 1 节」，空白格入口用被点的那格。
    var addingSchedule by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    // 占位按钮的轻提示（不接任何真实逻辑）
    var placeholderHint by remember { mutableStateOf<String?>(null) }
    // 更新课表：进行中标记（防连点）+ 重载触发键（成功后让课表页重读缓存）
    var updatingTimetable by remember { mutableStateOf(false) }
    var timetableReloadKey by remember { mutableIntStateOf(0) }
    // ---- 设置面板（全局设置包，当前只有「课程表」分区）----
    var settingsOpen by remember { mutableStateOf(false) }
    var gearCenterPx by remember { mutableStateOf<Offset?>(null) }
    // 齿轮是否已被设置面板接管：由面板回报，工具条据此隐藏原图标（两者严格互斥）
    var gearTakenOver by remember { mutableStateOf(false) }
    // 面板滑入进度 + 齿轮旋转：用一个 Animatable 驱动，保证两者严格同步
    val settingsProgress = remember { Animatable(0f) }
    // 课程表设置项的值（本期只做界面，先放内存，持久化留给后续票）
    var settingsDayMode by remember { mutableStateOf(false) }
    var settingsShowWeekends by remember { mutableStateOf(true) }
    var settingsQuietRefresh by remember { mutableStateOf(false) }

    // 打开/关闭统一走同一个动画：progress 0→1 滑入，1→0 滑出（反向动画由对称 tween 保证）
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            settingsProgress.animateTo(1f, settingsTween())
        } else {
            settingsProgress.animateTo(0f, settingsTween())
        }
    }
    // 票 #60 需求 4.1：切到课表 Tab 时，课表图标从底部往上变绿（800ms）。
    // 切走直接归零，不做离场动画（需求 5）。
    val iconFill = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            iconFill.snapTo(0f)
            iconFill.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 800,
                    easing = androidx.compose.animation.core.LinearOutSlowInEasing,
                ),
            )
        } else {
            iconFill.snapTo(0f)
        }
    }

    // 票 #60：节次行的真实屏幕范围（由 TimetableGrid 实测上报），
    // 时间条按它定位 —— 不再手算那些容易漏项的 padding 公式。
    var periodTopPx by remember { mutableFloatStateOf(0f) }
    var periodHeightPx by remember { mutableFloatStateOf(0f) }
    // 票 #60：时间条拖动状态（遮罩用）；时间条本身也搬到 Shell 层，
    // 这样遮罩才能盖住底部导航栏和右上角工具条，同时时间条浮在遮罩之上。
    var rulerDragging by remember { mutableStateOf(false) }
    // 当前时间在时间轴上的比例（每分钟刷新够了）
    var nowMinute by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinute = java.time.LocalTime.now()
            delay(30_000L)
        }
    }
    var rulerFocus by remember { mutableStateOf<Float?>(null) }
    var rulerFocusHeld by remember { mutableStateOf<Float?>(null) }
    // 详见 docs/specs/界面层.md §8.5 B 时间文字清除 effect 的 key 只盯 rulerDragging
    LaunchedEffect(rulerDragging) {
        if (!rulerDragging && rulerFocusHeld != null) {
            delay(1600)
            rulerFocusHeld = null
        }
    }
    // 拖动中实时跟随手指
    LaunchedEffect(rulerFocus) {
        if (rulerFocus != null) rulerFocusHeld = rulerFocus
    }
    // 占位按钮的轻提示（设置 / 更新课表）：不接任何真实逻辑，只显示一句话。
    LaunchedEffect(placeholderHint) {
        // 提示出现 2.2s 后自动消失；用独立 effect 盯 placeholderHint，
        // 不放在组装点里，避免被后续重组打断。
        if (placeholderHint != null) {
            delay(2200)
            placeholderHint = null
        }
    }

    // 详见 docs/specs/界面层.md §8.5 C 图标线宽只读已解析结果，不要在此再 resolve()
    val skin = LocalSkinSource.current
    val resolution = LocalSkinResolution.current
    val iconStrokes = remember(skin, resolution) {
        NavIconStrokes.resolve(
            payload = resolution.resourcePayload(),
            builtIn = skin.builtIn.resources,
        )
    }
    // ADR-0009：图标位图覆盖（已是绝对路径，由 FileSkinSource 在启动路径拼好）。
    // 缺项 = 该项走几何绘制，逐图标回落。
    val iconImages = remember(resolution) { resolution.resourcePayload().iconImages }

    // 详见 docs/specs/界面层.md §8.3 C 雷达波方向语义
    var pulseKey by remember { mutableIntStateOf(0) }
    var pulseExpand by remember { mutableStateOf(true) }
    val pulse = remember { Animatable(0f) }

    val active = mode != OrbMode.Idle

    // 退出激活态：返回键优先，其次再次点按。
    BackHandler(enabled = active) { mode = OrbMode.Idle }

    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, animationSpec = tween(if (pulseExpand) 520 else 460))
        }
    }

    // 详见 docs/specs/界面层.md §8.3 E dim 只在长按态出现，且只保留一层
    val dimAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Listening) 0.55f else 0f,
        animationSpec = tween(300),
        label = "dim",
    )

    // 输入态：只把**底栏以外**压暗（让输入栏保持明亮、成为视觉焦点）。
    val inputScrimAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 0.35f else 0f,
        animationSpec = tween(300),
        label = "inputScrim",
    )

    // 点按态：圆球缩没、输入框展开
    val orbScale by animateFloatAsState(
        targetValue = when (mode) {
            OrbMode.Idle -> 1f
            OrbMode.Listening -> 1.08f
            OrbMode.Input -> 0.4f
        },
        animationSpec = tween(420),
        label = "orbScale",
    )
    val orbAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 0f else 1f,
        animationSpec = tween(300),
        label = "orbAlpha",
    )
    val navAlpha by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 0f else 1f,
        animationSpec = tween(280),
        label = "navAlpha",
    )
    // 输入框展开：宽度交给父布局（与导航栏同宽），只用 scaleX 做展开动画。
    val inputScale by animateFloatAsState(
        targetValue = if (mode == OrbMode.Input) 1f else 0.18f,
        animationSpec = tween(420),
        label = "inputScale",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground),
    ) {
        // 详见 docs/specs/界面层.md §8.6 A uiScale 基准 411dp、上限 1.25
        val uiScale = uiScaleFor(maxWidth.value)

        // 「更新课表」的唯一实现：工具条按钮与设置面板里的同名条目都调它，
        // 避免两处各写一遍导致行为漂移（一个是 60s 限流，一个漏了就是 bug）。
        fun updateTimetableOnce() {
            if (!updatingTimetable) {
                updatingTimetable = true
                coroutineScope.launch {
                    val repo = CourseRepository(context)
                    repo.refreshFromJiaowu()
                        .onSuccess { count ->
                            if (!settingsQuietRefresh) {
                                showTextToast(context, "课表已更新：$count 个时段", uiScale = uiScale)
                            }
                            timetableReloadKey++
                        }
                        .onFailure { e ->
                            val msg = when (e) {
                                is CourseRepository.TooFrequentException ->
                                    e.message ?: "更新过于频繁，请稍后再试"
                                else -> e.message ?: "更新课表失败"
                            }
                            showTextToast(context, msg, uiScale = uiScale, long = true)
                        }
                    updatingTimetable = false
                }
            }
        }
        fun s(dp: Dp): Dp = (dp.value * uiScale).dp

        val navHeight = s(62.dp)
        val navPadding = s(18.dp)
        val navCorner = s(20.dp)
        // 详见 docs/specs/界面层.md §8.6 B 球体也必须参与缩放
        val orbSize = s(78.dp)
        // 输入框与导航栏完全同步（高/圆角/边距全部跟随 uiScale）
        val inputHeight by animateDpAsState(
            targetValue = navHeight,
            animationSpec = tween(420),
            label = "inputHeight",
        )
        val inputCorner by animateDpAsState(
            targetValue = if (mode == OrbMode.Input) navCorner else s(66.dp),
            animationSpec = tween(420),
            label = "inputCorner",
        )
        val iconSize = s(30.dp)
        val labelSize = (8f * uiScale).sp

        // 详见 docs/specs/界面层.md §8.4 B 占位上报的是底栏自身高度，不是顶边坐标
        var navOccupiedHeight by remember { mutableStateOf<Float?>(null) }

        // 三形态判定提前到这里：内容区的「空白点击」分层需要它。
        // （原先定义在底栏段落里，那里太晚，内容区拿不到。）
        // 优先级规则住在 shell/ShellState.kt（deriveBarMode），那里有单测钉顺序。
        val barMode = deriveBarMode(
            addingSchedule = addingSchedule,
            scheduleDetailId = scheduleDetailId,
            hasDetailCourse = detailCourse != null,
            orbMode = mode,
        )

        // 四个页面：目前只有「课表」有实体内容（地图 #11），其余仍为占位。
        Box(modifier = Modifier.fillMaxSize()) {
            // 详见 docs/specs/界面层.md §8.1 B 卡片外退出层：全屏 + 声明在底栏之前
            val dismissAction: (() -> Unit)? = when (dismissTargetOf(barMode)) {
                DismissTarget.Detail -> ({ collapseDetail() })
                DismissTarget.ScheduleDetail -> ({ scheduleDetailId = null })
                DismissTarget.AddSchedule -> ({ addingSchedule = null })
                DismissTarget.None -> null
            }
            if (dismissAction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { dismissAction() },
                )
            }

            if (selectedTab == 1) {
                // 点击规则住在 shell/CardIntent.kt（有单测）。两个入口语义**不同**：
                // - 点课程盒子：课程卡开着 → 直接切卡（不要求点两下）
                // - 点空白格：课程卡开着 → 直接清空（立即，不走收拢动画）
                val openCard = currentOpenCard(
                    addingSchedule = addingSchedule,
                    scheduleDetailId = scheduleDetailId,
                    hasDetailCourse = detailCourse != null,
                )
                fun applyIntent(intent: ClickIntent) {
                    when (intent) {
                        ClickIntent.Dismiss -> when (openCard) {
                            OpenCard.AddSchedule -> addingSchedule = null
                            OpenCard.ScheduleDetail -> scheduleDetailId = null
                            // 点课程盒子时退出走收拢动画（高度动画跑完再切内容）
                            OpenCard.DetailCourse -> collapseDetail()
                            OpenCard.None -> Unit
                        }
                        ClickIntent.DismissDetailImmediately -> {
                            detailCourse = null
                            isCollapsing = false
                        }
                        is ClickIntent.OpenDetail -> {
                            detailCourse = intent.course
                            isCollapsing = false
                        }
                        is ClickIntent.OpenAddSchedule -> {
                            addingSchedule = Triple(intent.day, intent.period, intent.week)
                        }
                    }
                }

                TimetableScreen(
                    navOccupiedPx = navOccupiedHeight,
                        reloadSignal = timetableReloadKey,
                    deletedIds = deletedIds,
                    schedules = addedSchedules,
                    onCourseClick = { course ->
                        applyIntent(resolveCourseClick(openCard, course))
                    },
                    onBlankSlotAdd = { day, period, week ->
                        applyIntent(resolveBlankSlotClick(openCard, day, period, week))
                    },
                    onSettings = { settingsOpen = true },
                    gearRotation = settingsProgress.value * SETTINGS_GEAR_DEGREES,
                    gearTakenOver = gearTakenOver,
                    onGearCenterChange = { gearCenterPx = it },
                    onUpdateSchedule = { updateTimetableOnce() },
                    onAddScheduleFromToolbar = { week, day ->
                        // 工具条入口：预填今天、第 1 节
                        addingSchedule = Triple(day, 1, week)
                    },
                    // 日程卡只有开/关两态（维护者：「只有开关两态，拉下去就是关了这个卡片」）：
                    // 点卡或下拉横杠都是 toggle。
                    onScheduleClick = { sc ->
                        scheduleDetailId = if (scheduleDetailId == sc.id) null else sc.id
                    },
                    onRulerDragStateChange = { rulerDragging = it },
                    onPeriodColumnBounds = { top, h ->
                        periodTopPx = top
                        periodHeightPx = h
                    },
                    onRulerFocusChange = { rulerFocus = it },
                )
            }

            // 【输入态】维护者定义「除输入框外所有区域都是空白」。
            // 所以这层必须盖在课表**之上**，连课程盒子一起覆盖 —— 点哪都退出。
            // 输入框在底栏里、声明更靠后，它自己的 BasicTextField 会消费点击，不受影响。
            if (barMode == BarMode.Input) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x14000000))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            mode = OrbMode.Idle
                        },
                )
            }
        }

        // 详见 docs/specs/界面层.md §8.1 A dim 必须画在底栏之前
        if (inputScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(inputScrimAlpha)
                    .background(Color(0xFF0A140F)),
            )
        }

        // 详见 docs/specs/界面层.md §8.2 底栏原地变形与星云球溶解/汇聚的完整说明
        val dissolve = remember { Animatable(0f) }
        LaunchedEffect(barMode) {
            if (barMode == BarMode.Normal) {
                dissolve.animateTo(0f, tween(420))   // 汇聚
            } else {
                dissolve.animateTo(1f, tween(280))   // 溶解
            }
        }
        // 球体可见度：溶解时同时缩小并变透明，配合下面的粒子外散
        val orbDissolveAlpha = 1f - dissolve.value
        val orbDissolveScale = 1f + dissolve.value * 0.55f

        // 详情卡三档高度：简介 128 / 详情 340 / 详情+删除 384（多出删除按钮一行 44dp）。
        // 数值集中在 BarMetrics，计算规则在 shell/BarHeight.kt（有单测）。
        val barMetrics = BarMetrics(
            brief = s(128.dp),
            full = s(340.dp),
            fullDelete = s(384.dp),
            // 添加日程表单卡高度：六字段＋底部固定按钮，取比详情态略高的值。
            // 内容区内部可滚动，这里不需要精确等于内容高度。
            addSchedule = s(392.dp),
            // 日程展开卡：只展示明细，比课程详情矮
            scheduleDetail = s(210.dp),
        )
        val targetDetailHeight = barTargetHeight(
            barMode = barMode,
            isCollapsing = isCollapsing,
            detailStage = detailStage,
            dragDp = detailDragDp,
            metrics = barMetrics,
            navHeight = navHeight,
        )

        // 详见 docs/specs/界面层.md §8.2 A 必须用显式 Animatable，不能用 animateDpAsState
        val density = LocalDensity.current
        val targetPx = with(density) { targetDetailHeight.toPx() }
        val barHeightPx = remember { Animatable(with(density) { navHeight.toPx() }) }
        // 详见 docs/specs/界面层.md §8.2 B effect 的 key 必须含 detailStage，且只含真正的输入
        LaunchedEffect(barMode, isCollapsing, detailStage, isDragging) {
            // 形态切换 / 吸附 / 开始退出：走弹簧动画（拖拽中除外）
            if (!isDragging) {
                barHeightPx.animateTo(
                    targetValue = targetPx,
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                    ),
                )
                if (isCollapsing) {
                    // 收拢动画走完才真正切回常态内容
                    detailCourse = null
                    isCollapsing = false
                }
            }
        }
        LaunchedEffect(targetPx, isDragging) {
            if (isDragging) barHeightPx.snapTo(targetPx)   // 拖拽中跟手，不动画
        }
        val barHeight = with(density) { barHeightPx.value.toDp() }

        // 详见 docs/specs/界面层.md §8.2 C 形态进度在 brief -> full 这段归一化
        val expandProgress = with(density) {
            val briefPx = barMetrics.brief.toPx()
            val fullPx = barMetrics.full.toPx()
            ((barHeightPx.value - briefPx) / (fullPx - briefPx)).coerceIn(0f, 1f)
        }

        val barCorner by animateDpAsState(
            targetValue = when (barMode) {
                BarMode.Detail -> s(26.dp)
                BarMode.AddSchedule -> s(26.dp)
                BarMode.ScheduleDetail -> s(26.dp)
                BarMode.Input -> navCorner
                BarMode.Normal -> navCorner
            },
            animationSpec = spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            ),
            label = "barCorner",
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 输入态才让键盘顶起底栏；其它形态不跟随（整页不动，只输入条上移）。
                // 配合 AndroidManifest 的 adjustNothing：窗口不 resize，页面整体不上移，
                // 只有这条底栏被 imePadding 推上去。
                .then(if (barMode == BarMode.Input) Modifier.imePadding() else Modifier)
                .padding(horizontal = navPadding, vertical = s(18.dp))
                .fillMaxWidth()
                .height(barHeight)
                // 详见 docs/specs/界面层.md §8.4 A 不能用 .alpha(navAlpha) 作用于整个容器
                .featheredEdge(colors.navBar.colorItem, barCorner)
                .clip(RoundedCornerShape(barCorner))
                .background(colors.navBar.colorContainer)
                // 详见 docs/specs/界面层.md §8.1 C 底栏容器自己消费点击（卡片外退出层不能接管）
                .then(
                    if (barMode != BarMode.Normal && barMode != BarMode.Input) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* 只消费，不动作 */ }
                    } else {
                        Modifier
                    },
                )
                // 详见 docs/specs/界面层.md §8.4 B 上报底栏自身高度，不做坐标换算
                .onGloballyPositioned { coords ->
                    // 详见 docs/specs/界面层.md §8.4 C 只在常态 + 高度动画稳定时上报占位
                    if (barMode == BarMode.Normal && barHeight == navHeight) {
                        val h = coords.size.height.toFloat()
                        if (h > 0f && h != navOccupiedHeight) navOccupiedHeight = h
                    }
                },
        ) {
            AnimatedContent(
                targetState = barMode,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "navMorph",
            ) { showing ->
                when (showing) {
                BarMode.AddSchedule -> {
                    val slot = addingSchedule
                    if (slot != null) {
                        // 表单布线已搬到 shell/bar/ScheduleCards.kt（行为未改）。
                        AddScheduleCardContent(
                            day = slot.first,
                            startPeriod = slot.second,
                            week = slot.third,
                            uiScale = uiScale,
                            // 先关表单、再收日程：两个状态分开写。
                            // 之前写在同一个回调里时，AnimatedContent 的 targetState
                            // 没有及时重算，导致表单保存后不关闭（实测）。
                            onSave = { entry ->
                                addingSchedule = null
                                addedSchedules.add(entry)
                            },
                            onCancel = { addingSchedule = null },
                            // 横杠下拉：跟手记录位移。
                            onDrag = { deltaDp -> scheduleDragDp += deltaDp },
                            // 松手时超过阈值（72 * uiScale）就关闭表单。
                            onDragEnd = { threshold ->
                                if (scheduleDragDp > threshold) addingSchedule = null
                                scheduleDragDp = 0f
                            },
                        )
                    }
                }
                BarMode.ScheduleDetail -> {
                    val sc = addedSchedules.firstOrNull { it.id == scheduleDetailId }
                    if (sc != null) {
                        // 明细卡布线已搬到 shell/bar/ScheduleCards.kt（行为未改）。
                        ScheduleDetailCardContent(
                            entry = sc,
                            uiScale = uiScale,
                            // 行内编辑即时落库：直接替换列表里同 id 的那条。
                            // scheduleDetailId 不变，卡片保持打开状态不闪。
                            onUpdate = { updated ->
                                val idx = addedSchedules.indexOfFirst { it.id == updated.id }
                                if (idx >= 0) addedSchedules[idx] = updated
                            },
                            onClose = { scheduleDetailId = null },
                        )
                    }
                }
                BarMode.Detail -> {
                    val course = detailCourse
                    if (course != null) {
                        // 详情卡的参数布线已搬到 shell/bar/DetailCardContent.kt（行为未改）。
                        // 规则（删除落盘 / 考勤翻转 / 三档吸附）仍留在本函数里。
                        DetailCardContent(
                            course = course,
                            detailStage = detailStage,
                            expandProgress = expandProgress,
                            isCollapsing = isCollapsing,
                            uiScale = uiScale,
                            location = locDay ?: locGeneral ?: course.room,
                            teacher = teacherOverride ?: course.teacher,
                            attendance = attendanceByDate,
                            attendanceStats = attendanceStats,
                            termStart = com.nongyu.appplus.timetable.data.MOCK_TERM_START,
                            note = noteText,
                            comments = comments,
                            hasMoreReviews = hasMoreReviews,
                            onCommentDetail = {
                                showTextToast(context, "查看评价详情：即将开放", uiScale = uiScale)
                            },
                            onCommentMore = {
                                showTextToast(context, "查看更多评价：即将开放", uiScale = uiScale)
                            },
                            onTeacherChange = { v ->
                                coroutineScope.launch {
                                    courseUserStore.setTeacherOverride(course.id, v)
                                }
                            },
                            onAttendanceToggle = { isoDate ->
                                val next = !(attendanceByDate[isoDate] ?: false)
                                attendanceByDate = attendanceByDate + (isoDate to next)
                                coroutineScope.launch {
                                    courseUserStore.setAttendance(course.id, isoDate, next)
                                }
                            },
                            onNoteChange = { text -> noteText = text },
                            onCommentAdd = { text ->
                                val parsed = com.nongyu.appplus.timetable.logic.ReviewText.parse(text)
                                coroutineScope.launch {
                                    reviewRepo.add(
                                        teacherName = course.teacher,
                                        courseName = course.name,
                                        rating = parsed.rating,
                                        body = parsed.body,
                                    ).onSuccess { reloadReviews(course) }
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    courseUserStore.setDeletedIds(deletedIds + course.id)
                                }
                            },
                            onCollapse = ::collapseDetail,
                            onDismiss = { detailCourse = null },
                            onWeekEdit = { showWeekEdit = true },
                            onLocationEdit = { showLocationEdit = true },
                            onLocationNav = { showLocationNav = true },
                            onDragStart = { isDragging = true },
                            onDragDelta = { deltaDp -> detailDragDp += deltaDp },
                            onDragEnd = {
                                // 三档吸附：规则住在 shell/BarHeight.kt 的 snapDetailStage（有单测）。
                                // 阈值取相邻两档中点；低于简介档 55% 视为拖到底 → 退出。
                                when (val snap = snapDetailStage(detailStage, detailDragDp, barMetrics)) {
                                    // 拖到底：不立刻清 detailCourse，否则 AnimatedContent
                                    // 当帧就把内容换成导航栏、收拢动画根本来不及跑。
                                    DetailSnap.Collapse -> isCollapsing = true
                                    is DetailSnap.To -> detailStage = snap.stage
                                }
                                detailDragDp = 0f
                                isDragging = false
                            },
                        )
                    }
                }
                BarMode.Input -> {
                    // 输入态：导航栏**原地**变成输入栏，不再浮一个独立输入框。
                    // 内容与自动聚焦时序已搬到 shell/bar/InputBarContent.kt（行为未改）。
                    InputBarContent(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        uiScale = uiScale,
                        s = ::s,
                    )
                }
                BarMode.Normal -> {
                    // 2+2 导航行已搬到 shell/bar/NavRowContent.kt（行为未改）。
                    NavRowContent(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        navAlpha = navAlpha,
                        iconFillProgress = iconFill.value,
                        itemColor = colors.navBar.colorItem,
                        indicatorColor = colors.navBar.colorIndicator,
                        iconSize = iconSize,
                        labelSize = labelSize,
                        iconStrokes = iconStrokes,
                        iconImages = iconImages,
                        s = ::s,
                    )
                }
                }
            }
        }

        // 占位按钮的轻提示（设置 / 更新课表）：不接任何真实逻辑，只显示一句话。
        placeholderHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = s(120.dp))
                    .clip(RoundedCornerShape(s(10.dp)))
                    .background(Color(0xE60A140F))
                    .padding(horizontal = s(14.dp), vertical = s(8.dp)),
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    fontSize = (9f * uiScale).sp,
                    lineHeight = (12f * uiScale).sp,
                )
            }
        }

        // 星云球宿主（dim + 雷达波 + 球体 + 手势状态机）已搬到 shell/orb/OrbHost.kt。
        // 层级顺序 dim -> 雷达波 -> 球体 由 OrbHost 内部保证，不能调换。
        OrbHost(
            orbSize = orbSize,
            scale = orbScale * orbDissolveScale,
            alpha = orbAlpha * orbDissolveAlpha,
            dimAlpha = dimAlpha,
            pulse = pulse.value,
            pulseExpand = pulseExpand,
            mode = mode,
            onModeChange = { mode = it },
            onPulse = { expand ->
                pulseExpand = expand
                pulseKey++
            },
            enabled = barMode == BarMode.Normal,
        )

        // ---- 票 #60：拖时间条时的遮罩 ----
        // 位置必须在**星云球之后**：维护者要求遮板也要遮住星云球。
        // 时间条仍然声明在它之后 → 时间条与时间文字浮在遮罩之上，不被压暗。
        ModalScrim(visible = rulerDragging)

        // ---- 全局设置面板（**层级最高**：盖住星云球、时间条与一切页面元素）----
        //
        // 位置必须在 OrbHost / ModalScrim **之后**：Compose 同层 Box 里后声明者在上，
        // 维护者 2026-11-17 要求「设置高于星云球」，所以这块是全文件最后一个浮层。
        //
        // 打开：齿轮旋转 + 面板贴右边缘滑入，左侧留 10% 空白；
        // 面板滑到包含齿轮后，齿轮克隆体随面板飞到标题行；
        // 点遮板或右上角 X 都走反向动画退回。
        if (settingsOpen || settingsProgress.value > 0f) {
            SettingsPanel(
                content = SettingsBuilders.buildContent(
                    dayMode = settingsDayMode,
                    showWeekends = settingsShowWeekends,
                    quietRefresh = settingsQuietRefresh,
                ),
                uiScale = uiScale,
                gearSize = s(16.dp),
                gearCenterPx = gearCenterPx,
                gearRotation = settingsProgress.value * SETTINGS_GEAR_DEGREES,
                progress = settingsProgress.value,
                onGearTakenOverChange = { gearTakenOver = it },
                onDismiss = { settingsOpen = false },
                onEntryClick = { key ->
                    when (key) {
                        TimetableSettings.Keys.UpdateSchedule -> {
                            // 复用工具条「更新课表」的同一套逻辑：先关面板再刷新
                            settingsOpen = false
                            updateTimetableOnce()
                        }

                        TimetableSettings.Keys.WebVpnLogin -> {
                            // 手动调度 WebVPN 登录弹窗：先收起设置面板（弹窗在根节点最顶层，
                            // 面板动画期间同时显示会打架），再投递 MANUAL 触发源。
                            // MANUAL 是显式操作，绕过「不同意」的抑制。
                            settingsOpen = false
                            com.nongyu.appplus.webvpn.data.WebVpnDialogScheduler
                                .request(
                                    com.nongyu.appplus.webvpn.model.WebVpnTrigger.MANUAL,
                                )
                        }

                        else -> Unit
                    }
                },
                onChoiceSelected = { key, index ->
                    if (key == TimetableSettings.Keys.DayWeekView) {
                        settingsDayMode = index == 1
                    }
                },
            )
        }

        // ---- 时间条（页面级 overlay）----
        if (selectedTab == 1) {
            // 详见 docs/specs/界面层.md §8.5 D 位置必须用 TimetableGrid 上报的实测值
            val rulerTop = with(density) { periodTopPx.toDp() }
            val rulerHeight = with(density) { periodHeightPx.toDp() }
            // 课表几何映射：滑动时间 / 松手节次 / 流水位置全部走它，
            // 于是与右侧课程行严格对齐（密度不均匀：45 分钟一节课 vs 150 分钟午休）。
            val geometry = remember(rulerHeight) {
                com.nongyu.appplus.timetable.logic.TimetableGeometry
                    .build(totalHeightDp = rulerHeight.value)
            }
            TimetableRuler(
                uiScale = uiScale,
                boxWidthRatio = 1f,
                waterFraction = geometry.fractionAtTime(nowMinute),
                tiltDegrees = rememberWaterTilt(),
                onRelease = { fraction ->
                    val period = geometry.periodAtFraction(fraction)
                    val today = java.time.LocalDate.now()
                    addingSchedule = Triple(
                        today.dayOfWeek.value - 1,
                        period,
                        com.nongyu.appplus.timetable.data.weekOfTerm(today),
                    )
                },
                onDragStateChange = { rulerDragging = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = rulerTop)
                    .width(s(8.dp))
                    .height(if (rulerHeight.value > 0f) rulerHeight else 1.dp),
            )
        }

        // ---- 地点 / 周次 / 导航 三个子窗体 ----
        // 参数布线住在 shell/dialogs/CourseDialogs.kt，地点回落链只算一次。
        val dlgCourse = detailCourse
        val dlgClassDays = remember(dlgCourse?.id) {
            if (dlgCourse != null) classDaysOf(dlgCourse) else emptySet()
        }
        if (dlgCourse != null) {
            val dlgLocation = currentLocationOf(locDay, locGeneral, dlgCourse)
            if (showLocationEdit) {
                CourseLocationEditDialog(
                    course = dlgCourse,
                    currentLocation = dlgLocation,
                    uiScale = uiScale,
                    classDays = dlgClassDays,
                    onConfirm = { newLocation, days ->
                        coroutineScope.launch {
                            courseUserStore.setLocationForDays(dlgCourse.id, days, newLocation)
                            // 未选天时退回通用覆盖，保证至少有值生效
                            if (days.isEmpty()) {
                                courseUserStore.setLocationOverride(
                                    dlgCourse.id,
                                    LocationScope.Once,
                                    newLocation,
                                )
                            }
                        }
                    },
                    onDismiss = { showLocationEdit = false },
                )
            }
            if (showWeekEdit) {
                CourseWeekEditDialog(
                    course = dlgCourse,
                    uiScale = uiScale,
                    classDays = dlgClassDays,
                    initialExceptions = exceptionIsoDays.mapNotNull {
                        runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                    }.toSet(),
                    onConfirm = { days ->
                        exceptionIsoDays = days
                        coroutineScope.launch {
                            courseUserStore.setExceptionDays(dlgCourse.id, days)
                        }
                    },
                    onDismiss = { showWeekEdit = false },
                )
            }
            if (showLocationNav) {
                CourseLocationNavDialog(
                    course = dlgCourse,
                    location = dlgLocation,
                    uiScale = uiScale,
                    onDismiss = { showLocationNav = false },
                )
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
private fun MainShellPreview() {
    NongyuAppPlusTheme { MainShell() }
}
