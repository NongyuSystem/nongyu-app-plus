package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.ProfileDetailExit
import com.nongyu.appplus.profile.model.ProfileFieldKey
import com.nongyu.appplus.profile.model.ProfileFieldItem
import com.nongyu.appplus.profile.model.ProfileSnapshot
import com.nongyu.appplus.profile.model.SessionRemaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 #113：画像卡 / 详情卡的纯判定接缝单测（纯 JVM，不碰 Android）。
 *
 * 只断言外部行为：哪些字段上卡、复制文案、详情标题、退出目标、
 * 剩余期文案与拖拽退出阈值。敏感字段（身份证后四位）永远不许出现在可复制项里。
 */
class ProfileFieldsTest {

    private val full = ProfileSnapshot(
        studentNo = "20260001",
        name = "张三",
        college = "信息学院",
        major = "计算机科学与技术",
        className = "计科 2026-1 班",
        grade = "2026 级",
        avatarUrl = "https://cdn/avatar.png",
        qq = "12345",
        expiresAtUnixSeconds = 1_700_000_000L,
        hasMeResponse = true,
    )

    // ---------- 字段取舍 ----------

    @Test
    fun `画像卡字段固定顺序且学院作为补充字段`() {
        val fields = ProfileFields.of(full)

        assertEquals(
            listOf(
                ProfileFieldKey.NAME,
                ProfileFieldKey.STUDENT_NO,
                ProfileFieldKey.CLASS_NAME,
                ProfileFieldKey.MAJOR,
                ProfileFieldKey.GRADE,
                ProfileFieldKey.COLLEGE,
            ),
            fields.map { it.key },
        )
        assertEquals("张三", fields.first { it.key == ProfileFieldKey.NAME }.value)
        assertEquals("20260001", fields.first { it.key == ProfileFieldKey.STUDENT_NO }.value)
    }

    @Test
    fun `空字段与纯空白字段不上卡，其余字段保留`() {
        val fields = ProfileFields.of(full.copy(name = "  ", major = "", className = null))

        val keys = fields.map { it.key }
        assertFalse(ProfileFieldKey.NAME in keys)
        assertFalse(ProfileFieldKey.MAJOR in keys)
        assertFalse(ProfileFieldKey.CLASS_NAME in keys)
        assertTrue(ProfileFieldKey.STUDENT_NO in keys)
        assertTrue(ProfileFieldKey.GRADE in keys)
    }

    @Test
    fun `身份敏感字段永不进入画像卡，也不可能被复制`() {
        val fields = ProfileFields.of(full)
        assertFalse(fields.any { it.key == ProfileFieldKey.ID_CARD_SUFFIX })

        val forged = ProfileFieldItem(
            key = ProfileFieldKey.ID_CARD_SUFFIX,
            label = "身份证后四位",
            value = "1234",
        )
        assertFalse(ProfileFields.isCopyable(forged))
        assertNull(ProfileFields.copyToastOf(forged))
    }

    @Test
    fun `空快照没有任何可上卡字段`() {
        assertTrue(ProfileFields.of(null).isEmpty())
        assertTrue(ProfileFields.of(ProfileSnapshot()).isEmpty())
    }

    // ---------- 复制文案 ----------

    @Test
    fun `复制文案明确到具体字段，不是整张名片`() {
        val fields = ProfileFields.of(full)
        assertEquals("已复制姓名", ProfileFields.copyToastOf(fields[0]))
        assertEquals("已复制学号", ProfileFields.copyToastOf(fields[1]))
        assertEquals("已复制班级", ProfileFields.copyToastOf(fields[2]))
        assertEquals("已复制专业", ProfileFields.copyToastOf(fields[3]))
        assertEquals("已复制年级", ProfileFields.copyToastOf(fields[4]))
        assertEquals("已复制学院", ProfileFields.copyToastOf(fields[5]))
        assertFalse(ProfileFields.copyToastOf(fields[1])!!.contains("名片"))
    }

    @Test
    fun `可复制判定：非空且非敏感字段才可复制`() {
        val fields = ProfileFields.of(full)
        assertTrue(fields.all { ProfileFields.isCopyable(it) })
        assertFalse(ProfileFields.isCopyable(fields[1].copy(value = "  ")))
    }

