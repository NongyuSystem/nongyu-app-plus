package com.nongyu.appplus.net.api

import com.nongyu.appplus.net.model.LoginRequestDto
import com.nongyu.appplus.net.model.NetJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 #96 契约层：`method=school` 请求**序列化形状**（纯 JVM，不联网）。
 *
 * 与 [SchoolRealBackendTest] 的分工（code-review Spec 轴第 7 条修正）：
 * - 本测试是**必跑**的形状契约：恰好四字段、无 `schoolMethod`/`wechatCode`；
 * - 真后端测试是**可选**的联调证据（后端没起时跳过），不再承担形状断言职责。
 *
 * 这样 CI 里形状契约恒有断言，不会因后端未启动而整类跳过变成假绿。
 */
class SchoolLoginShapeContractTest {

    private val json = NetJson.instance

    /** 由 S6 的 CasCredential 组装出的 `method=school` 请求体（与生产路径同一 DTO）。 */
    private fun encode(): String {
        val dto = LoginRequestDto(
            method = "school",
            deviceId = "device-uuid-1",
            goAdminTgt = "201690:TGT:TEST-PLACEHOLDER",
            cookie = "go-admin-tgt=201690:TGT:TEST-PLACEHOLDER; laravel_session=S; XSRF-TOKEN=X",
        )
        return json.encodeToString(LoginRequestDto.serializer(), dto)
    }

    @Test
    fun `method=school 请求体恰好四个字段`() {
        val obj = json.parseToJsonElement(encode()).jsonObject
        assertEquals(setOf("method", "deviceId", "goAdminTgt", "cookie"), obj.keys)
    }

    @Test
    fun `method 字段固定为 school`() {
        val obj = json.parseToJsonElement(encode()).jsonObject
        assertEquals("school", obj["method"]!!.jsonPrimitive.content)
    }

    @Test
    fun `goAdminTgt 原样透出`() {
        val obj = json.parseToJsonElement(encode()).jsonObject
        assertEquals("201690:TGT:TEST-PLACEHOLDER", obj["goAdminTgt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `cookie 携带整套同域 cookie 含 laravel_session`() {
        val obj = json.parseToJsonElement(encode()).jsonObject
        val cookie = obj["cookie"]!!.jsonPrimitive.content
        assertTrue(cookie.contains("laravel_session=S"))
        assertTrue(cookie.contains("XSRF-TOKEN=X"))
    }

    @Test
    fun `三格不切后端 method 禁用字段绝不出现`() {
        val text = encode()
        assertFalse(text.contains("schoolMethod"))
        assertFalse(text.contains("wechatCode"))
        val obj = json.parseToJsonElement(text).jsonObject
        assertFalse(obj.containsKey("schoolMethod"))
        assertFalse(obj.containsKey("wechatCode"))
    }
}
