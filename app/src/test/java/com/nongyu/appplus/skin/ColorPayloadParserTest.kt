package com.nongyu.appplus.skin

import androidx.compose.ui.graphics.Color
import com.nongyu.appplus.ui.theme.NongyuColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 色值段解析的契约测试（票 #37 / 规格 #33 的 Testing Decisions）。
 *
 * 只断言**外部行为**：给定一段键值数据，解析出什么色板 / 什么回落原因。
 * 不测内部实现，也不碰 `org.json`（它在纯 JVM 单测里是 stub，见 `OrgJsonAvailabilityProbeTest`）。
 */
class ColorPayloadParserTest {

    private val builtIn = NongyuColors.Green

    /** 一组合法的完整色值段（16 项全给），用于正例。 */
    private val fullTokens = mapOf(
        "colorBrand" to "#FF0A7C59",
        "colorBrandMuted" to "#FFD4E9DF",
        "colorOnBrand" to "#FFFFFFFF",
        "colorBackground" to "#FFF7FAF8",
        "colorCard" to "#FFFFFFFF",
        "colorText" to "#FF0F1A14",
        "colorTextSecondary" to "#FF4A564F",
        "colorOutline" to "#FFCFE3DA",
        "colorCore" to "#FFFFFFFF",
        "colorNebulaGreen" to "#FF0A7C59",
        "colorNebulaYellow" to "#FFE9C46A",
        "colorRimShade" to "#FF255A43",
        "colorGlow" to "#FF0A7C59",
        "colorContainer" to "#FFFFFFFF",
        "colorItem" to "#FF4A564F",
        "colorIndicator" to "#FF0A7C59",
    )

    private fun input(
        tokens: Map<String, String> = fullTokens,
        schemaMajor: Int? = 1,
        minApp: Int? = null,
        appVersionCode: Int = 37,
    ) = ColorPayloadInput(
        schemaMajor = schemaMajor,
        minAppVersionCode = minApp,
        appVersionCode = appVersionCode,
        rawTokens = tokens,
    )

    // ---------------------------------------------------------------- 正例

    @Test
    fun `正常色值段解析出的色板与段中色值一致`() {
        val result = ColorPayloadParser.parse(input(), builtIn)

        assertEquals(PayloadOrigin.Loaded, result.meta.origin)
        assertEquals("1", result.meta.version)
        val palette = requireNotNull(result.palette)

        // 逐组抽查（不是只测一个字段，避免局部正确整体错位）。
        assertEquals(Color(0xFF0A7C59), palette.brand.colorBrand)
        assertEquals(Color(0xFFF7FAF8), palette.surface.colorBackground)
        assertEquals(Color(0xFFE9C46A), palette.aiOrb.colorNebulaYellow)
        assertEquals(Color(0xFF0A7C59), palette.navBar.colorIndicator)
    }

    @Test
    fun `解析结果与内置默认不同，证明载荷真的被采纳`() {
        val tokens = fullTokens + ("colorBrand" to "#FFAA0011")
        val result = ColorPayloadParser.parse(input(tokens), builtIn)

        val palette = requireNotNull(result.palette)
        assertNotEquals("品牌色应被载荷覆盖", builtIn.brand.colorBrand, palette.brand.colorBrand)
        assertEquals(Color(0xFFAA0011), palette.brand.colorBrand)
    }

    // ---------------------------------------------------------------- 三层降级

    @Test
    fun `格式主版本过高时整段拒绝并回落内置默认`() {
        val result = ColorPayloadParser.parse(input(schemaMajor = 99), builtIn)

        assertNull("整段拒绝时不应返回色板", result.palette)
        assertEquals(PayloadOrigin.SchemaTooNew, result.meta.origin)
        assertTrue("应说明原因", result.meta.reason.orEmpty().contains("99"))
    }

