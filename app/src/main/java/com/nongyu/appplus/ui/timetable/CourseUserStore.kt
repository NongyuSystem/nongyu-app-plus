package com.nongyu.appplus.ui.timetable

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 课表的**用户数据**持久层：地点覆盖、考勤状态、备注。
 *
 * ## 为什么新建而不是复用现有 Store
 * - `MinimalSessionStore` 是「加密会话令牌」专用，写死 token 键、走 Keystore AES/GCM；
 * - `SkinStore` 是 `filesDir` 皮肤包指针的纯判定逻辑。
 * 两者都不是通用 KV，签名也不匹配。但**依赖与容错经验可以直接复用**：
 * 本项目已有 `androidx.datastore.preferences`，不需要引新依赖；
 * `data.first()[key]` 取值、`edit{}` 写入、以及「CorruptionException 是 IOException 子类」
 * 这三条在 `MinimalSessionStore` 里已真机验证过，这里照搬。
 *
 * ## 本期范围（方案已批准）
 * - 备注、考勤、地点覆盖落这里（明文即可，非敏感信息，不套 Keystore）；
 * - 评论走「mock 后端」，**不**存这里（由 `CourseComments` 提供内存态）；
 * - 高德地图与真实后端请求属 Out of scope，只留占位。
 *
 * ## 键的形状
 * 所有键都带 courseId，避免不同课程串数据：
 * - 地点：`loc_<scope>_<courseId>`（scope = once / course / name）
 * - 考勤：`att_<courseId>_<period>`（值 = 是否已考勤）
 * - 备注：`note_<courseId>`
 */
private val Context.courseUserDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "course_user_data",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** 地点修改的作用范围（三档，维护者拍板）。 */
enum class LocationScope {
    /** 只改这一节课（这一个 CourseEntry）。 */
    Once,

    /** 改本课程的所有节次（同名 + 同课程 id 前缀的语义在调用方定）。 */
    Course,

    /** 改所有同名课。 */
    Name,
    ;

    val keyPart: String get() = when (this) {
        Once -> "once"
        Course -> "course"
        Name -> "name"
    }
}

class CourseUserStore(private val context: Context) {

    /** 地点覆盖值（null = 未覆盖，显示原始 room）。 */
    fun locationOverride(courseId: String, scope: LocationScope): Flow<String?> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[stringPreferencesKey("loc_${scope.keyPart}_$courseId")] }

    suspend fun setLocationOverride(courseId: String, scope: LocationScope, value: String) {
        context.courseUserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("loc_${scope.keyPart}_$courseId")] = value
        }
    }

    /** 某节课是否已考勤。null = 无记录，由调用方按「课程是否已结束」给默认值。 */
    fun attendance(courseId: String, period: Int): Flow<Boolean?> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[booleanPreferencesKey("att_${courseId}_$period")] }

    suspend fun setAttendance(courseId: String, period: Int, attended: Boolean) {
        context.courseUserDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("att_${courseId}_$period")] = attended
        }
    }

    /** 备注文本。 */
    fun note(courseId: String): Flow<String> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[stringPreferencesKey("note_$courseId")] ?: "" }

    suspend fun setNote(courseId: String, text: String) {
        context.courseUserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("note_$courseId")] = text
        }
    }

    /** 一次性读取（供 LaunchedEffect 里初始化用）。 */
    suspend fun noteOnce(courseId: String): String =
        runCatching {
            context.courseUserDataStore.data.first()[stringPreferencesKey("note_$courseId")] ?: ""
        }.getOrDefault("")
}
