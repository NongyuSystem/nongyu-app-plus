package com.nongyu.appplus.net.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nongyu.appplus.net.core.NongyuLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 会话存储实现：**DataStore（落盘）+ 自管 Keystore（加密）**。
 *
 * ## 为什么不再用 EncryptedSharedPreferences
 * `androidx.security:security-crypto` 1.1.0 把 `EncryptedSharedPreferences` 与 `MasterKey`
 * 整体标为 `@Deprecated`，且**库内没有替代 API**（已核对 1.1.0 的 aar 类清单）。
 * 官方口径是改用 DataStore + 自己管 Keystore。本实现即按此重写。
 *
 * ## 三条硬约束（前两条与旧实现一致，第三条是 DataStore 特有的，不得退化）
 *
 * ### 1. 惰性打开
 * 构造时**只保存 context**，首次 [readToken] / [writeToken] / [clear] 才真正碰 Keystore 与磁盘。
 * Keystore 初始化是磁盘 + IPC，不能在启动路径上跑（`docs/adr/0006`）。
 *
 * ### 2. 读取侧容错（**这条不做会崩**）
 * 备份恢复 / 换设备后，密文带过来了但 Keystore 里的 Key 不在，解密必然失败。
 * 这是**必然发生的情形**，不是异常。所以 [readToken] 必须把解密失败当作
 * 「没有 token」并清空，不得抛出去。
 *
 * ### 3. DataStore 文件损坏也必须容错（真机实测踩到）
 * DataStore 的落盘文件被截断 / 写坏时，读取会抛
 * `androidx.datastore.core.CorruptionException`（**它是 `IOException` 的子类**，
 * 不属于 `RuntimeException`，所以早期只 catch `RuntimeException` 的写法会漏掉它，
 * 造成崩溃）。两层防护：
 * - DataStore 侧挂 [ReplaceFileCorruptionHandler]：文件坏掉时用空 prefs 替换；
 * - [readToken] 侧再 catch `IOException`：即使 handler 之外还有别的 IO 故障也不冒泡。
 * 真机上正是这条被 instrumented 测试抓出来的 —— 只靠编译和纯 JVM 测试发现不了。
 *
 * ## 存储格式
 * DataStore 里存的是**base64(iv + ciphertext)** 的密文，不是明文令牌。
 * 加密算法 AES/GCM/NoPadding，IV 每次随机 12 字节，与密文拼在一起存。
 */
class MinimalSessionStore(
    private val context: android.content.Context,
    private val logger: NongyuLogger = NongyuLogger.Noop,
) : SessionStore {

    @Volatile
    private var opened = false

    /** 惰性打开：首次真正用到时才标记，失败也不抛（由调用方按「没有存储」处理）。 */
    private fun ensureReady(): Boolean {
        if (opened) return true
        synchronized(this) {
            if (opened) return true
            return try {
                // 触发一次 Keystore 探活：能取到/建出密钥才算可用。
                obtainKey()
                opened = true
                true
            } catch (e: GeneralSecurityException) {
                logged("会话存储打开失败，按未登录处理", e)
                false
            } catch (e: IOException) {
                logged("会话存储打开失败（IO），按未登录处理", e)
                false
            }
        }
    }

    override fun readToken(): String? {
        if (!ensureReady()) return null
        return try {
            val key = stringPreferencesKey(SecurePrefs.KEY_TOKEN)
            // 用 DataStore 的标准取值：flow.first() 再按 String 显式取值。
            val stored: String? = runBlocking {
                context.secureSessionDataStore.data.first()[key]
            }
            if (stored == null) return null
            decrypt(stored)
        } catch (e: GeneralSecurityException) {
            logged("令牌解密失败，视为无 token 并清空", e)
            clearQuietly()
            null
        } catch (e: IOException) {
            // 磁盘读失败 / CorruptionException（IOException 子类）都在这里兜住。
            // 真机实测踩过：只 catch RuntimeException 会漏掉 CorruptionException 导致崩溃。
            logged("会话存储读取失败（IO/文件损坏），视为无 token", e)
            clearQuietly()
            null
        } catch (e: RuntimeException) {
            // 密钥失效时底层可能把 AEADBadTagException 包装成 RuntimeException
            logged("令牌解密失败（运行时），视为无 token 并清空", e)
            clearQuietly()
            null
        }
    }

    override fun writeToken(token: String) {
        if (!ensureReady()) {
            logged("会话存储不可用，令牌未写入", null)
            return
        }
        try {
            val encrypted = encrypt(token)
            runBlocking {
                context.secureSessionDataStore.edit { prefs ->
                    prefs[stringPreferencesKey(SecurePrefs.KEY_TOKEN)] = encrypted
                }
            }
        } catch (e: GeneralSecurityException) {
            logged("令牌加密失败，未写入", e)
        } catch (e: IOException) {
            logged("令牌写入失败（IO），未写入", e)
        } catch (e: RuntimeException) {
            logged("令牌加密失败（运行时），未写入", e)
        }
    }

    override fun clear() {
        if (!ensureReady()) return
        clearQuietly()
    }

    /** 清空且不抛；[readToken] 的容错路径也用它。 */
    private fun clearQuietly() {
        try {
            runBlocking {
                context.secureSessionDataStore.edit { prefs ->
                    prefs.remove(stringPreferencesKey(SecurePrefs.KEY_TOKEN))
                }
            }
        } catch (e: IOException) {
            logged("清空会话存储失败（IO）", e)
        } catch (e: RuntimeException) {
            logged("清空会话存储失败", e)
        }
    }

    // ---------------- 加密层 ----------------

    /** 取（或首次创建）Keystore 里的 AES 密钥。 */
    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(SecurePrefs.KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                SecurePrefs.KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    /** 加密并返回 base64(iv + ciphertext)。 */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val packed = TokenCipherLayout.pack(cipher.iv, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /** 解密 base64(iv + ciphertext)；失败抛 `GeneralSecurityException`（由调用方兜住）。 */
    private fun decrypt(stored: String): String {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val (iv, body) = TokenCipherLayout.unpack(raw)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TokenCipherLayout.GCM_TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun logged(message: String, throwable: Throwable?) {
        logger.log(NongyuLogger.Level.WARN, message, throwable)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * DataStore 实例必须全局唯一（同一名字重复创建会抛异常），故放在文件级。
 *
 * [ReplaceFileCorruptionHandler] 是**必须的**：落盘文件被截断/写坏时，
 * DataStore 读取会抛 `CorruptionException`（`IOException` 子类）。没有 handler 时
 * 这个异常会一路冒到调用方。会话令牌属于「丢了就重新登录」的数据，
 * 用空 prefs 替换是正确取舍 —— 总比崩溃好。
 */
private val Context.secureSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SecurePrefs.DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
