package com.nongyu.appplus.net.auth

import com.nongyu.appplus.net.core.NongyuLogger
import java.security.GeneralSecurityException

/**
 * 最小会话存储实现：`EncryptedSharedPreferences`（Keystore + AES）。
 *
 * ## 两条硬约束（规格 §2.4 守卫 2、§9 第 3 对）
 *
 * ### 1. 惰性打开
 * 构造时**只保存 context**，首次 [readToken] / [writeToken] / [clear] 才真正创建
 * `EncryptedSharedPreferences`。原因是 **Keystore 初始化是磁盘 + IPC**，
 * 不能在启动路径上跑（`docs/adr/0006`）。
 *
 * ### 2. 读取侧容错（**这条不做会崩**）
 * `data_extraction_rules.xml` 的备份排除**只是第一道防线**：排除规则不可能穷举所有备份通道
 * （ADB、厂商云、部分工具的私有通道）。一旦密文被带到新设备，那里没有对应的 Keystore Key，
 * 解密必然失败。所以 [readToken] 必须 catch 解密异常，**当作「没有 token」并清空**，
 * 不得抛出去。
 */
class MinimalSessionStore(
    private val context: android.content.Context,
    private val logger: NongyuLogger = NongyuLogger.Noop,
) : SessionStore {

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    /** 惰性打开；失败返回 `null`（由调用方按「没有存储」处理）。 */
    private fun obtain(): android.content.SharedPreferences? {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            return try {
                val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val created = androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    SecurePrefs.FILE_NAME,
                    masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                prefs = created
                created
            } catch (e: GeneralSecurityException) {
                logged("会话存储打开失败，按未登录处理", e)
                null
            } catch (e: java.io.IOException) {
                logged("会话存储打开失败（IO），按未登录处理", e)
                null
            }
        }
    }

    /**
     * 读令牌。
     *
     * **解密失败 = 没有 token，并清空**。这是换设备恢复备份后的必然情形，不是异常情形，
     * 所以这里**不抛异常**（规格 §9 第 3 对第 3 条）。
     */
    override fun readToken(): String? {
        val p = obtain() ?: return null
        return try {
            p.getString(SecurePrefs.KEY_TOKEN, null)
        } catch (e: GeneralSecurityException) {
            logged("令牌解密失败，视为无 token 并清空", e)
            clearQuietly(p)
            null
        } catch (e: RuntimeException) {
            // Tink 在 Key 失效时可能把 AEADBadTagException 包装成 RuntimeException
            logged("令牌解密失败（运行时），视为无 token 并清空", e)
            clearQuietly(p)
            null
        }
    }

    override fun writeToken(token: String) {
        val p = obtain() ?: run {
            logged("会话存储不可用，令牌未写入", null)
            return
        }
        try {
            p.edit().putString(SecurePrefs.KEY_TOKEN, token).apply()
        } catch (e: GeneralSecurityException) {
            logged("令牌加密失败，未写入", e)
        } catch (e: RuntimeException) {
            logged("令牌加密失败（运行时），未写入", e)
        }
    }

    override fun clear() {
        val p = obtain() ?: return
        clearQuietly(p)
    }

    /** 清空且不抛；[readToken] 的容错路径也用它。 */
    private fun clearQuietly(p: android.content.SharedPreferences) {
        try {
            p.edit().remove(SecurePrefs.KEY_TOKEN).apply()
        } catch (e: RuntimeException) {
            logged("清空会话存储失败", e)
        }
    }

    private fun logged(message: String, throwable: Throwable?) {
        logger.log(NongyuLogger.Level.WARN, message, throwable)
    }
}
