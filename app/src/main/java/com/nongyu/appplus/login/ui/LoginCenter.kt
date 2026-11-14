package com.nongyu.appplus.login.ui

import kotlinx.coroutines.launch
import com.nongyu.appplus.login.model.VerificationCodeButtonState
import com.nongyu.appplus.login.session.MailCodeLoginUseCase
import com.nongyu.appplus.login.session.MailLoginResult
import com.nongyu.appplus.login.session.MailCodeSender
import com.nongyu.appplus.login.session.MailCodeSendResult
import com.nongyu.appplus.login.data.cas.CasQrFetchResult
import com.nongyu.appplus.login.data.cas.CasQrPollResult
import com.nongyu.appplus.login.session.CampusSsoLoginUseCase
import com.nongyu.appplus.login.session.SchoolLoginResult
import com.nongyu.appplus.login.session.PasswordLoginUseCase
import com.nongyu.appplus.login.session.DeviceIdStore
import com.nongyu.appplus.login.model.LoginFailurePolicy
import com.nongyu.appplus.login.model.LoginFailureAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.nongyu.appplus.login.ui.NebulaBackdrop
import com.nongyu.appplus.login.ui.LoginFormBox
import com.nongyu.appplus.login.model.ContactValidator
import com.nongyu.appplus.login.model.LocalCaptcha
import com.nongyu.appplus.login.model.LoginMethod
import com.nongyu.appplus.login.model.AuthRealm
import com.nongyu.appplus.login.model.LoginCenterUiState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.design.NongyuColors
import com.nongyu.appplus.design.NongyuTheme

/**
 * 登录中心（票 #91）—— 圆形登录方式切换器。
 *
 * ## 交互
 * - **未点击**：只有中间一个圆，显示**当前登录方式**（绿底白图标）。
 * - **点击中间圆**：左右各弹出一个圆，分别是**另外两种登录方式**（淡绿底灰图标），
 *   同时中间圆切换为**当前登录渠道**（点它即切换「统一身份认证 ⇄ 平台登录」）。
 * - **点击左右圆**：把那个方式设为当前登录方式，并收起。
 * - **再点中间圆**：收起（回到只显示当前登录方式）。
 *
 * ## 图标
 * 三个图标全部 [DrawScope] 矢量绘制（等价 SVG path），不引图片/SVG 库：
 * - 账密：卡片 + 锁
 * - 验证码：手机 / 信封
 * - 微信：气泡对话
 * - OAuth：圆环链
 * - 渠道：双向箭头
 *
 * ## 配色（全部取自 [NongyuTheme.colors]）
 * - 中间圆：`brand.colorBrand` 底 + `brand.colorOnBrand` 图标（绿底白）
 * - 左右圆：`brand.colorBrandMuted` 底 + `surface.colorTextSecondary` 图标（淡绿底灰）
 */
