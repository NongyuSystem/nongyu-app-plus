package com.nongyu.appplus.ui.timetable.holiday

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一个法定节假日（或调休补班日）。
 *
 * @param date 该条目的日期
 * @param name 节日名，如「元旦」「春节」「国庆节」；补班条目通常带「补班」后缀
 * @param isOffDay true = 放假，false = 调休补班
 */
data class Holiday(
    val date: LocalDate,
    val name: String,
    val isOffDay: Boolean,
)

/**
 * 把某个节日的连续放假日**归并成一个假期**，用于「离 xx 节还有 x 天」。
 */
data class HolidaySpan(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/**
 * 节假日解析与筛选 —— **纯逻辑，不依赖 android.***，可直接 JVM 单测。
 *
 * ## 为什么用 kotlinx.serialization 而不是 org.json
 *
 * `org.json` 在 Android 上由系统提供，但在**纯 JVM 单测里是空 stub**：
 * `JSONObject("...")` 会直接抛 `NoSuchElementException`（本项目实测）。
 * `kotlinx-serialization-json` 是仓库既有依赖（网络层用它做转换器），
 * 主代码与单测行为一致，所以这里统一用它。
 *
 * ## 支持的两个免费数据源（双源回退）
 *
 * ### 1. timor.tech（主源，字段最全）
 * `GET https://timor.tech/api/holiday/year/{year}`
 * ```json
 * {"code":0,"holiday":{"01-01":{"holiday":true,"name":"元旦","date":"2026-01-01","wage":3},
 *                      "01-04":{"holiday":false,"name":"元旦后补班","date":"2026-01-04"}}}
 * ```
 * ⚠️ `holiday` 字段是 **boolean**，`false` 表示补班 —— 不能当"有值就算假期"。
 *
 * ### 2. holiday-cn（备源，jsdelivr CDN）
 * `GET https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json`
 * ```json
 * {"year":2026,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true}]}
 * ```
 *
 * 解析失败的条目**跳过而不是抛异常** —— 第三方数据格式变动时，
 * 倒计时少一行比整页崩掉好。
 */
object HolidayParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析 timor.tech 的响应；任何失败都返回空列表（不抛）。 */
    fun parseTimor(raw: String): List<Holiday> = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        // code != 0 表示接口自己报错（如年份不合法）
        if (root["code"]?.jsonPrimitive?.contentOrNull != "0") return emptyList()
        val obj = root["holiday"]?.jsonObject ?: return emptyList()
        val out = ArrayList<Holiday>(40)
        for ((_, v) in obj) {
            val item = runCatching { v.jsonObject }.getOrNull() ?: continue
            val date = parseDate(item.str("date")) ?: continue
            val name = item.str("name").trim()
            if (name.isEmpty()) continue
            // holiday 是 Boolean：true=放假，false=补班
            val off = item["holiday"]?.jsonPrimitive?.booleanOrNull ?: true
            out += Holiday(date = date, name = name, isOffDay = off)
        }
        out
    }.getOrDefault(emptyList())

    /** 解析 holiday-cn 的响应；任何失败都返回空列表（不抛）。 */
    fun parseHolidayCn(raw: String): List<Holiday> = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val days = root["days"]?.jsonArray ?: return emptyList()
        val out = ArrayList<Holiday>(40)
        for (el in days) {
            val item = runCatching { el.jsonObject }.getOrNull() ?: continue
            val date = parseDate(item.str("date")) ?: continue
            val name = item.str("name").trim()
            if (name.isEmpty()) continue
            val off = item["isOffDay"]?.jsonPrimitive?.booleanOrNull ?: true
            out += Holiday(date = date, name = name, isOffDay = off)
        }
        out
    }.getOrDefault(emptyList())

    /** 解析缓存里存的假期段（JSON 数组）。失败返回空列表。 */
    fun parseSpans(raw: String): List<HolidaySpan> = runCatching {
        val arr = json.parseToJsonElement(raw).jsonArray
        val out = ArrayList<HolidaySpan>(arr.size)
        for (el in arr) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            val name = o.str("name")
            val start = parseDate(o.str("start"))
            val end = parseDate(o.str("end"))
            if (name.isEmpty() || start == null || end == null) continue
            out += HolidaySpan(name, start, end)
        }
        out
    }.getOrDefault(emptyList())

    /** 把假期段序列化成缓存用的 JSON。 */
    fun encodeSpans(spans: List<HolidaySpan>): String = spans.joinToString(",", "[", "]") { s ->
        """{"name":"${escape(s.name)}","start":"${s.startDate}","end":"${s.endDate}"}"""
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun parseDate(s: String): LocalDate? = runCatching {
        if (s.length < 10) null else LocalDate.parse(s.substring(0, 10))
    }.getOrNull()

    /**
     * 把「放假条目」归并成连续假期段，按开始日期升序。
     *
     * 规则：同名的、日期连续的放假条目算同一个假期。
     * 补班条目（isOffDay=false）**不参与**倒计时 —— 「离补班还有 3 天」不是用户想要的。
     *
     * 例子：2026 元旦 1/1、1/2、1/3 三条 → 一个 `HolidaySpan(元旦, 1/1, 1/3)`。
     */
    fun toSpans(holidays: List<Holiday>): List<HolidaySpan> {
        val offDays = holidays.filter { it.isOffDay }.sortedBy { it.date }
        if (offDays.isEmpty()) return emptyList()

        val out = ArrayList<HolidaySpan>()
        var curName = offDays[0].name
        var curStart = offDays[0].date
        var curEnd = offDays[0].date

        for (h in offDays.drop(1)) {
            val sameName = h.name == curName
            val adjacent = h.date == curEnd.plusDays(1)
            if (sameName && adjacent) {
                curEnd = h.date
            } else {
                out += HolidaySpan(curName, curStart, curEnd)
                curName = h.name
                curStart = h.date
                curEnd = h.date
            }
        }
        out += HolidaySpan(curName, curStart, curEnd)
        return out
    }

    /**
     * 找出**今天及以后**最近的一个假期。
     *
     * - 假期正在进行中 → 返回它（`daysUntil` 为 0）；
     * - 已经过去的假期不返回；
     * - 没有未来假期返回 null（调用方据此隐藏倒计时）。
     */
    fun nextSpan(spans: List<HolidaySpan>, today: LocalDate): HolidaySpan? =
        spans.filter { !it.endDate.isBefore(today) }.minByOrNull { it.startDate }

    /** 从今天到该假期开始还有几天（今天已在假期内则为 0）。 */
    fun daysUntil(span: HolidaySpan, today: LocalDate): Int {
        val d = java.time.temporal.ChronoUnit.DAYS.between(today, span.startDate)
        return if (d < 0) 0 else d.toInt()
    }

    /**
     * 生成倒计时文案：`离元旦还有 51 天` / `明天是元旦` / `元旦假期中`。
     *
     * 注意顺序：**先判"是否已在假期内"**，再判天数。
     * 因为假期第一天时 `daysUntil == 0`，如果先判 `days == 0` 会误报"今天是元旦"，
     * 而用户更关心"假期还剩几天"（本项目实测踩过这个顺序问题）。
     */
    fun countdownText(span: HolidaySpan, today: LocalDate): String {
        val inProgress = !today.isBefore(span.startDate) && !today.isAfter(span.endDate)
        if (inProgress) {
            // 假期内给出剩余天数，比只说"假期中"信息量大
            val remain = java.time.temporal.ChronoUnit.DAYS.between(today, span.endDate).toInt() + 1
            return if (remain <= 1) "${span.name}假期最后一天" else "${span.name}假期中 · 还剩 $remain 天"
        }
        val days = daysUntil(span, today)
        return when {
            days == 0 -> "今天是${span.name}"
            days == 1 -> "明天是${span.name}"
            else -> "离${span.name}还有 $days 天"
        }
    }
}