    @Test
    fun `最低 App 版本高于当前时拒绝，且原因与版本过高可区分`() {
        val result = ColorPayloadParser.parse(
            input(minApp = 100, appVersionCode = 37),
            builtIn,
        )

        assertNull(result.palette)
        assertEquals(PayloadOrigin.AppVersionTooLow, result.meta.origin)
        assertNotEquals(
            "上下文不同（App 版本不足）不得判成 SchemaTooNew，否则日志分不清",
            PayloadOrigin.SchemaTooNew,
            result.meta.origin,
        )
    }

    @Test
    fun `核心 token 缺失时整段回落而不是逐项`() {
        val tokens = fullTokens - "colorBrand" // 拿掉一个核心项
        val result = ColorPayloadParser.parse(input(tokens), builtIn)

        assertNull("核心缺失应整段回落，不得返回部分色板", result.palette)
        assertEquals(PayloadOrigin.Invalid, result.meta.origin)
        assertTrue(
            "原因里应点名缺了哪个核心 token，实际是 ${result.meta.reason}",
            result.meta.reason.orEmpty().contains("colorBrand"),
        )
    }

    @Test
    fun `核心 token 存在但色值无法解析，同样整段回落`() {
        val tokens = fullTokens + ("colorText" to "not-a-color")
        val result = ColorPayloadParser.parse(input(tokens), builtIn)

        assertNull("核心项坏值应整段回落", result.palette)
        assertEquals(PayloadOrigin.Invalid, result.meta.origin)
    }

    @Test
    fun `非核心 token 缺失时逐项回落，其余照用`() {
        val tokens = fullTokens - "colorRimShade" // 拿掉一个非核心项
        val result = ColorPayloadParser.parse(input(tokens), builtIn)

        val palette = requireNotNull(result.palette)
        assertEquals(PayloadOrigin.Loaded, result.meta.origin)
        // 缺失的那项回落到内置默认。
        assertEquals(builtIn.aiOrb.colorRimShade, palette.aiOrb.colorRimShade)
        // 其余项仍来自载荷。
        assertEquals(Color(0xFFE9C46A), palette.aiOrb.colorNebulaYellow)
    }

    @Test
    fun `同主版本含未知字段时忽略并记 warning，不阻断`() {
        val tokens = fullTokens + ("somethingFromTheFuture" to "#FF123456")
        val result = ColorPayloadParser.parse(input(tokens), builtIn)

        assertEquals("未知字段不应阻断解析", PayloadOrigin.Loaded, result.meta.origin)
        assertTrue(
            "应记录未知 token 的 warning，实际是 ${result.warnings}",
            result.warnings.any { it.contains("somethingFromTheFuture") },
        )
    }

    @Test
    fun `缺少 schemaMajor 视为非法而不是太新`() {
        val result = ColorPayloadParser.parse(input(schemaMajor = null), builtIn)

        assertNull(result.palette)
        assertEquals(
            "无版本号应判 Invalid；判成 SchemaTooNew 会让日志误报",
            PayloadOrigin.Invalid,
            result.meta.origin,
        )
    }

    // ---------------------------------------------------------------- 色值串解析

    @Test
    fun `色值串支持六位与八位、带或不带井号`() {
        // 6 位：补成不透明
        assertEquals(Color(0xFF0A7C59), ColorPayloadParser.parseColor("#0A7C59"))
        assertEquals(Color(0xFF0A7C59), ColorPayloadParser.parseColor("0A7C59"))
        // 8 位：带 alpha 原样
        assertEquals(Color(0x800A7C59), ColorPayloadParser.parseColor("#800A7C59"))
        // 首尾空白应被容忍（服务端手填常见）
        assertEquals(Color(0xFF0A7C59), ColorPayloadParser.parseColor("  #0A7C59  "))
    }

    @Test
    fun `非法色值串返回 null 而不抛异常`() {
        val bad = listOf("", "#", "xyz", "#GGGGGG", "#12345", "#1234567", "0x0A7C59")
        for (raw in bad) {
            assertNull("'$raw' 应解析失败并返回 null（不抛异常）", ColorPayloadParser.parseColor(raw))
        }
    }
}
