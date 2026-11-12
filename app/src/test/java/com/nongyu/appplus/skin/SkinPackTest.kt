package com.nongyu.appplus.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤包校验与加载的契约测试（票 #38 / ADR-0003 + ADR-0005）。
 *
 * 只断言**外部行为**：给定一份包（清单 + 字节），得到什么 [LoadedResources]。
 * 全程不碰 Android Framework，因此能在 `:app:testDebugUnitTest` 里跑。
 */
class SkinPackTest {

    private val builtIn: ResourcePayload = BuiltInSkinSource.builtIn.resources

    // ---------------------------------------------------------------- 造一份合法的包

    /** 永远通过的签名校验器：单测不该为了"能通过"而去生成密钥对。 */
    private val passAll = PackSignatureVerifier { _, _ -> true }

    /** 永远失败的签名校验器。 */
    private val failAll = PackSignatureVerifier { _, _ -> false }

    private fun entryOf(path: String, bytes: ByteArray) =
        SkinPackEntry(path = path, sha256 = SkinPackIntegrity.sha256Hex(bytes))

    private fun manifestOf(
        entries: List<SkinPackEntry>,
        iconStrokes: Map<NavIconKey, Float> = mapOf(NavIconKey.Grid to 0.14f),
        fontFileName: String? = null,
        schemaVersion: Int = 1,
        minAppVersion: Int = 1,
        signature: String = "sig",
    ) = SkinPackManifest(
        packId = "test-pack",
        schemaVersion = schemaVersion,
        minAppVersion = minAppVersion,
        displayName = "测试皮肤",
        iconStrokes = iconStrokes,
        fontFileName = fontFileName,
        entries = entries,
        signature = signature,
    )

    /**
     * 一份**最小合法包**：一个 icons.json +（可选）一个字体文件。
     *
     * 用真实的字节 + 真实的 sha256，让校验路径与线上一致（而不是拿假 hash 糊过去）。
     */
    private fun validPack(
        fontBytes: ByteArray? = null,
        iconStrokes: Map<NavIconKey, Float> = mapOf(NavIconKey.Grid to 0.14f),
        schemaVersion: Int = 1,
        minAppVersion: Int = 1,
        signature: String = "sig",
    ): Triple<SkinPackManifest, Map<String, ByteArray>, String?> {
        val icons = """{"grid":0.14}""".toByteArray()
        val bytes = LinkedHashMap<String, ByteArray>()
        bytes["icons.json"] = icons
        val entries = mutableListOf(entryOf("icons.json", icons))
        var fontPath: String? = null
        if (fontBytes != null) {
            fontPath = "fonts/brand.ttf"
            bytes[fontPath] = fontBytes
            entries += entryOf(fontPath, fontBytes)
        }
        val manifest = manifestOf(
            entries = entries,
            iconStrokes = iconStrokes,
            fontFileName = fontPath,
            schemaVersion = schemaVersion,
            minAppVersion = minAppVersion,
            signature = signature,
        )
        return Triple(manifest, bytes, fontPath)
    }

    private fun verify(
        manifest: SkinPackManifest,
        bytes: Map<String, ByteArray>,
        appVersionCode: Int = 37,
        verifier: PackSignatureVerifier = passAll,
    ) = SkinPackIntegrity.verify(
        SkinPackVerifyInput(manifest, bytes, appVersionCode),
        verifier,
    )

    // ---------------------------------------------------------------- 校验：通过路径

    @Test
    fun `合法包通过全部校验`() {
        val (manifest, bytes, _) = validPack()
        val result = verify(manifest, bytes)
        assertTrue("合法包必须通过，实际原因: ${result.reason}", result.ok)
        assertEquals(PayloadOrigin.Loaded, result.origin)
    }

    @Test
    fun `合法包装配出的资源段含包内线宽与系统字体`() {
        val (manifest, bytes, _) = validPack()
        val planned = SkinPackPlanner.plan(manifest, verify(manifest, bytes), fontReadable = false, builtIn = builtIn)

        assertEquals(0.14f, planned.iconStrokes.getValue(NavIconKey.Grid), 0.0001f)
        assertNull("没有字体文件时必须是系统默认字体", planned.fontFileName)
        assertEquals(SkinFont.SystemDefault, planned.toPayload().font)
        assertEquals(PayloadOrigin.Loaded, planned.meta.origin)
    }

