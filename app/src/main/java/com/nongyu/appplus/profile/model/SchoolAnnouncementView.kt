package com.nongyu.appplus.profile.model

/**
 * 校内信息公告的展示模型（票 #114）。
 *
 * 时间字段已由数据层从 RFC3339 字符串解析成 epoch 毫秒；后端未给或解析失败时为 null，
 * 按「无边界」处理。纯逻辑层只做窗口判定，不碰字符串解析与系统时钟。
 */
data class SchoolAnnouncementView(
    val id: String,
    val title: String,
    val body: String,
    val publishedAt: String? = null,
    val effectiveFromRaw: String? = null,
    val effectiveFromMillis: Long? = null,
    val expiresAtRaw: String? = null,
    val expiresAtMillis: Long? = null,
    val pinned: Boolean = false,
)
