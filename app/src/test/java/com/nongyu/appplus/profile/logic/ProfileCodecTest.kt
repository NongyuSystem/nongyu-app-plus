package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.ProfileSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 画像缓存编解码与损坏容错的单测（纯 JVM，锁住「坏文件 = 无缓存」）。 */
class ProfileCodecTest {

    @Test
    fun `roundtrip 保留全部画像字段`() {
        val original = ProfileSnapshot(
            studentNo = "20260001",
            name = "张三",
            college = "信息学院",
            major = "计算机",
            className = "计科1班",
            grade = "2026",
            avatarUrl = "https://cdn/avatar.png",
            qq = "12345",
            expiresAtUnixSeconds = 9_999L,
            hasMeResponse = true,
        )

        val decoded = ProfileCodec.decode(ProfileCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `损坏或不认识的内容一律视为无缓存，不抛异常`() {
        assertNull(ProfileCodec.decode(null))
        assertNull(ProfileCodec.decode(""))
        assertNull(ProfileCodec.decode("{ not json"))
        assertNull(ProfileCodec.decode("[]"))
        assertNull(ProfileCodec.decode("null"))
    }

    @Test
    fun `旧版本缺少新字段时按默认值解码`() {
        val decoded = ProfileCodec.decode("""{"name":"张三","studentNo":"20260001"}""")
        assertEquals("张三", decoded?.name)
        assertEquals("20260001", decoded?.studentNo)
        assertNull(decoded?.avatarUrl)
        assertNull(decoded?.expiresAtUnixSeconds)
        assertFalse(decoded?.hasMeResponse ?: true)
    }

    @Test
    fun `后端新增字段不影响解码`() {
        val decoded = ProfileCodec.decode("""{"name":"张三","futureField":123}""")
        assertEquals("张三", decoded?.name)
        assertTrue(decoded?.hasMeResponse == false)
    }
}
