package com.nongyu.appplus.net.core

/**
 * 会话失效判据（规格 §4.3）。**纯逻辑，纯 JVM 可测。**
 *
 * ## 为什么不能只看 `code`
 * `session_expired` 这**一个字符串**被后端用在两种完全不同的语义上：
 * - `internal/server/revocable_auth.go:177` → **App 会话过期**
 * - `internal/school/jiaowu/handler.go:114-115` → **上游教务会话失效**
 *
 * 两者同为 `401`、字面量完全相同。只看 code 的后果：
 * **学生在教务侧掉线时，App 会把用户踢出登录。**
 *
 * ## 判定必须同时读三者
 * 域前缀 + HTTP 状态码 + code，缺一不可。
 * 例：`invalid_presence` 同一字面量既用于 400 也用于 401，只读 code 无法区分。
 *
 * ## ⚠️ 这是客户端单方面的协议补丁，不是后端契约
 * 后端将来若给其中一个 `session_expired` 正名，这里必须同步改。
 * **不得在任何地方把它描述成「后端保证」。**
 */
object SessionInvalidRules {

    /** 判定结果：网络层下一步该做什么。 */
    enum class Decision {
        /** 清本地登录态 + 回调 `onSessionInvalid()`。 */
        CLEAR_SESSION,

        /** 不动登录态。属「上游教务会话失效」，只记日志。 */
        KEEP_SESSION_SCHOOL_AUTH,

        /** 不动登录态。服务暂不可用，可重试。 */
        KEEP_SESSION_RETRYABLE,

        /** 不动登录态。**保守默认**：任何未匹配情形都落这里。 */
        KEEP_SESSION_UNKNOWN,
    }

    /**
     * 前缀匹配表。**必须「最长前缀优先」**。
     *
     * `#52` 的表里写的 `/presence` 是简写，真实路径是 `/school/presence`
     * （`presence.Register` 挂在 `/school` 下）。若顺序写反，presence 规则永远命不中 ——
     * 行为上恰好不致命（落到「未匹配 → 不清登录态」也是对的），但那属于碰巧对，不能留。
     */
    private val PREFIXES_LONGEST_FIRST: List<String> = listOf(
        "/school/presence",
        "/school/",
        "/identity/",
    )

    /**
     * 判定一次失败是否意味着 App 会话失效。
     *
     * @param path 请求路径，如 `/identity/me`、`/school/presence`
     * @param status HTTP 状态码
     * @param code 后端返回的字符串错误码（可能为 `null`）
     */
    fun decide(path: String, status: Int, code: String?): Decision {
        // 最长前缀优先：/school/presence 先于 /school/ 命中（顺序在 PREFIXES_LONGEST_FIRST 里固定）
        val domain = PREFIXES_LONGEST_FIRST.firstOrNull { path.startsWith(it) }
            ?: return Decision.KEEP_SESSION_UNKNOWN

        return when (domain) {
            // App 会话真的过期/无效 —— 只有 identity 域允许清登录态
            "/identity/" -> when {
                status == 401 && code in SESSION_ENDED_CODES -> Decision.CLEAR_SESSION
                // 服务暂不可用，不是会话失效
                status == 503 && code == ApiErrorCode.AUTH_UNAVAILABLE -> Decision.KEEP_SESSION_RETRYABLE
                else -> Decision.KEEP_SESSION_UNKNOWN
            }

            // ⚠️ 教务域的 session_expired 是「上游教务会话失效」，绝不清 App 登录态
            "/school/" -> when {
                status == 401 && code == ApiErrorCode.SESSION_EXPIRED ->
                    Decision.KEEP_SESSION_SCHOOL_AUTH
                else -> Decision.KEEP_SESSION_UNKNOWN
            }

            // presence：invalid_presence 既用于 400 也用于 401，两种情况都不代表 App 登录失效。
            // 单独列出来是为了让「为什么 presence 不走 school 那条规则」可见，而不是靠巧合。
            "/school/presence" -> Decision.KEEP_SESSION_UNKNOWN

            else -> Decision.KEEP_SESSION_UNKNOWN
        }
    }

    /** identity 域里表示「会话真的结束了」的码。 */
    private val SESSION_ENDED_CODES = setOf(
        ApiErrorCode.SESSION_EXPIRED,
        ApiErrorCode.UNAUTHORIZED,
    )

    /** `decide()` 结果的便捷判断：是否应当清登录态。 */
    fun shouldClearSession(decision: Decision): Boolean = decision == Decision.CLEAR_SESSION
}
