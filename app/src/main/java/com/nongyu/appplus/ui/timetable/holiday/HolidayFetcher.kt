package com.nongyu.appplus.ui.timetable.holiday

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 节假日数据拉取器（**双源回退**）。
 *
 * ## 为什么不走自家网络层
 *
 * `com.nongyu.appplus.net` 是围绕**自家后端**搭的：`BASE_URL` 固定、带 token 拦截器、
 * 会话失效规则、重试策略全都是为自家 API 服务的。第三方公开 API 不属于那个契约，
 * 硬塞进去会污染 `PublicEndpoint` 的语义（它明确记载"标了不代表后端不要票"）。
 *
 * 所以这里用一个**独立、极简**的 OkHttp 客户端：无拦截器、3 秒超时、只做 GET。
 * OkHttp 是现有依赖，不新增库。
 *
 * ## 双源策略（维护者选定）
 *
 * 1. 主源 `timor.tech` —— 一次拿全年，字段带 `holiday`(Boolean)/`name`/`date`，
 *    能区分放假与补班，最省事；
 * 2. 备源 `holiday-cn`（jsdelivr CDN）—— 静态 JSON，`isOffDay` 区分。
 *
 * 任一源拿到**未来有假期**的数据即停止；两个都失败返回空列表（调用方静默隐藏）。
 *
 * ## 跨年（维护者选定）
 *
 * 当年剩余假期为空时自动拉 `year + 1`，保证「离 xx 节还有 x 天」不会因为
 * 一年过完就消失。
 */
class HolidayFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        // 3 秒：这是装饰性信息，不值得让用户等
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    /**
     * 拉取**今天及以后**的假期段。
     *
     * @param today 用来裁掉已过假期、并决定要不要拉明年
     * @return 假期段列表；全部失败时为空列表
     */
    fun fetchUpcoming(today: LocalDate): List<HolidaySpan> {
        val year = today.year
        // 先试当年；结果里没有未来假期就试明年
        forCandidateYears(year, year + 1).forEach { y ->
            val spans = fetchYear(y)
            val upcoming = spans.filter { !it.endDate.isBefore(today) }
            if (upcoming.isNotEmpty()) return upcoming
        }
        return emptyList()
    }

    private fun forCandidateYears(vararg years: Int) = years.toList()

    /** 单年拉取：主源 → 备源。 */
    private fun fetchYear(year: Int): List<HolidaySpan> {
        // 主源：timor.tech
        get("https://timor.tech/api/holiday/year/$year")
            ?.let { HolidayParser.parseTimor(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return HolidayParser.toSpans(it) }

        // 备源：holiday-cn（jsdelivr CDN，GitHub 直连在国内常被拒）
        get("https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json")
            ?.let { HolidayParser.parseHolidayCn(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return HolidayParser.toSpans(it) }

        return emptyList()
    }

    /** 发一次 GET，失败返回 null（不抛：调用方按"没数据"处理）。 */
    private fun get(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "nongyu-app-plus")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        }
    }.getOrNull()
}
