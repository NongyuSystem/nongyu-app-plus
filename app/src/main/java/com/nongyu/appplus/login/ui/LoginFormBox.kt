package com.nongyu.appplus.login.ui

import com.nongyu.appplus.login.model.QrEncoder
import com.nongyu.appplus.login.model.LocalCaptcha
import com.nongyu.appplus.login.model.LoginMethod
import com.nongyu.appplus.login.model.VerificationCodeButtonState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
    /** 外部持有的账密值（票 #92）：切认证域回来后输入框仍保留内容，不清空。 */
    savedAccount: String = "",
    savedPassword: String = "",
    /** 统一身份认证三格输入上报（票 #96）：key 为字段名，value 为用户输入。 */
    onFormValuesChanged: (Map<String, String>) -> Unit = {},
    /** 已取到的图形验证码 data URI（票 #96），用于在表单里渲染真图。 */
    captchaImage: String? = null,
    /** 点验证码图换一张。 */
    onRefreshCaptcha: () -> Unit = {},
    /** 短信验证码按钮状态（票 #89 文案状态机）。 */
    smsCodeState: VerificationCodeButtonState = VerificationCodeButtonState(),
    /** 邮箱验证码按钮状态。 */
    mailCodeState: VerificationCodeButtonState = VerificationCodeButtonState(),
    /** 点「获取验证码」发短信码（不是图形验证码）。 */
    onRequestSmsCode: () -> Unit = {},
    /** 点「获取验证码」发邮箱码。 */
    onRequestMailCode: () -> Unit = {},
    /** 邮箱格的本地图形验证码（前端生成、本地校验）。 */
    localCaptcha: LocalCaptcha = LocalCaptcha("----"),
    /** 点本地验证码图换一张。 */
    onRefreshLocalCaptcha: () -> Unit = {},
    /** 微信扫码面板状态与内容。 */
    wechatQrState: WechatQrState = WechatQrState.LOADING,
    wechatQrUrl: String? = null,
    /** 刷新微信二维码。 */
    onRefreshWechatQr: () -> Unit = {},
    /** 下载二维码并跳转微信。 */
    onDownloadAndOpenWechat: () -> Unit = {},
    /** 复制链接并跳转微信。 */
    onCopyAndOpenWechat: () -> Unit = {},
) {
    // 真机适配：所有尺寸按屏幕等比缩放（基准蓝叠 540×1200dp）
    val uiScale = rememberLoginScale()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32f.scaledDp(uiScale)),
        // 组件间隔拉开：原 12dp → 20dp
        verticalArrangement = Arrangement.spacedBy(20f.scaledDp(uiScale)),
    ) {


        when (method) {
            LoginMethod.PLATFORM_PASSWORD -> {
                PullIn(0, method) { FormTitle("平台内账号密码", colors) }
                // 票 #92：账密两格的值上报给 UI，供登录用例组装请求
                var studentNo by remember { mutableStateOf(savedAccount) }
                var password by remember { mutableStateOf(savedPassword) }
                PullIn(1, method) {
                    FormField("请输入账号", colors, value = studentNo) {
                        studentNo = it
                        onCredentialsChanged(studentNo, password)
                    }
                }
                PullIn(2, method) {
                    FormField("请输入密码", colors, isPassword = true, value = password) {
                        password = it
                        onCredentialsChanged(studentNo, password)
                    }
                }
            }

            LoginMethod.CAMPUS_PASSWORD -> {
                PullIn(0, method) { FormTitle("统一身份认证账密", colors) }
                val values = remember { mutableMapOf<String, String>() }
                fun put(k: String, v: String) {
                    values[k] = v
                    onFormValuesChanged(values.toMap())
                }
                PullIn(1, method) { FormField("请输入学号", colors, onValueChange = { put("pwStudentNo", it) }) }
                PullIn(2, method) { FormField("请输入密码", colors, isPassword = true, onValueChange = { put("pwPassword", it) }) }
                PullIn(3, method) { CaptchaRow("请输入验证码", colors, onValueChange = { put("pwCaptchaCode", it) }, captchaImage = captchaImage, onRefreshCaptcha = onRefreshCaptcha) }
            }

            LoginMethod.CAMPUS_PHONE -> {
                PullIn(0, method) { FormTitle("统一身份认证手机号", colors) }
                val values = remember { mutableMapOf<String, String>() }
                fun put(k: String, v: String) {
                    values[k] = v
                    onFormValuesChanged(values.toMap())
                }
                PullIn(1, method) { FormField("请输入手机号", colors, onValueChange = { put("smsPhone", it) }) }
                PullIn(2, method) { CaptchaRow("请输入图形验证码", colors, onValueChange = { put("smsCaptchaCode", it) }, captchaImage = captchaImage, onRefreshCaptcha = onRefreshCaptcha) }
                PullIn(3, method) {
                    FormFieldWithAction(
                        placeholder = "请输入短信验证码",
                        actionState = smsCodeState,
                        colors = colors,
                        onAction = onRequestSmsCode,
                        onValueChange = { put("smsCode", it) },
                    )
                }
            }

            LoginMethod.CAMPUS_WECHAT -> {
                PullIn(0, method) { FormTitle("统一身份认证微信登录", colors) }
                PullIn(1, method) { WechatQrPanel(
                    colors = colors,
                    qrState = wechatQrState,
                    qrUrl = wechatQrUrl,
                    statusText = when (wechatQrState) {
                        WechatQrState.LOADING -> "正在获取二维码…"
                        WechatQrState.READY -> "请使用微信扫一扫登录"
                        WechatQrState.FAILED -> "二维码已失效，请刷新"
                    },
                    onRefresh = onRefreshWechatQr,
                    onDownloadAndOpenWechat = onDownloadAndOpenWechat,
                    onCopyAndOpenWechat = onCopyAndOpenWechat,
                    )
                }
            }

            LoginMethod.PLATFORM_MAIL -> {
                PullIn(0, method) { FormTitle("邮箱验证码", colors) }
                val values = remember { mutableMapOf<String, String>() }
                fun put(k: String, v: String) {
                    values[k] = v
                    onFormValuesChanged(values.toMap())
                }
                PullIn(1, method) { FormField("请输入邮箱", colors, onValueChange = { put("email", it) }) }
                // 邮箱发码后端不校验图形码，这张图是**前端本地**生成并本地校验的
                // （交互约束，不是安全边界；防刷靠后端 Cooldown）
                PullIn(2, method) {
                    LocalCaptchaRow(
                        placeholder = "请输入图形验证码",
                        captcha = localCaptcha,
                        colors = colors,
                        onValueChange = { put("localCaptchaCode", it) },
                        onRefresh = onRefreshLocalCaptcha,
                    )
                }
                PullIn(3, method) {
                    FormFieldWithAction(
                        placeholder = "请输入邮箱验证码",
                        actionState = mailCodeState,
                        colors = colors,
                        onAction = onRequestMailCode,
                        onValueChange = { put("mailCode", it) },
                    )
                }
            }

            // oauth 本期禁用，不会进入表单
            LoginMethod.PLATFORM_OAUTH -> {
                PullIn(0, method) { FormTitle("暂未开放", colors) }
            }
        }
    }
}

