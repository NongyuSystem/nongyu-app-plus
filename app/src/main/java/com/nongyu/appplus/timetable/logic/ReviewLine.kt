package com.nongyu.appplus.timetable.logic

/**
 * 评价在评论列表里的一行（纯数据，无 Android 依赖）。
 *
 * @property text 展示文本（含评分星级前缀）
 * @property isMine 是否本人所发（后端 `isMine`），用于加深颜色
 */
data class ReviewLine(
    val text: String,
    val isMine: Boolean,
)
