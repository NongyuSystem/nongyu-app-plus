package com.nongyu.appplus.net.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 断言备份规则 XML 里的文件名与 [SecurePrefs.FILE_NAME] 一致。
 *
 * ## 为什么需要这条测试
 * 文件名散落在三处：Kotlin 常量、`data_extraction_rules.xml`、`backup_rules.xml`。
 * XML 侧只能写字面量（`path` 是否解析 `@string/` 未取证），所以**改名时极易漏改**。
 * 漏改的后果：加密存储被备份带走 → 换机后解密失败。这条测试把「漏改」变成构建期失败。
 *
 * ## 为什么是纯 JVM 测试
 * Gradle 单测的**工作目录是模块目录**（`app/`），所以用相对路径
 * `src/main/res/xml/...` 就能读到资源文件，不需要 Android 运行时。
 */
class SecurePrefsNameConsistencyTest {

    private val expected = SecurePrefs.FILE_NAME

    private fun read(relative: String): String {
        val f = File(relative)
        assertTrue("找不到文件：${f.absolutePath}（单测工作目录应为模块目录 app/）", f.exists())
        return f.readText()
    }

    @Test
    fun `data_extraction_rules 同时排除 cloud-backup 与 device-transfer`() {
        val xml = read("src/main/res/xml/data_extraction_rules.xml")

        // 两个段必须都在 —— 只写 cloud-backup 会让 D2D 换机直传把密文带过去
        assertTrue("缺少 <cloud-backup> 段", xml.contains("<cloud-backup>"))
        assertTrue("缺少 <device-transfer> 段（API 31+ 换机直传会绕过云备份排除）",
            xml.contains("<device-transfer>"))

        // 两个段里都要有 exclude
        val excludes = Regex("""<exclude[^>]*path="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
        assertEquals("应在两个段里各写一条 exclude", 2, excludes.size)
        excludes.forEach { assertEquals("排除的文件名与 SecurePrefs.FILE_NAME 不一致", expected, it) }
    }

    @Test
    fun `backup_rules 排除同一个文件`() {
        val xml = read("src/main/res/xml/backup_rules.xml")

        assertTrue("缺少 <full-backup-content> 段", xml.contains("<full-backup-content>"))

        val excludes = Regex("""<exclude[^>]*path="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(1, excludes.size)
        assertEquals(expected, excludes.first())
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
}
