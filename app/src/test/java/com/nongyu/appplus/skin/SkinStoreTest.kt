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
 * 皮肤存储层与清单解析的契约测试（票 #40 / 规格 #33）。
 *
 * 覆盖 ADR-0006 要求的失败场景，全部断言**外部行为**：
 * - 指针损坏 → 回落，不抛异常
 * - 安装失败 → 当前生效那套不受影响
 * - 最多并存两套
 * - 清单非法 → 包不可用（返回 null），不抛异常
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

    // ---------------------------------------------------------------- 指针解析（纯函数）

    @Test
    fun `指针内容合法且槽位存在时返回该槽位`() {
        assertEquals("slot-a", SkinStoreLayout.resolvePointer("slot-a", setOf("slot-a", "slot-b")))
    }

    @Test
    fun `指针为空或不存在时返回 null`() {
        assertNull(SkinStoreLayout.resolvePointer(null, setOf("slot-a")))
        assertNull(SkinStoreLayout.resolvePointer("", setOf("slot-a")))
        assertNull(SkinStoreLayout.resolvePointer("   ", setOf("slot-a")))
    }

    @Test
    fun `指针指向未知槽位名时返回 null 不抛异常`() {
        // 防御指针被外部写入任意字符串。
        assertNull(SkinStoreLayout.resolvePointer("../../etc/passwd", setOf("slot-a")))
        assertNull(SkinStoreLayout.resolvePointer("slot-z", setOf("slot-a")))
    }

    @Test
    fun `指针指向的槽位在磁盘上不存在时返回 null`() {
        assertNull(SkinStoreLayout.resolvePointer("slot-b", setOf("slot-a")))
    }

    // ---------------------------------------------------------------- 槽位选择

    @Test
    fun `新包优先写入不是当前生效的那个槽位`() {
        // 这是原子性的关键：永远不原地覆盖正在用的包。
        assertEquals("slot-b", SkinStoreLayout.slotForNewInstall("slot-a"))
        assertEquals("slot-a", SkinStoreLayout.slotForNewInstall("slot-b"))
        // 首次安装（无当前）时用第一个槽位。
        assertEquals("slot-a", SkinStoreLayout.slotForNewInstall(null))
    }

    @Test
    fun `装上新包后只淘汰既非新包也非上一套的槽位`() {
        val evict = SkinStoreLayout.selectSlotToEvict(
            newSlot = "slot-b",
            previousCurrent = "slot-a",
            existingSlots = setOf("slot-a", "slot-b", "slot-c"),
        )
        assertEquals(listOf("slot-c"), evict)
    }

    @Test
    fun `只有两套时不淘汰任何东西`() {
        val evict = SkinStoreLayout.selectSlotToEvict(
            newSlot = "slot-b",
            previousCurrent = "slot-a",
            existingSlots = setOf("slot-a", "slot-b"),
        )
        assertTrue("两套是 ADR-0003 允许的上限，不该删", evict.isEmpty())
    }

    // ---------------------------------------------------------------- 端到端（真实文件）

    @Test
    fun `无包时 currentPackDir 返回 null`() {
        assertNull("首次启动没有任何包，必须安全返回 null", store().currentPackDir())
    }

    @Test
    fun `安装后 currentPackDir 指向新包，内容可读`() {
        val source = makePack("first")
        val outcome = store().install(source)

        assertTrue("安装应成功，实际是 $outcome", outcome is InstallOutcome.Installed)

        val dir = store().currentPackDir()
        assertNotNull("安装后指针应指向可用目录", dir)
        assertEquals("x", File(dir, "f.txt").readText())
    }

    @Test
    fun `第二次安装换到另一个槽位，旧包仍保留供回滚`() {
        val s = store()
        s.install(makePack("one", "first"))
        val firstDir = s.currentPackDir()

        val outcome = s.install(makePack("two", "second"))
        assertTrue(outcome is InstallOutcome.Installed)

        val secondDir = s.currentPackDir()
        assertNotNull(secondDir)
        assertEquals("second", File(secondDir!!, "f.txt").readText())
        assertNotNull("上一套应仍在磁盘上（供回滚）", firstDir)
        assertTrue("上一套目录应仍存在", firstDir!!.isDirectory)
    }

    @Test
    fun `安装源目录不存在时不改变当前生效的包`() {
        val s = store()
        s.install(makePack("good", "keep-me"))
        val before = s.currentPackDir()

        val outcome = s.install(File(base, "does-not-exist"))
        assertTrue("应返回 Unchanged，实际是 $outcome", outcome is InstallOutcome.Unchanged)

        val after = s.currentPackDir()
        assertEquals("失败安装不得影响当前生效的包", before?.path, after?.path)
        assertEquals("keep-me", File(after!!, "f.txt").readText())
    }

    @Test
    fun `指针文件被写成乱码时当前包失效但不抛异常`() {
        val s = store()
        s.install(makePack("x"))
        File(base, SkinStoreLayout.POINTER_FILE).writeText("%%%garbage%%%")

        assertNull("坏指针必须安全回落", s.currentPackDir())
    }

    @Test
    fun `指针存在但槽位目录被删掉时安全回落`() {
        val s = store()
        s.install(makePack("x"))
        val dir = s.currentPackDir()
        dir!!.deleteRecursively()

        assertNull("槽位消失后指针失效，回落内置默认", s.currentPackDir())
    }
}

/**
 * 清单解析的契约测试（票 #40）。
 *
 * 用 [ManifestRaw] 直接构造输入 —— 这层不碰 `org.json`，因此可在纯 JVM 单测里全量覆盖。
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
    ) = ManifestRaw(
        packId = packId,
        schemaVersion = schemaVersion,
        minAppVersion = minAppVersion,
        displayName = displayName,
        signature = signature,
        fontFileName = fontFileName,
        iconStrokes = iconStrokes,
        entries = entries,
    )

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
        // ADR-0003 的 6 项必填，逐项拿掉验证。
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
                SkinManifestParser.parseMap(
                    raw(entries = listOf(SkinPackEntry(path, goodSha))),
                ),
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
        val dup = listOf(
            SkinPackEntry("a.png", goodSha),
            SkinPackEntry("a.png", goodSha),
        )
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
                raw(
                    fontFileName = "../x.ttf",
                    entries = listOf(SkinPackEntry("../x.ttf", goodSha)),
                ),
            ),
        )
        val ok = SkinManifestParser.parseMap(
            raw(
                fontFileName = "fonts/x.ttf",
                entries = listOf(SkinPackEntry("fonts/x.ttf", goodSha)),
            ),
        )
        assertNotNull("字体在 entries 里应通过", ok)
        assertEquals("fonts/x.ttf", ok!!.fontFileName)
    }

    @Test
    fun `非法图标线宽被剔除而不是让整包失效`() {
        // 逐资源回落：坏的那项丢掉，其余照用，不阻断整包。
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
        // 注意：parseJson 里用了 org.json，纯 JVM 下它是 stub 会抛异常。
        // 这里断言的是「异常被兜住并返回 null」，正是 ADR-0006 要的行为。
        val cases = listOf("", "{", "not json at all", "[]")
        for (text in cases) {
            assertNull("乱码 '$text' 应安全返回 null", SkinManifestParser.parseJson(text))
        }
    }
}