/**
 * 眼睛图标：纯矢量路径绘制（等价 SVG），无矩形外框。
 *
 * - 睁眼：椭圆眼眶 + 实心瞳孔 + 上方高光
 * - 闭眼：眼眶改画一条下弯弧线 + 三根睫毛，表示"隐藏"
 */
@Composable
private fun EyeIcon(visible: Boolean, colors: NongyuColors) {
    val uiScale = rememberLoginScale()
    val tint = colors.surface.colorTextSecondary
    Canvas(modifier = Modifier.size(22f.scaledDp(uiScale))) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val rx = w * 0.40f
        val ry = h * 0.26f
        val stroke = 1.6f.scaledDp(uiScale).toPx()

        // 眼眶
        if (visible) {
            drawOval(
                color = tint,
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(width = stroke),
            )
            // 瞳孔
            drawCircle(color = tint, radius = h * 0.11f, center = Offset(cx, cy))
            // 高光
            drawCircle(
                color = colors.surface.colorCard,
                radius = h * 0.035f,
                center = Offset(cx - h * 0.035f, cy - h * 0.035f),
            )
        } else {
            // 闭眼：下弯弧线
            val path = Path().apply {
                moveTo(cx - rx, cy)
                quadraticBezierTo(cx, cy + ry * 1.6f, cx + rx, cy)
            }
            drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
            // 三根睫毛
            listOf(-0.6f, 0f, 0.6f).forEach { k ->
                val x = cx + rx * k
                drawLine(
                    color = tint,
                    start = Offset(x, cy + ry * 0.45f),
                    end = Offset(x + rx * 0.12f, cy + ry * 1.05f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 表单内第 [index] 个组件的「拉出式」入场包装（顶层函数，避免局部 @Composable 限制）。
 *
 * [method] 变化时整组重新入场；各组件按 index 错峰（每个 +60ms），
 * 从最上方 -120dp 处移动到最终位置，同时淡入。
 */
@Composable
private fun PullIn(index: Int, method: LoginMethod, content: @Composable () -> Unit) {
    PullInItem(index = index, key = method) { content() }
}

/**
 * 「拉出式」入场：组件从最上方偏移处一个一个移动到最终位置，配合淡入。
 *
 * @param index 该组件在列表中的序号，决定错峰延迟（每个 +60ms）
 * @param key 切换登录方式时变化的值，用于重新触发入场
 */
@Composable
private fun PullInItem(
    index: Int,
    key: Any?,
    content: @Composable () -> Unit,
) {
    val uiScale = rememberLoginScale()
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay((index * 60L).coerceAtMost(600L))
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "pullIn",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = (-120f.scaledDp(uiScale).toPx()) * (1f - progress)
            alpha = progress
        },
    ) {
        content()
    }
}

/** 表单标题。 */
@Composable
private fun FormTitle(text: String, colors: NongyuColors) {
    val uiScale = rememberLoginScale()
    Text(
        text = text,
        color = colors.surface.colorText,
        fontSize = 15f.scaledSp(uiScale),
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
    val uiScale = rememberLoginScale()
    var internalText by remember { mutableStateOf("") }
    val text = value ?: internalText
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val showPlaceholder = !focused && text.isEmpty()
    // 密码可见性：睁眼=明文，闭眼（默认）=星号
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48f.scaledDp(uiScale)),
    ) {
        // 底色 + 描边（矢量）
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = colors.surface.colorCard,
                cornerRadius = CornerRadius(12f.scaledDp(uiScale).toPx(), 12f.scaledDp(uiScale).toPx()),
            )
            drawRoundRect(
                color = colors.surface.colorOutline,
                cornerRadius = CornerRadius(12f.scaledDp(uiScale).toPx(), 12f.scaledDp(uiScale).toPx()),
                style = Stroke(width = 1f.scaledDp(uiScale).toPx()),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14f.scaledDp(uiScale),
                    end = if (isPassword) 44f.scaledDp(uiScale) else 14f.scaledDp(uiScale),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showPlaceholder) {
                Text(
                    text = placeholder,
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
                    fontSize = 13f.scaledSp(uiScale),
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
                    fontSize = 13f.scaledSp(uiScale),
                ),
                cursorBrush = SolidColor(colors.brand.colorBrand),
                visualTransformation = if (isPassword && !passwordVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 密码框尾部：纯图案眼睛按钮（无外框、无水波纹）
        if (isPassword) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40f.scaledDp(uiScale))
                    // 无 indication → 不产生水波纹
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { passwordVisible = !passwordVisible },
                contentAlignment = Alignment.Center,
            ) {
                EyeIcon(visible = passwordVisible, colors = colors)
            }
        }
    }
}

