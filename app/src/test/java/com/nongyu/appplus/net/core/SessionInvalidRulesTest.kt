package com.nongyu.appplus.net.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SessionInvalidRules] 的纯 JVM 单测（规格 §4.3）。
 *
 * 这张判据表最容易写错，两个重点：
 * 1. **`session_expired` 同名不同义**：identity 域要清登录态，school 域绝不能清。
 * 2. **最长前缀优先**：`/school/presence` 必须先于 `/school/` 命中。
 */
class SessionInvalidRulesTest {

    private fun d(path: String, status: Int, code: String?) =
        SessionInvalidRules.decide(path, status, code)

    // ---------- identity 域：唯一允许清登录态的地方 ----------

    @Test
    fun `identity 域的 session_expired 401 清登录态`() {
        val r = d("/identity/me", 401, ApiErrorCode.SESSION_EXPIRED)
        assertEquals(SessionInvalidRules.Decision.CLEAR_SESSION, r)
        assertTrue(SessionInvalidRules.shouldClearSession(r))
    }

    @Test
    fun `identity 域的 unauthorized 401 清登录态`() {
        assertEquals(
            SessionInvalidRules.Decision.CLEAR_SESSION,
            d("/identity/logout", 401, ApiErrorCode.UNAUTHORIZED),
        )
    }

    @Test
    fun `identity 域 401 但码不认识时保守不清`() {
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/identity/me", 401, "some_new_code"),
        )
    }

    @Test
    fun `identity 域 503 auth_unavailable 不清登录态`() {
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_RETRYABLE,
            d("/identity/me", 503, ApiErrorCode.AUTH_UNAVAILABLE),
        )
    }

    @Test
    fun `identity 域 500 不清登录态`() {
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/identity/me", 500, ApiErrorCode.INTERNAL_ERROR),
        )
    }

    // ---------- school 域：同名不同义，绝不能清 ----------

    @Test
    fun `school 域的 session_expired 是教务失效，绝不清登录态`() {
        val r = d("/school/address", 401, ApiErrorCode.SESSION_EXPIRED)
        assertEquals(SessionInvalidRules.Decision.KEEP_SESSION_SCHOOL_AUTH, r)
        assertFalse(SessionInvalidRules.shouldClearSession(r))
    }

    // ---------- 最长前缀优先 ----------

    @Test
    fun `presence 走自己的规则而非 school 的`() {
        // 若前缀顺序写反（/school/ 先于 /school/presence），这里会错误地返回 SCHOOL_AUTH
        val r = d("/school/presence", 401, ApiErrorCode.SESSION_EXPIRED)
        assertEquals(SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN, r)
    }

    @Test
    fun `invalid_presence 无论 400 还是 401 都不清登录态`() {
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/school/presence", 400, ApiErrorCode.INVALID_PRESENCE),
        )
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/school/presence", 401, ApiErrorCode.INVALID_PRESENCE),
        )
    }

    // ---------- 兜底保守 ----------

    @Test
    fun `未匹配路径一律不清登录态`() {
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/healthz", 401, ApiErrorCode.SESSION_EXPIRED),
        )
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/unknown/path", 401, ApiErrorCode.UNAUTHORIZED),
        )
    }

    @Test
    fun `code 为 null 时不崩且保守`() {
        assertEquals(
            SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN,
            d("/identity/me", 401, null),
        )
    }
}
