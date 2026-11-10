package com.nongyu.appplus.ui.timetable

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nongyu.appplus.ui.theme.NongyuTheme
import java.time.LocalDateTime

/**
 * 「点击空白格 → 添加日程」子窗体（票 #18 第 6 项）。
 *
 * 维护者口径：**做成和课程卡片差不多的日程卡片**，不是加课程。
 * 字段按 #16 决策的第 6 条：标题 / 内容 / 地点 / 起止节次 / 周次 / 颜色，
 * 但本窗体的最小可用面只暴露「标题 / 地点 / 起止节次」，其余走默认值——
 * 周次与颜色在 #14（右上角 4 个按钮 + 添加日程动画）里再展开。
 *
 * @param day 空格所在的星期（0..6）
 * @param startPeriod 起始节次（1..10）
 * @param week 当前周（写进 generated entry 的 weeksList）
 */
@Composable
fun AddScheduleDialog(
    day: Int,
    startPeriod: Int,
    week: Int,
    uiScale: Float,
    onConfirm: (ScheduleEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var spanPeriods by remember { mutableStateOf(1) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(18.dp)),
        ) {
            Text(
                text = "添加日程",
                color = colors.surface.colorText,
                fontSize = (13f * uiScale).sp,
                lineHeight = (16f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(3.dp)))
            Text(
                text = "${WEEKDAY_LABELS.getOrNull(day) ?: ""} 第 $startPeriod 节",
                color = colors.brand.colorBrand,
                fontSize = (9.5f * uiScale).sp,
                lineHeight = (12f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(s(12.dp)))

            FieldLabel("标题", uiScale)
            InputBox(uiScale) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (10f * uiScale).sp,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(s(10.dp)))
            FieldLabel("地点", uiScale)
            InputBox(uiScale) {
                BasicTextField(
                    value = location,
                    onValueChange = { location = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (10f * uiScale).sp,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(s(10.dp)))
            FieldLabel("时长（节）", uiScale)
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                for (n in 1..4) {
                    val active = spanPeriods == n
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(s(8.dp)))
                            .background(
                                if (active) colors.brand.colorBrand
                                else colors.surface.colorBackground,
                            )
                            .clickable { spanPeriods = n }
                            .padding(horizontal = s(12.dp), vertical = s(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$n",
                            color = if (active) colors.brand.colorOnBrand
                            else colors.surface.colorTextSecondary,
                            fontSize = (9.5f * uiScale).sp,
                            lineHeight = (12f * uiScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(s(14.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
                DialogButton("取消", uiScale, Modifier.weight(1f), onDismiss)
                DialogButton(
                    "添加",
                    uiScale,
                    Modifier.weight(1f),
                    {
                        val end = (startPeriod + spanPeriods - 1).coerceAtMost(PERIOD_COUNT)
                        val now = LocalDateTime.now().toString()
                        onConfirm(
                            ScheduleEntry(
                                id = "sched-${System.currentTimeMillis()}",
                                studentId = MOCK_STUDENT_ID,
                                title = title.ifBlank { "新日程" },
                                content = "",
                                location = location,
                                day = day,
                                startPeriod = startPeriod,
                                endPeriod = end,
                                weeksList = listOf(week),
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        onDismiss()
                    },
                    primary = true,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Text(
        text = text,
        color = colors.surface.colorTextSecondary,
        fontSize = (8.5f * uiScale).sp,
        lineHeight = (11f * uiScale).sp,
    )
    Spacer(Modifier.height((4f * uiScale).dp))
}

@Composable
private fun InputBox(uiScale: Float, content: @Composable () -> Unit) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((10f * uiScale).dp))
            .background(colors.surface.colorBackground)
            .padding(horizontal = (10f * uiScale).dp, vertical = (8f * uiScale).dp),
    ) {
        content()
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape((10f * uiScale).dp))
            .background(if (primary) colors.brand.colorBrand else colors.surface.colorBackground)
            .clickable(onClick = onClick)
            .padding(vertical = (9f * uiScale).dp),
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
