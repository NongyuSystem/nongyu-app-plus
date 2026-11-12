package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * 山形放大峰值倍率（维护者 2026-11-09 拍板 2.2 倍）。
 */
private const val PEAK_SCALE = 2.2f

/**
 * 衰减半径：占整条高度的比例。
 *
 * 维护者拍板「上下约 120dp 内衰减」；课表时间轴高约 700dp，
 * 120 / 700 ≈ 0.17。用比例而非绝对值，面板高度变化时观感一致。
 */
private const val FALLOFF_FRACTION = 0.17f

/** 每 5 分钟一根刻度；每 10 分钟（整点/半点）加长。 */
private const val TICK_MINUTES = 5

/**
 * 左侧刻度时间条（票 #60 需求 1 / 2）。
 *
 * ## 为什么改造节次列而不是新增一条
 *
 * 维护者拍板：411dp 屏上 7 列已经很挤，再插一条会把课程列压得太窄。
 * 所以把原来 40dp 的节次列**改造成刻度条**：既有三行文字保留，
 * 在列的**右缘**画无数字刻度（需求 1「像尺子的刻度，但没有数字」）。
 *
 * ## 山形放大（需求 2）
 *
 * 竖直拖动时，手指处的刻度**横向伸长**：峰值 [PEAK_SCALE] 倍，
 * 上下 [FALLOFF_FRACTION] 内按余弦衰减回 1 倍。
 * 是「伸长」不是整体缩放 —— 每根刻度位置不动，只变长，
 * 连起来看就是一座山。
 *
 * ## 时间气泡
 *
 * 只显示**手指那一个**刻度对应的时刻（需求 2「只显示手指位置的」），
 * 画在刻度**右侧**（票面第 4 条「第一列一半的位置」）。
 *
 * ## 松手
 *
 * 回调 [onRelease] 传出手指比例，业务语义交给调用方。
 */
