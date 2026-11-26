package com.nongyu.appplus.profile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.design.uiScaleFor
import com.nongyu.appplus.profile.data.DemoProfileHolder
import com.nongyu.appplus.profile.logic.ProfileFields
import com.nongyu.appplus.profile.logic.ProfileLogic
import com.nongyu.appplus.profile.model.ProfileFieldItem
import com.nongyu.appplus.profile.model.ProfileSnapshot
import com.nongyu.appplus.profile.model.ProfileState

/**
 * 「个人」Tab 的画像卡 + 两态揭示入口区（票 #113 画像卡 / #114 入口区 / #117 揭示形态）。
 *
 * ## 两态揭示（票 #117）
 * 收起态只显示上半画像卡；在页面上滑揭示下方画像入口区，下拉收回。
 * 揭示进度由 [ProfileRootForm] 的显式 Animatable 驱动（拖拽 snapTo / 松手 animateTo），
 * 阈值与吸附目标由 `profile/logic/RootForm` 纯函数判定并单测。刷新果实的点击语义
 * 与上滑手势分流：点击走 clickable，上滑走竖直拖拽，互不抢占。
 *
 * ## 三态与数据来源
 * 本地优先：只读画像缓存快照，页面**不发 `/me` 请求**。三态由
 * [ProfileLogic.resolveState] 推导，demo 兜底只在 debug 源集且开关打开时生效。
 * - 未登录：占位画像 + 登录入口；
 * - 数据不全：登录响应兜底的字段照常展示，并给明确说明；
 * - 会话过期：明确提示「登录已过期」，不展示误导性的旧画像；
 * - 已登录：画像字段 + 登录态剩余期。
 *
 * ## 字段详情
 * 点击字段不在此处弹窗，而是把 [ProfileFieldItem] 上报给 Shell；Shell 复用既有
 * `BarMode.Detail` 的高度与退出语义，不新增底栏形态枚举值（见 ADR-0015）。
 *
 * ## 入口区（票 #114）
 * 下半部三个入口：关于农屿（原生静态页，零网络、不依赖登录）、校内信息（公告列表）、
 * 末端刷新果实（重读 ProfileStore + 加载反馈）。入口区在收起态隐藏、展开态可见，
 * 复用 #114 的入口内容，不与上滑展开手势复用语义（规格 #111 承载结构）。
 *
 * @param navOccupiedPx 底栏常态占位高度（px，null = 首帧未测完先铺满），与课表同口径
 * @param hasCredential 是否有 App 会话凭证（真源 LoginSessionHolder / SessionStore）
 * @param profile 画像缓存快照（可为 null）
 * @param isRefreshing 画像是否正在刷新（由 Shell 驱动，果实显示加载反馈）
 * @param onRefresh 刷新果实点击 → 由 Shell 重读 ProfileStore
 * @param onFieldClick 画像字段点击 → 由 Shell 打开字段详情卡
 * @param onLoginClick 未登录 / 过期态的登录入口点击
 */
