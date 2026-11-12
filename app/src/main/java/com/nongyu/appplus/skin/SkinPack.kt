package com.nongyu.appplus.skin

import java.security.MessageDigest

/**
 * 皮肤包的**清单契约与完整性校验**（票 #38 / ADR-0003 + ADR-0005）。
 *
 * ## 为什么这里一条 Android API 都不碰
 *
 * 本票的验收里有一条「API 30+ 与 26–29 两条路径，同一输入产物等价（契约测试）」。
 * 只要校验/装配逻辑里出现 `Resources` / `Typeface` / `BitmapFactory`，这条测试就只能
 * 退化成 instrumented 测试 —— 而规格明确要求**纯 JVM 单测**。
 *
 * 所以本文件只做三件与平台无关的事：
 * 1. 描述清单长什么样（[SkinPackManifest]）；
 * 2. 逐文件 `sha256` + 整包签名校验（[SkinPackIntegrity]）；
 * 3. 把「清单 + 校验结论 + 平台能否取到字体」装配成 [LoadedResources]（[SkinPackPlanner]）。
 *
 * 真正"取"字节的两条路径分别在同包的 `SkinPackFileSource`（API 26–29）与
 * `SkinPackResourcesSource`（API 30+）里，它们**只负责取**，不做任何判定 ——
 * 这样两条路径的产物等价性是**结构上**成立的，而不是靠两边各写一遍相同的判断去凑。
 */

/** 一个皮肤包条目的身份：规范化相对路径 + 它的 sha256（小写十六进制）。 */
data class SkinPackEntry(
    val path: String,
    val sha256: String,
)

/**
 * 皮肤包清单（ADR-0003 的 6 项必填 + 本票用到的资源段）。
 *
 * 只建模本期**客户端真的会读**的字段。ADR-0003 里「结构预留、值为空」的那些
 * （`cover` / `author` / `description` / 投放条件 / A/B / 增量包 / 回滚标记）
 * 刻意不建模 —— 同主版本未知字段本来就该被忽略（第②层降级），
 * 建模等于给它们一个客户端根本不会用的位置。
 *
 * @property packId 包标识（必填）。
 * @property schemaVersion 格式版本；**主版本**高于客户端支持即整包拒绝。
 * @property minAppVersion 最低 App versionCode；由客户端判定（ADR-0003：服务端不在场）。
 * @property displayName 展示名（必填，本期只用于日志）。
 * @property iconStrokes 图标名 → 线宽系数；缺失项由渲染层逐资源回落。
 * @property fontFileName 包内字体路径（相对包根）；`null` = 系统默认字体。
 * @property entries 完整性块里声明过的全部文件。
 * @property signature 整包签名（对 [entries] 的规范化摘要签名）。空 = 未签名 → 拒绝。
 * @property warnings 解析期的非阻断问题（例如「旧扁平格式，建议迁移」）。
 *   带默认值，既有构造处无需改动；由 [SkinPackPlanner.plan] 并入 [LoadedResources.warnings]。
 */
data class SkinPackManifest(
    val packId: String,
    val schemaVersion: Int,
    val minAppVersion: Int,
    val displayName: String,
    val iconStrokes: Map<NavIconKey, Float>,
    val fontFileName: String?,
    val entries: List<SkinPackEntry>,
    val signature: String,
    val warnings: List<String> = emptyList(),
) {

    companion object {
        /**
         * 客户端支持的**皮肤包**格式主版本（ADR-0003 第①层：主版本过高整包拒绝）。
         *
         * 这是皮肤包 `schemaVersion` 的**单一真源**：
         * [SkinManifestParser] 与 [SkinPackIntegrity] 都引用它，不再各写一份字面量 1。
         *
         * 注意与 [ColorPayloadParser.COLOR_SEGMENT_SCHEMA_MAJOR] 的区别：后者是**色值段**
         * （ADR-0004 数据库目录接口）的格式主版本，与皮肤包是两套独立演进的载荷，
         * 语义不同、不能合并成同一个常量（见票 #57 ④）。
         */
        const val SUPPORTED_SCHEMA_MAJOR: Int = 1
    }
}

/** 一次校验的输入：清单 + **实际拿到的**每个文件的字节。 */
data class SkinPackVerifyInput(
    val manifest: SkinPackManifest,
    /** 路径 → 实际字节。用于逐文件 sha256；缺失即视为该文件校验失败。 */
    val actualBytes: Map<String, ByteArray>,
    /** 当前 App 的 versionCode（ADR-0003：判定权在客户端）。 */
    val appVersionCode: Int,
) {
    // ByteArray 在 data class 里只比较引用，这里显式给出内容比较，避免测试里出现"假不等"。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkinPackVerifyInput) return false
        if (manifest != other.manifest) return false
        if (appVersionCode != other.appVersionCode) return false
        if (actualBytes.size != other.actualBytes.size) return false
        return actualBytes.all { (k, v) -> other.actualBytes[k]?.contentEquals(v) == true }
    }

    override fun hashCode(): Int {
        var result = manifest.hashCode()
        result = 31 * result + appVersionCode
        for ((k, v) in actualBytes) {
            result = 31 * result + k.hashCode()
            result = 31 * result + v.contentHashCode()
        }
        return result
    }
}

