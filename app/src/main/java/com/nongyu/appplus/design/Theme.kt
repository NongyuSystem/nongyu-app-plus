package com.nongyu.appplus.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.nongyu.appplus.skin.SkinResolution
import com.nongyu.appplus.skin.SkinSource

/**
 * 农屿主题层（ADR-0002）。
 *
 * `MaterialTheme.colorScheme` 固定 48 个槽位、装不下品牌浅色 / 玻璃 / AI 圆专色，
 * 因此自建 [NongyuColors] 经 [LocalNongyuColors] 下发，作为配色的唯一真源；
 * `MaterialTheme` 只保留一层投影，供 Material3 组件取色。
 *
 * 本期只提供白绿一套、固定浅色。色板来自**皮肤数据源接口** `SkinSource`
 * （唯一主接缝，见 `skin/SkinSource.kt`）：本期实现只有内置默认
 * （[SkinSource.BuiltIn] → [NongyuColors.Green]），后期数据库目录接口就绪后
 * 替换实现即可，Composable 层零改动。
 */


/**
 * 当前色板。用 [staticCompositionLocalOf]（ADR-0004 Q3=B）：本期色板只在冷启动定一次，
 * 正合官方「值几乎不会变就用 static」的口径。代价：换值会让整块 content 重组 ——
 * 这正是 ADR-0006「不允许首帧后跳色」的原因。
 *
 * 有安全默认值 [NongyuColors.Green]，@Preview 不 Provide 也能渲染。
 */
val LocalNongyuColors = staticCompositionLocalOf { NongyuColors.Green }

/** 组件侧统一从这里取色，组件代码不直接写十六进制。 */
object NongyuTheme {
    val colors: NongyuColors
        @Composable get() = LocalNongyuColors.current
}

/**
 * 由色板派生出 Material3 的 ColorScheme —— 只是**投影**，不是真源。
 * M3 组件默认只读它，自定义色（星云、玻璃等）必须手动传给组件参数。
 */
private fun NongyuColors.toColorScheme() = lightColorScheme(
    primary = brand.colorBrand,
    onPrimary = brand.colorOnBrand,
    primaryContainer = brand.colorBrandMuted,
    background = surface.colorBackground,
    onBackground = surface.colorText,
    surface = surface.colorCard,
    onSurface = surface.colorText,
    surfaceVariant = brand.colorBrandMuted,
    onSurfaceVariant = surface.colorTextSecondary,
    outline = surface.colorOutline,
)

/**
 * 应用主题。
 *
 * 本期固定浅色：移除 `darkTheme` 参数并关闭动态取色，
 * 避免系统暗色 / Android 12+ 动态色把白绿品牌色冲掉（ADR-0002）。
 * 暗色以后作为另一份 [NongyuColors] 加入即可。
 *
 * [source] 是皮肤数据源（唯一主接缝）。色板缺省取自它的内置默认皮肤，
 * 所以不传 [colors] 时渲染结果与换接口之前完全一致；
 * 外部色值段就绪后由 T5 在启动路径上 `resolve()` 后经 [colors] 传入。
 *
 * @param colors 显式指定色板；`null`（缺省）= 用 [resolution] 或 [source] 的解析结果。
 * @param source 皮肤数据源；缺省为 [SkinSource.BuiltIn]。
 * @param resolution **启动路径上已解析好的**皮肤结果。传入后不再重复解析
 *   （ADR-0006：解析只发生一次）；不传则回落到内置默认。
 */
@Composable
fun NongyuAppPlusTheme(
    colors: NongyuColors? = null,
    source: SkinSource = SkinSource.BuiltIn,
    resolution: SkinResolution? = null,
    content: @Composable () -> Unit,
) {
    // 已解析结果优先；没传就造一个「全回落」的结果，语义等价于内置默认。
    val effective = resolution ?: SkinResolution(null, null, source.builtIn)
    val resolved = colors ?: effective.palette()
    val colorScheme = remember(resolved) { resolved.toColorScheme() }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalNongyuColors provides resolved,
        com.nongyu.appplus.skin.LocalSkinSource provides source,
        // 下发同一个对象，UI 层只读不解析（避免冷启动重复磁盘读）。
        com.nongyu.appplus.skin.LocalSkinResolution provides effective,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
