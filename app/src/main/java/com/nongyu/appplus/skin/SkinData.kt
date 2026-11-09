package com.nongyu.appplus.skin

import com.nongyu.appplus.ui.theme.NongyuColors

/**
 * 皮肤数据源（唯一主接缝）的**数据载体**。
 *
 * 这里只定义「接口能供出什么形状」，不定义「怎么取」（取用逻辑在 [SkinSource]，
 * 解析 / 校验 / 落盘在 T3、T4）。三条 ADR 约束直接体现在类型上：
 *
 * 1. **两段载荷各自独立缺失**（ADR-0006 Q2=C）：色值段与资源段是两个独立的可空对象，
 *    不打包成「一套皮肤必须齐全」的整体。一套新配色配一套旧图标是**合法状态**，不是错误。
 * 2. **回落必须能解释**（ADR-0006「日志必须写清是哪一环失败」）：每个载荷都带
 *    [PayloadMeta]，说清拿到的是哪一份、为什么退回了内置默认。
 * 3. **不抛异常**（ADR-0006 Q3=B「静默回落内置默认 + 记日志」）：
 *    [SkinResolution] 永远持有可渲染的内置默认皮肤，没有「失败」这个状态。
 */

/**
 * 一个载荷段（色值段或资源段）从哪来 —— 用于日志区分「用上了」还是「回落了」。
 *
 * 枚举而不是布尔，是为了让日志能直接说出 T5 要求的那四种成因
 * （无包 / 校验失败 / 格式版本过高 / 最低 App 版本过高）。
 */
enum class PayloadOrigin {
    /** 本机已校验的皮肤包 / 目录接口数据，成功采用。 */
    Loaded,

    /** 没有任何可用的载荷（无包、无缓存、接口无数据）。 */
    Absent,

    /** 载荷存在但校验不通过（sha256 / 整包签名）。 */
    IntegrityFailed,

    /** 载荷的格式主版本高于客户端支持 → 整包拒绝（ADR-0003 第①层）。 */
    SchemaTooNew,

    /** 载荷声明的最低 App 版本高于当前 App → 拒绝（ADR-0003）。 */
    AppVersionTooLow,

    /** 载荷存在且版本可读，但内容被判非法（如核心 token 缺失，ADR-0004）。 */
    Invalid,
    ;

    /** 是否成功采用了外部载荷（而不是回落内置默认）。 */
    val isLoaded: Boolean get() = this == Loaded
}

/**
 * 一次载荷解析的元信息：供日志用，不参与渲染。
 *
 * 刻意做成「值对象」而不是日志回调：数据源不应依赖 Android `Log`，
 * 这样它在纯 JVM 单测里可测（本票的测试要求）。
 *
 * @property origin 这份载荷从哪来 / 为什么没用上。
 * @property version 当前生效版本（[PayloadOrigin.Loaded] 时通常非空）。
 * @property reason 人类可读的回落原因，供日志直接拼进一行；成功时为 `null`。
 */
data class PayloadMeta(
    val origin: PayloadOrigin,
    val version: String? = null,
    val reason: String? = null,
) {
    /** 日志用的一行摘要，例如 `colors: loaded v5` 或 `resources: absent (无本地皮肤包)`。 */
    fun describe(channel: String): String = when {
        origin.isLoaded && version != null -> "$channel: loaded $version"
        origin.isLoaded -> "$channel: loaded"
        reason != null -> "$channel: ${origin.name} ($reason)"
        else -> "$channel: ${origin.name}"
    }

    companion object {
        /** 载荷成功采用的便捷构造。 */
        fun loaded(version: String?) = PayloadMeta(PayloadOrigin.Loaded, version)

        /** 回落内置默认的便捷构造。 */
        fun fallback(origin: PayloadOrigin, reason: String) =
            PayloadMeta(origin = origin, reason = reason)
    }
}

/**
 * 色值段：一套 [NongyuColors] 色板 + 它的来源。
 *
 * 来自服务端数据库目录接口（ADR-0004），**不随皮肤包**。因此它可以与
 * [ResourcePayload] 的版本完全无关 —— 这就是 ADR-0006「两载体不一致不算错误」。
 */
data class ColorPayload(
    val palette: NongyuColors,
    val meta: PayloadMeta,
)

/**
 * 资源段：图标绘制参数 + 字体。
 *
 * 来自皮肤包（ADR-0005），**不含色值**。图标在 T4 才数据化，本票只留形状：
 * 参数以「按图标名索引的标量表」承载，让内置默认（现有手绘常量）能原样放进去，
 * 也让 T4 换成包内参数时**不需要改接口**。
 *
 * @property iconStrokes 每个图标的线宽系数（相对画布最小边）。缺项由取用方回落内置默认。
 * @property font 字体标识；`null` = 系统默认字体（内置默认皮肤就是这个状态）。
 */
