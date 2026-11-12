package com.nongyu.appplus.ui.login

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.nongyu.appplus.net.auth.SessionStore

/**
 * 登录会话状态容器（票 #90 单一主接缝的 Android 承载）。
 *
 * 用 `mutableStateOf` 持有 [LoginSessionState]，根节点 `when(state.destination)` 据此重组。
 * 进程内单例，避免 Activity 重建时状态丢失，也避免第二套凭证存储。
 *
 * ## 凭证真源
 * 只读 [SessionStore.readToken] 判断首帧去向；清凭证走既有失效分流（由
 * `DefaultSessionInvalidListener` 调 [SessionStore.clear]），本类**不自己 clear**，
 * 以保证「全仓只有一个凭证真源」（Q8=A / #43）。
 */
object LoginSessionHolder {

    private val _state = mutableStateOf(
        // 占位初值：真正的 initial() 由 MainActivity 在首帧前用 store 判定后写入。
        LoginSessionState.initial(hasCredential = false),
    )

    /** 供根节点观察的只读状态。 */
    val state: State<LoginSessionState> get() = _state

    /** 首帧前：用真实会话存储判定落地去向（不发起任何网络请求）。 */
    fun initialize(store: SessionStore) {
        _state.value = LoginSessionState.initial(hasCredential = store.readToken() != null)
    }

    /**
     * 首帧之后校验结果回来时调用。
     *
     * SESSION_ENDED → 切回登录中心；上层同时应触发既有失效分流 `clear()`。
     * VALID / INDETERMINATE → 不动状态（乐观放行、零提示）。
     */
    fun onVerified(result: VerificationResult) {
        _state.value = _state.value.onVerified(result)
    }

    /** 登录成功后调用（供 S3/S4/S7 复用）。 */
    fun onSignedIn() {
        _state.value = _state.value.onSignedIn()
    }
}
