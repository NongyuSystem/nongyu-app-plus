package com.nongyu.appplus.shell.bar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme

/**
 * 探索页底栏内容（`BarMode.ExplorationPage` 分支）—— **实验性占位**。
 *
 * 存在的唯一目的是量出「新增一种底栏变形要散动多少处」，
 * 结论见 `docs/specs/界面层.md`。真接入探索页时，这个文件会被真实内容替换。
 *
 * 特意做成**不依赖任何 Shell 状态**的纯展示组件，
 * 以便观察：即使内容很干净，接入仍要改 Shell 的若干个 `when`。
 */
@Composable
fun ExplorationBarPlaceholder(
    uiScale: Float,
    s: (Dp) -> Dp,
) {
    val colors = NongyuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = s(16.dp), vertical = s(10.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "探索页底栏（占位）",
            color = colors.surface.colorText,
            fontSize = (11f * uiScale).sp,
        )
        Text(
            text = "用于验证新增底栏形态的改动面",
            color = colors.surface.colorTextSecondary,
            fontSize = (8f * uiScale).sp,
        )
    }
}
