package com.nongyu.appplus.net

import com.nongyu.appplus.BuildConfig
import com.nongyu.appplus.net.auth.AuthTokenProvider

/**
 * 默认令牌来源 —— **本文件只存在于 `src/debug/` 源集**。
 *
 * 这里能安全引用 `BuildConfig.DEV_TOKEN`，因为该字段只在 `debug {}` 里用
 * `buildConfigField` 声明。release 源集有同名但返回 `null` 的实现，
 * 因此「DEV_TOKEN 进不了 release 包」是**结构性保证**，不依赖运行时判断。
 *
 * ## 怎么注入调试 token
 * 默认是空串（不把真实 token 写进仓库）。三种方式任选：
 * 1. `./gradlew :app:assembleDebug -PdevToken=<token>`
 * 2. 在 `~/.gradle/gradle.properties` 写 `devToken=<token>`（该文件不在仓库内）
 * 3. 将来接真实登录后，改由 `SessionStore` 提供，本函数退役
 *
 * ## 为什么需要它
 * 后端 `POST /identity/login` 目前在可吊销装配下返回 401（尾斜杠不匹配，见
 * `docs/research/backend-plus-app-contract.md` §4.3），调试期走不通真登录。
 * 没有这个兜底，任何需要鉴权的端点都无法在真机上验证。
 */
internal fun defaultTokenProvider(): AuthTokenProvider {
    val devToken = BuildConfig.DEV_TOKEN
    return AuthTokenProvider { devToken.ifBlank { null } }
}
