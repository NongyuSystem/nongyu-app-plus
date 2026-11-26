package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.ProfileSnapshot
import com.nongyu.appplus.profile.model.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像纯判定接缝的单测（票 #112 / 规格 #111 Testing Decisions）。
 *
 * 只断言公开 seam 的外部行为，覆盖易错的字段缺失组合与 `when` 顺序。
 */
class ProfileLogicTest {

    // ---------- 字段合并 ----------

    @Test
    fun `两份互补数据合并：学号只在登录响应，头像与 QQ 只在 me`() {
        val login = ProfileSnapshot(
            studentNo = "20260001",
            name = "张三",
            college = "信息学院",
            major = "计算机",
            className = "计科1班",
            grade = "2026",
        )
        val me = ProfileSnapshot(
            avatarUrl = "https://cdn/avatar.png",
            qq = "12345",
            hasMeResponse = true,
        )

        val merged = ProfileLogic.merge(login, me)

        assertEquals("20260001", merged.studentNo)
        assertEquals("张三", merged.name)
        assertEquals("信息学院", merged.college)
        assertEquals("计算机", merged.major)
        assertEquals("计科1班", merged.className)
        assertEquals("2026", merged.grade)
        assertEquals("https://cdn/avatar.png", merged.avatarUrl)
        assertEquals("12345", merged.qq)
        assertTrue(merged.hasMeResponse)
    }

    @Test
    fun `同字段两边都有时 me 优先覆盖登录响应`() {
        val login = ProfileSnapshot(name = "旧名字", major = "旧专业")
        val me = ProfileSnapshot(name = "新名字", major = "新专业")

        val merged = ProfileLogic.merge(login, me)

        assertEquals("新名字", merged.name)
        assertEquals("新专业", merged.major)
    }

    @Test
    fun `me 侧字段为空字符串时不盖掉登录响应已有的值`() {
        val login = ProfileSnapshot(name = "登录名", college = "登录学院")
        val me = ProfileSnapshot(name = "", college = "   ", studentNo = "   ")

        val merged = ProfileLogic.merge(login, me)

        assertEquals("登录名", merged.name)
        assertEquals("登录学院", merged.college)
        assertNull("登录侧本就没有学号，不应被 me 的空值造出来", merged.studentNo)
    }

    @Test
    fun `只有登录响应时 hasMeResponse 为 false，只有 me 时仍可保留头像`() {
        val onlyLogin = ProfileLogic.merge(ProfileSnapshot(name = "张三"), null)
        assertFalse(onlyLogin.hasMeResponse)
        assertEquals("张三", onlyLogin.name)

        val onlyMe = ProfileLogic.merge(null, ProfileSnapshot(avatarUrl = "url", hasMeResponse = true))
        assertTrue(onlyMe.hasMeResponse)
        assertEquals("url", onlyMe.avatarUrl)
    }

    @Test
    fun `过期时间只从登录响应带入，不被 me 覆盖`() {
        val login = ProfileSnapshot(studentNo = "20260001", expiresAtUnixSeconds = 9_999L)
        val me = ProfileSnapshot(name = "张三", expiresAtUnixSeconds = 1L, hasMeResponse = true)
        val merged = ProfileLogic.merge(login, me)
        assertEquals(9_999L, merged.expiresAtUnixSeconds)
    }

    @Test
    fun `存储合并：登录响应与 me 两次落盘互补不互相抹掉`() {
        val fromLogin = ProfileSnapshot(studentNo = "20260001", name = "张三")
        val afterLogin = ProfileLogic.mergeForStorage(ProfileSnapshot(), fromLogin)
        val fromMe = ProfileSnapshot(avatarUrl = "url", qq = "123", hasMeResponse = true, name = "")
        val afterMe = ProfileLogic.mergeForStorage(afterLogin, fromMe)

        assertEquals("20260001", afterMe.studentNo)
        assertEquals("张三", afterMe.name)
        assertEquals("url", afterMe.avatarUrl)
        assertEquals("123", afterMe.qq)
        assertTrue(afterMe.hasMeResponse)
    }

    @Test
    fun `存储合并：非空覆盖旧值，空串保留旧值`() {
        val old = ProfileSnapshot(name = "旧名", major = "旧专业")
        val merged = ProfileLogic.mergeForStorage(old, ProfileSnapshot(name = "新名", major = "  "))
        assertEquals("新名", merged.name)
        assertEquals("旧专业", merged.major)
    }

    @Test
    fun `两边都为空返回空快照`() {
        val empty = ProfileLogic.merge(null, null)
        assertFalse(ProfileLogic.isCacheNonEmpty(empty))
        assertFalse(empty.hasMeResponse)
    }

    // ---------- 缓存非空判定 ----------