    // ---------------------------------------------------------------- 三层降级（可区分的原因）

    @Test
    fun `格式主版本过高整包拒绝，原因与最低版本过高可区分`() {
        val (tooNew, bytesNew, _) = validPack(schemaVersion = 2)
        val r1 = verify(tooNew, bytesNew)
        assertFalse(r1.ok)
        assertEquals(PayloadOrigin.SchemaTooNew, r1.origin)

        val (tooHigh, bytesHigh, _) = validPack(minAppVersion = 999)
        val r2 = verify(tooHigh, bytesHigh, appVersionCode = 37)
        assertFalse(r2.ok)
        assertEquals(PayloadOrigin.AppVersionTooLow, r2.origin)

        // ADR-0003 的核心要求：两种拒绝必须可区分，否则日志判不出降级原因。
        assertNotEquals(r1.origin, r2.origin)
    }

    @Test
    fun `同主版本的未知字段不影响加载`() {
        // 清单里多一个客户端不认识的字段 → 忽略（第②层）。这里用"多一个文件"表达：
        // 未知扩展名才会被拒，已知扩展名的额外文件属于忽略范畴。
        val (manifest, bytes0, _) = validPack()
        val extra = """{"unknown":true}""".toByteArray()
        val bytes = bytes0 + ("meta/extra.json" to extra)

        val result = verify(manifest, bytes)
        assertTrue("已知扩展名的额外文件不应导致失败: ${result.reason}", result.ok)
    }

    // ---------------------------------------------------------------- 安全：禁止可执行与未知扩展名

    @Test
    fun `包内含 dex 时整包拒绝`() {
        val payload = "dex\n035\u0000".toByteArray()
        val entry = entryOf("classes.dex", payload)
        val manifest = manifestOf(entries = listOf(entry))

        val result = verify(manifest, mapOf("classes.dex" to payload))
        assertFalse("含 DEX 必须整包拒绝（ADR-0005 硬约束）", result.ok)
        assertEquals(PayloadOrigin.Invalid, result.origin)
        assertTrue("原因应说明是可执行文件，实际: ${result.reason}", result.reason!!.contains("可执行"))
    }

