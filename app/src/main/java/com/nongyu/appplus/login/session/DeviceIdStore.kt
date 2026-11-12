package com.nongyu.appplus.login.session

import android.content.Context
import java.util.UUID

/**
 * 设备标识（票 #92 / Q20=A）。
 *
 * - 一次生成、持久化到**普通 SharedPreferences 独立文件**（不进加密存储、不改 `SessionStore` 接口）；
 * - 只在换设备 / 重装 / 清数据时才允许变；
 * - 后端只在登录时收 `loginReq.deviceId`（单设备顶替 + 审计），**每次业务请求不读 device 头**
 *   （`docs/research/backend-plus-login-ui-facts.md` §5）。
 *
 * 与 App 会话凭证（[com.nongyu.appplus.net.auth.SessionStore]）完全分离：
 * 那是凭证真源，这里只是设备维度，两者生命周期不同。
 */
object DeviceIdStore {

    private const val PREFS_NAME = "nongyu_device_id"
    private const val KEY_DEVICE_ID = "device_id"

    /** 取当前设备标识；首次调用时生成并落盘。 */
    fun getOrCreate(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }
}
