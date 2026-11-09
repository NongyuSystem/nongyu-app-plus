package com.nongyu.appplus.ui.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nongyu.appplus.skin.NavIconKey

/**
 * 导航图标的**数据化绘制参数**（票 #38 / ADR-0005 Q1=A）。
 *
 * ## 为什么要这一步
 *
 * 改造前图标是 `MainShell.kt` 里的一段 `when (icon) { … }` 手绘代码：几何常量**写在语句里**。
 * 代码不可下发 —— 皮肤包下下来没有任何东西能换掉它。所以先要把几何从「语句」变成「数据」，
 * 「换包 = 换参数」才成立。
 *
 * ## 与改造前逐像素一致的做法（关键）
 *
 * 这里的每个字段都是**改造前那段代码里的字面量原样搬出来**，且渲染函数 [drawNavIcon]
 * 保持**完全相同的运算顺序**（例如 `(s - gap * 3f) / 2f` 而不是事先算成 `s * 0.32f`）。
 * 顺序变了浮点末位就会不同，抗锯齿边缘会差 1 个色阶 —— 那就不叫「视觉完全一致」了。
 *
 * 本文件只描述**几何**：线宽不在这里，它由皮肤包的 `ResourcePayload.iconStrokes` 提供
 * （[strokeScale] 参数），这正是「图标可换」的那一半。
 */
sealed interface NavIconGeometry {

    /** 全部：四宫格。`gap` 是格间距、`cellCorner` 是每格圆角（相对格子边长）。 */
    data class Grid(
        val gap: Float = 0.12f,
        val cellCorner: Float = 0.28f,
    ) : NavIconGeometry

    /**
     * 课表：日历（外框 + 两条挂钉 + 一条横向分隔）。
     *
     * 全部相对画布最小边 `s`；`bottomInset` 是外框下缘内缩比例（原有 `s * 0.16f`）。
     */
    data class Calendar(
        val left: Float = 0.16f,
        val top: Float = 0.22f,
        val bottomInset: Float = 0.16f,
        val corner: Float = 0.14f,
        val pegLeftX: Float = 0.34f,
        val pegRightX: Float = 0.66f,
        val pegTopY: Float = 0.10f,
        val pegBottomY: Float = 0.30f,
        val dividerY: Float = 0.44f,
    ) : NavIconGeometry

    /** 探索：指南针（外圆 + 内部指针菱形）。`needle` 是指针半高、`needleRatio` 是半宽比。 */
    data class Compass(
        val radius: Float = 0.34f,
        val needle: Float = 0.20f,
        val needleRatio: Float = 0.72f,
        val notch: Float = 0.10f,
    ) : NavIconGeometry

    /** 个人：人形（圆头 + 肩部半圆弧）。 */
    data class Person(
        val headRadius: Float = 0.15f,
        val headCenterX: Float = 0.5f,
        val headCenterY: Float = 0.33f,
        val arcWidth: Float = 0.62f,
        val arcHeight: Float = 0.46f,
        val arcTop: Float = 0.52f,
    ) : NavIconGeometry

    /**
     * 未知图标键的占位几何：画不出东西，但**不是错误**。
     *
     * 用一个空实现而不是抛异常，是为了守住 ADR-0006 Q3=B「不抛异常、静默回落」。
     * 内置默认皮肤只覆盖 [NavIconKey.BuiltIn] 四个键，正常路径永远走不到这里。
     */
    data object Unsupported : NavIconGeometry

    companion object {
        /**
         * 内置默认几何（= 改造前 `MainShell.drawNavIcon` 的四个分支，逐字面量搬运）。
         *
         * 皮肤包将来可以下发**另一组**几何参数而不只是线宽；本期契约只开线宽一个口子
         * （`ResourcePayload.iconStrokes` 是标量表），所以这里按图标键给出一份固定几何，
         * 线宽由调用方传入。要扩成「几何也可下发」只需把这张表换成数据源，不影响调用方。
         */
        fun builtInOf(key: NavIconKey): NavIconGeometry = when (key) {
            NavIconKey.Grid -> Grid()
            NavIconKey.Calendar -> Calendar()
            NavIconKey.Compass -> Compass()
            NavIconKey.Person -> Person()
            // 包内出现未知图标名 → 忽略（ADR-0003 第②层同主版本未知字段忽略）。
            // 渲染层不认识的图标不画，而不是崩。
            else -> Unsupported
        }
    }
}

