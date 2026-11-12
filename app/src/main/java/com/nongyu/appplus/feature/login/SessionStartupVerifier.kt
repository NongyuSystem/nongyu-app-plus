package com.nongyu.appplus.feature.login

import android.content.Context
import android.util.Log
import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.core.ApiCall
import kotlinx.coroutines.launch

/**
 * 启动校验：**首帧之后**打一次 `GET /identity/me`，做事后纠正。
 *
 * ## 为什么单独成类、为什么仿 `NetStartupProbe`
 * ADR-0006 要求冷启动绝不碰网络；`NetStartupProbe` 已给出范式：
 * `Choreographer.postFrameCallback` 等首帧 → `lifecycleScope` + `repeatOnLifecycle(STARTED)`。
 * 本类沿用同一调度，只是把请求从 `/healthz` 换成 `/identity/me`。
 *
 * ## 三条边界（与 NetStartupProbe 一致）
 * 1. 只在首帧之后发，不延长开屏、不等待网络；
 * 2. 任何异常都吞掉、只记日志，绝不影响首帧去向；
 * 3. 只发一次（进程内标志），Activity 重建不重复。
 *
 * ## 事后纠正
 * 结果由 [VerificationPolicy]（纯 JVM）分类：
 * - SESSION_ENDED → 走既有失效分流 `clear()` 清凭证 + [LoginSessionHolder.onVerified] 切回登录中心；
 * - VALID / INDETERMINATE → 不动状态（乐观放行、零提示）。
 *
 * 清凭证复用 `NetHolder.sessionInvalidListener(context)` 的同一实例，不新建第二套存储。
 */
object SessionStartupVerifier {

    private const val TAG = "NongyuSessionVerify"

    @Volatile
    private var alreadyScheduled = false

    /** 测试用：复位「只发一次」标志。 */
    internal fun resetForTest() {
        alreadyScheduled = false
    }

    /**
     * 在首帧之后发起一次校验。
     *
     * @param owner 绑定协程生命周期（传 Activity）
     * @param context 应用上下文（用于 NetHolder 装配）
     */
    fun schedule(owner: LifecycleOwner, context: Context) {
        if (alreadyScheduled) return
        alreadyScheduled = true

        Choreographer.getInstance().postFrameCallback {
            val appContext = context.applicationContext
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runVerification(appContext)
                }
            }
        }
    }

    /** 单独成函数便于测试直接调（绕开 Choreographer）。不抛任何异常。 */
    internal suspend fun runVerification(context: Context) {
        try {
            val api = NetHolder.api(context)
            val failure = when (val outcome = api.me()) {
                is ApiCall.Outcome.Success -> null
                is ApiCall.Outcome.Failure -> outcome.failure
            }
            val result = VerificationPolicy.decide(failure)
            Log.i(TAG, "启动校验结果：$result")
            applyResult(context, result)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "启动校验异常，按无法判定处理", e)
            applyResult(context, VerificationResult.INDETERMINATE)
        }
    }

    private fun applyResult(context: Context, result: VerificationResult) {
        if (result == VerificationResult.SESSION_ENDED) {
            // 走既有失效分流清凭证（同一 SessionStore 实例，保证只有一个凭证真源）。
            // 注意：/identity/me 的 401 可能已被 SessionInvalidInterceptor 先调过一次，
            // DefaultSessionInvalidListener 的 AtomicBoolean 会去重 —— 这是刻意的，
            // 清凭证 + UI 回登录中心由该 listener 的 onCleared 统一驱动。
            try {
                NetHolder.sessionInvalidListener(context).onSessionInvalid()
            } catch (e: RuntimeException) {
                Log.w(TAG, "失效分流清理失败（不影响切回登录中心）", e)
            }
        }
        // 幂等地把状态推到目标：SESSION_ENDED 切回登录中心，其余不动状态（乐观放行）。
        LoginSessionHolder.onVerified(result)
    }
}
