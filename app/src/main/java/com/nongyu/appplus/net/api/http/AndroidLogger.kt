package com.nongyu.appplus.net.api.http

import android.util.Log
import com.nongyu.appplus.net.core.NongyuLogger

/**
 * [NongyuLogger] 的 Android 实现 —— 唯一允许碰 `android.util.Log` 的地方。
 *
 * 日志走 `Log.*` 到 logcat，**不落盘**（规格 §6.3）。
 */
object AndroidLogger : NongyuLogger {

    private const val TAG = "NongyuNet"

    override fun log(level: NongyuLogger.Level, message: String, throwable: Throwable?) {
        when (level) {
            NongyuLogger.Level.VERBOSE -> Log.v(TAG, message, throwable)
            NongyuLogger.Level.DEBUG -> Log.d(TAG, message, throwable)
            NongyuLogger.Level.INFO -> Log.i(TAG, message, throwable)
            NongyuLogger.Level.WARN -> Log.w(TAG, message, throwable)
            NongyuLogger.Level.ERROR -> Log.e(TAG, message, throwable)
        }
    }
}
