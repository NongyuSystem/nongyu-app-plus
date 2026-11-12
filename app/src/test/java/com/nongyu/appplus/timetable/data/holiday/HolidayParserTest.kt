package com.nongyu.appplus.timetable.data.holiday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 节假日解析 / 归并 / 倒计时（纯逻辑）。
 *
 * 样例数据取自 2026-11-11 对 timor.tech 的**真实响应**（只截取片段），
 * 所以这些测试同时也在保护「字段名改了」这类回归。
 */
class HolidayParserTest {

    /** timor.tech 真实响应片段（含放假与补班两种条目）。 */
    private val timorSample = """
        {"code":0,"holiday":{
          "01-01":{"holiday":true,"name":"元旦","wage":3,"date":"2026-01-01","rest":47},
          "01-02":{"holiday":true,"name":"元旦","wage":2,"date":"2026-01-02"},
          "01-03":{"holiday":true,"name":"元旦","wage":2,"date":"2026-01-03"},
          "01-04":{"holiday":false,"name":"元旦后补班","wage":1,"date":"2026-01-04"},
          "02-14":{"holiday":false,"name":"春节前补班","wage":1,"date":"2026-02-14"},
          "02-15":{"holiday":true,"name":"春节","wage":2,"date":"2026-02-15"},
          "02-16":{"holiday":true,"name":"除夕","wage":3,"date":"2026-02-16"}
        }}
    """.trimIndent()

    private val holidayCnSample = """
        {"year":2026,"days":[
          {"name":"元旦","date":"2026-01-01","isOffDay":true},
          {"name":"元旦","date":"2026-01-02","isOffDay":true},
          {"name":"元旦","date":"2026-01-04","isOffDay":false}
        ]}
    """.trimIndent()

    @Test
    fun `解析 timor 源：放假与补班都能区分`() {
        val list = HolidayParser.parseTimor(timorSample)
        assertEquals(7, list.size)
        val newYear = list.filter { it.name == "元旦" }
        assertEquals(3, newYear.size)
        assertTrue("元旦三天都应是放假日", newYear.all { it.isOffDay })
        val makeup = list.first { it.name == "元旦后补班" }
        assertTrue("补班条目应是 isOffDay=false", !makeup.isOffDay)
    }

    @Test
    fun `解析 holiday-cn 源`() {
        val list = HolidayParser.parseHolidayCn(holidayCnSample)
        assertEquals(3, list.size)
        assertEquals(2, list.count { it.isOffDay })
        assertEquals(1, list.count { !it.isOffDay })
    }

    @Test
    fun `坏数据不抛异常，只返回空`() {
        assertTrue(HolidayParser.parseTimor("not json").isEmpty())
        assertTrue(HolidayParser.parseTimor("""{"code":-1}""").isEmpty())
        assertTrue(HolidayParser.parseHolidayCn("{}").isEmpty())
        assertTrue(HolidayParser.parseHolidayCn("").isEmpty())
    }

    @Test
    fun `连续放假日归并成一个假期，补班不参与`() {
        val spans = HolidayParser.toSpans(HolidayParser.parseTimor(timorSample))
        // 元旦 1/1~1/3 归并成一段；春节 2/15 与「除夕」2/16 名字不同 → 分成两段
        val newYear = spans.first { it.name == "元旦" }
        assertEquals(LocalDate.of(2026, 1, 1), newYear.startDate)
        assertEquals(LocalDate.of(2026, 1, 3), newYear.endDate)
        // 补班「元旦后补班」不应产生任何 span
        assertTrue("补班不该出现在假期列表里", spans.none { it.name.contains("补班") })
    }

    @Test
    fun `找未来最近的假期`() {
        val spans = HolidayParser.toSpans(HolidayParser.parseTimor(timorSample))
        val today = LocalDate.of(2026, 1, 10)
        val next = HolidayParser.nextSpan(spans, today)
        assertEquals("春节", next?.name)
    }

    @Test
    fun `已过完的假期不会被选为下一个`() {
        val spans = listOf(
            HolidaySpan("元旦", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)),
        )
        assertNull(HolidayParser.nextSpan(spans, LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun `假期进行中也算下一个，天数为 0`() {
        val span = HolidaySpan("国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(span, HolidayParser.nextSpan(listOf(span), today))
        assertEquals(0, HolidayParser.daysUntil(span, today))
        // 假期内优先报"还剩几天"（10/3 到 10/7 含当天是 5 天）
        assertEquals("国庆节假期中 · 还剩 5 天", HolidayParser.countdownText(span, today))
    }

    @Test
    fun `倒计时文案：还有 N 天 明天 假期首日`() {
        val span = HolidaySpan("元旦", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3))
        assertEquals("离元旦还有 51 天", HolidayParser.countdownText(span, LocalDate.of(2026, 11, 11)))
        assertEquals("明天是元旦", HolidayParser.countdownText(span, LocalDate.of(2026, 12, 31)))
        // 假期首日：不能误报成"今天是元旦"，应报假期进度
        assertEquals("元旦假期中 · 还剩 3 天", HolidayParser.countdownText(span, LocalDate.of(2027, 1, 1)))
        // 假期最后一天
        assertEquals("元旦假期最后一天", HolidayParser.countdownText(span, LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun `今天之后没有假期时返回 null（调用方据此隐藏）`() {
        val spans = HolidayParser.toSpans(HolidayParser.parseTimor(timorSample))
        assertNull(HolidayParser.nextSpan(spans, LocalDate.of(2026, 3, 1)))
    }
}
