package com.nongyu.appplus.net

import com.nongyu.appplus.BuildConfig
import com.nongyu.appplus.net.auth.AuthTokenProvider

/**
 * 默认令牌来源 —— **单一真源**（原来 debug/release 各一份，现已合并）。
 *
 * ## 一期口径（不要越界）
 * 本函数只做「返回固定注入值或 null」：
 * - debug：取 `BuildConfig.DEV_TOKEN`（由 `app/build.gradle.kts` 的 `debug {}` 注入，
 *   默认空串 → `null`）。用途是本地联调带 token。
 * - release：**恒为 `null`**（一期不做登录）。
 *
 * 刷新、过期判断都不在这里（见 [AuthTokenProvider] 的职责边界）。
 *
 * ## 为什么能合并写一份
 * 关键在于 `DEV_TOKEN` 这个 `buildConfigField` 现在**两个变体都有**（release 版本注入空串），
 * 所以同一份主代码可以引用它，不会出现「release 变体符号不存在」的编译失败。
 * 变体差异收敛成一处运行时判断 `BuildConfig.DEBUG`。
 *
 * 进一步的安全保证：release 注入的是**空串字面量**，
 * 即使有人改坏了 `BuildConfig.DEBUG` 的分支，也拿不到任何真实 token。
 */
internal fun defaultTokenProvider(): AuthTokenProvider {
    // release 变体这里恒为 false；debug 变体才可能为 true。
    if (!BuildConfig.DEBUG) return AuthTokenProvider { null }

    val devToken = BuildConfig.DEV_TOKEN
    return AuthTokenProvider { devToken.ifBlank { null } }
}
