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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 地点修改子窗体（维护者要求：点地点值 → 弹出，选三档范围 + 改地点值）。
 *
 * 注意：SVG 导航按钮**不在这里**，它在课程卡片的地点值旁边（维护者明确纠正）。
 * 本子窗体只负责「改哪里」和「改成什么」。
 *
 * 本期的「改成什么」是自由文本输入；将来接后端获取地点列表时，
 * 把这个 TextField 换成下拉选择即可，范围选项不用动。
 */
@Composable
fun LocationEditDialog(
    courseName: String,
    currentLocation: String,
    uiScale: Float,
    onConfirm: (scope: LocationScope, newLocation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NongyuTheme.colors
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp
    var scope by remember { mutableStateOf(LocationScope.Once) }
    var text by remember { mutableStateOf(currentLocation) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s(20.dp)))
                .background(colors.surface.colorCard)
                .padding(s(18.dp)),
        ) {
            Text(
                text = "修改上课地点",
                color = colors.surface.colorText,
                fontSize = (13f * uiScale).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(3.dp)))
            Text(
                text = courseName,
                color = colors.surface.colorTextSecondary.copy(alpha = 0.75f),
                fontSize = (9f * uiScale).sp,
            )
            Spacer(Modifier.height(s(12.dp)))

            Text(
                text = "新地点",
                color = colors.surface.colorTextSecondary,
                fontSize = (8.5f * uiScale).sp,
            )
            Spacer(Modifier.height(s(4.dp)))
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

            Spacer(Modifier.height(s(14.dp)))
            Text(
                text = "修改范围",
                color = colors.surface.colorTextSecondary,
                fontSize = (8.5f * uiScale).sp,
            )
            Spacer(Modifier.height(s(6.dp)))

            LocationScope.entries.forEach { option ->
                val selected = scope == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(s(10.dp)))
                        .background(
                            if (selected) colors.brand.colorBrandMuted.copy(alpha = 0.7f)
                            else colors.surface.colorBackground,
                        )
                        .clickable { scope = option }
                        .padding(horizontal = s(10.dp), vertical = s(9.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(s(12.dp))
                            .clip(RoundedCornerShape(s(6.dp)))
                            .background(
                                if (selected) colors.brand.colorBrand
                                else colors.surface.colorOutline.copy(alpha = 0.5f),
                            ),
                    )
                    Spacer(Modifier.width(s(8.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            color = colors.surface.colorText,
                            fontSize = (9.5f * uiScale).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = option.subtitle,
                            color = colors.surface.colorTextSecondary.copy(alpha = 0.7f),
                            fontSize = (7.5f * uiScale).sp,
                        )
                    }
                }
                Spacer(Modifier.height(s(5.dp)))
            }

            Spacer(Modifier.height(s(10.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(s(8.dp))) {
                DialogButton("取消", uiScale, Modifier.weight(1f), onDismiss)
                DialogButton(
                    "确定",
                    uiScale,
                    Modifier.weight(1f),
                    { onConfirm(scope, text); onDismiss() },
                    primary = true,
                )
            }
        }
    }
}

/**
 * 自绘 SVG 风格「导航」箭头按钮（零依赖，不引图标库）。
 *
 * 维护者要求：地点值旁边放一个 SVG 导航按钮，点击请求后端获取地点 + 高德地图显示。
 * 本期按已批准的「占位」范围：只画矢量图标并弹占位子窗体，**不发真实请求、不引高德 SDK**。
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
            // 导航箭头（纸飞机/指针形状）：自绘 path，视觉上是矢量图标
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

/** 导航占位子窗体：mock 地点 + 地图示意框，不发请求（Out of scope 的网络层/高德留给后续 effort）。 */
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
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(s(3.dp)))
            Text(
                text = "$courseName · $location",
                color = colors.brand.colorBrand,
                fontSize = (9.5f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(s(12.dp)))
            // 地图示意区（本轮占位，不接高德）
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
                )
            }
            Spacer(Modifier.height(s(8.dp)))
            Text(
                text = "后端地点服务与高德地图待接入（本期占位，不发请求）",
                color = colors.surface.colorTextSecondary.copy(alpha = 0.7f),
                fontSize = (8f * uiScale).sp,
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
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private val LocationScope.title: String
    get() = when (this) {
        LocationScope.Once -> "只改这节课"
        LocationScope.Course -> "本课程所有节次"
        LocationScope.Name -> "所有同名课"
    }

private val LocationScope.subtitle: String
    get() = when (this) {
        LocationScope.Once -> "仅当前这一节课生效"
        LocationScope.Course -> "这门课的每一次上课都改"
        LocationScope.Name -> "课表里所有同名课程一起改"
    }
