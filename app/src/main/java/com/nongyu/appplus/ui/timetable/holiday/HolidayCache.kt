package com.nongyu.appplus.ui.timetable.holiday

import android.content.Context
import java.time.LocalDate

/**
 * 节假日本地缓存（SharedPreferences + JSON）。
 *
 * ## 维护者定的策略：**缓存优先，不是每次都请求**
 *
 * ```
 * 读本地 → 删掉已经过完的节日 → 仍有剩余的，直接用（不发请求）
 *                              └─ 空了 → 联网拉（当年 → 明年）→ 写回缓存
 * ```
 *
 * 「过完的节日从列表删去」在**读写两侧都做**：
 * - 读的时候按 [today] 过滤（屏幕上永不出现过去的节日）；
 * - 写的时候只存未来条目（缓存不会越攒越大）。
 *
 * ## 为什么用 SharedPreferences 而不是 DataStore
 *
 * 节假日是**非敏感、可丢弃**的小数据（几十条 JSON），没有并发写入场景。
 * 仓库里 DataStore 是为 token（加密、需迁移）引入的，这里没必要跟着上。
 *
 * ## 版本号
 *
 * 存 [VERSION]。将来缓存结构变了，版本不匹配就整份丢弃重拉，不用写迁移逻辑。
 */
class HolidayCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 读出缓存中**今天及以后**的假期段。
     *
     * 返回空列表有两种含义，调用方都应视为"需要联网"：
     * 1. 从未拉取过；
     * 2. 拉过，但节日都过完了（这正是维护者要的"过完就删"）。
     */
    fun loadUpcoming(today: LocalDate): List<HolidaySpan> {
        val raw = prefs.getString(KEY_DATA, null) ?: return emptyList()
        if (prefs.getInt(KEY_VERSION, 0) != VERSION) return emptyList()
        return HolidayParser.parseSpans(raw)
            // 读的时候过滤：已过完的不返回（"过完的节日从列表删去"）
            .filter { !it.endDate.isBefore(today) }
            .sortedBy { it.startDate }
    }

    /** 写回缓存 —— **只存今天及以后的条目**，缓存因此不会无限增长。 */
    fun save(spans: List<HolidaySpan>, today: LocalDate) {
        val kept = spans.filter { !it.endDate.isBefore(today) }
        prefs.edit()
            .putInt(KEY_VERSION, VERSION)
            .putString(KEY_DATA, HolidayParser.encodeSpans(kept))
            .apply()
    }

    /** 清空缓存（调试或数据源明显异常时用）。 */
    fun clear() {
        prefs.edit().remove(KEY_DATA).remove(KEY_VERSION).apply()
    }

    private companion object {
        const val PREFS_NAME = "holiday_cache"
        const val KEY_DATA = "spans"
        const val KEY_VERSION = "version"
        const val VERSION = 1
    }
}
