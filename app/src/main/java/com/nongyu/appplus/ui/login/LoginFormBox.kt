package com.nongyu.appplus.ui.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.ui.theme.NongyuColors

/**
 * 登录表单盒（票 #91）。
 *
 * 位于三个圆的下方，**常驻显示、自身无背景**（只渲染内容，容器不画底/描边）。
 * 按当前登录方式渲染对应表单：
 *
 * | 登录方式 | 表单内容 |
 * | --- | --- |
 * | 平台登录 · 账密 | 账号 + 密码 |
 * | 统一身份认证 · 账密 | 学号 + 密码 + 验证码（含图形验证码） |
 * | 统一身份认证 · 手机号 | 手机号 + 图形验证码 + 验证码 |
 * | 统一身份认证 · 微信 | 二维码图 + 「下载图片并跳转微信」按钮 |
 * | 平台登录 · 邮箱验证码 | 邮箱 + 图形验证码 + 邮箱验证码 |
 *
 * ## 输入框的「文字在内部」
 * 标签不再单独占一列，而是作为**占位文字（placeholder）画在输入框内部**：
 * 未聚焦且为空时显示「请输入账号」，聚焦或有内容后消失（[FormField] 内部处理）。
 */
@Composable
fun LoginFormBox(
    method: LoginMethod,
    colors: NongyuColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        // 组件间隔拉开：原 12dp → 20dp
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (method) {
            LoginMethod.PLATFORM_PASSWORD -> {
                FormTitle("平台内账号密码", colors)
                FormField("请输入账号", colors)
                FormField("请输入密码", colors, isPassword = true)
            }

            LoginMethod.CAMPUS_PASSWORD -> {
                FormTitle("统一身份认证账密", colors)
                FormField("请输入学号", colors)
                FormField("请输入密码", colors, isPassword = true)
                CaptchaRow("请输入验证码", colors)
            }

            LoginMethod.CAMPUS_PHONE -> {
                FormTitle("统一身份认证手机号", colors)
                FormField("请输入手机号", colors)
                CaptchaRow("请输入图形验证码", colors)
                FormField("请输入短信验证码", colors)
            }

            LoginMethod.CAMPUS_WECHAT -> {
                FormTitle("统一身份认证微信登录", colors)
                QrPlaceholder(colors)
                PrimaryWideButton("下载图片并跳转微信", colors)
            }

            LoginMethod.PLATFORM_MAIL -> {
                FormTitle("邮箱验证码", colors)
                FormField("请输入邮箱", colors)
                CaptchaRow("请输入图形验证码", colors)
                FormField("请输入邮箱验证码", colors)
            }

            // oauth 本期禁用，不会进入表单
            LoginMethod.PLATFORM_OAUTH -> {
                FormTitle("暂未开放", colors)
            }
        }
    }
}

/** 表单标题。 */
@Composable
private fun FormTitle(text: String, colors: NongyuColors) {
    Text(
        text = text,
        color = colors.surface.colorText,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 输入框：标签作为内部占位文字。
 *
 * 占位文字在「未聚焦且内容为空」时显示；一旦聚焦或有输入就消失。
 * 底色与描边用矢量绘制（`Canvas`），不依赖 Material 的 TextField 外观。
 */
@Composable
private fun FormField(
    placeholder: String,
    colors: NongyuColors,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val showPlaceholder = !focused && text.isEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        // 底色 + 描边（矢量）
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = colors.surface.colorCard,
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            )
            drawRoundRect(
                color = colors.surface.colorOutline,
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showPlaceholder) {
                Text(
                    text = placeholder,
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = colors.surface.colorText,
                    fontSize = 13.sp,
                ),
                cursorBrush = SolidColor(colors.brand.colorBrand),
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 带右侧图形验证码的输入框行。
 * 输入框占位文字同样在内部；右侧是「验证码图」占位（矢量纹理块）。
 */
@Composable
private fun CaptchaRow(
    placeholder: String,
    colors: NongyuColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FormField(placeholder, colors)
        }
        Spacer(Modifier.width(12.dp))
        CaptchaImage(colors)
    }
}

/** 图形验证码占位：淡绿底 + 斜线纹理（矢量），示意「验证码的图」。 */
@Composable
private fun CaptchaImage(colors: NongyuColors) {
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 48.dp)
            .background(colors.brand.colorBrandMuted, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawLine(
                color = colors.surface.colorTextSecondary.copy(alpha = 0.35f),
                start = Offset(w * 0.10f, h * 0.75f),
                end = Offset(w * 0.90f, h * 0.25f),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = colors.surface.colorTextSecondary.copy(alpha = 0.25f),
                start = Offset(w * 0.15f, h * 0.30f),
                end = Offset(w * 0.85f, h * 0.70f),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
        Text(
            text = "验证码",
            color = colors.surface.colorTextSecondary,
            fontSize = 12.sp,
        )
    }
}

/** 二维码占位（矢量方块网格），示意「二维码图」。 */
@Composable
private fun QrPlaceholder(colors: NongyuColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(colors.surface.colorCard, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val n = 8
            val cell = size.width / n
            // 三个角的定位块（finder pattern 示意）
            listOf(0 to 0, n - 2 to 0, 0 to n - 2).forEach { (cx, cy) ->
                drawRect(
                    color = colors.surface.colorText,
                    topLeft = Offset(cx * cell, cy * cell),
                    size = Size(cell * 2, cell * 2),
                )
            }
            // 数据块示意
            for (i in 2..5) {
                drawRect(
                    color = colors.surface.colorText.copy(alpha = 0.55f),
                    topLeft = Offset(i * cell, ((i % 4) + 2) * cell),
                    size = Size(cell, cell),
                )
            }
            drawRect(
                color = colors.surface.colorOutline,
                size = this.size,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** 整宽主按钮（矢量圆角矩形 + 文字）。 */
@Composable
private fun PrimaryWideButton(text: String, colors: NongyuColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.brand.colorBrand, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.brand.colorOnBrand,
            fontSize = 14.sp,
        )
    }
}