@Composable
fun ProfileScreen(
    navOccupiedPx: Float?,
    hasCredential: Boolean,
    profile: ProfileSnapshot?,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onFieldClick: (ProfileFieldItem) -> Unit,
    onLoginClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val navPaddingDp: Dp = remember(navOccupiedPx) {
        if (navOccupiedPx == null) 0.dp else with(density) { navOccupiedPx.toDp() }
    }
    val uiScale = uiScaleFor(context.resources.displayMetrics.widthPixels / density.density)

    var subPage by remember { mutableStateOf(ProfileSubPage.NONE) }
    BackHandler(enabled = subPage != ProfileSubPage.NONE) {
        subPage = ProfileSubPage.NONE
    }

    // 票 #117：两态揭示。收起态只显示画像卡；上滑揭示画像入口区，下拉收回。
    // 揭示进度用显式 Animatable（拖拽 snapTo / 松手 animateTo），阈值由 RootForm 纯函数推导。
    val rootProgress = rememberProfileRootFormProgress()
    var rootExpanded by remember { mutableStateOf(false) }
    // 入口区实测约 260dp（两张卡 20dp 内边距 + 两行入口 64dp + 刷新果实 58dp + 间距）。
    // 取 300dp 留出余量，保证刷新果实在展开态完整可见、不被揭示容器裁掉。
    val revealHeight = (300f * uiScale).dp

    if (subPage != ProfileSubPage.NONE) {
        when (subPage) {
            ProfileSubPage.ABOUT -> AboutNongyuScreen(
                uiScale = uiScale,
                bottomPadding = navPaddingDp,
                onBack = { subPage = ProfileSubPage.NONE },
            )
            ProfileSubPage.SCHOOL_INFO -> SchoolInfoScreen(
                uiScale = uiScale,
                bottomPadding = navPaddingDp,
                onBack = { subPage = ProfileSubPage.NONE },
            )
            ProfileSubPage.NONE -> Unit
        }
        return
    }

    // demo 兜底：只有 debug 源集才会给 DemoProfileHolder.demo 赋值；
    // release 包里 demo 恒 null，这段分支恒不生效。
    val demo = DemoProfileHolder.demo
    val demoActive = demo != null && DemoProfileHolder.enabled && !ProfileLogic.isCacheNonEmpty(profile)
    val effective = if (demoActive) demo else profile
    val effectiveCredential = ProfileFields.hasEffectiveCredential(hasCredential, demoActive)
    val nowSeconds = System.currentTimeMillis() / 1000L
    val state = ProfileLogic.resolveState(
        hasCredential = effectiveCredential,
        profile = effective,
        // demo 不伪造过期时间；真实 expiresAt 由登录响应透传。
        expiresAtUnixSeconds = effective?.expiresAtUnixSeconds,
        nowEpochSeconds = nowSeconds,
    )
    val remainingLabel = effective?.expiresAtUnixSeconds?.let {
        ProfileFields.remainingLabelOf(ProfileLogic.remaining(it, nowSeconds))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground)
            .padding(horizontal = (20f * uiScale).dp)
            // 底栏占位：与 TimetableScreen 同口径，子页不做坐标换算。
            .padding(bottom = navPaddingDp + (24f * uiScale).dp)
            .padding(top = (24f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((12f * uiScale).dp),
    ) {
        // 票 #117：画像卡（上半）+ 画像入口区（下半）装进两态揭示容器。
        // 收起态只显示卡片；上滑揭示入口区，下拉收回；卡片高度与位置不随揭示变化。
        ProfileRootForm(
            progress = rootProgress,
            expanded = rootExpanded,
            onExpandedChange = { rootExpanded = it },
            revealHeight = revealHeight,
            uiScale = uiScale,
            card = {
                when (state) {
                    ProfileState.NOT_SIGNED_IN -> StateNotice(
                        title = "占位画像",
                        body = "登录后这里会展示你的姓名、学号、班级与专业。未登录也可以使用「关于农屿」。",
                        actionLabel = "去登录",
                        onAction = onLoginClick,
                        uiScale = uiScale,
                    )
                    ProfileState.SESSION_EXPIRED -> StateNotice(
                        title = "登录已过期",
                        body = "会话已经到期，请重新登录以查看画像。",
                        actionLabel = "重新登录",
                        onAction = onLoginClick,
                        uiScale = uiScale,
                    )
                    ProfileState.INCOMPLETE -> {
                        Text(
                            text = "登录信息不完整，以下字段来自登录响应；请重新登录补全画像。",
                            color = colors.surface.colorTextSecondary,
                            fontSize = (13f * uiScale).sp,
                        )
                        ProfileCard(
                            fields = ProfileFields.of(effective),
                            remainingLabel = remainingLabel,
                            uiScale = uiScale,
                            onFieldClick = onFieldClick,
                        )
                    }
                    ProfileState.READY -> ProfileCard(
                        fields = ProfileFields.of(effective),
                        remainingLabel = remainingLabel,
                        uiScale = uiScale,
                        onFieldClick = onFieldClick,
                    )
                }
            },
            entries = {
                // 票 #114 的入口区原样复用：刷新果实的点击语义不与上滑手势共用。
                ProfileEntries(
                    uiScale = uiScale,
                    isRefreshing = isRefreshing,
                    onAboutClick = { subPage = ProfileSubPage.ABOUT },
                    onSchoolInfoClick = { subPage = ProfileSubPage.SCHOOL_INFO },
                    onRefreshClick = onRefresh,
                )
            },
        )
    }
}

private enum class ProfileSubPage { NONE, ABOUT, SCHOOL_INFO }

@Composable
private fun StateNotice(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    uiScale: Float,
) {
    val colors = NongyuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((18f * uiScale).dp))
            .background(colors.surface.colorCard)
            .padding((20f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((10f * uiScale).dp),
    ) {
        Text(text = title, color = colors.surface.colorText, fontSize = (20f * uiScale).sp)
        Text(text = body, color = colors.surface.colorTextSecondary, fontSize = (14f * uiScale).sp)
        Text(
            text = actionLabel,
            color = colors.brand.colorBrand,
            fontSize = (15f * uiScale).sp,
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onAction,
                )
                .padding(vertical = (6f * uiScale).dp),
        )
    }
}

