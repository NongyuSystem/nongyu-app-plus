package com.nongyu.appplus.skin

import org.json.JSONArray
import org.json.JSONObject

/**
 * 皮肤包清单（`manifest.json`）的解析层（票 #40）。
 *
 * ## 分层方式与 [ColorPayloadParser] 一致，理由也一样
 *
 * `org.json` 在纯 JVM 单测（`:app:testDebugUnitTest`）里是 Android **stub**，
 * 一调用就抛 `RuntimeException: Method ... not mocked`（已由仓库里的探针实测确认）。
 * 所以：
 * - **判定逻辑**放在 [SkinManifestParser.parseMap] —— 只接收 `Map`/基础类型，可全量单测；
 * - **本文件下半部分**只做「把 JSON 取成 Map」的类型转换，几乎无分支。
 *
 * ## 解析失败必须返回 `null` 而不是抛异常
 *
 * 清单是外部数据（B 端上传）。任何一个字段缺失、类型不对、字节是乱码，
 * 都只能导致「这套包不可用」，**不能导致 App 打不开**（ADR-0006 优先级不对称）。
 */
object SkinManifestParser {

    /** 客户端支持的最高格式主版本（ADR-0003 第①层：主版本过高整包拒绝）。 */
    const val SUPPORTED_SCHEMA_MAJOR: Int = 1

    /**
     * 从「已取出的键值对」构造清单。**纯函数，可全量单测。**
     *
     * @param raw 顶层字段 + 已展开的 entries/iconStrokes。
     * @return 清单；`null` = 必填项缺失或类型非法（包不可用，回落内置默认）。
     */
    fun parseMap(raw: ManifestRaw): SkinPackManifest? {
        // 6 项必填（ADR-0003）：任一缺失即整包不可用。
        val packId = raw.packId?.trim().orEmpty()
        if (packId.isEmpty()) return null

        val schema = raw.schemaVersion ?: return null
        if (schema <= 0) return null

        val minApp = raw.minAppVersion ?: return null
        if (minApp < 0) return null

        val displayName = raw.displayName?.trim().orEmpty()
        if (displayName.isEmpty()) return null

        val signature = raw.signature?.trim().orEmpty()
        if (signature.isEmpty()) return null

        // entries：完整性块。空清单的包没有意义（没有任何可替换资源）。
        val entries = raw.entries ?: return null
        if (entries.isEmpty()) return null
        // 路径必须全部合法；有一个不合法就整包拒绝（安全优先于可用性）。
        for (entry in entries) {
            if (SkinPackIntegrity.checkPathAllowed(entry.path) != null) return null
            if (entry.sha256.isBlank()) return null
            if (!entry.sha256.all { it.isDigit() || it in 'a'..'f' }) return null
            if (entry.sha256.length != 64) return null
        }
        // 路径不得重复（重复会让「按路径取字节」产生歧义）。
        if (entries.map { it.path }.toSet().size != entries.size) return null

        // 字体路径若声明，也必须是合法相对路径，且必须出现在 entries 里。
        val font = raw.fontFileName?.trim()?.takeIf { it.isNotEmpty() }
        if (font != null) {
            if (SkinPackIntegrity.checkPathAllowed(font) != null) return null
            if (entries.none { it.path == font }) return null
        }

        // 图标线宽：非法值直接剔除（逐资源回落，不阻断整包）。
        val strokes = raw.iconStrokes.orEmpty()
            .mapNotNull { (name, value) ->
                val clean = SkinPackPlanner.sanitizeStroke(value) ?: return@mapNotNull null
                NavIconKey(name) to clean
            }
            .toMap()

        return SkinPackManifest(
            packId = packId,
            schemaVersion = schema,
            minAppVersion = minApp,
            displayName = displayName,
            iconStrokes = strokes,
            fontFileName = font,
            entries = entries,
            signature = signature,
        )
    }

    /**
     * 从 JSON 文本解析清单。**这是唯一允许碰 `org.json` 的地方。**
     *
     * @return 清单；`null` = 文本不是合法 JSON，或必填项不满足。
     */
    fun parseJson(manifestJson: String): SkinPackManifest? = try {
        val obj = JSONObject(manifestJson)
        parseMap(
            ManifestRaw(
                packId = obj.optString("packId", null),
                schemaVersion = obj.optIntOrNull("schemaVersion"),
                minAppVersion = obj.optIntOrNull("minAppVersion"),
                displayName = obj.optString("displayName", null),
                signature = obj.optString("signature", null),
                fontFileName = obj.optString("fontFileName", null),
                iconStrokes = obj.optJSONObject("iconStrokes")?.let { o ->
                    buildMap {
                        for (key in o.keys()) {
                            // 只接受数值；非数值项在 parseMap 里本就无法 sanitize，这里不抛。
                            val v = o.optDouble(key, Double.NaN)
                            if (!v.isNaN()) put(key, v.toFloat())
                        }
                    }
                },
                entries = obj.optJSONArray("entries")?.let { arr ->
                    buildList {
                        for (i in 0 until arr.length()) {
                            val e = arr.optJSONObject(i) ?: continue
                            val path = e.optString("path", null) ?: continue
                            val sha = e.optString("sha256", null) ?: continue
                            add(SkinPackEntry(path = path.trim(), sha256 = sha.trim().lowercase()))
                        }
                    }
                },
            ),
        )
    } catch (e: Exception) {
        // 乱码 JSON / 类型错乱一律视为「包不可用」，不抛给调用方。
        null
    }

    /** `optInt` 无法区分「缺失」与「0」，而 0 不是合法值 —— 这里显式区分。 */
    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

    /** `optString` 在缺失时返回 `""`；这里显式返回 `null` 以便与「空串」区分。 */
    private fun JSONObject.optString(key: String, fallback: String?): String? =
        if (has(key) && !isNull(key)) optString(key, fallback.orEmpty()).takeIf { it.isNotEmpty() } else null
}

/**
 * 清单的「已取出的」中间形态 —— 与 [SkinManifestParser.parseMap] 的输入契约。
 *
 * 单独建模的价值：测试可以直接构造它，不需要碰 JSON；而 [SkinManifestParser.parseJson]
 * 只负责填充这个结构。字段为 `null` 统一表示「缺失或类型非法」。
 */
data class ManifestRaw(
    val packId: String?,
    val schemaVersion: Int?,
    val minAppVersion: Int?,
    val displayName: String?,
    val signature: String?,
    val fontFileName: String?,
    val iconStrokes: Map<String, Float>?,
    val entries: List<SkinPackEntry>?,
)
