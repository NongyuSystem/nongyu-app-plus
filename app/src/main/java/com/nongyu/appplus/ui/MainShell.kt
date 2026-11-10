package com.nongyu.appplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.skin.LocalSkinResolution
import com.nongyu.appplus.skin.LocalSkinSource
import com.nongyu.appplus.skin.NavIconKey
import com.nongyu.appplus.ui.ai.NongyuOrb
import com.nongyu.appplus.ui.icon.NavIconGeometry
import com.nongyu.appplus.ui.icon.NavIconStrokes
import com.nongyu.appplus.ui.icon.drawNavIcon
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme
import com.nongyu.appplus.ui.theme.NongyuTheme
import com.nongyu.appplus.ui.timetable.CourseEntry

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

/**
 * 第 [period] 节是否已经结束（用于考勤默认态）。
 *
 * 取 COURSE_TIMES 里该节的**结束时刻**，与当前系统时间比较。
 * 纯判定、不依赖 Android/Compose，放在这里便于以后挪进数据层加单测。
 */
private fun isPeriodEnded(period: Int): Boolean {
    val end = com.nongyu.appplus.ui.timetable.COURSE_TIMES
        .getOrNull(period - 1)?.end ?: return false
    val parts = end.split(":")
    if (parts.size != 2) return false
    val endMin = (parts[0].toIntOrNull() ?: return false) * 60 +
        (parts[1].toIntOrNull() ?: return false)
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute >= endMin
}

private enum class OrbMode { Idle, Listening, Input }

/**
 * 底部条的三形态。三者**共用同一个容器**，靠高度/圆角/内容切换实现「原地变形」：
 * - [Normal]：常规导航栏（2+2 + 中间星云球）
 * - [Input]：输入态（导航项淡出，原地变输入栏）
 * - [Detail]：课程详情卡（导航项与星云球都溶解，拉高展示课程信息）
 */
private enum class BarMode { Normal, Input, Detail }

/**
 * 课程详情卡的两个形态（维护者 2026-11-10 要求）。
 * 拖动顶部横杠在两者之间吸附；继续下拉到底则退出卡片。
 */
private enum class DetailStage { Brief, Full }

