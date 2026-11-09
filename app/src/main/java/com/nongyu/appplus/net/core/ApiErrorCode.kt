package com.nongyu.appplus.net.core

/**
 * `nongyu-backend-plus` 的字符串错误码常量表（规格 §3.3）。
 *
 * ## 三个来源，不要试图合并
 * | 组 | 来源 |
 * | --- | --- |
 * | (A) identity | `internal/identity/` 下 |
 * | (B) school | `internal/school/targets.go` |
 * | (C) presence | `internal/school/presence/handler.go` |
 * | (D) 中间件 | `internal/server/{auth,revocable_auth}.go` |
 * | (E) handler 手写 | 散落在各 handler，**不在任何常量表里** |
 *
 * ## 一期的失效判定只用这四个
 * [SESSION_EXPIRED] / [UNAUTHORIZED] / [AUTH_UNAVAILABLE] / [INVALID_PRESENCE]。
 * 其余 19 个只是**记录**（进日志 / 异常信息），**不产生行为分支**。
 * 把每个码都写成 `when` 分支是过度实现。
 *
 * ## `Unknown` 兜底是强制的
 * 后端仍在密集重写，漏映射是必然的。解析出的码不在本表时走 [Unknown]，
 * **不得抛异常、不得崩溃**。
 */
object ApiErrorCode {

    // ---------- (A) identity ----------

    const val UNAUTHORIZED = "unauthorized"
    const val SESSION_EXPIRED = "session_expired"
    const val BAD_CREDENTIALS = "bad_credentials"
    const val NEED_SCHOOL_AUTH = "need_school_auth"
    const val FORBIDDEN = "forbidden"
    const val USER_DISABLED = "user_disabled"
    const val LOGIN_METHOD_UNBOUND = "login_method_unbound"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"
    const val INTERNAL_ERROR = "internal_error"

    /**
     * ⚠️ **退化码**：定义在 409，但生产代码**无产出点**（死码）。
     * 常量与消费都在，唯独没有地方生成它。不要为它写正常契约分支。
     */
    const val CONFLICT = "conflict"

    // ---------- (B) school ----------

    const val TARGET_DENIED = "target_denied"
    const val BLOCKED_REDIRECT = "blocked_redirect"

    /**
     * ⚠️ **退化码**：不在 `switch` 里，落默认分支 → **实际返回 500**，`"ticket_expired"` 语义不成立。
     * 不要相信这个码能表达「票过期」。
     */
    const val TICKET_EXPIRED = "ticket_expired"

    /**
     * ⚠️ **退化码**：死码 —— 常量与消费都在，生产代码**无产出点**。
     */
    const val CAMPUS_UNREACHABLE = "campus_unreachable"

    // ---------- (C) presence ----------

    /**
     * ⚠️ **同一字面量既用于 400 也用于 401**（`presence/handler.go` L99 与 L66/L88）。
     * 只读 code 无法区分，判定**必须同时读 HTTP 状态码**。
     */
    const val INVALID_PRESENCE = "invalid_presence"

    // ---------- (D) 中间件 ----------

    const val AUTH_UNAVAILABLE = "auth_unavailable"
    const val RATE_LIMITED = "rate_limited"

    // ---------- (E) handler 手写（不在任何常量表） ----------

    /** 与 [BAD_REQUEST] **同义异名**，分属两个域，两个都要认。 */
    const val INVALID_REQUEST = "invalid_request"
    const val NOT_FOUND = "not_found"
    const val BAD_REQUEST = "bad_request"

    // ---------- 兜底 ----------

    /**
     * 全部已收录的码。用于判定「后端给了个新码」。
     *
     * 注意：这是**登记表**，不是行为分支表 —— 判定行为只走 [SessionInvalidRules]。
     */
    private val KNOWN: Set<String> = setOf(
        UNAUTHORIZED, SESSION_EXPIRED, BAD_CREDENTIALS, NEED_SCHOOL_AUTH, FORBIDDEN,
        USER_DISABLED, LOGIN_METHOD_UNBOUND, TOO_MANY_ATTEMPTS, INTERNAL_ERROR, CONFLICT,
        TARGET_DENIED, BLOCKED_REDIRECT, TICKET_EXPIRED, CAMPUS_UNREACHABLE, INVALID_PRESENCE,
        AUTH_UNAVAILABLE, RATE_LIMITED, INVALID_REQUEST, NOT_FOUND, BAD_REQUEST,
    )

    /**
     * 该码是否已收录。
     *
     * `null` / 空串 / 未收录值一律 `false` —— 调用方据此走**保守**路径
     * （不清登录态、只记日志），不抛异常、不崩溃。
     *
     * 后端仍在密集重写，漏映射是必然的，所以这个函数存在的意义是「能判断」，
     * 而不是「必须全覆盖」。
     */
    fun isKnown(raw: String?): Boolean = !raw.isNullOrEmpty() && raw in KNOWN
}
