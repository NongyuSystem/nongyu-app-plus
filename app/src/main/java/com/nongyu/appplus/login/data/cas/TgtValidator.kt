package com.nongyu.appplus.login.data.cas

/**
 * `go-admin-tgt` 的形态校验。
 *
 * ## 为什么必须校验（权威文档 §1.2「血泪教训」）
 * 客户端若把一次 HTTP 响应的 **body 当成 cookie 值**交上来，后端不校验就会把它
 * 加密存进 Redis，之后**每次换票都 500「未登录」**，表现为
 * 「能登录、有姓名、画像其他字段全空」，而日志里零线索。
 *
 * 典型误取：拿到的是 302 重定向的 HTML —— `<a href="https://webvpn.sicau.edu.cn/login">Found</a>.`
 *
 * ## 判据（与后端 `internal/school/schoolauth/tgt.go` 的 `looksLikeTGT` 一致）
 * - 长度 8..512
 * - 必须含 `:`
 * - 只允许可见 ASCII（0x21..0x7e）
 * - 不含 `<` `>` `"` `'` `\` 与空格
 *
 * ## 取材来源（**不能弄错**）
 * `go-admin-tgt` 是 **HttpOnly** cookie，`document.cookie` / 响应 body 都读不到，
 * 只能从响应的 **`Set-Cookie` 头**里取。OkHttp 的 `CookieJar` 收到的正是这些头。
 */
object TgtValidator {

    private const val MIN_LEN = 8
    private const val MAX_LEN = 512

    /** 是否像一个合法的 go-admin-tgt 值。 */
    fun looksLikeTgt(value: String?): Boolean {
        val s = value?.trim().orEmpty()
        if (s.length < MIN_LEN || s.length > MAX_LEN) return false
        if (!s.contains(':')) return false
        for (ch in s) {
            if (ch.code < 0x21 || ch.code > 0x7e) return false
            when (ch) {
                '<', '>', '"', '\'', '\\', ' ' -> return false
            }
        }
        return true
    }

    /**
     * 从 `Set-Cookie` 原始头里解析出 `go-admin-tgt` 的值。
     *
     * @param setCookieHeaders 响应里所有 `Set-Cookie` 头（OkHttp `response.headers.values("Set-Cookie")`）
     * @return 合法的 tgt 值；没有或形态不对返回 null
     */
    fun extractFromSetCookie(setCookieHeaders: List<String>): String? {
        for (raw in setCookieHeaders) {
            // 形如：go-admin-tgt=201690:TGT:xxx; Path=/; Max-Age=864000; HttpOnly
            val firstSemi = raw.indexOf(';')
            val pair = if (firstSemi >= 0) raw.substring(0, firstSemi) else raw
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val name = pair.substring(0, eq).trim()
            if (name != COOKIE_NAME) continue
            val value = pair.substring(eq + 1).trim()
            if (looksLikeTgt(value)) return value
        }
        return null
    }

    /** cookie 名（**不是**标准 CAS 的 `TGC`，按 TGC 猜会一路走错）。 */
    const val COOKIE_NAME = "go-admin-tgt"
}
