package com.nongyu.appplus

import android.app.Application
import com.nongyu.appplus.map.data.DebugMapDataSource
import com.nongyu.appplus.map.data.MapDataSourceHolder

/**
 * **debug 专用** Application：装载 mock 地图数据。
 *
 * ## 为什么用 Application 子类
 *
 * Kotlin 的源集**不做同名文件覆盖**——`src/main` 与 `src/debug` 各放一份
 * `fun installMapDataSource()` 会直接报 `Conflicting overloads`（已实测）。
 * （Java 可以靠同名类覆盖，Kotlin 的顶层函数不行。）
 *
 * 所以改用：`main` 里只留 [MapDataSourceHolder] 这个可赋值的全局点，
 * debug 变体用自己的 Application 在最早时机把它换成 mock 实现。
 * release 包不挂这个 Application（见 src/debug/AndroidManifest.xml），
 * 所以 mock 代码不会被装配，也不会被引用。
 */
class DebugApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapDataSourceHolder.source = DebugMapDataSource(this)
    }
}
