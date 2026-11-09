package com.nongyu.appplus.net

/**
 * 网络层基础配置 —— **本文件只存在于 `src/debug/` 源集**。
 *
 * ## 为什么按源集两写（不要合并回 src/main）
 * `buildConfigField` 只在 `debug {}` 里声明 `DEV_TOKEN`，**release 变体没有这个符号**。
 * 任何在 `src/main` 里引用 `BuildConfig.DEV_TOKEN` 的代码都会让 `assembleRelease` 编译失败。
 * 而「结构性保证 DEV_TOKEN 不进 release 包」又要求不能用运行时 `if (BuildConfig.DEBUG)`。
 * 两条要求的唯一交集就是：按源集放同名文件。
 *
 * 改动这里时必须同步改 `src/release/java/com/nongyu/appplus/net/NetConfig.kt`，
 * 两者签名必须保持一致，否则其中一个变体会编译失败（这是有意的：漏改会立刻暴露）。
 */
object NetConfig {
    /**
     * Retrofit 的 base URL。
     *
     * 契约：**必须以 `/` 结尾**（Retrofit 的 `baseUrl()` 要求）。缺失时由
     * [com.nongyu.appplus.net.core.UrlBuilder.normalize] 补齐并校验。
     *
     * debug 指向本机后端。蓝叠（模拟器）联调走 `adb reverse tcp:3000 tcp:3000`，
     * 不用 `10.0.2.2` —— 这样两个环境共用同一个值，避免「按 Build.FINGERPRINT 猜是否模拟器」。
     */
    const val BASE_URL: String = "http://127.0.0.1:3000/"
}

/**
 * 是否挂网络日志拦截器 —— **本文件只存在于 `src/debug/`**。
 *
 * 与 [NetConfig] 同一手法：按源集分流，而不是运行时判断 `BuildConfig.DEBUG`。
 * release 源集里有同名但返回 `false` 的实现，删掉这个文件会让 release 编译失败（有意的）。
 */
internal fun netLoggingEnabled(): Boolean = true
