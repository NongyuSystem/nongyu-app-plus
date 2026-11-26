package com.nongyu.appplus.timetable.logic

/**
 * 日程可选颜色色板（纯数据，无 Android/Compose 依赖，可纯 JVM 单测）。
 *
 * ## 为什么放在 logic 而不是 UI
 *
 * 同一份色板有**两个消费者**：
 * 1. 添加日程对话框（选色）
 * 2. 课表上的日程卡片（按存下的 [ScheduleEntry.colorIndex] 上色）
 *
 * 原先它写在 `AddScheduleDialog` 里且是 `private`，日程卡片只能写死品牌色，
 * 于是"选颜色无用"——存了 `colorIndex` 却没人读。
 * 抽到 logic 后两边共用同一份，选什么颜色卡片就是什么颜色。
 *
 * 值与课程卡的哈希色板刻意区分：课程色是按课程名散列出来的，
 * 这里是有序固定色（用户主动选，语义明确）。
 */
object SchedulePalette {

    /** 颜色的 ARGB 值（Long，避免 logic 层依赖 Compose 的 Color）。 */
    private val ARGB = listOf(
        0xFF0A7C59L, // 品牌绿
        0xFF3F7FBFL, // 蓝
        0xFFB06A2CL, // 橙
        0xFF8A6BBEL, // 紫
        0xFFB3455EL, // 红
        0xFF5C7A3FL, // 橄榄
    )

    /** 色板大小（选色区的格子数）。 */
    val size: Int get() = ARGB.size

    /** 全部颜色的 ARGB，供选色区逐个绘制。 */
    fun all(): List<Long> = ARGB

    /** 第 [index] 个颜色的 ARGB。越界时夹到合法范围（不抛异常，卡片永远能上色）。 */
    fun argbAt(index: Int): Long = ARGB[index.coerceIn(0, ARGB.lastIndex)]

    /**
     * 解析某个日程该用的颜色。
     *
     * @param colorIndex 日程存下的色板下标；`null`（旧数据/未选）→ 返回 null，
     *   由调用方回落到主题品牌色。**不在这里写死品牌色**，
     *   因为品牌色来自皮肤，logic 层不该知道。
     */
    fun argbOrNull(colorIndex: Int?): Long? =
        colorIndex?.let { argbAt(it) }
}
