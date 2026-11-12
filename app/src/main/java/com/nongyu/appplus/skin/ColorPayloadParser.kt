package com.nongyu.appplus.skin

import androidx.compose.ui.graphics.Color
import com.nongyu.appplus.design.NongyuColors

/**
 * 色值段解析的**纯逻辑层**（票 #37 / 规格 #33）。
 *
 * ## 为什么把逻辑和 JSON 库拆开（重要）
 *
 * ADR-0004 定了用平台自带 `org.json`（不引 `kotlinx-serialization`，因为它的 `.module`
 * 要求 Kotlin ≥ 2.3.21，本仓 2.3.0）。但实测发现一个坑：
 *
 * > **`org.json` 在 `:app:testDebugUnitTest` 这种纯 JVM 单测里是 stub**，
 * > 一旦调用就抛 `RuntimeException: Method put in org.json.JSONObject not mocked`。
 *
 * 如果把「颜色串怎么解析、核心 token 缺了怎么办、版本过高怎么拒」这些**决策逻辑**
 * 写在 `JSONObject` 的调用链里，它们就全部无法被测试 —— 而那恰恰是最容易出错的部分。
 *
 * 所以分成两层：
 * - **本文件**：只接收「已经取出来的字符串」，不含任何 JSON API → 可在纯 JVM 单测里全量覆盖。
 * - **`ColorPayloadJson`**：薄适配层，只负责把 `JSONObject` 里的值取成字符串/Z 然后交给这里。
 *   它本身几乎无逻辑，因此不需要单测去覆盖（逻辑都在下面）。
 *
 * 这也符合规格「测试只断言外部行为」的要求：外部行为 = 给定一段键值数据，解析出什么色板。
 */

/** 核心 token：ADR-0004 规定这 5 项必填，缺一即整段回落。 */
val CORE_TOKEN_KEYS: List<String> = listOf(
    "colorBrand",
    "colorBackground",
    "colorCard",
    "colorText",
    "colorIndicator",
)

/** 全部 16 个 token 名（4 组 16 字段，与 `NongyuColors` 结构一一对应）。 */
val ALL_TOKEN_KEYS: List<String> = listOf(
    // BrandColors
    "colorBrand", "colorBrandMuted", "colorOnBrand",
    // SurfaceColors
    "colorBackground", "colorCard", "colorText", "colorTextSecondary", "colorOutline",
    // AiOrbColors
    "colorCore", "colorNebulaGreen", "colorNebulaYellow", "colorRimShade", "colorGlow",
    // NavBarColors
    "colorContainer", "colorItem", "colorIndicator",
)

/**
 * 一次色值段解析的输入：**已经从 JSON 里取出来的原始字符串**。
 *
 * @property schemaMajor 载荷格式主版本；`null` = 字段缺失（按「太旧/非法」处理，不按「太新」）。
 * @property minAppVersionCode 载荷声明的最低 App versionCode；`null` = 未声明（不拦截）。
 * @property appVersionCode 当前 App 的 versionCode（用于客户端侧判定，ADR-0003 Q3b）。
 * @property rawTokens token 名 → 原始色值串。可能包含未知键（要忽略并记日志）。
 */
data class ColorPayloadInput(
    val schemaMajor: Int?,
    val minAppVersionCode: Int?,
    val appVersionCode: Int,
    val rawTokens: Map<String, String>,
)

/** 解析结果：要么拿到一份色板，要么说明为什么回落。 */
data class ColorParseResult(
    val palette: NongyuColors?,
    val meta: PayloadMeta,
    /** 需要记录的问题（未知 token、被忽略的值等）。空 = 没有任何异常。 */
    val warnings: List<String> = emptyList(),
)

object ColorPayloadParser {

    /**
     * 客户端支持的**色值段**格式主版本。更高 = 整段拒绝（ADR-0003 第①层）。
     *
     * 与 [SkinPackManifest.SUPPORTED_SCHEMA_MAJOR]（皮肤包格式主版本）是**两套独立演进的
     * 载荷版本**：色值段走 ADR-0004 数据库目录接口，皮肤包走资源载荷。刻意分开命名，
     * 不合并成同一个常量（票 #57 ④：格式版本与最低 App 版本必须分开，两载体各自版本亦然）。
     */
    const val COLOR_SEGMENT_SCHEMA_MAJOR: Int = 1

