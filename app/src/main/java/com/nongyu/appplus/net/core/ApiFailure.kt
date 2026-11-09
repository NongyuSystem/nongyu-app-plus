package com.nongyu.appplus.net.core

import java.io.IOException

/**
 * 网络层统一失败模型（规格 §5.1）。
 *
 * ## 为什么 `Parse` 必须独立成类
 * 后端（`nongyu-backend-plus`）仍在密集重写，同一端点存在**两条完全不同的响应形状**
 * （成功吐裸业务结构体 / 失败吐 `{code, message}` 信封）。若把解析失败并入 [Network]，
 * 排查时分不清「网断了」还是「后端改了字段」。
 *
 * ## 六类之外不要加第七类
 * 「未知失败」不存在：`ApiCall` 的 `Throwable` 兜底也会归到 [Parse]
 * （语义是「拿到了响应但没法变成模型」）。新增类别属于改决议。
 */
sealed class ApiFailure {

    /**
     * 连接层失败：DNS、连接被拒、握手失败等，且已经收到了「非正常响应」。
     *
     * 注意：若 `IOException.message == "Canceled"`，`ApiCall` 会映射成 [Cancelled]，
     * 不是本类（见规格 §5.4 第 3 条）。
     */
    data class Network(val cause: IOException) : ApiFailure()

    /**
     * 超时。
     *
     * 两种来源：读超时抛 [java.net.SocketTimeoutException]；
     * `callTimeout` 到期抛 [java.io.InterruptedIOException]（OkHttp 的 `InterruptedIOException("timeout")`）。
     */
    data class Timeout(val cause: IOException) : ApiFailure()

    /**
     * HTTP 非 2xx。
     *
     * @param status HTTP 状态码
     * @param code 从错误信封里解出的业务码；信封解不出时为 `null`
     * @param message 可读信息；信封解不出时为 `null`
     * @param url 完整 URL（**已剥掉 query**，见规格 §6 脱敏要求）
     */
    data class Http(
        val status: Int,
        val code: String?,
        val message: String?,
        val url: String,
    ) : ApiFailure()

    /**
     * 业务拒绝：HTTP 2xx 但响应体里带非零业务码。
     *
     * **一期没有产出点** —— `backend-plus` 的错误一律配 4xx/5xx，不存在「200 + code」。
     * 保留类型是为了将来兼容，但**不许为它编造处理分支**。
     */
    data class Business(val code: String, val message: String) : ApiFailure()

    /**
     * 解析失败：拿到了响应，但没法变成模型。
     *
     * 典型来源：`kotlinx.serialization` 的 `SerializationException`（含 `MissingFieldException`）。
     * 它继承 `IllegalArgumentException`、**不继承** `IOException`，这是本类能独立的前提。
     *
     * @param bodyPreview 脱敏并截断后的响应体预览；不可用时为 `null`
     */
    data class Parse(val cause: Throwable, val bodyPreview: String?) : ApiFailure()

    /**
     * 请求被取消。
     *
     * **只允许**在「调用方主动取消、且确实需要一个值」的外层由调用方构造。
     * 网络层内部**不得**用它吞掉取消 —— `CancellationException` 必须重新抛出（规格 §5.4）。
     */
    data object Cancelled : ApiFailure()
}
