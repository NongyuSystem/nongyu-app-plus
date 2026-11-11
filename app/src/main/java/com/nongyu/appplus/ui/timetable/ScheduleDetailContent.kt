package com.nongyu.appplus.ui.timetable

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 日程展开卡（票 #14 的日程卡「开」态）。
 *
 * 维护者口径：「只有开关两态，拉下去就是关了这个卡片」——
 * 所以这里**没有**详情卡的 Brief/Full/FullDelete 三档，
 * 只有「开着」这一种形态：下拉横杠拖到底、或点卡片外任意位置，都回到关闭态。
 */
@Composable
fun ScheduleDetailContent(
    entry: ScheduleEntry,
    uiScale: Float,
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

        Spacer(Modifier.height(s(8.dp)))

        // ---- 标题 ----
        Text(
            text = entry.title,
            color = colors.surface.colorText,
            fontSize = t(15f),
            lineHeight = t(18f),
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(s(8.dp)))

        // ---- 明细：时间 / 地点 / 内容 ----
        val timeText = "${WEEKDAY_LABELS.getOrNull(entry.day) ?: ""} " +
            "第${entry.startPeriod}" +
            if (entry.endPeriod > entry.startPeriod) "-${entry.endPeriod} 节" else " 节"
        DetailRow("时间", timeText, uiScale)
        if (entry.location.isNotBlank()) {
            DetailRow("地点", entry.location, uiScale)
        }
        if (entry.content.isNotBlank()) {
            DetailRow("内容", entry.content, uiScale)
        }

        Spacer(Modifier.height(s(6.dp)))
        Text(
            text = "点任意位置或下拉收起",
            color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
            fontSize = t(7.5f),
            lineHeight = t(10f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = s(2.dp)),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(s(8.dp)),
    ) {
        Text(
            text = label,
            color = colors.surface.colorTextSecondary,
            fontSize = (8.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(s(26.dp)),
        )
        Text(
            text = value,
            color = colors.surface.colorText,
            fontSize = (9f * uiScale).sp,
            lineHeight = (12f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
    }
}
