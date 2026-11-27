package com.nongyu.appplus.profile.logic

import kotlin.math.PI

/**
 * 个人主页开页生长动画的纯判定接缝（票 #115，纯 JVM 单测）。
 *
 * 全部函数都是确定性的：不读系统时钟、不碰 Android / Compose、不碰网络。
 * 时间以「从入场首帧起的毫秒数」由 UI 侧显式传入，段边界、内容淡入、
 * 收尾帧透明度、降级判定与字符头像取字全部在这里，由纯 JVM 单测锁定。
 *
 * ## 术语边界（规格 #111）
 * 「枝条」「果实」「圆」只是视觉形态词，不进领域命名，也不进 GLOSSARY。
 * 本 object 的领域语义是「开页入场动画」，段名只描述视觉顺序，不映射领域实体。
 *
 * ## 分段顺序
 * [Segment.BRANCH] 枝条从球心起步生长 → [Segment.ORBIT] 沿路径绕圆 →
 * [Segment.FRUIT] 圆的等间距位置依次长出果实 → [Segment.FADE_IN] 其余元素淡入 →
 * [Segment.AVATAR] 字符头像收尾。五段总时长 [TOTAL_MS]，首尾闭合。
 */
object EnterAnimation {

    /** 五段（顺序即播放顺序）。 */
    enum class Segment {
        /** 枝条从农小屿球心位置起步，沿路径向上生长。 */
        BRANCH,

        /** 枝条延伸到圆的起点后绕圆一周。 */
        ORBIT,

        /** 圆上等间距位置依次长出果实。 */
        FRUIT,

        /** 画像卡与入口区其余元素淡入。 */
        FADE_IN,

        /** 字符头像收尾（票 #120 仅替换本段的渲染实现）。 */
        AVATAR,
    }

    /** 五段总时长（毫秒）。 */
    const val TOTAL_MS: Long = 2_400L

    /** 圆上果实的枚数（等间距）。 */
    const val FRUIT_COUNT: Int = 7

    /** 字符头像缺省字：无姓名 / 姓名空白时用它占位。 */
    const val FALLBACK_INITIAL: String = "农"

    /** 单枚果实在本段内占用的进度窗口（其余进度留给错峰起始）。 */
    private const val FRUIT_WINDOW: Float = 0.5f

    private val DURATIONS: List<Pair<Segment, Long>> = listOf(
        Segment.BRANCH to 520L,
        Segment.ORBIT to 760L,
        Segment.FRUIT to 520L,
        Segment.FADE_IN to 380L,
        Segment.AVATAR to 220L,
    )

    init {
        // 常量自校验：任一段时长改动后必须同步 TOTAL_MS，否则这里先炸。
        require(DURATIONS.sumOf { it.second } == TOTAL_MS)
    }

    /** 单段时长（毫秒）。 */
    fun durationOf(segment: Segment): Long =
        DURATIONS.first { it.first == segment }.second

    /** 段的起始时刻（毫秒，相对入场首帧）。 */
    fun startTimeOf(segment: Segment): Long {
        var elapsed = 0L
        for ((s, duration) in DURATIONS) {
            if (s == segment) return elapsed
            elapsed += duration
        }
        return elapsed
    }

    /** 段的结束时刻（毫秒，相对入场首帧）。 */
    fun endTimeOf(segment: Segment): Long = startTimeOf(segment) + durationOf(segment)

    /**
     * 把「已播放毫秒数」归一化成 0..1 的总进度；负数按 0、超出按 1 夹取。
     * UI 侧用 Animatable<Float, AnimationVector1D> 推进时直接传它的 value。
     */
    fun progressAtTime(elapsedMs: Long): Float =
        (elapsedMs.coerceAtLeast(0L).toFloat() / TOTAL_MS.toFloat()).coerceIn(0f, 1f)

    /**
     * 总进度落在哪一段。边界归下一段（段尾那一帧已经进入下一段）。
     * 传入越界进度先夹到 0..1，保证调用方不用自己防脏数据。
     */
    fun segmentAt(progress: Float): Segment {
        val p = progress.coerceIn(0f, 1f)
        for ((segment, _) in DURATIONS) {
            if (p < endTimeOf(segment).toFloat() / TOTAL_MS.toFloat()) return segment
        }
        return Segment.AVATAR
    }

