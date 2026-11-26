package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.ProfileSnapshot
import com.nongyu.appplus.profile.model.ProfileState
import com.nongyu.appplus.profile.model.SessionRemaining

/**
 * 画像数据的纯判定接缝（票 #112 最高接缝，纯 JVM 单测）。
 *
 * 全部函数都是确定性的：不读系统时钟、不碰 Android、不碰网络。需要「当前时间」的地方
 * 由调用方显式传入，从而在测试里锁死易错的 `when` 顺序与字段缺失组合。
 */
object ProfileLogic {

    /**
     * 合并登录响应与 `/identity/me` 两份互补数据。
     *
     * ## 优先级规则（规格 #111 / 票 #112）
     * - **`/me` 优先**：同一字段两边都有时，`/me` 的值覆盖登录响应；
     * - `/me` 侧字段为空字符串/`null` 时，**不**盖掉登录响应已有的值（互补，不让空值抹掉已知数据）；
     * - `studentNo` 只在登录响应里（`/me` 因 `omitempty` 常缺）；
     * - `avatarUrl` / `qq` 只在 `/me` 里；
     * - `hasMeResponse` 记录这次是否真的拿到了 `/me` 成功响应。
     */
    fun merge(
        login: ProfileSnapshot?,
        me: ProfileSnapshot?,
    ): ProfileSnapshot {
        if (login == null && me == null) return ProfileSnapshot()
        val l = login ?: ProfileSnapshot()
        val m = me ?: ProfileSnapshot()
        return ProfileSnapshot(
            studentNo = prefer(m.studentNo, l.studentNo),
            name = prefer(m.name, l.name),
            college = prefer(m.college, l.college),
            major = prefer(m.major, l.major),
            className = prefer(m.className, l.className),
            grade = prefer(m.grade, l.grade),
            avatarUrl = prefer(m.avatarUrl, l.avatarUrl),
            qq = prefer(m.qq, l.qq),
            // expiresAt 只来自登录响应；/me 侧没有该字段，恒取登录侧。
            expiresAtUnixSeconds = l.expiresAtUnixSeconds,
            hasMeResponse = me != null,
        )
    }

    /**
     * 存储合并策略：非空字段覆盖旧值，空字段保留旧值。
     *
     * 登录响应先落一次、`/me` 成功再落一次，两份互补数据分两次写入时不会互相抹掉。
     * 纯函数，Android 侧 DataStore 适配只负责调用它。
     */
    fun mergeForStorage(old: ProfileSnapshot, patch: ProfileSnapshot): ProfileSnapshot = old.copy(
        studentNo = prefer(patch.studentNo, old.studentNo),
        name = prefer(patch.name, old.name),
        college = prefer(patch.college, old.college),
        major = prefer(patch.major, old.major),
        className = prefer(patch.className, old.className),
        grade = prefer(patch.grade, old.grade),
        avatarUrl = prefer(patch.avatarUrl, old.avatarUrl),
        qq = prefer(patch.qq, old.qq),
        expiresAtUnixSeconds = patch.expiresAtUnixSeconds ?: old.expiresAtUnixSeconds,
        hasMeResponse = old.hasMeResponse || patch.hasMeResponse,
    )

    /** 缓存是否「非空」：任一画像字段有内容即视为有缓存。 */
    fun isCacheNonEmpty(profile: ProfileSnapshot?): Boolean {
        if (profile == null) return false
        return listOf(
            profile.studentNo, profile.name, profile.college, profile.major,
            profile.className, profile.grade, profile.avatarUrl, profile.qq,
        ).any { it != null && it.isNotBlank() }
    }

    /**
     * 三态推导（顺序**刻意固定**，测试会锁住）：
     * 1. 无凭证 → [ProfileState.NOT_SIGNED_IN]（最先判，其余状态都要求已登录）；
     * 2. `expiresAt` 明确存在且已过 → [ProfileState.SESSION_EXPIRED]
     *   （`expiresAt` 缺失表示无法判定，不判过期，交给画像完整度）；
     * 3. 画像缺关键字段（姓名或学号） → [ProfileState.INCOMPLETE]；
     * 4. 否则 → [ProfileState.READY]。
     *
     * @param hasCredential 当前是否有 App 会话凭证（真源是 SessionStore）
     * @param profile 合并后的画像快照（可为 null）
     * @param expiresAtUnixSeconds 登录响应透传的过期时间（Unix 秒，可为 null）
     * @param nowEpochSeconds 当前时间（Unix 秒，由调用方显式传入）
     */
    fun resolveState(
        hasCredential: Boolean,
        profile: ProfileSnapshot?,
        expiresAtUnixSeconds: Long?,
        nowEpochSeconds: Long,
    ): ProfileState {
        if (!hasCredential) return ProfileState.NOT_SIGNED_IN
        // expiresAt 缺失 = 无法判定，不据此判过期；只有明确给出且已过才判 SESSION_EXPIRED。
        if (expiresAtUnixSeconds != null &&
            remainingSeconds(expiresAtUnixSeconds, nowEpochSeconds) == 0L
        ) {
            return ProfileState.SESSION_EXPIRED
        }
        val merged = profile ?: ProfileSnapshot()
        if (merged.name.isNullOrBlank() || merged.studentNo.isNullOrBlank()) {
            return ProfileState.INCOMPLETE
        }
        return ProfileState.READY
    }

    /**
     * 从 `expiresAt`（Unix 秒）与给定的「当前时间」算剩余期。
     *
     * - `expiresAt == null` → 视为无法判定，返回全 0（调用方据此不展示具体期）；
     * - 已经过期或时间倒流 → 全 0，`isExpired = true`；
     * - 否则 `totalSeconds = expiresAt - now`，`totalMinutes = floor(totalSeconds / 60)`。
     */
    fun remaining(
        expiresAtUnixSeconds: Long?,
        nowEpochSeconds: Long,
    ): SessionRemaining {
        val seconds = remainingSeconds(expiresAtUnixSeconds, nowEpochSeconds)
        return SessionRemaining(
            totalSeconds = seconds,
            totalMinutes = seconds / 60,
            isUnderDay = seconds in 1..<86_400,
            isExpired = seconds == 0L,
        )
    }

    private fun remainingSeconds(expiresAtUnixSeconds: Long?, nowEpochSeconds: Long): Long {
        if (expiresAtUnixSeconds == null) return 0L
        val delta = expiresAtUnixSeconds - nowEpochSeconds
        return if (delta <= 0L) 0L else delta
    }

    private fun prefer(me: String?, login: String?): String? =
        if (me.isNullOrBlank()) login else me
}
