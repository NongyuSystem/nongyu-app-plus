package com.nongyu.appplus.profile.logic

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 公告 RFC3339 时间字符串的解析与展示文案（票 #114，纯 JVM）。
 *
 * 放在 logic 层而不是 UI 层：字符串解析规则与「坏值如何降级」容易被边界情况坑到，
 * 这里锁死为「解析不出来就返回 null」，数据层据此把边界视为无约束。
 * 不 import Android/Compose，纯 JVM 单测覆盖。
 */
object AnnouncementText {

    /**
     * 解析后端 RFC3339 时间串为 epoch 毫秒；null / 空 / 非法串一律返回 null。
     * 支持 `Z`、`+08:00` 等偏移，以及带小数秒的形式。
     */
    fun parseRfc3339Millis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                Instant.parse(raw).toEpochMilli()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * 列表展示用的简化时间文案：取 `yyyy-MM-dd HH:mm`（UTC 口径，不编造时区），
     * 空值或解析失败给占位符「—」。不改动原始值也不判生效。
     */
    fun displayTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val millis = parseRfc3339Millis(raw) ?: return "—"
        val dt = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC)
        return dt.format(DISPLAY_FORMAT)
    }

    private val DISPLAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
}
