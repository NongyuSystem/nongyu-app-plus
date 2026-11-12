package com.nongyu.appplus.timetable.ui.holiday

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nongyu.appplus.timetable.data.holiday.HolidayCache
import com.nongyu.appplus.timetable.data.holiday.HolidayFetcher
import com.nongyu.appplus.timetable.data.holiday.HolidayParser
import com.nongyu.appplus.timetable.ui.TimetableGrid

/**
 * 课表页左上角的「课程表」标题 + 节假日倒计时。
 *
 * ## 布局（维护者定的：两行、颜色不同、标题大倒计时小）
 *
 * ```
 * 课程表                      ← 大字、加粗、品牌绿
 * 离元旦还有 51 天            ← 小字、次要色
 * ```
 *
 * ## 缓存优先（维护者明确要求）
 *
 * **不是每次都请求**。流程：
 * ```
 * 读本地缓存（按今天过滤掉已过的节日）
 *   └─ 有剩余 → 直接用，一个请求都不发
 *   └─ 空     → 联网拉（当年 → 明年）→ 写回缓存
 * ```
 * 所以节日过完后缓存自然变空，下次进入才重新拉。
 *
 * ## 为什么高度固定、且失败时不占位
 *
 * 这块叠在 [com.nongyu.appplus.timetable.ui.TimetableGrid] 的
 * `TOOLBAR_RESERVE_HEIGHT` 预留区里，**高度必须恒定**，否则会挤压课表、
 * 让时间条对齐用的实测 `periodTopPx` 变化。
 * 所以倒计时没数据时**只隐藏那行文字，不隐藏容器**（容器高度锁死）。
 *
 * ## 为什么不加入场动画
 *
 * 维护者要求本功能不能影响入场动画。这里：
 * - 标题随页面立即出现（它是静态 chrome，不是内容）；
 * - 倒计时由网络回来后填充，**不做淡入/不做 animateContentSize**，
 *   直接替换文字 —— 避免在网络回来的随机时刻触发一次动画，和
 *   「水位线逐张揭幕」的入场节奏打架。
 */
@Composable
fun TimetableTitleBlock(
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NongyuTheme.colors
    val context = LocalContext.current
    fun s(dp: Dp): Dp = (dp.value * uiScale).dp

    // null = 尚未确定（首次组合）／空串 = 确实没有可显示的倒计时
    var countdown by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val today = LocalDate.now()
        // 步骤 1：先看本地缓存（缓存优先，命中就完全不发请求）
        val cached = withContext(Dispatchers.IO) {
            runCatching { HolidayCache(context).loadUpcoming(today) }.getOrDefault(emptyList())
        }
        val cachedNext = HolidayParser.nextSpan(cached, today)
        if (cachedNext != null) {
            countdown = HolidayParser.countdownText(cachedNext, today)
            return@LaunchedEffect
        }

        // 步骤 2：缓存空了才联网（当年 → 明年，双源回退）
        val fetched = withContext(Dispatchers.IO) {
            runCatching { HolidayFetcher().fetchUpcoming(today) }.getOrDefault(emptyList())
        }
        if (fetched.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching { HolidayCache(context).save(fetched, today) }
            }
        }
        val next = HolidayParser.nextSpan(fetched, today)
        // 拉不到就留空串 → 那行文字不画，容器高度不变
        countdown = next?.let { HolidayParser.countdownText(it, today) } ?: ""
    }

    Column(modifier = modifier.padding(start = s(2.dp))) {
        // 第 1 行：标题（大、加粗、品牌绿）
        Text(
            text = "课程表",
            color = colors.brand.colorBrand,
            fontSize = (16f * uiScale).sp,
            lineHeight = (19f * uiScale).sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
        )
        // 倒计时没有内容时也要占住这一行的高度（避免挤压课表）
        androidx.compose.foundation.layout.Spacer(Modifier.height(s(2.dp)))
        Text(
            // 空串与 null 都渲染为空串，行高由 fontSize/lineHeight 撑住
            text = countdown.orEmpty(),
            color = colors.surface.colorTextSecondary.copy(alpha = 0.85f),
            fontSize = (8.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
