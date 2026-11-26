package com.nongyu.appplus.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.profile.data.SchoolAnnouncementRepository
import com.nongyu.appplus.profile.logic.AnnouncementText
import com.nongyu.appplus.profile.model.SchoolAnnouncementView

/**
 * 「校内信息」公告页（票 #114）。
 *
 * 复用 [NetHolder.announcements] 暴露的既有公告接口（Bearer 由网络层统一注入，
 * 401 由既有失效分流处理）。列表 / `data:null` 空态 / 失败态三种状态都明确呈现，
 * 绝不白屏；生效窗口过滤在 [SchoolAnnouncementRepository] 里用纯函数完成。
 * 作为 ProfileScreen 的全屏子页，由 [onBack] 返回。
 */
@Composable
fun SchoolInfoScreen(
    uiScale: Float,
    bottomPadding: Dp,
    onBack: () -> Unit,
) {
    val colors = NongyuTheme.colors
    val context = LocalContext.current
    val repository = remember {
        SchoolAnnouncementRepository(NetHolder.announcements(context.applicationContext))
    }
    var state by remember { mutableStateOf<AnnouncementUiState>(AnnouncementUiState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        state = when (val result = repository.load(nowMillis = System.currentTimeMillis())) {
            is SchoolAnnouncementRepository.Result.Success ->
                AnnouncementUiState.Loaded(result.items)
            is SchoolAnnouncementRepository.Result.Failure ->
                AnnouncementUiState.Failed(result.message)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.colorBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = (20f * uiScale).dp)
            .padding(top = (24f * uiScale).dp)
            .padding(bottom = bottomPadding + (32f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((14f * uiScale).dp),
    ) {
        Text(
            text = "校内信息",
            color = colors.surface.colorText,
            fontSize = (20f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
        when (val s = state) {
            AnnouncementUiState.Loading -> StatusCard("公告加载中…", uiScale)
            is AnnouncementUiState.Loaded -> {
                if (s.items.isEmpty()) {
                    // data:null / items 为空 / 全部过期 —— 都是正常空态，不是错误。
                    StatusCard("当前没有生效的公告", uiScale)
                } else {
                    s.items.forEach { announcement ->
                        AnnouncementCard(announcement, uiScale)
                    }
                }
            }
            is AnnouncementUiState.Failed -> {
                StatusCard(s.message, uiScale)
                Text(
                    text = "重试",
                    color = colors.brand.colorBrand,
                    fontSize = (15f * uiScale).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { reloadKey += 1 },
                        )
                        .padding(vertical = (8f * uiScale).dp),
                )
            }
        }
        Text(
            text = "返回",
            color = colors.brand.colorBrand,
            fontSize = (16f * uiScale).sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onBack,
                )
                .padding(vertical = (10f * uiScale).dp),
        )
    }
}

private sealed interface AnnouncementUiState {
    data object Loading : AnnouncementUiState
    data class Loaded(val items: List<SchoolAnnouncementView>) : AnnouncementUiState
    data class Failed(val message: String) : AnnouncementUiState
}

@Composable
private fun StatusCard(text: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Text(
        text = text,
        color = colors.surface.colorTextSecondary,
        fontSize = (14f * uiScale).sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((18f * uiScale).dp))
            .background(colors.surface.colorCard)
            .padding((20f * uiScale).dp),
    )
}

@Composable
private fun AnnouncementCard(item: SchoolAnnouncementView, uiScale: Float) {
    val colors = NongyuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((18f * uiScale).dp))
            .background(colors.surface.colorCard)
            .padding((18f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((8f * uiScale).dp),
    ) {
        Text(
            text = if (item.pinned) "${item.title}（置顶）" else item.title,
            color = colors.surface.colorText,
            fontSize = (16f * uiScale).sp,
            fontWeight = FontWeight.Bold,
        )
        if (item.body.isNotBlank()) {
            Text(
                text = item.body,
                color = colors.surface.colorTextSecondary,
                fontSize = (14f * uiScale).sp,
                lineHeight = (21f * uiScale).sp,
            )
        }
        val window = buildString {
            append("发布：").append(AnnouncementText.displayTime(item.publishedAt))
            if (item.effectiveFromRaw != null || item.expiresAtRaw != null) {
                append("  生效期：")
                append(AnnouncementText.displayTime(item.effectiveFromRaw))
                append(" ~ ")
                append(AnnouncementText.displayTime(item.expiresAtRaw))
            }
        }
        Text(
            text = window,
            color = colors.surface.colorTextSecondary,
            fontSize = (12f * uiScale).sp,
        )
    }
}
