package com.nongyu.appplus.net.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 回传缓存子域的 DTO（规格 §3.6）。
 *
 * ## ⚠️ GET 端点有两条不同的 200 形状
 * 后端 `cacheGetHandler`：
 * - **命中** → `200` + [CacheEnvelope]
 * - **未命中** → `200` + [CacheMissDto]（`{"code":"cache_miss", ...}`，**不是 404**）
 *
 * 所以「2xx 就取 body」这条常规规则在这里不成立。实现上统一声明成
 * [CacheGetResponse]（字段全可空的并集），再用 [CacheGetResponse.discriminate]
 * 判别，避免调用点各自猜。
 */

/**
 * GET `/school/cache/:dataType` 的**统一响应容器**：命中与未命中字段的并集。
 *
 * 字段全部可空，因为任一形状都只填其中一部分。
 * 不要给任何一个字段写非空类型 —— 后端 `success` 用 `omitempty`、`payload`
 * 可能为 `null`，缺字段是常态。
 *
 * @param payload 命中时的业务数据，**原始 JSON 透传**（数组 / 对象 / null 三种形态都合法）。
 *   用 [JsonElement] 承接而不是 `String`：后端给的是 JSON 值，不是被字符串化的 JSON 文本。
 * @param code 未命中/错误时才有值（如 `cache_miss`）。**它是判别依据**。
 */
@Serializable
data class CacheGetResponse(
    val payload: JsonElement? = null,
    val success: Boolean? = null,
    val schemaVersion: Int? = null,
    val fetchedAt: String? = null,
    val stale: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val address: String? = null,
) {
    /** 判别这次 200 到底是命中还是未命中/错误。**只读 [code]，不猜其它字段。 */
    fun discriminate(): CacheResult = when {
        !code.isNullOrEmpty() -> CacheResult.Miss(
            code = code,
            message = message,
            address = address,
        )

        else -> CacheResult.Hit(
            envelope = CacheEnvelope(
                payload = payload,
                success = success,
                schemaVersion = schemaVersion,
                fetchedAt = fetchedAt,
                stale = stale,
            ),
        )
    }
}

/** 判别结果。让调用方用一个 `when` 就能覆盖两种形状，不必自己判空。 */
sealed class CacheResult {
    /** 缓存命中。 */
    data class Hit(val envelope: CacheEnvelope) : CacheResult()

    /**
     * 未命中或子域错误（后端把这些都放在 200 里）。
     *
     * @param code 如 `cache_miss`；其它值按 [com.nongyu.appplus.net.core.ApiErrorCode] 归类。
     */
    data class Miss(
        val code: String,
        val message: String?,
        val address: String?,
    ) : CacheResult() {

        /** 是否「没有可用缓存」这一正常流程分支。 */
        fun isCacheMiss(): Boolean = code == com.nongyu.appplus.net.core.ApiErrorCode.CACHE_MISS
    }
}

/**
 * 缓存命中时的信封（对应后端 `cacheEnvelope`）。
 *
 * - `payload`：`json.RawMessage` 原样透出，**客户端不解析内部字段**。
 * - `success`：后端用 `omitempty`，缺字段时即 `null`。
 * - `schemaVersion`：回传体自带的 schema 版本。
 * - `fetchedAt`：**服务端盖章**的 UTC RFC3339 字符串（不采信客户端时间）。
 * - `stale`：只有 `fallback=1` 时可能为 true（客户端上报直连失败后要旧数据）。
 */
@Serializable
data class CacheEnvelope(
    val payload: JsonElement? = null,
    val success: Boolean? = null,
    val schemaVersion: Int? = null,
    val fetchedAt: String? = null,
    val stale: Boolean? = null,
)

/**
 * POST `/school/cache/:dataType` 的成功响应。
 *
 * 后端 `cacheSubmitHandler` 回的也是 `envelopeOf(snap)`，形状与 [CacheEnvelope] 相同，
 * 这里用 typealias 表达「同一形状」，避免复制一份 DTO 造成两处真源。
 */
typealias CacheSubmitResponse = CacheEnvelope

/** DELETE `/school/cache/:dataType` 的成功响应：`{"invalidated":true}`。 */
@Serializable
data class CacheInvalidateDto(
    val invalidated: Boolean? = null,
)
