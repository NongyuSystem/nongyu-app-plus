package com.nongyu.appplus.net

import android.content.Context
import com.nongyu.appplus.net.api.CacheApi
import com.nongyu.appplus.net.api.IdentityApi
import com.nongyu.appplus.net.auth.DefaultSessionInvalidListener
import com.nongyu.appplus.net.auth.MinimalSessionStore
import com.nongyu.appplus.net.auth.SessionStore
import com.nongyu.appplus.net.http.AndroidLogger
import com.nongyu.appplus.BuildConfig
import com.nongyu.appplus.net.http.OkHttpFactory
import com.nongyu.appplus.net.sign.AppRequestSigner
import com.nongyu.appplus.net.model.NetJson
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * 网络层惰性装配点（规格 §2.4）。
 *
 * ## ⚠️ 三条守卫（违反会破坏 ADR-0006）
 *
 * **1. 禁止在 Composable / Activity / `onCreate` 路径上访问 [api]。**
 * `docs/adr/0006-冷启动只读本地皮肤包不碰网络.md` 明写「冷启动只读本地、绝不碰网络」。
 * 当前启动路径是 `MainActivity.onCreate` → `enableEdgeToEdge()` → `FileSkinSource.resolve()`
 * → `setContent`，**没有任何网络入口**。本类是 `by lazy`，所以只要没人访问就不会构造 ——
 * 但「没人访问」这件事必须由调用方守住。
 *
 * **2. 首次访问会阻塞调用线程。**
 * `by lazy` 默认 `SYNCHRONIZED` 模式。它不做网络 IO（只构造 OkHttpClient / Retrofit），
 * 但**会话存储的 Keystore 初始化是磁盘 + IPC**。所以 `MinimalSessionStore` 自己也是惰性的
 * （构造只存 context，首次读写才真正打开）。即便如此，也**不要在主线程首次访问本类**。
 *
 * **3. 将来接入「启动时恢复会话」或「皮肤包下载器」时，必须在首帧之后。**
 * ADR-0006 的约束是对整条启动路径的，不只是皮肤。
 *
 * ## 一期构造范围
 * [NongyuApi] 只持有 [IdentityApi]；[SchoolApi] / [PresenceApi] 只定义接口、不构造实例
 * （属「客户端直连学校」的独立平面，out of scope）。
 *
 * **例外**：[CacheApi]（回传缓存 3 条路由）已纳入一期，由 [cache] 单独暴露，
 * 与 [api] 共享同一个 Retrofit / OkHttp 连接池。见规格 §3.6。
 */
object NetHolder {

    @Volatile
    private var initialized: NongyuApi? = null

    /** 共享的 Retrofit 实例，`api()` 与 `cache()` 复用它（避免重复建 OkHttp 连接池）。 */
    @Volatile
    private var retrofitRef: Retrofit? = null

    /**
     * 身份域门面。**首次访问才装配**。
     *
     * @param context 用来建会话存储。传 `applicationContext`，不要传 Activity（防泄漏）。
     */
    fun api(context: Context): NongyuApi =
        initialized ?: synchronized(this) {
            initialized ?: build(context.applicationContext).also { initialized = it }
        }

    /**
     * 回传缓存接口（规格 §3.6）。**独立入口，不并入 [NongyuApi]**。
     *
     * 与 [api] 共享同一个 [Retrofit] / OkHttp 连接池；若 [api] 已装配，这里不再重复构建。
     * 三条路由都需要 Bearer，无票时由 `AuthInterceptor` 本地短路。
     *
     * @param context 首次装配时用；已装配时可传任意 context（不会用到）。
     */
    fun cache(context: Context): CacheApi {
        val r = retrofitRef ?: synchronized(this) {
            retrofitRef ?: buildRetrofit(context.applicationContext).also { retrofitRef = it }
        }
        return r.create(CacheApi::class.java)
    }

    /** 仅供测试：注入一个预构造的实例，绕过 Keystore 与网络装配。 */
    internal fun overrideForTest(api: NongyuApi?) {
        synchronized(this) { initialized = api }
    }

    /** 测试用：读取当前是否已装配（验证惰性）。 */
    internal fun isInitialized(): Boolean = initialized != null

    private fun build(context: Context): NongyuApi {
        val retrofit = buildRetrofit(context).also { retrofitRef = it }
        return NongyuApi(
            identity = retrofit.create(IdentityApi::class.java),
            baseUrl = retrofit.baseUrl().toString(),
        )
    }

    /** 构造 Retrofit —— 唯一一处装配 OkHttp 客户端与转换器。 */
    private fun buildRetrofit(context: Context): Retrofit {
        // 1) 会话存储：构造只存 context，首次读写才真正打开 Keystore（守卫 2）
        val store: SessionStore = MinimalSessionStore(context, AndroidLogger)
        val sessionInvalidListener = DefaultSessionInvalidListener(store, AndroidLogger)

        // 2) token 来源：按变体分流（debug 用 DEV_TOKEN，release 恒 null）
        val tokenProvider = defaultTokenProvider()

        // 3) OkHttp：超时 + retryOnConnectionFailure(false) + 三个拦截器
        //    日志开关按变体分流
        val client: OkHttpClient = OkHttpFactory.build(
            tokenProvider = tokenProvider,
            sessionInvalidListener = sessionInvalidListener,
            loggingEnabled = netLoggingEnabled(),
            logger = AndroidLogger,
            requestSigner = AppRequestSigner.fromBase64(
                BuildConfig.APP_SIGNING_KEY_ID,
                BuildConfig.APP_SIGNING_KEY_B64,
            ),
        )

        // 4) baseUrl 规范化（空串会在这里抛带明确文案的异常，不静默失败）
        val baseUrl = com.nongyu.appplus.net.core.UrlBuilder.normalize(NetConfig.BASE_URL)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()
    }
}
