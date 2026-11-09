package com.nongyu.appplus.ui.icon

import com.nongyu.appplus.skin.BuiltInSkin
import com.nongyu.appplus.skin.BuiltInSkinSource
import com.nongyu.appplus.skin.NavIconKey
import com.nongyu.appplus.skin.PayloadMeta
import com.nongyu.appplus.skin.ResourcePayload
import com.nongyu.appplus.skin.SkinFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标数据化的契约测试（票 #38）。
 *
 * 只测**外部行为**：给定一份资源段，渲染层拿到的线宽是多少；以及几何数据是否与
 * 改造前的手绘常量一致。不测 `DrawScope` 怎么画（那属于像素回归，由截帧做）。
 */
class NavIconStrokesTest {

    private val builtIn: ResourcePayload = BuiltInSkinSource.builtIn.resources

    // ---------------------------------------------------------------- 内置默认即改造前视觉

    @Test
    fun `内置默认下四个图标的线宽都等于改造前的手绘常量`() {
        // 这是「视觉不变」的第一道锁：改造前 MainShell.drawNavIcon 里是 `s * 0.085f`。
        val resolved = NavIconStrokes.resolve(payload = builtIn, builtIn = builtIn)

        assertEquals(
            "应恰好覆盖四个导航图标",
            NavIconKey.BuiltIn.size,
            resolved.size,
        )
        for (key in NavIconKey.BuiltIn) {
            assertEquals(
                "图标 $key 的线宽必须等于改造前的 0.085f",
                BuiltInSkin.BUILT_IN_ICON_STROKE,
                resolved.getValue(key),
                0.0001f,
            )
        }
        assertEquals(0.085f, BuiltInSkin.BUILT_IN_ICON_STROKE, 0.0001f)
    }

    @Test
    fun `空资源段（无皮肤包）时全部回落内置默认`() {
        val empty = ResourcePayload.empty(PayloadMeta.fallback(com.nongyu.appplus.skin.PayloadOrigin.Absent, "无"))

        val resolved = NavIconStrokes.resolve(payload = empty, builtIn = builtIn)

        for (key in NavIconKey.BuiltIn) {
            assertEquals(
                "无包时 $key 必须回落内置默认",
                BuiltInSkin.BUILT_IN_ICON_STROKE,
                resolved.getValue(key),
                0.0001f,
            )
        }
    }

    // ---------------------------------------------------------------- 逐资源回落（票面验收场景）

    @Test
    fun `包内只给一个图标时只该项用它，其余回落内置默认`() {
        // 票面验收：资源缺失单项 → 该图标用内置默认，其余照用。
        // 反过来测：给了的那项用包内值，没给的三项回落到内置默认。
        val payload = ResourcePayload(
            iconStrokes = mapOf(NavIconKey.Grid to 0.14f),
            font = SkinFont.SystemDefault,
            meta = PayloadMeta.loaded("v1"),
        )

        val resolved = NavIconStrokes.resolve(payload = payload, builtIn = builtIn)

        assertEquals("包内给了 Grid，必须照用", 0.14f, resolved.getValue(NavIconKey.Grid), 0.0001f)
        for (key in NavIconKey.BuiltIn.filter { it != NavIconKey.Grid }) {
            assertEquals(
                "包内没给 $key，必须回落内置默认",
                BuiltInSkin.BUILT_IN_ICON_STROKE,
                resolved.getValue(key),
                0.0001f,
            )
        }
        assertEquals("仍是四项齐全（每项一定有值）", 4, resolved.size)
    }

    @Test
    fun `包内某个图标值非法时只该项回落，其余照用`() {
        // 非法值不能"照用"：strokeWidth = 0 或 NaN 会让图标静默消失/渲染异常。
        val payload = ResourcePayload(
            iconStrokes = mapOf(
                NavIconKey.Grid to Float.NaN,
                NavIconKey.Calendar to 0f,
                NavIconKey.Compass to 0.20f,
                NavIconKey.Person to 5f,
            ),
            font = SkinFont.SystemDefault,
            meta = PayloadMeta.loaded("v1"),
        )

        val resolved = NavIconStrokes.resolve(payload = payload, builtIn = builtIn)

        assertEquals(BuiltInSkin.BUILT_IN_ICON_STROKE, resolved.getValue(NavIconKey.Grid), 0.0001f)
        assertEquals(BuiltInSkin.BUILT_IN_ICON_STROKE, resolved.getValue(NavIconKey.Calendar), 0.0001f)
        assertEquals("合法的 Compass 必须照用", 0.20f, resolved.getValue(NavIconKey.Compass), 0.0001f)
        assertEquals(BuiltInSkin.BUILT_IN_ICON_STROKE, resolved.getValue(NavIconKey.Person), 0.0001f)
    }

    @Test
    fun `内置默认自身被清空时仍有硬兜底常量`() {
        // 三层回落链的最底一层：即使内置默认资源段是空的也不能画不出图标。
        val emptyBuiltIn = ResourcePayload.empty(PayloadMeta.loaded(null))
        val emptyPayload = ResourcePayload.empty(PayloadMeta.loaded(null))

        val resolved = NavIconStrokes.resolve(payload = emptyPayload, builtIn = emptyBuiltIn)

        for (key in NavIconKey.BuiltIn) {
            assertEquals(
                0.085f,
                resolved.getValue(key),
                0.0001f,
            )
        }
    }

