package com.nongyu.appplus.net.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * 统一调用包装：**catch 顺序 + 重试执行 + `Response`/`errorBody` 拆解**（规格 §5、§3.2）。
 *
 * 这是 `net/core` 的汇聚点。`ApiCall` 之外不应再有第二处做失败分类或 errorBody 解析的代码 ——
 * 那就是两份真源。
 *
 * ## 为什么分类落在这里，而不是 `Converter.Factory`
 * `kotlinx.serialization` 抛的 `SerializationException` **继承 `IllegalArgumentException`、
 * 不继承 `IOException`**。Retrofit 的 `Converter.Factory` 不会把反序列化失败变成
 * `isSuccessful = false`；解析发生在拿到 2xx 之后，异常**从 `suspend` 调用点抛出**。
 * 工厂只负责「用我们定好的 `Json` 建出来」，**不要再在工厂里 catch**。
 */
object ApiCall {

    /** 成功与失败的统一表示。 */
    sealed class Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>()
        data class Failure(val failure: ApiFailure) : Outcome<Nothing>()
    }

    /**
     * 把非 2xx 的响应体解析成业务码与信息。
     *
     * 由 `net/model` 层提供实现（需要读取原始 body 文本再交给 `Json`），
     * 本接口存在的意义是让 `ApiCall` 保持零 serialization 依赖、纯逻辑可测。
     *
     * **实现方必须容错**：body 不是 JSON（例如 404 返回 `text/plain`）时返回 `null`，
     * 不得抛异常 —— 那会让一个本该是 `Http` 的失败被误判成 `Parse`。
     */
    fun interface ErrorEnvelopeParser {
        fun parse(errorBody: ResponseBody?): ParsedEnvelope?
    }

    /** 错误信封解出的内容。两者都可空（identity/中间件部分错误只有 code）。 */
    data class ParsedEnvelope(val code: String?, val message: String?)

    /**
     * 执行一次请求，失败时按 [RetryPolicy] 重试。
     *
     * @param method HTTP 方法（大小写不敏感），用于重试判定
     * @param url 仅用于日志与错误信息（内部会剥掉 query）
     * @param errorEnvelopeParser 把 errorBody 解成 `(code, message)`；不传则两者恒为 `null`
     * @param block 真正的网络调用
     */
    suspend fun <T> execute(
        method: String,
        url: String,
        errorEnvelopeParser: ErrorEnvelopeParser = ErrorEnvelopeParser { null },
        block: suspend () -> Response<T>,
    ): Outcome<T> {
        var lastFailure: ApiFailure? = null
        val safeUrl = UrlBuilder.stripQuery(url)

        repeat(RetryPolicy.MAX_RETRIES + 1) { attempt ->
            // ⚠️ 规格 §5.4 第 3 条：重试前必须查 isActive。
            // 否则「取消 → OkHttp 抛 IOException("Canceled") → 被判成 Network → 又发一次」。
            currentCoroutineContext().ensureActive()

            when (val result = attemptOnce(method, safeUrl, errorEnvelopeParser, block)) {
                is Outcome.Success -> return result
                is Outcome.Failure -> {
                    lastFailure = result.failure
                    val isLastAttempt = attempt == RetryPolicy.MAX_RETRIES
                    if (isLastAttempt || !RetryPolicy.shouldRetry(method, result.failure)) {
                        return result
                    }
                    delay(RetryPolicy.RETRY_DELAY_MS)
                }
            }
        }

        // 逻辑上不可达：上面循环必然在某一轮 return 或抛异常
        return Outcome.Failure(
            lastFailure ?: ApiFailure.Parse(IllegalStateException("unreachable"), null),
        )
    }

    /**
     * 单次尝试。**catch 顺序不许调换。**
     */
    private suspend fun <T> attemptOnce(
        method: String,
        safeUrl: String,
        errorEnvelopeParser: ErrorEnvelopeParser,
        block: suspend () -> Response<T>,
    ): Outcome<T> = try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) {
                // 2xx 但没有 body：这不是「成功」，也不是解析失败 —— 归 Parse，
                // 语义是「拿到了响应但没法变成模型」。不静默返回 null 给上层。
                Outcome.Failure(
                    ApiFailure.Parse(
                        IllegalStateException("2xx 响应缺少 body：$safeUrl"),
                        bodyPreview = null,
                    ),
                )
            } else {
                Outcome.Success(body)
            }
        } else {
            val envelope = errorEnvelopeParser.parse(response.errorBody())
            Outcome.Failure(
                ApiFailure.Http(
                    status = response.code(),
                    code = envelope?.code,
                    message = envelope?.message,
                    url = safeUrl,
                ),
            )
        }
    } catch (e: CancellationException) {
        // ① 绝不吞。吞了会破坏 withContext / ensureActive 语义。
        throw e
    } catch (e: SerializationException) {
        // ② 必须在 IOException 之前显式列出（它不继承 IOException，顺序反了也不会误捕，
        //    但置于此处能让「Parse 为什么独立」在代码里可读）。
        Outcome.Failure(ApiFailure.Parse(e, bodyPreview = null))
    } catch (e: SocketTimeoutException) {
        // ③ 读超时。注意它是 InterruptedIOException 的子类，所以必须写在 ④ 之前。
        Outcome.Failure(ApiFailure.Timeout(e))
    } catch (e: InterruptedIOException) {
        // ④ callTimeout 到期抛的就是 InterruptedIOException("timeout")
        Outcome.Failure(ApiFailure.Timeout(e))
    } catch (e: IOException) {
        // ⑤ 其余 IO。OkHttp 取消时抛 IOException("Canceled")，要单独认出来，
        //    否则会被当成 Network 并触发重试（见 RetryPolicy / §5.4）。
        if (e.message == "Canceled") {
            Outcome.Failure(ApiFailure.Cancelled)
        } else {
            Outcome.Failure(ApiFailure.Network(e))
        }
    } catch (e: Throwable) {
        // ⑥ 兜底，不崩。
        Outcome.Failure(ApiFailure.Parse(e, bodyPreview = null))
    }
}