    @Test
    fun `缓存非空判定：任一画像字段有内容即为非空`() {
        assertFalse("null 缓存为空", ProfileLogic.isCacheNonEmpty(null))
        assertFalse("全字段缺失为空", ProfileLogic.isCacheNonEmpty(ProfileSnapshot()))
        assertFalse(
            "全空白字段也为空",
            ProfileLogic.isCacheNonEmpty(ProfileSnapshot(name = "  ", qq = "\t")),
        )
        assertTrue(
            "只有 avatarUrl 也算非空",
            ProfileLogic.isCacheNonEmpty(ProfileSnapshot(avatarUrl = "x")),
        )
        assertTrue(
            "只有学号也算非空",
            ProfileLogic.isCacheNonEmpty(ProfileSnapshot(studentNo = "20260001")),
        )
    }

    // ---------- 三态推导（when 顺序） ----------

    @Test
    fun `无凭证优先于一切：即使已过期或缺数据也判未登录`() {
        val state = ProfileLogic.resolveState(
            hasCredential = false,
            profile = ProfileSnapshot(),
            expiresAtUnixSeconds = 1L,
            nowEpochSeconds = 100L,
        )
        assertEquals(ProfileState.NOT_SIGNED_IN, state)
    }

    @Test
    fun `有凭证但已过期优先于数据不全，判会话过期`() {
        val state = ProfileLogic.resolveState(
            hasCredential = true,
            profile = ProfileSnapshot(), // 数据也缺
            expiresAtUnixSeconds = 100L,
            nowEpochSeconds = 101L,
        )
        assertEquals(ProfileState.SESSION_EXPIRED, state)
    }

    @Test
    fun `已登录但未到过期时间、画像缺关键字段判数据不全`() {
        val missingName = ProfileLogic.resolveState(
            hasCredential = true,
            profile = ProfileSnapshot(studentNo = "20260001"),
            expiresAtUnixSeconds = 1000L,
            nowEpochSeconds = 100L,
        )
        assertEquals(ProfileState.INCOMPLETE, missingName)

        val missingStudentNo = ProfileLogic.resolveState(
            hasCredential = true,
            profile = ProfileSnapshot(name = "张三"),
            expiresAtUnixSeconds = 1000L,
            nowEpochSeconds = 100L,
        )
        assertEquals(ProfileState.INCOMPLETE, missingStudentNo)
    }

    @Test
    fun `已登录且画像完整且未过期判 READY`() {
        val state = ProfileLogic.resolveState(
            hasCredential = true,
            profile = ProfileSnapshot(name = "张三", studentNo = "20260001"),
            expiresAtUnixSeconds = 1000L,
            nowEpochSeconds = 100L,
        )
        assertEquals(ProfileState.READY, state)
    }

    @Test
    fun `expiresAt 缺失时退回画像完整度判定，不判过期`() {
        val state = ProfileLogic.resolveState(
            hasCredential = true,
            profile = ProfileSnapshot(name = "张三", studentNo = "20260001"),
            expiresAtUnixSeconds = null,
            nowEpochSeconds = 100L,
        )
        assertEquals(ProfileState.READY, state)
    }

    // ---------- 剩余期 ----------

    @Test
    fun `剩余期从透传的过期时间算出：秒与整分钟向下取整`() {
        val r = ProfileLogic.remaining(expiresAtUnixSeconds = 1000L, nowEpochSeconds = 100L)
        assertEquals(900L, r.totalSeconds)
        assertEquals(15L, r.totalMinutes)
        assertTrue(r.isUnderDay)
        assertFalse(r.isExpired)

        val notWholeMinute = ProfileLogic.remaining(expiresAtUnixSeconds = 1000L, nowEpochSeconds = 101L)
        assertEquals(899L, notWholeMinute.totalSeconds)
        assertEquals(14L, notWholeMinute.totalMinutes)
    }

    @Test
    fun `剩余期边界：一天以上不算 underDay，已过期与缺失都归零`() {
        val day = ProfileLogic.remaining(expiresAtUnixSeconds = 86_400L, nowEpochSeconds = 0L)
        assertEquals(86_400L, day.totalSeconds)
        assertFalse("正好一天不算不足一天", day.isUnderDay)
        assertFalse(day.isExpired)

        val lessThanDay = ProfileLogic.remaining(expiresAtUnixSeconds = 86_399L, nowEpochSeconds = 0L)
        assertTrue(lessThanDay.isUnderDay)

        val expired = ProfileLogic.remaining(expiresAtUnixSeconds = 100L, nowEpochSeconds = 100L)
        assertEquals(0L, expired.totalSeconds)
        assertTrue(expired.isExpired)

        val missing = ProfileLogic.remaining(expiresAtUnixSeconds = null, nowEpochSeconds = 100L)
        assertEquals(0L, missing.totalSeconds)
        assertTrue(missing.isExpired)
    }
}
