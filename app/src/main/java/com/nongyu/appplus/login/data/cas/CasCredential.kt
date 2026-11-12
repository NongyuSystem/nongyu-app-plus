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
 * - 只交单枚 tgt 换票会返回 `code=500 未登录`，所以 [isUsable] 要求两者齐备且
 *   除 tgt 外至少还有一枚配套 cookie。
 *
 * 本类**不含任何真实凭据值**，只是运行时容器。
 */
data class CasCredential(
    val goAdminTgt: String?,
    val cookieHeader: String,
    /** 除 `go-admin-tgt` 外的其它同域 cookie 名（用于判定「整套」而非单枚）。 */
    val companionCookieNames: Set<String>,
) {
    /** 是否可作为可用登录态交给 `method=school`。 */
    val isUsable: Boolean
        get() = !goAdminTgt.isNullOrBlank() && companionCookieNames.isNotEmpty()
}
