package com.nongyu.appplus.login.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContactValidator] 纯 JVM 单测。
 *
 * 口径：手机号 11 位 1[3-9] 开头；邮箱宽松（有 @、域名有 .、顶级域 ≥2 字母）。
 * 正例来自真实使用场景，反例覆盖「明显打错」的常见形态。
 */
class ContactValidatorTest {

    // ---------- 手机号 ----------

    @Test
    fun `常见手机号合法`() {
        listOf("13800000000", "19912345678", "15012345678", "17712345678")
            .forEach { assertTrue("应合法: $it", ContactValidator.isValidPhone(it)) }
    }

    @Test
    fun `手机号首尾空白会被忽略`() {
        assertTrue(ContactValidator.isValidPhone("  13800000000  "))
    }

    @Test
    fun `位数不对不合法`() {
        listOf("1380000000", "138000000000", "138000000").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidPhone(it))
        }
    }

    @Test
    fun `不以1开头或第二位非法不合法`() {
        listOf("23800000000", "12800000000", "10000000000").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidPhone(it))
        }
    }

    @Test
    fun `含非数字不合法`() {
        listOf("1380000000a", "138-0000-0000", "138 0000 0000").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidPhone(it))
        }
    }

    @Test
    fun `手机号空与null不合法`() {
        assertFalse(ContactValidator.isValidPhone(null))
        assertFalse(ContactValidator.isValidPhone(""))
        assertFalse(ContactValidator.isValidPhone("   "))
    }

    // ---------- 邮箱 ----------

    @Test
    fun `常见邮箱合法`() {
        listOf(
            "a@b.com",
            "student@sicau.edu.cn",
            "first.last@example.com",
            "user+tag@example.co.uk",
            "x_y-z@sub.example.org",
        ).forEach { assertTrue("应合法: $it", ContactValidator.isValidEmail(it)) }
    }

    @Test
    fun `邮箱首尾空白会被忽略`() {
        assertTrue(ContactValidator.isValidEmail("  a@b.com  "))
    }

    @Test
    fun `缺at或缺域名不合法`() {
        listOf("abc", "a@", "@b.com", "a@b").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidEmail(it))
        }
    }

    @Test
    fun `多个at不合法`() {
        assertFalse(ContactValidator.isValidEmail("a@b@c.com"))
    }

    @Test
    fun `含空格不合法`() {
        assertFalse(ContactValidator.isValidEmail("a b@c.com"))
    }

    @Test
    fun `顶级域不足两位不合法`() {
        assertFalse(ContactValidator.isValidEmail("a@b.c"))
    }

    @Test
    fun `域名段为空不合法`() {
        listOf("a@.com", "a@b..com", "a@b.com.").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidEmail(it))
        }
    }

    @Test
    fun `邮箱空与null不合法`() {
        assertFalse(ContactValidator.isValidEmail(null))
        assertFalse(ContactValidator.isValidEmail(""))
        assertFalse(ContactValidator.isValidEmail("   "))
    }

    @Test
    fun `超长邮箱不合法`() {
        val long = "a".repeat(250) + "@b.com"
        assertFalse(ContactValidator.isValidEmail(long))
    }
}