@Composable
fun LoginCenterScreen() {
    val colors = NongyuTheme.colors
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(LoginCenterUiState.initial()) }
    var expanded by remember { mutableStateOf(false) }
    // 底部提示文案（点击任意圆时更新，空串表示不显示）
    var hint by remember { mutableStateOf("") }
    // 提示的自增序号，用于「同样文案再次点击」也能重新计时
    var hintTick by remember { mutableStateOf(0) }
    // 平台登录账密输入（票 #92）：表单通过回调上报，UI 持有用于提交
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    // 提示统一走底部 hint（维护者要求：不在表单内单独显示）。
    // null/空白 表示清空提示，统一在这里收敛，避免每处都写 ifBlank。
    fun showHint(msg: String?) {
        hint = msg?.takeIf { it.isNotBlank() } ?: ""
        hintTick++
    }
    // 统一身份认证三格的表单输入（票 #96）
    var campusValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // 已取到的图形验证码（一次性，取码与提交必须同一 uuid）
    var campusCaptcha by remember { mutableStateOf<com.nongyu.appplus.login.data.cas.CasCaptcha?>(null) }
    // 缓存编排器，避免每次点击都重建 OkHttp/Retrofit
    val campusUseCase = remember { CampusSsoLoginUseCase.production(appContext) }
    val mailCodeSender = remember { MailCodeSender.production(appContext) }
    val mailLoginUseCase = remember { MailCodeLoginUseCase.production(appContext) }
    // 「获取验证码」按钮状态（短信 / 邮箱各一份）
    var smsCodeState by remember { mutableStateOf(VerificationCodeButtonState()) }
    var mailCodeState by remember { mutableStateOf(VerificationCodeButtonState()) }
    // 邮箱格的本地图形验证码（前端生成、本地校验）
    var localCaptcha by remember { mutableStateOf(LocalCaptcha.random()) }
    // 微信扫码：二维码状态、URL、state、以及轮询开关
    var wechatQrState by remember { mutableStateOf(WechatQrState.LOADING) }
    var wechatQrUrl by remember { mutableStateOf<String?>(null) }
    var wechatQrStateToken by remember { mutableStateOf<String?>(null) }
    var wechatPollActive by remember { mutableStateOf(false) }
    var wechatPollFailCount by remember { mutableStateOf(0) }

    // 切换到微信格时自动取码
    LaunchedEffect(uiState.selectedMethod) {
        if (uiState.selectedMethod == LoginMethod.CAMPUS_WECHAT && wechatQrUrl == null) {
            wechatQrState = WechatQrState.LOADING
            when (val r = campusUseCase.fetchLoginQr()) {
                is CasQrFetchResult.Success -> {
                    wechatQrUrl = r.qr.url
                    wechatQrStateToken = r.qr.state
                    wechatQrState = WechatQrState.READY
                    wechatPollActive = true
                }
                is CasQrFetchResult.Failure -> {
                    wechatQrState = WechatQrState.FAILED
                    if (r.message.isNotBlank()) showHint(r.message)
                }
            }
        }
    }

    // 轮询扫码结果（每 1.5 秒）
    LaunchedEffect(wechatPollActive, wechatQrStateToken) {
        val token = wechatQrStateToken
        if (!wechatPollActive || token == null) return@LaunchedEffect
        while (wechatPollActive) {
            kotlinx.coroutines.delay(1_500)
            when (val r = campusUseCase.pollQrResult(token)) {
                is CasQrPollResult.Scanned -> {
                    // 命中即彻底停止：不能只置 false 又让别的分支重启取码
                    wechatPollActive = false
                    wechatQrStateToken = null
                    submitting = true
                    android.util.Log.d("NongyuWechatQr", "扫码完成，提交 method=school")
                    // 提交放在 NonCancellable 里：登录成功后根节点会切到 MAIN，
                    // 本 Composable 离开组合会取消 LaunchedEffect 协程；若提交被半途取消，
                    // 凭证可能没写进去，且 CancellationException 会被误判成"登录失败"。
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            val res = campusUseCase.submitCredential(
                                r.credential, DeviceIdStore.getOrCreate(appContext),
                            )
                            when (res) {
                                is SchoolLoginResult.Success -> {
                                    android.util.Log.d("NongyuWechatQr", "method=school 成功，应已切主界面")
                                    // 不调用 showHint：此刻界面可能已开始切走，state 写入无意义
                                }
                                is SchoolLoginResult.Failure -> {
                                    val failure = res.failure
                                    val code = (failure as? com.nongyu.appplus.net.core.ApiFailure.Http)?.code
                                    val msg = LoginFailurePolicy.messageOf(code)
                                        ?: res.message.takeIf { it.isNotBlank() }
                                        ?: when (failure) {
                                            is com.nongyu.appplus.net.core.ApiFailure.Timeout -> "登录超时，请重新扫码"
                                            is com.nongyu.appplus.net.core.ApiFailure.Network -> "网络不可用，请重新扫码"
                                            else -> "登录失败，请重新扫码"
                                        }
                                    android.util.Log.d("NongyuWechatQr", "method=school 失败 code=$code msg=$msg")
                                    showHint(msg)
                                    wechatQrState = WechatQrState.FAILED
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 组合离开导致的正常取消：不是失败，绝不写 FAILED / 错误提示
                        android.util.Log.d("NongyuWechatQr", "组合离开，提交协程正常取消")
                    } catch (e: Exception) {
                        android.util.Log.w("NongyuWechatQr", "提交异常", e)
                        showHint("登录失败，请重新扫码")
                        wechatQrState = WechatQrState.FAILED
                    } finally {
                        submitting = false
                    }
                    return@LaunchedEffect   // 命中后退出轮询循环，别再取新码
                }
                is CasQrPollResult.Pending -> wechatPollFailCount = 0
                is CasQrPollResult.Failure -> {
                    // 瞬时抖动允许重试；但持续失败要停轮询并让用户刷新，
                    // 否则界面永远停在"请使用微信扫一扫登录"。
                    wechatPollFailCount++
                    if (wechatPollFailCount >= 5) {
                        wechatPollActive = false
                        wechatQrState = WechatQrState.FAILED
                        showHint(r.message.ifBlank { "二维码已失效，请刷新" })
                    } else if (r.message.isNotBlank()) {
                        showHint(r.message)
                    }
                }
            }
        }
    }

    // 倒计时：每秒推进
    LaunchedEffect(smsCodeState.remainingSeconds) {
        if (smsCodeState.remainingSeconds > 0) {
            kotlinx.coroutines.delay(1_000)
            smsCodeState = smsCodeState.tick()
        }
    }
    LaunchedEffect(mailCodeState.remainingSeconds) {
        if (mailCodeState.remainingSeconds > 0) {
            kotlinx.coroutines.delay(1_000)
            mailCodeState = mailCodeState.tick()
        }
    }

    // 进入统一身份认证账密/短信两格时自动取一张图形验证码（一次性，展示给用户）
    LaunchedEffect(uiState.selectedMethod) {
        val needsCaptcha = uiState.selectedMethod == LoginMethod.CAMPUS_PASSWORD ||
            uiState.selectedMethod == LoginMethod.CAMPUS_PHONE
        if (needsCaptcha && campusCaptcha == null) {
            when (val r = campusUseCase.fetchCaptcha()) {
                is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Success ->
                    campusCaptcha = r.captcha
                is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Failure ->
                    if (r.message.isNotBlank()) showHint(r.message);
            }
        }
    }

    val mainSize = 73.dp
    val sideSize = 70.dp
    val gap = 16.dp
    /** 从屏幕顶部算的圆心位置：0.25 = 从底部往上 3/4。 */
    val heightRatio = 0.25f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground),
    ) {
        // ---- 背景：大面积流动星云光晕（铺满整屏，位于所有内容最底层）----
        NebulaBackdrop(modifier = Modifier.fillMaxSize())

        val screenH = maxHeight
        val screenW = maxWidth
        val centerY: Dp = screenH * heightRatio

        // ---- 品牌标题：放大加粗的「农屿 X」----
        Text(
            text = "农屿 X",
            color = colors.surface.colorText,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 32.dp, top = 64.dp),
        )

        val progress by animateFloatAsState(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "orbitProgress",
        )

        val travel: Dp = mainSize / 2 + gap + sideSize / 2

        // 当前方式的索引：用它把「另外两种」分到左右两侧
        val currentIndex = uiState.methods
            .indexOf(uiState.selectedMethod)
            .coerceAtLeast(0)
        val others = uiState.methods.filterIndexed { i, _ -> i != currentIndex }
        val leftMethod = others.getOrNull(0)
        val rightMethod = others.getOrNull(1)

        // ---- 左右两个圆：另外两种登录方式 ----
        if (leftMethod != null) {
            OrbitingCircle(
                colors = colors,
                size = sideSize,
                progress = progress,
                cx = screenW / 2 - travel * progress,
                cy = centerY,
                method = leftMethod,
                isCenter = false,
                onClick = {
                    uiState = uiState.onMethodSelected(leftMethod)
                    expanded = false
                    hint = "已选择" + LoginCenterUiState.labelOf(leftMethod)
                    hintTick++
                },
            )
        }
        if (rightMethod != null) {
            OrbitingCircle(
                colors = colors,
                size = sideSize,
                progress = progress,
                cx = screenW / 2 + travel * progress,
                cy = centerY,
                method = rightMethod,
                isCenter = false,
                onClick = {
                    uiState = uiState.onMethodSelected(rightMethod)
                    expanded = false
                    hint = "已选择" + LoginCenterUiState.labelOf(rightMethod)
                    hintTick++
                },
            )
        }

        // ---- 中间圆 ----
        // 未展开：显示当前登录方式；展开后：显示当前登录渠道（点击切换）
        CenterCircle(
            colors = colors,
            size = mainSize,
            cx = screenW / 2,
            cy = centerY,
            method = if (expanded) null else uiState.selectedMethod,
            realm = if (expanded) uiState.realm else null,
            onClick = {
                if (expanded) {
                    // 已展开：点中间圆 = 切换登录渠道（认证域）
                    uiState = uiState.onRealmSwitched()
                    hint = "已切换到" + realmName(uiState.realm)
                } else {
                    // 未展开：点中间圆 = 展开左右两个
                    expanded = true
                    hint = "请选择登录方式"
                }
                hintTick++
            },
        )

        // ---- 表单容器：圆的下方，**常驻显示但没有背景**（透明容器，不画底、不画描边）----
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = centerY + mainSize / 2 + 36.dp)
                .fillMaxWidth()
                // 长表单（微信二维码 / 邮箱验证码）可滚动，避免被屏幕底部截断
                .height(screenH - centerY - mainSize / 2 - 36.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LoginFormBox(
                method = uiState.selectedMethod,
                colors = colors,
                onCredentialsChanged = { no, pwd ->
                    account = no
                    password = pwd
                },
                savedAccount = account,
                savedPassword = password,
                onFormValuesChanged = { campusValues = it },
                captchaImage = campusCaptcha?.image,
                smsCodeState = smsCodeState,
                mailCodeState = mailCodeState,
                onRequestSmsCode = {
                    // 短信码：先要有图形验证码（一次性），再发短信
                    if (!submitting && smsCodeState.enabled) {
                        scope.launch {
                            smsCodeState = smsCodeState.onStarted()
                            try {
                                val phone = campusValues["smsPhone"].orEmpty()
                                val captchaCode = campusValues["smsCaptchaCode"].orEmpty()
                                val cap = campusCaptcha
                                // 前置校验：缺手机号/图形码时不打后端（避免学校侧回"参数错误"）
                                if (phone.isBlank()) {
                                    showHint("请输入手机号")
                                    smsCodeState = smsCodeState.onFailed()
                                    return@launch
                                }
                                if (!ContactValidator.isValidPhone(phone)) {
                                    showHint("手机号格式不正确")
                                    smsCodeState = smsCodeState.onFailed()
                                    return@launch
                                }
                                if (captchaCode.isBlank()) {
                                    showHint("请输入图形验证码")
                                    smsCodeState = smsCodeState.onFailed()
                                    return@launch
                                }
                                if (cap == null) {
                                    showHint("图形验证码已失效，请点击图片刷新")
                                    smsCodeState = smsCodeState.onFailed()
                                    return@launch
                                }
                                when (
                                    val r = campusUseCase.sendSmsCode(
                                        phone = phone,
                                        captcha = cap,
                                        captchaCode = captchaCode,
                                    )
                                ) {
                                    is com.nongyu.appplus.login.data.cas.CasSmsSendResult.Success ->
                                        smsCodeState = smsCodeState.onSucceeded()
                                    is com.nongyu.appplus.login.data.cas.CasSmsSendResult.Failure -> {
                                        if (r.message.isNotBlank()) showHint(r.message);
                                        smsCodeState = smsCodeState.onFailed()
                                    }
                                }
                            } finally {
                                if (smsCodeState.sending) smsCodeState = smsCodeState.onFailed()
                            }
                        }
                    }
                },
                localCaptcha = localCaptcha,
                wechatQrState = wechatQrState,
                wechatQrUrl = wechatQrUrl,
                onRefreshWechatQr = {
                    wechatQrUrl = null
                    wechatQrStateToken = null
                    wechatQrState = WechatQrState.LOADING
                    wechatPollFailCount = 0
                    scope.launch {
                        when (val r = campusUseCase.fetchLoginQr()) {
                            is CasQrFetchResult.Success -> {
                                wechatQrUrl = r.qr.url
                                wechatQrStateToken = r.qr.state
                                wechatQrState = WechatQrState.READY
                                wechatPollActive = true
                            }
                            is CasQrFetchResult.Failure -> {
                                wechatQrState = WechatQrState.FAILED
                                if (r.message.isNotBlank()) showHint(r.message)
                            }
                        }
                    }
                },
                onDownloadAndOpenWechat = {
                    // 把二维码存到相册，再拉起微信
                    val url = wechatQrUrl
                    if (url != null) {
                        val ok = WechatQrActions.saveQrToGallery(appContext, url)
                        showHint(if (ok) "二维码已保存到相册，请用微信扫一扫" else "保存失败")
                        WechatQrActions.openWechat(appContext)
                    }
                },
                onCopyAndOpenWechat = {
                    val url = wechatQrUrl
                    if (url != null) {
                        WechatQrActions.copyToClipboard(appContext, url)
                        showHint("链接已复制，请在微信中打开")
                        WechatQrActions.openWechat(appContext)
                    }
                },
                onRefreshLocalCaptcha = { localCaptcha = LocalCaptcha.random() },
                onRequestMailCode = {
                    // 邮箱码：不需要图形验证码
                    if (!submitting && mailCodeState.enabled) {
                        scope.launch {
                            mailCodeState = mailCodeState.onStarted()
                            try {
                                val email = campusValues["email"].orEmpty()
                                if (email.isBlank()) {
                                    showHint("请输入邮箱")
                                    mailCodeState = mailCodeState.onFailed()
                                    return@launch
                                }
                                if (!ContactValidator.isValidEmail(email)) {
                                    showHint("邮箱格式不正确")
                                    mailCodeState = mailCodeState.onFailed()
                                    return@launch
                                }
                                // 本地校验图形码（后端不校验，这是前端交互约束）
                                if (!localCaptcha.matches(campusValues["localCaptchaCode"])) {
                                    showHint("图形验证码不正确，请重试")
                                    localCaptcha = LocalCaptcha.random()
                                    mailCodeState = mailCodeState.onFailed()
                                    return@launch
                                }
                                when (val r = mailCodeSender.send(email)) {
                                    is MailCodeSendResult.Success ->
                                        mailCodeState = mailCodeState.onSucceeded()
                                    is MailCodeSendResult.Failure -> {
                                        if (r.message.isNotBlank()) showHint(r.message);
                                        mailCodeState = mailCodeState.onFailed()
                                    }
                                }
                            } finally {
                                if (mailCodeState.sending) mailCodeState = mailCodeState.onFailed()
                            }
                        }
                    }
                },
                onRefreshCaptcha = {
                    if (!submitting) {
                        scope.launch {
                            submitting = true
                            try {
                                campusCaptcha = null
                                showHint(null);
                                when (val r = campusUseCase.fetchCaptcha()) {
                                    is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Success ->
                                        campusCaptcha = r.captcha
                                    is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Failure ->
                                        if (r.message.isNotBlank()) showHint(r.message);
                                }
                            } finally {
                                submitting = false
                            }
                        }
                    }
                },
            )
        }

        // 平台账密结果处理（原 #92 逻辑抽出）
        fun handlePasswordResult(result: com.nongyu.appplus.login.session.PasswordLoginResult) {
            when (result) {
                is com.nongyu.appplus.login.session.PasswordLoginResult.Success -> {
                    // 成功：用例已写凭证并切主界面。登录页随即移出组合，
                    // profileIncomplete 的轻提示由主界面侧承接（S5 转场票）。
                    showHint(null);
                }
                is com.nongyu.appplus.login.session.PasswordLoginResult.Failure -> {
                    val action = LoginFailurePolicy.decide(result.failure)
                    val code = (result.failure as? com.nongyu.appplus.net.core.ApiFailure.Http)?.code
                    when (action) {
                        LoginFailureAction.SWITCH_TO_CAMPUS_SSO -> {
                            // 自动切统一身份认证 tab；输入保留在 UI state，不清空
                            uiState = LoginCenterUiState.initial()
                            showHint(LoginFailurePolicy.messageOf(code));
                        }
                        LoginFailureAction.SHOW_MESSAGE ->
                            showHint(LoginFailurePolicy.messageOf(code));
                        LoginFailureAction.SILENT -> showHint(null);
                    }
                }
            }
        }

        // 统一身份认证结果处理（票 #96）：失败口径与平台登录一致
        fun handleCampusResult(result: com.nongyu.appplus.login.session.SchoolLoginResult) {
            when (result) {
                is com.nongyu.appplus.login.session.SchoolLoginResult.Success -> showHint(null);
                is com.nongyu.appplus.login.session.SchoolLoginResult.Failure -> {
                    val failure = result.failure
                    if (failure == null) {
                        // 无 ApiFailure：文案直接来自学校侧（或空串零提示）
                        showHint(result.message.ifBlank { null });
                    } else {
                        val code = (failure as? com.nongyu.appplus.net.core.ApiFailure.Http)?.code
                        when (LoginFailurePolicy.decide(failure)) {
                            LoginFailureAction.SWITCH_TO_CAMPUS_SSO -> {
                                uiState = LoginCenterUiState.initial()
                                showHint(LoginFailurePolicy.messageOf(code));
                            }
                            LoginFailureAction.SHOW_MESSAGE ->
                                showHint(LoginFailurePolicy.messageOf(code));
                            LoginFailureAction.SILENT -> showHint(null);
                        }
                    }
                    // 验证码是一次性的：提交失败后旧码已作废，自动换一张，
                    // 否则用户再点一次就会拿同一个废码空跑（表现为"点两次就报错"）。
                    if (uiState.selectedMethod == LoginMethod.CAMPUS_PASSWORD ||
                        uiState.selectedMethod == LoginMethod.CAMPUS_PHONE
                    ) {
                        campusCaptcha = null
                        scope.launch {
                            when (val r = campusUseCase.fetchCaptcha()) {
                                is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Success ->
                                    campusCaptcha = r.captcha
                                is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Failure -> Unit
                            }
                        }
                    }
                }
            }
        }

        // ---- 底部大圆形箭头按钮 = 登录 ----
        LoginArrowButton(
            colors = colors,
            onClick = {
                if (submitting) return@LoginArrowButton
                // 前置校验：与后端 validate.go 同一套规则，不合格不发请求
                // （后端不合法只回 400 invalid_request，没有 message，用户会觉得"没反应"）
                val missing = when (uiState.selectedMethod) {
                    LoginMethod.PLATFORM_PASSWORD ->
                        if (account.isBlank()) "请输入账号"
                        else if (!ContactValidator.isValidStudentNo(account)) "账号必须是 9 位数字"
                        else if (password.isBlank()) "请输入密码"
                        else if (!ContactValidator.isValidPassword(password)) "密码过长（最多 256 字符）"
                        else null

                    LoginMethod.CAMPUS_PASSWORD ->
                        if (campusValues["pwStudentNo"].isNullOrBlank()) "请输入学号"
                        else if (!ContactValidator.isValidStudentNo(campusValues["pwStudentNo"])) "学号必须是 9 位数字"
                        else if (campusValues["pwPassword"].isNullOrBlank()) "请输入密码"
                        else if (campusValues["pwCaptchaCode"].isNullOrBlank()) "请输入验证码"
                        else null

                    LoginMethod.CAMPUS_PHONE ->
                        if (campusValues["smsPhone"].isNullOrBlank()) "请输入手机号"
                        else if (!ContactValidator.isValidPhone(campusValues["smsPhone"])) "手机号格式不正确"
                        else if (campusValues["smsCaptchaCode"].isNullOrBlank()) "请输入图形验证码"
                        else if (campusValues["smsCode"].isNullOrBlank()) "请输入短信验证码"
                        else null

                    LoginMethod.PLATFORM_MAIL ->
                        if (campusValues["email"].isNullOrBlank()) "请输入邮箱"
                        else if (!ContactValidator.isValidEmail(campusValues["email"])) "邮箱格式不正确"
                        else if (campusValues["mailCode"].isNullOrBlank()) "请输入邮箱验证码"
                        else if (!ContactValidator.isValidMailCode(campusValues["mailCode"])) "验证码必须是 4-8 位字母或数字"
                        else null

                    else -> null
                }
                if (missing != null) {
                    showHint(missing)
                    return@LoginArrowButton
                }
                submitting = true
                showHint(null);
                val deviceId = DeviceIdStore.getOrCreate(appContext)
                scope.launch {
                    // 票 #96：按当前登录方式分派。finally 保证异常时也解锁按钮，
                    // 否则一次抛异常会让 submitting 永久为 true、按钮卡死。
                    try {
                        when (uiState.selectedMethod) {
                            LoginMethod.PLATFORM_PASSWORD -> {
                                handlePasswordResult(
                                    PasswordLoginUseCase(
                                        context = appContext,
                                        studentNo = account,
                                        password = password,
                                        deviceId = deviceId,
                                    ),
                                )
                            }
                            LoginMethod.CAMPUS_PASSWORD -> {
                                val cap = campusCaptcha
                                if (cap == null) {
                                    // 一次性验证码必须先取；本次点击只负责取码
                                    when (val r = campusUseCase.fetchCaptcha()) {
                                        is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Success ->
                                            campusCaptcha = r.captcha
                                        is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Failure ->
                                            showHint(r.message.ifBlank { null });
                                    }
                                    return@launch
                                }
                                handleCampusResult(
                                    campusUseCase.loginWithPassword(
                                        studentNo = campusValues["pwStudentNo"].orEmpty(),
                                        password = campusValues["pwPassword"].orEmpty(),
                                        captcha = cap,
                                        captchaCode = campusValues["pwCaptchaCode"].orEmpty(),
                                        deviceId = deviceId,
                                    ),
                                )
                            }
                            LoginMethod.CAMPUS_PHONE -> {
                                val cap = campusCaptcha
                                if (cap == null) {
                                    when (val r = campusUseCase.fetchCaptcha()) {
                                        is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Success ->
                                            campusCaptcha = r.captcha
                                        is com.nongyu.appplus.login.data.cas.CasCaptchaResult.Failure ->
                                            showHint(r.message.ifBlank { null });
                                    }
                                    return@launch
                                }
                                handleCampusResult(
                                    campusUseCase.loginWithPhone(
                                        phone = campusValues["smsPhone"].orEmpty(),
                                        smsCode = campusValues["smsCode"].orEmpty(),
                                        deviceId = deviceId,
                                    ),
                                )
                            }
                            LoginMethod.CAMPUS_WECHAT -> {
                                // 扫码完成感知待真机/抓包实测（票 #97），不臆造轮询
                                showHint("扫码登录暂不可用");
                            }
                            LoginMethod.PLATFORM_MAIL -> {
                                when (
                                    val r = mailLoginUseCase(
                                        email = campusValues["email"].orEmpty(),
                                        code = campusValues["mailCode"].orEmpty(),
                                        deviceId = deviceId,
                                    )
                                ) {
                                    is MailLoginResult.Success -> showHint(null)
                                    is MailLoginResult.Failure -> {
                                        val f = r.failure
                                        if (f == null) {
                                            showHint(r.message.ifBlank { null })
                                        } else {
                                            val c = (f as? com.nongyu.appplus.net.core.ApiFailure.Http)?.code
                                            when (LoginFailurePolicy.decide(f)) {
                                                LoginFailureAction.SILENT -> showHint(null)
                                                else -> showHint(LoginFailurePolicy.messageOf(c))
                                            }
                                        }
                                    }
                                }
                            }
                            LoginMethod.PLATFORM_OAUTH -> Unit
                        }
                    } finally {
                        submitting = false
                        hintTick++
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 148.dp),
        )


        // ---- 屏幕底部署名「IOT 工作室制作」----
        // 用 navigationBarsPadding 避开系统导航栏，不会被遮挡
        Text(
            text = "IOT 工作室制作",
            color = colors.surface.colorTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
        )

        // ---- 底部提示 ----
        AnimatedVisibility(
            visible = hint.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        ) {
            // 唯一的提示出口：字号与颜色要够醒目、允许换行（学校侧 msg 可能较长）
            Text(
                text = hint,
                color = colors.surface.colorText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )
        }
    }
}

