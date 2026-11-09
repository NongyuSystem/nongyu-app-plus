package com.nongyu.appplus.net.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DTO 反序列化的纯 JVM 单测（规格 §7.1）。
 *
 * 用与生产一致的 `Json` 配置验证四条硬规则：
 * - 未知字段被忽略（后端加字段不炸客户端）
 * - 缺字段 / 显式 null / 零值 三种输入一律得到 null 或默认值
 * - `expiresAt` 同名不同型（identity 是 Long，school 是 String）
 */
class DtoParsingTest {

    /** 与生产一致的配置（规格 §3.4 规则 4）。 */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // ---------- 未知字段被忽略 ----------

    @Test
    fun `后端新增字段不会炸客户端`() {
        val raw = """{"status":"ok","brandNewField":123,"nested":{"a":1}}"""
        val dto = json.decodeFromString(HealthzDto.serializer(), raw)
        assertEquals("ok", dto.status)
    }

    // ---------- 缺字段 / null / 零值 ----------

    @Test
    fun `缺字段时取默认 null`() {
        val dto = json.decodeFromString(ProfileDto.serializer(), """{"name":"张三"}""")
        assertEquals("张三", dto.name)
        assertNull(dto.studentNo)
        assertNull(dto.major)
    }

    @Test
    fun `显式 null 与缺字段等价`() {
        val withNull = json.decodeFromString(ProfileDto.serializer(), """{"name":null}""")
        val missing = json.decodeFromString(ProfileDto.serializer(), """{}""")
        assertNull(withNull.name)
        assertNull(missing.name)
    }

    @Test
    fun `零值不会变成 null`() {
        // Go 的零值 "" 会原样出现；DTO 层不做「零值当缺失」的转换，交给调用方按业务判断
        val dto = json.decodeFromString(ProfileDto.serializer(), """{"name":""}""")
        assertEquals("", dto.name)
    }

    // ---------- expiresAt 同名不同型 ----------

    @Test
    fun `identity 的 expiresAt 是 Unix 秒 Long`() {
        val raw = """{"token":"t","expiresAt":1763000000,"isNew":true}"""
        val dto = json.decodeFromString(LoginDto.serializer(), raw)
        assertEquals(1763000000L, dto.expiresAt)
    }

    @Test
    fun `school 的 expiresAt 是 RFC3339 字符串`() {
        val raw = """{"ticket":"","expiresAt":"2026-11-18T13:38:33.6860073+08:00"}"""
        val dto = json.decodeFromString(SchoolLoginDto.serializer(), raw)
        assertEquals("2026-11-18T13:38:33.6860073+08:00", dto.expiresAt)
    }

    // ---------- 错误信封两种形状 ----------

    @Test
    fun `只有 code 的错误信封`() {
        val dto = json.decodeFromString(ApiErrorDto.serializer(), """{"code":"unauthorized"}""")
        assertEquals("unauthorized", dto.code)
        assertNull(dto.message)
    }

    @Test
    fun `code 加 message 的错误信封`() {
        val dto = json.decodeFromString(
            ApiErrorDto.serializer(),
            """{"code":"target_denied","message":"未知逻辑目标名"}""",
        )
        assertEquals("target_denied", dto.code)
        assertEquals("未知逻辑目标名", dto.message)
    }

    // ---------- 键名照抄（不加 @SerialName） ----------

    @Test
    fun `presence 的 userID 保持大写 ID`() {
        val dto = json.decodeFromString(
            PresenceReportDto.serializer(),
            """{"userID":"42","mode":"direct"}""",
        )
        assertEquals("42", dto.userID)
    }

    @Test
    fun `identity 与 school 的 logout 键名不同`() {
        val identity = json.decodeFromString(LogoutAckDto.serializer(), """{"ok":true}""")
        val school = json.decodeFromString(SchoolLogoutAckDto.serializer(), """{"status":"ok"}""")
        assertEquals(true, identity.ok)
        assertEquals("ok", school.status)
    }
}
