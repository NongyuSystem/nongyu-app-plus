package com.nongyu.appplus.net.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回传缓存 DTO 的纯 JVM 单测（规格 §3.6）。
 *
 * 重点覆盖三件最容易错的事：
 * 1. **GET 的两条 200 形状**能不能被 [CacheGetResponse.discriminate] 正确分开；
 * 2. `payload` 用 `JsonElement` 能否承接**数组 / 对象 / null** 三种顶层形态；
 * 3. 15 个 dataType 受控词表是否与后端一致、不接受未知名。
 */
class CacheDtoTest {

    /** 与生产一致的配置（规格 §3.4 规则 4）。 */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // ---------- 双 200 形状判别（本子域最关键的一条） ----------

    @Test
    fun `命中形状被判别为 Hit`() {
        val raw = """{
            "payload":[{"name":"高等数学","credit":"4"}],
            "success":true,
            "schemaVersion":1,
            "fetchedAt":"2026-11-11T09:00:00Z",
            "stale":false
        }"""

        val r = json.decodeFromString(CacheGetResponse.serializer(), raw).discriminate()

        assertTrue("应为 Hit，实际 $r", r is CacheResult.Hit)
        val e = (r as CacheResult.Hit).envelope
        assertEquals(1, e.schemaVersion)
        assertEquals("2026-11-11T09:00:00Z", e.fetchedAt)
        assertEquals(false, e.stale)
        assertEquals(true, e.success)
        assertTrue("payload 应为数组", e.payload is JsonArray)
    }

    @Test
    fun `cache_miss 形状被判别为 Miss，且是正常流程不是错误`() {
        // ⚠️ 后端未命中回的是 HTTP 200，不是 404
        val raw = """{
            "code":"cache_miss",
            "message":"没有可用缓存",
            "address":"https://encoded.example/jiaowu"
        }"""

        val r = json.decodeFromString(CacheGetResponse.serializer(), raw).discriminate()

        assertTrue("应为 Miss，实际 $r", r is CacheResult.Miss)
        val m = r as CacheResult.Miss
        assertEquals("cache_miss", m.code)
        assertEquals("https://encoded.example/jiaowu", m.address)
        assertTrue(m.isCacheMiss())
        assertEquals("没有可用缓存", m.message)
    }

    @Test
    fun `cache_miss 无 address 时也不崩`() {
        val r = json.decodeFromString(
            CacheGetResponse.serializer(),
            """{"code":"cache_miss","message":"没有可用缓存"}""",
        ).discriminate()

        assertTrue(r is CacheResult.Miss)
        assertNull((r as CacheResult.Miss).address)
    }

    @Test
    fun `非 cache_miss 的 code 也算 Miss 但不是正常未命中`() {
        val r = json.decodeFromString(
            CacheGetResponse.serializer(),
            """{"code":"cache_unavailable","message":"缓存服务未启用"}""",
        ).discriminate()

        assertTrue(r is CacheResult.Miss)
        assertFalse((r as CacheResult.Miss).isCacheMiss())
    }

    @Test
    fun `后端新增字段不影响判别`() {
        val raw = """{"payload":{"a":1},"futureField":123,"another":{"x":true}}"""
        val r = json.decodeFromString(CacheGetResponse.serializer(), raw).discriminate()
        assertTrue(r is CacheResult.Hit)
        assertTrue((r as CacheResult.Hit).envelope.payload is JsonObject)
    }

    // ---------- payload 三种顶层形态 ----------

    @Test
    fun `payload 是数组`() {
        val e = json.decodeFromString(
            CacheEnvelope.serializer(),
            """{"payload":[1,2,3],"schemaVersion":1,"fetchedAt":"t","stale":false}""",
        )
        assertTrue(e.payload is JsonArray)
        assertEquals(3, (e.payload as JsonArray).size)
    }

    @Test
    fun `payload 是单对象`() {
        val e = json.decodeFromString(
            CacheEnvelope.serializer(),
            """{"payload":{"gpa":"3.8"},"schemaVersion":1,"fetchedAt":"t","stale":false}""",
        )
        assertTrue(e.payload is JsonObject)
    }

