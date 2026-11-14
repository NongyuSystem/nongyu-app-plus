package com.nongyu.appplus.login.data.cas

import com.nongyu.appplus.net.model.NetJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CasSmsSendBody] 序列化契约。
 *
 * ## 钉死什么
 * `NetJson` **没有**开 `encodeDefaults`，带默认值的字段不会被序列化。
 * 学校 `POST /api/v1/sms/send` 要求 `type` 必填，缺了会回 `500 验证码错误`
 * （实测：uuid/code 都对但缺 type 仍然失败）。
 *
 * 所以这里断言「序列化出的 JSON 里一定有 type 字段」——一旦有人把
 * `type` 改回带默认值且调用点不传，这条测试会立刻红。
 */
class CasSmsSendBodyTest {

    private fun encode(body: CasSmsSendBody): Map<String, String> {
        val text = NetJson.instance.encodeToString(CasSmsSendBody.serializer(), body)
        return NetJson.instance.parseToJsonElement(text).jsonObject
            .mapValues { it.value.jsonPrimitive.content }
    }

    @Test
    fun `序列化结果包含 type 字段`() {
        val json = encode(
            CasSmsSendBody(
                uuid = "UUID-X",
                code = "5203",
                type = CasSmsSendBody.SMS_TYPE_LOGIN,
                phone = "13800000000",
            ),
        )
        assertTrue("JSON 必须含 type（学校侧必填）", json.containsKey("type"))
        assertEquals("login", json["type"])
    }

    @Test
    fun `四个字段齐全`() {
        val json = encode(
            CasSmsSendBody(
                uuid = "UUID-X",
                code = "5203",
                type = CasSmsSendBody.SMS_TYPE_LOGIN,
                phone = "13800000000",
            ),
        )
        assertEquals(setOf("uuid", "code", "type", "phone"), json.keys)
        assertEquals("UUID-X", json["uuid"])
        assertEquals("5203", json["code"])
        assertEquals("13800000000", json["phone"])
    }

    @Test
    fun `type 常量是 login`() {
        assertEquals("login", CasSmsSendBody.SMS_TYPE_LOGIN)
    }
}
