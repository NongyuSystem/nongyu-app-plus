package com.nongyu.appplus.skin

import com.nongyu.appplus.design.NongyuColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤数据源接口（唯一主接缝）的契约测试（票 #36 / 规格 #33）。
 *
 * 只断言**外部行为**（接口给调用方什么），不测实现细节。
 * 这些测试的存在意义是证明接缝**真的可替换** —— 这是「单一主接缝」的全部价值：
 * 上层渲染只认这个接口，测试在这一层替身即可，不碰 UI。
 */
class SkinSourceContractTest {

    /** 一个最小的替身实现：色值段来自「外部」，资源段故意缺失，用来验证两段可独立缺失。 */
    private class FakeSource(
        override val builtIn: BuiltInSkin,
        private val resolution: SkinResolution,
    ) : SkinSource {
        override fun resolve(): SkinResolution = resolution
    }

    private val builtIn: BuiltInSkin = BuiltInSkinSource.builtIn

    // ---------------------------------------------------------------- 接缝可替换性

    @Test
    fun `替身实现可替换内置默认，调用方拿到替身的数据`() {
        // 造一个与内置默认**明显不同**的色板，避免「碰巧相等」造成的假绿。
        // 用 Color(0xFF...) 直接构造，不复用 Green 的任何值。
        val customPalette = NongyuColors.Green.copy(
            brand = NongyuColors.Green.brand.copy(
                colorBrand = androidx.compose.ui.graphics.Color(0xFFAA0011),
            ),
        )
        assertNotEquals(
            "替身色板必须与内置默认不同，否则本测试会假绿",
            NongyuColors.Green,
            customPalette,
        )
        val fake = FakeSource(
            builtIn = builtIn,
            resolution = SkinResolution(
                colors = ColorPayload(
                    palette = customPalette,
                    meta = PayloadMeta.loaded(version = "fake-v1"),
                ),
                resources = null,
                builtIn = builtIn,
            ),
        )

        val resolved = fake.resolve()

        // 关键断言：`palette()` 返回替身给的那一份，而不是内置默认。
        assertEquals(customPalette, resolved.palette())
        // 且版本号确实来自替身（证明 meta 通道是通的，不是被忽略的字段）。
        assertEquals("fake-v1", resolved.colorsVersion())
    }

    @Test
    fun `SkinSource_BuiltIn 是可用对象且不恒定抛异常`() {
        // 注：`LocalSkinSource.current` 是 @Composable，纯 JVM 单测里调用不了
        // （编译期即报「Functions which invoke @Composable functions must be marked
        // with the @Composable annotation」）。所以这里只验证它能被当作静态默认值使用：
        // staticCompositionLocalOf<SkinSource> { SkinSource.BuiltIn } 要求它是非空的稳定对象。
        assertNotNull("内置实现不能为 null", SkinSource.BuiltIn)
        val a = SkinSource.BuiltIn.resolve()
        val b = SkinSource.BuiltIn.resolve()
        assertNotNull("重复取用都应拿到结果", a)
        assertNotNull("重复取用都应拿到结果", b)
        // 内置实现无 IO，两次结果应等价（值对象可比较）。
        assertEquals("内置实现应是无状态的，两次结果等价", a, b)
    }

    // ---------------------------------------------------------------- 两段各自独立缺失

    @Test
    fun `色值段存在而资源段缺失时，色值照用、资源回落内置默认`() {
        val fake = FakeSource(
            builtIn = builtIn,
            resolution = SkinResolution(
                colors = ColorPayload(builtIn.colors, PayloadMeta.loaded("v2")),
                resources = null,
                builtIn = builtIn,
            ),
        )

        val resolved = fake.resolve()

        assertEquals("v2", resolved.colorsVersion())
        // 资源段缺失不视为错误（ADR-0006 Q2=C），回落而不抛异常。
        assertNull("资源段缺失时版本应为 null", resolved.resourcesVersion())
        assertNotNull("资源段回落结果不能为 null", resolved.resourcePayload())
    }

    @Test
    fun `资源段存在而色值段缺失时，资源照用、色值回落内置默认`() {
        val fake = FakeSource(
            builtIn = builtIn,
            resolution = SkinResolution(
                colors = null,
                resources = ResourcePayload(
                    iconStrokes = mapOf(NavIconKey.Grid to 0.12f),
                    font = SkinFont.SystemDefault,
                    meta = PayloadMeta.loaded("res-v3"),
                ),
                builtIn = builtIn,
            ),
        )

        val resolved = fake.resolve()

        assertNull("色值段缺失时版本应为 null", resolved.colorsVersion())
        assertEquals("res-v3", resolved.resourcesVersion())
        // 色值回落内置默认，且**不抛异常**。
        assertSame("色值应回落内置默认色板", builtIn.colors, resolved.palette())
    }