@Composable
fun TimetableRuler(
    uiScale: Float,
    /** 可见刻度宽度占整列的比例（与既有时间格一致，0.82）。 */
    boxWidthRatio: Float,
    /** 流水当前位置比例 0..1（与刻度共用 [TimetableAxis]）。 */
    waterFraction: Float,
    /** 水平仪倾斜角（透传给水面前沿）。 */
    tiltDegrees: Float,
    /** 松手：回调 (手指比例 0..1)。 */
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** 拖动状态上报：true = 正在拖时间条（供外层显示全局遮罩）。 */
    onDragStateChange: (Boolean) -> Unit = {},
    /** 手指位置比例上报（供外层在置顶层画时间文字）。null = 松手了。 */
    onFocusFractionChange: (Float?) -> Unit = {},
) {
    val colors = NongyuTheme.colors
    var focusFraction by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val fullHeight = maxHeight
        // 提前取出来：嵌套 Box 作用域里 maxWidth 的隐式接收者会冲突
        val fullWidth = maxWidth
        // 课表几何映射：按本控件真实高度构建，于是「比例」与右侧课程行严格对齐。
        // 只在换算（时间文字、松手节次）时用；刻度画法仍是等距的，不受影响。
        val geometry = remember(fullHeight) {
            TimetableGeometry.build(totalHeightDp = fullHeight.value)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            focusFraction = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            onDragStateChange(true)
                        },
                        onVerticalDrag = { change, _ ->
                            focusFraction =
                                (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            focusFraction?.let(onRelease)
                            focusFraction = null
                            onDragStateChange(false)
                        },
                        onDragCancel = {
                            focusFraction = null
                            onDragStateChange(false)
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val columnRight = size.width * boxWidthRatio
                val h = size.height
                val totalMin = TimetableAxis.totalMinutes
                val focus = focusFraction

                // **不画竖基准线**（维护者 2026-11-11 明确要求"不要竖线"）。
                // 只保留长短不一的横刻度，左端统一顶死屏幕最左边缘，
                // 靠长度差（长/短两档）形成尺子感。
                val railX = 0f

                // ---- 刻度横线：尺子节奏「4 短 1 长」----
                //
                // 维护者 2026-11-11 定形：像尺子那样 4 根短刻度 + 1 根长刻度循环。
                // 每 5 分钟一格，第 5 格（25 分钟整）为长刻度。
                //
                // 纵向范围：头部对齐第 1 节课开始时间（08:10），
                // 尾部对齐最后一节课结束时间（21:10），
                // 由调用方把高度裁到这一段（见 TimetableGrid 的 modifier）。
                val tickColor = colors.surface.colorText.copy(alpha = 0.32f)
                var index = 0
                var minute = 0
                while (minute <= totalMin) {
                    val frac = minute.toFloat() / totalMin.toFloat()
                    val y = h * frac
                    // 4 短 1 长：index % 5 == 4 为长刻度
                    val isLong = index % 5 == 4
                    val baseLen = if (isLong) columnRight * 0.95f else columnRight * 0.55f
                    val scale = if (focus == null) 1f else {
                        val d = (abs(frac - focus) / FALLOFF_FRACTION).coerceIn(0f, 1f)
                        1f + (PEAK_SCALE - 1f) * (0.5f + 0.5f * cos(d * PI.toFloat()))
                    }
                    val len = (baseLen * scale).coerceAtMost(columnRight)
                    val near = focus != null && abs(frac - focus) < 0.010f
                    drawLine(
                        color = if (near) colors.brand.colorBrand else tickColor,
                        start = Offset(0f, y),
                        end = Offset(len, y),
                        strokeWidth = if (isLong) 1.6.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    index++
                    minute += TICK_MINUTES
                }

                // 流水**不在这里画**：维护者改口径为「整屏宽横带、一直铺到屏幕底部、带渐变」，
                // 由外层全屏的 WaterFall 负责。刻度条只保留刻度本身。
            }

            // 时间文字：**画在刻度条内部**，用自身高度 × 手指比例定位。
            //
            // 为什么放回这里（之前放到 MainShell 根层是错的）：
            // 外层拿不到刻度条的坐标系，用 maxHeight*0.86 猜位置必然偏，
            // 且松手后要跨层清状态、容易清不掉（维护者实测反馈这两点）。
            // 在这里画：位置天然精确，松手 focusFraction=null 就自动消失。
            focusFraction?.let { f ->
                val label = formatHhMm(geometry.timeAtFraction(f))
                Box(
                    modifier = Modifier
                        // 关键：刻度条只有 8dp 宽，直接放文字会被父约束挤成一条竖线
                        //（实测「时间被压得扁扁的」）。用 wrapContentWidth(unbounded = true)
                        // 解除宽度约束，让它按内容自然撑开。
                        .wrapContentWidth(unbounded = true, align = Alignment.Start)
                        // 维护者要求：时间文字再往右一些、字号再大一点。
                        // x 在刻度量尺宽度基础上再让出 4dp 间距；字号 8.5sp → 11sp。
                        .offset(
                            x = (fullWidth * boxWidthRatio) + (4f * uiScale).dp,
                            y = fullHeight * f,
                        )
                        .clip(RoundedCornerShape((6f * uiScale).dp))
                        .background(colors.brand.colorBrand)
                        .padding(horizontal = (6f * uiScale).dp, vertical = (2.5f * uiScale).dp),
                ) {
                    Text(
                        text = label,
                        color = colors.brand.colorOnBrand,
                        fontSize = (11f * uiScale).sp,
                        lineHeight = (14f * uiScale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                // 同时上报给外层（遮罩用）
                onFocusFractionChange(f)
            }
            if (focusFraction == null) onFocusFractionChange(null)
        }
    }
}

/** "HH:mm" -> 当天分钟数（本文件内部用，避免多引一个依赖）。 */
private fun hourMinuteToMinutes(hhmm: String): Int {
    val parts = hhmm.split(":")
    if (parts.size != 2) return 0
    val h = parts[0].toIntOrNull() ?: return 0
    val m = parts[1].toIntOrNull() ?: return 0
    return h * 60 + m
}