/** 认证域的中文名（用于底部提示）。 */
private fun realmName(realm: AuthRealm): String = when (realm) {
    AuthRealm.CAMPUS_SSO -> "统一身份认证"
    AuthRealm.PLATFORM_LOGIN -> "平台登录"
}

/**
 * 中间的圆。
 *
 * @param method 非空时显示该登录方式的图标（未展开态）
 * @param realm 非空时显示登录渠道图标（展开态）
 */
@Composable
private fun CenterCircle(
    colors: NongyuColors,
    size: Dp,
    cx: Dp,
    cy: Dp,
    method: LoginMethod?,
    realm: AuthRealm?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .offset(x = cx - size / 2, y = cy - size / 2)
            .size(size)
            .graphicsLayer {
                val s = if (pressed) 0.94f else 1f
                scaleX = s
                scaleY = s
            }
            // clip 成圆形，让点击水波纹沿圆形边界扩散（否则是方形）
            .clip(CircleShape)
            .clickable(interactionSource = interaction) { onClick() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = colors.brand.colorBrand)
            val icon = size.toPx() * 0.42f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val tint = colors.brand.colorOnBrand
            if (method != null) {
                drawMethodIcon(method, c, icon, tint)
            } else {
                drawRealmIcon(realm, c, icon, tint)
            }
        }
    }
}