    /**
     * 解析色值段。**纯函数，不碰 JSON API、不碰 Android、可全量单测。**
     *
     * 判定顺序即 ADR-0003 的三层降级，从粗到细：
     * 1. 格式主版本过高 → 整段拒绝（`SchemaTooNew`）。
     * 2. 载荷要求的最低 App 版本高于当前 → 整段拒绝（`AppVersionTooLow`）。
     * 3. 核心 5 项有缺/不可解析 → 整段回落（`Invalid`），**不是逐项**。
     * 4. 非核心项缺失/不可解析 → 逐项回落 `builtIn`，其余照用，并记 warning。
     * 5. 未知 token 名 → 忽略 + 记 warning（同主版本未知字段忽略，第②层）。
     *
     * @param builtIn 回落基准；本函数永远不会返回 `null` 色板（失败时返回 `builtIn`）。
     */
    fun parse(
        input: ColorPayloadInput,
        builtIn: NongyuColors,
    ): ColorParseResult {
        // ① 格式主版本过高 → 整段拒绝。
        val schemaMajor = input.schemaMajor
        if (schemaMajor != null && schemaMajor > COLOR_SEGMENT_SCHEMA_MAJOR) {
            return fail(
                PayloadOrigin.SchemaTooNew,
                "色值段格式主版本 $schemaMajor 高于客户端支持的 $COLOR_SEGMENT_SCHEMA_MAJOR",
            )
        }

        // ①b 声明了最低 App 版本且当前不够 → 拒绝，且原因要与上面可区分（ADR-0003 要求分开）。
        val minApp = input.minAppVersionCode
        if (minApp != null && input.appVersionCode < minApp) {
            return fail(
                PayloadOrigin.AppVersionTooLow,
                "色值段要求 App versionCode ≥ $minApp，当前 ${input.appVersionCode}",
            )
        }

        // ①c 格式主版本缺失 = 载荷不合法（无法判断兼容性），按 Invalid 整段回落。
        if (schemaMajor == null) {
            return fail(PayloadOrigin.Invalid, "色值段缺少 schemaMajor，无法判断兼容性")
        }

        // ② 收集未知 token（忽略 + 记 warning，不阻断）。
        val warnings = mutableListOf<String>()
        val known = ALL_TOKEN_KEYS.toSet()
        input.rawTokens.keys.filterNot { it in known }.forEach {
            warnings += "忽略未知 token: $it"
        }

        // ③ 核心 5 项：任何一项缺失或不可解析 → 整段回落（**不是逐项**）。
        val missingCore = CORE_TOKEN_KEYS.filter { key ->
            val raw = input.rawTokens[key]
            raw == null || parseColor(raw) == null
        }
        if (missingCore.isNotEmpty()) {
            return fail(
                PayloadOrigin.Invalid,
                "核心 token 缺失或无法解析: ${missingCore.joinToString(",")}",
            )
        }

        // ④ 逐项装配：非核心缺失/坏值 → 该项用 builtIn，其余照用。
        fun pick(key: String, fallback: Color): Color {
            val raw = input.rawTokens[key] ?: return fallback
            val parsed = parseColor(raw)
            if (parsed == null) {
                warnings += "token $key 的色值无法解析（'$raw'），已回落内置默认"
                return fallback
            }
            return parsed
        }

        val palette = NongyuColors(
            brand = NongyuColors.BrandColors(
                colorBrand = pick("colorBrand", builtIn.brand.colorBrand),
                colorBrandMuted = pick("colorBrandMuted", builtIn.brand.colorBrandMuted),
                colorOnBrand = pick("colorOnBrand", builtIn.brand.colorOnBrand),
            ),
            surface = NongyuColors.SurfaceColors(
                colorBackground = pick("colorBackground", builtIn.surface.colorBackground),
                colorCard = pick("colorCard", builtIn.surface.colorCard),
                colorText = pick("colorText", builtIn.surface.colorText),
                colorTextSecondary = pick("colorTextSecondary", builtIn.surface.colorTextSecondary),
                colorOutline = pick("colorOutline", builtIn.surface.colorOutline),
            ),
            aiOrb = NongyuColors.AiOrbColors(
                colorCore = pick("colorCore", builtIn.aiOrb.colorCore),
                colorNebulaGreen = pick("colorNebulaGreen", builtIn.aiOrb.colorNebulaGreen),
                colorNebulaYellow = pick("colorNebulaYellow", builtIn.aiOrb.colorNebulaYellow),
                colorRimShade = pick("colorRimShade", builtIn.aiOrb.colorRimShade),
                colorGlow = pick("colorGlow", builtIn.aiOrb.colorGlow),
            ),
            navBar = NongyuColors.NavBarColors(
                colorContainer = pick("colorContainer", builtIn.navBar.colorContainer),
                colorItem = pick("colorItem", builtIn.navBar.colorItem),
                colorIndicator = pick("colorIndicator", builtIn.navBar.colorIndicator),
            ),
        )

        return ColorParseResult(
            palette = palette,
            meta = PayloadMeta.loaded(version = schemaMajor.toString()),
            warnings = warnings,
        )
    }

    private fun fail(origin: PayloadOrigin, reason: String): ColorParseResult =
        ColorParseResult(palette = null, meta = PayloadMeta.fallback(origin, reason))

    /**
     * 解析单个色值串。支持设计系统里常见的两种写法：
     * - `#RRGGBB` / `#AARRGGBB`（带或不带 `#`）
     * - `RRGGBB` / `AARRGGBB`（纯十六进制）
     *
     * 不可解析时返回 `null`（由调用方决定是整段回落还是逐项回落），**不抛异常**。
     */
    fun parseColor(raw: String): Color? {
        val s = raw.trim().removePrefix("#")
        if (s.isEmpty()) return null
        if (s.any { it.digitToIntOrNull(16) == null }) return null
        val value = s.toLongOrNull(16) ?: return null
        return when (s.length) {
            // #RRGGBB → 补成不透明
            6 -> Color(value or 0xFF000000L)
            // #AARRGGBB → 原样
            8 -> Color(value)
            else -> null
        }
    }
}