/**
 * 输入框 + 右侧「获取验证码」按钮（票 #89 文案状态机）。
 *
 * 用于**短信验证码**与**邮箱验证码**（不是图形验证码）：
 * 按钮贴在输入框右侧，文案与可用性由 [VerificationCodeButtonState] 驱动
 * （获取验证码 → 发送中… → 重新获取（Ns）→ 重新获取）。
 */
@Composable
private fun FormFieldWithAction(
    placeholder: String,
    actionState: VerificationCodeButtonState,
    colors: NongyuColors,
    onAction: () -> Unit,
    onValueChange: (String) -> Unit = {},
) {
    val uiScale = rememberLoginScale()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FormField(placeholder, colors, onValueChange = onValueChange)
        }
        Spacer(Modifier.width(12f.scaledDp(uiScale)))
        Box(
            modifier = Modifier
                .height(48f.scaledDp(uiScale))
                .widthIn(min = 104f.scaledDp(uiScale))
                .background(
                    if (actionState.enabled) colors.brand.colorBrandMuted
                    else colors.surface.colorCard,
                    RoundedCornerShape(12f.scaledDp(uiScale)),
                )
                // clip 放在 clickable 之前，让水波纹按圆角矩形边界扩散（否则是直角矩形）
                .clip(RoundedCornerShape(12f.scaledDp(uiScale)))
                .clickable(enabled = actionState.enabled) { onAction() }
                .padding(horizontal = 10f.scaledDp(uiScale)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionState.label,
                color = if (actionState.enabled) colors.brand.colorBrand
                        else colors.surface.colorTextSecondary.copy(alpha = 0.6f),
                fontSize = 12f.scaledSp(uiScale),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 邮箱格：输入框 + 本地绘制的图形验证码（前端生成，本地校验）。
 *
 * 与 [CaptchaRow]（学校侧真图）不同：这里的字符由 [LocalCaptcha] 现场生成，
 * 用 `Canvas` 矢量绘制并配干扰线，点击可换一张。
 */
@Composable
private fun LocalCaptchaRow(
    placeholder: String,
    captcha: LocalCaptcha,
    colors: NongyuColors,
    onValueChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val uiScale = rememberLoginScale()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FormField(placeholder, colors, onValueChange = onValueChange)
        }
        Spacer(Modifier.width(12f.scaledDp(uiScale)))
        Box(
            modifier = Modifier
                .size(width = 100f.scaledDp(uiScale), height = 48f.scaledDp(uiScale))
                .background(colors.brand.colorBrandMuted, RoundedCornerShape(12f.scaledDp(uiScale)))
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // 两条干扰线
                drawLine(
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.30f),
                    start = Offset(w * 0.08f, h * 0.72f),
                    end = Offset(w * 0.92f, h * 0.28f),
                    strokeWidth = 1.5f.scaledDp(uiScale).toPx(),
                )
                drawLine(
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.22f),
                    start = Offset(w * 0.12f, h * 0.30f),
                    end = Offset(w * 0.88f, h * 0.70f),
                    strokeWidth = 1.5f.scaledDp(uiScale).toPx(),
                )
            }
            Text(
                text = captcha.text,
                color = colors.brand.colorBrand,
                fontSize = 18f.scaledSp(uiScale),
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
    val uiScale = rememberLoginScale()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FormField(placeholder, colors, onValueChange = onValueChange)
        }
        Spacer(Modifier.width(12f.scaledDp(uiScale)))
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
    val uiScale = rememberLoginScale()
    val bitmap = remember(dataUri) {
        CaptchaImageDecoder.decode(dataUri)?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Box(
        modifier = Modifier
            .size(width = 100f.scaledDp(uiScale), height = 48f.scaledDp(uiScale))
            .background(colors.brand.colorBrandMuted, RoundedCornerShape(12f.scaledDp(uiScale)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "图形验证码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2f.scaledDp(uiScale)),
            )
        } else {
            Text(
                text = "验证码",
                color = colors.surface.colorTextSecondary,
                fontSize = 12f.scaledSp(uiScale),
            )
        }
    }
}

