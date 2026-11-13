package com.nongyu.appplus.login.data.cas

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 统一身份认证（统一身份认证平台）独立网络平面的装配点（票 #95）。
 *
 * ## 为什么必须独立
 * 票 #95 验收判据：「统一身份认证平台请求不带农屿 NY1 签名头；与农屿后端请求隔离」。
 * 农屿 `net/` 的 `OkHttpFactory` 会强制挂 `AppSignInterceptor` + `AuthInterceptor`，
 * 复用它必然误带 `X-Nongyu-*` 头与 Bearer。这里只装一个裸 OkHttp + [CasCookieJar]，
 * 两者物理隔离。
 *
 * ## baseUrl
 * 生产为 `https://auth.sicau.edu.cn/`；测试用 MockWebServer 地址注入。
 * **不在此写死真站地址**，避免本票「不访问真实统一身份认证平台 URL」的验收被无意触发。
 */
object CasApiFactory {

    /**
     * 学校统一身份认证平台根地址（票 #96 真站装配）。
     *
     * 真源：后端 `internal/school/schoolauth/jiaowu.go` `authHost`、
     * `internal/school/cas/exchange.go` `authBase`，以及出站白名单
     * `app.go` 里的 `auth.sicau.edu.cn`。https，不需要 debug 明文放行。
     */
    const val REAL_BASE_URL = "https://auth.sicau.edu.cn/"

    fun create(
        baseUrl: String,
        cookieJar: CasCookieJar = CasCookieJar(),
        tgtInterceptor: TgtCaptureInterceptor = TgtCaptureInterceptor(),
        client: OkHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // 从 Set-Cookie 头捕获 go-admin-tgt（HttpOnly，body 里没有）
            .addInterceptor(tgtInterceptor)
            // 与旧 RN 工具包 CAS_TIMEOUT / CALLBACK_TIMEOUT 一致：15s 连接 / 20s 读
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build(),
    ): CasClient {
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(CasApi::class.java)
        return CasClient(api, cookieJar, tgtInterceptor)
    }

    /** 一次装配产出的三件套。 */
    data class CasClient(
        val api: CasApi,
        val jar: CasCookieJar,
        val tgtInterceptor: TgtCaptureInterceptor,
    )
}
