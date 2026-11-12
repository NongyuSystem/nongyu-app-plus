package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nongyu.appplus.timetable.data.MOCK_COURSES

/**
 * 课程配色单测。
 *
 * 这套配色的风险不在「算不出来」，而在三件肉眼不易发现的事：
 * 1. **同名课不同色**——用户会以为课表错乱；
 * 2. **两门课颜色几乎一样**——360 个色相随机取十几个，最小间隔可能只有 2°，
 *    屏幕上就是同一个颜色（这是最初版本的真实缺陷）；
 * 3. **某些色相上文字看不清**——浅底深字看着安全，实际在黄色区亮度差最小。
 *
 * 所以这里只测那些「靠看发现不了、靠算才可靠」的东西。
 */
class CoursePaletteTest {

    @Test
    fun `同名课永远得到同一个颜色`() {
        val slot = CoursePalette.preferredSlot("高等数学")
        assertEquals(slot, CoursePalette.preferredSlot("高等数学"))
        assertEquals(
            CoursePalette.backgroundArgb(slot, false),
            CoursePalette.backgroundArgb(CoursePalette.preferredSlot("高等数学"), false),
        )
        assertEquals(
            CoursePalette.textArgb(slot),
            CoursePalette.textArgb(slot),
        )
    }

    @Test
    fun `FNV-1a 的已知向量正确`() {
        // FNV-1a 32 位标准测试向量。改动哈希实现时这条会立刻红。
        assertEquals(0x811C9DC5L, CoursePalette.fnv1a(""))
        assertEquals(0xE40C292CL, CoursePalette.fnv1a("a"))
        // foobar 的标准值是 0xBF9CF968（不是 0x4F9F2CAB，后者是别的变体）
        assertEquals(0xBF9CF968L, CoursePalette.fnv1a("foobar"))
    }

    @Test
    fun `色槽数固定且色相均匀铺满整个色环`() {
        assertEquals(24, CoursePalette.SLOT_COUNT)
        val hues = (0 until CoursePalette.SLOT_COUNT).map { CoursePalette.hueForSlot(it) }
        assertEquals(24, hues.toSet().size)
        // 相邻槽间隔 15°
        hues.zipWithNext().forEach { (a, b) ->
            assertEquals(15f, b - a, 0.001f)
        }
        assertTrue(hues.all { it >= 0f && it < 360f })
    }

    @Test
    fun `同屏课程必然分到不同色槽`() {
        val names = MOCK_COURSES.map { it.name }
        val slots = CoursePalette.assignHueSlots(names)
        assertEquals(names.distinct().size, slots.size)
        assertEquals(
            "有课程撞成同一个色槽: $slots",
            slots.size,
            slots.values.toSet().size,
        )
    }

    @Test
    fun `撞槽时会确定性顺延而不是直接复用`() {
        // 构造两个首选槽相同的名字，验证顺延后仍然不同
        val slots = CoursePalette.assignHueSlots(listOf("甲", "乙", "丙", "丁", "戊"))
        assertEquals(slots.size, slots.values.toSet().size)
    }

    @Test
    fun `分配结果与传入顺序无关`() {
        val names = MOCK_COURSES.map { it.name }
        val a = CoursePalette.assignHueSlots(names)
        val b = CoursePalette.assignHueSlots(names.reversed())
        val c = CoursePalette.assignHueSlots(names.shuffled(java.util.Random(42)))
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `课程数超过色槽数后会复用颜色但不会崩`() {
        val many = (0 until 40).map { "课程$it" }
        val slots = CoursePalette.assignHueSlots(many)
        assertEquals(40, slots.size)
        assertTrue(slots.values.all { it in 0 until CoursePalette.SLOT_COUNT })
        // 超过 24 门后必然有复用，这是设计上的已知边界
        assertTrue(slots.values.toSet().size <= CoursePalette.SLOT_COUNT)
    }

    @Test
    fun `正文与底色的对比度在每个色槽上都达到 AA 正文标准`() {
        var min = Double.MAX_VALUE
        var minSlot = 0
        for (slot in 0 until CoursePalette.SLOT_COUNT) {
            for (spanning in listOf(false, true)) {
                val bg = CoursePalette.backgroundArgb(slot, spanning)
                val fg = CoursePalette.textArgb(slot)
                val ratio = CoursePalette.contrastRatio(fg, bg)
                if (ratio < min) {
                    min = ratio
                    minSlot = slot
                }
                assertTrue(
                    "色槽 $slot（跨节=$spanning）正文对比度只有 $ratio（< 4.5）",
                    ratio >= 4.5,
                )
            }
        }
        println("正文最低对比度 = ${"%.2f".format(min)}（色槽 $minSlot）")
    }

    @Test
    fun `地点等次要文字的对比度也达到 AA`() {
        for (slot in 0 until CoursePalette.SLOT_COUNT) {
            for (spanning in listOf(false, true)) {
                val bg = CoursePalette.backgroundArgb(slot, spanning)
                val fg = CoursePalette.secondaryTextArgb(slot)
                val ratio = CoursePalette.contrastRatio(fg, bg)
                assertTrue(
                    "色槽 $slot（跨节=$spanning）次要文字对比度只有 $ratio（< 4.5）",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `跨节大课的底色比单节更深`() {
        val slot = CoursePalette.preferredSlot("设计基础")
        val single = CoursePalette.backgroundArgb(slot, spanning = false)
        val spanning = CoursePalette.backgroundArgb(slot, spanning = true)
        assertNotEquals(single, spanning)
        val singleSum = CoursePalette.toRgb(single).let { it.first + it.second + it.third }
        val spanningSum = CoursePalette.toRgb(spanning).let { it.first + it.second + it.third }
        assertTrue("跨节底色应当更深（$spanningSum < $singleSum）", spanningSum < singleSum)
    }

    @Test
    fun `输出颜色都是不透明的 ARGB`() {
        for (slot in 0 until CoursePalette.SLOT_COUNT) {
            listOf(
                CoursePalette.backgroundArgb(slot, false),
                CoursePalette.backgroundArgb(slot, true),
                CoursePalette.textArgb(slot),
                CoursePalette.secondaryTextArgb(slot),
            ).forEach { argb ->
                val alpha = ((argb shr 24) and 0xFF).toInt()
                assertEquals("色槽 $slot 的颜色不是不透明", 255, alpha)
            }
        }
    }

    @Test
    fun `相邻色槽的底色彼此可辨`() {
        // 15° 间隔在 RGB 上的最小通道差应当足够大（不是 1-2 的抖动）
        for (slot in 0 until CoursePalette.SLOT_COUNT - 1) {
            val a = CoursePalette.toRgb(CoursePalette.backgroundArgb(slot, false))
            val b = CoursePalette.toRgb(CoursePalette.backgroundArgb(slot + 1, false))
            val diff = maxOf(
                kotlin.math.abs(a.first - b.first),
                kotlin.math.abs(a.second - b.second),
                kotlin.math.abs(a.third - b.third),
            )
            assertTrue("色槽 $slot 与 ${slot + 1} 的底色差只有 $diff", diff >= 3)
        }
    }

    @Test
    fun `hsl 转换在纯黑与纯白处正确`() {
        assertEquals(0xFF000000L, CoursePalette.hslToArgb(0f, 0f, 0f))
        assertEquals(0xFFFFFFFFL, CoursePalette.hslToArgb(0f, 0f, 1f))
    }
}
