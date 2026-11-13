package com.nongyu.appplus.login.model

import kotlin.random.Random

/**
 * 前端本地图形验证码（邮箱发码格用）。
 *
 * ## 为什么是前端的
 * 后端 `sendMailCodeHandler` **只读 `{email}`、不校验任何图形码**（真源：
 * `internal/identity/flow/handler.go:170-186`），防刷由服务端 `mailCodes.Cooldown`
 * 兜底。所以这张图**不是安全边界**，只是本地的交互约束：
 * 用户必须把图上的 4 个字符填对，才允许点「获取验证码」。
 *
 * ## 明确的边界（不要误当安全能力）
 * - 攻击者直接调 API 就绕过它，本地生成的东西在客户端一定能被读到；
 * - 它挡的是"普通用户误点/瞎点"，不是脚本；
 * - 校验也只在本地做，后端不参与。
 *
 * ## 为什么放 model 层
 * 生成与校验是纯逻辑（无 Android/Compose 依赖），必须能纯 JVM 测；
 * 绘制由 UI 层读 [text] 画出来。
 */
data class LocalCaptcha(
    /** 图上要显示的 4 位字符（大写字母 + 数字，去掉了易混的 0/O/1/I/L）。 */
    val text: String,
) {
    /** 校验用户输入：忽略大小写与首尾空白。 */
    fun matches(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return input.trim().equals(text, ignoreCase = true)
    }

    companion object {
        /** 字符集：去掉 0/O/1/I/L 这些读图时容易混淆的字符。 */
        private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        const val LENGTH = 4

        /** 生成一张新的验证码。 */
        fun random(random: Random = Random.Default): LocalCaptcha =
            LocalCaptcha(
                text = (1..LENGTH)
                    .map { ALPHABET[random.nextInt(ALPHABET.length)] }
                    .joinToString(""),
            )
    }
}
