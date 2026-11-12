package com.nongyu.appplus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nongyu.appplus.feature.login.LoginCenterScreen
import com.nongyu.appplus.feature.login.LoginDestination
import com.nongyu.appplus.feature.login.LoginSessionHolder
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme

/**
 * 应用根组件。
 *
 * 票 #90 起：根节点用 `when(会话状态)` 在「主界面 / 登录中心」之间切换（Q9=A），
 * 不引导航库。状态由 [LoginSessionHolder]（单一主接缝）持有：
 * - 首帧前 MainActivity 用会话存储判定落地去向，无「检查中」态；
 * - 首帧之后 `SessionStartupVerifier` 的校验结果只做事后纠正。
 *
 * 本组件**不发起网络请求**，也不访问 NetHolder（ADR-0006）。
 */
@Composable
fun NongyuApp() {
    val state = LoginSessionHolder.state.value
    when (state.destination) {
        LoginDestination.MAIN -> MainShell()
        LoginDestination.LOGIN_CENTER -> LoginCenterScreen()
    }
}

@Preview(showBackground = true)
@Composable
private fun NongyuAppPreview() {
    NongyuAppPlusTheme {
        NongyuApp()
    }
}
