package com.nongyu.appplus.login.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 登录页的统一尺寸缩放（真机适配）。
 *
 * ## 为什么需要
 * 登录页所有尺寸原先按蓝叠（1080×2400 @ 320dpi = **540dp × 1200dp**）写死。
 * 常见真机的可用宽度只有 **360–410dp**、高度 **780–900dp**，比蓝叠窄 25–33%。
 * 同样的固定 dp 在真机上就会「所有东西都很大、挤在一起」。
 *
 * ## 做法
 * 以蓝叠为基准算一个等比因子：
 * ```
 * scale = min(屏幕宽 / 540dp, 屏幕高 / 1200dp)
 * ```
 * 上下限 `0.68..1.20`：
 * - 下限防止极小屏把内容缩到不可点（68% 时 48dp 输入框仍有 32dp）；
 * - 上限防止平板/大屏把内容拉得过于巨大。
 *
 * 所有尺寸用 [scaledDp] / [scaledSp] 换算，**不要**再写裸 dp。
 */
@Composable
fun rememberLoginScale(): Float {
    val config = LocalConfiguration.current
    val w = config.screenWidthDp.toFloat()
    val h = config.screenHeightDp.toFloat()
    return remember(w, h) {
        val byWidth = w / BASE_WIDTH_DP
        val byHeight = h / BASE_HEIGHT_DP
        // 宽度通常是瓶颈（真机更窄），取两者较小值保证不溢出
        minOf(byWidth, byHeight).coerceIn(MIN_SCALE, MAX_SCALE)
    }
}

/** 按缩放因子换算长度。 */
fun Float.scaledDp(scale: Float): Dp = (this * scale).dp

/** 按缩放因子换算字号。 */
fun Float.scaledSp(scale: Float): TextUnit = (this * scale).sp

/** 基准宽度（蓝叠 1080px @ 320dpi）。 */
private const val BASE_WIDTH_DP = 540f

/** 基准高度（蓝叠 2400px @ 320dpi）。 */
private const val BASE_HEIGHT_DP = 1200f

/** 最小缩放：不再缩小，保证可点面积。 */
private const val MIN_SCALE = 0.68f

/** 最大缩放：避免大屏内容过于夸张。 */
private const val MAX_SCALE = 1.20f
