package com.nongyu.appplus.login.data.cas

import kotlinx.serialization.Serializable

/**
 * 微信扫码登录的三个数据形状（票 #96 / 真站实测 2026-11-13）。
 *
 * ## 端点与参数（浏览器实测，非猜测）
 * - `POST /api/v1/cas/getLoginQr`   body `{"appid":"ywtb"}` → 解密得 [CasLoginQr]
 * - `POST /api/v1/cas/getCasLoginQrRes` body `{"state":"loginByQr:..."}` → 解密得 [CasQrResult]
 *
 * 两者都是 POST + JSON，响应都是标准 [CasEnvelope]（`requestId/code/msg/data`），
 * `data` 用 [CasDecrypt] 解（MD5(requestId+":sicau-cas") 作 AES-128 key）。
 *
 * ⚠️ 二维码**不是** GET 请求：GET 会 404（实测）。
 * ⚠️ 轮询必须传 `state`，不是 `requestId`；传错会得到"微信回调链接有误"。
 */

/** `getLoginQr` 解密后的载荷。 */
@Serializable
data class CasLoginQr(
    /** 微信网页授权 URL；App 需把它渲染成二维码给用户扫。 */
    val url: String? = null,
    /** 一次性事务串，轮询时要原样回传。 */
    val state: String? = null,
)

/**
 * `getCasLoginQrRes` 解密后的载荷。
 *
 * 未扫码时**全为空**（`tgt:""`、`user:null`）——**不能当成登录成功**。
 * 扫码完成后 `tgt` 才有值，那就是交给后端 `method=school` 的 `go-admin-tgt`。
 */
@Serializable
data class CasQrResult(
    val callbackUrl: String? = null,
    val tgt: String? = null,
    val st: String? = null,
    val user: CasQrUser? = null,
    val users: List<CasQrUser>? = null,
    val uuid: String? = null,
) {
    /** 是否已扫码完成（拿到 tgt 才算）。 */
    val isScanned: Boolean get() = !tgt.isNullOrBlank()
}

/** 扫码结果里的用户信息（tgt 有值时可能带）。 */
@Serializable
data class CasQrUser(
    val id: Long? = null,
    val number: String? = null,
    val name: String? = null,
)