@Composable
private fun ProfileCard(
    fields: List<ProfileFieldItem>,
    remainingLabel: String?,
    uiScale: Float,
    onFieldClick: (ProfileFieldItem) -> Unit,
) {
    val colors = NongyuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((18f * uiScale).dp))
            .background(colors.surface.colorCard)
            .padding((20f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((2f * uiScale).dp),
    ) {
        Text(
            text = "我的画像",
            color = colors.surface.colorText,
            fontSize = (20f * uiScale).sp,
            modifier = Modifier.padding(bottom = (8f * uiScale).dp),
        )
        fields.forEach { field ->
            ProfileFieldRow(field = field, uiScale = uiScale, onClick = { onFieldClick(field) })
        }
        if (remainingLabel != null) {
            Text(
                text = remainingLabel,
                color = colors.surface.colorTextSecondary,
                fontSize = (12f * uiScale).sp,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = (10f * uiScale).dp),
            )
        }
    }
}

@Composable
private fun ProfileFieldRow(
    field: ProfileFieldItem,
    uiScale: Float,
    onClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = (10f * uiScale).dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = field.label,
                color = colors.surface.colorTextSecondary,
                fontSize = (12f * uiScale).sp,
            )
            Text(
                text = field.value,
                color = colors.surface.colorText,
                fontSize = (16f * uiScale).sp,
            )
        }
    }
}

/**
 * 入口区（票 #114）：关于农屿 / 校内信息两行入口 + 末端刷新果实。
 * 果实是可点按钮，热区不小于最小可点尺寸并带无障碍描述；刷新中显示加载反馈且不可重复点。
 */
@Composable
private fun ProfileEntries(
    uiScale: Float,
    isRefreshing: Boolean,
    onAboutClick: () -> Unit,
    onSchoolInfoClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val colors = NongyuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((18f * uiScale).dp))
            .background(colors.surface.colorCard)
            .padding((20f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((4f * uiScale).dp),
    ) {
        Text(
            text = "入口",
            color = colors.surface.colorText,
            fontSize = (16f * uiScale).sp,
            modifier = Modifier.padding(bottom = (4f * uiScale).dp),
        )
        EntryRow(label = "关于农屿", uiScale = uiScale, onClick = onAboutClick)
        EntryRow(label = "校内信息", uiScale = uiScale, onClick = onSchoolInfoClick)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (10f * uiScale).dp)
                .clickable(
                    enabled = !isRefreshing,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onRefreshClick,
                )
                .heightIn(min = (48f * uiScale).dp)
                .clip(RoundedCornerShape((24f * uiScale).dp))
                .background(colors.brand.colorBrand)
                .semantics { contentDescription = "刷新画像" },
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size((16f * uiScale).dp),
                        color = colors.brand.colorOnBrand,
                        strokeWidth = (2f * uiScale).dp,
                    )
                    Text(
                        text = "  刷新中…",
                        color = colors.brand.colorOnBrand,
                        fontSize = (14f * uiScale).sp,
                    )
                }
            } else {
                Text(
                    text = "刷新果实 · 更新画像",
                    color = colors.brand.colorOnBrand,
                    fontSize = (15f * uiScale).sp,
                )
            }
        }
    }
}

@Composable
private fun EntryRow(label: String, uiScale: Float, onClick: () -> Unit) {
    val colors = NongyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .heightIn(min = (44f * uiScale).dp)
            .padding(vertical = (10f * uiScale).dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = colors.surface.colorText, fontSize = (15f * uiScale).sp)
    }
}
