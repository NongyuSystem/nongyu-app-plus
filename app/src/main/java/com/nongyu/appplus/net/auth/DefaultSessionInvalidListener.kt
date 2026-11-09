package com.nongyu.appplus.net.auth

import com.nongyu.appplus.net.core.NongyuLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 默认失效处理：清掉本地令牌。
 *
 * ## 为什么必须有它（而不是纯回调）
 * 一期上层是空的（无 ViewModel、无登录页）。若只声明 [SessionInvalidListener] 而不提供实现，
 * 失效回调后**什么都不发生** → token 还在 → 下个请求继续 401 → **死循环**。
 *
 * ## 并发去重
 * 同一屏可能并发发 5 个请求，触发 5 次 401。用 `AtomicBoolean` 守门：
 * **首个**满足失效条件时 `compareAndSet(false, true)` 成功 → 真正清理 + 回调；
 * 其余**静默丢弃**。
 *
 * 复位时机（两个条件都满足）：① 清理完成；② 下次携带**新** token 的请求发起前。
 * 一期没有登录就没有「新 token」，所以复位路径**一期只有测试能覆盖** ——
 * 这是「一期只做接缝」的必然结果，但实现写全，不留 TODO。
 *
 * ## 判定权不在这里
 * 「什么算失效」由 `net/core/SessionInvalidRules` 判定（域前缀 + HTTP 状态码 + code 三者都要读）。
 * 本类只负责「已经判定为失效之后做什么」。
 */
class DefaultSessionInvalidListener(
    private val store: SessionStore,
    private val logger: NongyuLogger = NongyuLogger.Noop,
    private val onCleared: (() -> Unit)? = null,
) : SessionInvalidListener {

    private val triggered = AtomicBoolean(false)

    override fun onSessionInvalid() {
        // 首个置位者做清理，其余丢弃
        if (!triggered.compareAndSet(false, true)) {
            logger.log(NongyuLogger.Level.DEBUG, "会话失效回调已处理过，本次丢弃")
            return
        }
        try {
            store.clear()
            onCleared?.invoke()
        } catch (e: RuntimeException) {
            logger.log(NongyuLogger.Level.WARN, "清理会话失败", e)
        }
    }

    /**
     * 复位守门标志。
     *
     * **只在「清理完成 + 下次带新 token 的请求发起前」调用**。
     * 一期的 `AuthTokenProvider` 永远返回固定值或 null，不会有新 token，
     * 因此本方法一期只被测试调用 —— 但必须存在，否则将来接真实登录时无从下手。
     */
    fun reset() {
        triggered.set(false)
    }

    /** 当前是否已触发（测试用）。 */
    fun isTriggered(): Boolean = triggered.get()
}
