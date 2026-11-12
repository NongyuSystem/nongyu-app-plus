package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.design.NongyuTheme

/**
 * 课表右上角四个按钮（票 #14）。
 *
 * 从左到右：设置 / 更新课表 / 切换日·周 / 添加日程。
 * 前两个是**占位**——点了只给一句轻提示，不接任何真实逻辑（票面验收要求
 * 「两个占位按钮不误触真实逻辑」）。
 *
 * 图标全部**纯几何自绘**（零依赖，不引图标库），与项目既有 `drawNavIcon` 同思路。
 * 四个按钮共用同一个尺寸/圆角/底色，看起来是一组工具条。
 */
@Composable
fun TimetableToolbar(
    uiScale: Float,
    dayMode: Boolean,
    onSettings: () -> Unit,
    onUpdateSchedule: () -> Unit,
    onToggleDayWeek: () -> Unit,
    onAddSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    val buttonSize = s(34.dp)
    val corner = s(10.dp)
    val gap = s(8.dp)
    val iconSize = s(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s(12.dp), vertical = s(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(buttonSize, corner, colors.surface.colorCard, uiScale, onSettings) {
            SettingsIcon(iconSize, colors.brand.colorBrand, uiScale)
        }
        ToolbarButton(buttonSize, corner, colors.surface.colorCard, uiScale, onUpdateSchedule) {
            UpdateIcon(iconSize, colors.brand.colorBrand, uiScale)
        }
        ToolbarButton(
            buttonSize,
            corner,
            if (dayMode) colors.brand.colorBrand else colors.surface.colorCard,
            uiScale,
            onToggleDayWeek,
        ) {
            DayWeekIcon(
                iconSize,
                if (dayMode) colors.brand.colorOnBrand else colors.brand.colorBrand,
                uiScale,
                dayMode,
            )
        }
        ToolbarButton(buttonSize, corner, colors.surface.colorCard, uiScale, onAddSchedule) {
            AddIcon(iconSize, colors.brand.colorBrand, uiScale)
        }
    }
}

@Composable
private fun ToolbarButton(
    size: Dp,
    corner: Dp,
    bg: Color,
    uiScale: Float,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** 齿轮：外圈 + 4 方向齿 + 中心孔。 */
@Composable
private fun SettingsIcon(size: Dp, tint: Color, uiScale: Float) {
    Canvas(modifier = Modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension * 0.30f
        val toothLen = this.size.minDimension * 0.16f
        val stroke = Stroke(width = this.size.minDimension * 0.11f, cap = StrokeCap.Round)
        drawCircle(tint, radius = r, center = c, style = stroke)
        // 四方向齿（上下左右各一小段）
        val dirs = listOf(
            Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f),
        )
        dirs.forEach { d ->
            drawLine(
                color = tint,
                start = c + d * r,
                end = c + d * (r + toothLen),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 更新课表：向下的箭头 + 底部托盘（下载语义）。 */
@Composable
private fun UpdateIcon(size: Dp, tint: Color, uiScale: Float) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.11f
        drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.62f), sw, StrokeCap.Round)
        // 箭头两撇
        drawLine(tint, Offset(w * 0.26f, h * 0.42f), Offset(w * 0.5f, h * 0.66f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.74f, h * 0.42f), Offset(w * 0.5f, h * 0.66f), sw, StrokeCap.Round)
        // 底部托盘
        drawLine(tint, Offset(w * 0.22f, h * 0.86f), Offset(w * 0.78f, h * 0.86f), sw, StrokeCap.Round)
    }
}

/** 切换日/周：日模式画 1 条粗条，周模式画 3 条细条（一眼看出当前是哪个）。 */
@Composable
private fun DayWeekIcon(size: Dp, tint: Color, uiScale: Float, dayMode: Boolean) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (dayMode) {
            // 一条粗横条 = 单日
            drawLine(
                color = tint,
                start = Offset(w * 0.2f, h * 0.5f),
                end = Offset(w * 0.8f, h * 0.5f),
                strokeWidth = w * 0.20f,
                cap = StrokeCap.Round,
            )
        } else {
            val sw = w * 0.11f
            listOf(0.28f, 0.5f, 0.72f).forEach { y ->
                drawLine(
                    color = tint,
                    start = Offset(w * 0.2f, h * y),
                    end = Offset(w * 0.8f, h * y),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 添加日程：加号。 */
@Composable
private fun AddIcon(size: Dp, tint: Color, uiScale: Float) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.13f
        drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), sw, StrokeCap.Round)
    }
}