@Composable
private fun WechatQrPanel(
    colors: NongyuColors,
    qrState: WechatQrState,
    qrUrl: String?,
    statusText: String,
    onRefresh: () -> Unit,
    onDownloadAndOpenWechat: () -> Unit,
    onCopyAndOpenWechat: () -> Unit,
) {
    val uiScale = rememberLoginScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 二维码图片区
        Box(
            modifier = Modifier
                .size(200f.scaledDp(uiScale))
                .background(colors.surface.colorCard, RoundedCornerShape(14f.scaledDp(uiScale))),
            contentAlignment = Alignment.Center,
        ) {
            when (qrState) {
                WechatQrState.LOADING -> Text(
                    text = "正在获取二维码…",
                    color = colors.surface.colorTextSecondary,
                    fontSize = 13f.scaledSp(uiScale),
                )
                WechatQrState.READY -> {
                    val matrix = remember(qrUrl) { qrUrl?.let { QrEncoder.encode(it) } }
                    if (matrix != null) {
                        Canvas(modifier = Modifier.size(180f.scaledDp(uiScale))) {
                            val n = matrix.size
                            val cell = size.width / n
                            for (y in 0 until n) for (x in 0 until n) {
                                if (matrix[y][x]) {
                                    drawRect(
                                        color = colors.surface.colorText,
                                        topLeft = Offset(x * cell, y * cell),
                                        size = Size(cell, cell),
                                    )
                                }
                            }
                        }
                    } else {
                        Text("二维码生成失败", color = colors.surface.colorTextSecondary, fontSize = 12f.scaledSp(uiScale))
                    }
                }
                WechatQrState.FAILED -> Text(
                    text = "二维码已失效",
                    color = colors.surface.colorTextSecondary,
                    fontSize = 13f.scaledSp(uiScale),
                )
            }
        }

        Spacer(Modifier.height(10f.scaledDp(uiScale)))
        Text(
            text = statusText,
            color = colors.surface.colorTextSecondary,
            fontSize = 12f.scaledSp(uiScale),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12f.scaledDp(uiScale)))
        // 两个按钮：上下排布（按维护者要求），各占满一行
        PanelButton(
            text = "下载二维码并跳转微信",
            colors = colors,
            enabled = qrState == WechatQrState.READY,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDownloadAndOpenWechat,
        )
        Spacer(Modifier.height(10f.scaledDp(uiScale)))
        PanelButton(
            text = "复制链接并跳转微信",
            colors = colors,
            enabled = qrState == WechatQrState.READY,
            modifier = Modifier.fillMaxWidth(),
            onClick = onCopyAndOpenWechat,
        )

        Spacer(Modifier.height(8f.scaledDp(uiScale)))
        Text(
            text = "刷新二维码",
            color = colors.brand.colorBrand,
            fontSize = 12f.scaledSp(uiScale),
            modifier = Modifier.clickable { onRefresh() },
        )
    }
}

/** 微信二维码面板状态。 */
enum class WechatQrState { LOADING, READY, FAILED }

/** 面板内的等宽按钮。 */
@Composable
private fun PanelButton(
    text: String,
    colors: NongyuColors,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val uiScale = rememberLoginScale()
    Box(
        modifier = modifier
            .height(44f.scaledDp(uiScale))
            .background(
                if (enabled) colors.brand.colorBrandMuted else colors.surface.colorCard,
                RoundedCornerShape(10f.scaledDp(uiScale)),
            )
            .clip(RoundedCornerShape(10f.scaledDp(uiScale)))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6f.scaledDp(uiScale)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) colors.brand.colorBrand
                    else colors.surface.colorTextSecondary.copy(alpha = 0.5f),
            fontSize = 11f.scaledSp(uiScale),
            textAlign = TextAlign.Center,
        )
    }
}
