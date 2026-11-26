package com.nongyu.appplus.timetable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nongyu.appplus.design.NongyuTheme
import java.time.LocalDate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

/**
 * 地点修改子窗体：地点值 + **日历选生效天**（与周次编辑共用 CourseCalendar）。
 *
 * 维护者 2026-11-10 拍板：地点与周次都先选「生效天」，再改值。
 * 原来的三档范围（只改这节/本课程/所有同名）取消，改为按天生效。
 *
 * @param classDays 需要上课的天（日历里标主题色），由调用方按固定周算好传入
 * @param initialSelected 初始选中的天
 * @param onConfirm 返回 (新地点, 选中的 ISO 日期集合)
 */
@Composable
fun LocationEditDialog(
    courseName: String,
    currentLocation: String,
    uiScale: Float,
    classDays: Set<LocalDate> = emptySet(),
    initialSelected: Set<LocalDate> = emptySet(),
    onConfirm: (newLocation: String, days: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    var text by remember { mutableStateOf(currentLocation) }
    var selected by remember { mutableStateOf(initialSelected) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(16.dp)),
        ) {
            Text(
                text = "修改上课地点",
                color = colors.surface.colorText,
                fontSize = (13f * uiScale).sp,
                lineHeight = (16f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(2.dp)))
            Text(
                text = courseName,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
                fontSize = (9f * uiScale).sp,
                lineHeight = (12f * uiScale).sp,
            )
            Spacer(Modifier.height(s(10.dp)))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(10.dp)))
                    .background(colors.surface.colorBackground)
                    .padding(horizontal = s(10.dp), vertical = s(8.dp)),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (10f * uiScale).sp,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(s(10.dp)))
            Text(
                text = "选择生效的天（可多选，主题色为需上课日）",
                color = colors.surface.colorTextSecondary,
                fontSize = (8.5f * uiScale).sp,
                lineHeight = (11f * uiScale).sp,
            )
            Spacer(Modifier.height(s(6.dp)))
            CourseCalendar(
                selectedDays = selected,
                onSelectionChange = { selected = it },
                uiScale = uiScale,
                classDays = classDays,
                multiSelect = true,
            )

            Spacer(Modifier.height(s(12.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
                DialogButton("取消", uiScale, Modifier.weight(1f), onDismiss)
                DialogButton(
                    "确定",
                    uiScale,
                    Modifier.weight(1f),
                    {
                        onConfirm(text, selected.map { it.toString() }.toSet())
                        onDismiss()
                    },
                    primary = true,
                )
            }
        }
    }
}

/**
 * 周次编辑子窗体（复杂态）：固定周 + 例外天（不上）。
 *
 * 语义：默认按固定周（课程原 weeks/weeksList）上课，
 * 在日历里点选的天是「不上」的例外；组件把选中的 ISO 日期集合回传。
 * 与地点编辑共用同一个 CourseCalendar，只是选中语义由调用方解释。
 */
@Composable
fun WeekEditDialog(
    courseName: String,
    weekSummary: String,
    uiScale: Float,
    /** 固定周上需要上课的天（标主题色）。 */
    classDays: Set<LocalDate> = emptySet(),
    /** 初始「不上」的例外天（ISO -> LocalDate）。 */
    initialExceptions: Set<LocalDate> = emptySet(),
    onConfirm: (exceptionIsoDays: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    var exceptions by remember { mutableStateOf(initialExceptions) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(16.dp)),
        ) {
            Text(
                text = "编辑周次",
                color = colors.surface.colorText,
                fontSize = (13f * uiScale).sp,
                lineHeight = (16f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(2.dp)))
            Text(
                text = "$courseName · 固定周上：$weekSummary",
                color = colors.brand.colorBrand,
                fontSize = (9f * uiScale).sp,
                lineHeight = (12f * uiScale).sp,
            )
            Spacer(Modifier.height(s(8.dp)))
            Text(
                text = "点选「哪天不上」（标主题色的日子是固定周上课日）",
                color = colors.surface.colorTextSecondary,
                fontSize = (8.5f * uiScale).sp,
                lineHeight = (11f * uiScale).sp,
            )
            Spacer(Modifier.height(s(6.dp)))
            CourseCalendar(
                selectedDays = exceptions,
                onSelectionChange = { exceptions = it },
                uiScale = uiScale,
                classDays = classDays,
                multiSelect = true,
            )
            Spacer(Modifier.height(s(12.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
                DialogButton("取消", uiScale, Modifier.weight(1f), onDismiss)
                DialogButton(
                    "确定",
                    uiScale,
                    Modifier.weight(1f),
                    {
                        onConfirm(exceptions.map { it.toString() }.toSet())
                        onDismiss()
                    },
                    primary = true,
                )
            }
        }
    }
}

/**
 * 自绘 SVG 风格「导航」箭头按钮（零依赖，不引图标库）。
 * 点击 -> 导航占位子窗体（本期不发请求、不引高德 SDK）。
 */
@Composable
fun LocationNavButton(
    uiScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val tint = colors.brand.colorBrand
    Box(
        modifier = modifier
            .size(s(20.dp))
            .clip(RoundedCornerShape(s(6.dp)))
            .background(colors.brand.colorBrandMuted.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(s(12.dp))) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.94f, h * 0.94f)
                lineTo(w * 0.5f, h * 0.68f)
                lineTo(w * 0.06f, h * 0.94f)
                close()
            }
            drawPath(p, color = tint)
        }
    }
}

