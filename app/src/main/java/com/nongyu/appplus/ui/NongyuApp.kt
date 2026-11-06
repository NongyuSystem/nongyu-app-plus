package com.nongyu.appplus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme

/**
 * 应用根组件。
 *
 * 0.0.1test：挂载主框架与导航壳（[MainShell]），四页全白留空，
 * 正中凸起的农小屿星云球为唯一交互入口。
 * 语音转文字 / 后端 agent / 路由栈尚未接入（骨架阶段边界）。
 */
@Composable
fun NongyuApp() {
    MainShell()
}

@Preview(showBackground = true)
@Composable
private fun NongyuAppPreview() {
    NongyuAppPlusTheme {
        NongyuApp()
    }
}
