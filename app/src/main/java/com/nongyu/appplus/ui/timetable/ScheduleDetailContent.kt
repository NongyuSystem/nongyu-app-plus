package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 日程展开卡（票 #14 的日程卡「开」态），**字段可直接编辑并即时保存**。
 *
 * 维护者口径（2026-11-11）：
 * - 只有开/关两态，没有课程详情卡那套三档；
 * - **卡片内部不退出**，只响应顶部滑动条；点卡片外才退出（由 MainShell 判断）；
 * - 日程内部要可以编辑 → 这里每个字段都是行内输入框，改动即回调 onUpdate。
 *
 * 为什么用「行内即时保存」而不是编辑按钮：
 * 六个字段如果都要点「编辑→改→保存」三步，在这么矮的卡片里操作负担太重；
 * 行内改动直接落库，观感上就是一份随时可改的备忘。
 */
@Composable
fun ScheduleDetailContent(
    entry: ScheduleEntry,
    uiScale: Float,
    onUpdate: (ScheduleEntry) -> Unit,
    onClose: () -> Unit,
    /** 下拉横杠：拖到底即关闭。 */
    onHandleDragDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    fun t(size: Float) = (size * uiScale).sp

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 卡片内部消费点击：不消费会穿透到「卡片外退出层」，点卡片内部就退出了
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) { }
            .padding(horizontal = s(18.dp), vertical = s(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- 可拖拽的横杠（命中区 56×18，视觉 40×4），与课程详情卡同一套 ----
        Box(
            modifier = Modifier
                .width(s(56.dp))
                .height(s(18.dp))
                .pointerInput(entry.id) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onVerticalDrag = { _, dy -> acc += dy },
                        onDragEnd = {
                            if (acc > 40.dp.toPx()) onHandleDragDown()
                        },
                        onDragCancel = { },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(s(40.dp))
                    .height(s(4.dp))
                    .clip(RoundedCornerShape(s(2.dp)))
                    .background(colors.surface.colorOutline.copy(alpha = 0.55f)),
            )
        }

        Spacer(Modifier.height(s(6.dp)))

        // 可编辑字段区（卡片矮，内容多了能滚）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(s(4.dp)),
        ) {
            // 标题：大一号，直接改
            EditField(
                value = entry.title,
                placeholder = "标题",
                uiScale = uiScale,
                fontSize = 13f,
                bold = true,
                onValueChange = { v ->
                    // 标题不能为空（ScheduleEntry 有 require）
                    if (v.isNotBlank()) onUpdate(entry.copy(title = v))
                },
            )

            val timeText = "${WEEKDAY_LABELS.getOrNull(entry.day) ?: ""} " +
                "第${entry.startPeriod}" +
                if (entry.endPeriod > entry.startPeriod) "-${entry.endPeriod} 节" else " 节"
            ReadOnlyRow("时间", timeText, uiScale)

            EditField(
                value = entry.location,
                placeholder = "地点（点此填写）",
                uiScale = uiScale,
                fontSize = 9f,
                label = "地点",
                onValueChange = { v -> onUpdate(entry.copy(location = v)) },
            )

            EditField(
                value = entry.content,
                placeholder = "内容（点此填写）",
                uiScale = uiScale,
                fontSize = 9f,
                label = "内容",
                singleLine = false,
                onValueChange = { v -> onUpdate(entry.copy(content = v)) },
            )
        }

        Spacer(Modifier.height(s(4.dp)))
        Text(
            text = "点卡片外收起 · 字段可直接修改",
            color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
            fontSize = t(7.5f),
            lineHeight = t(10f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 行内可编辑字段：点哪都能直接输入，没有「编辑」按钮。 */
@Composable
private fun EditField(
    value: String,
    placeholder: String,
    uiScale: Float,
    fontSize: Float,
    onValueChange: (String) -> Unit,
    label: String? = null,
    bold: Boolean = false,
    singleLine: Boolean = true,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.8f),
                fontSize = (7.5f * uiScale).sp,
                lineHeight = (9f * uiScale).sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(s(2.dp)))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(7.dp)))
                .background(colors.surface.colorBackground.copy(alpha = 0.7f))
                .padding(horizontal = s(7.dp), vertical = s(4.dp)),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.5f),
                    fontSize = (fontSize * uiScale).sp,
                    lineHeight = ((fontSize + 2.5f) * uiScale).sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = colors.surface.colorText,
                    fontSize = (fontSize * uiScale).sp,
                    fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
                ),
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = s(1.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s(6.dp)),
    ) {
        Text(
            text = label,
            color = colors.surface.colorTextSecondary.copy(alpha = 0.8f),
            fontSize = (7.5f * uiScale).sp,
            lineHeight = (9f * uiScale).sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = colors.brand.colorBrand,
            fontSize = (8.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
