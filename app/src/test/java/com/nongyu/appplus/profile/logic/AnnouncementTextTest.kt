package com.nongyu.appplus.profile.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 票 #114：RFC3339 解析与展示文案的纯 JVM 单测。
 *
 * 解析失败返回 null（按无边界处理，不判为错误）；展示文案去掉末尾的 Z，
 * 不改变时间本身。
 */
class AnnouncementTextTest {

    @Test
    fun `解析带 Z 的 RFC3339 字符串`() {
        assertEquals(1_700_000_000_000L, AnnouncementText.parseRfc3339Millis("2023-11-14T22:13:20Z"))
    }

    @Test
    fun `解析带毫秒与偏移的 RFC3339 字符串`() {
        assertEquals(1_700_000_000_123L, AnnouncementText.parseRfc3339Millis("2023-11-14T22:13:20.123Z"))
        assertEquals(1_699_971_200_000L, AnnouncementText.parseRfc3339Millis("2023-11-14T22:13:20+08:00"))
    }

    @Test
    fun `空串与非法串按无边界处理`() {
        assertNull(AnnouncementText.parseRfc3339Millis(null))
        assertNull(AnnouncementText.parseRfc3339Millis(""))
        assertNull(AnnouncementText.parseRfc3339Millis("not-a-time"))
    }

    @Test
    fun `展示文案保留原值空值给占位符`() {
        assertEquals("2023-11-14 22:13", AnnouncementText.displayTime("2023-11-14T22:13:20Z"))
        assertEquals("—", AnnouncementText.displayTime(null))
        assertEquals("—", AnnouncementText.displayTime("bad"))
    }
}
