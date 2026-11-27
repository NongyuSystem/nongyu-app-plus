package com.nongyu.appplus.net.profile

import com.nongyu.appplus.net.model.LoginDto
import com.nongyu.appplus.net.model.ProfileDto
import com.nongyu.appplus.profile.model.ProfileSnapshot

/**
 * 传输 DTO → 画像快照的**纯适配**（无 Android 依赖，纯 JVM 可测）。
 *
 * 放在 `net/profile` 而不是 `profile/logic`：画像领域不依赖网络传输 DTO，
 * 适配责任归 net 侧装配层。
 */
object ProfileAdapters {

    /** `/identity/me` 成功响应 → 画像 patch（头像与 QQ 只在这里有）。 */
    fun fromMe(dto: ProfileDto): ProfileSnapshot = ProfileSnapshot(
        studentNo = dto.studentNo,
        name = dto.name,
        college = dto.college,
        major = dto.major,
        className = dto.className,
        grade = dto.grade,
        avatarUrl = dto.avatarUrl,
        qq = dto.qq,
        hasMeResponse = true,
    )

    /** 登录成功响应 → 画像 patch（学号与过期时间只在这里有）。 */
    fun fromLogin(dto: LoginDto): ProfileSnapshot = ProfileSnapshot(
        studentNo = dto.user?.studentNo,
        name = dto.user?.name,
        college = dto.user?.college,
        major = dto.user?.major,
        className = dto.user?.className,
        grade = dto.user?.grade,
        expiresAtUnixSeconds = dto.expiresAt,
        hasMeResponse = false,
    )
}
