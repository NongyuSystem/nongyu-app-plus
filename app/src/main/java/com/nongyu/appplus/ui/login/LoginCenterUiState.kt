package com.nongyu.appplus.ui.login

/**
 * 认证域（票 #91）。取值与 `GLOSSARY.md` 一致：统一身份认证 / 平台登录。
 *
 * 这是「登录中心里两套可切换的身份来源分组」，不是登录方式本身。
 */
enum class AuthRealm {
    /** 统一身份认证（Campus SSO）：学校侧身份来源。 */
    CAMPUS_SSO,

    /** 平台登录（Platform Login）：农屿平台自有账号身份来源。 */
    PLATFORM_LOGIN,
}

/**
 * 登录方式（票 #91）：登录中心上方三个 tab 的取值。
 *
 * 注意与后端 `method` 不是同一维度：这里只决定「展示哪张输入表单」，
 * 不携带 `schoolMethod` / `method` 等后端选择（Q21）。后续实施票（S3/S4/S7）
 * 会把选中的方式映射成具体请求，但映射不在本状态里。
 */
enum class LoginMethod {
    CAMPUS_PASSWORD,
    CAMPUS_PHONE,
    CAMPUS_WECHAT,
    PLATFORM_PASSWORD,
    PLATFORM_MAIL,
    PLATFORM_OAUTH,
}

/**
 * 登录中心 UI 状态（票 #91，纯 JVM，单一主接缝的扩展）。
 *
 * ## 两条设计约束
 * - **只切输入表单**：[onMethodSelected] 只改 [selectedMethod]，不触碰认证域，
 *   也不含任何后端 `method` 概念 —— 切换 tab 不改变后端 method 选择逻辑。
 * - **oauth 禁用占位**：[PLATFORM_OAUTH][LoginMethod.PLATFORM_OAUTH] 出现在平台登录的
 *   方式列表里，但 [isEnabled] 为 false、[onMethodSelected] 选不中（点击无反应）、
 *   [labelOf] 固定「OAuth（暂未开放）」，不弹 toast、不切面板。
 */
data class LoginCenterUiState(
    val realm: AuthRealm,
    val methods: List<LoginMethod>,
    val selectedMethod: LoginMethod,
) {
    companion object {
        /** 首帧：默认统一身份认证，展示它的三种方式，默认选账密。 */
        fun initial(): LoginCenterUiState = forRealm(AuthRealm.CAMPUS_SSO)

        /** 每个认证域对应的三种方式（顺序即上方 tab 顺序）。 */
        fun methodsFor(realm: AuthRealm): List<LoginMethod> = when (realm) {
            AuthRealm.CAMPUS_SSO -> listOf(
                LoginMethod.CAMPUS_PASSWORD,
                LoginMethod.CAMPUS_PHONE,
                LoginMethod.CAMPUS_WECHAT,
            )

            AuthRealm.PLATFORM_LOGIN -> listOf(
                LoginMethod.PLATFORM_PASSWORD,
                LoginMethod.PLATFORM_MAIL,
                LoginMethod.PLATFORM_OAUTH,
            )
        }

        private fun forRealm(realm: AuthRealm): LoginCenterUiState {
            val methods = methodsFor(realm)
            return LoginCenterUiState(
                realm = realm,
                methods = methods,
                selectedMethod = methods.first { isEnabled(it) },
            )
        }

        /** 仅 oauth 禁用；其余方式均可点。 */
        fun isEnabled(method: LoginMethod): Boolean =
            method != LoginMethod.PLATFORM_OAUTH

        /** tab 文案。oauth 固定显示「暂未开放」。 */
        fun labelOf(method: LoginMethod): String = when (method) {
            LoginMethod.CAMPUS_PASSWORD -> "账密"
            LoginMethod.CAMPUS_PHONE -> "手机号验证码"
            LoginMethod.CAMPUS_WECHAT -> "微信扫码"
            LoginMethod.PLATFORM_PASSWORD -> "账密"
            LoginMethod.PLATFORM_MAIL -> "邮箱验证码"
            LoginMethod.PLATFORM_OAUTH -> "OAuth（暂未开放）"
        }
    }

    /** 下方切换按钮：在两个认证域之间切换，切后默认选该域第一个可用方式。 */
    fun onRealmSwitched(): LoginCenterUiState =
        forRealm(if (realm == AuthRealm.CAMPUS_SSO) AuthRealm.PLATFORM_LOGIN else AuthRealm.CAMPUS_SSO)

    /**
     * 上方 tab 点击：只改 [selectedMethod]。
     * 禁用方式、不属于当前认证域的方式都保持原选择（点击无反应）。
     */
    fun onMethodSelected(method: LoginMethod): LoginCenterUiState {
        if (!isEnabled(method) || method !in methods) return this
        return copy(selectedMethod = method)
    }
}