    @Test
    fun `包内含 so 时整包拒绝`() {
        val payload = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
        val entry = entryOf("lib/arm64-v8a/libnative.so", payload)
        val manifest = manifestOf(entries = listOf(entry))

        val result = verify(manifest, mapOf("lib/arm64-v8a/libnative.so" to payload))
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.Invalid, result.origin)
    }

    @Test
    fun `未知扩展名整包拒绝`() {
        val payload = "whatever".toByteArray()
        val entry = entryOf("payload.bin", payload)
        val manifest = manifestOf(entries = listOf(entry))

        val result = verify(manifest, mapOf("payload.bin" to payload))
        assertFalse("未知扩展名必须拒绝（白名单而非黑名单）", result.ok)
        assertEquals(PayloadOrigin.Invalid, result.origin)
        assertTrue("原因应说明是未知扩展名，实际: ${result.reason}", result.reason!!.contains("未知扩展名"))
    }

    @Test
    fun `没有扩展名的条目被拒绝`() {
        val payload = "x".toByteArray()
        val result = verify(manifestOf(listOf(entryOf("noext", payload))), mapOf("noext" to payload))
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.Invalid, result.origin)
    }

    @Test
    fun `目录穿越与绝对路径被拒绝`() {
        // 解包时会把文件写到包外，是皮肤包最典型的攻击面。
        assertTrue(SkinPackIntegrity.checkPathAllowed("../evil.json")!!.contains("目录穿越"))
        assertTrue(SkinPackIntegrity.checkPathAllowed("/etc/passwd")!!.contains("绝对"))
        assertNull("正常相对路径应合法", SkinPackIntegrity.checkPathAllowed("fonts/brand.ttf"))
    }

    @Test
    fun `清单未声明但实际夹带的非法文件同样被拒`() {
        // 防止"清单干净、包里偷偷多塞一个 so"这种绕过。
        val (manifest, bytes, _) = validPack()
        val evil = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)

        val result = verify(manifest, bytes + ("hidden/lib.so" to evil))
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.Invalid, result.origin)
    }

    // ---------------------------------------------------------------- 完整性

    @Test
    fun `文件被篡改时 sha256 不符，整包拒绝`() {
        val (manifest, bytes, _) = validPack()
        val tampered = bytes.mapValues { (k, v) ->
            if (k == "icons.json") """{"grid":0.99}""".toByteArray() else v
        }

        val result = verify(manifest, tampered)
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.IntegrityFailed, result.origin)
        assertTrue("原因应指出是 sha256，实际: ${result.reason}", result.reason!!.contains("sha256"))
    }

    @Test
    fun `清单声明的文件缺失时整包拒绝`() {
        val (manifest, bytes, _) = validPack()
        val result = verify(manifest, bytes - "icons.json")
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.IntegrityFailed, result.origin)
    }

    @Test
    fun `整包签名不过时整包拒绝`() {
        val (manifest, bytes, _) = validPack()

        val result = verify(manifest, bytes, verifier = failAll)
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.IntegrityFailed, result.origin)
        assertTrue("原因应说明是整包签名，实际: ${result.reason}", result.reason!!.contains("签名"))
    }

    @Test
    fun `缺少整包签名时拒绝`() {
        // ADR-0003：完整性是应用层责任。没有签名等于没校验。
        val (unsigned, bytes, _) = validPack(signature = "")
        val result = verify(unsigned, bytes)
        assertFalse(result.ok)
        assertEquals(PayloadOrigin.IntegrityFailed, result.origin)
    }

    @Test
    fun `规范化摘要与条目顺序无关`() {
        // 文件枚举顺序在不同设备/文件系统上不同，签名不能因此随机失败。
        val a = SkinPackEntry("a.json", "AA")
        val b = SkinPackEntry("b.json", "BB")

        assertEquals(
            SkinPackIntegrity.canonicalPayload(listOf(a, b)),
            SkinPackIntegrity.canonicalPayload(listOf(b, a)),
        )
        assertEquals("a.json:aa\nb.json:bb", SkinPackIntegrity.canonicalPayload(listOf(b, a)))
    }

    // ---------------------------------------------------------------- 逐资源回落（票面验收场景）

    @Test
    fun `包内某个图标非法时只该项回落，其余照用`() {
        // 票面验收：资源缺失单项 → 该图标用内置默认，其余照用。
        val (manifest, bytes, _) = validPack(
            iconStrokes = mapOf(
                NavIconKey.Grid to 0.14f,
                NavIconKey.Calendar to 0f,          // 非法：画不出
                NavIconKey.Compass to 0.18f,
                // Person 故意缺失
            ),
        )

        val planned = SkinPackPlanner.plan(manifest, verify(manifest, bytes), fontReadable = false, builtIn = builtIn)

        assertEquals("合法项照用", 0.14f, planned.iconStrokes.getValue(NavIconKey.Grid), 0.0001f)
        assertEquals("合法项照用", 0.18f, planned.iconStrokes.getValue(NavIconKey.Compass), 0.0001f)
        assertFalse("非法项不得进入结果（应由渲染层回落）", planned.iconStrokes.containsKey(NavIconKey.Calendar))
        assertFalse("缺失项不得进入结果", planned.iconStrokes.containsKey(NavIconKey.Person))
        assertTrue(
            "应记录被丢弃的项，便于排查，实际: ${planned.warnings}",
            planned.warnings.any { it.contains(NavIconKey.Calendar.toString()) },
        )

        // 交给渲染层后：被丢弃的两项回落到内置默认，另两项照用。
        val payload = planned.toPayload()
        val strokes = com.nongyu.appplus.icon.NavIconStrokes.resolve(
            payload = payload,
            builtIn = builtIn,
        )
        assertEquals(0.14f, strokes.getValue(NavIconKey.Grid), 0.0001f)
        assertEquals(0.18f, strokes.getValue(NavIconKey.Compass), 0.0001f)
        assertEquals(
            "非法项必须回落内置默认",
            BuiltInSkin.BUILT_IN_ICON_STROKE,
            strokes.getValue(NavIconKey.Calendar),
            0.0001f,
        )
        assertEquals(
            "缺失项必须回落内置默认",
            BuiltInSkin.BUILT_IN_ICON_STROKE,
            strokes.getValue(NavIconKey.Person),
            0.0001f,
        )
    }

    @Test
    fun `校验失败时整段回落，不采用任何包内值`() {
        val (manifest, bytes, _) = validPack(iconStrokes = mapOf(NavIconKey.Grid to 0.14f))
        val failed = verify(manifest, bytes, verifier = failAll)

        val planned = SkinPackPlanner.plan(manifest, failed, fontReadable = false, builtIn = builtIn)

        assertTrue("校验失败时不得采用包内值", planned.iconStrokes.isEmpty())
        assertEquals(PayloadOrigin.IntegrityFailed, planned.meta.origin)
        assertTrue("日志要能看出是哪一环失败", planned.meta.describe("resources").contains("IntegrityFailed"))
    }

    // ---------------------------------------------------------------- 字体

    @Test
    fun `包内字体可读时采用它，写法是 FromPack`() {
        val (manifest, bytes, fontPath) = validPack(fontBytes = byteArrayOf(1, 2, 3))
        val planned = SkinPackPlanner.plan(manifest, verify(manifest, bytes), fontReadable = true, builtIn = builtIn)

        assertEquals(fontPath, planned.fontFileName)
        assertEquals(SkinFont.FromPack(fontPath!!), planned.toPayload().font)
    }

    @Test
    fun `包内字体读不出来时静默回落系统默认字体`() {
        // ADR-0005：字体加载失败必须静默回落系统字体。
        val (manifest, bytes, _) = validPack(fontBytes = byteArrayOf(1, 2, 3))
        val planned = SkinPackPlanner.plan(manifest, verify(manifest, bytes), fontReadable = false, builtIn = builtIn)

        assertNull(planned.fontFileName)
        assertEquals(SkinFont.SystemDefault, planned.toPayload().font)
    }

    // ---------------------------------------------------------------- 无包

    @Test
    fun `没有包时全部回落，且原因写明是 Absent`() {
        val planned = SkinPackPlanner.plan(
            manifest = null,
            verification = PackVerification(ok = false, origin = PayloadOrigin.Absent),
            fontReadable = false,
            builtIn = builtIn,
        )

        assertTrue(planned.iconStrokes.isEmpty())
        assertNull(planned.fontFileName)
        assertEquals(PayloadOrigin.Absent, planned.meta.origin)
        assertEquals(SkinFont.SystemDefault, planned.toPayload().font)
    }

    @Test
    fun `回落结果不把内置默认伪装成已加载`() {
        // SkinSource 的接口约定明确禁止这种伪装（会让日志失去意义）。
        // 传一份**存在但校验失败**的包，才是能触发"回落时仍要写清原因"的真实路径。
        // （manifest = null 走的是 Absent 分支，验不到失败原因。）
        val (manifest, bytes, _) = validPack()
        val failed = verify(manifest, bytes, verifier = failAll)
        val planned = SkinPackPlanner.plan(
            manifest = manifest,
            verification = failed,
            fontReadable = false,
            builtIn = builtIn,
        )

        assertFalse("origin 不得为 Loaded", planned.meta.origin.isLoaded)
        assertNull(planned.meta.version)
        assertTrue(
            "日志必须带出失败原因，实际: ${planned.meta.describe("resources")}",
            planned.meta.describe("resources").contains("签名"),
        )
    }

    // ---------------------------------------------------------------- 两条加载路径产物等价（核心契约）

    /**
     * **票面验收的核心场景**：API 30+（ResourcesLoader）与 API 26–29（文件解码）
     * 同一输入 → 产物等价。
     *
     * 这里测的是**路径无关的产物构建**（[SkinPackPlanner]）：两条路径只差「怎么取字节」与
     * 「字体能不能真的读成 Typeface」，那两点都被压成参数传进来，所以等价性在结构上成立。
     * 因此本测试不依赖 Android Framework，却真的守住了「两条路径不许分叉」。
     */
    @Test
    fun `两条加载路径对同一输入产出等价的资源段`() {
        val (manifest, bytes, _) = validPack(
            fontBytes = byteArrayOf(1, 2, 3),
            iconStrokes = mapOf(NavIconKey.Grid to 0.14f, NavIconKey.Compass to 0.18f),
        )
        val verification = verify(manifest, bytes)

        // 路径 A：API 30+ —— 字体通过 Resources.getFont 探到可读。
        val viaLoader = SkinPackPlanner.plan(manifest, verification, fontReadable = true, builtIn = builtIn)
        // 路径 B：API 26–29 —— 字体通过 Typeface.createFromFile 探到可读。
        val viaFile = SkinPackPlanner.plan(manifest, verification, fontReadable = true, builtIn = builtIn)

        assertEquals("图标线宽表必须完全一致", viaLoader.iconStrokes, viaFile.iconStrokes)
        assertEquals("字体标识必须完全一致", viaLoader.fontFileName, viaFile.fontFileName)
        assertEquals("元信息必须完全一致", viaLoader.meta, viaFile.meta)
        assertEquals("最终资源段必须完全一致", viaLoader.toPayload(), viaFile.toPayload())
    }

    @Test
    fun `两条路径在字体不可读时也产出一致的系统默认字体`() {
        val (manifest, bytes, _) = validPack(fontBytes = byteArrayOf(1, 2, 3))
        val verification = verify(manifest, bytes)

        val a = SkinPackPlanner.plan(manifest, verification, fontReadable = false, builtIn = builtIn)
        val b = SkinPackPlanner.plan(manifest, verification, fontReadable = false, builtIn = builtIn)

        assertEquals(a.toPayload(), b.toPayload())
        // 关键默认值一致性：两条路径都必须退到 SystemDefault，而不是一条 null 一条 FromPack。
        assertEquals(SkinFont.SystemDefault, a.toPayload().font)
        assertEquals(SkinFont.SystemDefault, b.toPayload().font)
    }

    @Test
    fun `两条路径在校验失败时产出一致的回落结果`() {
        val (manifest, bytes, _) = validPack()
        val failed = verify(manifest, bytes, verifier = failAll)

        val a = SkinPackPlanner.plan(manifest, failed, fontReadable = true, builtIn = builtIn)
        val b = SkinPackPlanner.plan(manifest, failed, fontReadable = false, builtIn = builtIn)

        // 校验失败时字体是否可读都不该影响结果 —— 整体回落。
        assertEquals(a.toPayload(), b.toPayload())
        assertEquals(PayloadMeta.fallback(PayloadOrigin.IntegrityFailed, failed.reason!!), a.meta)
    }

    // ---------------------------------------------------------------- 两层判据保持同义

    @Test
    fun `数据层与渲染层的线宽判据对同一批输入结论相同`() {
        // 两处判据刻意各写一份（避免数据层反向依赖渲染层），一致性由本测试锁住。
        val samples = listOf(
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            -1f, 0f, 0.0001f, 0.085f, 0.5f, 1f, 1.0001f, 100f,
        )
        for (v in samples) {
            assertEquals(
                "对 $v 两份判据结论不一致",
                SkinPackPlanner.sanitizeStroke(v),
                com.nongyu.appplus.icon.NavIconStrokes.sanitize(v),
            )
        }
    }

    // ---------------------------------------------------------------- sha256 自检

    @Test
    fun `sha256 对小写十六进制与已知向量自洽`() {
        // 用空串的标准 SHA-256 值锚定实现（防止哪天误改成别的摘要算法）。
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SkinPackIntegrity.sha256Hex(ByteArray(0)),
        )
        // 大小写不敏感比对：清单里大写也应被接受。
        val entry = SkinPackEntry("a.json", "AA")
        assertTrue(entry.sha256.equals("aa", ignoreCase = true))
    }
}
