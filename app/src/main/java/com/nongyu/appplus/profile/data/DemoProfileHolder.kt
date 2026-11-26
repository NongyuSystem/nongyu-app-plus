package com.nongyu.appplus.profile.data

import com.nongyu.appplus.profile.model.ProfileSnapshot

/**
 * demo 画像数据的装配点（`profile/data`）。
 *
 * 与 `map/data/MapDataSourceHolder` 同构：main 侧只持有可空的 [demo]（默认 null），
 * `src/debug` 的 Application 在最早时机把它换成一份假画像；release 包没有任何
 * debug 代码，[demo] 恒为 null，R8 也会把「与 demo 兜底相关的分支」判定为常假并裁掉。
 *
 * [enabled] 是「可关闭开关」：即便在 debug 包里也能一键关掉 demo，看到真实数据链路
 * 的空白态，避免假数据掩盖缓存失败。
 */
object DemoProfileHolder {
    @Volatile
    var demo: ProfileSnapshot? = null

    @Volatile
    var enabled: Boolean = true

    /** 测试/调试用：恢复默认（无 demo）。 */
    fun reset() {
        demo = null
        enabled = true
    }
}
