package com.nongyu.appplus.webpage.logic

/**
 * WebView 导航白名单判定（票 #119，纯 JVM）。
 *
 * 规则：URL 可解析、scheme 为 https、host 精确命中允许集合（支持 `*.` 前缀子域）
 * 才放行；其余（http、非法 URL、不在白名单）一律拦截。
 *
 * 这与 H5 节日页 D7 的「离开白名单一律拦截」并不矛盾：那条针对 H5 活动页，
 * 本票把白名单边界**扩展为允许校方站点**（见 `docs/specs/活动层-H5-节日页.md`
 * 的修订与 ADR-0016），学校相关页面用同一套判定机构但注入校方 host 集合。
 */
object WebPageAllowlist {

    /** 协议：学校站点强制 https（与网安全配置「release 禁明文」一致）。 */
    private const val ALLOWED_SCHEME = "https"

    /**
     * 判定 [rawUrl] 是否允许在容器内导航。
     *
     * @param rawUrl 待判定 URL
     * @param allowedHosts 允许的 host 集合；元素以 `*.` 开头表示允许该域及其子域
     */
    fun isAllowed(rawUrl: String?, allowedHosts: Set<String>): Boolean {
        val normalized = UrlNormalizer.normalize(rawUrl) ?: return false
        val uri = try {
            java.net.URI(normalized)
        } catch (e: Exception) {
            return false
        }
        if (!ALLOWED_SCHEME.equals(uri.scheme, ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (allowedHosts.isEmpty()) return false
        return allowedHosts.any { rule -> hostMatches(host, rule.lowercase()) }
    }

    /** `*.sicau.edu.cn` 匹配 `sicau.edu.cn` 与 `jiaowu.sicau.edu.cn`；精确串只匹配自身。 */
    private fun hostMatches(host: String, rule: String): Boolean {
        return if (rule.startsWith("*.")) {
            val base = rule.removePrefix("*.")
            host == base || host.endsWith(".$base")
        } else {
            host == rule
        }
    }
}
