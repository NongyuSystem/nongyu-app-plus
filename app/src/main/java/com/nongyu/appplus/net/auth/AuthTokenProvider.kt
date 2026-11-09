package com.nongyu.appplus.net.auth

/**
 * 网络层取会话令牌的唯一接缝。
 *
 * ## 职责边界（不要越界）
 * - **只取，不刷新、不判过期**。刷新是 provider 实现自己的事（内部缓存 + 后台刷新，签名不变）。
 * - 一期实现（[com.nongyu.appplus.net.defaultTokenProvider]）就是「返回固定注入值」或 `null`。
 * - 将来接真实登录时**只换实现**，网络层零改动。
 *
 * 被否掉的方案：
 * - 把刷新塞进拦截器 → 网络层会悄悄承担登录职责，与「一期不做登录」冲突。
 * - `suspend` 取值器 → 一期根本不需要异步刷新，且每个请求都要进协程。
 *
 * @see com.nongyu.appplus.net.auth.SessionStore 存储接缝
 * @see com.nongyu.appplus.net.auth.SessionInvalidListener 失效回调
 */
fun interface AuthTokenProvider {
    /** 返回当前可用令牌；没有则返回 `null`（调用方据此按分区决定发不发）。 */
    fun currentToken(): String?
}
