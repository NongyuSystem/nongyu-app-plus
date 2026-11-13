package com.nongyu.appplus.login.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [LocalCaptcha] 纯 JVM 单测。
 *
 * 钉死：生成 4 位、字符集不含易混字符、校验忽略大小写与空白。
 */
class LocalCaptchaTest {

    @Test
    fun `生成长度为 4`() {
        assertEquals(4, LocalCaptcha.random().text.length)
    }

    @Test
    fun `生成的字符不含易混淆字符`() {
        val forbidden = setOf('0', 'O', '1', 'I', 'L')
        repeat(200) {
            LocalCaptcha.random().text.forEach { c ->
                assertFalse("出现了易混字符 $c", c in forbidden)
            }
        }
    }

    @Test
    fun `生成的字符都是大写字母或数字`() {
        repeat(100) {
            LocalCaptcha.random().text.forEach { c ->
                assertTrue("非法字符 $c", c in 'A'..'Z' || c in '2'..'9')
            }
        }
    }

    @Test
    fun `校验忽略大小写`() {
        val c = LocalCaptcha("A2B3")
        assertTrue(c.matches("a2b3"))
        assertTrue(c.matches("A2B3"))
    }

    @Test
    fun `校验忽略首尾空白`() {
        val c = LocalCaptcha("A2B3")
        assertTrue(c.matches("  a2b3  "))
    }

    @Test
    fun `输入为空返回 false`() {
        val c = LocalCaptcha("A2B3")
        assertFalse(c.matches(null))
        assertFalse(c.matches(""))
        assertFalse(c.matches("   "))
    }

    @Test
    fun `输错返回 false`() {
        val c = LocalCaptcha("A2B3")
        assertFalse(c.matches("A2B4"))
        assertFalse(c.matches("A2B"))
    }

    @Test
    fun `同一 Random 种子生成相同结果 保证可测`() {
        val a = LocalCaptcha.random(Random(42))
        val b = LocalCaptcha.random(Random(42))
        assertEquals(a.text, b.text)
    }
}
