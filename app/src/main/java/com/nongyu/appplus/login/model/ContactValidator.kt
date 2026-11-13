package com.nongyu.appplus.login.model

/**
 * 手机号与邮箱的格式校验（前端）。
 *
 * ## 为什么前端要校验
 * 后端只做 `trim + 非空`（`normalizeEmail` 仅 `lower+trim`），手机号更是原样透传给
 * 学校侧。格式明显不对时（如把 `abc` 填进邮箱、`123` 填进手机号），
 * 不拦就会白跑一次网络并拿到一个含糊的学校侧错误。这里提前拦下，给明确文案。
 *
 * ## 口径
 * - **手机号**：中国大陆 11 位，`1` 开头，第二位 `3-9`。不做号段白名单
 *   （学校侧/运营商口径可能变化，过严会误伤）。
 * - **邮箱**：宽松校验——本地部分非空、有且仅有一个 `@`、域名有 `.` 且各段非空、
 *   顶级域 ≥ 2 位。**不用 RFC 5322 全量正则**：邮箱语法极宽，过严正则会误伤真实地址。
 *
 * ## 为什么放 model 层
 * 纯逻辑、零 Android 依赖，能纯 JVM 测。
 */
object ContactValidator {

    private val PHONE = Regex("^1[3-9]\\d{9}$")

    /** 邮箱：本地部分@域名，域名至少两段且每段非空、末段 ≥ 2 位字母。 */
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)*\\.[A-Za-z]{2,}$")

    /** 手机号是否合法。 */
    fun isValidPhone(phone: String?): Boolean {
        val v = phone?.trim().orEmpty()
        return v.isNotEmpty() && PHONE.matches(v)
    }

    /** 邮箱是否合法。 */
    fun isValidEmail(email: String?): Boolean {
        val v = email?.trim().orEmpty()
        if (v.isEmpty() || v.length > 254) return false
        return EMAIL.matches(v)
    }
}