    // ---------- 详情标题与退出目标 ----------

    @Test
    fun `详情标题逐字段命名`() {
        val fields = ProfileFields.of(full)
        assertEquals("姓名详情", ProfileFields.detailTitleOf(fields[0]))
        assertEquals("学号详情", ProfileFields.detailTitleOf(fields[1]))
        assertEquals("班级详情", ProfileFields.detailTitleOf(fields[2]))
        assertEquals("专业详情", ProfileFields.detailTitleOf(fields[3]))
        assertEquals("年级详情", ProfileFields.detailTitleOf(fields[4]))
        assertEquals("学院详情", ProfileFields.detailTitleOf(fields[5]))
    }

    @Test
    fun `退出目标：详情开则回画像卡，未开则无动作`() {
        assertEquals(ProfileDetailExit.BACK_TO_CARD, ProfileFields.exitTargetOf(detailOpen = true))
        assertEquals(ProfileDetailExit.NONE, ProfileFields.exitTargetOf(detailOpen = false))
    }

    @Test
    fun `详情按字段查找：找不到的字段不渲染详情`() {
        assertNull(ProfileFields.fieldByKey(ProfileFields.of(full), ProfileFieldKey.ID_CARD_SUFFIX))
        assertEquals(
            ProfileFieldKey.STUDENT_NO,
            ProfileFields.fieldByKey(ProfileFields.of(full), ProfileFieldKey.STUDENT_NO)?.key,
        )
    }

    // ---------- 详情拖拽退出 ----------

    @Test
    fun `详情卡上滑或下滑越过阈值都退出，微小拖动不退出`() {
        val threshold = 72f
        assertTrue(ProfileFields.dismissByDrag(dragDp = 80f, thresholdDp = threshold))
        assertTrue(ProfileFields.dismissByDrag(dragDp = -80f, thresholdDp = threshold))
        assertFalse(ProfileFields.dismissByDrag(dragDp = 40f, thresholdDp = threshold))
        assertFalse(ProfileFields.dismissByDrag(dragDp = -40f, thresholdDp = threshold))
        assertFalse(ProfileFields.dismissByDrag(dragDp = 0f, thresholdDp = threshold))
    }

    // ---------- 登录剩余期 ----------

    @Test
    fun `剩余期文案按精度分档`() {
        assertEquals(
            "登录剩余 12 天 3 小时",
            ProfileFields.remainingLabelOf(SessionRemaining(17_460L, 1_047_600L, false, false)),
        )
        assertEquals(
            "登录剩余 45 分钟",
            ProfileFields.remainingLabelOf(SessionRemaining(45L, 2_700L, true, false)),
        )
        assertEquals(
            "登录剩余 30 秒",
            ProfileFields.remainingLabelOf(SessionRemaining(0L, 30L, true, false)),
        )
        assertEquals("登录已过期", ProfileFields.remainingLabelOf(SessionRemaining(0L, 0L, true, true)))
    }

    @Test
    fun `剩余期无法判定时不上卡`() {
        assertNull(ProfileFields.remainingLabelOf(null))
    }

    // ---------- demo 兜底 ----------

    @Test
    fun `只有真实缓存为空时才用 demo，且开关关闭时永不使用`() {
        val real = ProfileSnapshot(name = "真实用户", studentNo = "1")
        val demo = ProfileSnapshot(name = "演示用户", studentNo = "demo")

        assertEquals(real, ProfileFields.demoFallback(real, demo, enabled = true))
        assertEquals(demo, ProfileFields.demoFallback(null, demo, enabled = true))
        assertEquals(ProfileSnapshot(), ProfileFields.demoFallback(null, demo, enabled = false))
    }

    @Test
    fun `demo 生效时按已登录展示，真实态不改变凭证判定`() {
        assertTrue(ProfileFields.hasEffectiveCredential(false, demoActive = true))
        assertFalse(ProfileFields.hasEffectiveCredential(false, demoActive = false))
        assertTrue(ProfileFields.hasEffectiveCredential(true, demoActive = false))
    }
}