/** 整包签名校验器。抽成接口，是为了让单测注入替身而不用真的生成密钥对。 */
fun interface PackSignatureVerifier {
    /**
     * @param signedPayload 规范化摘要（见 [SkinPackIntegrity.canonicalPayload]）。
     * @param signatureBase64 清单里的 `integrity.signature`。
     * @return 是否通过。实现**不得抛异常**（异常语义对调用方无用，只有"过/不过"有意义）。
     */
    fun verify(signedPayload: String, signatureBase64: String): Boolean
}

/** 校验结论：要么通过，要么带 [PayloadOrigin] 与原因 —— 调用方据此决定降级层级。 */
data class PackVerification(
    val ok: Boolean,
    val origin: PayloadOrigin,
    val reason: String? = null,
    /** 非阻断问题（例如某文件不在白名单里但已被丢弃）。 */
    val warnings: List<String> = emptyList(),
)

object SkinPackIntegrity {

    // 皮肤包格式主版本的真源收敛到 SkinPackManifest.SUPPORTED_SCHEMA_MAJOR（票 #57 ④）。
    // 这里不再自带一份字面量，避免三处常量漂移。

    /**
     * 允许出现在包内的扩展名**白名单**。
     *
     * ADR-0005 的硬约束是「禁止包内 DEX / so / 可执行文件与**未知扩展名**」。
     * 用白名单而不是黑名单：黑名单永远漏（`sh`、`bat`、`dylib`、无扩展名…），
     * 而皮肤包需要的文件类型是**封闭且已知**的，白名单能把"没想到的类型"一并挡掉。
     */
    val ALLOWED_EXTENSIONS: Set<String> = setOf(
        "json",           // 清单与参数表
        "ttf", "otf", "ttc", // 字体（ADR-0005：本期做字体）
        "png", "webp", "jpg", "jpeg", // 位图（图标/封面预留）
    )

    /** 明确判死的可执行扩展名。白名单已覆盖，这里单独列出是为了给出**可读的拒绝原因**。 */
    val FORBIDDEN_EXECUTABLE_EXTENSIONS: Set<String> =
        setOf("dex", "jar", "so", "apk", "aab", "class", "odex", "oat", "vdex", "dylib", "sh", "bat", "exe")

    /**
     * 整包校验。顺序即 ADR-0003 的三层降级（从粗到细）：
     * 1. 格式主版本高于客户端 → 整包拒绝（`SchemaTooNew`）。
     * 2. 最低 App 版本高于当前 → 整包拒绝（`AppVersionTooLow`）。
     * 3. 出现了禁用的可执行文件 / 未知扩展名 → 整包拒绝（`Invalid`）。
     *    **安全违规不做逐项回落**：能往包里塞 dex 的包，其内容一律不可信。
     * 4. 逐文件 sha256 与清单不符 → 整包拒绝（`IntegrityFailed`）。
     * 5. 整包签名不过 → 整包拒绝（`IntegrityFailed`）。
     *
     * 通过时才返回 `ok = true`；任何一步失败都**不抛异常**（ADR-0006 Q3=B）。
     */
    fun verify(input: SkinPackVerifyInput, verifier: PackSignatureVerifier): PackVerification {
        val manifest = input.manifest

        // ① 格式主版本过高 → 整包拒绝（真源：SkinPackManifest.SUPPORTED_SCHEMA_MAJOR）。
        if (manifest.schemaVersion > SkinPackManifest.SUPPORTED_SCHEMA_MAJOR) {
            return PackVerification(
                ok = false,
                origin = PayloadOrigin.SchemaTooNew,
                reason = "皮肤包格式主版本 ${manifest.schemaVersion} 高于客户端支持的 ${SkinPackManifest.SUPPORTED_SCHEMA_MAJOR}",
            )
        }

        // ② 最低 App 版本过高 → 整包拒绝，且原因必须与①可区分。
        if (input.appVersionCode < manifest.minAppVersion) {
            return PackVerification(
                ok = false,
                origin = PayloadOrigin.AppVersionTooLow,
                reason = "皮肤包要求 App versionCode ≥ ${manifest.minAppVersion}，当前 ${input.appVersionCode}",
            )
        }

        // ③ 安全白名单：任何一项不合法就整包拒绝。
        for (entry in manifest.entries) {
            val problem = checkPathAllowed(entry.path)
            if (problem != null) {
                return PackVerification(
                    ok = false,
                    origin = PayloadOrigin.Invalid,
                    reason = problem,
                )
            }
        }
        // 清单里没声明、但实际躺在包里/被传进来的文件同样要挡（防止绕过声明的夹带）。
        for (path in input.actualBytes.keys) {
            val problem = checkPathAllowed(path)
            if (problem != null) {
                return PackVerification(
                    ok = false,
                    origin = PayloadOrigin.Invalid,
                    reason = "包内存在未声明的非法文件：$problem",
                )
            }
        }

        // ④ 逐文件 sha256（内容比对用大小写不敏感的十六进制）。
        for (entry in manifest.entries) {
            val bytes = input.actualBytes[entry.path]
                ?: return PackVerification(
                    ok = false,
                    origin = PayloadOrigin.IntegrityFailed,
                    reason = "清单声明的文件缺失：${entry.path}",
                )
            val actual = sha256Hex(bytes)
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                return PackVerification(
                    ok = false,
                    origin = PayloadOrigin.IntegrityFailed,
                    reason = "文件 sha256 不符：${entry.path}（清单 ${entry.sha256}，实际 $actual）",
                )
            }
        }

