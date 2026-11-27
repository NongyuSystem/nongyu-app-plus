package com.nongyu.appplus.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 分组色板：按用途命名，不按色值命名。 */
@Immutable
data class NongyuColors(
    /** 品牌色族 */
    val brand: BrandColors,
    /** 表面与文字 */
    val surface: SurfaceColors,
    /** 农小屿星云球专用色 */
    val aiOrb: AiOrbColors,
    /** 底栏 */
    val navBar: NavBarColors,
) {
    @Immutable
    data class BrandColors(
        val colorBrand: Color,
        val colorBrandMuted: Color,
        val colorOnBrand: Color,
        /** 品牌绿的嫩绿变体：同色相提亮，用于「生长中」的装饰元素（票 #111 开页茎）。 */
        val colorBrandFresh: Color,
    )

    @Immutable
    data class SurfaceColors(
        val colorBackground: Color,
        val colorCard: Color,
        val colorText: Color,
        val colorTextSecondary: Color,
        val colorOutline: Color,
    )

    @Immutable
    data class AiOrbColors(
        /** 球体底色（主体白） */
        val colorCore: Color,
        /** 绿色（较多） */
        val colorNebulaGreen: Color,
        /** 黄色（少量） */
        val colorNebulaYellow: Color,
        /** 球面边缘暗角，用于表达体积感 */
        val colorRimShade: Color,
        /** 紧贴球缘的绿→透明外发光 */
        val colorGlow: Color,
    )

    @Immutable
    data class NavBarColors(
        val colorContainer: Color,
        val colorItem: Color,
        val colorIndicator: Color,
    )

    companion object {
        /** 白绿一套，色值取自 design-system/web-site/MASTER.md。 */
        val Green = NongyuColors(
            brand = BrandColors(
                colorBrand = Color(0xFF0A7C59),
                colorBrandMuted = Color(0xFFD4E9DF),
                colorOnBrand = Color(0xFFFFFFFF),
                // 品牌绿的黄绿变体：草绿偏黄，用于「生长中的枝条」这类需要有生命感的装饰元素
                // （票 #111 个人主页开页茎）。与深绿 #0A7C59 同一家族，不跳出色系。
                colorBrandFresh = Color(0xFF8BC34A),
            ),
            surface = SurfaceColors(
                colorBackground = Color(0xFFF7FAF8),
                colorCard = Color(0xFFFFFFFF),
                colorText = Color(0xFF0F1A14),
                colorTextSecondary = Color(0xFF4A564F),
                colorOutline = Color(0xFFCFE3DA),
            ),
            aiOrb = AiOrbColors(
                colorCore = Color(0xFFFFFFFF),
                colorNebulaGreen = Color(0xFF0A7C59),
                colorNebulaYellow = Color(0xFFE9C46A),
                colorRimShade = Color(0xFF255A43),
                colorGlow = Color(0xFF0A7C59),
            ),
            navBar = NavBarColors(
                colorContainer = Color(0xFFFFFFFF),
                colorItem = Color(0xFF4A564F),
                colorIndicator = Color(0xFF0A7C59),
            ),
        )
    }
}
