package com.nongyu.appplus.net.core

/**
 * 极简日志接缝。
 *
 * ## 为什么需要这一层
 * `android.util.Log` 在**纯 JVM 单测里是 stub**，任何调用都会抛
 * `RuntimeException: Method d in android.util.Log not mocked`（与 `org.json` 同一类问题）。
 *
 * 网络层里最需要单测的东西恰好是「失效判据 / 重试判定 / 会话清理」这些**决策逻辑**，
 * 它们天然要打日志。若直接调 `android.util.Log`，这些逻辑就全部无法在纯 JVM 里验证 ——
 * 而那恰恰是最容易写错的部分。
 *
 * ## 做法
 * 与 `skin/` 的既有范式一致：**纯逻辑层只依赖本接口，Android 薄壳提供实现**。
 * - 生产：`net/http/AndroidLogger` → `android.util.Log`
 * - 测试：默认 [Noop]（什么都不做），或收集器实现
 *
 * ## 为什么默认是 no-op 而不是抛异常
 * 默认 no-op 让纯逻辑类在测试里**无需注入任何东西就能构造**，降低测试噪音；
 * 需要断言日志的用例再显式传入收集器。
 */
interface NongyuLogger {

    fun log(level: Level, message: String, throwable: Throwable? = null)

    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    companion object {
        /** 什么都不做的实现 —— 测试与「不需要日志」场景的默认值。 */
        val Noop: NongyuLogger = object : NongyuLogger {
            override fun log(level: Level, message: String, throwable: Throwable?) = Unit
        }

        /** 便捷构造：只需要一条 lambda 时用它。 */
        inline fun of(crossinline block: (Level, String, Throwable?) -> Unit): NongyuLogger =
            object : NongyuLogger {
                override fun log(level: Level, message: String, throwable: Throwable?) =
                    block(level, message, throwable)
            }
    }
}
