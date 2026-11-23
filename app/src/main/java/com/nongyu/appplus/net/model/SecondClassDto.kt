package com.nongyu.appplus.net.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 二课（第二课堂）缓存与配方（后端 `internal/school/secondclass/`，2026-11-23 接入）。
 *
 * ## ⚠️ 与 jiaowu 域的关键差别：配方是**嵌套对象**
 * jiaowu 的 `cache_miss` 把地址/方法/Cookie **平铺**在响应顶层；
 * 二课把它收进 `recipe` 对象里：
 *
 * | | jiaowu | secondclass |
 * | --- | --- | --- |
 * | 未命中形状 | `{code, address, method, cookies, ...}` | `{code, message, recipe:{...}}` |
 *
 * 所以**不要复用** `CacheGetResponse` 那套判别逻辑，这里单独建模。
 *
 * ## 二课是「读三 + 写一」
 * - `GET /school/secondclass/cache/:dataType` —— 读副本
 * - `POST /school/secondclass/cache/:dataType` —— 客户端自愿回传（尽力而为）
 * - `DELETE /school/secondclass/cache/:dataType` —— 副本失效（60 秒节流）
 * - `GET /school/secondclass/recipe/:recipeId` —— **仅写类用**（写不经 GET，没有 miss 时机）
 */

/**
 * 二课缓存未命中响应（后端 `cacheMissResponse`）。
 *
 * `code` 为 `cache_miss` 时带 [recipe]；其它 code 属错误，[recipe] 为 null。
 */
@Serializable
data class SecondClassMissDto(
    val code: String? = null,
    val message: String? = null,
    /** 取数/交数配方。`cache_miss` 时必有，错误时无。 */
    val recipe: SecondClassRecipeDto? = null,
)

/**
 * 二课配方（后端 `Recipe`）。
 *
 * ## 字段语义
 * - `id`：配方标识
 * - `target`：目标系统标识
 * - `method` / `path` / `contentType`：请求形态
 * - `auth`：鉴权从哪取（见 [SecondClassRecipeAuthDto]）
 * - `params`：URL/表单参数，`origin` 说明值从哪来（`client_local` 客户端本地 / `fixed` 固定 / `optional` 可选）
 * - `fields`：要回传的数据字段
 * - `headers`：固定请求头
 * - `steps`：多步配方（`omitempty`）
 */
@Serializable
data class SecondClassRecipeDto(
    val id: String? = null,
    val target: String? = null,
    val method: String? = null,
    val path: String? = null,
    val contentType: String? = null,
    val auth: SecondClassRecipeAuthDto? = null,
    val params: List<SecondClassRecipeParamDto>? = null,
    val fields: List<SecondClassRecipeFieldDto>? = null,
    val headers: List<SecondClassRecipeHeaderDto>? = null,
    /** 多步配方；单步时后端不返回该字段。 */
    val steps: List<SecondClassRecipeStepDto>? = null,
)

/** 配方的鉴权来源（后端 `RecipeAuth`）。`in` 表示放在 header 还是 query。 */
@Serializable
data class SecondClassRecipeAuthDto(
    /** 放哪：`header` / `query` 等。 */
    val `in`: String? = null,
    /** 凭据的名字。 */
    val name: String? = null,
    /** 凭据从哪来。 */
    val from: String? = null,
)

/**
 * 配方参数（后端 `RecipeParam`）。
 *
 * `origin` 决定谁来提供这个值：
 * - `client_local`：客户端本地有（如学号）
 * - `fixed`：固定值，后端已给
 * - `optional`：可选
 */
@Serializable
data class SecondClassRecipeParamDto(
    val name: String? = null,
    val required: Boolean? = null,
    val origin: String? = null,
    /** 说明（`omitempty`）。 */
    val note: String? = null,
)

/** 要回传的数据字段（后端 `RecipeField`）。`kind` 是字段类型提示。 */
@Serializable
data class SecondClassRecipeFieldDto(
    val name: String? = null,
    val required: Boolean? = null,
    val kind: String? = null,
    val note: String? = null,
)

/** 固定请求头（后端 `RecipeHeader`）。 */
@Serializable
data class SecondClassRecipeHeaderDto(
    val name: String? = null,
    val value: String? = null,
)

/**
 * 多步配方的一步（后端 `RecipeStep`）。
 *
 * `params_from` 是「本步参数 ← 上一步结果的哪个字段」的映射。
 */
@Serializable
data class SecondClassRecipeStepDto(
    /** 引用某条配方。 */
    val ref: String? = null,
    /**
     * 参数来源映射（`omitempty`）。
     *
     * ⚠️ 后端字段名是 **`params_from`**（snake_case），必须显式映射；
     * 不写 `@SerialName` 会因 `ignoreUnknownKeys` 而静默拿到 null。
     */
    @SerialName("params_from")
    val paramsFrom: Map<String, String>? = null,
)

/**
 * 二课缓存命中/回传成功的信封（后端 `cacheEnvelope`）。
 *
 * 与 jiaowu 的 `CacheEnvelope` **不同**：这里没有 `success` 字段
 * （后端结构体里就没有），`payload` 仍是原样透传的 JSON。
 */
@Serializable
data class SecondClassEnvelopeDto(
    val payload: JsonElement? = null,
    val schemaVersion: Int? = null,
    /** RFC3339 字符串。 */
    val fetchedAt: String? = null,
    val stale: Boolean? = null,
)

/**
 * 二课缓存的统一响应容器：命中信封与未命中配方的**字段并集**。
 *
 * ## 判别规则
 * 与 jiaowu 一样看 `code`：
 * - 有 `code` → 未命中/错误，读 [recipe]
 * - 无 `code` → 命中，读 [payload] / [schemaVersion]
 */
@Serializable
data class SecondClassCacheResponse(
    val code: String? = null,
    val message: String? = null,
    val recipe: SecondClassRecipeDto? = null,
    val payload: JsonElement? = null,
    val schemaVersion: Int? = null,
    val fetchedAt: String? = null,
    val stale: Boolean? = null,
) {
    /** 是否未命中（要按配方去取数）。 */
    fun isMiss(): Boolean = !code.isNullOrEmpty()

    /** 是否「没有可用副本」这一正常流程分支。 */
    fun isCacheMiss(): Boolean =
        code == com.nongyu.appplus.net.core.ApiErrorCode.CACHE_MISS
}
