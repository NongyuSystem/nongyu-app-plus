package com.nongyu.appplus.login.data.cas

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 统一身份认证（学校侧）独立网络平面的装配点（票 #95）。
 *
 * ## 为什么必须独立
 * 票 #95 验收判据：「学校 CAS 请求不带农屿 NY1 签名头；与农屿后端请求隔离」。
 * 农屿 `net/` 的 `OkHttpFactory` 会强制挂 `AppSignInterceptor` + `AuthInterceptor`，
 * 复用它必然误带 `X-Nongyu-*` 头与 Bearer。这里只装一个裸 OkHttp + [CasCookieJar]，
 * 两者物理隔离。
 *
 * ## baseUrl
 * 生产为 `https://auth.sicau.edu.cn/`；测试用 MockWebServer 地址注入。
 * **不在此写死真站地址**，避免本票「不访问真实学校 URL」的验收被无意触发。
 */
object CasApiFactory {

    fun create(
        baseUrl: String,
        cookieJar: CasCookieJar = CasCookieJar(),
        client: OkHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // 与旧 RN 工具包 CAS_TIMEOUT / CALLBACK_TIMEOUT 一致：15s 连接 / 20s 读
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build(),
    ): Pair<CasApi, CasCookieJar> {
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(CasApi::class.java)
        return api to cookieJar
    }
}
