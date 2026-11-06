package com.nongyu.appplus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 应用主题。
 *
 * 当前使用 Material 3 默认配色，仅作为骨架占位：
 * 设计稿确定后，此处替换为农屿自有色板与字体体系（颜色、字号、圆角等 token）。
 */
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun NongyuAppPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 支持动态取色，骨架阶段默认关闭，避免与未来农屿品牌色冲突
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
