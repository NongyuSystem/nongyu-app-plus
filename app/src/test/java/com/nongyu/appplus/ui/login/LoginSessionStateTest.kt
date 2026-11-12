package com.nongyu.appplus.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [LoginSessionState] 的纯 JVM 单测（票 #90 单一主接缝）。
 *
 * 钉死「首帧即落地 + 校验事后纠正」的状态机：
 * - 初始状态由本地是否有 App 会话凭证决定，不存在「检查中」态；
 * - 只有 SESSION_ENDED 才把去向改回登录中心，其余结果不动状态。
 */
class LoginSessionStateTest {

    @Test
    fun `本地有凭证时首帧进主界面`() {
        val state = LoginSessionState.initial(hasCredential = true)
        assertEquals(LoginDestination.MAIN, state.destination)
    }

    @Test
    fun `本地无凭证时首帧进登录中心`() {
        val state = LoginSessionState.initial(hasCredential = false)
        assertEquals(LoginDestination.LOGIN_CENTER, state.destination)
    }

    @Test
    fun `没有独立检查中状态`() {
        // 初始态只能是 MAIN 或 LOGIN_CENTER 之一，不渲染第三块界面
        val withCredential = LoginSessionState.initial(hasCredential = true)
        val withoutCredential = LoginSessionState.initial(hasCredential = false)
        assertEquals(LoginDestination.MAIN, withCredential.destination)
        assertEquals(LoginDestination.LOGIN_CENTER, withoutCredential.destination)
    }

    @Test
    fun `校验判会话结束时切回登录中心`() {
        val state = LoginSessionState.initial(hasCredential = true)
            .onVerified(VerificationResult.SESSION_ENDED)
        assertEquals(LoginDestination.LOGIN_CENTER, state.destination)
    }

    @Test
    fun `校验判合法时保持主界面`() {
        val state = LoginSessionState.initial(hasCredential = true)
            .onVerified(VerificationResult.VALID)
        assertEquals(LoginDestination.MAIN, state.destination)
    }

    @Test
    fun `校验无法判定时保持主界面且乐观放行`() {
        val state = LoginSessionState.initial(hasCredential = true)
            .onVerified(VerificationResult.INDETERMINATE)
        assertEquals(LoginDestination.MAIN, state.destination)
    }

    @Test
    fun `登录中心状态下校验无法判定仍留在登录中心`() {
        val state = LoginSessionState.initial(hasCredential = false)
            .onVerified(VerificationResult.INDETERMINATE)
        assertEquals(LoginDestination.LOGIN_CENTER, state.destination)
    }

    @Test
    fun `登录成功后进主界面`() {
        val state = LoginSessionState.initial(hasCredential = false)
            .onSignedIn()
        assertEquals(LoginDestination.MAIN, state.destination)
    }

    @Test
    fun `会话结束后重新登录可再次进主界面`() {
        val state = LoginSessionState.initial(hasCredential = true)
            .onVerified(VerificationResult.SESSION_ENDED)
            .onSignedIn()
        assertEquals(LoginDestination.MAIN, state.destination)
    }
}