        // ⑤ 整包签名。空签名直接拒绝：ADR-0003 把完整性定为应用层责任，缺签名等于没校验。
        if (manifest.signature.isBlank()) {
            return PackVerification(
                ok = false,
                origin = PayloadOrigin.IntegrityFailed,
                reason = "皮肤包缺少整包签名",
            )
        }
        val payload = canonicalPayload(manifest.entries)
        if (!verifier.verify(payload, manifest.signature)) {
            return PackVerification(
                ok = false,
                origin = PayloadOrigin.IntegrityFailed,
                reason = "皮肤包整包签名校验失败",
            )
        }

        return PackVerification(ok = true, origin = PayloadOrigin.Loaded)
    }

    /**
     * 被签名的规范化摘要：`path:sha256` 按路径**字典序**排序后以 `\n` 连接。
     *
     * 必须有确定顺序，否则同一份包在不同设备上（文件枚举顺序不同）算出的摘要不同，
     * 签名会随机失败。这是签名方案里最容易踩的一条。
     */
    fun canonicalPayload(entries: List<SkinPackEntry>): String =
        entries.sortedBy { it.path }
            .joinToString("\n") { "${it.path}:${it.sha256.lowercase()}" }

    /** 逐文件 sha256（小写十六进制）。`MessageDigest` 是 JDK/Android 共有 API，纯 JVM 可测。 */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 路径白名单检查。返回 `null` = 合法，否则返回人类可读的拒绝原因。
     *
     * 除了扩展名，还挡两类经典问题：
     * - **绝对路径 / 目录穿越**（`/etc/x`、`../x`）：解包时会把文件写到包外。
     * - **无扩展名**：`ALLOWED_EXTENSIONS` 天然不含它，但仍单独给出原因，便于日志排查。
     */
    fun checkPathAllowed(path: String): String? {
        if (path.isBlank()) return "包内存在空路径条目"
        if (path.startsWith("/") || path.startsWith("\\")) return "包内存在绝对路径条目：$path"
        if (path.contains("..")) return "包内条目试图目录穿越：$path"
        if (path.endsWith("/")) return "包内条目是目录而不是文件：$path"

        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return "包内条目没有扩展名：$path"
        val ext = name.substring(dot + 1).lowercase()

        if (ext in FORBIDDEN_EXECUTABLE_EXTENSIONS) {
            return "皮肤包禁止包含可执行文件：$path（.${ext}）"
        }
        if (ext !in ALLOWED_EXTENSIONS) {
            return "皮肤包包含未知扩展名：$path（.${ext}）"
        }
        return null
    }
}

/**
 * **加载两条路径的共同产物**（契约测试的断言对象）。
 *
 * 关键设计：这是**纯数据**，不含 `Typeface` / `Bitmap` / 任何 Android 对象。
 * 两条路径（`ResourcesLoader` 与文件解码）都产出它，因此"产物等价"可以直接
 * 用 `equals` 断言 —— 如果这里换成平台对象，等价性就只能靠人工肉眼比。
 *
 * @property iconStrokes 已通过校验的图标线宽（只含**合法**值；非法项已剔除，留给渲染层回落）。
 * @property fontFileName 可用的包内字体路径；`null` = 两条路径都取不到 → 系统默认字体。
 * @property meta 元信息，供日志区分「用上了」还是「回落了」。
 * @property warnings 非阻断问题（逐资源回落的细节）。
 */