/** 左右两侧的圆：另外两种登录方式（淡绿底灰图标）。 */
@Composable
private fun OrbitingCircle(
    colors: NongyuColors,
    size: Dp,
    progress: Float,
    cx: Dp,
    cy: Dp,
    method: LoginMethod,
    isCenter: Boolean,
    onClick: () -> Unit,
) {
    if (progress <= 0.001f) return

    Box(
        modifier = Modifier
            .offset(x = cx - size / 2, y = cy - size / 2)
            .size(size)
            .graphicsLayer {
                val s = 0.4f + 0.6f * progress
                scaleX = s
                scaleY = s
                alpha = progress.coerceIn(0f, 1f)
            }
            // 同样 clip 成圆形涟漪
            .clip(CircleShape)
            .clickable(enabled = LoginCenterUiState.isEnabled(method)) { onClick() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = colors.brand.colorBrandMuted)
            drawMethodIcon(
                method = method,
                center = Offset(this.size.width / 2f, this.size.height / 2f),
                iconSize = size.toPx() * 0.42f,
                tint = colors.surface.colorTextSecondary.copy(
                    alpha = if (LoginCenterUiState.isEnabled(method)) 1f else 0.4f,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 矢量图标（等价 SVG path）
// ---------------------------------------------------------------------------

/** 按登录方式画对应图标。 */
private fun DrawScope.drawMethodIcon(
    method: LoginMethod,
    center: Offset,
    iconSize: Float,
    tint: Color,
) {
    when (method) {
        LoginMethod.CAMPUS_PASSWORD,
        LoginMethod.PLATFORM_PASSWORD,
        -> drawPasswordIcon(center, iconSize, tint)

        LoginMethod.CAMPUS_PHONE -> drawPhoneIcon(center, iconSize, tint)
        LoginMethod.PLATFORM_MAIL -> drawMailIcon(center, iconSize, tint)
        LoginMethod.CAMPUS_WECHAT -> drawChatIcon(center, iconSize, tint)
        LoginMethod.PLATFORM_OAUTH -> drawOAuthIcon(center, iconSize, tint)
    }
}

/** 账密：卡片 + 锁。 */
private fun DrawScope.drawPasswordIcon(center: Offset, s: Float, tint: Color) {
    val w = s * 0.82f
    val h = s * 0.58f
    val left = center.x - w / 2f
    val top = center.y - h / 2f + s * 0.06f
    val r = s * 0.10f
    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = s * 0.075f),
    )
    // 锁体
    val lockW = s * 0.26f
    val lockH = s * 0.18f
    drawRoundRect(
        color = tint,
        topLeft = Offset(center.x - lockW / 2f, top + h * 0.52f),
        size = Size(lockW, lockH),
        cornerRadius = CornerRadius(s * 0.04f, s * 0.04f),
    )
    // 锁梁
    val arcR = s * 0.09f
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - arcR, top + h * 0.52f - arcR),
        size = Size(arcR * 2, arcR * 2),
        style = Stroke(width = s * 0.065f, cap = StrokeCap.Round),
    )
}

