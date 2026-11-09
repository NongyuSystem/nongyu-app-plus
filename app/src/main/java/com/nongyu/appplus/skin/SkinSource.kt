package com.nongyu.appplus.skin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.nongyu.appplus.ui.theme.NongyuColors

/**
 * **皮肤数据源接口 —— 本工程的唯一主接缝**（规格 #33 Implementation Decisions）。
 *
 * 一句话：界面渲染只认这一个接口，测试也只在这一层替换实现，不碰 UI。
 *
 * ## 为什么是「一个接口」而不是「两个接口」
 *
 * 色值段（数据库目录接口，ADR-0004）与资源段（皮肤包，ADR-0005）虽然
 * **来源、版本、校验方式全部不同**，但都是从「皮肤」这一个概念里出去的视觉数据。
 * 合在一个接口上，调用方（主题层 / 导航壳）只需要一个依赖，
 * 测试也只需要替身一处 —— 这就是规格说的「测试只需替换一处实现」。
 * 两段的独立性由 [SkinResolution] 的两个**各自可空**的字段表达，
 * 而不是靠拆成两个接口：拆开会把「谁先就绪」的问题漏给调用方。
 *
 * ## 本接口刻意不做什么
 *
 * - **不碰网络、磁盘、校验**（那是 T3 / T4）：本票只定形状。
 * - **不抛异常**：所有失败都以 [SkinResolution] 里的 `meta` 表达，
 *   渲染永远拿得到内置默认（ADR-0006 Q3=B「静默回落内置默认 + 记日志」）。
 * - **不做三层降级的判定**（那是 T3 / T4）：本接口只保证结果形状**能**表达
 *   「整段回落 / 逐项回落 / 照用」三种形态。
 *
 * ## 实现者约定
 *
 * 实现 **必须**返回非空 [SkinResolution]，且在拿不到数据时把对应字段置 `null`
 * 并在 `meta` 里写明 [PayloadOrigin] 与原因 —— **不得**抛异常、
 * **不得**返回一个把内置默认伪装成 `Loaded` 的结果（那会让日志失去意义）。
 */
interface SkinSource {

    /**
     * 取当前生效的皮肤数据。
     *
     * 约定是**同步**的：ADR-0006 要求冷启动同步读本地、首帧即正确皮肤，
     * 所以这不是挂起函数。把网络 / 下载放在首帧之后的实现，
     * 应当返回「本地已就绪的那一份」，而不是让调用方等。
     */
    fun resolve(): SkinResolution

    /**
     * 内置默认皮肤（永不删除、最终真源）。
     *
     * 放在接口上而不是做成全局单例，是为了让替身实现能同一个「自洽的默认」一起被替换 ——
     * 测试断言「回落到谁」时不必依赖真实常量。
     */
    val builtIn: BuiltInSkin

    companion object {
        /**
         * 内置默认实现：**本期唯一实现**。
         *
         * 两段都是 `null`（没有任何外部载荷），因此 [resolve] 的结果完全来自内置默认。
         * 这也是 T2 之前工程的实际状态 —— 视觉与换接口之前逐像素一致。
         */
        val BuiltIn: SkinSource = BuiltInSkinSource
    }
}

/**
 * 当前皮肤数据源。Composable 侧唯一入口，测试在这里 provide 替身。
 *
 * 用 `staticCompositionLocalOf`：皮肤只在冷启动定一次、本期无运行时换肤入口，
 * 与 ADR-0004 Q3=B 对 `LocalNongyuColors` 的口径一致（值几乎不会变 → 用 static）。
 * 代价同样记在 ADR-0004：换值会让提供处的整块 content 重组，这正是 ADR-0006 不允许跳色的原因。
 *
 * 有安全默认 [SkinSource.BuiltIn]，`@Preview` 不 Provide 也能渲染。
 */
val LocalSkinSource = staticCompositionLocalOf<SkinSource> { SkinSource.BuiltIn }

/**
 * 内置默认皮肤实现（[SkinSource] 的本期唯一实现）。
 *
 * 它**没有**任何 IO：色板是编译期常量 [NongyuColors.Green]，
 * 图标线宽是 `MainShell.drawNavIcon` 里既有的 `0.085f`，字体是系统默认。
 * 因此它天然满足 ADR-0006 的「冷启动只读本地、不碰网络」——
 * 因为什么都不读。
 */
object BuiltInSkinSource : SkinSource {

    override val builtIn: BuiltInSkin = BuiltInSkin(
        colors = NongyuColors.Green,
        resources = ResourcePayload(
            iconStrokes = NavIconKey.BuiltIn.associateWith { BuiltInSkin.BUILT_IN_ICON_STROKE },
            font = SkinFont.SystemDefault,
            meta = PayloadMeta.loaded(version = null),
        ),
    )

    /**
     * 内置实现永远返回内置默认：两段载荷为 `null`，
     * 元信息写明 `Absent`，让日志能区分「这是内置默认」与「外部载荷生效了」。
     */
    override fun resolve(): SkinResolution = SkinResolution(
        colors = null,
        resources = null,
        builtIn = builtIn,
    )
}
