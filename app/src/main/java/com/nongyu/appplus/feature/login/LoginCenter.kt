package com.nongyu.appplus.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nongyu.appplus.ui.theme.NongyuTheme

/**
 * 登录中心的**占位界面组件**（票 #90）。
 *
 * 「登录中心」是产品概念（`GLOSSARY.md` 明确它不是 UI 组件名），所以组件本身取名为
 * [LoginCenterScreen]，避免把产品概念当成组件名。本票只负责「无 App 会话凭证时首帧
 * 进登录中心」这条去向，因此这里是一个最小可渲染的占位：主题底 + 一行规范术语文字。
 * S2 会把两个认证域切换、上方三 tab、oauth 占位等骨架补上。
 * 配色只取 [NongyuTheme.colors] 色板 token（Q6=A），不写死十六进制、不扩皮肤包。
 */
@Composable
fun LoginCenterScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NongyuTheme.colors.surface.colorBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "登录中心",
            color = NongyuTheme.colors.surface.colorText,
        )
    }
}