@Composable
fun MainShell() {
    val colors = NongyuTheme.colors
    val context = LocalContext.current
    val courseUserStore = remember { com.nongyu.appplus.ui.timetable.CourseUserStore(context) }
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

    // ---- 详情卡的可修改数据（方案已批准）----
    // 地点覆盖 / 考勤 / 备注走 CourseUserStore（本地持久层）；
    // 评论走 CourseComments 的 mock 后端。
    val courseId = detailCourse?.id
    // 地点覆盖：Flow 用 remember 固定，避免每次重组都新建 Flow 导致订阅抖动。
    val locationFlow = remember(courseId) {
        if (courseId != null) {
            courseUserStore.locationOverride(
                courseId,
                com.nongyu.appplus.ui.timetable.LocationScope.Once,
            )
        } else null
    }
    val locationOverride by (locationFlow ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsState(initial = null)

    // 备注：**不用 collectAsState 直接喂给 TextField**。
    // 那样每次输入都要等 DataStore 回读，写入与回读会打架（实测重启后丢值）。
    // 正确做法：本地 mutableState 做 UI 真源，打开卡片时从持久层读一次，
    // 之后每次改动既更新本地、又异步落盘。
    var noteText by remember(courseId) { mutableStateOf("") }
    // true = 初始化读取已完成，之后的改动才允许落盘（避免把空串覆盖掉已存的值）。
    var noteReady by remember(courseId) { mutableStateOf(false) }
    LaunchedEffect(courseId) {
        if (courseId != null) {
            noteText = courseUserStore.noteOnce(courseId)
            noteReady = true
        } else {
            noteText = ""
            noteReady = false
        }
    }
    // 输入停止 250ms 后统一落盘：既不会每个字符都起一个写协程（竞态），
    // 也不会因为 Flow 回读把输入框内容拉回去。
    LaunchedEffect(noteText, courseId, noteReady) {
        if (noteReady && courseId != null) {
            delay(250)
            courseUserStore.setNote(courseId, noteText)
        }
    }

    // 考勤：period -> 是否已考勤。默认按「课程结束时刻 < 现在」判定已考勤；
    // 持久层里有显式记录时以记录为准（用户点过就听用户的）。
    var attendanceOverrides by remember(courseId) {
        mutableStateOf<Map<Int, Boolean>>(emptyMap())
    }
    val effectiveAttendance: Map<Int, Boolean> = remember(courseId, attendanceOverrides) {
        val c = detailCourse
        if (c == null) emptyMap()
        else (c.startPeriod..c.endPeriod).associateWith { p ->
            attendanceOverrides[p] ?: isPeriodEnded(p)
        }
    }

    // 评论（mock 后端，进程内可增删）
    var comments by remember(courseId) {
        mutableStateOf(
            if (courseId != null) {
                com.nongyu.appplus.ui.timetable.MockCourseCommentRepository.list(courseId)
            } else emptyList(),
        )
    }

    // 两个子窗体的开关
    var showLocationEdit by remember { mutableStateOf(false) }
    var showLocationNav by remember { mutableStateOf(false) }

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
        val uiScale = (maxWidth.value / 411f).coerceIn(0.85f, 1.25f)
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
        val barMode = when {
            detailCourse != null -> BarMode.Detail
            mode == OrbMode.Input -> BarMode.Input
            else -> BarMode.Normal
        }

        // 四个页面：目前只有「课表」有实体内容（地图 #11），其余仍为占位。
        Box(modifier = Modifier.fillMaxSize()) {
            // 【详情卡态】空白收起层放在**课表之下**。
            //
            // 维护者 2026-11-10 给的精确定义：
            //   空白区域 = 除「详情卡 / 课表上有的课程盒子 / 日程盒子」之外的所有区域。
            //
            // 为什么放底层：Compose 命中测试按「后声明的先命中」。
            // 课程盒子（课表覆盖层里、带 clickable）是更上层的命中节点，
            // 会先消费掉 down，事件就落不到这层 —— 点盒子不会退出。
            // 而课表的空白槽位没有任何 pointerInput，事件自然落到这里 —— 退出。
            //
            // 反过来（原来那版）把收起层盖在课表**之上**，
            // 它先命中并消费，点课程盒子也被强行收起 —— 正是这次要修的 bug。
            //
            // 日程盒子本期未渲染到网格（MOCK_SCHEDULES 留给 #18），
            // 将来接入时它自带 clickable，同样会被这层放行，无需改这里。
            if (barMode == BarMode.Detail) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { collapseDetail() },
                )
            }

            if (selectedTab == 1) {
                com.nongyu.appplus.ui.timetable.TimetableScreen(
                    navOccupiedPx = navOccupiedHeight,
                    onCourseClick = { detailCourse = it },
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

        // 详情卡两个形态的高度：简介 128dp / 详情 340dp（340 才装得下全部内容）。
        val briefHeight = s(128.dp)
        val fullHeight = s(340.dp)
        val detailHeight = when (detailStage) {
            DetailStage.Brief -> briefHeight
            DetailStage.Full -> fullHeight
        }
        val draggedDetailHeight = (detailHeight + detailDragDp.dp).coerceAtLeast(navHeight)

        // ---- 高度 / 圆角：全部由 Animatable + spring 驱动 ----
        //
        // 为什么不用 animateDpAsState：它每帧读一次 animationSpec，
        // 拖拽期用 tween(0) 跟手、松手那帧若 spec 仍是 0 就会瞬间跳变，
        // 表现为「上划/下降没有动画」。改用显式 Animatable：
        //   - 拖拽中：snapTo（零延迟，纯跟手）
        //   - 吸附 / 打开 / 退出收拢：animateTo(spring)（有弹性过渡）
        // 两者路径完全分开，不会互相吞掉。
        val density = LocalDensity.current
        val targetPx = with(density) {
            when {
                isCollapsing || barMode != BarMode.Detail -> navHeight.toPx()
                else -> draggedDetailHeight.toPx()
            }
        }
        val barHeightPx = remember { Animatable(with(density) { navHeight.toPx() }) }
        // key 必须包含 detailStage：上划吸附到 Full 时 barMode 仍是 Detail、
        // isCollapsing 仍是 false，若只盯这两个 key，effect 不会重跑，
        // 高度就停在简介态 —— 这正是「上划/下降没有动画」的另一半根因。
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
            val briefPx = briefHeight.toPx()
            val fullPx = fullHeight.toPx()
            ((barHeightPx.value - briefPx) / (fullPx - briefPx)).coerceIn(0f, 1f)
        }

        val barCorner by animateDpAsState(
            targetValue = when (barMode) {
                BarMode.Detail -> s(26.dp)
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
                BarMode.Detail -> {
                    val course = detailCourse
                    if (course != null) {
                        com.nongyu.appplus.ui.timetable.CourseDetailContent(
                            course = course,
                            uiScale = uiScale,
                            fullStage = detailStage == DetailStage.Full,
                            // 高度进度 0..1，内容层据此做「胶囊 -> K-V 移动」与
                            // 「评论/备注从盒底滑出」的分帧动画
                            expandProgress = expandProgress,
                            collapsing = isCollapsing,
                            otherSlots = com.nongyu.appplus.ui.timetable.MOCK_COURSES
                                .filter { it.name == course.name && it.id != course.id },
                            onDismiss = { detailCourse = null },
                            // 地点：显示覆盖值，没有覆盖就用原始 room
                            location = locationOverride ?: course.room,
                            attendance = effectiveAttendance,
                            onAttendanceToggle = { p ->
                                val next = !(effectiveAttendance[p] ?: false)
                                attendanceOverrides = attendanceOverrides + (p to next)
                                coroutineScope.launch {
                                    courseUserStore.setAttendance(course.id, p, next)
                                }
                            },
                            note = noteText,
                            onNoteChange = { text -> noteText = text },
                            comments = comments,
                            onCommentAdd = { text ->
                                com.nongyu.appplus.ui.timetable.MockCourseCommentRepository
                                    .add(course.id, text)
                                comments = com.nongyu.appplus.ui.timetable.MockCourseCommentRepository
                                    .list(course.id)
                            },
                            onCommentRemove = { i ->
                                com.nongyu.appplus.ui.timetable.MockCourseCommentRepository
                                    .remove(course.id, i)
                                comments = com.nongyu.appplus.ui.timetable.MockCourseCommentRepository
                                    .list(course.id)
                            },
                            onLocationEdit = { showLocationEdit = true },
                            onLocationNav = { showLocationNav = true },
                            onHandleDrag = { deltaDp ->
                                isDragging = true
                                detailDragDp += deltaDp
                            },
                            onHandleDragEnd = {
                                val briefDp = briefHeight.value
                                val fullDp = fullHeight.value
                                val base = when (detailStage) {
                                    DetailStage.Brief -> briefDp
                                    DetailStage.Full -> fullDp
                                }
                                val current = base + detailDragDp
                                when {
                                    current < briefDp * 0.55f -> {
                                        // 拖到底：不立刻清 detailCourse，否则 AnimatedContent
                                        // 当帧就把内容换成导航栏、收拢动画根本来不及跑。
                                        // 改为触发「收拢」：高度先弹回 navHeight，走完再切内容。
                                        isCollapsing = true
                                    }
                                    current > (briefDp + fullDp) / 2f ->
                                        detailStage = DetailStage.Full
                                    else -> detailStage = DetailStage.Brief
                                }
                                detailDragDp = 0f
                                isDragging = false
                            },
                            // 详情卡属于「不退出」白名单，但必须**消费掉点击**：
                            // 若这里没有任何 pointerInput，事件会穿透到内容区下方的收起层，
                            // 点卡片自己也会退出（实测 b3 那种情况）。
                            // 所以挂一个只消费、不动作的 clickable —— 白名单的正确实现。
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { /* 点详情卡：不退出 */ },
                        )
                    }
                }
                BarMode.Input -> {
                    // 输入态：导航栏**原地**变成输入栏，不再浮一个独立输入框。
                    // 这是维护者要求「点击星云球 → 导航按钮变淡转化为输入栏」。
                    //
                    // 自动聚焦：等切换动画走完再请求焦点。
                    // 直接 rememberFocusRequester + requestFocus() 在重组时调用会失败
                    // （此时输入框还没 attach 到窗口），必须放到 LaunchedEffect 里，
                    // 并延迟一点等 AnimatedContent 把内容挂上去，否则键盘不弹。
                    val focusRequester = remember { FocusRequester() }
                    val keyboard = LocalSoftwareKeyboardController.current
                    // 先等一帧让 AnimatedContent 真正把输入框挂上，
                    // 再等切换动画走完，最后才请求焦点并弹键盘。
                    // 只 delay 一次会在内容 attach 前就 requestFocus（静默失败，键盘不弹）。
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        delay(340)
                        runCatching {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = s(20.dp)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // 占位提示必须画在 EditText**之上或同层但不可交互**，
                        // 否则空输入时它会被 BasicTextField 自己的绘制区域盖住（实测看不到）。
                        // 这里用 Box 叠放：输入框在下，占位在上且不接收触摸。
                        Box(modifier = Modifier.fillMaxWidth()) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = TextStyle(
                                    color = colors.surface.colorText,
                                    fontSize = (12f * uiScale).sp,
                                ),
                                cursorBrush = SolidColor(colors.brand.colorBrand),
                                singleLine = true,
                            )
                            if (inputText.isEmpty()) {
                                // 占位文字**不能带 clickable**：clickable 会把手势整段消费掉，
                                // 下层 BasicTextField 永远收不到触摸，表现就是「点了打不了字」。
                                // 纯 Text 不参与手势，事件会自然穿透到下面的输入框。
                                Text(
                                    text = "输入想问农小屿的问题…",
                                    color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
                                    fontSize = (12f * uiScale).sp,
                                )
                            }
                        }
                    }
                }
                BarMode.Normal -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            // 导航项自己承担淡出：输入态/详情态时 navAlpha=0，
                            // 但它们住在同一个容器里，不能让容器整体透明。
                            .alpha(navAlpha),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavItem(TABS[0], NavIconKey.Grid, selectedTab == 0, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Grid), Modifier.weight(1f)) { selectedTab = 0 }
                        NavItem(TABS[1], NavIconKey.Calendar, selectedTab == 1, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Calendar), Modifier.weight(1f)) { selectedTab = 1 }
                        Spacer(modifier = Modifier.width(s(96.dp)))
                        NavItem(TABS[2], NavIconKey.Compass, selectedTab == 2, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Compass), Modifier.weight(1f)) { selectedTab = 2 }
                        NavItem(TABS[3], NavIconKey.Person, selectedTab == 3, colors.navBar.colorItem, colors.navBar.colorIndicator, iconSize, labelSize, iconStrokes.getValue(NavIconKey.Person), Modifier.weight(1f)) { selectedTab = 3 }
                    }
                }
                }
            }
        }

        // 长按态的全屏 dim：画在底栏之后，把整条导航栏也一起压暗
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(dimAlpha)
                    .background(Color(0xFF0A140F)),
            )
        }

        // 全屏雷达波：进入时向外扩散、退出时向内回收。
        // 画在 dim 之后、球体之前 —— 压在暗色后景上、位于球体下层。
        if (pulse.value > 0f && pulse.value < 1f) {
            val pv = pulse.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                // 圆心与球心保持一致：用与球 Box 相同的偏移比例
                val cy = size.height - (orbSize.value * 0.22f).dp.toPx() - (orbSize / 2).toPx()
                val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                val r0 = (orbSize / 2).toPx()              // 球体边缘
                // 浅绿波纹：暗底上不能用深品牌绿，否则显得很脏很沉。
                val ringColor = Color(0xFF7FD9A8)

                val r: Float
                val ringAlpha: Float
                if (pulseExpand) {
                    // 扩散：球 → 整屏，随扩散淡出
                    val ease = 1f - (1f - pv) * (1f - pv)  // easeOut，前段冲得快
                    r = r0 + ease * maxR
                    ringAlpha = (1f - pv) * 0.34f
                } else {
                    // 回收：整屏 → 球，越靠近球体越亮，末段快速收敛
                    val ease = pv * pv                     // easeIn，后段收得快
                    r = r0 + (1f - ease) * maxR
                    ringAlpha = pv * 0.34f
                }

                // 一圈亮环 + 一圈更宽更淡的伴随环，形成雷达波纹
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha * 0.40f),
                    radius = r * 0.86f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 9.dp.toPx()),
                )
            }
        }

        // 正中凸起的农小屿星云球（激活态为最高层）。
        // 手势状态机：按下 → 超过长按阈值进入 Listening（加速 + 放大 + 全屏 dim）；
        // 手指抬起 / 手势取消时**必定**复位到 Idle（onPress 的 tryAwaitRelease 兜底）。
        // 短按才切换 Input（扩开为输入框）。二者互斥，避免长按态卡死。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 凸起偏移量按球径比例：球小则凸起少，避免球浮在导航栏之上脱节
                .offset(y = (orbSize.value * -0.22f).dp)
                .size(orbSize)
                .scale(orbScale * orbDissolveScale)
                .alpha(orbAlpha * orbDissolveAlpha)
                // 不 clip(CircleShape)：星云球边缘在渲染层已羽化到透明，
                // 硬裁剪会把软边切成 3D 球的硬圆轮廓。
                // 星云球只在**常态**接收触摸。
                //
                // 否则球的手势区会盖在课程表底部（球是浮在内容之上的），
                // 在课程卡片上点按/长按会被判成星云球的操作（维护者反馈）。
                // 输入态/详情态时球本来就在溶解，也不需要交互。
                .then(
                    if (barMode == BarMode.Normal) {
                        Modifier.pointerInput(Unit) {
                            // longPressed 记录本轮手势是否已判为长按，避免长按后又误触发 onTap。
                            var longPressed = false
                            detectTapGestures(
                                onPress = {
                                    longPressed = false
                                    // 按下后等待释放；抬手 / 移出 / 手势取消都会返回。
                                    // 只有长按态需要「松手后恢复」，点按态(Input)要保留。
                                    tryAwaitRelease()
                                    if (longPressed) {
                                        mode = OrbMode.Idle
                                        pulseExpand = false   // 变亮：波向内回收
                                        pulseKey++
                                    }
                                },
                                onLongPress = {
                                    longPressed = true
                                    mode = OrbMode.Listening
                                    pulseExpand = true        // 变黑：波向外扩散
                                    pulseKey++
                                },
                                onTap = {
                                    if (!longPressed) {
                                        mode = if (mode == OrbMode.Input) OrbMode.Idle else OrbMode.Input
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            NongyuOrb(
                diameter = orbSize,
                speed = if (mode == OrbMode.Listening) 2.2f else 0.25f,
                // 激活态有全屏 dim，球边缘要消融进暗背景，不能留灰白亮边。
                dimmed = active,
            )
        }

        // ---- 地点修改 / 导航占位 两个子窗体 ----
        // 放在 MainShell 根 Box 的最后：Dialog 是独立 Window，
        // 但它读的 CourseEntry / location / uiScale 都在这里，就近接线最清楚。
        val dlgCourse = detailCourse
        if (showLocationEdit && dlgCourse != null) {
            com.nongyu.appplus.ui.timetable.LocationEditDialog(
                courseName = dlgCourse.name,
                currentLocation = locationOverride ?: dlgCourse.room,
                uiScale = uiScale,
                onConfirm = { scope, newLocation ->
                    coroutineScope.launch {
                        courseUserStore.setLocationOverride(dlgCourse.id, scope, newLocation)
                    }
                },
                onDismiss = { showLocationEdit = false },
            )
        }
        if (showLocationNav && dlgCourse != null) {
            com.nongyu.appplus.ui.timetable.LocationNavDialog(
                courseName = dlgCourse.name,
                location = locationOverride ?: dlgCourse.room,
                uiScale = uiScale,
                onDismiss = { showLocationNav = false },
            )
        }

    }
}

/**
 * 边界虚化 Modifier：在控件外侧画多层「逐圈外扩 + alpha 递减」的圆角描边，
 * 等效于把边界向外糊开约 6.dp，边界自然过渡到背景，没有生硬的 1.dp 线条。
 *
 * 注意：Compose 没有「背景毛玻璃 / backdrop blur」能力
 * （Modifier.blur / RenderEffect 模糊的是控件自身，不是背后内容，且 API<31 失效），
 * 所以这里用「多层柔边」做出虚化观感，而不是真正的背景模糊。
 */
private fun Modifier.featheredEdge(tint: Color, corner: Dp): Modifier = this.drawBehind {
    val cornerPx = corner.toPx()
    val layers = 7
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()               // 1.0(最外) .. 0.14(最内)
        val expand = t * 6.dp.toPx()               // 外扩距离
        val a = (1f - t) * 0.10f                   // 越外越淡
        drawRoundRect(
            color = tint.copy(alpha = a),
            topLeft = Offset(-expand, -expand * 0.7f),
            size = Size(size.width + expand * 2f, size.height + expand * 1.4f),
            cornerRadius = CornerRadius(cornerPx + expand, cornerPx + expand),
            style = Stroke(width = 1.2.dp.toPx() * (1f - t * 0.5f)),
        )
    }
}

/**
 * 单个导航项：图标 + 文案。
 * 尺寸：图标 26.dp（原 20.dp 偏小），文字 9.sp（原 10.sp 偏大）。
 * 选中态：图标与文字都用品牌色，未选中用低透明度中性色。
 */
@Composable
private fun NavItem(
    label: String,
    icon: NavIconKey,
    selected: Boolean,
    itemColor: Color,
    indicatorColor: Color,
    iconSize: Dp,
    labelSize: TextUnit,
    iconStroke: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) indicatorColor else itemColor
    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
        // 整体下移：导航栏本身是垂直居中，加一点顶部内边距把图标+文字压向下方。
        // 偏移量随图标尺寸等比，避免不同 uiScale 下偏得不一样。
        verticalArrangement = Arrangement.Center,
    ) {
        // 整体轻微下移；幅度取图标尺寸的 0.07（上一版 0.16 明显过头）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (iconSize.value * 0.07f).dp),
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                drawNavIcon(
                    geometry = NavIconGeometry.builtInOf(icon),
                    tint = tint,
                    strokeScale = iconStroke,
                )
            }
            // 图标与文字继续靠近：不留 Spacer，用压缩行高让文字紧贴图标下缘
            Text(
                text = label,
                color = tint,
                fontSize = labelSize,
                style = TextStyle(lineHeight = labelSize * 0.92f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainShellPreview() {
    NongyuAppPlusTheme { MainShell() }
}
