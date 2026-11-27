package com.nongyu.appplus.webpage.logic

import java.net.URI
import java.net.URISyntaxException

/**
 * URL 归一化（票 #119，纯 JVM，零 Android/Compose import）。
 *
 * WebView 容器在判定白名单与真正 loadUrl 之前都先归一：补尾斜杠、去 fragment、
 * 小写 scheme/host，避免「同一个站点写法不同导致一会儿放行一会儿拦截」。
 */
object UrlNormalizer {

    /**
     * 归一 URL：
     * - 去首尾空白；
     * - scheme 与 host 转小写；
     * - 去掉 fragment（`#...`，WebView 导航不靠它判白名单）；
     * - 根路径（path 为空）补 `/`；
     * - 保留 query。
     *
     * 无法解析（非绝对 URL / 语法错）返回 null，由调用方按「不允许」处理。
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = URI(raw.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val portPart = if (uri.port == -1) "" else ":${uri.port}"
            val path = uri.path?.takeIf { it.isNotEmpty() } ?: "/"
            val queryPart = uri.query?.let { "?$it" } ?: ""
            "$scheme://$host$portPart$path$queryPart"
        } catch (e: URISyntaxException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** 只取 host（小写）；无法解析返回 null。白名单判定用。 */
    fun hostOf(raw: String?): String? = normalize(raw)?.let { URI(it).host?.lowercase() }
}
