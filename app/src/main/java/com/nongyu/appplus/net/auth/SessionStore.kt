package com.nongyu.appplus.net.auth

/**
 * 会话令牌存储接缝。一期只有一个实现（[MinimalSessionStore]，N5 交付）。
 *
 * ## 为什么独立成接口
 * 一期实现依赖 `androidx.security:security-crypto`（Keystore + AES），
 * **在纯 JVM 单测里不存在**。抽象成接口后：
 * - 上层逻辑（失效判据、并发去重）可以用假存储在纯 JVM 测；
 * - 将来换实现（如手写 Keystore + AES-GCM）时调用点零改动。
 *
 * ## 实现必须遵守的两条
 * 1. **惰性打开**：构造时只存 context，首次 [readToken] / [clear] 才真正建
 *    `EncryptedSharedPreferences`。Keystore 初始化是磁盘 + IPC，不能在启动路径上跑
 *    （`docs/adr/0006` + 规格 §2.4 守卫 2）。
 * 2. **读取侧容错**：解密失败（`GeneralSecurityException` / `AEADBadTagException` /
 *    `InvalidKeyException` 一族）必须当作「没有 token」并清空，**不得抛异常**。
 *    换设备恢复备份后 Key 不在，这是必然会发生的情形，不是异常情形。
 */
interface SessionStore {
    /** 读取当前令牌；不存在或解不开时返回 `null`（不抛异常）。 */
    fun readToken(): String?

    /** 写入令牌。 */
    fun writeToken(token: String)

    /** 清除令牌与底层存储。幂等。 */
    fun clear()
}
