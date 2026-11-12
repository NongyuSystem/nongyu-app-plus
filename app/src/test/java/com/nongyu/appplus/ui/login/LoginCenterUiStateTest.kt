package com.nongyu.appplus.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LoginCenterUiState] 的纯 JVM 单测（票 #91 / 规格 #89）。
 *
 * 钉死登录中心的三条外部行为：
 * - 下方切换按钮在两个认证域间切换，切后上方展示对应的三种方式；
 * - 上方三 tab 只切「当前方式」，不改变后端 method 选择（本状态不含 method 字段）；
 * - `oauth` tab 存在但禁用，选不中、点不动。
 */
class LoginCenterUiStateTest {

    @Test
    fun `默认认证域是统一身份认证且展示三种方式`() {
        val state = LoginCenterUiState.initial()
        assertEquals(AuthRealm.CAMPUS_SSO, state.realm)
        assertEquals(
            listOf(LoginMethod.CAMPUS_PASSWORD, LoginMethod.CAMPUS_PHONE, LoginMethod.CAMPUS_WECHAT),
            state.methods,
        )
    }

    @Test
    fun `切到平台登录后展示平台三方式且默认选账密`() {
        val state = LoginCenterUiState.initial().onRealmSwitched()
        assertEquals(AuthRealm.PLATFORM_LOGIN, state.realm)
        assertEquals(
            listOf(LoginMethod.PLATFORM_PASSWORD, LoginMethod.PLATFORM_MAIL, LoginMethod.PLATFORM_OAUTH),
            state.methods,
        )
        assertEquals(LoginMethod.PLATFORM_PASSWORD, state.selectedMethod)
    }

    @Test
    fun `再切一次回到统一身份认证且默认选账密`() {
        val state = LoginCenterUiState.initial()
            .onRealmSwitched()
            .onRealmSwitched()
        assertEquals(AuthRealm.CAMPUS_SSO, state.realm)
        assertEquals(LoginMethod.CAMPUS_PASSWORD, state.selectedMethod)
    }

    @Test
    fun `切 tab 只改当前方式且不改认证域`() {
        val state = LoginCenterUiState.initial()
            .onMethodSelected(LoginMethod.CAMPUS_PHONE)
        assertEquals(AuthRealm.CAMPUS_SSO, state.realm)
        assertEquals(LoginMethod.CAMPUS_PHONE, state.selectedMethod)
    }

    @Test
    fun `选不认证域里不存在的方式时保持原选择`() {
        val state = LoginCenterUiState.initial()
            .onMethodSelected(LoginMethod.PLATFORM_MAIL)
        assertEquals(LoginMethod.CAMPUS_PASSWORD, state.selectedMethod)
    }

    @Test
    fun `oauth tab 存在但禁用且选不中`() {
        val state = LoginCenterUiState.initial().onRealmSwitched()
        assertTrue(state.methods.contains(LoginMethod.PLATFORM_OAUTH))
        assertFalse(LoginCenterUiState.isEnabled(LoginMethod.PLATFORM_OAUTH))
        val after = state.onMethodSelected(LoginMethod.PLATFORM_OAUTH)
        assertEquals(LoginMethod.PLATFORM_PASSWORD, after.selectedMethod)
    }

    @Test
    fun `oauth 标签文案固定`() {
        assertEquals("OAuth（暂未开放）", LoginCenterUiState.labelOf(LoginMethod.PLATFORM_OAUTH))
    }

    @Test
    fun `认证域与方式不携带后端 method 概念`() {
        // 状态里只有「选了哪个方式」，没有 schoolMethod / method 字段：
        // 切换 tab 不改变后端 method 选择逻辑（Q21）。
        val state = LoginCenterUiState.initial().onMethodSelected(LoginMethod.CAMPUS_WECHAT)
        assertEquals(LoginMethod.CAMPUS_WECHAT, state.selectedMethod)
    }
}
