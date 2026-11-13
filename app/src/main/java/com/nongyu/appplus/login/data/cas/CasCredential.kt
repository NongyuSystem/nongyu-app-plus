package com.nongyu.appplus.login.data.cas

/**
 * 统一身份认证登录成功后交给农屿后端的凭据（票 #95）。
 *
 * 真源：`docs/research/backend-plus-login-ui-facts.md` §1.2 与
 * 知识库 `换票只带 go-admin-tgt 会报「未登录」`：
 * - [goAdminTgt] 必须单独填进 `POST /identity/login` 的 `goAdminTgt` 字段
 *   （触发后端 `tgtSaver.Save` 保管，供免密续期）；
 * - [cookieHeader] 是 **auth.sicau.edu.cn 域下整套会话 cookie**（`laravel_session` /
 *   `XSRF-TOKEN` 等），填进 `cookie` 字段作为出站换票的 `Cookie` 头原样透传；
 * - **单枚 tgt 就够**（权威文档 §1.1：`fetch.go:48` 只设一枚），
 *   配套 cookie 属"更保险但非必要"，有则一并透传供后端续期；
 * - [isUsable] 只要求 tgt 非空——但 tgt 必须是**从 `Set-Cookie` 头取到的合法值**
 *   （见 [TgtValidator]），不能是响应 body 的 HTML。
 *
 * 本类**不含任何真实凭据值**，只是运行时容器。
 */
data class CasCredential(
    val goAdminTgt: String?,
    val cookieHeader: String,
    /** 除 `go-admin-tgt` 外的其它同域 cookie 名（用于判定「整套」而非单枚）。 */
    val companionCookieNames: Set<String>,
) {
    /**
     * 是否可作为可用登录态交给 `method=school`。
     *
     * 判据是**有没有 tgt**，不是"有没有配套 cookie"：
     * 真站实测（2026-11-13）学校 CAS 的响应**不带 Set-Cookie**，
     * tgt 是从登录响应/轮询结果里直接给的。配套 cookie 有则一并透传（利于后端续期），
     * 没有也不算失败——否则会把成功当失败。
     */
    val isUsable: Boolean
        get() = TgtValidator.looksLikeTgt(goAdminTgt)
}