    // ---------------------------------------------------------------- sanitize

    @Test
    fun `sanitize 只接受有限且落在区间内的值`() {
        assertNull("null 不接受", NavIconStrokes.sanitize(null))
        assertNull("NaN 不接受", NavIconStrokes.sanitize(Float.NaN))
        assertNull("+Inf 不接受", NavIconStrokes.sanitize(Float.POSITIVE_INFINITY))
        assertNull("0 不接受（Stroke 宽度 0 画不出东西）", NavIconStrokes.sanitize(0f))
        assertNull("负数不接受", NavIconStrokes.sanitize(-0.1f))
        assertNull("大于 1 不接受", NavIconStrokes.sanitize(1.01f))
        assertEquals(0.085f, NavIconStrokes.sanitize(0.085f)!!, 0.0001f)
        assertEquals("1.0 是允许的上界", 1f, NavIconStrokes.sanitize(1f)!!, 0.0001f)
    }

    // ---------------------------------------------------------------- 几何数据化

    @Test
    fun `四个内置图标都有几何数据，且默认值与改造前手绘常量一致`() {
        // 逐个字段对齐改造前 MainShell.drawNavIcon 里的字面量。
        // 任何一项被"顺手改小一点"都会让视觉回归失败，所以这里逐值钉死。
        val grid = NavIconGeometry.builtInOf(NavIconKey.Grid) as NavIconGeometry.Grid
        assertEquals(0.12f, grid.gap, 0.0001f)
        assertEquals(0.28f, grid.cellCorner, 0.0001f)

        val cal = NavIconGeometry.builtInOf(NavIconKey.Calendar) as NavIconGeometry.Calendar
        assertEquals(0.16f, cal.left, 0.0001f)
        assertEquals(0.22f, cal.top, 0.0001f)
        assertEquals(0.16f, cal.bottomInset, 0.0001f)
        assertEquals(0.14f, cal.corner, 0.0001f)
        assertEquals(0.34f, cal.pegLeftX, 0.0001f)
        assertEquals(0.66f, cal.pegRightX, 0.0001f)
        assertEquals(0.10f, cal.pegTopY, 0.0001f)
        assertEquals(0.30f, cal.pegBottomY, 0.0001f)
        assertEquals(0.44f, cal.dividerY, 0.0001f)

        val comp = NavIconGeometry.builtInOf(NavIconKey.Compass) as NavIconGeometry.Compass
        assertEquals(0.34f, comp.radius, 0.0001f)
        assertEquals(0.20f, comp.needle, 0.0001f)
        assertEquals(0.72f, comp.needleRatio, 0.0001f)
        assertEquals(0.10f, comp.notch, 0.0001f)

        val person = NavIconGeometry.builtInOf(NavIconKey.Person) as NavIconGeometry.Person
        assertEquals(0.15f, person.headRadius, 0.0001f)
        assertEquals(0.5f, person.headCenterX, 0.0001f)
        assertEquals(0.33f, person.headCenterY, 0.0001f)
        assertEquals(0.62f, person.arcWidth, 0.0001f)
        assertEquals(0.46f, person.arcHeight, 0.0001f)
        assertEquals(0.52f, person.arcTop, 0.0001f)
    }

    @Test
    fun `未知图标键给出 Unsupported 而不是抛异常`() {
        // ADR-0003 第②层：包内出现未知图标名 → 忽略，不让反序列化/渲染炸掉。
        val unknown = NavIconGeometry.builtInOf(NavIconKey("brand-new-icon"))
        assertEquals(NavIconGeometry.Unsupported, unknown)
    }

    @Test
    fun `几何是值对象，改一处不影响另一处`() {
        // data class 语义：皮肤包将来下发自定义几何时，两份配置不会互相串味。
        val a = NavIconGeometry.Grid(gap = 0.2f)
        val b = NavIconGeometry.Grid()
        assertNotEquals(a, b)
        assertNotNull(a.copy(gap = 0.12f))
        assertEquals("copy 回原值后应与默认相等", b, a.copy(gap = 0.12f))
    }

    @Test
    fun `线宽系数确实参与几何之外的计算（数据驱动而非写死）`() {
        // 证明"换包 = 换参数"成立：纯逻辑层对同一个键给出不同值的两条路径。
        // （真正的像素差异由设备截帧验证，这里只锁数据链是通的。）
        val custom = ResourcePayload(
            iconStrokes = NavIconKey.BuiltIn.associateWith { 0.14f },
            font = SkinFont.SystemDefault,
            meta = PayloadMeta.loaded("custom"),
        )
        val resolved = NavIconStrokes.resolve(payload = custom, builtIn = builtIn)

        assertTrue(
            "包内全部为 0.14f 时，不应有任何一项等于改造前的 0.085f",
            resolved.values.all { kotlin.math.abs(it - 0.085f) > 0.001f },
        )
    }
}
