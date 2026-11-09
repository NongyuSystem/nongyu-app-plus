package com.nongyu.appplus.net

import android.content.Context
import android.util.Log
import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nongyu.appplus.net.core.ApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 最小真实出口：**首帧之后**打一次 `/healthz`，失败只记日志。
 *
 * ## 为什么需要它（这条不是可有可无的验证代码）
 * `#48` 与 N6 两次实测表明：只要**没有任何业务代码从入口点可达地引用网络层**，
 * R8 就会把 okhttp3 / retrofit2 / kotlinx.serialization 整条链删光
 * （`mapping.txt` 保留 0 个类），于是：
 * - 「R8 需要什么 keep」永远测不出来；
 * - 真机验收没有对象可验（没有任何代码路径会真的发请求）。
 *
 * 本类提供一个**可达的、极小的**真实调用点，让上述两件事成为可能。
 *
 * ## 为什么不是「在首页 Composable 里调」
 * `#50` Q6 原本定「一期网络层不被 UI 调用」，理由是「一期没有 ViewModel，允许 Composable 直调的话
 * 将来要全改一遍」。本类**不违反该理由**：它不是 UI 逻辑，不含任何状态与渲染，
 * 只是一个**启动期一次性副作用**，放在 Activity 层、由 Lifecycle 驱动。
 * 将来接 ViewModel 时删掉本类即可，与 UI 层零耦合。
 *
 * ## ⚠️ 三条必须守住的边界
 * 1. **必须在首帧之后**（`docs/adr/0006`：冷启动只读本地、绝不碰网络）。
 *    实现上用 `Choreographer.postFrameCallback` 等首帧，再切到 `Dispatchers.IO`。
 * 2. **任何异常都必须吞掉**，只记日志。健康探针失败绝不能影响用户使用。
 * 3. **只打一次**。用 `repeatOnLifecycle(STARTED)` + 一次性标志，Activity 重建也不重复发。
 */
object NetStartupProbe {

    private const val TAG = "NongyuNetProbe"

    @Volatile
    private var alreadyProbed = false

    /** 测试用：复位「只打一次」标志。 */
    internal fun resetForTest() {
        alreadyProbed = false
    }

    /**
     * 在**首帧之后**发起一次 `/healthz`。
     *
     * @param owner 用来绑定协程生命周期（传 Activity）
     * @param context 应用上下文（用于 `NetHolder` 装配）
     */
    fun schedule(owner: LifecycleOwner, context: Context) {
        if (alreadyProbed) return
        alreadyProbed = true

        // 等首帧：postFrameCallback 在下一帧绘制时回调，天然晚于 setContent 的首次布局+绘制
        Choreographer.getInstance().postFrameCallback {
            val appContext = context.applicationContext
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runProbe(appContext)
                }
            }
        }
    }

    /**
     * 真正发请求。**不抛任何异常。**
     *
     * 单独成函数是为了能在测试里直接调（绕开 Choreographer）。
     */
    internal suspend fun runProbe(context: Context) {
        try {
            val api = NetHolder.api(context)
            when (val result = api.healthz()) {
                is ApiCall.Outcome.Success ->
                    Log.i(TAG, "健康检查成功：status=${result.value.status}")

                is ApiCall.Outcome.Failure ->
                    // 失败只记日志 —— 连不上后端不该影响任何用户可用性
                    Log.w(TAG, "健康检查失败：${result.failure}")
            }
        } catch (e: Throwable) {
            // 包括 CancellationException 之外的一切；探针绝不能把 App 弄崩
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "健康检查异常", e)
        }
    }
}
