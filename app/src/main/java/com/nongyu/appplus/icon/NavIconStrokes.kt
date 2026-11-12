package com.nongyu.appplus.icon

import com.nongyu.appplus.skin.BuiltInSkin
import com.nongyu.appplus.skin.NavIconKey
import com.nongyu.appplus.skin.ResourcePayload
import com.nongyu.appplus.skin.SkinPackPlanner

/**
 * 把「皮肤包的资源段」与「内置默认资源段」合成为**每个图标一定有值**的线宽表
 * （票 #38，ADR-0005 Q5=A「逐资源回落」）。
 *
 * ## 为什么单独一个纯逻辑文件
 *
 * 回落链有三层（包内值 → 内置默认值 → 硬兜底常量），每层都可能因为「包内值非法」而跳过。
 * 这段判定如果写在 Composable 里就**无法被纯 JVM 单测覆盖**（`LocalSkinSource.current`
 * 是 `@Composable`，单测里调用即编译失败）。抽成纯函数后：
 * - `MainShell` 只负责 `remember` 一次再往下传；
 * - 单测可以直接断言「包内缺一项时其余照用、该项回落」。
 *
 * 本文件**不依赖任何 Android / Compose 运行时**（只用 `@Immutable` 都嫌多余），
 * 全部类型都是数据类，因此在 `:app:testDebugUnitTest` 里可直接执行。
 */
object NavIconStrokes {

    // 线宽合法性判据的唯一真源是 SkinPackPlanner.sanitizeStroke（票 #57 ④）。
    // 这里不再自带 MIN_SCALE / MAX_SCALE 两份阈值，避免与数据层判据漂移。
    // 判据语义见 SkinPackPlanner.sanitizeStroke 的 KDoc：有限且严格落在 (0, 1] 内。

    /**
     * 合成最终线宽表。
     *
     * 逐资源回落（ADR-0005 Q5=A）：每个键**独立**走一遍
     * 「包内合法值 → 内置默认合法值 → [BuiltInSkin.BUILT_IN_ICON_STROKE]」，
     * 因此某一项缺失或非法**只影响它自己**，不会拖累其余图标。
     *
     * @param payload 皮肤包的资源段（可能整段为空 = 没有包）。
     * @param builtIn 内置默认资源段（[com.nongyu.appplus.skin.SkinSource.builtIn] 的 `resources`）。
     * @param keys 需要解析的图标全集，缺省为导航壳的四个 Tab。
     */
    fun resolve(
        payload: ResourcePayload,
        builtIn: ResourcePayload,
        keys: List<NavIconKey> = NavIconKey.BuiltIn,
    ): Map<NavIconKey, Float> = keys.associateWith { key ->
        sanitize(payload.iconStrokes[key])
            ?: sanitize(builtIn.iconStrokes[key])
            ?: BuiltInSkin.BUILT_IN_ICON_STROKE
    }

    /**
     * 只保留**可用**的线宽系数，非法值返回 `null` 交给上层继续回落。
     *
     * 判据的**唯一真源**是 [SkinPackPlanner.sanitizeStroke]（票 #57 ④）：
     * 有限（排除 NaN / Infinity）且严格落在 `(0, 1]` 内。
     * 方向是渲染层依赖数据层，不再是两份复制粘贴的阈值。
     */
    fun sanitize(value: Float?): Float? {
        if (value == null) return null
        return SkinPackPlanner.sanitizeStroke(value)
    }
}
