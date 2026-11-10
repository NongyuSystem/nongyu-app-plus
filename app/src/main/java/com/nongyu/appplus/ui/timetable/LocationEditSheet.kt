package com.nongyu.appplus.ui.timetable

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
import com.nongyu.appplus.ui.theme.NongyuTheme
import java.time.LocalDate

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

/** 导航占位子窗体：mock 地点 + 地图示意框，不发请求。 */
@Composable
fun LocationNavDialog(
    courseName: String,
    location: String,
    uiScale: Float,
    onDismiss: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(18.dp)),
        ) {
            Text(
                text = "地点导航",
                color = colors.surface.colorText,
                fontSize = (13f * uiScale).sp,
                lineHeight = (16f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(3.dp)))
            Text(
                text = "$courseName · $location",
                color = colors.brand.colorBrand,
                fontSize = (9.5f * uiScale).sp,
                lineHeight = (12f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(s(12.dp)))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(s(120.dp))
                    .clip(RoundedCornerShape(s(12.dp)))
                    .background(colors.brand.colorBrandMuted.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "地图示意区",
                    color = colors.brand.colorBrand.copy(alpha = 0.7f),
                    fontSize = (9f * uiScale).sp,
                    lineHeight = (12f * uiScale).sp,
                )
            }
            Spacer(Modifier.height(s(8.dp)))
            Text(
                text = "后端地点服务与高德地图待接入（本期占位，不发请求）",
                color = colors.surface.colorTextSecondary.copy(alpha = 0.7f),
                fontSize = (8f * uiScale).sp,
                lineHeight = (11f * uiScale).sp,
            )
            Spacer(Modifier.height(s(12.dp)))
            DialogButton("关闭", uiScale, Modifier.fillMaxWidth(), onDismiss, primary = true)
        }
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
