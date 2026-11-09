package com.nongyu.appplus.skin

import org.json.JSONObject

/**
 * 皮肤包清单（`manifest.json`）的解析层（票 #40 / 票 #57 ①，ADR-0003）。
 *
 * ## 分层方式与 [ColorPayloadParser] 一致，理由也一样
 *
 * `org.json` 在纯 JVM 单测（`:app:testDebugUnitTest`）里是 Android **stub**，
 * 一调用就抛 `RuntimeException: Method ... not mocked`（已由仓库里的探针实测确认）。
 * 所以：
 * - **判定逻辑**放在 [SkinManifestParser.parseMap] —— 只接收 `Map`/基础类型，可全量单测；
 * - **本文件下半部分**只做「把 JSON 取成 [ManifestRaw]」的类型转换，几乎无分支。
 *
 * ## 两种清单格式（ADR-0003 第 17–18 行）
 *
 * ```
 * // 新结构（ADR 要求）
 * { "packId":…, "schemaVersion":…, "minAppVersion":…, "displayName":…,
 *   "payload":   { "iconStrokes": {…}, "fontFileName": "…" },
 *   "integrity": { "entries": [ { "path":…, "sha256":… } ], "signature": "…" } }
 *
 * // 旧扁平结构（向后兼容，命中时记 warning）
 * { "packId":…, "schemaVersion":…, "minAppVersion":…, "displayName":…,
 *   "signature": "…", "iconStrokes": {…}, "fontFileName": "…", "entries": [ … ] }
 * ```
 *
 * **两种格式不能混用**：同一份 JSON 里既有 `payload` 又有顶层 `entries` → 判为非法。
 * 格式识别（[ManifestFormat]）在薄适配层按顶层键存在性判定；「混用判非法 / 旧格式记
 * warning / 产出 manifest」这些决策全部在 [parseMap]，因此纯 JVM 单测可直接构造
 * [ManifestRaw] 覆盖所有分支，不必碰 `org.json`。
 *
 * ## 解析失败必须返回 `null` 而不是抛异常
 *
 * 清单是外部数据（B 端上传）。任何一个字段缺失、类型不对、字节是乱码，
 * 都只能导致「这套包不可用」，**不能导致 App 打不开**（ADR-0006 优先级不对称）。
 */
object SkinManifestParser {

    /** 旧扁平格式命中时记入 [SkinPackManifest.warnings] 的迁移提示（票 #57 ①）。 */
    const val LEGACY_FLAT_WARNING = "清单为旧扁平格式，建议迁移"

    // 皮肤包格式主版本的真源收敛到 SkinPackManifest（票 #57 ④），此处不再自带一份常量。

    /**
     * 从「已取出的键值对」构造清单。**纯函数，可全量单测。**
     *
     * @param raw 顶层字段 + 已展开的 entries/iconStrokes + 格式标记。
     * @return 清单；`null` = 必填项缺失 / 类型非法 / 新旧格式混用。
     */
    fun parseMap(raw: ManifestRaw): SkinPackManifest? {
        // 混用检测属于判定逻辑，必须留在这里（可纯 JVM 测），不能塞进 parseJson。
        // 「既有 payload 又有顶层 entries」= 到底以哪个为准的歧义，一律整包拒绝。
        if (raw.format == ManifestFormat.INVALID_MIXED) return null

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

        // 旧扁平格式命中 → 记一条迁移 warning（票 #57 ①，随 LoadedResources.warnings 进日志）。
        val warnings = if (raw.format == ManifestFormat.FLAT) {
            listOf(LEGACY_FLAT_WARNING)
        } else {
            emptyList()
        }

        return SkinPackManifest(
            packId = packId,
            schemaVersion = schema,
            minAppVersion = minApp,
            displayName = displayName,
            iconStrokes = strokes,
            fontFileName = font,
            entries = entries,
            signature = signature,
            warnings = warnings,
        )
    }

