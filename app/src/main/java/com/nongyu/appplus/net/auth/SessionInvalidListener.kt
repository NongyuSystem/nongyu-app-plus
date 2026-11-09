package com.nongyu.appplus.net.auth

/**
 * 会话失效回调接缝。
 *
 * ## 为什么必须有默认实现
 * 一期上层是空的（无 ViewModel、无登录页）。若只声明回调不提供默认实现，
 * 回调后什么都不发生 → token 还在 → 下个请求继续 401 → **死循环**。
 * 所以 [com.nongyu.appplus.net.auth.DefaultSessionInvalidListener] 会在
 * `onSessionInvalid()` 里调 `SessionStore.clear()`。
 *
 * ## 判定权不在本接口
 * 「什么算会话失效」由 [com.nongyu.appplus.net.core.SessionInvalidRules] 判定
 * （域前缀 + HTTP 状态码 + code 三者都要读）。本接口只负责「已经判定为失效之后做什么」。
 *
 * 特别注意：`/school/` 下的 `session_expired` 是**上游教务会话失效**，
 * 不代表 App 登录态失效，**不得**触发本回调。
 */
fun interface SessionInvalidListener {
    /** 已确认 App 会话失效时调用。实现方负责幂等（并发由 `AtomicBoolean` 守门）。 */
    fun onSessionInvalid()
}
