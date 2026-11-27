package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.ProfileSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 画像缓存的编解码接缝（纯 JVM，可单测；Android 侧 DataStore 只做存储适配）。
 *
 * ## 为什么独立成纯类
 * 规格 #111 Testing Decisions：编解码与损坏容错判定放纯逻辑类，Android 侧只做存储适配。
 * 这样「文件损坏 → 视为无缓存」的行为能在纯 JVM 单测里锁死，不必起设备。
 */
object ProfileCodec {

    @Serializable
    private data class Wire(
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

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** 编码成可落盘字符串。 */
    fun encode(profile: ProfileSnapshot): String = json.encodeToString(
        Wire.serializer(),
        Wire(
            studentNo = profile.studentNo,
            name = profile.name,
            college = profile.college,
            major = profile.major,
            className = profile.className,
            grade = profile.grade,
            avatarUrl = profile.avatarUrl,
            qq = profile.qq,
            expiresAtUnixSeconds = profile.expiresAtUnixSeconds,
            hasMeResponse = profile.hasMeResponse,
        ),
    )

    /**
     * 解码；任何损坏 / 不认识的形状都返回 `null`（视为无缓存），**不抛异常**。
     */
    fun decode(raw: String?): ProfileSnapshot? {
        if (raw.isNullOrBlank()) return null
        return try {
            val w = json.decodeFromString(Wire.serializer(), raw)
            ProfileSnapshot(
                studentNo = w.studentNo,
                name = w.name,
                college = w.college,
                major = w.major,
                className = w.className,
                grade = w.grade,
                avatarUrl = w.avatarUrl,
                qq = w.qq,
                expiresAtUnixSeconds = w.expiresAtUnixSeconds,
                hasMeResponse = w.hasMeResponse,
            )
        } catch (t: Throwable) {
            null
        }
    }
}
