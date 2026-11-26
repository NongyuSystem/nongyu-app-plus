package com.nongyu.appplus.map.logic

import com.nongyu.appplus.map.model.MapViewport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MapGeometry.clampPan] 的单测。
 *
 * 2026-11-26 修 bug 时补：地图控件常常只占屏幕一小块（对话框里约 1080×400），
 * 而调用方曾把屏幕尺寸写死成 1080×1920，导致
 *   1) 图比「假想屏幕」小 → 被竖直推到下方（点不在中心）
 *   2) 一拖动又按假想尺寸重新夹取 → 画面突然跳位
 * 这里钉住「夹取结果只取决于传入的视口尺寸」这一性质。
 *
 * 注意：`MapViewport` 约束 `scale >= 1`，所以「图比视口小」要用**小图 + 大视口**构造，
 * 不能靠缩小 scale。
 */
class MapGeometryClampPanTest {

    /** 小图：400×300，配合 scale=1 后仍小于下面的视口。 */
    private val smallW = 400
    private val smallH = 300

    /** 大图：1600×1200，超过视口，用于验证夹取。 */
    private val bigW = 1600
    private val bigH = 1200

    @Test
    fun `图比视口小的时候居中`() {
        val vp = MapViewport(scale = 1f)
        val r = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, screenWidth = 1000, screenHeight = 600)
        assertEquals("水平居中", (1000 - 400) / 2f, r.offsetX, 0.01f)
        assertEquals("竖直居中", (600 - 300) / 2f, r.offsetY, 0.01f)
    }

    @Test
    fun `同样的视口在小视口里不会掉到下方`() {
        // 复现 bug：若误用 1920 作屏幕高，图会比它小得多，offsetY 变成 (1920-300)/2=810，
        // 整图掉到 400 高的控件之外（这就是维护者看到的「点不在中心」）。
        val vp = MapViewport(scale = 1f)
        val real = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, 1000, 400)
        val wrong = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, 1000, 1920)
        assertEquals("真实视口高 400 → 竖直居中", (400 - 300) / 2f, real.offsetY, 0.01f)
        assertEquals("错用 1920 → 掉到下方", (1920 - 300) / 2f, wrong.offsetY, 0.01f)
    }

    @Test
    fun `图比视口大时左上角对齐且可拖出`() {
        val vp = MapViewport(scale = 1f)
        val r = MapGeometry.clampPan(vp, 0f, 0f, bigW, bigH, 1000, 600)
        assertEquals("初始贴左上", 0f, r.offsetX, 0.01f)
        assertEquals("初始贴左上", 0f, r.offsetY, 0.01f)
    }

    @Test
    fun `向右下拖动不会让图脱离左上边界`() {
        val vp = MapViewport(scale = 1f)
        val r = MapGeometry.clampPan(vp, 500f, 500f, bigW, bigH, 1000, 600)
        assertEquals("offsetX 不能为正", 0f, r.offsetX, 0.01f)
        assertEquals("offsetY 不能为正", 0f, r.offsetY, 0.01f)
    }

    @Test
    fun `向左上拖动夹在图片右下的极限`() {
        val vp = MapViewport(scale = 1f)
        val r = MapGeometry.clampPan(vp, -5000f, -5000f, bigW, bigH, 1000, 600)
        assertEquals("最左 = 视口宽 - 图宽", (1000 - 1600).toFloat(), r.offsetX, 0.01f)
        assertEquals("最上 = 视口高 - 图高", (600 - 1200).toFloat(), r.offsetY, 0.01f)
    }

    @Test
    fun `夹取结果与视口尺寸一一对应（同输入同输出）`() {
        val vp = MapViewport(scale = 1f)
        val a = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, 1080, 400)
        val b = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, 1080, 400)
        assertEquals(a.offsetX, b.offsetX, 0.001f)
        assertEquals(a.offsetY, b.offsetY, 0.001f)
    }

    @Test
    fun `同一张图在不同高度视口下竖直位置不同（这正是 bug 的表现）`() {
        val vp = MapViewport(scale = 1f)
        val shallow = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, 1080, 400)
        val tall = MapGeometry.clampPan(vp, 0f, 0f, smallW, smallH, 1080, 1920)
        assertEquals("两者必然不同，证明写死尺寸一定会错", false, shallow.offsetY == tall.offsetY)
    }
}