/** 验证码（手机）：圆角矩形 + 听筒 + home。 */
private fun DrawScope.drawPhoneIcon(center: Offset, s: Float, tint: Color) {
    val w = s * 0.48f
    val h = s * 0.80f
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    val r = s * 0.10f
    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = s * 0.075f),
    )
    // 听筒
    drawLine(
        color = tint,
        start = Offset(center.x - w * 0.16f, top + h * 0.14f),
        end = Offset(center.x + w * 0.16f, top + h * 0.14f),
        strokeWidth = s * 0.06f,
        cap = StrokeCap.Round,
    )
    // home
    drawCircle(
        color = tint,
        radius = s * 0.045f,
        center = Offset(center.x, top + h * 0.86f),
    )
}

/** 邮箱验证码：信封。 */
private fun DrawScope.drawMailIcon(center: Offset, s: Float, tint: Color) {
    val w = s * 0.84f
    val h = s * 0.58f
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    val r = s * 0.08f
    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = s * 0.075f),
    )
    // 信封折线
    val p = Path().apply {
        moveTo(left + w * 0.06f, top + h * 0.16f)
        lineTo(center.x, top + h * 0.58f)
        lineTo(left + w * 0.94f, top + h * 0.16f)
    }
    drawPath(p, color = tint, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round))
}

