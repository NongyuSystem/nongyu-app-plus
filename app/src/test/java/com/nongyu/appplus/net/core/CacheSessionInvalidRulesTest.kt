package com.nongyu.appplus.net.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 回传缓存端点接入后的失效判据回归（规格 §3.6 + §4.3）。
 *
 * `/school/cache/` 落在已有的 `/school/` 前缀下，天然应该走
 * 「教务域 → 不清 App 登录态」这条规则。这组测试把这件事钉住，
 * 防止将来有人给 cache 子域加前缀时误把它归到 identity 域。
 */
class CacheSessionInvalidRulesTest {

    private fun d(path: String, status: Int, code: String?) =
        SessionInvalidRules.decide(path, status, code)

    @Test
    fun `school cache 401 session_expired 不清登录态`() {
        // 教务侧会话失效，与 App 登录态无关
        val r = d("/school/cache/course", 401, ApiErrorCode.SESSION_EXPIRED)
        assertEquals(SessionInvalidRules.Decision.KEEP_SESSION_SCHOOL_AUTH, r)
        assertFalse(SessionInvalidRules.shouldClearSession(r))
    }

    @Test
    fun `school cache 401 unauthorized 也不清登录态`() {
        // 后端 requireUserID 无票回 401 unauthorized；这是请求缺凭证，不是会话结束
        val r = d("/school/cache/course", 401, ApiErrorCode.UNAUTHORIZED)
        assertEquals(SessionInvalidRules.Decision.KEEP_SESSION_UNKNOWN, r)
        assertFalse(SessionInvalidRules.shouldClearSession(r))
    }

    @Test
    fun `school cache 503 cache_unavailable 不清登录态`() {
        val r = d("/school/cache/score", 503, ApiErrorCode.CACHE_UNAVAILABLE)
        assertFalse(SessionInvalidRules.shouldClearSession(r))
    }

    @Test
    fun `school cache 429 refresh_too_soon 不清登录态`() {
        val r = d("/school/cache/score", 429, ApiErrorCode.REFRESH_TOO_SOON)
        assertFalse(SessionInvalidRules.shouldClearSession(r))
    }

    @Test
    fun `school cache 200 cache_miss 不触发任何失效判定`() {
        // cache_miss 配的是 200，正常流程；判据对它应保守不清
        val r = d("/school/cache/exam", 200, ApiErrorCode.CACHE_MISS)
        assertFalse(SessionInvalidRules.shouldClearSession(r))
    }

    @Test
    fun `identity 域仍然照常清登录态（回归，确认没被改坏）`() {
        val r = d("/identity/me", 401, ApiErrorCode.SESSION_EXPIRED)
        assertEquals(SessionInvalidRules.Decision.CLEAR_SESSION, r)
    }

    @Test
    fun `新错误码都已登记`() {
        listOf(
            ApiErrorCode.CACHE_MISS, ApiErrorCode.SCHEMA_REJECTED,
            ApiErrorCode.PAYLOAD_TOO_LARGE, ApiErrorCode.REFRESH_TOO_SOON,
            ApiErrorCode.CACHE_UNAVAILABLE,
        ).forEach { org.junit.Assert.assertTrue("$it 应已登记", ApiErrorCode.isKnown(it)) }
    }
}
