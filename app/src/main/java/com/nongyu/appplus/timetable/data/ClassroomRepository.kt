package com.nongyu.appplus.timetable.data

import android.content.Context
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.model.ClassroomBuildingDto
import com.nongyu.appplus.net.model.ClassroomLookupDto

/**
 * 教室楼栋与导航的**数据源**（后端 `internal/classroom/`）。
 *
 * ## 后端契约（2026-11-23）
 *
 * ```
 * GET  /classrooms/lookup?room=         按教室名反查楼栋（含 reason 说明为何查不到）
 * GET  /classrooms/buildings            全部楼栋
 * GET  /classrooms/buildings/{id}       楼栋详情 + 路线
 * POST /keys/amap/acquire               从密钥池取高德 Key
 * ```
 * 四条都需 Bearer（后端 `e934bb2` 把它们移出了 publicReadablePaths）。
 *
 * ## 为什么 lookup 的三种结果要分开
 *
 * 后端把"查不到"分成两类，**给用户的指引完全不同**：
 * - `unrecognized`：教室名没解析出楼栋 → 提示用户核对地点
 * - `building_not_configured`：楼栋认出来了但没录坐标 → 提示"还没录入，请联系管理员"
 *
 * 混成一句"未找到"会让用户以为是自己的课表错了。
 */
class ClassroomRepository(private val context: Context) {

    /**
     * 一次地点查询的结果。
     *
     * 用密封类而不是「可空 DTO + 布尔标志」：三种情况的 UI 文案完全不同，
     * 让编译器强制调用方处理全部分支，避免漏掉"楼栋未录入"这种中间态。
     */
    sealed interface LookupResult {
        /** 查到了，[building] 带坐标。 */
        data class Found(val building: ClassroomBuildingDto) : LookupResult

        /** 教室名认不出楼栋。 */
        data class Unrecognized(val input: String) : LookupResult

        /** 楼栋认出来了，但还没录坐标。 */
        data class BuildingNotConfigured(val buildingKey: String?, val roomNo: String?) : LookupResult

        /** 网络/服务端失败。 */
        data class Failed(val message: String) : LookupResult
    }

    /**
     * 按教室名反查楼栋。
     *
     * @param room 教室名（课程卡上的地点原文，如 `10-B201室`）
     */
    suspend fun lookup(room: String): LookupResult = runCatching {
        val resp = NetHolder.classroom(context).lookup(room)
        if (!resp.isSuccessful) {
            return LookupResult.Failed("查询失败：HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return LookupResult.Failed("查询失败：响应体为空")
        toResult(room, body)
    }.getOrElse { e ->
        LookupResult.Failed(e.message ?: "查询失败")
    }

    /** 全部已录入楼栋。 */
    suspend fun buildings(): Result<List<ClassroomBuildingDto>> = runCatching {
        val resp = NetHolder.classroom(context).buildings()
        if (!resp.isSuccessful) error("加载楼栋失败：HTTP ${resp.code()}")
        resp.body()?.items.orEmpty()
    }

    /**
     * 取一个高德 Key。
     *
     * ⚠️ 返回值是**明文 Key**：调用方不要落盘、不要打日志。
     * 目前用于将来接高德 SDK；空态阶段暂不调用。
     */
    suspend fun acquireAmapKey(platform: String = "amap"): Result<String> = runCatching {
        val resp = NetHolder.apiKeys(context).acquireAmap(
            com.nongyu.appplus.net.model.AmapKeyRequest(platform = platform),
        )
        if (!resp.isSuccessful) error("取高德 Key 失败：HTTP ${resp.code()}")
        resp.body()?.key?.takeIf { it.isNotBlank() } ?: error("返回的 Key 为空")
    }

    /** DTO → 密封类。抽成纯函数便于单测（不碰网络）。 */
    private fun toResult(input: String, body: ClassroomLookupDto): LookupResult {
        if (body.found == true && body.building != null) {
            return LookupResult.Found(body.building)
        }
        return when (body.reason) {
            ClassroomLookupDto.REASON_BUILDING_NOT_CONFIGURED ->
                LookupResult.BuildingNotConfigured(body.buildingKey, body.roomNo)
            // unrecognized 或未知 reason 都按"认不出"处理：
            // 宁可让用户核对地点，也不要凭空猜一个楼栋。
            else -> LookupResult.Unrecognized(body.input ?: input)
        }
    }
}
