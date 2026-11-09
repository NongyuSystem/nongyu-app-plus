package com.nongyu.appplus.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **真实的两路径一致性契约测试**（票 #57 ③ / ADR-0005 Q3=B）。
 *
 * 与 [SkinPackTest] 里「只测 planner」的旧用例不同：这里直接驱动
 * [SkinPackLoader.assemble] —— 也就是生产里 API 30+（[ResourcesLoaderPackByteSource]）
 * 与 API 26–29（[FilePackByteSource]）两条路径**共同走的那段「取字节 → 校验 → 装配」**。
 *
 * 做法：给同一个 [SkinPackLoader.assemble] 喂两个**取字节实现不同、字节内容完全相同**的
 * [PackByteSource]，断言产出的 [LoadedResources] 相等。这样「同一份包、同一份期望产物」
 * 在纯 JVM 单测里就真的被验证了，且完全不依赖 Android Framework
 * （`Resources` / `Typeface` 相关 API 在单测里不可用，故只测「取字节」这一抽象层）。
 */
class SkinPackLoaderTest {

    private val builtIn = BuiltInSkinSource.builtIn.resources
    private val passAll = PackSignatureVerifier { _, _ -> true }

    /** 模拟「API 26–29 文件解码」路径：字节来自一个内存 Map。 */
    private class MapByteSource(private val bytes: Map<String, ByteArray>) : PackByteSource {
        override fun read(relativePath: String): ByteArray? = bytes[relativePath]
    }

    /**
     * 模拟「API 30+ ResourcesLoader」路径：字节来自**另一份独立的字节副本**，
     * 且读取时会重新拷贝一次（模拟 assets.open 返回新流）。
     * 与 [MapByteSource] 取法不同、但内容一致 —— 正是要验证的两路径关系。
     */
    private class CopyingByteSource(private val bytes: Map<String, ByteArray>) : PackByteSource {
        override fun read(relativePath: String): ByteArray? =
            bytes[relativePath]?.clone() // 每次给一份新副本，独立于另一条路径
    }

    private fun entryOf(path: String, bytes: ByteArray) =
        SkinPackEntry(path = path, sha256 = SkinPackIntegrity.sha256Hex(bytes))

    /** 造一份真实的、能通过全部校验的包（含字体，用于覆盖 fontReadable 分支）。 */
    private fun validPack(): Pair<SkinPackManifest, Map<String, ByteArray>> {
        val icons = """{"grid":0.14}""".toByteArray()
        val font = byteArrayOf(1, 2, 3, 4)
        val bytes = mapOf("icons.json" to icons, "fonts/brand.ttf" to font)
        val manifest = SkinPackManifest(
            packId = "test-pack",
            schemaVersion = 1,
            minAppVersion = 1,
            displayName = "测试皮肤",
            iconStrokes = mapOf(NavIconKey.Grid to 0.14f, NavIconKey.Compass to 0.18f),
            fontFileName = "fonts/brand.ttf",
            entries = listOf(entryOf("icons.json", icons), entryOf("fonts/brand.ttf", font)),
            signature = "sig",
        )
        return manifest to bytes
    }

    @Test
    fun `同一份包经两条取字节路径产出完全一致的 LoadedResources`() {
        val (manifest, bytes) = validPack()

        // 路径 A：模拟 API 26–29（文件解码取字节）。
        val viaFile = SkinPackLoader.assemble(
            source = MapByteSource(bytes),
            manifest = manifest,
            appVersionCode = 37,
            builtIn = builtIn,
            verifier = passAll,
            fontReadable = { true },
        )
        // 路径 B：模拟 API 30+（ResourcesLoader 挂载后从 assets 取字节，给独立副本）。
        val viaLoader = SkinPackLoader.assemble(
            source = CopyingByteSource(bytes),
            manifest = manifest,
            appVersionCode = 37,
            builtIn = builtIn,
            verifier = passAll,
            fontReadable = { true },
        )

        assertEquals("两条路径的 LoadedResources 必须整体相等", viaFile, viaLoader)
        assertEquals("图标线宽表一致", viaFile.iconStrokes, viaLoader.iconStrokes)
        assertEquals("字体标识一致", viaFile.fontFileName, viaLoader.fontFileName)
        assertEquals("元信息一致", viaFile.meta, viaLoader.meta)
        assertEquals("最终资源段一致", viaFile.toPayload(), viaLoader.toPayload())
    }

    @Test
    fun `字体不可读时两条路径一致地回落到系统默认字体`() {
        val (manifest, bytes) = validPack()

        val a = SkinPackLoader.assemble(MapByteSource(bytes), manifest, 37, builtIn, passAll) { false }
        val b = SkinPackLoader.assemble(CopyingByteSource(bytes), manifest, 37, builtIn, passAll) { false }

        assertEquals(a, b)
        assertEquals(SkinFont.SystemDefault, a.toPayload().font)
        assertEquals(SkinFont.SystemDefault, b.toPayload().font)
    }

    @Test
    fun `校验失败时两条路径一致地整体回落`() {
        val (manifest, bytes) = validPack()
        // 校验器恒失败 → 无论哪条路径取到的字节，都应整体回落且产物一致。
        val reject = PackSignatureVerifier { _, _ -> false }

        val a = SkinPackLoader.assemble(MapByteSource(bytes), manifest, 37, builtIn, reject)
        val b = SkinPackLoader.assemble(CopyingByteSource(bytes), manifest, 37, builtIn, reject)

        assertEquals(a, b)
        assertEquals(PayloadOrigin.IntegrityFailed, a.meta.origin)
        assertTrue("回落结果不得采用包内线宽", a.iconStrokes.isEmpty())
    }

    @Test
    fun `缺文件时两条路径一致地报 IntegrityFailed`() {
        val (manifest, bytes) = validPack()
        val missing = bytes - "icons.json"

        val a = SkinPackLoader.assemble(MapByteSource(missing), manifest, 37, builtIn, passAll)
        val b = SkinPackLoader.assemble(CopyingByteSource(missing), manifest, 37, builtIn, passAll)

        assertEquals(a, b)
        assertEquals(PayloadOrigin.IntegrityFailed, a.meta.origin)
        assertTrue("原因应点名缺失文件", a.meta.reason.orEmpty().contains("icons.json"))
    }
}
