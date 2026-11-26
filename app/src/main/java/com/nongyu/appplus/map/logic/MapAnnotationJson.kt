package com.nongyu.appplus.map.logic

import com.nongyu.appplus.map.model.AnnotationKind
import com.nongyu.appplus.map.model.MapAnnotation
import com.nongyu.appplus.map.model.MapAnnotationDto
import com.nongyu.appplus.map.model.MapDoc
import com.nongyu.appplus.map.model.MapPoint
import com.nongyu.appplus.map.model.MapPointDto
import kotlinx.serialization.json.Json

/**
 * 标注文档的**序列化编解码**（纯逻辑，零 Android / Compose 依赖，纯 JVM 可测）。
 *
 * ## JSON 形状（2026-11-26 改为带版本号的包装文档）
 *
 * ```json
 * {
 *   "version": 1,
 *   "campus": "yaan",
 *   "annotations": [
 *     {"kind":"POINT","points":[{"x":120,"y":340}],"colorArgb":-15626753},
 *     {"kind":"LINE","points":[{"x":10,"y":20},{"x":80,"y":90}],"colorArgb":-15626753},
 *     {"kind":"TEXT","points":[{"x":50,"y":60}],"text":"第一教学楼","colorArgb":-15626753}
 *   ]
 * }
 * ```
 *
 * 之前是**裸数组**，没有版本位、没有 campus，且点的坐标在存储层还被拆成 x[]/y[]。
 * 现在统一走 [MapDoc]，见该类的 KDoc 说明原因。
 *
 * ## 容错口径：一律跳过坏条目，绝不整体失败
 *
 * 一条坏数据不该让整张地图白屏。以下情况都只丢那一条：
 * - `kind` 非法 / 缺失
 * - 点数不对（POINT/TEXT 必须 1 点，LINE 至少 2 点）
 * - 坐标缺失
 *
 * 整个 JSON 解析失败（不是合法 JSON）时返回空列表。
 */
object MapAnnotationJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 解析文档为标注列表。
     *
     * @param raw JSON 字符串；空/空白返回空列表
     * @param campus 归属校区。文档里有 `campus` 时**以文档为准**，
     *   缺失时才用本参数补齐 —— 这样同一份文档不会被调用方参数悄悄改写归属。
     */
    fun parse(raw: String?, campus: String): List<MapAnnotation> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val doc = json.decodeFromString<MapDoc>(raw)
            // 版本不认识就保守返回空：比起按错误结构硬解，不如让调用方看到"没有标注"
            if (doc.version > MapDoc.CURRENT_VERSION) return emptyList()
            val owner = doc.campus.ifBlank { campus }
            doc.annotations.mapNotNull { toAnnotation(it, owner) }
        }.getOrDefault(emptyList())
    }

    /**
     * 把标注列表编码成文档 JSON。
     *
     * 用 kotlinx.serialization 而不是手拼字符串：手拼版本只转义了引号，
     * `text` 里带反斜杠或换行就会产出非法 JSON。
     */
    fun encode(campus: String, annotations: List<MapAnnotation>): String {
        val doc = MapDoc(
            version = MapDoc.CURRENT_VERSION,
            campus = campus,
            annotations = annotations.map { toDto(it) },
        )
        return encodeDoc(doc)
    }

    /** 直接序列化文档对象（调用方已有 [MapDoc] 时用，免去列表往返）。 */
    fun encodeDoc(doc: MapDoc): String = json.encodeToString(MapDoc.serializer(), doc)

    private fun toAnnotation(dto: MapAnnotationDto, campus: String): MapAnnotation? {
        val kind = runCatching {
            AnnotationKind.valueOf(dto.kind.trim().uppercase())
        }.getOrNull() ?: return null

        val points = dto.points.map { MapPoint(it.x, it.y) }

        return runCatching {
            MapAnnotation(
                id = dto.id.ifBlank { deriveId(campus, kind, points) },
                kind = kind,
                points = points,
                text = dto.text,
                colorArgb = dto.colorArgb.toInt(),
                campus = campus,
            )
        }.getOrNull() // 点数不合法时 MapAnnotation 的 init 会抛，这里跳过该条
    }

    private fun toDto(a: MapAnnotation): MapAnnotationDto = MapAnnotationDto(
        id = a.id,
        kind = a.kind.name,
        points = a.points.map { MapPointDto(it.x, it.y) },
        text = a.text,
        colorArgb = a.colorArgb.toLong(),
    )

    /** 旧数据没有 id 时按内容派生一个稳定值，保证同一条数据每次解析得到同一个 id。 */
    private fun deriveId(campus: String, kind: AnnotationKind, points: List<MapPoint>): String =
        "${campus}_${kind.name}_${points.hashCode()}"
}