data class LoadedResources(
    val iconStrokes: Map<NavIconKey, Float>,
    val fontFileName: String?,
    val meta: PayloadMeta,
    val warnings: List<String> = emptyList(),
) {
    /** 组装成皮肤数据源要的资源段。字体取不到时是 [SkinFont.SystemDefault]，两条路径一致。 */
    fun toPayload(): ResourcePayload = ResourcePayload(
        iconStrokes = iconStrokes,
        font = fontFileName?.let { SkinFont.FromPack(it) } ?: SkinFont.SystemDefault,
        meta = meta,
    )

    companion object {
        /** 全回落：没有任何可用资源段（无包 / 校验失败 / 未配置）。 */
        fun fallback(meta: PayloadMeta) = LoadedResources(
            iconStrokes = emptyMap(),
            fontFileName = null,
            meta = meta,
        )
    }
}

/**
 * **路径无关的产物构建**（本票契约测试直接测这个对象）。
 *
 * 两条加载路径的差别只在"怎么把字节取到手"和"字体文件能不能真的读成 `Typeface`"；
 * 该差别被压成一个布尔 [fontReadable]。除此之外的一切判定都收敛到这里，
 * 于是「同一输入 → 同一个 [LoadedResources]」在结构上成立，而不是靠两边对齐。
 */
object SkinPackPlanner {

    /**
     * 把校验结论 + 清单装配成共同产物。
     *
     * @param manifest 清单；`null` = 没有包。
     * @param verification 校验结论；`ok = false` 时全部回落。
     * @param fontReadable 平台侧是否真的能把这个字体文件读成可用字体。
     *   两条路径各自探测后传入（`Resources.getFont` 拿到非空 / `Typeface.createFromFile` 不抛异常），
     *   这里只消费结论，因此行为对两条路径**完全相同**。
     * @param builtIn 内置默认资源段，用于逐资源回落的第二级。
     */
    fun plan(
        manifest: SkinPackManifest?,
        verification: PackVerification,
        fontReadable: Boolean,
        builtIn: ResourcePayload,
    ): LoadedResources {
        if (manifest == null) {
            return LoadedResources.fallback(
                PayloadMeta.fallback(PayloadOrigin.Absent, "无皮肤包"),
            )
        }
        if (!verification.ok) {
            return LoadedResources.fallback(
                PayloadMeta.fallback(
                    verification.origin,
                    verification.reason ?: "皮肤包校验未通过",
                ),
            )
        }

        // 解析期的非阻断问题（票 #57 ①：旧扁平清单的迁移提示）与校验期 warning 一并带出，
        // 调用方（FileSkinSource）会把它们逐条写进日志。
        val warnings = (manifest.warnings + verification.warnings).toMutableList()

        // 逐资源回落：只保留**合法**的线宽系数；不合法/缺失的项不进表，
        // 由渲染层（NavIconStrokes）回落到内置默认 —— 这就是 ADR-0005 Q5=A「只该项受影响」。
        val accepted = LinkedHashMap<NavIconKey, Float>()
        for ((key, raw) in manifest.iconStrokes) {
            val clean = sanitizeStroke(raw)
            if (clean == null) {
                warnings += "图标 $key 的线宽系数 $raw 非法，已回落内置默认"
            } else {
                accepted[key] = clean
            }
        }

        // 字体：清单声明了 + 平台能读 → 用它；否则系统默认。两条路径走的是同一个判断。
        val fontFileName = manifest.fontFileName?.takeIf { it.isNotBlank() }
        val useFont = if (fontFileName != null && fontReadable) {
            fontFileName
        } else {
            if (fontFileName != null) {
                warnings += "包内字体 $fontFileName 无法加载，已回落系统默认字体"
            }
            null
        }

        return LoadedResources(
            iconStrokes = accepted,
            fontFileName = useFont,
            meta = PayloadMeta.loaded(version = manifest.schemaVersion.toString()),
            warnings = warnings,
        )
    }

    /**
     * 线宽系数合法性判据的**唯一真源**（票 #57 ④）。
     *
     * `skin/` 是数据层、`ui/icon/` 是渲染层，方向应当是渲染层依赖数据层：
     * [com.nongyu.appplus.icon.NavIconStrokes.sanitize] 直接调用本函数，
     * 不再自带一份阈值常量，避免两处 0f / 1f 漂移。
     * 判据：有限（排除 NaN / Infinity）、严格落在 `(0, 1]` 内。
     */
    fun sanitizeStroke(value: Float): Float? {
        if (!value.isFinite()) return null
        if (value <= 0f) return null
        if (value > 1f) return null
        return value
    }
}