/**
 * 绘制导航图标：几何来自 [geometry]（数据），线宽来自 [strokeScale]（皮肤包参数）。
 *
 * @param strokeScale 线宽系数，**相对画布最小边**。内置默认 `0.085f`
 *   （`BuiltInSkin.BUILT_IN_ICON_STROKE`），调用方应经 `ResourcePayload.strokeOf`
 *   逐资源回落取得，而不是直接写常量。
 *
 * 与改造前的等价性：`sw = size.minDimension * strokeScale` 完全一致；
 * 各分支的中间量运算顺序也逐条保留（见各 `val` 的注释）。
 */
fun DrawScope.drawNavIcon(
    geometry: NavIconGeometry,
    tint: Color,
    strokeScale: Float,
) {
    val s = size.minDimension
    val sw = s * strokeScale                       // 改造前：val sw = s * 0.085f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (geometry) {
        is NavIconGeometry.Grid -> {
            val gap = s * geometry.gap
            // 保持原运算顺序：(s - gap * 3f) / 2f（不要提前折叠成 s * 0.32f）
            val cell = (s - gap * 3f) / 2f
            val r = CornerRadius(cell * geometry.cellCorner, cell * geometry.cellCorner)
            for (i in 0..1) for (j in 0..1) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(gap + i * (cell + gap), gap + j * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = r,
                    style = stroke,
                )
            }
        }

        is NavIconGeometry.Calendar -> {
            val l = s * geometry.left
            val t = s * geometry.top
            val w = s - l * 2f
            val h = s - t - s * geometry.bottomInset
            drawRoundRect(
                color = tint,
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(s * geometry.corner, s * geometry.corner),
                style = stroke,
            )
            // 顶部两个挂钉
            drawLine(
                tint,
                Offset(s * geometry.pegLeftX, s * geometry.pegTopY),
                Offset(s * geometry.pegLeftX, s * geometry.pegBottomY),
                sw, StrokeCap.Round,
            )
            drawLine(
                tint,
                Offset(s * geometry.pegRightX, s * geometry.pegTopY),
                Offset(s * geometry.pegRightX, s * geometry.pegBottomY),
                sw, StrokeCap.Round,
            )
            // 横向分隔线
            drawLine(tint, Offset(l, s * geometry.dividerY), Offset(l + w, s * geometry.dividerY), sw, StrokeCap.Round)
        }

        is NavIconGeometry.Compass -> {
            val c = Offset(s / 2f, s / 2f)
            val r = s * geometry.radius
            drawCircle(tint, r, c, style = stroke)
            val d = s * geometry.needle
            val halfW = d * geometry.needleRatio
            val midY = c.y + d * geometry.notch
            drawLine(tint, Offset(c.x, c.y - d), Offset(c.x + halfW, c.y + d), sw, StrokeCap.Round)
            drawLine(tint, Offset(c.x + halfW, c.y + d), Offset(c.x, midY), sw, StrokeCap.Round)
            drawLine(tint, Offset(c.x, midY), Offset(c.x - halfW, c.y - d), sw, StrokeCap.Round)
            drawLine(tint, Offset(c.x - halfW, c.y - d), Offset(c.x, c.y - d), sw, StrokeCap.Round)
        }

        is NavIconGeometry.Person -> {
            val headR = s * geometry.headRadius
            drawCircle(tint, headR, Offset(s * geometry.headCenterX, s * geometry.headCenterY), style = stroke)
            // 肩：以画布下半部为中心的半圆弧（用 drawArc 避免 Path/drawPath 的依赖问题）
            val arcW = s * geometry.arcWidth
            val arcH = s * geometry.arcHeight
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset((s - arcW) / 2f, s * geometry.arcTop),
                size = Size(arcW, arcH),
                style = stroke,
            )
        }

        // 未知几何：不绘制（静默，不抛异常）。
        NavIconGeometry.Unsupported -> Unit
    }
}
