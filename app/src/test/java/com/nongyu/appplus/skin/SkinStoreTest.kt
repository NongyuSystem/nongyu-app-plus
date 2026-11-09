package com.nongyu.appplus.skin

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 皮肤存储层与清单解析的契约测试（票 #40 / 票 #57 ②，ADR-0003）。
 *
 * 覆盖 ADR-0006 与票 #57 Testing Decisions 要求的失败场景，全部断言**外部行为**：
 * - 指针损坏 / 指向的目录被删 → 回落，不抛异常
 * - 安装失败 → 当前生效那套不受影响
 * - 落盘后路径 `skins/{packId}/{version}/`
 * - 最多并存两套、回滚有明确 API
 * - 新结构清单无 warning / 旧扁平有 warning / 新旧混用返回 null
 */
class SkinStoreTest {

    private lateinit var base: File

    @Before
    fun setUp() {
        base = Files.createTempDirectory("skin-store-test").toFile()
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun store() = SkinStore(base) { }

    private fun makePack(name: String, content: String = "x"): File {
        val dir = File(base, "incoming-$name")
        dir.mkdirs()
        File(dir, "f.txt").writeText(content)
        return dir
    }

    /** `skins/{packId}/{version}/` 是否存在（票 #57 ② 落盘路径断言）。 */
    private fun versionDirExists(packId: String, version: String): Boolean =
        File(File(File(base, SkinStoreLayout.SKINS_DIR), packId), version).isDirectory

    // ---------------------------------------------------------------- 指针编解码（纯函数）

    @Test
    fun `指针 JSON 编码与解析 round-trip 对称`() {
        val ref = PackRef("pack-1", "1.0.0")
        val encoded = SkinStoreLayout.encodePointer(ref)
        assertTrue("指针内容应是 JSON", encoded.startsWith("{") && encoded.endsWith("}"))
        assertEquals(ref, SkinStoreLayout.parsePointer(encoded))
    }

    @Test
    fun `指针内容合法且版本目录存在时返回该版本`() {
        val ref = PackRef("pack-1", "1")
        val content = SkinStoreLayout.encodePointer(ref)
        assertEquals(ref, SkinStoreLayout.resolvePointer(content, setOf(ref)))
    }

    @Test
    fun `指针为空乱码缺字段时返回 null 不抛异常`() {
        assertNull(SkinStoreLayout.resolvePointer(null, setOf(PackRef("p", "1"))))
        assertNull(SkinStoreLayout.resolvePointer("", setOf(PackRef("p", "1"))))
        assertNull(SkinStoreLayout.resolvePointer("   ", setOf(PackRef("p", "1"))))
        assertNull(SkinStoreLayout.resolvePointer("%%%garbage%%%", setOf(PackRef("p", "1"))))
        assertNull(SkinStoreLayout.resolvePointer("""{"packId":"p"}""", setOf(PackRef("p", "1"))))
        assertNull(SkinStoreLayout.resolvePointer("""{"version":"1"}""", setOf(PackRef("p", "1"))))
        assertNull(SkinStoreLayout.resolvePointer("""{"packId":"p","version":"1"""", setOf(PackRef("p", "1"))))
    }

    @Test
    fun `指针含穿越路径段时判非法`() {
        assertNull(SkinStoreLayout.parsePointer("""{"packId":"../etc","version":"1"}"""))
        assertNull(SkinStoreLayout.parsePointer("""{"packId":"p","version":"a/b"}"""))
    }

    @Test
    fun `指针指向的版本目录不在磁盘上时返回 null`() {
        val ref = PackRef("p", "1")
        assertNull(SkinStoreLayout.resolvePointer(SkinStoreLayout.encodePointer(ref), setOf(PackRef("p", "2"))))
    }

    // ---------------------------------------------------------------- 淘汰 / 回滚（纯函数）

    @Test
    fun `装上新版本后只淘汰既非新版本也非上一版本的目录`() {
        val newRef = PackRef("p", "3")
        val previous = PackRef("p", "2")
        val oldest = PackRef("p", "1")
        val evict = SkinStoreLayout.selectVersionsToEvict(
            newRef = newRef,
            previousCurrent = previous,
            existingVersions = setOf(newRef, previous, oldest),
        )
        assertEquals(listOf(oldest), evict)
    }

    @Test
    fun `只有两套时不淘汰任何东西`() {
        val newRef = PackRef("p", "2")
        val previous = PackRef("p", "1")
        assertTrue(
            SkinStoreLayout.selectVersionsToEvict(newRef, previous, setOf(newRef, previous)).isEmpty(),
        )
    }

    @Test
    fun `回滚目标是磁盘上不是当前的那一套`() {
        val current = PackRef("p", "2")
        assertEquals(
            PackRef("p", "1"),
            SkinStoreLayout.rollbackTarget(current, setOf(current, PackRef("p", "1"))),
        )
        assertNull("只有一套时无法回滚", SkinStoreLayout.rollbackTarget(current, setOf(current)))
    }

    // ---------------------------------------------------------------- 端到端（真实文件，票 #57 Testing Decisions 表）

    @Test
    fun `无包时 currentPackDir 返回 null`() {
        assertNull("首次启动没有任何包，必须安全返回 null", store().currentPackDir())
    }

    @Test
    fun `落盘后路径是 skins_packId_version 且指针是 JSON`() {
        val outcome = store().install(makePack("first", "x"), "pack-1", "1.0.0")

        assertTrue("安装应成功，实际是 $outcome", outcome is InstallOutcome.Installed)
        assertTrue("应落到 skins/{packId}/{version}/", versionDirExists("pack-1", "1.0.0"))

        val pointer = File(base, SkinStoreLayout.POINTER_FILE).readText()
        assertEquals(PackRef("pack-1", "1.0.0"), SkinStoreLayout.parsePointer(pointer))

        val dir = store().currentPackDir()
        assertNotNull("安装后指针应指向可用目录", dir)
        assertEquals("x", File(dir, "f.txt").readText())
    }

    @Test
    fun `装第二版后旧版目录仍在供回滚`() {
        val s = store()
        s.install(makePack("one", "first"), "pack-1", "1")
        s.install(makePack("two", "second"), "pack-1", "2")

        assertTrue("v1 应仍在", versionDirExists("pack-1", "1"))
        assertTrue("v2 应就位", versionDirExists("pack-1", "2"))
        val dir = s.currentPackDir()
        assertEquals("second", File(dir!!, "f.txt").readText())
    }

    @Test
    fun `装第三版后最老那版被淘汰只剩两套`() {
        val s = store()
        s.install(makePack("one", "first"), "pack-1", "1")
        s.install(makePack("two", "second"), "pack-1", "2")
        val outcome = s.install(makePack("three", "third"), "pack-1", "3")

        assertTrue("安装应成功", outcome is InstallOutcome.Installed)
        assertFalse("最老那版应被淘汰", versionDirExists("pack-1", "1"))
        assertTrue("上一版应保留", versionDirExists("pack-1", "2"))
        assertTrue("新版本应就位", versionDirExists("pack-1", "3"))
        assertEquals("third", File(s.currentPackDir()!!, "f.txt").readText())
    }

    @Test
    fun `回滚把指针改回上一版本且目录未删`() {
        val s = store()
        s.install(makePack("one", "first"), "pack-1", "1")
        s.install(makePack("two", "second"), "pack-1", "2")

        assertTrue("应能回滚", s.rollback())
        assertEquals("first", File(s.currentPackDir()!!, "f.txt").readText())
        // 回滚不删数据，可再次切回去。
        assertTrue("应能再次回滚", s.rollback())
        assertEquals("second", File(s.currentPackDir()!!, "f.txt").readText())
    }

    @Test
    fun `只有一套时回滚返回 false 不抛异常`() {
        val s = store()
        s.install(makePack("only"), "pack-1", "1")
        assertFalse("只有一套不该回滚成功", s.rollback())
    }

    @Test
    fun `安装源目录不存在时不改变当前生效的包`() {
        val s = store()
        s.install(makePack("good", "keep-me"), "pack-1", "1")
        val before = s.currentPackDir()

        val outcome = s.install(File(base, "does-not-exist"), "pack-1", "2")
        assertTrue("应返回 Unchanged，实际是 $outcome", outcome is InstallOutcome.Unchanged)

        val after = s.currentPackDir()
        assertEquals("失败安装不得影响当前生效的包", before?.path, after?.path)
        assertEquals("keep-me", File(after!!, "f.txt").readText())
    }

    @Test
    fun `packId 或 version 含穿越时安装被拒绝`() {
        val s = store()
        assertTrue(
            s.install(makePack("x"), "../evil", "1") is InstallOutcome.Unchanged,
        )
        assertTrue(
            s.install(makePack("x"), "pack-1", "a/b") is InstallOutcome.Unchanged,
        )
        assertNull("非法安装不得留下可用包", s.currentPackDir())
    }

    @Test
    fun `指针文件被写成乱码时当前包失效但不抛异常`() {
        val s = store()
        s.install(makePack("x"), "pack-1", "1")
        File(base, SkinStoreLayout.POINTER_FILE).writeText("%%%garbage%%%")

        assertNull("坏指针必须安全回落", s.currentPackDir())
    }

    @Test
    fun `指针指向的版本目录被删掉时安全回落`() {
        val s = store()
        s.install(makePack("x"), "pack-1", "1")
        val dir = s.currentPackDir()
        dir!!.deleteRecursively()

        assertNull("版本目录消失后指针失效，回落内置默认", s.currentPackDir())
    }
}

/**
 * 清单解析的契约测试（票 #40 / 票 #57 ①）。
 *
 * 用 [ManifestRaw] 直接构造输入 —— 这层不碰 `org.json`，因此可在纯 JVM 单测里全量覆盖。
 * `org.json` 只在 [SkinManifestParser.parseJson] 薄适配层使用，纯 JVM 下是 stub，
 * 故新旧结构的**判定逻辑**全部留在 [SkinManifestParser.parseMap]，通过 [ManifestRaw.format] 注入。
 */
class SkinManifestParserTest {

    private val goodSha = "a".repeat(64)

    private fun raw(
        packId: String? = "pack-1",
        schemaVersion: Int? = 1,
        minAppVersion: Int? = 0,
        displayName: String? = "测试皮肤",
        signature: String? = "sig",
        fontFileName: String? = null,
        iconStrokes: Map<String, Float>? = mapOf("grid" to 0.1f),
        entries: List<SkinPackEntry>? = listOf(SkinPackEntry("icons/grid.png", goodSha)),
        format: ManifestFormat = ManifestFormat.FLAT,
    ) = ManifestRaw(
        packId = packId,
        schemaVersion = schemaVersion,
        minAppVersion = minAppVersion,
        displayName = displayName,
        signature = signature,
        fontFileName = fontFileName,
        iconStrokes = iconStrokes,
        entries = entries,
        format = format,
    )

    // ---------------------------------------------------------------- 票 #57 ①：三种格式

    @Test
    fun `新结构 payload 加 integrity 的清单正常解析且无 warning`() {
        val m = SkinManifestParser.parseMap(raw(format = ManifestFormat.NEW))
        assertNotNull("新结构清单不应返回 null", m)
        assertTrue("新结构不该有 warning，实际是 ${m!!.warnings}", m.warnings.isEmpty())
        assertEquals("pack-1", m.packId)
    }

    @Test
    fun `旧扁平格式清单正常解析但带迁移 warning`() {
        val m = SkinManifestParser.parseMap(raw(format = ManifestFormat.FLAT))
        assertNotNull("旧格式仍应可解析（向后兼容）", m)
        assertTrue(
            "旧格式必须记迁移 warning，实际是 ${m!!.warnings}",
            m.warnings.any { it.contains("建议迁移") },
        )
    }

    @Test
    fun `新旧混用的清单返回 null`() {
        // 既有 payload 又有顶层 entries → 到底以哪个为准的歧义，整包拒绝。
        assertNull(SkinManifestParser.parseMap(raw(format = ManifestFormat.INVALID_MIXED)))
    }

    @Test
    fun `旧格式 warning 经 planner 进入 LoadedResources 的 warnings`() {
        val m = requireNotNull(SkinManifestParser.parseMap(raw(format = ManifestFormat.FLAT)))
        assertTrue("manifest 本身应带迁移 warning", m.warnings.any { it.contains("建议迁移") })

        // 造一套能通过校验的字节，让 plan 走「成功采用」分支（回落分支不收 manifest warnings）。
        val fileBytes = "x".toByteArray()
        val goodManifest = m.copy(
            entries = listOf(SkinPackEntry("icons/grid.png", SkinPackIntegrity.sha256Hex(fileBytes))),
        )
        val ok = SkinPackIntegrity.verify(
            SkinPackVerifyInput(goodManifest, mapOf("icons/grid.png" to fileBytes), 37),
            PackSignatureVerifier { _, _ -> true },
        )
        assertTrue("校验应通过", ok.ok)
        val planned = SkinPackPlanner.plan(
            goodManifest, ok, fontReadable = false, builtIn = BuiltInSkinSource.builtIn.resources,
        )
        assertTrue(
            "warning 应出现在 LoadedResources.warnings（旧格式命中时日志可见），实际是 ${planned.warnings}",
            planned.warnings.any { it.contains("建议迁移") },
        )
    }

    // ---------------------------------------------------------------- 既有 parseMap 契约（保留）

    @Test
    fun `合法清单能解析出全部必填项`() {
        val m = SkinManifestParser.parseMap(raw())
        assertNotNull("合法清单不应返回 null", m)
        assertEquals("pack-1", m!!.packId)
        assertEquals(1, m.schemaVersion)
        assertEquals("测试皮肤", m.displayName)
        assertEquals(1, m.entries.size)
    }

    @Test
    fun `任一必填项缺失都返回 null 而不是抛异常`() {
        assertNull("缺 packId", SkinManifestParser.parseMap(raw(packId = null)))
        assertNull("缺 packId（空串）", SkinManifestParser.parseMap(raw(packId = "  ")))
        assertNull("缺 schemaVersion", SkinManifestParser.parseMap(raw(schemaVersion = null)))
        assertNull("缺 minAppVersion", SkinManifestParser.parseMap(raw(minAppVersion = null)))
        assertNull("缺 displayName", SkinManifestParser.parseMap(raw(displayName = null)))
        assertNull("缺 signature", SkinManifestParser.parseMap(raw(signature = null)))
        assertNull("缺 entries", SkinManifestParser.parseMap(raw(entries = null)))
        assertNull("entries 为空", SkinManifestParser.parseMap(raw(entries = emptyList())))
    }

    @Test
    fun `schemaVersion 非正数视为非法`() {
        assertNull(SkinManifestParser.parseMap(raw(schemaVersion = 0)))
        assertNull(SkinManifestParser.parseMap(raw(schemaVersion = -1)))
    }

    @Test
    fun `条目路径非法时整包拒绝`() {
        val bad = listOf(
            "../escape.png",
            "/abs/path.png",
            "a/../../b.png",
            "noext",
            "evil.dex",
            "lib/x.so",
        )
        for (path in bad) {
            assertNull(
                "非法路径 '$path' 应导致整包拒绝",
                SkinManifestParser.parseMap(raw(entries = listOf(SkinPackEntry(path, goodSha)))),
            )
        }
    }

    @Test
    fun `sha256 格式不对时整包拒绝`() {
        assertNull("太短", SkinManifestParser.parseMap(raw(entries = listOf(SkinPackEntry("a.png", "abc")))))
        assertNull("大写", SkinManifestParser.parseMap(raw(entries = listOf(SkinPackEntry("a.png", "A".repeat(64))))))
        assertNull("空", SkinManifestParser.parseMap(raw(entries = listOf(SkinPackEntry("a.png", "")))))
    }

    @Test
    fun `条目路径重复时整包拒绝`() {
        val dup = listOf(SkinPackEntry("a.png", goodSha), SkinPackEntry("a.png", goodSha))
        assertNull("重复路径会让取字节产生歧义", SkinManifestParser.parseMap(raw(entries = dup)))
    }

    @Test
    fun `字体路径必须合法且出现在 entries 里`() {
        assertNull(
            "字体路径不在 entries 里 → 拒绝",
            SkinManifestParser.parseMap(raw(fontFileName = "fonts/x.ttf")),
        )
        assertNull(
            "字体路径含穿越 → 拒绝",
            SkinManifestParser.parseMap(
                raw(fontFileName = "../x.ttf", entries = listOf(SkinPackEntry("../x.ttf", goodSha))),
            ),
        )
        val ok = SkinManifestParser.parseMap(
            raw(fontFileName = "fonts/x.ttf", entries = listOf(SkinPackEntry("fonts/x.ttf", goodSha))),
        )
        assertNotNull("字体在 entries 里应通过", ok)
        assertEquals("fonts/x.ttf", ok!!.fontFileName)
    }

    @Test
    fun `非法图标线宽被剔除而不是让整包失效`() {
        val m = SkinManifestParser.parseMap(
            raw(iconStrokes = mapOf("grid" to 0.1f, "calendar" to Float.NaN, "compass" to -1f)),
        )
        assertNotNull("单项坏值不应让整包失效", m)
        assertEquals(0.1f, m!!.iconStrokes.getValue(NavIconKey("grid")), 0.0001f)
        assertFalse("NaN 项应被剔除", m.iconStrokes.containsKey(NavIconKey("calendar")))
        assertFalse("负数项应被剔除", m.iconStrokes.containsKey(NavIconKey("compass")))
    }

    @Test
    fun `乱码 JSON 文本返回 null 而不抛异常`() {
        // parseJson 用了 org.json，纯 JVM 下是 stub 会抛异常；断言异常被兜住返回 null。
        val cases = listOf("", "{", "not json at all", "[]")
        for (text in cases) {
            assertNull("乱码 '$text' 应安全返回 null", SkinManifestParser.parseJson(text))
        }
    }
}
