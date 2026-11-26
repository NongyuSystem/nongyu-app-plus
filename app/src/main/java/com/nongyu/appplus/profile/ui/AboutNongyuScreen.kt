package com.nongyu.appplus.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.BuildConfig
import com.nongyu.appplus.design.NongyuTheme

/**
 * 「关于农屿」原生静态页（票 #114）。
 *
 * 版本号取 [BuildConfig.VERSION_NAME]（编译期常量），项目地址 / 许可 / 致谢全部硬编码，
 * **零网络请求、不依赖登录** —— 没有 Retrofit、没有 context、没有 LaunchedEffect。
 * 作为 ProfileScreen 的全屏子页，由 [onBack] 返回画像页；系统返回键的接管由调用方
 * 统一在 ProfileScreen 里用 BackHandler 驱动，不在本页重复注册。
 */
@Composable
fun AboutNongyuScreen(
    uiScale: Float,
    bottomPadding: Dp,
    onBack: () -> Unit,
) {
    val colors = NongyuTheme.colors
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
        SectionTitle("关于农屿", uiScale)
        InfoCard(uiScale) {
            InfoRow("版本", BuildConfig.VERSION_NAME, uiScale)
            InfoRow("项目地址", "github.com/NongyuSystem/nongyu-app-plus", uiScale)
            InfoRow("许可", "本项目为农屿团队内部翻新项目，未对外发布开源许可", uiScale)
        }
        SectionTitle("致谢", uiScale)
        InfoCard(uiScale) {
            Text(
                text = "感谢农屿团队与所有为翻新版提供反馈的同学。" +
                    "课表、登录、网络层与主题能力建立在开源组件之上，" +
                    "包括 Jetpack Compose、Retrofit、OkHttp、kotlinx.serialization 与 DataStore。",
                color = colors.surface.colorTextSecondary,
                fontSize = (14f * uiScale).sp,
                lineHeight = (22f * uiScale).sp,
            )
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

@Composable
private fun SectionTitle(text: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Text(
        text = text,
        color = colors.surface.colorText,
        fontSize = (20f * uiScale).sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun InfoCard(uiScale: Float, content: @Composable ColumnScope.() -> Unit) {
    val colors = NongyuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((18f * uiScale).dp))
            .background(colors.surface.colorCard)
            .padding((20f * uiScale).dp),
        verticalArrangement = Arrangement.spacedBy((12f * uiScale).dp),
        content = content,
    )
}

@Composable
private fun InfoRow(label: String, value: String, uiScale: Float) {
    val colors = NongyuTheme.colors
    Column {
        Text(
            text = label,
            color = colors.surface.colorTextSecondary,
            fontSize = (12f * uiScale).sp,
        )
        Text(
            text = value,
            color = colors.surface.colorText,
            fontSize = (15f * uiScale).sp,
        )
    }
}
