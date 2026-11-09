package com.nongyu.appplus.net.core

/**
 * URL 规范化与日志安全（规格 §2.3、§6）。
 *
 * **纯逻辑，零 Android 依赖，纯 JVM 可测。**
 *
 * 两个职责：
 * 1. 保证 `BASE_URL` 满足 Retrofit 的要求（host 之后必须以 `/` 结尾）。
 * 2. 给日志/异常提供一个**剥掉 query 的 URL**。
 */
object UrlBuilder {

    /**
     * 规范化 base URL。
     *
     * Retrofit 的 `baseUrl()` 要求 URL **以 `/` 结尾**，否则所有相对路径都会被错误地
     * 拼到最后一个路径段上（`http://h/api` + `users` → `http://h/users`，而不是 `http://h/api/users`）。
     *
     * @throws IllegalArgumentException base URL 为空或没有 scheme —— 空值**必须显式报错**，
     * 不能让它变成一个静默的坏 URL（release 变体的 `BASE_URL` 就是空串，见规格 §2.3）。
     */
    fun normalize(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        require(trimmed.isNotEmpty()) {
            "release 变体未配置 BASE_URL：请在 app/src/release/java/.../net/NetConfig.kt 填入生产域名"
        }
        require(trimmed.contains("://")) {
            "BASE_URL 缺少 scheme（应形如 http://host:port/）：$trimmed"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * 剥掉 query，只保留 scheme + host + path。
     *
     * **为什么必须剥**（规格 §6）：异常与日志里允许带 URL（排查「哪个端点挂了」很有用），
     * 但 query 里可能进敏感值。`backend-plus` 目前的查询参数只有
     * `/school/address?target=...&path=...`，但仍然统一剥掉 —— 不依赖「当前没有敏感 query」
     * 这个会变的假设。
     */
    fun stripQuery(url: String): String {
        val q = url.indexOf('?')
        return if (q >= 0) url.substring(0, q) else url
    }

    /**
     * 为该请求生成一个可安全进日志的 URL。
     *
     * 输入可以是相对路径（`/identity/me`）或绝对 URL。
     */
    fun forLog(baseUrl: String, path: String): String =
        stripQuery(join(baseUrl, path))

    /** 拼接 base 与 path，避免出现双斜杠或漏斜杠。 */
    fun join(baseUrl: String, path: String): String {
        val base = normalize(baseUrl)
        return when {
            path.isEmpty() -> base
            path.startsWith("/") -> base.dropLast(1) + path
            else -> base + path
        }
    }
}