/**
 * 地点导航子窗体：上「课程 + 地点」，中「地图」，下「跳转并导航」。
 *
 * ## 2026-11-26 按维护者要求重排
 *
 * 地图用 `map` 能力域的只读 [com.nongyu.appplus.map.ui.MapEditor]：
 * 底图缓存优先、标注由调用方数据决定（本地 DataStore），无高德 SDK。
 * 底部跳转用独立的 [com.nongyu.appplus.map.ui.openMapNavigation]（geo: URI）。
 *
 * 布局分三段：
 * 1. 顶部：课程名 + 教室地点（让用户确认看的是哪一节课）
 * 2. 中间：地图，占满剩余空间
 * 3. 底部：「跳转并导航」按钮；无坐标时提示而非崩
 */
@Composable
fun LocationNavDialog(
    courseName: String,
    location: String,
    uiScale: Float,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val campus = "yaan"

    // 标注：直接读原始文档 JSON 喂给只读地图，避免 encode/decode 往返
    val annotationStore = remember { com.nongyu.appplus.map.data.MapAnnotationStore(context) }
    var annotationsJson by remember(campus) { mutableStateOf<String?>(null) }
    LaunchedEffect(campus) {
        annotationsJson = annotationStore.documentJson(campus)
    }

    // 查一次后端坐标，仅供底部「跳转并导航」使用；查不到不影响地图浏览
    var externalLat by remember { mutableStateOf<Double?>(null) }
    var externalLng by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(location) {
        val r = com.nongyu.appplus.timetable.data.ClassroomRepository(context).lookup(location)
        if (r is com.nongyu.appplus.timetable.data.ClassroomRepository.LookupResult.Found) {
            externalLat = r.building.lat
            externalLng = r.building.lng
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 总高随地图一起收：地图由 weight 改为固定 200dp 后，
                // 对话框若仍留 520dp 会在底部多出一大片空白。
                // 340dp ≈ 顶部文字 40 + 间距 20 + 地图 200 + 间距 10 + 按钮 40 + padding 32
                .height((340f * uiScale).dp)
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(16.dp)),
        ) {
            // ① 顶部：课程 + 地点
            Text(
                text = courseName,
                color = colors.surface.colorText,
                fontSize = (12f * uiScale).sp,
                lineHeight = (15f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(2.dp)))
            Text(
                text = location,
                color = colors.brand.colorBrand,
                fontSize = (9.5f * uiScale).sp,
                lineHeight = (12f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(s(10.dp)))

            // ② 中间：地图
            //
            // 2026-11-26 维护者要求「高度缩小一半」：原来是 weight(1f) 吃掉剩余全部空间
            // （对话框 520dp 时约 400dp），这里改成固定 200dp。
            // 不用 weight(0.5f) 是因为 weight 按**剩余空间**分配比例，
            // 顶部文字一变高度地图就跟着变，固定值更可控。
            Box(
                modifier = Modifier
                    .height((200f * uiScale).dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(12.dp))),
            ) {
                com.nongyu.appplus.map.ui.MapEditor(
                    campus = campus,
                    annotationsJson = annotationsJson,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(s(10.dp)))

            // ③ 底部：跳转并导航
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(s(10.dp)))
                    .background(colors.brand.colorBrand)
                    .clickable {
                        val lat = externalLat
                        val lng = externalLng
                        if (lat == null || lng == null) {
                            com.nongyu.appplus.design.showTextToast(
                                context, "该地点暂无坐标，暂无法导航", uiScale,
                            )
                        } else {
                            val ok = com.nongyu.appplus.map.ui.openMapNavigation(
                                context, lat, lng, "$courseName · $location",
                            )
                            if (!ok) {
                                com.nongyu.appplus.design.showTextToast(
                                    context, "未找到可用的地图应用", uiScale,
                                )
                            }
                        }
                    }
                    .padding(vertical = s(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "跳转并导航",
                    color = colors.brand.colorOnBrand,
                    fontSize = (10f * uiScale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * 地图不可用时的文字降级：显示楼栋名 + 原因。
 *
 * 为什么不直接留黑屏：高德 Key 未配置 / 缺坐标时地图区是空白的，
 * 用户会以为是 App 坏了。这里至少告诉他"楼栋是哪一栋"以及"为什么没地图"。
 */
@Composable
private fun MapFallbackText(title: String, hint: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = (10f * uiScale).dp),
    ) {
        Text(
            text = title,
            color = colors.brand.colorBrand,
            fontSize = (10f * uiScale).sp,
            lineHeight = (13f * uiScale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height((3f * uiScale).dp))
        Text(
            text = hint,
            color = colors.surface.colorTextSecondary,
            fontSize = (7.5f * uiScale).sp,
            lineHeight = (10f * uiScale).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DialogButton(
    label: String,
    uiScale: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s(10.dp)))
            .background(
                if (primary) colors.brand.colorBrand
                else colors.surface.colorBackground,
            )
            .clickable(onClick = onClick)
            .padding(vertical = s(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) colors.brand.colorOnBrand else colors.surface.colorTextSecondary,
            fontSize = (9.5f * uiScale).sp,
            lineHeight = (12f * uiScale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
