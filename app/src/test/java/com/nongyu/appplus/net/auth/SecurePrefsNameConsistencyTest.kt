package com.nongyu.appplus.net.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 断言备份规则 XML 里的路径与 [SecurePrefs] 一致。
 *
 * ## 为什么需要这条测试
 * 存储路径散落在三处：Kotlin 常量（[SecurePrefs.DATASTORE_NAME]）、
 * `data_extraction_rules.xml`、`backup_rules.xml`。
 * XML 侧只能写字面量（`path` 是否解析 `@string/` 未取证），所以**改名时极易漏改**。
 * 漏改的后果：加密存储被备份带走 → 换机后解密失败。这条测试把「漏改」变成构建期失败。
 *
 * ## 从 EncryptedSharedPreferences 迁到 DataStore 后的两处变化
 * 1. `path` 从 `nongyu_secure_session`（shared_prefs 下的裸文件名）
 *    变成 `datastore/nongyu_secure_session.preferences_pb`；
 * 2. `domain` 从 `sharedpref` 变成 **`file`** ——
 *    DataStore 落在 `files/` 下，domain 写错会让排除规则形同不存在。
 *    因此这里**同时断言 domain**，防止「路径改对了、domain 忘了改」这种更隐蔽的错误。
 *
 * ## 为什么是纯 JVM 测试
 * Gradle 单测的**工作目录是模块目录**（`app/`），所以用相对路径
 * `src/main/res/xml/...` 就能读到资源文件，不需要 Android 运行时。
 */
class SecurePrefsNameConsistencyTest {

    private val expectedPath = SecurePrefs.BACKUP_EXCLUDE_PATH

    private fun read(relative: String): String {
        val f = File(relative)
        assertTrue("找不到文件：${f.absolutePath}（单测工作目录应为模块目录 app/）", f.exists())
        return f.readText()
    }

    /** 取出所有 `<exclude domain="..." path="..." />` 的 (domain, path) 对。 */
    private fun excludes(xml: String): List<Pair<String, String>> =
        Regex("""<exclude\s+domain="([^"]+)"\s+path="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    @Test
    fun `data_extraction_rules 同时排除 cloud-backup 与 device-transfer`() {
        val xml = read("src/main/res/xml/data_extraction_rules.xml")

        // 两个段必须都在 —— 只写 cloud-backup 会让 D2D 换机直传把密文带过去
        assertTrue("缺少 <cloud-backup> 段", xml.contains("<cloud-backup>"))
        assertTrue(
            "缺少 <device-transfer> 段（API 31+ 换机直传会绕过云备份排除）",
            xml.contains("<device-transfer>"),
        )

        val pairs = excludes(xml)
        assertEquals("应在两个段里各写一条 exclude", 2, pairs.size)
        pairs.forEach { (domain, path) ->
            assertEquals("排除的 domain 必须是 file（DataStore 落在 files/ 下）", "file", domain)
            assertEquals("排除的路径与 SecurePrefs.BACKUP_EXCLUDE_PATH 不一致", expectedPath, path)
        }
    }

    @Test
    fun `backup_rules 排除同一个文件`() {
        val xml = read("src/main/res/xml/backup_rules.xml")

        assertTrue("缺少 <full-backup-content> 段", xml.contains("<full-backup-content>"))

        val pairs = excludes(xml)
        assertEquals(1, pairs.size)
        assertEquals("file", pairs.first().first)
        assertEquals(expectedPath, pairs.first().second)
    }

    @Test
    fun `两个备份文件都用字面量而非 at-string 引用`() {
        // path 属性是否解析 @string/ 未取证，所以禁止用资源引用
        listOf(
            "src/main/res/xml/data_extraction_rules.xml",
            "src/main/res/xml/backup_rules.xml",
        ).forEach { rel ->
            val xml = read(rel)
            assertTrue(
                "$rel 的 path 不得使用 @string/ 引用",
                !Regex("""<exclude[^>]*path="@string/"""").containsMatchIn(xml),
            )
        }
    }

    @Test
    fun `备份路径由 DATASTORE_NAME 派生，落盘名符合 DataStore 约定`() {
        // DataStore 的固定命名规则：<name>.preferences_pb，位于 files/datastore/
        assertEquals(
            "BACKUP_EXCLUDE_PATH 必须由 DATASTORE_NAME 派生且符合 DataStore 落盘约定",
            "datastore/${SecurePrefs.DATASTORE_NAME}.preferences_pb",
            SecurePrefs.BACKUP_EXCLUDE_PATH,
        )
    }
}