    @Test
    fun `两段都缺失时不抛异常，全部回落内置默认`() {
        val resolved = SkinSource.BuiltIn.resolve()

        assertNull(resolved.colors)
        assertNull(resolved.resources)
        assertSame(builtIn.colors, resolved.palette())
        assertNotNull(resolved.resourcePayload())
    }

    // ---------------------------------------------------------------- 逐资源回落

    @Test
    fun `单个图标参数缺失时只该项回落，其余照用`() {
        val payload = ResourcePayload(
            iconStrokes = mapOf(NavIconKey.Grid to 0.12f), // 只给 Grid
            font = SkinFont.SystemDefault,
            meta = PayloadMeta.loaded(null),
        )

        // 有值的那项用包内值。
        assertEquals(0.12f, payload.strokeOf(NavIconKey.Grid, fallback = 0.085f), 0.0001f)
        // 缺失的那项用调用方给的 fallback（逐资源回落，ADR-0005 Q5=A），而不是整段失效。
        assertEquals(0.085f, payload.strokeOf(NavIconKey.Calendar, fallback = 0.085f), 0.0001f)
    }

    // ---------------------------------------------------------------- 日志可解释

    @Test
    fun `回落原因的元信息能区分四种成因`() {
        // ADR-0006 要求日志写清是哪一环失败，所以枚举必须能各自表达。
        val cases = listOf(
            PayloadOrigin.Absent,
            PayloadOrigin.IntegrityFailed,
            PayloadOrigin.SchemaTooNew,
            PayloadOrigin.AppVersionTooLow,
        )

        for (origin in cases) {
            val meta = PayloadMeta.fallback(origin, "测试原因")
            val line = meta.describe("colors")
            assertTrue("描述应包含成因 $origin，实际是 $line", line.contains(origin.name))
            assertTrue("描述应包含原因，实际是 $line", line.contains("测试原因"))
            assertFalse("回落状态不应标记为已加载", meta.origin.isLoaded)
        }
    }

    @Test
    fun `两通道的状态能分别记录在一行里`() {
        val resolved = SkinResolution(
            colors = ColorPayload(builtIn.colors, PayloadMeta.loaded("c1")),
            resources = null,
            builtIn = builtIn,
        )

        val line = resolved.describe()

        assertTrue("应含色值通道状态，实际是 $line", line.contains("colors"))
        assertTrue("应含资源通道状态，实际是 $line", line.contains("resources"))
        // 两通道版本不同或一有一无，不算错误（ADR-0006）。
        assertTrue("色值已加载应体现在描述里，实际是 $line", line.contains("c1"))
    }

    // ---------------------------------------------------------------- 内置默认即既有视觉

    @Test
    fun `内置默认皮肤的色板就是既有的 NongyuColors_Green 同一对象`() {
        // 这条守住「换接口不改变视觉」：内置默认不是重新设计的一套颜色，
        // 而是既有常量本身（同一引用）。
        assertSame(
            "内置默认色板必须是既有 NongyuColors.Green 本身，而不是拷贝",
            NongyuColors.Green,
            builtIn.colors,
        )
    }

    @Test
    fun `内置默认皮肤覆盖导航壳全部四个图标`() {
        // 少一个就意味着该项会走 fallback，属于「内置默认不完整」。
        for (key in NavIconKey.BuiltIn) {
            assertTrue(
                "内置默认皮肤应覆盖图标 $key",
                builtIn.resources.iconStrokes.containsKey(key),
            )
        }
        assertEquals(
            "内置默认应恰好覆盖四个导航图标",
            NavIconKey.BuiltIn.size,
            builtIn.resources.iconStrokes.size,
        )
    }

    @Test
    fun `内置默认的图标线宽沿用主工程既有常量`() {
        // MainShell.drawNavIcon 里是 `s * 0.085f`，改这里等于改视觉。
        assertEquals(0.085f, BuiltInSkin.BUILT_IN_ICON_STROKE, 0.0001f)
        for (key in NavIconKey.BuiltIn) {
            assertEquals(
                0.085f,
                builtIn.resources.iconStrokes.getValue(key),
                0.0001f,
            )
        }
    }

    @Test
    fun `内置默认字体是系统默认`() {
        assertSame(SkinFont.SystemDefault, builtIn.resources.font)
    }
}
