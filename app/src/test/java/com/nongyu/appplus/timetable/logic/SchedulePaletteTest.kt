package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [SchedulePalette] 的单测。
 *
 * 维护者 2026-11-24：「添加日程时选择颜色无用」——
 * 根因是日程卡片写死品牌色、从不读 colorIndex。这里钉住色板本身的取值与边界。
 */
class SchedulePaletteTest {

    @Test
    fun `色板有 6 个颜色`() {
        assertEquals(6, SchedulePalette.size)
        assertEquals(6, SchedulePalette.all().size)
    }

    @Test
    fun `第一个是品牌绿`() {
        assertEquals(0xFF0A7C59L, SchedulePalette.argbAt(0))
    }

    @Test
    fun `每个下标都能取到颜色`() {
        SchedulePalette.all().indices.forEach { i ->
            assertEquals(SchedulePalette.all()[i], SchedulePalette.argbAt(i))
        }
    }

    @Test
    fun `下标越界时夹到合法范围不抛异常`() {
        assertEquals(SchedulePalette.argbAt(0), SchedulePalette.argbAt(-5))
        assertEquals(
            SchedulePalette.argbAt(SchedulePalette.size - 1),
            SchedulePalette.argbAt(99),
        )
    }

    @Test
    fun `null 返回 null 交给调用方回落主题色`() {
        assertNull(SchedulePalette.argbOrNull(null))
    }

    @Test
    fun `有 colorIndex 时返回对应颜色`() {
        assertNotNull(SchedulePalette.argbOrNull(2))
        assertEquals(SchedulePalette.argbAt(2), SchedulePalette.argbOrNull(2))
    }

    @Test
    fun `所有颜色都是不透明的`() {
        // ARGB 高字节为 FF，否则卡片会半透明到看不清
        SchedulePalette.all().forEach { argb ->
            assertEquals("颜色 $argb 应不透明", 0xFFL, (argb ushr 24) and 0xFF)
        }
    }

    @Test
    fun `颜色互不相同`() {
        assertEquals(SchedulePalette.size, SchedulePalette.all().toSet().size)
    }
}
