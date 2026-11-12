package com.nongyu.appplus.timetable.logic

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 课程配色：**按课程名哈希**分配颜色，同名课永远同色，不需要任何手写映射表。
 *
 * ## 为什么用哈希
 * 固定色板 + 人工映射有两个毛病：课程一多就要维护「哪门课用第几号色」；
 * 换数据源后课程增减会让同一门课的颜色整体平移，破坏视觉记忆。
 * 哈希方案下颜色只由课程名决定，加课删课都不影响已有课程。
 *
 * ## 为什么不直接用「哈希 % 360」当色相
 * 实测过：13 门 mock 课的色相是 `[10,65,118,124,127,132,155,205,216,257,259,264,321]`，
 * **最小相邻间隔只有 2°** —— 在屏幕上就是同一个颜色，用户会以为课表错乱。
 * 360 个色相里随机取十几个，撞近似值几乎是必然，这不是分布不够好，是数学结果。
 *
 * 所以改成两层：
 * 1. **量化到 [SLOT_COUNT] 个色槽**（间隔 15°），颜色只会是「明显不同」或「完全一样」，
 *    不存在「差一点点、看着像又不一样」的糊味；
 * 2. **撞槽时确定性顺延**（[assignHueSlots]）：把当前所有课程名排序后依次占槽，
 *    保证**同屏课程必然不同色**。
 *
 * 取舍：颜色取决于「当前有哪些课」，所以新增一门课可能让另一门课换色。
 * 这是为了保证「同屏不撞色」付出的代价，比两门课同色更好。
 * 课程数超过 [SLOT_COUNT] 后会开始复用颜色（无法避免）。
 *
 * ## 对比度
 * 正文与底色的 WCAG 对比度在**全部 24 个色槽**上都不低于 4.5（AA 正文标准），
 * 由单测逐槽验证。配色不能只靠肉眼判断深浅——黄色区最容易翻车。
 */
object CoursePalette {

    /** 色槽数。24 槽 = 每槽间隔 15°，相邻槽肉眼可辨。 */
    const val SLOT_COUNT: Int = 24

    /** 底色饱和度（全表固定，保证同一调性）。 */
    private const val BG_SATURATION = 0.55f

    /** 底色亮度：足够浅才能衬住深色字。 */
    private const val BG_LIGHTNESS = 0.93f

    /** 跨节大课底色稍深，让连堂一眼可辨，仍属同色相。 */
    private const val SPANNING_BG_LIGHTNESS = 0.86f

    /** 主文字饱和度与亮度。 */
    private const val FG_SATURATION = 0.62f
    private const val FG_LIGHTNESS = 0.26f

    /**
     * 次要文字（地点）的饱和度与亮度。
     *
     * 刻意**不把它做浅**：靠字号（6.5sp）与字重（SemiBold）已足够区分层级，
     * 而调浅会让对比度掉到 4.5 以下，小字就看不清了。
     * 层次用大小与字重表达，不用「把字变淡」表达。
     */
    private const val SECONDARY_SATURATION = 0.35f
    private const val SECONDARY_LIGHTNESS = 0.27f

    /** 色槽 → 色相（0f..360f）。 */
    fun hueForSlot(slot: Int): Float = slot * (360f / SLOT_COUNT)

    /**
     * 给一组课程名分配互不相同的色槽。
     *
     * 处理顺序按 `(哈希, 名字)` 排序，所以**结果只取决于名字集合本身**，
     * 与传入顺序无关；同样的课表多次渲染得到同样的配色。
     * 撞槽时向后线性探测，保证已占用的槽不复用。
     */
    fun assignHueSlots(names: Collection<String>): Map<String, Int> {
        val ordered = names.distinct().sortedWith(compareBy({ fnv1a(it) }, { it }))
        val taken = BooleanArray(SLOT_COUNT)
        val result = LinkedHashMap<String, Int>(ordered.size)
        for (name in ordered) {
            var slot = preferredSlot(name)
            var probe = 0
            while (taken[slot] && probe < SLOT_COUNT) {
                slot = (slot + 1) % SLOT_COUNT
                probe++
            }
            taken[slot] = true
            result[name] = slot
        }
        return result
    }

    /** 单个名字的首选色槽（未考虑冲突）。 */
    fun preferredSlot(name: String): Int = (fnv1a(name) % SLOT_COUNT).toInt()

    /** 课程卡片底色（ARGB 打包成 Long）。 */
    fun backgroundArgb(slot: Int, spanning: Boolean): Long {
        val l = if (spanning) SPANNING_BG_LIGHTNESS else BG_LIGHTNESS
        return hslToArgb(hueForSlot(slot), BG_SATURATION, l)
    }

    /** 课程名主文字色（ARGB）。 */
    fun textArgb(slot: Int): Long =
        hslToArgb(hueForSlot(slot), FG_SATURATION, FG_LIGHTNESS)

    /** 地点等次要文字色（ARGB）。 */
    fun secondaryTextArgb(slot: Int): Long =
        hslToArgb(hueForSlot(slot), SECONDARY_SATURATION, SECONDARY_LIGHTNESS)

    /**
     * FNV-1a 32 位哈希。
     *
     * 不用 [String.hashCode]：它是 `h = 31h + c`，前缀贡献会互相抵消，
     * 「大学英语」与「大学英语二」容易落到很近的槽——这正是要避免的。
     * FNV-1a 每字节先异或再乘，前缀变化会影响全部结果位。
     */
    fun fnv1a(s: String): Long {
        var hash = 2166136261L            // FNV offset basis
        for (ch in s) {
            hash = hash xor ch.code.toLong()
            hash = (hash * 16777619L) and 0xFFFFFFFFL   // FNV prime，掩到 32 位
        }
        return hash
    }

    /**
     * HSL → ARGB。
     *
     * 自己实现而不借用 android.graphics：本文件要能被**纯 JVM 单测**覆盖，
     * 一旦 import android.* 就会在单测里抛 "not mocked"。
     */
    fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Long {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)

        val c = (1f - abs(2f * l - 1f)) * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))

        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)

        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /**
     * WCAG 对比度（1..21）。
     *
     * 这是配色方案的安全网：浅底深字看着安全，实际在黄绿色相上亮度差会明显变小，
     * 必须算，不能靠看。
     */
    fun contrastRatio(argb1: Long, argb2: Long): Double {
        val l1 = relativeLuminance(argb1)
        val l2 = relativeLuminance(argb2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 相对亮度。 */
    private fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** 把 ARGB 拆成 (r, g, b)，供测试与调试用。 */
    fun toRgb(argb: Long): Triple<Int, Int, Int> = Triple(
        ((argb shr 16) and 0xFF).toInt(),
        ((argb shr 8) and 0xFF).toInt(),
        (argb and 0xFF).toInt(),
    )
}
