package com.nongyu.appplus.login.model

/**
 * 「获取验证码」按钮的文案状态机（票 #89 文案表 / 本轮新增）。
 *
 * ## 规格文案（#89 明写）
 * 「获取验证码 → 发送中… → 重新获取（Ns）→ 重新获取」
 * —— 60s 倒计时**只防连点**，不宣称 60s 后可重发（后端冷却 5 分钟）。
 *
 * ## 为什么抽成纯 JVM
 * 这是易错的状态+计时逻辑（发送中不可点、倒计时中不可点、结束后可点），
 * 必须能脱离 Compose 测；UI 只负责把 [label] 画出来、把 [enabled] 映射到按钮可用性。
 */
data class VerificationCodeButtonState(
    /** 是否正在发请求。 */
    val sending: Boolean = false,
    /** 剩余倒计时秒数；> 0 表示不可点。 */
    val remainingSeconds: Int = 0,
    /** 本次会话是否已经成功发过码：决定归零后显示「重新获取」还是「获取验证码」。 */
    val hasSentOnce: Boolean = false,
) {
    /** 按钮文案。 */
    val label: String
        get() = when {
            sending -> "发送中…"
            remainingSeconds > 0 -> "重新获取（${remainingSeconds}s）"
            hasSentOnce -> "重新获取"
            else -> "获取验证码"
        }

    /** 是否可点击：发送中与倒计时中都不可点。 */
    val enabled: Boolean
        get() = !sending && remainingSeconds == 0

    /** 开始发送。 */
    fun onStarted(): VerificationCodeButtonState = copy(sending = true)

    /** 发送失败：回到可点状态，不进倒计时。 */
    fun onFailed(): VerificationCodeButtonState =
        VerificationCodeButtonState(hasSentOnce = hasSentOnce)

    /** 发送成功：进入 [seconds] 秒倒计时。 */
    fun onSucceeded(seconds: Int = DEFAULT_COUNTDOWN_SECONDS): VerificationCodeButtonState =
        VerificationCodeButtonState(
            sending = false,
            remainingSeconds = seconds,
            hasSentOnce = true,
        )

    /** 每秒推进一次。 */
    fun tick(): VerificationCodeButtonState =
        if (remainingSeconds <= 0) this
        else copy(remainingSeconds = remainingSeconds - 1)

    companion object {
        /** 60s 只防连点（规格 #89），不是后端冷却时间。 */
        const val DEFAULT_COUNTDOWN_SECONDS = 60
    }
}
