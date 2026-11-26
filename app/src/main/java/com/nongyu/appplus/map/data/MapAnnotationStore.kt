package com.nongyu.appplus.map.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nongyu.appplus.map.logic.MapAnnotationJson
import com.nongyu.appplus.map.model.MapAnnotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 标注持久层：DataStore 存 JSON，按 campus 分区。
 *
 * ## 序列化统一走 [MapAnnotationJson]（2026-11-26 重构）
 *
 * 之前本类自己维护一套私有 DTO（`StoredAnnotation` 把 x/y 拆成两个并行数组），
 * 与 `MapAnnotationJson` 的对外契约各说各话。现在两者共用 `MapDoc` 文档模型，
 * 存储内容与对外契约**字节级一致**，少一层转换就少一类错位风险。
 *
 * 尤其是 x[]/y[] 并行数组：两条长度不等时旧实现会补 0，
 * 点坐标悄悄变了却不报错——这类静默数据损坏比崩溃更难查。
 *
 * 容错沿用 `CourseUserStore` 已真机验证过的三件套：
 * `ReplaceFileCorruptionHandler` + `catch { IOException -> emptyPreferences }` + `edit{}`。
 *
 * 明文存储即可（标注不是敏感信息），不套 Keystore。
 */
private val Context.mapAnnotationStore: DataStore<Preferences> by preferencesDataStore(
    name = "map_annotations",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class MapAnnotationStore(private val context: Context) {

    /** 读某个校区的全部标注。 */
    fun annotations(campus: String): Flow<List<MapAnnotation>> =
        context.mapAnnotationStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(campus, prefs[stringKey(campus)]) }

    /** 一次性读取（供编辑页进入时取快照）。 */
    suspend fun load(campus: String): List<MapAnnotation> =
        annotations(campus).first()

    /**
     * 读原始文档 JSON（给只读地图组件用）。
     *
     * 地图组件要的是"直接喂给 [com.nongyu.appplus.map.ui.MapEditor] 的 JSON"，
     * 刻意不在 UI 层做 encode/decode 往返——那既浪费又可能掩盖数据问题。
     */
    suspend fun documentJson(campus: String): String =
        context.mapAnnotationStore.data.first()[stringKey(campus)].orEmpty()

    /** 覆盖某个校区的全部标注。 */
    suspend fun save(campus: String, annotations: List<MapAnnotation>) {
        val json = MapAnnotationJson.encode(campus, annotations)
        context.mapAnnotationStore.edit { prefs -> prefs[stringKey(campus)] = json }
    }

    /** 清空某校区标注。 */
    suspend fun clear(campus: String) {
        context.mapAnnotationStore.edit { prefs -> prefs.remove(stringKey(campus)) }
    }

    private fun decode(campus: String, raw: String?): List<MapAnnotation> =
        MapAnnotationJson.parse(raw, campus)

    private fun stringKey(campus: String) = stringPreferencesKey("anno_$campus")
}
