package com.nongyu.appplus.login.model

/**
 * 登录请求字段的格式校验（前端）。
 *
 * ## 与后端对齐（2026-11-13 起）
 * 后端 `internal/identity/flow/validate.go` 新增了 `ValidateLoginReq`，
 * 不合法直接返回 **400 `invalid_request`**（响应体只有 code、没有 message）。
 * 前端必须在发请求前用**同一套规则**拦下，否则用户只会看到一个含糊的错误。
 *
 * | 字段 | 后端规则（validate.go） |
 * | --- | --- |
 * | `studentNo` | `^[0-9]{9}$`（9 位数字） |
 * | `password` | 非空、≤256 字符 |
 * | `email` | `^[^@\s]+@[^@\s]+\.[^@\s]+$`、≤254 |
 * | `code`（mailcode） | `^[0-9A-Za-z]{4,8}$`（4–8 位字母或数字） |
 * | `deviceId` | ≤128（可空） |
 * | `goAdminTgt` | 见 [TgtValidator] 的形态规则 |
 *
 * 手机号不在 `identity` 请求体里（走学校 CAS），保留宽松校验避免误伤。
 *
 * ## 为什么放 model 层
 * 纯逻辑、零 Android 依赖，能纯 JVM 测。
 */
object ContactValidator {

    /** 后端 `reStudentNo`。 */
    private val STUDENT_NO = Regex("^[0-9]{9}$")
    /** 后端 `reEmail`。 */
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    /** 后端 `reCode`（mailcode 的 code）。 */
    private val MAIL_CODE = Regex("^[0-9A-Za-z]{4,8}$")
    /** 学校侧手机号：中国大陆 11 位，1[3-9] 开头（后端不校验，仅前端防误填）。 */
    private val PHONE = Regex("^1[3-9]\\d{9}$")

    const val MAX_DEVICE_ID = 128
    const val MAX_EMAIL_LEN = 254
    const val MAX_PASSWORD_LEN = 256

    /** 学号必须是 9 位数字（与后端 `validateStudentNo` 一致）。 */
    fun isValidStudentNo(s: String?): Boolean {
        val v = s?.trim().orEmpty()
        return v.isNotEmpty() && v.length <= 32 && STUDENT_NO.matches(v)
    }

    /** 邮箱（与后端 `validateEmail` 一致）。 */
    fun isValidEmail(s: String?): Boolean {
        val v = s?.trim().orEmpty()
        return v.isNotEmpty() && v.length <= MAX_EMAIL_LEN && EMAIL.matches(v)
    }

    /** mailcode 的验证码必须是 4–8 位字母或数字（与后端 `validateCode` 一致）。 */
    fun isValidMailCode(s: String?): Boolean {
        val v = s?.trim().orEmpty()
        return v.isNotEmpty() && v.length <= 16 && MAIL_CODE.matches(v)
    }

    /** 密码：非空且 ≤256 字符（与后端 `password` 分支一致）。 */
    fun isValidPassword(s: String?): Boolean {
        val v = s.orEmpty()
        return v.trim().isNotEmpty() && v.length <= MAX_PASSWORD_LEN
    }

    /** deviceId 可空，给了就不能超长。 */
    fun isValidDeviceId(s: String?): Boolean = (s?.length ?: 0) <= MAX_DEVICE_ID

    /** 学校侧手机号（前端防误填，后端不校验）。 */
    fun isValidPhone(phone: String?): Boolean {
        val v = phone?.trim().orEmpty()
        return v.isNotEmpty() && PHONE.matches(v)
    }
}
