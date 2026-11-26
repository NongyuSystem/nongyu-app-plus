package com.nongyu.appplus.map.data

/**
 * 地图数据注入点（**测试用**，生产恒为 [DefaultMapDataSource]）。
 *
 * ## 为什么要这个接口
 *
 * 后端目前的教室底图与标注都还没有数据（`classroom_map` 未上传、标注为空），
 * 真机上看不到任何东西，无法验证「上中下三段布局 + 底图 + 标注 + 跳转」这条链路。
 *
 * 但**不能把 mock 数据写进 `main`**：那会让发布包带上假地址，
 * 用户看到 "10-B201室" 指到一片虚构的校园图。所以：
 *
 * - 接口与默认实现放 `main`（release 只有这一份，行为不变）
 * - mock 实现放 `src/debug`，只进 debug 包
 *
 * 装配处（[MapRepository] / `MapAnnotationStore`）按接口取数，
 * 这样替换只需改 `debug` 源的工厂，`main` 里一行都不用动。
 */
interface MapDataSource {

    /**
     * 底图元数据 + 图片文件路径。
     *
     * @return 成功时返回 (元数据, 图片绝对路径)；无图/失败返回 null
     */
    suspend fun basemap(campus: String): BasemapData?

    /** 某个校区的标注文档 JSON（`MapAnnotationJson` 契约）。空串表示没有标注。 */
    suspend fun annotationsJson(campus: String): String

    /**
     * 兜底经纬度（**仅 mock 用**）。
     *
     * 后端 `classroom_building` 尚未录入楼栋时，真实链路查不到坐标，
     * 「跳转并导航」就永远走不通。debug 注入点可在这里给一组坐标，
     * 让导航链路能被完整验证。
     *
     * 生产实现 [DefaultMapDataSource] 返回 null，**不会**覆盖真实查询结果。
     */
    suspend fun fallbackLatLng(campus: String): Pair<Double, Double>? = null

    /**
     * 底图数据。
     *
     * @param width / height 图片像素尺寸（标注坐标就落在这个像素系里）
     * @param imagePath 本地文件绝对路径（可直接交给 BitmapFactory 解码）
     */
    data class BasemapData(
        val width: Int,
        val height: Int,
        val imagePath: String,
        val calibrated: Boolean = false,
    )
}

/**
 * 默认实现：**什么都不提供**。
 *
 * 真实取数仍由 [MapRepository] / [MapAnnotationStore] 走网络与 DataStore；
 * 本类只作为「没有注入」时的安全兜底，保证 UI 拿到 null/空串而不是崩溃。
 */
object DefaultMapDataSource : MapDataSource {
    override suspend fun basemap(campus: String): MapDataSource.BasemapData? = null
    override suspend fun annotationsJson(campus: String): String = ""
}

/**
 * 全局注入点。
 *
 * 默认 [DefaultMapDataSource]；debug 包的 `Application`（或任何早期入口）
 * 可把它换成 mock 实现。用 `@Volatile` 是因为可能在主线程赋值、
 * 在 IO 线程读取。
 */
object MapDataSourceHolder {
    @Volatile
    var source: MapDataSource = DefaultMapDataSource

    /** 测试用：恢复默认实现。 */
    fun reset() {
        source = DefaultMapDataSource
    }
}
