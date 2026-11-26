package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.SchoolAnnouncementView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 #114：公告生效/失效判定的纯 JVM 单测。
 *
 * 只断言外部行为：时间窗的四个边界、列表过滤的顺序保持、以及
 * latest/detail 信封 `data:null` 这一「HTTP 200 的正常空态」。
 */
class AnnouncementRulesTest {

    private fun view(
        id: String = "1",
        from: Long? = null,
        to: Long? = null,
    ) = SchoolAnnouncementView(
        id = id,
        title = "公告 $id",
        body = "正文 $id",
        publishedAt = null,
        effectiveFromRaw = null,
        effectiveFromMillis = from,
        expiresAtRaw = null,
        expiresAtMillis = to,
        pinned = false,
    )

    // ---------- 单条生效窗口 ----------

    @Test
    fun `起止时间都为空时任意时刻都生效`() {
        assertTrue(AnnouncementRules.isEffectiveAt(null, null, nowMillis = 1_000L))
        assertTrue(AnnouncementRules.isEffectiveAt(null, null, nowMillis = 9_000_000L))
    }

    @Test
    fun `只有生效起点时起点之前不生效起点及之后生效`() {
        assertFalse(AnnouncementRules.isEffectiveAt(1_000L, null, nowMillis = 999L))
        assertTrue(AnnouncementRules.isEffectiveAt(1_000L, null, nowMillis = 1_000L))
        assertTrue(AnnouncementRules.isEffectiveAt(1_000L, null, nowMillis = 1_001L))
    }

    @Test
    fun `只有失效时间时失效点之前生效失效点及其后不生效`() {
        assertTrue(AnnouncementRules.isEffectiveAt(null, 1_000L, nowMillis = 999L))
        assertFalse(AnnouncementRules.isEffectiveAt(null, 1_000L, nowMillis = 1_000L))
        assertFalse(AnnouncementRules.isEffectiveAt(null, 1_000L, nowMillis = 1_001L))
    }

    @Test
    fun `起止都给定时是左闭右开区间`() {
        assertFalse(AnnouncementRules.isEffectiveAt(1_000L, 2_000L, nowMillis = 999L))
        assertTrue(AnnouncementRules.isEffectiveAt(1_000L, 2_000L, nowMillis = 1_000L))
        assertTrue(AnnouncementRules.isEffectiveAt(1_000L, 2_000L, nowMillis = 1_999L))
        assertFalse(AnnouncementRules.isEffectiveAt(1_000L, 2_000L, nowMillis = 2_000L))
    }

    @Test
    fun `窗口颠倒时任何时刻都不生效`() {
        assertFalse(AnnouncementRules.isEffectiveAt(2_000L, 1_000L, nowMillis = 1_500L))
    }

    // ---------- 列表过滤 ----------

    @Test
    fun `effectiveOnly 只保留生效公告且保持输入顺序`() {
        val items = listOf(
            view(id = "unbounded"),
            view(id = "future", from = 2_000L),
            view(id = "current", from = 500L, to = 2_000L),
            view(id = "expired", to = 900L),
        )

        val result = AnnouncementRules.effectiveOnly(items, nowMillis = 1_000L)

        assertEquals(listOf("unbounded", "current"), result.map { it.id })
    }

    @Test
    fun `空列表或全部失效时返回空列表而不是错误`() {
        assertTrue(AnnouncementRules.effectiveOnly(emptyList(), nowMillis = 1_000L).isEmpty())
        assertTrue(
            AnnouncementRules.effectiveOnly(
                listOf(view(id = "a", to = 500L), view(id = "b", from = 2_000L)),
                nowMillis = 1_000L,
            ).isEmpty(),
        )
    }

    // ---------- 信封空态 ----------

    @Test
    fun `信封 data 为 null 是正常空态不是错误`() {
        assertTrue(AnnouncementRules.singleOrEmpty(null).isEmpty())
    }

    @Test
    fun `信封 data 非 null 时作为单条候选`() {
        val item = view()
        assertEquals(listOf(item), AnnouncementRules.singleOrEmpty(item))
    }
}