/** 微信扫码：对话气泡。 */
private fun DrawScope.drawChatIcon(center: Offset, s: Float, tint: Color) {
    val w = s * 0.86f
    val h = s * 0.66f
    val left = center.x - w / 2f
    val top = center.y - h / 2f - s * 0.02f
    val r = s * 0.20f
    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = s * 0.075f),
    )
    // 气泡尾巴
    val p = Path().apply {
        moveTo(center.x - w * 0.16f, top + h)
        lineTo(center.x - w * 0.22f, top + h + s * 0.14f)
        lineTo(center.x + w * 0.02f, top + h)
    }
    drawPath(p, color = tint, style = Stroke(width = s * 0.075f, cap = StrokeCap.Round))
    // 两个眼睛
    drawCircle(tint, s * 0.055f, Offset(center.x - s * 0.14f, top + h * 0.46f))
    drawCircle(tint, s * 0.055f, Offset(center.x + s * 0.14f, top + h * 0.46f))
}

/** OAuth：两个环 + 断开点。 */
private fun DrawScope.drawOAuthIcon(center: Offset, s: Float, tint: Color) {
    val r = s * 0.22f
    val off = s * 0.18f
    drawCircle(
        color = tint,
        radius = r,
        center = Offset(center.x - off, center.y),
        style = Stroke(width = s * 0.075f),
    )
    drawCircle(
        color = tint,
        radius = r,
        center = Offset(center.x + off, center.y),
        style = Stroke(width = s * 0.075f),
    )
}

