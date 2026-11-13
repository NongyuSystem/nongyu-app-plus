package com.nongyu.appplus.login.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VerificationCodeButtonState] 纯 JVM 单测。
 *
 * 钉死规格 #89 的文案状态机：
 * 「获取验证码 → 发送中… → 重新获取（Ns）→ 重新获取」。
 */
class VerificationCodeButtonStateTest {

    @Test
    fun `初始文案是获取验证码且可点`() {
        val s = VerificationCodeButtonState()
        assertEquals("获取验证码", s.label)
        assertTrue(s.enabled)
    }

    @Test
    fun `发送中文案是发送中且不可点`() {
        val s = VerificationCodeButtonState().onStarted()
        assertEquals("发送中…", s.label)
        assertFalse(s.enabled)
    }

    @Test
    fun `发送成功后进入倒计时文案且不可点`() {
        val s = VerificationCodeButtonState().onStarted().onSucceeded()
        assertEquals(60, s.remainingSeconds)
        assertEquals("重新获取（60s）", s.label)
        assertFalse(s.enabled)
    }

    @Test
    fun `倒计时推进到零后回到可点`() {
        var s = VerificationCodeButtonState().onSucceeded(seconds = 2)
        s = s.tick()
        assertEquals("重新获取（1s）", s.label)
        s = s.tick()
        assertEquals(0, s.remainingSeconds)
        assertEquals("重新获取", s.label)
        assertTrue(s.enabled)
    }

    @Test
    fun `倒计时归零后继续 tick 不变`() {
        val s = VerificationCodeButtonState(remainingSeconds = 0)
        assertEquals(s, s.tick())
    }

    @Test
    fun `发送失败回到可点且不进倒计时`() {
        val s = VerificationCodeButtonState().onStarted().onFailed()
        assertEquals("获取验证码", s.label)
        assertTrue(s.enabled)
        assertEquals(0, s.remainingSeconds)
    }

    @Test
    fun `规格示例文案全部覆盖`() {
        assertEquals("获取验证码", VerificationCodeButtonState().label)
        assertEquals("发送中…", VerificationCodeButtonState(sending = true).label)
        assertEquals("重新获取（30s）", VerificationCodeButtonState(remainingSeconds = 30).label)
        // 发过码且倒计时归零 → 「重新获取」（不是「获取验证码」）
        assertEquals(
            "重新获取",
            VerificationCodeButtonState(remainingSeconds = 0, hasSentOnce = true).label,
        )
    }
}
