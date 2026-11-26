package com.nongyu.appplus.net.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nongyu.appplus.net.core.NongyuLogger
import com.nongyu.appplus.profile.logic.ProfileCodec
import com.nongyu.appplus.profile.logic.ProfileLogic
import com.nongyu.appplus.profile.model.ProfileSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 画像存储实现：**DataStore 落盘 + 损坏容错**（ADR-0011 / 票 #112）。
 *
 * 与 [com.nongyu.appplus.net.auth.MinimalSessionStore] 同风格，但更简单：
 * - **不加密**：画像快照不含凭证（令牌只在 SessionStore），明文落盘可接受；
 * - **惰性打开**：构造只存 context；
 * - **读取侧容错**：`IOException`（含 `CorruptionException`）与 `RuntimeException`
 *   一律吞掉并当作无缓存，绝不影响调用方（尤其是启动校验判定）。
 *
 * 写入采取「合并」语义：非空字段覆盖旧值，空字段保留旧值。这样「登录响应先落一次、
 * `/me` 成功再落一次」两份互补数据不会互相抹掉。
 */
class MinimalProfileStore(
    private val context: Context,
    private val logger: NongyuLogger = NongyuLogger.Noop,
) : ProfileStore {

    override fun read(): ProfileSnapshot? = try {
        val raw = runBlocking { context.profileDataStore.data.first()[KEY_PROFILE] }
        ProfileCodec.decode(raw)
    } catch (e: IOException) {
        logged("画像缓存读取失败（IO/文件损坏），按无缓存处理", e)
        null
    } catch (e: RuntimeException) {
        logged("画像缓存读取失败，按无缓存处理", e)
        null
    }

    override fun write(patch: ProfileSnapshot) {
        try {
            runBlocking {
                context.profileDataStore.edit { prefs ->
                    val existing = ProfileCodec.decode(prefs[KEY_PROFILE]) ?: ProfileSnapshot()
                    prefs[KEY_PROFILE] = ProfileCodec.encode(ProfileLogic.mergeForStorage(existing, patch))
                }
            }
        } catch (e: IOException) {
            logged("画像缓存写入失败（IO），不影响启动判定", e)
        } catch (e: RuntimeException) {
            logged("画像缓存写入失败，不影响启动判定", e)
        }
    }

    override fun clear() {
        try {
            runBlocking { context.profileDataStore.edit { it.remove(KEY_PROFILE) } }
        } catch (e: IOException) {
            logged("画像缓存清空失败（IO）", e)
        } catch (e: RuntimeException) {
            logged("画像缓存清空失败", e)
        }
    }

    private fun logged(message: String, throwable: Throwable?) {
        logger.log(NongyuLogger.Level.WARN, message, throwable)
    }

    private companion object {
        val KEY_PROFILE = stringPreferencesKey("profile_snapshot_json")
    }
}

/**
 * DataStore 实例必须全局唯一（同一名字重复创建会抛异常），故放在文件级。
 *
 * [ReplaceFileCorruptionHandler] 是必须的：落盘文件被截断/写坏时，DataStore 读取会抛
 * `CorruptionException`（`IOException` 子类）。用空 prefs 替换是正确取舍 ——
 * 画像丢失可以重取，总比崩溃好，更不能把启动校验拖下水。
 */
private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nongyu_profile",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
