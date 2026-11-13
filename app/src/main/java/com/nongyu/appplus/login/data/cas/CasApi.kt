package com.nongyu.appplus.login.data.cas

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 统一身份认证平台（统一身份认证平台）四条端点（票 #95）。
 *
 * ## 与 `net/` 的隔离
 * 本接口由 [CasApiFactory] 用**独立 OkHttp client** 装配：
 * - 不挂 `AppSignInterceptor`（统一身份认证平台 请求**不带农屿 NY1 签名头**）
 * - 不挂 `AuthInterceptor`（不携带农屿 Bearer）
 * - 不复用 `NetHolder` / `NongyuGateWay` 的任何装配
 *
 * ## 形状真源
 * 旧 RN 工具包 `packages/nongyu-tool-jiaowu/core/login/cas.ts`：
 * - `GET  /api/v1/captcha?appid=<appid>`
 * - `POST /api/v1/cas/login`        body `{appid, code, number, password, uuid}`
 * - `POST /api/v1/sms/send`         body `{uuid, code, type, phone}`
 * - `POST /api/v1/cas/loginByPhone` body `{code, phone, appid}`
 *
 * 响应统一是 [CasEnvelope] 信封（`{requestId, code, msg, data, id}`），
 * `data` 为加密体；用 `ResponseBody` 收原始文本，由 [CasDecrypt] 拆信封。
 */
interface CasApi {

    @GET("api/v1/captcha")
    suspend fun captcha(@Query("appid") appid: String): Response<ResponseBody>

    @POST("api/v1/cas/login")
    suspend fun login(@Body body: okhttp3.RequestBody): Response<ResponseBody>

    @POST("api/v1/sms/send")
    suspend fun sendSms(@Body body: okhttp3.RequestBody): Response<ResponseBody>

    @POST("api/v1/cas/loginByPhone")
    suspend fun loginByPhone(@Body body: okhttp3.RequestBody): Response<ResponseBody>

    /**
     * `POST /api/v1/cas/getLoginQr` —— 取微信登录二维码（真站实测 2026-11-13）。
     *
     * **必须是 POST**（GET 会 404）；body `{"appid":"ywtb"}`。
     * 响应 `data` 解密后是 CasLoginQr（含 url 与 state）。
     */
    @POST("api/v1/cas/getLoginQr")
    suspend fun getLoginQr(@Body body: okhttp3.RequestBody): Response<ResponseBody>

    /**
     * `POST /api/v1/cas/getCasLoginQrRes` —— 轮询扫码结果（真站实测 2026-11-13）。
     *
     * body `{"state":"loginByQr:..."}`（**是 state，不是 requestId**）。
     * 未扫码时解密结果为全空；扫码完成后带 tgt。
     */
    @POST("api/v1/cas/getCasLoginQrRes")
    suspend fun getCasLoginQrRes(@Body body: okhttp3.RequestBody): Response<ResponseBody>
}
