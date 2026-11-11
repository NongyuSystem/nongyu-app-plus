package com.nongyu.appplus.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuTheme
import java.time.LocalDateTime

/**
 * 「添加日程」表单（票 #14 / 需求 12，字段按 #16 决策的六字段）。
 *
 * **不再是 Dialog**：住进导航栏变形容器里（与点课开详情卡同一套动画），
 * 所以本组件只负责内容，外框（圆角/虚化/拉高动画）由 MainShell 负责。
 *
 * 六字段：标题 / 内容 / 地点 / 起止节次 / 周次 / 颜色。
 * 周次本期简化为「当前周」（周次多选的复杂态留给后续），颜色给 6 个色点。
 *
 * 内容区必须 `verticalScroll`：导航栏容器高度有限，六字段一定超出；
 * 而**保存按钮不能放进滚动区**（上次删除按钮就是这么被裁掉的），
 * 所以它固定在本组件底部、滚动区之外。
 */
@Composable
fun ScheduleFormContent(
    initialDay: Int,
    initialPeriod: Int,
    week: Int,
    uiScale: Float,
    onSave: (ScheduleEntry) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var startPeriod by remember { mutableIntStateOf(initialPeriod) }
    var spanPeriods by remember { mutableIntStateOf(1) }
    var colorIndex by remember { mutableIntStateOf(0) }
    // 星期本期沿用触发时的那天（工具条入口 = 今天，空白格入口 = 该列）
    val day = initialDay

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s(16.dp), vertical = s(2.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "添加日程",
            color = colors.surface.colorText,
            fontSize = (12f * uiScale).sp,
            lineHeight = (15f * uiScale).sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(s(2.dp)))
        Text(
            text = "${WEEKDAY_LABELS.getOrNull(day) ?: ""} · 第${week}周",
            color = colors.brand.colorBrand,
            fontSize = (8.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(s(6.dp)))

        // ---- 可滚动字段区 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(s(6.dp)),
        ) {
            // 1 标题
            FieldLabel("标题", uiScale)
            InputBox(uiScale) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (9.5f * uiScale).sp,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 2 内容
            FieldLabel("内容", uiScale)
            InputBox(uiScale) {
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (9.5f * uiScale).sp,
                    ),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 3 地点
            FieldLabel("地点", uiScale)
            InputBox(uiScale) {
                BasicTextField(
                    value = location,
                    onValueChange = { location = it },
                    textStyle = TextStyle(
                        color = colors.surface.colorText,
                        fontSize = (9.5f * uiScale).sp,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 4 起止节次
            FieldLabel("起止节次（第 $startPeriod 节起，共 $spanPeriods 节）", uiScale)
            Row(horizontalArrangement = Arrangement.spacedBy(s(5.dp))) {
                for (n in 1..4) {
                    ChoiceChip(
                        label = "$n 节",
                        active = spanPeriods == n,
                        uiScale = uiScale,
                    ) { spanPeriods = n }
                }
            }

            // 5 周次（本期：当前周 / 全周）
            FieldLabel("周次", uiScale)
            Row(horizontalArrangement = Arrangement.spacedBy(s(5.dp))) {
                ChoiceChip("第${week}周", true, uiScale) {}
                Text(
                    text = "（周次多选留给后续迭代）",
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.6f),
                    fontSize = (7.5f * uiScale).sp,
                    lineHeight = (10f * uiScale).sp,
                )
            }

            // 6 颜色
            FieldLabel("颜色", uiScale)
            Row(horizontalArrangement = Arrangement.spacedBy(s(6.dp))) {
                SCHEDULE_COLORS.forEachIndexed { idx, c ->
                    Box(
                        modifier = Modifier
                            .size(s(20.dp))
                            .clip(RoundedCornerShape(s(10.dp)))
                            .background(Color(c))
                            .then(
                                if (colorIndex == idx) {
                                    Modifier.border(s(2.dp), colors.brand.colorBrand, RoundedCornerShape(s(10.dp)))
                                } else Modifier,
                            )
                            .clickable { colorIndex = idx },
                    )
                }
            }
            Spacer(Modifier.height(s(2.dp)))
        }

        // ---- 保存/取消：固定在滚动区之外，避免被裁 ----
        Spacer(Modifier.height(s(6.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
            FormButton("取消", uiScale, Modifier.weight(1f), onCancel)
            FormButton(
                "保存",
                uiScale,
                Modifier.weight(1f),
                {
                    val now = LocalDateTime.now().toString()
                    val end = (startPeriod + spanPeriods - 1).coerceAtMost(PERIOD_COUNT)
                    onSave(
                        ScheduleEntry(
                            id = "sched-${System.currentTimeMillis()}",
                            studentId = MOCK_STUDENT_ID,
                            title = title.ifBlank { "新日程" },
                            content = content,
                            location = location,
                            day = day,
                            startPeriod = startPeriod,
                            endPeriod = end,
                            weeksList = listOf(week),
                            colorIndex = colorIndex,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                },
                primary = true,
            )
        }
    }
}

/** 日程可选颜色（6 个，与课程哈希色板区分开：这里是有序固定色）。 */
private val SCHEDULE_COLORS = listOf(
    0xFF0A7C59, // 品牌绿
    0xFF3F7FBF, // 蓝
    0xFFB06A2C, // 橙
    0xFF8A6BBE, // 紫
    0xFFB3455E, // 红
    0xFF5C7A3F, // 橄榄
)

@Composable
private fun FieldLabel(text: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Text(
        text = text,
        color = colors.surface.colorTextSecondary.copy(alpha = 0.85f),
        fontSize = (8f * uiScale).sp,
        lineHeight = (10f * uiScale).sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun InputBox(uiScale: Float, content: @Composable () -> Unit) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((8f * uiScale).dp))
            .background(colors.surface.colorBackground)
            .padding(horizontal = (8f * uiScale).dp, vertical = (5f * uiScale).dp),
    ) {
        content()
    }
}

@Composable
private fun ChoiceChip(label: String, active: Boolean, uiScale: Float, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape((7f * uiScale).dp))
            .background(if (active) colors.brand.colorBrand else colors.surface.colorBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = (9f * uiScale).dp, vertical = (4f * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) colors.brand.colorOnBrand else colors.surface.colorTextSecondary,
            fontSize = (8f * uiScale).sp,
            lineHeight = (10f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FormButton(
    label: String,
    uiScale: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val colors = NongyuTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape((9f * uiScale).dp))
            .background(if (primary) colors.brand.colorBrand else colors.surface.colorBackground)
            .clickable(onClick = onClick)
            .padding(vertical = (7f * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) colors.brand.colorOnBrand else colors.surface.colorTextSecondary,
            fontSize = (9f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
