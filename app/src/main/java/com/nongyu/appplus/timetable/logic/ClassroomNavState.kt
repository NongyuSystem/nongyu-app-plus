package com.nongyu.appplus.timetable.logic

/**
 * 地点导航面板的**展示态**（纯逻辑，无 Android / Compose 依赖）。
 *
 * 这个 UI 有三种「基本正常」的结果，文案各不相同，混成一句"未找到"
 * 会让用户以为自己的课表错了：
 *
 * | 状态 | 含义 | 该给用户的指引 |
 * |---|---|---|
 * | [Ready] | 查到楼栋且有坐标 | 显示楼栋名/地址，可导航 |
 * | [NoData] | 楼栋已录入但**没有任何数据** | 「尚未录入」——不是用户的错 |
 * | [Unrecognized] | 教室名解析不出楼栋 | 「请核对地点」——用户的输入问题 |
 * | [Failed] | 网络/服务端失败 | 「稍后重试」——系统问题 |
 *
 * ## 为什么"空数据"要单独一类
 *
 * 后端教室表当前可能一条都没录（`classroom_building` 0 行）。
 * 那种情况下 lookup 会返回 `building_not_configured` 或 total=0，
 * 如果统一显示「未找到」，用户会怀疑是不是自己填错了地点，
 * 而真相是**运维还没录数据**。这条边界必须显式区分。
 */
object ClassroomNavState {

    sealed interface State {
        /** 查到楼栋，[hasCoordinate] 决定能不能真的画地图。 */
        data class Ready(
            val displayName: String,
            val addressText: String?,
            val latitude: Double?,
            val longitude: Double?,
            val routesCount: Int,
        ) : State {
            /** 有经纬度才能在地图上定位。 */
            val hasCoordinate: Boolean
                get() = latitude != null && longitude != null
        }

        /** 楼栋已配置但缺坐标 / 服务端没有任何楼栋数据。 */
        data class NoData(val detail: String) : State

        /** 教室名解析不出楼栋。 */
        data class Unrecognized(val input: String) : State

        /** 网络或服务端失败。 */
        data class Failed(val message: String) : State
    }

    /**
     * 生成空数据态的说明文案。
     *
     * @param buildingKey 后端识别出的楼栋编号（可能为 null）
     */
    fun noDataDetail(buildingKey: String?): String =
        if (buildingKey.isNullOrBlank()) {
            "还没有录入教室坐标数据，暂时无法显示地图。"
        } else {
            "楼栋「$buildingKey」已识别，但还没有录入坐标，暂时无法显示地图。"
        }

    /** 认不出教室名时的说明。 */
    fun unrecognizedDetail(input: String): String =
        "无法从「$input」识别出楼栋。\n可以在课程详情里点「地点」改成更规范的名字（如 一教A201）。"
}
