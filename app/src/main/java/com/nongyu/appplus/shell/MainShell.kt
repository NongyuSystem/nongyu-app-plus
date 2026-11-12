package com.nongyu.appplus.shell

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
 * 主框架与导航壳（概念验证版 0.0.1test）。
 *
 * 依据 #5 已答结论 + #10 农小屿三态定义：
 * - 底栏贴底、2+2 分列（全部/课表 · 探索/个人），中间留空给正中凸起的 AI 圆。
 * - 四个页面全白留空；冷启动默认「全部」，用零依赖 rememberSaveable 记住当前 Tab。
 * - 长按圆球：黄绿流动瞬间加速 + 球体微放大 + 后景全屏 dim，激活态最高层。
 * - 点按圆球：扩开覆盖原导航栏 → 四按钮淡出 → 变形为输入框（无左侧圆点），
 *   退出（返回键 / 再次点按）反向动画。
 *
 * 每个导航按钮的差异化动画按用户要求推迟，本版不做。
 * 语音转文字 / 后端 agent / 网络层不在本原型范围（用户「之后的事后面会说」）。
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

    // 点哪门课 → 导航栏**原地变形**为课程详情卡（票 #16 决策）。
    //
    // 为什么状态放 Shell：导航栏是自绘的、在 Shell 里，详情卡要「占据导航栏位置」，
    // 所以选中态必须提升到 Shell，子页面只负责上报点击。
    // 这与既有「导航栏变形为标签胶囊 / 输入条」是同一套做法。
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

    // 备注：**不用 collectAsState 直接喂给 TextField**。
    // 那样每次输入都要等 DataStore 回读，写入与回读会打架（实测重启后丢值）。
    // 正确做法：本地 mutableState 做 UI 真源，打开卡片时从持久层读一次，
    // 之后每次改动既更新本地、又异步落盘。「该不该落盘」的判据住在
    // timetable/logic/CourseDetailRules.kt（shouldPersistNote / noteChanged），有单测。
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

    // 考勤：period -> 是否已考勤。默认按「课程结束时刻 < 现在」判定已考勤；
    // 持久层里有显式记录时以记录为准（用户点过就听用户的）。
    // 合并优先级住在 logic/mergeAttendance（有单测：显式记录必须压过时间判定）。
    var attendanceOverrides by remember(courseId) {
        mutableStateOf<Map<Int, Boolean>>(emptyMap())
    }
    val effectiveAttendance: Map<Int, Boolean> = remember(courseId, attendanceOverrides) {
        mergeAttendance(detailCourse, attendanceOverrides) { p -> isPeriodEnded(p) }
    }

    // 评论（mock 后端，进程内可增删）
    var comments by remember(courseId) {
        mutableStateOf(
            if (courseId != null) {
                com.nongyu.appplus.timetable.data.MockCourseCommentRepository.list(courseId)
            } else emptyList(),
        )
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
    // 松手后延时清除。key 只盯 rulerDragging：
    // 之前同时盯 rulerFocus 和 rulerDragging，两个状态在 onDragEnd 里连续写，
    // 可能出现「focus 已 null 但 dragging 仍 true」的一帧，导致清除分支不触发，
    // 时间文字就一直挂在屏幕上（实测反馈）。
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

    // 图标线宽来自**皮肤资源段**（票 #38 / ADR-0005 Q1=A）：逐资源回落内置默认。
    // 内置默认皮肤下这四个值都是 BuiltInSkin.BUILT_IN_ICON_STROKE（0.085f），
    // 与改造前 drawNavIcon 里的 `s * 0.085f` 完全一致 —— 因此视觉不变。
    // 注意：这里**不再调用** skin.resolve()。启动路径上的解析只发生一次
    // （MainActivity 在 setContent 前完成，ADR-0006），此处只读取已解析好的结果。
    // 曾在此处再 resolve 一次，导致冷启动多做一遍磁盘读 + 一遍日志。
    val skin = LocalSkinSource.current
    val resolution = LocalSkinResolution.current
    val iconStrokes = remember(skin, resolution) {
        NavIconStrokes.resolve(
            payload = resolution.resourcePayload(),
            builtIn = skin.builtIn.resources,
        )
    }

    // 雷达波：状态切换时的圆环反馈。
    // - 进入激活态（变黑）：从球心**向外扩散**并淡出
    // - 退出激活态（变亮）：从外侧**向内回收**到球体（呼应"能量收回"，不是再释放一次）
    // pulseKey 每次自增重跑动画；pulseExpand 标记本轮方向。
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

    // 全屏 dim —— **只在长按态（Listening）出现**。
    //
    // 输入态（Input）不再整屏压暗：输入栏本身就在底栏里，
    // 整屏压暗会把输入栏一起压灰（历史上输入栏浮在上层所以没暴露）。
    // 输入态需要的是「底栏可见 + 背后课表变暗」，后者由下面的 inputScrim 单独一层负责。
    // 只保留这一层：曾经在底栏内部用 drawWithContent 又补过一层 longPressDim，
    // 两层叠加导致过暗（维护者一直要求删掉，本次删除）。
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
        // 尺寸自适应：固定 dp 在不同 dp 宽度的设备上观感差异极大
        // （蓝叠 1920x1080@240dpi = 1280dp 宽，手机 1080x2400@420dpi ≈ 411dp 宽，
        //   同一组固定 dp 在大屏上显得很小、在小屏上显得很大）。
        // 这里以 411dp（典型手机）为基准，按屏宽缩放，并夹在合理区间内。
        // 上限压到 1.25：手机上 1.0，720dp 的平板/模拟器上约 1.25，
        // 避免大画布上整套 UI 被等比放大得过于笨重（之前 1.55 明显过大）。
        val uiScale = uiScaleFor(maxWidth.value)
        fun s(dp: Dp): Dp = (dp.value * uiScale).dp

        val navHeight = s(62.dp)
        val navPadding = s(18.dp)
        val navCorner = s(20.dp)
        // 球体也必须参与缩放，否则其他元素变大、球固定 120dp 会显得变小
        // 球体也要跟随导航栏一起缩：导航栏已从 74dp 降到 62dp，
        // 球若仍保持 120dp 会接近导航栏高度的 2 倍，视觉上明显过大。
        // 96dp 视觉上仍偏大（光晕外扩使观感大于几何尺寸），再收到 78dp。
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

        // 底栏占位高度（像素）：内容区底部必须让出的量（需求 15）。
        // 必须实测：底栏高度与内边距都乘了 uiScale，写死 dp 会随屏宽漂移。
        // 取的是**底栏节点自身高度**（含竖直 padding 的外框），不是顶边坐标 ——
        // 顶边还要再做坐标系换算，历史上两次都算错（一次少让出被遮、一次多让出留白过大）。
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
            // 【卡片外退出层】三种形态规则统一（维护者 2026-11-11 拍板）：
            //   课程卡 / 日程卡 / 添加日程：点「卡片之外」退出，点「卡片之内」不退出。
            //
            // 实现要点：这层是**全屏**的，但声明位置在**底栏之前**。
            // Compose 命中测试「后声明的先命中」，而卡片本体就住在底栏里
            // （底栏声明在上面几行之后），所以：
            //   - 落在卡片上的点击 → 底栏先命中，这层收不到 → 卡片内部不退出 ✅
            //   - 落在其余区域     → 只有这层能接 → 退出 ✅
            // 之前两版都不对：放整屏最顶（底栏之后）会连卡片内部一起吃掉；
            // 干脆不放则卡片外无处接收点击（点了没反应）。
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
                    deletedIds = deletedIds,
                    schedules = addedSchedules,
                    onCourseClick = { course ->
                        applyIntent(resolveCourseClick(openCard, course))
                    },
                    onBlankSlotAdd = { day, period, week ->
                        applyIntent(resolveBlankSlotClick(openCard, day, period, week))
                    },
                    onSettings = { placeholderHint = "设置：本期占位，未实现" },
                    onUpdateSchedule = { placeholderHint = "更新课表：本期占位，未实现" },
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

        // 全屏 dim：**必须画在底栏之前**。
        //
        // 历史上它画在底栏之后（为了"把导航栏一起压暗"），那时输入框是浮在 dim 之上的
        // 独立控件，所以没问题。现在输入态与详情态都住进底栏本体，
        // 若仍画在底栏之后，整块底栏会被这层近乎不透明的遮罩完全盖住 ——
        // 表现就是「点星云球后什么都没出现」（实测屏幕变成整片深灰）。
        // 输入态的压暗层：同样画在底栏**之前**，于是它压暗课表、不压底栏。
        // 维护者要求「长按时导航栏要变暗」——长按那层（dimAlpha）已经画在底栏之后，
        // 会把底栏一起压暗；这层只服务输入态，两者互不干扰。
        if (inputScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(inputScrimAlpha)
                    .background(Color(0xFF0A140F)),
            )
        }

        // ---------- 底部：导航栏 ⇄ 课程详情卡（原地变形）----------
        //
        // 票 #16 决策：**导航栏原地拉高成详情卡**，中间星云球淡出。
        // 不跟旧农屿的 BottomSheet、也不跟它的全屏宽——这一条是明文采纳需求原文的。
        //
        // 实现要点（与既有「导航栏变形为标签胶囊」同一套）：
        // - 容器高度 `animateDpAsState` 在「栏高」与「详情卡高」之间插值，
        //   所以是**真的拉高**，不是淡入一个新卡片；
        // - 两种形态用 `AnimatedContent` 交叉淡入，避免内容跳变；
        // - 圆角、虚化边界、背景色两种形态共用，视觉上才像同一个东西变形。
        // barMode 已提前到「四个页面」之前定义（内容区的空白点击分层需要它）。
        // ---- 星云球「溶解 / 汇聚」动画（维护者要求）----
        //
        // 方向语义：
        // - 导航栏进入变形（Input / Detail）时，球**溶解**：粒子外散 + 透明度渐隐；
        // - 回到常态时，球**汇聚**：从四周收拢回球心并显形。
        //
        // 用 Animatable 而不是 animateFloatAsState，是因为要区分「正向/反向」
        // 两个不同时长（溶解略快、汇聚略慢，收拢感更自然）。
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

        // ---- 高度 / 圆角：全部由 Animatable + spring 驱动 ----
        //
        // 为什么不用 animateDpAsState：它每帧读一次 animationSpec，
        // 拖拽期用 tween(0) 跟手、松手那帧若 spec 仍是 0 就会瞬间跳变，
        // 表现为「上划/下降没有动画」。改用显式 Animatable：
        //   - 拖拽中：snapTo（零延迟，纯跟手）
        //   - 吸附 / 打开 / 退出收拢：animateTo(spring)（有弹性过渡）
        // 两者路径完全分开，不会互相吞掉。
        val density = LocalDensity.current
        val targetPx = with(density) { targetDetailHeight.toPx() }
        val barHeightPx = remember { Animatable(with(density) { navHeight.toPx() }) }
        // key 必须包含 detailStage：上划吸附到 Full 时 barMode 仍是 Detail、
        // isCollapsing 仍是 false，若只盯这两个 key，effect 不会重跑，
        // 高度就停在简介态 —— 这正是「上划/下降没有动画」的另一半根因。
        //
        // 第 2 步解耦：旧 key 里还有 addingSchedule、scheduleDetailId，
        // 但这两者的信息已由 barMode 承载（见 deriveBarMode），属冗余 key，
        // 会让同一变化触发多条重跑路径。现固定为 4 个真正的输入。
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

        // 形态进度 0..1：**在 briefHeight -> fullHeight 这段归一化**。
        // 用 navHeight 做分母会让简介态就有约 0.24 的进度，
        // 导致胶囊半透明、K-V 提前浮现（两种形态糊在一起）。
        // 这里让简介态 = 0（胶囊满、K-V 无），详情态 = 1（胶囊无、K-V 满）。
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
                // 这里**不能**用 .alpha(navAlpha)：
                // navAlpha 的语义是「导航项该不该显示」，它若作用在整个容器上，
                // 会把输入态 / 详情态的内容一起干掉。
                // 历史上输入框浮在这层之外所以没暴露，现在它住进容器就立刻被隐藏。
                // 正确做法：容器始终不透明，只让下面的导航项自己淡出（见 BarMode.Normal 分支）。
                .featheredEdge(colors.navBar.colorItem, barCorner)
                .clip(RoundedCornerShape(barCorner))
                .background(colors.navBar.colorContainer)
                // 卡片形态下，底栏容器本身**消费点击**：容器就是卡片本体，
                // 落在它上面的点击必须停在这里，不能穿透到底下的「卡片外退出层」，
                // 否则点卡片内部也会退出（维护者明确要求「卡片内部不退出」）。
                //
                // 放这里而不是放在各个卡片内容里：卡片内容高度会随形态变化，
                // 内容之外的容器留白同样是「卡片内部」，也得消费掉。
                // 横杠等子控件的 pointerInput 在更内层，命中测试先给子节点，拖拽不受影响。
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
                // 上报底栏占位（供内容页做底部避让）。
                //
                // 为什么不再上报「顶边 y 坐标」：
                // 之前用 positionInRoot / boundsInRoot 取顶边，再在内容页里做坐标换算，
                // 两次都出现了偏差（一次少让出、一次多让出），因为 padding 与子树坐标
                // 叠加后极易算错，且不同机型表现不一致。
                // 改为直接上报**底栏自身在外层 Box 中的高度**：
                // 它就是「内容区底部必须让出的像素数」，无需任何坐标换算。
                .onGloballyPositioned { coords ->
                    // 只在**常态、且高度动画已经稳定**时上报占位高度。
                    //
                    // 两个条件缺一不可：
                    // 1. barMode == Normal：点开详情卡（62->128dp）时不该推动课表
                    //    （弹出版挤压，之前已修）。
                    // 2. barHeight == navHeight：退出详情卡时 barMode **立刻**回到 Normal，
                    //    但 barHeight 还在 128->62dp 的 340ms 动画途中。
                    //    若此时无条件上报，会逐帧把中间高度传给课表，
                    //    整张课表跟着一帧一帧改大小 —— 就是「退回时瞬间变大小、弹一下」。
                    //    等动画落回 navHeight 再上报，值与此前一致，不会触发重组。
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
                            attendance = effectiveAttendance,
                            note = noteText,
                            comments = comments,
                            onTeacherChange = { v ->
                                coroutineScope.launch {
                                    courseUserStore.setTeacherOverride(course.id, v)
                                }
                            },
                            onAttendanceToggle = { p ->
                                attendanceOverrides = toggledAttendance(effectiveAttendance, p)
                                val next = attendanceOverrides[p] ?: false
                                coroutineScope.launch {
                                    courseUserStore.setAttendance(course.id, p, next)
                                }
                            },
                            onNoteChange = { text -> noteText = text },
                            onCommentChanged = {
                                comments = com.nongyu.appplus.timetable.data
                                    .MockCourseCommentRepository.list(course.id)
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

        // ---- 时间条（页面级 overlay）----
        if (selectedTab == 1) {
            // 位置与高度**全部用实测值**（TimetableGrid 上报的节次行范围）。
            // 手算公式已连续错两次（漏底栏占位、漏工具条预留），
            // 而这段区域还受状态栏、表头、各种 padding 影响，永远算不全。
            // 实测值天然包含所有偏移，所以这里只做一次 dp 换算。
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
