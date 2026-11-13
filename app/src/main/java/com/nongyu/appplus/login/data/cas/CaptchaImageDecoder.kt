package com.nongyu.appplus.login.data.cas

import java.util.Base64

/**
 * 把验证码的 data URI 解码成 PNG 字节（票 #96 缺口补齐）。
 *
 * ## 为什么单独成函数
 * S6 的 `fetchCaptcha()` 解出的明文形如 `data:image/png;base64,XXXX`
 * （真站还会带一层 JSON 引号，S6 已剥）。要显示成图片需要「剥前缀 → base64 解码」
 * 两步，这是纯数据变换，单独抽出来便于测试与复用。
 *
 * ## 为什么用 java.util.Base64 而不是 android.util.Base64
 * 纯 JVM 单测里 `android.util.Base64` 是 stub（调用抛 `RuntimeException: Stub!`），
 * 用 JDK 自带的解码器才能真测，且行为对这些输入完全够用。
 *
 * ## 容错
 * 任何异常返回 `null`，由 UI 决定显示占位。验证码图显示不出来不该让登录崩掉。
 */
object CaptchaImageDecoder {

    private const val MARKER = ";base64,"

    /**
     * @param dataUri `data:image/png;base64,XXXX`（可带外层引号与空白）
     * @return PNG 字节；格式不对或解码失败返回 null
     */
    fun decode(dataUri: String?): ByteArray? {
        if (dataUri.isNullOrBlank()) return null
        val cleaned = dataUri.trim().trim('"').trim()
        if (!cleaned.startsWith("data:image")) return null

        val idx = cleaned.indexOf(MARKER)
        if (idx < 0) return null
        val payload = cleaned.substring(idx + MARKER.length)
        if (payload.isEmpty()) return null

        return try {
            Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
