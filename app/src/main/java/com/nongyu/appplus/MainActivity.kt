package com.nongyu.appplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nongyu.appplus.ui.login.LoginSessionHolder
import com.nongyu.appplus.ui.login.SessionStartupVerifier
import com.nongyu.appplus.ui.login.VerificationResult
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.NetStartupProbe
import com.nongyu.appplus.skin.FileSkinSource
import com.nongyu.appplus.ui.NongyuApp
import com.nongyu.appplus.design.NongyuAppPlusTheme

/**
 * 应用唯一入口 Activity。
 *
 * 骨架阶段只负责挂载 Compose 根节点，不承载业务逻辑；
 * 后续路由、依赖注入等能力均在 [NongyuApp] 内部扩展。
 *
 * ## 皮肤在哪儿解析（ADR-0006，重要）
 *
 * 皮肤在 [setContent] **之前**同步解析完，然后把结果作为 `colors` 传进主题。
 * 这样做的原因只有一条：**不允许首帧后跳色**。
 *
 * 如果改成「先渲染默认色板、异步加载后再换」，用户会看到一次颜色跳变
 * （`staticCompositionLocalOf` 换值还会触发整块 content 重组）。而先解析再渲染，
 * 首帧就是最终色板，观感稳定。
 *
 * 代价是启动路径上多了一次**本地磁盘读**（读指针小文件 + 可能的包清单）。
 * 这个代价是 ADR-0006 明确接受的；它**绝不包含网络**：
 * 换肤数据的网络刷新必须放在首帧之后，由下载器负责写盘，下次启动才生效。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 键盘不许推动整页（维护者要求「只用输入框上移」）。
        // 必须在 super.onCreate + enableEdgeToEdge 之后设：这两步都会重算窗口 flag，
        // 只写在 AndroidManifest 里会被覆盖（实测键盘弹出时整页仍上移 63px）。
        // 真正的「输入条上移」由 MainShell 里底栏的 imePadding() 负责。
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED,
        )

        // 同步解析皮肤：只读本地，不碰网络（ADR-0006）。
        // 任何异常都在 FileSkinSource 内部被兜住并回落内置默认，这里不需要 try/catch。
        val skin = FileSkinSource(
            context = applicationContext,
            appVersionCode = BuildConfig.VERSION_CODE,
        )
        val resolution = skin.resolve()

        // 票 #90：会话失效时（含业务请求 401 兜底）驱动根节点回登录中心。
        // UI → net 注入，net 层不反向依赖 UI。
        NetHolder.setOnSessionInvalidUi {
            LoginSessionHolder.onVerified(VerificationResult.SESSION_ENDED)
        }

        // 票 #90：首帧前用会话存储判定落地去向（只读本地，不碰网络，ADR-0006）。
        // 同一 SessionStore 实例由 NetHolder 持有，供 token 来源 / 失效分流 / 校验复用。
        LoginSessionHolder.initialize(NetHolder.sessionStore(applicationContext))

        setContent {
            NongyuAppPlusTheme(
                colors = resolution.palette(),
                source = skin,
                resolution = resolution,
            ) {
                NongyuApp()
            }
        }

        // 首帧之后打一次健康探针（**不违反 ADR-0006**）。
        //
        // 为什么需要它：没有从入口点可达的网络调用时，R8 会把 okhttp3 / retrofit2 /
        // kotlinx.serialization 整条链删光（mapping.txt 保留 0 个类），
        // 「R8 需要什么 keep」与真机验收都无从验证。见 docs/specs/网络层-N6-实施记录.md §3。
        //
        // 为什么安全：schedule() 内部用 Choreographer.postFrameCallback 等首帧，
        // 再切到协程里发请求；失败只记日志，绝不影响可用性。
        NetStartupProbe.schedule(owner = this, context = applicationContext)

        // 票 #90：首帧之后打一次 GET /identity/me 做事后纠正（同样不延长开屏）。
        // 401 会话结束 → 走既有失效分流清凭证并切回登录中心；其余乐观放行、零提示。
        SessionStartupVerifier.schedule(owner = this, context = applicationContext)
    }
}
