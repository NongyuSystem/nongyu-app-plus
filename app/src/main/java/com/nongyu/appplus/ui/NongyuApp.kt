package com.nongyu.appplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme

/**
 * 应用根组件。
 *
 * 当前为骨架占位页，仅用于验证工程可编译、可打包、可启动。
 * 后续接入设计稿后，此处替换为真实的路由容器。
 */
@Composable
fun NongyuApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        PlaceholderHome(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * 占位首页：确认应用能正常渲染，非最终设计。
 */
@Composable
private fun PlaceholderHome(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "农屿 · 翻新版",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "工程骨架已就绪\n等待接入设计稿",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderHomePreview() {
    NongyuAppPlusTheme {
        NongyuApp()
    }
}