data class ResourcePayload(
    val iconStrokes: Map<NavIconKey, Float>,
    val font: SkinFont,
    val meta: PayloadMeta,
) {
    /** 取某个图标的线宽系数，缺失则回落 [fallback]（逐资源回落，ADR-0005 Q5=A）。 */
    fun strokeOf(icon: NavIconKey, fallback: Float): Float = iconStrokes[icon] ?: fallback

    companion object {
        /** 空资源段（全部回落）：用于「资源通道不可用」的半降级状态。 */
        fun empty(meta: PayloadMeta) = ResourcePayload(
            iconStrokes = emptyMap(),
            font = SkinFont.SystemDefault,
            meta = meta,
        )
    }
}

/**
 * 图标身份的**皮肤侧**键。
 *
 * 与 `ui/MainShell.kt` 里 `private enum class NavIcon` 是两回事：那个是渲染细节、
 * 私有且未数据化；这个是皮肤契约里的稳定字符串键，T4 数据化后由渲染层反向映射。
 * 用字符串而非枚举，是为了「包内出现未知图标名 → 忽略」这一层降级天然成立
 * （ADR-0003 第②层：同主版本未知字段忽略，而不是让枚举反序列化炸掉）。
 */
data class NavIconKey(val name: String) {
    override fun toString() = name

    companion object {
        val Grid = NavIconKey("grid")
        val Calendar = NavIconKey("calendar")
        val Compass = NavIconKey("compass")
        val Person = NavIconKey("person")

        /** 内置默认皮肤必须覆盖的图标全集（导航壳的四个 Tab）。 */
        val BuiltIn: List<NavIconKey> = listOf(Grid, Calendar, Compass, Person)
    }
}

/**
 * 字体标识。
 *
 * 本期内置默认 = 系统字体；T4 接 `Typeface.createFromFile` 后新增「包内字体」形态。
 * 做成密封层次而不是裸字符串，是为了让渲染层穷举时编译器能提醒「新增形态要处理」。
 */
sealed interface SkinFont {
    /** 使用系统默认字体（内置默认皮肤，也是任何字体加载失败时的最终回落）。 */
    data object SystemDefault : SkinFont

    /** 皮肤包内的字体文件（T4 实现加载；本票只定义标识）。 */
    data class FromPack(val fileName: String) : SkinFont
}

/**
 * 一次取用的**完整结果**：两段载荷 + 各自元信息。
 *
 * 关键设计：字段是**可空**（`palette` / `resources` 为 `null` 表示该通道没有可用载荷），
 * 而渲染必须永远拿得到东西 —— 所以取值一律走 [colors] / [resourcesOrDefault]，
 * 它们内部回落到 [SkinDefaults.builtIn]。**接口不提供任何抛异常路径**（ADR-0006 Q3=B）。
 *
 * @property builtIn 本次解析所依据的内置默认皮肤，回落时的真源。
 */
data class SkinResolution(
    val colors: ColorPayload?,
    val resources: ResourcePayload?,
    val builtIn: BuiltInSkin,
) {
    /** 实际渲染用的色板：有外部色值段就用它，否则内置默认。 */
    fun palette(): NongyuColors = colors?.palette ?: builtIn.colors

    /** 实际渲染用的资源段：逐项回落内置默认（ADR-0005 Q5=A）。 */
    fun resourcePayload(): ResourcePayload =
        resources ?: ResourcePayload.empty(PayloadMeta.fallback(PayloadOrigin.Absent, "无资源段"))

    /** 色值通道生效版本（日志用）；回落时为 `null`。 */
    fun colorsVersion(): String? = colors?.meta?.version?.takeIf { colors.meta.origin.isLoaded }

    /** 资源通道生效版本（日志用）；回落时为 `null`。注意**与色值版本无关**。 */
    fun resourcesVersion(): String? = resources?.meta?.version?.takeIf { resources.meta.origin.isLoaded }

    /** 供日志打一行：两通道各自的状态与版本（ADR-0006：两载体分别记录）。 */
    fun describe(): String {
        val colorMeta = colors?.meta ?: PayloadMeta.fallback(PayloadOrigin.Absent, "无色值段")
        val resMeta = resources?.meta ?: PayloadMeta.fallback(PayloadOrigin.Absent, "无资源段")
        return colorMeta.describe("colors") + "; " + resMeta.describe("resources")
    }
}

/**
 * 内置默认皮肤：随 APK 编译期内置、永不删除、任何失败情形下的最终渲染依据
 * （`GLOSSARY.md`「内置默认皮肤」）。
 *
 * 它就是 T2 之前的既有视觉，**逐值拷出来而不是重新设计**，所以换成它渲染的结果
 * 与换接口之前逐像素一致。
 *
 * @property colors 白绿色板，即 `NongyuColors.Green` 本身（同一份对象，不拷贝值）。
 * @property resources 图标线宽系数与系统字体，值与 `MainShell.drawNavIcon` 现有常量一致。
 */
data class BuiltInSkin(
    val colors: NongyuColors,
    val resources: ResourcePayload,
) {
    companion object {
        /**
         * 现有手绘图标的线宽系数：`MainShell.drawNavIcon` 里 `val sw = s * 0.085f`。
         * 四个图标共用同一个系数（统一线性风格），这里为每个键各存一份，
         * 以便将来单图标可覆盖而不影响其余。
         */
        const val BUILT_IN_ICON_STROKE = 0.085f
    }
}
