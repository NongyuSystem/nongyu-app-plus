package com.nongyu.appplus.net.profile

import com.nongyu.appplus.profile.model.ProfileSnapshot

/**
 * 画像缓存接缝（票 #112 / ADR-0011）。
 *
 * 与 [com.nongyu.appplus.net.auth.SessionStore] 同级同风格，但**只存画像，绝不存凭证**：
 * - 令牌（App 会话凭证）只有 [com.nongyu.appplus.net.auth.SessionStore] 一个真源；
 * - 本接口存的是展示用画像快照，丢失后可以重新从登录响应 / `/me` 补回。
 *
 * 实现必须**惰性打开**且**永不抛异常**：画像落盘失败绝不能影响启动校验判定
 * （ADR-0011 / 规格 #111 US24）。
 */
interface ProfileStore {
    /** 读取当前画像；无缓存或读取失败时返回 `null`（不抛异常）。 */
    fun read(): ProfileSnapshot?

    /**
     * 合并写入画像。`patch` 中非空的字段覆盖已存值，空字段保留旧值
     * （两份互补数据分两次落盘时不会互相抹掉）。
     */
    fun write(patch: ProfileSnapshot)

    /** 清空画像缓存。幂等。 */
    fun clear()
}
