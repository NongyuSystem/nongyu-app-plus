package com.nongyu.appplus.timetable.data

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
import com.nongyu.appplus.timetable.model.CourseEntry

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

    /**
     * 某天这次课是否已考勤。`null` = 无记录。
     *
     * ## 为什么键里必须带日期（2026-11-23 修正）
     *
     * 原键是 `att_<courseId>_<period>` —— **没有日期**。
     * 一门 1-16 周的课每周都上，但键只有「课程 + 节次」，
     * 于是第 1 周打过的考勤会让第 2 周自动变成已考勤，一学期实际只能记一次。
     *
     * 现在改为 `att_<courseId>_<ISO日期>`，按「哪一次课」独立记录。
     * 学期统计（总课次 / 已上 / 剩余）由 `AttendanceStats` 按日期推导，不在存储里另存计数。
     *
     * ⚠️ 旧键不做迁移：老记录没有日期，无法判断属于哪一周，强行猜会写错数据。
     *
     * @param isoDate 形如 `2026-11-23`（`LocalDate.toString()` 的格式）
     */
    fun attendance(courseId: String, isoDate: String): Flow<Boolean?> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[booleanPreferencesKey("att_${courseId}_${isoDate}")] }

    suspend fun setAttendance(courseId: String, isoDate: String, attended: Boolean) {
        context.courseUserDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("att_${courseId}_${isoDate}")] = attended
        }
    }

    /**
     * 一次性读取某课程**所有**考勤记录（日期 → 是否已上）。
     *
     * 学期统计要按日期遍历，逐日收集 Flow 太碎，这里给一个整表读取。
     */
    suspend fun attendanceAll(courseId: String): Map<String, Boolean> =
        runCatching {
            val prefs = context.courseUserDataStore.data.first()
            val prefix = "att_${courseId}_"
            prefs.asMap()
                .filterKeys { it.name.startsWith(prefix) }
                .mapNotNull { (key, value) ->
                    val date = key.name.removePrefix(prefix)
                    val on = value as? Boolean ?: return@mapNotNull null
                    date to on
                }
                .toMap()
        }.getOrDefault(emptyMap())

    /** 写入某天考勤（供批量勾选）。 */
    suspend fun setAttendanceBatch(courseId: String, attendance: Map<String, Boolean>) {
        context.courseUserDataStore.edit { prefs ->
            attendance.forEach { (date, on) ->
                prefs[booleanPreferencesKey("att_${courseId}_${date}")] = on
            }
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

    // ---- 四态/编辑扩展（维护者 2026-11-10 批准方案）----

    /** 已删除的课程 id 集合（逗号分隔存储）。删除「这天的这节课」用。 */
    fun deletedIds(): Flow<Set<String>> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                (prefs[stringPreferencesKey("deleted_ids")] ?: "")
                    .split(",").filter { it.isNotBlank() }.toSet()
            }

    suspend fun setDeletedIds(ids: Set<String>) {
        context.courseUserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("deleted_ids")] = ids.sorted().joinToString(",")
        }
    }

    /**
     * 周次复杂态的「例外天（不上）」：ISO 日期字符串集合（逗号分隔）。
     * 语义：默认按固定周上课，这些天不上。
     */
    fun exceptionDays(courseId: String): Flow<Set<String>> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                (prefs[stringPreferencesKey("exc_$courseId")] ?: "")
                    .split(",").filter { it.isNotBlank() }.toSet()
            }

    suspend fun setExceptionDays(courseId: String, days: Set<String>) {
        context.courseUserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("exc_$courseId")] = days.sorted().joinToString(",")
        }
    }

    /** 教师覆盖值（行内输入，null = 用原始 teacher）。 */
    fun teacherOverride(courseId: String): Flow<String?> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[stringPreferencesKey("teacher_$courseId")] }

    suspend fun setTeacherOverride(courseId: String, value: String) {
        context.courseUserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("teacher_$courseId")] = value
        }
    }

    /**
     * 按天生效的地点覆盖：key = `locday_<courseId>_<ISO日期>`。
     * 地点编辑用日历选「生效天」后，只对选中的天生效。
     */
    fun locationForDay(courseId: String, isoDate: String): Flow<String?> =
        context.courseUserDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[stringPreferencesKey("locday_${courseId}_$isoDate")] }

    suspend fun setLocationForDays(courseId: String, days: Set<String>, value: String) {
        context.courseUserDataStore.edit { prefs ->
            days.forEach { d ->
                prefs[stringPreferencesKey("locday_${courseId}_$d")] = value
            }
        }
    }

    /** 一次性读取（供 LaunchedEffect 里初始化用）。 */
    suspend fun noteOnce(courseId: String): String =
        runCatching {
            context.courseUserDataStore.data.first()[stringPreferencesKey("note_$courseId")] ?: ""
        }.getOrDefault("")
}
