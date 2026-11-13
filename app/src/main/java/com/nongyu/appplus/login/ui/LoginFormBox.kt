package com.nongyu.appplus.login.ui

import com.nongyu.appplus.login.model.LoginMethod
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.nongyu.appplus.login.data.cas.CaptchaImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.nongyu.appplus.design.NongyuColors

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
    /** 平台登录账密输入变化上报（票 #92）；其它方式不上报。 */
    onCredentialsChanged: (studentNo: String, password: String) -> Unit = { _, _ -> },
    /** 就地行内提示文案（票 #92 验收判据）；null 表示不显示。 */
    inlineMessage: String? = null,
    /** 外部持有的账密值（票 #92）：切认证域回来后输入框仍保留内容，不清空。 */
    savedAccount: String = "",
    savedPassword: String = "",
    /** 统一身份认证三格输入上报（票 #96）：key 为字段名，value 为用户输入。 */
    onFormValuesChanged: (Map<String, String>) -> Unit = {},
    /** 已取到的图形验证码 data URI（票 #96），用于在表单里渲染真图。 */
    captchaImage: String? = null,
    /** 点验证码图换一张。 */
    onRefreshCaptcha: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        // 组件间隔拉开：原 12dp → 20dp
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 就地行内提示：紧跟表单标题，不弹窗、不清空输入（#89 文案表 / 票 #92）
        if (!inlineMessage.isNullOrBlank()) {
            Text(
                text = inlineMessage,
                color = colors.brand.colorBrand,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (method) {
            LoginMethod.PLATFORM_PASSWORD -> {
                FormTitle("平台内账号密码", colors)
                // 票 #92：账密两格的值上报给 UI，供登录用例组装请求
                var studentNo by remember { mutableStateOf(savedAccount) }
                var password by remember { mutableStateOf(savedPassword) }
                FormField("请输入账号", colors, value = studentNo) {
                    studentNo = it
                    onCredentialsChanged(studentNo, password)
                }
                FormField("请输入密码", colors, isPassword = true, value = password) {
                    password = it
                    onCredentialsChanged(studentNo, password)
                }
            }

            LoginMethod.CAMPUS_PASSWORD -> {
                FormTitle("统一身份认证账密", colors)
                val values = remember { mutableMapOf<String, String>() }
                fun put(k: String, v: String) {
                    values[k] = v
                    onFormValuesChanged(values.toMap())
                }
                FormField("请输入学号", colors, onValueChange = { put("studentNo", it) })
                FormField("请输入密码", colors, isPassword = true, onValueChange = { put("password", it) })
                CaptchaRow("请输入验证码", colors, onValueChange = { put("captchaCode", it) }, captchaImage = captchaImage, onRefreshCaptcha = onRefreshCaptcha)
            }

            LoginMethod.CAMPUS_PHONE -> {
                FormTitle("统一身份认证手机号", colors)
                val values = remember { mutableMapOf<String, String>() }
                fun put(k: String, v: String) {
                    values[k] = v
                    onFormValuesChanged(values.toMap())
                }
                FormField("请输入手机号", colors, onValueChange = { put("phone", it) })
                CaptchaRow("请输入图形验证码", colors, onValueChange = { put("captchaCode", it) }, captchaImage = captchaImage, onRefreshCaptcha = onRefreshCaptcha)
                FormField("请输入短信验证码", colors, onValueChange = { put("smsCode", it) })
            }

            LoginMethod.CAMPUS_WECHAT -> {
                FormTitle("统一身份认证微信登录", colors)
                WechatQrPanel(colors)
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
    value: String? = null,
    onValueChange: (String) -> Unit = {},
) {
    var internalText by remember { mutableStateOf("") }
    val text = value ?: internalText
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
                onValueChange = {
                    if (value == null) internalText = it
                    onValueChange(it)
                },
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
    onValueChange: (String) -> Unit = {},
    /** 已取到的图形验证码 data URI（票 #96）；null 时显示占位。 */
    captchaImage: String? = null,
    /** 点验证码图换一张（看图一次即作废，填错要能刷新）。 */
    onRefreshCaptcha: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FormField(placeholder, colors, onValueChange = onValueChange)
        }
        Spacer(Modifier.width(12.dp))
        CaptchaImage(colors, captchaImage, onRefreshCaptcha)
    }
}

/**
 * 图形验证码图（票 #96 缺口补齐）。
 *
 * 有 [dataUri] 时把 S6 解出的 `data:image/png;base64,...` 解码成 Bitmap 真实渲染；
 * 没有（尚未取码 / 解码失败）时显示淡绿占位块。
 */
@Composable
private fun CaptchaImage(colors: NongyuColors, dataUri: String?, onClick: () -> Unit = {}) {
    val bitmap = remember(dataUri) {
        CaptchaImageDecoder.decode(dataUri)?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 48.dp)
            .background(colors.brand.colorBrandMuted, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图形验证码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        } else {
            Text(
                text = "验证码",
                color = colors.surface.colorTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WechatQrPanel(colors: NongyuColors) {
    // 微信扫码的二维码由学校平台服务端下发一次性 state，App 无法自拼；
    // 扫码完成感知机制属未实测项，这里**不渲染不可扫的假码**误导用户，
    // 只如实告知暂不可用。接入后替换本面板。
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(colors.surface.colorCard, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "扫码登录暂不可用",
                color = colors.surface.colorTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