/** 登录渠道：双向箭头（切换统一身份认证 / 平台登录）。 */
private fun DrawScope.drawRealmIcon(realm: AuthRealm?, center: Offset, s: Float, tint: Color) {
    val stroke = s * 0.09f
    val half = s * 0.40f
    // 上行箭头
    drawLine(
        color = tint,
        start = Offset(center.x - half, center.y - s * 0.16f),
        end = Offset(center.x + half, center.y - s * 0.16f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawPath(
        path = Path().apply {
            moveTo(center.x + half - s * 0.20f, center.y - s * 0.32f)
            lineTo(center.x + half, center.y - s * 0.16f)
            lineTo(center.x + half - s * 0.20f, center.y)
        },
        color = tint,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // 下行箭头
    drawLine(
        color = tint,
        start = Offset(center.x + half, center.y + s * 0.16f),
        end = Offset(center.x - half, center.y + s * 0.16f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawPath(
        path = Path().apply {
            moveTo(center.x - half + s * 0.20f, center.y)
            lineTo(center.x - half, center.y + s * 0.16f)
            lineTo(center.x - half + s * 0.20f, center.y + s * 0.32f)
        },
        color = tint,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/**
 * 底部大圆形箭头按钮：点击表示「登录」。
 *
 * - 直径 88dp（比其他圆更大，视觉上是主操作）
 * - 品牌绿实心底 + 白色右向箭头（矢量 Path 绘制，等价 SVG path）
 * - `clip(CircleShape)` 让点击水波纹沿圆形扩散，不是方形
 * - 按下 spring 缩放到 0.92
 */
@Composable
private fun LoginArrowButton(
    colors: NongyuColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = 88.dp
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "loginArrowScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(interactionSource = interaction) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.width / 2f
            val c = Offset(r, this.size.height / 2f)
            // 圆底
            drawCircle(color = colors.brand.colorBrand, radius = r, center = c)

            // 白色**向上**箭头
            val tint = colors.brand.colorOnBrand
            val s = r * 0.86f           // 箭头长度基准
            val stroke = s * 0.10f
            val startY = c.y + s * 0.34f   // 尾部在下
            val endY = c.y - s * 0.34f     // 尖端在上
            drawLine(
                color = tint,
                start = Offset(c.x, startY),
                end = Offset(c.x, endY),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            // 箭头头部（两笔向上收拢）
            val head = s * 0.26f
            drawPath(
                path = Path().apply {
                    moveTo(c.x - head, endY + head)
                    lineTo(c.x, endY)
                    lineTo(c.x + head, endY + head)
                },
                color = tint,
                style = Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}