    /**
     * 从 JSON 文本解析清单。**这是唯一允许碰 `org.json` 的地方。**
     *
     * 只做三件薄事：① 按顶层键存在性判 [ManifestFormat]；② 从 `payload` / `integrity`
     * 子对象（或旧格式顶层）取值；③ 塞进 [ManifestRaw] 交给 [parseMap]。
     * **没有任何「该不该接受」的判定分支** —— 全在 parseMap。
     *
     * @return 清单；`null` = 文本不是合法 JSON，或必填项不满足 / 格式混用。
     */
    fun parseJson(manifestJson: String): SkinPackManifest? = try {
        val obj = JSONObject(manifestJson)

        // 结构识别：顶层出现 payload / integrity 即视作新格式。
        val hasPayload = obj.has("payload")
        val hasIntegrity = obj.has("integrity")
        // 旧格式的关键顶层字段（用于混用判定）。
        val hasFlatEntries = obj.has("entries")
        val hasFlatSignature = obj.has("signature")
        val hasFlatIconStrokes = obj.has("iconStrokes")

        val format = when {
            // 新旧混用：新结构的载荷段与旧结构的完整性/签名字段同时出现 → 非法。
            (hasPayload || hasIntegrity) && (hasFlatEntries || hasFlatSignature || hasFlatIconStrokes) ->
                ManifestFormat.INVALID_MIXED
            hasPayload || hasIntegrity -> ManifestFormat.NEW
            else -> ManifestFormat.FLAT
        }

        // 载荷段与完整性块的容器：新格式取子对象，旧格式就是顶层。
        val payload = if (hasPayload) obj.optJSONObject("payload") else obj
        val integrity = if (hasIntegrity) obj.optJSONObject("integrity") else obj

        parseMap(
            ManifestRaw(
                packId = obj.optStringOrNull("packId"),
                schemaVersion = obj.optIntOrNull("schemaVersion"),
                minAppVersion = obj.optIntOrNull("minAppVersion"),
                displayName = obj.optStringOrNull("displayName"),
                signature = integrity?.optStringOrNull("signature"),
                fontFileName = payload?.optStringOrNull("fontFileName"),
                iconStrokes = payload?.optJSONObject("iconStrokes")?.let { o ->
                    buildMap {
                        for (key in o.keys()) {
                            // 只接受数值；非数值项在 parseMap 里本就无法 sanitize，这里不抛。
                            val v = o.optDouble(key, Double.NaN)
                            if (!v.isNaN()) put(key, v.toFloat())
                        }
                    }
                },
                entries = integrity?.optJSONArray("entries")?.let { arr ->
                    buildList {
                        for (i in 0 until arr.length()) {
                            val e = arr.optJSONObject(i) ?: continue
                            val path = e.optStringOrNull("path") ?: continue
                            val sha = e.optStringOrNull("sha256") ?: continue
                            add(SkinPackEntry(path = path.trim(), sha256 = sha.trim().lowercase()))
                        }
                    }
                },
                format = format,
            ),
        )
    } catch (e: Exception) {
        // 乱码 JSON / 类型错乱一律视为「包不可用」，不抛给调用方。
        null
    }

    /** `optInt` 无法区分「缺失」与「0」，而 0 不是合法值 —— 这里显式区分。 */
    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

    /**
     * `org.json` 的成员方法 `optString(key, fallback)` 第二参是非空 `String`，
     * 传 `null` 会与 Kotlin 的 `Nothing?` 推断冲突。这里换个名字避开重载歧义，
     * 语义是「缺失 / 空串都返回 null，以便与合法空值区分」。
     */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key, "").takeIf { it.isNotEmpty() } else null
}

/** 清单的结构形态（ADR-0003 对齐用）。格式识别在薄适配层，决策在 [SkinManifestParser.parseMap]。 */
enum class ManifestFormat {
    /** 新结构：`payload{…}` + `integrity{…}`（ADR 要求）。 */
    NEW,

    /** 旧扁平结构：字段全在顶层。向后兼容，但记一条迁移 warning。 */
    FLAT,

    /** 新旧混用：既有 `payload` 又有顶层 `entries` 等。整包拒绝。 */
    INVALID_MIXED,
}

/**
 * 清单的「已取出的」中间形态 —— 与 [SkinManifestParser.parseMap] 的输入契约。
 *
 * 单独建模的价值：测试可以直接构造它，不需要碰 JSON；而 [SkinManifestParser.parseJson]
 * 只负责填充这个结构。字段为 `null` 统一表示「缺失或类型非法」。
 *
 * [format] 带默认值 [ManifestFormat.FLAT]：既有测试直接构造 [ManifestRaw] 时代表旧扁平格式，
 * 不改坏那批用例；新格式与混用由 parseJson 或新测试显式传入。
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
    val format: ManifestFormat = ManifestFormat.FLAT,
)
