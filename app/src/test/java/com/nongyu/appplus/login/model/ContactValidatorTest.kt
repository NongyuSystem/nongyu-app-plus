package com.nongyu.appplus.login.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContactValidator] 与后端 `validate.go` 对齐的单测。
 *
 * 每条正例/反例都对应后端的一条规则，避免前端放过后端要 400 的输入。
 */
class ContactValidatorTest {

    // ---------- 学号：后端 ^[0-9]{9}$ ----------

    @Test
    fun `9位数字学号合法`() {
        listOf("202502204", "123456789", "000000000").forEach {
            assertTrue("应合法: $it", ContactValidator.isValidStudentNo(it))
        }
    }

    @Test
    fun `学号位数或字符不对不合法`() {
        listOf("20250220", "2025022044", "20250220a", "", "  ", "2025 0220").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidStudentNo(it))
        }
    }

    @Test
    fun `学号null不合法`() {
        assertFalse(ContactValidator.isValidStudentNo(null))
    }

    // ---------- 邮箱：后端 ^[^@\s]+@[^@\s]+\.[^@\s]+$、<=254 ----------

    @Test
    fun `常见邮箱合法`() {
        listOf("a@b.com", "student@sicau.edu.cn", "user+tag@example.co.uk").forEach {
            assertTrue("应合法: $it", ContactValidator.isValidEmail(it))
        }
    }

    @Test
    fun `缺at或缺点号不合法`() {
        listOf("abc", "a@b", "a@.com", "@b.com", "a@b@c.com").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidEmail(it))
        }
    }

    @Test
    fun `含空格不合法`() {
        assertFalse(ContactValidator.isValidEmail("a b@c.com"))
    }

    @Test
    fun `超长邮箱不合法`() {
        assertFalse(ContactValidator.isValidEmail("a".repeat(250) + "@b.com"))
    }

    // ---------- mailcode 的 code：后端 ^[0-9A-Za-z]{4,8}$ ----------

    @Test
    fun `4到8位字母数字验证码合法`() {
        listOf("1234", "abcdef", "A1B2C3", "12345678").forEach {
            assertTrue("应合法: $it", ContactValidator.isValidMailCode(it))
        }
    }

    @Test
    fun `验证码长度或字符不对不合法`() {
        listOf("123", "123456789", "12a4!", "12 4", "").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidMailCode(it))
        }
    }

    // ---------- 密码：非空、<=256 ----------

    @Test
    fun `非空短密码合法`() {
        assertTrue(ContactValidator.isValidPassword("x"))
        assertTrue(ContactValidator.isValidPassword("yuanshen123ABC."))
    }

    @Test
    fun `空密码或超长密码不合法`() {
        assertFalse(ContactValidator.isValidPassword(""))
        assertFalse(ContactValidator.isValidPassword("   "))
        assertFalse(ContactValidator.isValidPassword(null))
        assertFalse(ContactValidator.isValidPassword("a".repeat(257)))
    }

    // ---------- deviceId：<=128 ----------

    @Test
    fun `deviceId长度校验`() {
        assertTrue(ContactValidator.isValidDeviceId(null))
        assertTrue(ContactValidator.isValidDeviceId(""))
        assertTrue(ContactValidator.isValidDeviceId("a".repeat(128)))
        assertFalse(ContactValidator.isValidDeviceId("a".repeat(129)))
    }

    // ---------- 手机号（学校侧，宽松） ----------

    @Test
    fun `手机号校验`() {
        assertTrue(ContactValidator.isValidPhone("13800000000"))
        assertTrue(ContactValidator.isValidPhone("19912345678"))
        listOf("12800000000", "1380000000", "138000000000", "1380000000a", "").forEach {
            assertFalse("应非法: $it", ContactValidator.isValidPhone(it))
        }
    }
}
