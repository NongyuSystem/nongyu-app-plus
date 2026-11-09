package com.nongyu.appplus.net.model

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/**
 * 全网络层唯一的 `Json` 实例与转换器工厂（规格 §3.4 规则 4）。
 *
 * ## 配置是固定的，不要各处自建
 * ```kotlin
 * ignoreUnknownKeys = true   // 后端加字段不炸客户端
 * explicitNulls = false      // 序列化时不输出 null
 * ```
 *
 * ## `explicitNulls = false` 只影响序列化，**不影响反序列化**
 * 所以「`ProfileDto` 在 `/identity/me` 上可能没有 `studentNo`」这件事，
 * 靠的是 DTO 字段可空，不是靠这个开关。
 */
object NetJson {

    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Retrofit 用的 JSON 转换器工厂。 */
    fun converterFactory(): Converter.Factory =
        instance.asConverterFactory("application/json".toMediaType())

    /**
     * 把非 2xx 的 `errorBody` 解析成 `(code, message)`（规格 §3.2）。
     *
     * ## 必须容错
     * `backend-plus` 的 404 会返回 **`text/plain`**（无 DB 时不注册路由），
     * 那不是 JSON。若这里抛异常，一个本该是 `ApiFailure.Http` 的失败
     * 会被 `ApiCall` 的兜底误判成 `ApiFailure.Parse` —— 分类就错了。
     *
     * 所以：**读得到就读，读不到返回 `null`**，绝不抛。
     */
    fun parseErrorEnvelope(errorBody: ResponseBody?): ApiErrorDto? {
        if (errorBody == null) return null
        return try {
            val text = errorBody.string()
            if (text.isBlank()) return null
            instance.decodeFromString(ApiErrorDto.serializer(), text)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            // SerializationException 继承 IllegalArgumentException（RuntimeException 子类）
            null
        }
    }

    /** 给 `ApiCall.ErrorEnvelopeParser` 用的适配器（保持 `net/core` 不依赖 serialization）。 */
    fun errorEnvelopeParser(): com.nongyu.appplus.net.core.ApiCall.ErrorEnvelopeParser =
        com.nongyu.appplus.net.core.ApiCall.ErrorEnvelopeParser { body ->
            parseErrorEnvelope(body)?.let {
                com.nongyu.appplus.net.core.ApiCall.ParsedEnvelope(it.code, it.message)
            }
        }
}