    @Test
    fun `payload 是 null 而不是被当成缺字段炸掉`() {
        // rank / personal / teacherInfo 三类单对象可为 null（后端清单已证实）。
        //
        // ⚠️ 实测语义：在 `explicitNulls = false` 下，kotlinx.serialization 把
        // 显式 `"payload": null` 解析成 **Kotlin null**（不是 `JsonNull`），
        // 与「字段缺失」完全等价。这正好符合规格 §3.4 硬规则 2
        // 「客户端不区分缺字段与显式 null」。
        // 对业务而言两者都意味着「没有可用数据」，等价是安全的 —— 不要试图去区分。
        val e = json.decodeFromString(
            CacheEnvelope.serializer(),
            """{"payload":null,"schemaVersion":1,"fetchedAt":"t","stale":false}""",
        )
        assertNull(e.payload)

        // 与「缺 payload 字段」的结果一致
        val missing = json.decodeFromString(
            CacheEnvelope.serializer(),
            """{"schemaVersion":1,"fetchedAt":"t","stale":false}""",
        )
        assertEquals(e.payload, missing.payload)
    }

    @Test
    fun `success 用 omitempty 缺字段时为 null`() {
        val e = json.decodeFromString(
            CacheEnvelope.serializer(),
            """{"payload":[],"schemaVersion":1,"fetchedAt":"t","stale":false}""",
        )
        assertNull(e.success)
    }

    @Test
    fun `stale 只在 fallback 时为 true`() {
        val raw = """{"payload":[],"schemaVersion":1,"fetchedAt":"t","stale":true}"""
        val e = json.decodeFromString(CacheEnvelope.serializer(), raw)
        assertEquals(true, e.stale)
    }

    // ---------- DELETE / POST 响应 ----------

    @Test
    fun `DELETE 响应 invalidated 可空`() {
        val dto = json.decodeFromString(CacheInvalidateDto.serializer(), """{"invalidated":true}""")
        assertEquals(true, dto.invalidated)
    }

    @Test
    fun `POST 成功响应与 GET 命中同形状`() {
        val raw = """{"payload":{"ok":true},"schemaVersion":2,"fetchedAt":"t","stale":false}"""
        val dto = json.decodeFromString(CacheSubmitResponse.serializer(), raw)
        assertEquals(2, dto.schemaVersion)
        assertTrue(dto.payload is JsonObject)
    }

    // ---------- 15 个 dataType 受控词表 ----------

    @Test
    fun `词表恰好 15 个且与后端 wire 值一致`() {
        val expected = listOf(
            "course", "score", "exam", "progress", "rank", "plan", "personal",
            "notice", "teachingNotice", "competition", "classroomInfo", "teacherInfo",
            "teacherCourse", "classroomCourse", "kaike",
        )
        assertEquals(expected, CacheDataType.ALL.map { it.wireValue })
    }

    @Test
    fun `fromWire 精确匹配，大小写不符一律拒绝`() {
        assertEquals(CacheDataType.COURSE, CacheDataType.fromWire("course"))
        assertEquals(CacheDataType.TEACHING_NOTICE, CacheDataType.fromWire("teachingNotice"))
        assertNull(CacheDataType.fromWire("Course"))
        assertNull(CacheDataType.fromWire("COURSE"))
        assertNull(CacheDataType.fromWire("teachingnotice"))
    }

    @Test
    fun `未知名字与空串不合法`() {
        assertFalse(CacheDataType.isValid(null))
        assertFalse(CacheDataType.isValid(""))
        assertFalse(CacheDataType.isValid("unknown"))
        assertFalse(CacheDataType.isValid("../../etc/passwd"))
        assertTrue(CacheDataType.isValid("kaike"))
    }

    @Test
    fun `wireValue 无重复`() {
        assertEquals(
            CacheDataType.ALL.size,
            CacheDataType.ALL.map { it.wireValue }.toSet().size,
        )
    }
}
