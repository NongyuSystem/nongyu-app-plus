package com.nongyu.appplus.profile.logic

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 #118：「土豆状果实按钮」几何判定纯函数单测（纯 JVM，不碰 Android / Compose）。
 *
 * 只断言外部行为：热区边长不小于 Material 最小可点尺寸、形状中心/边界/形状外点的判定、
 * 命中容差只在形状附近生效、文字安全区恒在形状内且互不重叠。
 * UI 绘制（Canvas drawPath 用同一份 [ProfileEntryGeometry.edgeRadiusAt] 采样）与点击反馈
 * 不写 Compose UI 测试，靠 assembleRelease + 真机四步验收。
 */
class ProfileEntryGeometryTest {

    // ---------- 热区尺寸 ----------

    @Test
    fun `热区边长不小于最小可点尺寸且随缩放只增不减`() {
        val min = ProfileEntryGeometry.MIN_TOUCH_TARGET_DP
        listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f).forEach { scale ->
            val side = ProfileEntryGeometry.hotZoneSizeDp(scale)
            assertTrue("UI 缩放 $scale 下热区 $side 应不小于 $min", side >= min - 1e-4f)
        }
        // 缩放单调不减：大屏的果实不会比小屏更难点。
        var previous = 0f
        listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f).forEach { scale ->
            val side = ProfileEntryGeometry.hotZoneSizeDp(scale)
            assertTrue(side >= previous - 1e-4f)
            previous = side
        }
    }

    @Test
    fun `热区计算对非法缩放做下限保护`() {
        val min = ProfileEntryGeometry.MIN_TOUCH_TARGET_DP
        assertEquals(min, ProfileEntryGeometry.hotZoneSizeDp(0f), 1e-4f)
        assertEquals(min, ProfileEntryGeometry.hotZoneSizeDp(-1f), 1e-4f)
    }

    // ---------- 命中判定：中心 / 边界 / 形状外 ----------

    @Test
    fun `形状中心恒命中`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, Math.PI.toFloat() / 2f, Math.PI.toFloat(), 7f)
            .forEach { angle ->
                assertTrue(
                    "角度 $angle 的中心点应命中",
                    ProfileEntryGeometry.isInsidePotato(
                        x = 0f,
                        y = 0f,
                        radiusX = 90f,
                        radiusY = 70f,
                        angleOffset = angle,
                    ),
                )
            }
    }

    @Test
    fun `形状外远点恒不命中`() {
        listOf(0f, Math.PI.toFloat() / 4f, Math.PI.toFloat() / 2f, Math.PI.toFloat(), -Math.PI.toFloat() / 3f)
            .forEach { angle ->
                assertFalse(
                    "角度 $angle 的远点应落空",
                    ProfileEntryGeometry.isInsidePotato(
                        x = cos(angle) * 1_000f,
                        y = sin(angle) * 1_000f,
                        radiusX = 90f,
                        radiusY = 70f,
                        angleOffset = 0f,
                    ),
                )
            }
    }

    @Test
    fun `基准椭圆边界内侧的点恒成立而外推到凸起外侧落空`() {
        val rx = 90f
        val ry = 70f
        listOf(0f, Math.PI.toFloat() / 6f, Math.PI.toFloat() / 2f, Math.PI.toFloat(), -Math.PI.toFloat() / 2f)
            .forEach { angle ->
                val base = ProfileEntryGeometry.ellipseRadiusAt(angle, rx, ry)
                // 基准半径的 99% 一定在椭圆内，从而一定在土豆内。
                assertTrue(
                    "角度 $angle 的基准半径内侧应命中",
                    ProfileEntryGeometry.isInsidePotato(
                        x = cos(angle) * base * 0.99f,
                        y = sin(angle) * base * 0.99f,
                        radiusX = rx,
                        radiusY = ry,
                        angleOffset = 0f,
                    ),
                )
                // 边界外 40px 必然落在形状外（凸起幅度仅 18%，远不足以覆盖）。
                assertFalse(
                    "角度 $angle 的远外侧应落空",
                    ProfileEntryGeometry.isInsidePotato(
                        x = cos(angle) * (base + 40f),
                        y = sin(angle) * (base + 40f),
                        radiusX = rx,
                        radiusY = ry,
                        angleOffset = 0f,
                    ),
                )
            }
    }

    @Test
    fun `凸起峰值点命中而峰值外侧落空`() {
        val rx = 90f
        val ry = 70f
        val peakAngle = ProfileEntryGeometry.LOBE_PEAK_ANGLE
        val peakMultiplier = ProfileEntryGeometry.edgeRadiusAt(peakAngle, 0f)
        assertTrue(peakMultiplier > 1f)
        // 峰值方位的椭圆基准半径（绝对像素），乘以形状倍率就是边界的绝对半径。
        val baseAbs = 1f / kotlin.math.sqrt(
            (cos(peakAngle) * cos(peakAngle)) / (rx * rx) + (sin(peakAngle) * sin(peakAngle)) / (ry * ry),
        )
        val peakAbs = peakMultiplier * baseAbs
        // 峰值内侧 1px 命中。
        assertTrue(
            ProfileEntryGeometry.isInsidePotato(
                x = cos(peakAngle) * (peakAbs - 1f),
                y = sin(peakAngle) * (peakAbs - 1f),
                radiusX = rx,
                radiusY = ry,
                angleOffset = 0f,
            ),
        )
        // 峰值外侧 1px 落空（不加容差时为严格判定）。
        assertFalse(
            ProfileEntryGeometry.isInsidePotato(
                x = cos(peakAngle) * (peakAbs + 1f),
                y = sin(peakAngle) * (peakAbs + 1f),
                radiusX = rx,
                radiusY = ry,
                angleOffset = 0f,
            ),
        )
    }

    @Test
    fun `angleOffset 会把同一凸起点移到不同方位`() {
        val offset = Math.PI.toFloat() / 3f
        val peak = ProfileEntryGeometry.edgeRadiusAt(ProfileEntryGeometry.LOBE_PEAK_ANGLE, 0f)
        // 不旋转时，offset 方位不是峰值，edge 更短。
        val plain = ProfileEntryGeometry.edgeRadiusAt(offset, 0f)
        // 旋转后 peak 从 LOBE_PEAK_ANGLE 移到 LOBE_PEAK_ANGLE + offset。
        val rotated = ProfileEntryGeometry.edgeRadiusAt(ProfileEntryGeometry.LOBE_PEAK_ANGLE + offset, offset)
        assertTrue("rotated=$rotated 应接近峰值 $peak 且大于 plain=$plain", rotated > plain)
        assertTrue(abs(rotated - peak) < 1e-3f)
    }

    // ---------- 命中容差 ----------

    @Test
    fun `容差只放形状边界附近的点且不超过给定像素`() {
        val rx = 90f
        val ry = 70f
        val angle = Math.PI.toFloat() / 5f
        val edgeAbs = absoluteEdgeRadius(angle, 0f, rx, ry)
        // 边界点本身命中。
        assertTrue(
            ProfileEntryGeometry.isInsidePotatoWithTolerance(
                cos(angle) * edgeAbs, sin(angle) * edgeAbs, rx, ry, 0f, 6f,
            ),
        )
        // 边界外 5px 在 6px 容差内命中。
        assertTrue(
            ProfileEntryGeometry.isInsidePotatoWithTolerance(
                cos(angle) * (edgeAbs + 5f), sin(angle) * (edgeAbs + 5f), rx, ry, 0f, 6f,
            ),
        )
        // 边界外 7px 超出 6px 容差落空。
        assertFalse(
            ProfileEntryGeometry.isInsidePotatoWithTolerance(
                cos(angle) * (edgeAbs + 7f), sin(angle) * (edgeAbs + 7f), rx, ry, 0f, 6f,
            ),
        )
        // 远离形状的角落不受容差影响。
        assertFalse(
            ProfileEntryGeometry.isInsidePotatoWithTolerance(1_000f, 1_000f, rx, ry, 0f, 6f),
        )
    }

    @Test
    fun `负容差按零处理不改变严格判定`() {
        val rx = 90f
        val ry = 70f
        val angle = ProfileEntryGeometry.LOBE_PEAK_ANGLE
        val edgeAbs = absoluteEdgeRadius(angle, 0f, rx, ry)
        assertTrue(ProfileEntryGeometry.isInsidePotatoWithTolerance(0f, 0f, rx, ry, 0f, -4f))
        assertFalse(
            ProfileEntryGeometry.isInsidePotatoWithTolerance(
                cos(angle) * (edgeAbs + 1f), sin(angle) * (edgeAbs + 1f), rx, ry, 0f, -4f,
            ),
        )
    }

    /** 某方位角上土豆边界的绝对半径（像素），供容差测试用。 */
    private fun absoluteEdgeRadius(angle: Float, offset: Float, rx: Float, ry: Float): Float {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val base = 1f / kotlin.math.sqrt((cosA * cosA) / (rx * rx) + (sinA * sinA) / (ry * ry))
        return base * ProfileEntryGeometry.edgeRadiusAt(angle, offset)
    }

    // ---------- 退化输入 ----------

    @Test
    fun `非正半径时任何点都不命中且边界倍率恒不小于一`() {
        assertFalse(ProfileEntryGeometry.isInsidePotato(0f, 0f, 0f, 70f, 0f))
        assertFalse(ProfileEntryGeometry.isInsidePotato(0f, 0f, 90f, 0f, 0f))
        assertFalse(ProfileEntryGeometry.isInsidePotato(0f, 0f, -10f, 70f, 0f))
        // 边界倍率只由角度决定，与半径无关；任意方位都不小于基准椭圆（倍率 >= 1）。
        listOf(0f, 0.5f, 1f, 2f, 4f, -3f).forEach { angle ->
            listOf(0f, 0.7f, -1.2f).forEach { offset ->
                assertTrue(
                    "edgeRadiusAt($angle,$offset) 应不小于 1",
                    ProfileEntryGeometry.edgeRadiusAt(angle, offset) >= 1f,
                )
            }
        }
    }

    // ---------- 文字安全区 ----------

    @Test
    fun `文字安全区半宽半高恒小于最短半径且为正`() {
        listOf(90f to 70f, 120f to 60f, 60f to 60f, 200f to 40f).forEach { (rx, ry) ->
            val box = ProfileEntryGeometry.textSafeHalfExtent(rx, ry)
            assertTrue("rx=$rx 时半宽 ${box.first} 应小于 $rx", box.first < rx)
            assertTrue("ry=$ry 时半高 ${box.second} 应小于 $ry", box.second < ry)
            assertTrue(box.first > 0f && box.second > 0f)
        }
    }

    @Test
    fun `文字安全区四角落在土豆形状内`() {
        val rx = 90f
        val ry = 70f
        val (halfW, halfH) = ProfileEntryGeometry.textSafeHalfExtent(rx, ry)
        listOf(-1f, 1f).forEach { sx ->
            listOf(-1f, 1f).forEach { sy ->
                assertTrue(
                    "安全区角 ($sx,$sy) 应命中",
                    ProfileEntryGeometry.isInsidePotato(sx * halfW, sy * halfH, rx, ry, 0f),
                )
            }
        }
    }

    @Test
    fun `相邻果实的中心间距不小于热区边长加最小间距`() {
        val scale = 1f
        val side = ProfileEntryGeometry.hotZoneSizeDp(scale)
        val gap = ProfileEntryGeometry.fruitSpacingDp(scale)
        val centerDistance = side + gap
        assertTrue("中心距应让两个热区不重叠", centerDistance >= side)
        assertEquals(ProfileEntryGeometry.MIN_FRUIT_SPACING_DP, gap, 1e-4f)
    }

    // ---------- 绘制与命中一致性 ----------

    @Test
    fun `采样边界点严格命中且多旋转方位下峰值高度一致`() {
        val rx = 90f
        val ry = 70f
        val steps = 72
        listOf(0f, 0.4f, 1.1f, -0.9f, 3f).forEach { offset ->
            (0 until steps).forEach { i ->
                val angle = (2f * Math.PI.toFloat() * i) / steps
                val edge = ProfileEntryGeometry.absoluteEdgeRadiusAt(angle, offset, rx, ry)
                // 边界内侧 0.5px 必命中（绘制路径上的点被命中判定覆盖）。
                assertTrue(
                    "offset=$offset angle=$angle 的边界内侧应命中",
                    ProfileEntryGeometry.isInsidePotato(
                        cos(angle) * (edge - 0.5f), sin(angle) * (edge - 0.5f), rx, ry, offset,
                    ),
                )
            }
            // 无论旋转到哪个方位，凸起峰值倍率都与未旋转时一致。
            val referencePeak = ProfileEntryGeometry.edgeRadiusAt(ProfileEntryGeometry.LOBE_PEAK_ANGLE, 0f)
            val peak = ProfileEntryGeometry.edgeRadiusAt(ProfileEntryGeometry.LOBE_PEAK_ANGLE + offset, offset)
            assertTrue("offset=$offset 峰值倍率应恒定", abs(peak - referencePeak) < 1e-3f)
        }
    }
}
