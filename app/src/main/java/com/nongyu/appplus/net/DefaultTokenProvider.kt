package com.nongyu.appplus.net

import com.nongyu.appplus.BuildConfig
import com.nongyu.appplus.net.auth.AuthTokenProvider
import com.nongyu.appplus.net.auth.SessionStore

/**
 * 默认令牌来源 —— **单一真源**。
 *
 * ## 现状（票 #90）
 * 本函数返回的 provider 每次 [AuthTokenProvider.currentToken]
 * 都从 [SessionStore.readToken] 取当前 App 会话凭证，作为网络层的凭证来源：
 * - 登录成功后由上层 `writeToken()` 落盘（S3/S4/S7）；
 * - 会话失效时由既有失效分流 `clear()` 清掉，下一次读取自然是 `null`；
 * - `readToken()` 内部对解密失败/文件损坏已容错，解不开即「没有凭证」。
 *
 * 调试期仍保留 `DEV_TOKEN` 兜底：本地联调可以用 `./gradlew -PdevToken=...` 直接带票，
 * 不依赖真实登录。两个变体都有 `DEV_TOKEN` 这个 `buildConfigField`（release 注入空串），
 * 所以同一份主代码引用它不会编译失败；release 里即使分支被改坏也拿不到真实票。
 *
 * ## 惰性
 * [SessionStore] 的实现（`MinimalSessionStore`）构造时只存 context，首次读写才真正打开
 * Keystore。这里只是持有一个引用，**不在装配阶段读盘**，因此不违反 ADR-0006。
 */
internal fun defaultTokenProvider(store: SessionStore): AuthTokenProvider {
    val devToken = if (BuildConfig.DEBUG) BuildConfig.DEV_TOKEN else ""
    return AuthTokenProvider {
        // 会话存储是唯一真源；DEV_TOKEN 仅作本地联调兜底，且永远不覆盖存储里的真值。
        store.readToken() ?: devToken.ifBlank { null }
    }
}
