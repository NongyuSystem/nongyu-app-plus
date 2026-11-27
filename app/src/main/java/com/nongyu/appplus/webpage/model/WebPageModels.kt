package com.nongyu.appplus.webpage.model

/**
 * WebView 容器的配置与 Cookie 桥（票 #119）。
 *
 * `webpage` 顶层包**不 import net 内部实现**；网络层要注入 Cookie 时实现本接口，
 * 由装配点传入。这样 `webpage/logic` 保持纯 JVM、`webpage/ui` 只依赖 Android WebView。
 */

/** 一个可内嵌浏览的页面配置。 */
data class WebPageConfig(
    /** 首屏加载地址（会被白名单判定）。 */
    val url: String,
    /** 允许的 host 集合（`*.` 前缀表示含子域）。 */
    val allowedHosts: Set<String>,
    /** 页面标题（返回键/加载失败态展示）。 */
    val title: String,
    /** 是否允许 JS（学校站点需要）。 */
    val javaScriptEnabled: Boolean = true,
)

/**
 * Cookie 桥：由装配点注入，把 App 会话 Cookie 写给 WebView。
 *
 * `webpage` 只声明接口，不含 net 依赖；net 侧的实现在装配处 new 出来传进来。
 */
fun interface WebPageCookieBridge {
    /** 把与 [url] 同域的 Cookie 同步到 WebView 的 CookieManager。 */
    fun syncCookies(url: String)
}
