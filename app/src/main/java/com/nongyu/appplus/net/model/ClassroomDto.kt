package com.nongyu.appplus.net.model

import kotlinx.serialization.Serializable

/**
 * 教室楼栋与导航（后端 `internal/classroom/`，2026-11-23 新增）。
 *
 * ## 这一域解决什么
 * 课表里写的是「一教 A201」这种字符串，学生不知道是哪栋楼、怎么走。
 * 后端把楼栋地址（含高德坐标）、进入路线（多段折线 + 换乘说明）落库，
 * 客户端查出楼栋就能画导航。
 *
 * ## 三条端点都是**匿名可读**
 * 后端 `RegisterPublic`，无 Bearer 也能查。都标 `@PublicEndpoint`。
 *
 * ## 响应形状（每个端点不同，别混用）
 * | 端点 | 形状 |
 * | --- | --- |
 * | `GET /classrooms/lookup?room=` | **平铺对象**：`{input, buildingKey, roomNo, found, building?, reason?}` |
 * | `GET /classrooms/buildings` | `{"items":[...]}` |
 * | `GET /classrooms/buildings/:id` | `{"data":{...}}` |
 */

/**
 * 楼栋（后端 `buildingView`）。
 *
 * `lat` / `lng` 可空：部分楼栋只录了文字地址还没标坐标。
 */
@Serializable
data class ClassroomBuildingDto(
    val id: Long? = null,
    /** 稳定的楼栋键（如 `yijiao`），lookup 返回的 `buildingKey` 与它对应。 */
    val key: String? = null,
    /** 楼号（如 `1`）。 */
    val no: String? = null,
    /** 校区片区（`omitempty`）。 */
    val zone: String? = null,
    /** 展示名（如「第一教学楼」）。 */
    val displayName: String? = null,
    /** 别名列表（`omitempty`），用于匹配「一教」「教一」这类写法。 */
    val aliases: List<String>? = null,
    /** 校区（`omitempty`）。 */
    val campus: String? = null,
    /** 纬度，未标注时为 `null`。 */
    val lat: Double? = null,
    /** 经度，未标注时为 `null`。 */
    val lng: Double? = null,
    /** 文字地址（`omitempty`）。 */
    val addressText: String? = null,
    /** 备注（`omitempty`）。 */
    val note: String? = null,
    val enabled: Boolean? = null,
    /** 进入路线（`omitempty`）：列表接口不返回，详情接口才带。 */
    val routes: List<ClassroomRouteDto>? = null,
)

/**
 * 进入路线（后端 `routeView`）：从某个起点走到这栋楼的多段指引。
 *
 * `points` 是折线（按 `seq` 排序），`startLabel` 是起点描述（如「南门」）。
 */
@Serializable
data class ClassroomRouteDto(
    val id: Long? = null,
    val title: String? = null,
    /** 路线说明（`omitempty`）。 */
    val description: String? = null,
    /** 起点名称（`omitempty`），如「校门口」「地铁站」。 */
    val startLabel: String? = null,
    /** 是否推荐路线。 */
    val recommended: Boolean? = null,
    val sortOrder: Int? = null,
    val enabled: Boolean? = null,
    /** 折线坐标点，**按 seq 升序**。 */
    val points: List<ClassroomRoutePointDto>? = null,
)

/** 路线上的一个坐标点（后端 `pointView`）。`lat`/`lng` 后端是非空数字，这里保持可空以统一口径。 */
@Serializable
data class ClassroomRoutePointDto(
    val seq: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** 该点的说明（`omitempty`），如「此处左转」。 */
    val label: String? = null,
)

/**
 * `GET /classrooms/lookup?room=` 的响应（后端**平铺返回**，不是信封）。
 *
 * ## `reason` 区分了两种「查不到」
 * - `unrecognized`：教室名本身无法识别（如输入乱码）
 * - `building_not_configured`：能识别出楼栋，但该楼还没在后台录入
 *
 * 两者对用户的提示不同：前者让用户检查输入，后者是运维问题。
 */
@Serializable
data class ClassroomLookupDto(
    /** 原始输入（后端 trim 后）。 */
    val input: String? = null,
    /** 解析出的楼栋键；为空说明没识别出楼栋。 */
    val buildingKey: String? = null,
    /** 解析出的房间号。 */
    val roomNo: String? = null,
    /** 是否查到了楼栋详情。 */
    val found: Boolean? = null,
    /** 查到时的楼栋详情（含 routes）。 */
    val building: ClassroomBuildingDto? = null,
    /** 查不到时的原因：`unrecognized` / `building_not_configured`。 */
    val reason: String? = null,
) {
    /** 教室名无法识别。 */
    fun isUnrecognized(): Boolean = reason == REASON_UNRECOGNIZED

    /** 楼栋已识别但后台未录入 —— 属运维问题，不是用户输错。 */
    fun isBuildingNotConfigured(): Boolean = reason == REASON_BUILDING_NOT_CONFIGURED

    companion object {
        const val REASON_UNRECOGNIZED = "unrecognized"
        const val REASON_BUILDING_NOT_CONFIGURED = "building_not_configured"
    }
}

/** `GET /classrooms/buildings` 的响应：`{"items":[...]}`。 */
@Serializable
data class ClassroomBuildingListDto(
    val items: List<ClassroomBuildingDto>? = null,
)

/** `GET /classrooms/buildings/:id` 的响应：`{"data":{...}}`。 */
@Serializable
data class ClassroomBuildingEnvelopeDto(
    val data: ClassroomBuildingDto? = null,
)

/**
 * `POST /keys/amap/acquire` 的响应（后端 `internal/apikey/handler.go`）。
 *
 * ## ⚠️ `key` 是**明文高德 Key**，一次一用
 * 后端按 QPS 与启用状态从密钥池里挑一个下发。客户端：
 * - **不要持久化**（落盘就等于把池里的钥匙长期泄露到设备上）
 * - **不要打印进日志**
 * - 拿到后直接用于高德 SDK/Web 请求
 *
 * 请求体 `{"platform":"amap"}`（或缺省时用 query `platform`）。
 */
@Serializable
data class AmapKeyDto(
    val platform: String? = null,
    /** 明文 Key。**不落盘、不打日志。** */
    val key: String? = null,
    /** 给人看的提示（如剩余配额说明）。 */
    val hint: String? = null,
)

/**
 * `POST /keys/amap/acquire` 的请求体。
 *
 * ## ⚠️ `platform` 不给默认值，必须显式传
 * `NetJson` 用的是 `explicitNulls = false`，它**只影响 null，不影响默认值**；
 * 但 kotlinx.serialization 的 `encodeDefaults` 默认是 `false` ——
 * **带默认值的字段不会被序列化**。
 *
 * 所以若写成 `platform: String? = "amap"`，不显式传时序列化结果是 `{}`，
 * 后端只能靠 query 兜底（它确实兜了，但请求体就失去意义了）。
 * 这里保持无默认值，强制调用方写明平台。
 */
@Serializable
data class AmapKeyRequest(
    val platform: String,
)