    /**
     * 段内归一化进度：段首为 0、段尾为 1；尚未轮到该段为 0，已经播完为 1。
     * 注意段尾那一帧归下一段，但本函数仍返回 1，方便 UI 画「本段完成态」。
     */
    fun segmentProgress(progress: Float, segment: Segment): Float {
        val p = progress.coerceIn(0f, 1f)
        val start = startTimeOf(segment).toFloat() / TOTAL_MS.toFloat()
        val end = endTimeOf(segment).toFloat() / TOTAL_MS.toFloat()
        if (p <= start) return 0f
        if (p >= end) return 1f
        return ((p - start) / (end - start)).coerceIn(0f, 1f)
    }

    /** 其余元素淡入段的内容透明度（easeOut，前段升得快）。 */
    fun contentAlpha(progress: Float): Float = easeOut(segmentProgress(progress, Segment.FADE_IN))

    /** 字符头像收尾段的透明度（easeOut）。 */
    fun avatarAlpha(progress: Float): Float = easeOut(segmentProgress(progress, Segment.AVATAR))

    /**
     * 系统「减弱动效」判定：动画时长缩放 <= 0 即视为用户要求减弱 / 关闭动效。
     * 只收 Float、不收 Context，Context 侧的 Settings.Global 读取留在 UI 层。
     */
    fun shouldReduceMotion(durationScale: Float): Boolean = durationScale <= 0f

    /**
     * 是否跳过分段动画、直接显示终态。系统减弱动效与低端机任一成立即跳过。
     * [lowEndDevice] 由 UI 侧按设备档位传入（如 runtime 内存 / 核心数判定）。
     */
    fun shouldSkipAnimation(durationScale: Float, lowEndDevice: Boolean): Boolean =
        shouldReduceMotion(durationScale) || lowEndDevice

    /**
     * 字符头像取姓名首字：去掉首尾空白后取第一个字符；无姓名 / 全空白回落 [FALLBACK_INITIAL]。
     * 不按 code point 拆 surrogate pair —— 中文姓名首字恒在 BMP 内，emoji 姓名回落占位字即可。
     */
    fun avatarInitial(name: String?): String {
        if (name.isNullOrBlank()) return FALLBACK_INITIAL
        return name.trim().substring(0, 1)
    }

    /**
     * 果实等间距角度（弧度，0 = 三点钟方向，负 PI/2 = 正上方）。
     * 首枚在正上方，按顺时针等间距排满一圈。纯函数，UI 侧据此算坐标。
     */
    fun fruitAngles(): List<Double> = (0 until FRUIT_COUNT).map { i ->
        -PI / 2.0 + i * (2.0 * PI / FRUIT_COUNT)
    }

    /**
     * 单枚果实在果实段内的局部进度：各枚错峰起始，第 0 枚从段首开始，
     * 末枚从 (1 - FRUIT_WINDOW) 处开始，全部在段尾前长满。
     * [index] 越界时按夹取处理，不让脏数据把数组访问打穿。
     */
    fun fruitLocalProgress(index: Int, segmentProgress: Float): Float {
        val p = segmentProgress.coerceIn(0f, 1f)
        if (FRUIT_COUNT <= 1) return p
        val i = index.coerceIn(0, FRUIT_COUNT - 1)
        val start = i * (1f - FRUIT_WINDOW) / (FRUIT_COUNT - 1)
        return ((p - start) / FRUIT_WINDOW).coerceIn(0f, 1f)
    }

    /** easeOut 曲线（1 - (1-p)^2），供段内进度转透明度 / 生长量。 */
    fun easeOut(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return 1f - (1f - p) * (1f - p)
    }

    /** 枝条段用的 easeOut，单独命名便于 UI 侧表达语义（与 [easeOut] 同曲线）。 */
    fun branchGrowth(progress: Float): Float = easeOut(segmentProgress(progress, Segment.BRANCH))

    /** 绕圆段用的线性进度：沿路径匀速延伸，避免忽快忽慢。 */
    fun orbitTravel(progress: Float): Float = segmentProgress(progress, Segment.ORBIT)
}
