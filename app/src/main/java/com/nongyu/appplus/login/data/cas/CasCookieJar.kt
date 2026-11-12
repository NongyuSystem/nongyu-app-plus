package com.nongyu.appplus.login.data.cas

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 统一身份认证域的 cookie 罐（票 #95）。
 *
 * ## 为什么自己实现而不是用 `JavaNetCookieJar`
 * 需要把「整套同域 cookie」**拼成一个 Cookie 头字符串**交给农屿后端的
 * `POST /identity/login { cookie = ... }`，而 `JavaNetCookieJar` 只负责自动回送、
 * 不暴露「当前罐里有哪些 cookie」的读取接口。自己实现 `CookieJar` 既能在 OkHttp
 * 链路上自动携带，也能随时 [cookieHeader] 取出整套。
 *
 * ## 关键约束（知识库实证）
 * `go-admin-tgt` 是换票凭据，但**单枚 tgt 不足以续期**：必须同时保留
 * `laravel_session` / `XSRF-TOKEN` 等配套同域 cookie，否则后端换票返回
 * `code=500 未登录`。[toCredential] 因此把「有没有配套 cookie」作为 `isUsable` 判据。
 */
class CasCookieJar : CookieJar {

    /** name → Cookie，同名以新值覆盖；保持插入顺序以便拼出稳定 Cookie 头。 */
    private val store = LinkedHashMap<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.values.filter { it.matches(url) }

    /** 拼成 `name=value; name=value` 形式，供 `cookie` 字段原样透传。 */
    @Synchronized
    fun cookieHeader(): String = store.values.joinToString("; ") { "${it.name}=${it.value}" }

    /** 从同名 cookie 读出 tgt 值；没有返回 null。 */
    @Synchronized
    fun goAdminTgt(): String? = store[TGT_COOKIE]?.value

    /** 产出交给 `method=school` 的凭据。 */
    @Synchronized
    fun toCredential(): CasCredential {
        val companions = store.keys.filter { it != TGT_COOKIE }.toSet()
        return CasCredential(
            goAdminTgt = goAdminTgt(),
            cookieHeader = cookieHeader(),
            companionCookieNames = companions,
        )
    }

    @Synchronized
    fun clear() {
        store.clear()
    }

    companion object {
        /** 真源：后端 `internal/school/schoolauth/jiaowu.go:49` 的 cookie 名常量。 */
        const val TGT_COOKIE = "go-admin-tgt"
    }
}
