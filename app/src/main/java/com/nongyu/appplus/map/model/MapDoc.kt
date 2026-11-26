package com.nongyu.appplus.map.model

import kotlinx.serialization.Serializable

/**
 * 标注文档：**地图标注的唯一序列化真源**（纯数据，零 Android / Compose 依赖）。
 *
 * ## 为什么要有这一层（2026-11-26 重构）
 *
 * 重构前同一个概念在仓里有三套不兼容的形状：
 *
 * | 位置 | 形状 |
 * |---|---|
 * | `MapAnnotationJson` 契约 | 裸数组 `[{kind, points:[{x,y}], ...}]` |
 * | `MapAnnotationStore` 存储 | `{items:[{id, kind, x:[], y:[], ...}]}`（x/y 拆成并行数组）|
 * | `MapEditor` 兼容重载 | 手写字符串拼接生成 JSON |
 *
 * 三者各写一套，问题有三类：
 *
 * 1. **裸数组没有版本位**：将来改结构只能靠 `ignoreUnknownKeys` 硬扛，无法做迁移；
 * 2. **手拼 JSON 是 bug 温床**：`encodeAnnotations` 只转义了引号，
 *    没处理反斜杠与换行，`text` 里带 `\` 就会产出非法 JSON；
 * 3. **x[]/y[] 并行数组会静默错位**：两条长度不等时补 0，
 *    点坐标就悄悄变了，不报错也看不出来。
 *
 * 现在统一成**一个带版本号的包装文档** [MapDoc]，内部点统一用 `[{x,y}]`：
 * 契约、存储、组件三处共用同一套 DTO，不再各自解释。
 */
@Serializable
data class MapDoc(
    /**
     * 文档版本。
     *
     * 加这个字段的唯一理由就是**将来能迁移**：读到不认识的版本时，
     * 调用方可以明确拒绝或走转换，而不是像裸数组那样只能默默丢字段。
     */
    val version: Int = CURRENT_VERSION,
    /** 所属校区（`yaan` / `chengdu` / `dujiangyan`）。 */
    val campus: String = "",
    /** 全部标注。 */
    val annotations: List<MapAnnotationDto> = emptyList(),
) {
    companion object {
        /** 当前文档版本。改 [MapAnnotationDto] 形状时必须递增。 */
        const val CURRENT_VERSION: Int = 1
    }
}

/**
 * 一条标注的序列化形状。
 *
 * @param id 稳定 id（旧数据可能缺省，解析时按内容派生）
 * @param kind `POINT` / `LINE` / `TEXT`（大小写不敏感）
 * @param points 图像像素坐标；POINT/TEXT 一点，LINE 至少两点
 * @param text 仅 TEXT 有意义
 * @param colorArgb ARGB（用 Long 承接，兼容 Java int 的负数）
 */
@Serializable
data class MapAnnotationDto(
    val id: String = "",
    val kind: String = "",
    val points: List<MapPointDto> = emptyList(),
    val text: String = "",
    val colorArgb: Long = 0L,
)

/** 一个图像像素坐标点。 */
@Serializable
data class MapPointDto(
    val x: Float = 0f,
    val y: Float = 0f,
)
