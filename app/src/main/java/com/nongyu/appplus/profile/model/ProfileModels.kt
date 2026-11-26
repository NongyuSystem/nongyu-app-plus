package com.nongyu.appplus.profile.model

import kotlinx.serialization.Serializable

/**
 * 画像领域模型（`profile/model`，纯 JVM，禁止 import android.* / androidx.compose.*）。
 *
 * 设计取舍：本包**不复用** `net/model` 的 `ProfileDto` / `UserDto` / `LoginDto`。
 * 网络 DTO 属于传输层，字段含义随后端契约变动；画像领域只声明页面与判定真正需要的
 * 结构化字段。装配点（data 层）负责把两份互补的传输 DTO 收敛成 [ProfileSnapshot]。
 */

/**
 * 一份画像快照：从登录响应与 `/identity/me` 两份互补数据合并而来的**结构化结果**。
 *
 * - `studentNo` / `name` / `college` / `major` / `className` / `grade` 来自登录响应或 `/me`；
 * - `avatarUrl` / `qq` 只可能来自 `/me`；
 * - `expiresAtUnixSeconds` 来自登录响应透传（不是凭证，是会话过期时间戳）；
 * - `hasMeResponse` 记录是否真的拿到过 `/me` 成功响应（供「数据不全」判定）。
 */
@Serializable
data class ProfileSnapshot(
    val studentNo: String? = null,
    val name: String? = null,
    val college: String? = null,
    val major: String? = null,
    val className: String? = null,
    val grade: String? = null,
    val avatarUrl: String? = null,
    val qq: String? = null,
    val expiresAtUnixSeconds: Long? = null,
    val hasMeResponse: Boolean = false,
)

/** 登录态剩余期（由透传的 `expiresAt` Unix 秒算出，此前无人消费）。 */
data class SessionRemaining(
    /** 距离过期还剩的完整分钟数，向下取整，最小 0。 */
    val totalMinutes: Long,
    /** 距离过期还剩的秒数（用于分钟以下的展示），最小 0。 */
    val totalSeconds: Long,
    /** 剩余是否不足一天（可按此决定展示精度）。 */
    val isUnderDay: Boolean,
    /** 是否已经过期（`totalSeconds == 0`）。 */
    val isExpired: Boolean,
)

/** 页面三态（由纯函数 [com.nongyu.appplus.profile.logic.ProfileLogic] 推导）。 */
enum class ProfileState {
    /** 未登录：没有 App 会话凭证。 */
    NOT_SIGNED_IN,

    /** 已登录但画像数据不全：有凭证，但合并后的画像缺关键字段。 */
    INCOMPLETE,

    /** 会话过期：有凭证但已过 `expiresAt`。 */
    SESSION_EXPIRED,

    /** 已登录且画像完整。 */
    READY,
}
