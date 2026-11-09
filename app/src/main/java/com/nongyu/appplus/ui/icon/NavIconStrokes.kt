package com.nongyu.appplus.ui.icon

import com.nongyu.appplus.skin.BuiltInSkin
import com.nongyu.appplus.skin.NavIconKey
import com.nongyu.appplus.skin.ResourcePayload

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

    /**
     * 线宽系数的合法区间（开区间下界）。
     *
     * 为什么要有上界：系数相对画布最小边，`1.0f` 意味着线宽等于整个图标 —— 一定是坏数据。
     * 为什么下界不能是 `0f`：`strokeWidth = 0` 的 `Stroke` 在 Skia 里画不出任何东西，
     * 图标会**静默消失**，比回落内置默认糟得多。所以非有限值 / ≤0 / >[MAX_SCALE]
     * 一律视为非法 → 回落，而不是"照用坏值"。
     */
    const val MIN_SCALE: Float = 0f

    /** 见 [MIN_SCALE] 的说明。 */
    const val MAX_SCALE: Float = 1f

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
     * 判据：有限（排除 `NaN` / `Infinity`，它们会让所有 `draw*` 得到空绘制）
     * 且严格落在 `(MIN_SCALE, MAX_SCALE]` 内。
     */
    fun sanitize(value: Float?): Float? {
        if (value == null) return null
        if (!value.isFinite()) return null
        if (value <= MIN_SCALE) return null
        if (value > MAX_SCALE) return null
        return value
    }
}
