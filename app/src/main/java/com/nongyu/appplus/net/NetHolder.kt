package com.nongyu.appplus.net

import android.content.Context
import com.nongyu.appplus.net.api.IdentityApi
import com.nongyu.appplus.net.auth.DefaultSessionInvalidListener
import com.nongyu.appplus.net.auth.MinimalSessionStore
import com.nongyu.appplus.net.auth.SessionStore
import com.nongyu.appplus.net.http.AndroidLogger
import com.nongyu.appplus.net.http.OkHttpFactory
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
 * ## 一期不构造 school / presence
 * `NongyuApi` 只持有 `IdentityApi`。那两个域属「客户端直连学校」的独立平面（out of scope）。
 */
object NetHolder {

    @Volatile
    private var initialized: NongyuApi? = null

    /**
     * 对外唯一入口。**首次访问才装配**。
     *
     * @param context 用来建会话存储。传 `applicationContext`，不要传 Activity（防泄漏）。
     */
    fun api(context: Context): NongyuApi =
        initialized ?: synchronized(this) {
            initialized ?: build(context.applicationContext).also { initialized = it }
        }

    /** 仅供测试：注入一个预构造的实例，绕过 Keystore 与网络装配。 */
    internal fun overrideForTest(api: NongyuApi?) {
        synchronized(this) { initialized = api }
    }

    /** 测试用：读取当前是否已装配（验证惰性）。 */
    internal fun isInitialized(): Boolean = initialized != null

    private fun build(context: Context): NongyuApi {
        // 1) 会话存储：构造只存 context，首次读写才真正打开 Keystore（守卫 2）
        val store: SessionStore = MinimalSessionStore(context, AndroidLogger)
        val sessionInvalidListener = DefaultSessionInvalidListener(store, AndroidLogger)

        // 2) token 来源：按源集分流（src/debug 用 DEV_TOKEN，src/release 恒 null）
        val tokenProvider = defaultTokenProvider()

        // 3) OkHttp：超时 + retryOnConnectionFailure(false) + 三个拦截器
        //    日志开关按源集分流（与 NetConfig / DefaultTokenProvider 同一手法）
        val client: OkHttpClient = OkHttpFactory.build(
            tokenProvider = tokenProvider,
            sessionInvalidListener = sessionInvalidListener,
            loggingEnabled = netLoggingEnabled(),
            logger = AndroidLogger,
        )

        // 4) Retrofit：baseUrl 规范化（空串会在这里抛带明确文案的异常，不静默失败）
        val baseUrl = com.nongyu.appplus.net.core.UrlBuilder.normalize(NetConfig.BASE_URL)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(NetJson.converterFactory())
            .build()

        return NongyuApi(
            identity = retrofit.create(IdentityApi::class.java),
            baseUrl = baseUrl,
        )
    }
}
