package com.nongyu.appplus.timetable.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import com.nongyu.appplus.timetable.model.PERIOD_COUNT
import com.nongyu.appplus.timetable.model.TOTAL_WEEKS
import com.nongyu.appplus.timetable.data.weekOfTerm
import com.nongyu.appplus.timetable.data.mondayOfWeek
import com.nongyu.appplus.timetable.data.MOCK_COURSES
import com.nongyu.appplus.timetable.data.MOCK_SCHEDULES

/**
 * 课表数据模型的单测（票 #12 口径）。
 *
 * 这套模型的风险集中在三处，且都**肉眼看不出来**：
 * 1. 单双周过滤写反（奇偶颠倒），界面看起来「有课」，只是上错了周；
 * 2. 连堂合并把不该合并的合成一块，或该合并的没合并；
 * 3. 学期周次算错，整张表显示的是别的周。
 *
 * 所以这里只测这类会静默出错的地方。
 */
class CourseEntryTest {

    private fun entry(
        id: String = "c1",
        name: String = "高等数学",
        room: String = "1-101",
        day: Int = 0,
        start: Int = 1,
        end: Int = 2,
        weeks: WeekRange = WeekRange(1, 16),
        weeksList: List<Int>? = null,
        odd: Boolean = true,
        even: Boolean = true,
    ) = CourseEntry(
        id = id, name = name, teacher = "老师", room = room,
        day = day, startPeriod = start, endPeriod = end,
        weeks = weeks, weeksList = weeksList, odd = odd, even = even,
    )

    // ---------- 基本形态 ----------

    @Test
    fun `spanRows 与 isSpanning 正确`() {
        assertEquals(1, entry(start = 7, end = 7).spanRows)
        assertEquals(2, entry(start = 1, end = 2).spanRows)
        assertEquals(4, entry(start = 1, end = 4).spanRows)
        assertFalse(entry(start = 7, end = 7).isSpanning)
        assertTrue(entry(start = 1, end = 2).isSpanning)
    }

    @Test
    fun `covers 覆盖区间内所有节次`() {
        val e = entry(start = 1, end = 4)
        (1..4).forEach { assertTrue("应覆盖第 $it 节", e.covers(it)) }
        assertFalse(e.covers(5))
        assertFalse(e.covers(0))
    }

    @Test
    fun `非法参数在构造时抛出`() {
        assertThrows(IllegalArgumentException::class.java) { entry(day = 7) }
        assertThrows(IllegalArgumentException::class.java) { entry(day = -1) }
        assertThrows(IllegalArgumentException::class.java) { entry(start = 0) }
        assertThrows(IllegalArgumentException::class.java) { entry(start = 5, end = 3) }
        assertThrows(IllegalArgumentException::class.java) { entry(end = PERIOD_COUNT + 1) }
        assertThrows(IllegalArgumentException::class.java) { entry(id = " ") }
        assertThrows(IllegalArgumentException::class.java) { entry(name = "  ") }
    }

    @Test
    fun `WeekRange 越界时抛异常`() {
        assertThrows(IllegalArgumentException::class.java) { WeekRange(0, 16) }
        assertThrows(IllegalArgumentException::class.java) { WeekRange(8, 4) }
        assertThrows(IllegalArgumentException::class.java) { WeekRange(1, TOTAL_WEEKS + 1) }
    }

    // ---------- 周次过滤（最容易写反的地方） ----------

    @Test
    fun `全周课在任意周都出现`() {
        val e = entry(weeks = WeekRange(1, 16), odd = true, even = true)
        (1..16).forEach { assertTrue("第 $it 周应出现", e.occursIn(it)) }
    }

    @Test
    fun `单周课只在奇数周出现`() {
        val e = entry(weeks = WeekRange(1, 16), odd = true, even = false)
        listOf(1, 3, 5, 7, 9, 11, 13, 15).forEach {
            assertTrue("第 $it 周（奇）应出现", e.occursIn(it))
        }
        listOf(2, 4, 6, 8, 10, 12, 14, 16).forEach {
            assertFalse("第 $it 周（偶）不应出现", e.occursIn(it))
        }
    }

    @Test
    fun `双周课只在偶数周出现`() {
        val e = entry(weeks = WeekRange(1, 16), odd = false, even = true)
        listOf(2, 4, 6, 8, 10, 12, 14, 16).forEach {
            assertTrue("第 $it 周（偶）应出现", e.occursIn(it))
        }
        listOf(1, 3, 5, 7, 9, 11, 13, 15).forEach {
            assertFalse("第 $it 周（奇）不应出现", e.occursIn(it))
        }
    }

    @Test
    fun `超出 weeks 区间的周不出现`() {
        val short = entry(weeks = WeekRange(1, 8))
        assertTrue(short.occursIn(1))
        assertTrue(short.occursIn(8))
        assertFalse("第 9 周超出 1-8 区间", short.occursIn(9))
        assertFalse(short.occursIn(16))
    }

    @Test
    fun `weeksList 优先于 weeks 区间`() {
        // weeks 说 1-16 都有，但 weeksList 只列了 3 个周，应以 weeksList 为准
        val e = entry(weeks = WeekRange(1, 16), weeksList = listOf(1, 2, 3))
        assertTrue(e.occursIn(1))
        assertTrue(e.occursIn(3))
        assertFalse("weeksList 里没有第 4 周", e.occursIn(4))
        assertFalse(e.occursIn(16))
    }

    @Test
    fun `weeksList 与单双周同时存在时两者都要满足`() {
        // 第 2 周在 weeksList 里，但 odd-only 的课不该出现在偶数周
        val e = entry(weeks = WeekRange(1, 16), weeksList = listOf(1, 2, 3), odd = true, even = false)
        assertTrue(e.occursIn(1))
        assertFalse("第 2 周是偶数周，单周课不应出现", e.occursIn(2))
        assertTrue(e.occursIn(3))
    }

    // ---------- 按周过滤与分组 ----------

    @Test
    fun `coursesInWeek 按周过滤`() {
        val all = MOCK_COURSES
        val week1 = all.coursesInWeek(1)
        val week2 = all.coursesInWeek(2)
        // 单周课只在奇数周出现
        assertTrue("第 1 周应含单周课", week1.any { it.name.contains("单周") })
        assertFalse("第 2 周不应含单周课", week2.any { it.name.contains("单周") })
        // 双周课只在偶数周出现
        assertFalse("第 1 周不应含双周课", week1.any { it.name.contains("双周") })
        assertTrue("第 2 周应含双周课", week2.any { it.name.contains("双周") })
    }

    @Test
    fun `短课在超过其周次后消失`() {
        val dataStructure = { week: Int ->
            MOCK_COURSES.coursesInWeek(week).any { it.name == "数据结构" }
        }
        assertTrue("第 1 周应有数据结构", dataStructure(1))
        assertTrue("第 8 周应有数据结构", dataStructure(8))
        assertFalse("第 9 周不应有数据结构（只上 1-8 周）", dataStructure(9))
    }

    @Test
    fun `groupedByDay 总是返回 7 组且内部有序`() {
        val grouped = MOCK_COURSES.groupedByDay()
        assertEquals(7, grouped.size)
        grouped.forEachIndexed { day, list ->
            val starts = list.map { it.startPeriod }
            assertEquals("第 $day 天未按节次排序", starts.sorted(), starts)
            list.forEach { assertEquals("条目被放错了天", day, it.day) }
        }
    }

    @Test
    fun `同一天节次重叠会被拒绝`() {
        val overlapping = listOf(
            entry(id = "a", day = 0, start = 1, end = 4),
            entry(id = "b", day = 0, start = 3, end = 6),
        )
        assertThrows(IllegalArgumentException::class.java) { overlapping.groupedByDay() }
    }

    @Test
    fun `不同天节次重叠是允许的`() {
        val differentDays = listOf(
            entry(id = "a", day = 0, start = 1, end = 4),
            entry(id = "b", day = 1, start = 3, end = 6),
        )
        assertEquals(7, differentDays.groupedByDay().size)
    }

    // ---------- 连堂合并（口径：只按节次相连，不跨午休限制） ----------

    @Test
    fun `同课同地方相邻节次会合并成一条`() {
        val parts = listOf(
            entry(id = "1", start = 1, end = 2),
            entry(id = "2", start = 3, end = 4),
        )
        val merged = parts.mergeAdjacent()
        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startPeriod)
        assertEquals(4, merged[0].endPeriod)
    }

    @Test
    fun `不同课程不合并`() {
        val parts = listOf(
            entry(id = "1", name = "高数", start = 1, end = 2),
            entry(id = "2", name = "英语", start = 3, end = 4),
        )
        assertEquals(2, parts.mergeAdjacent().size)
    }

    @Test
    fun `不同地点不合并`() {
        val parts = listOf(
            entry(id = "1", room = "A", start = 1, end = 2),
            entry(id = "2", room = "B", start = 3, end = 4),
        )
        assertEquals(2, parts.mergeAdjacent().size)
    }

    @Test
    fun `不同周次不合并`() {
        val parts = listOf(
            // 第 1 条奇数周，第 2 条偶数周：不是同一组课，不能合并
            entry(id = "1", start = 1, end = 2, odd = true, even = false),
            entry(id = "2", start = 3, end = 4, odd = false, even = true),
        )
        assertEquals(2, parts.mergeAdjacent().size)
    }

    @Test
    fun `节次不相邻不合并`() {
        val parts = listOf(
            entry(id = "1", start = 1, end = 2),
            entry(id = "2", start = 4, end = 5),   // 缺第 3 节
        )
        assertEquals(2, parts.mergeAdjacent().size)
    }

    @Test
    fun `跨午休（4 到 5）仍然合并`() {
        // 票 #12 第 6 条：维护者明确选择「只按节次相连」，不限制跨午休。
        // 这条测试是**钉住该口径**，防止后人「顺手优化」成不跨午休。
        val parts = listOf(
            entry(id = "1", start = 3, end = 4),
            entry(id = "2", start = 5, end = 6),
        )
        val merged = parts.mergeAdjacent()
        assertEquals("跨午休应合并成一块", 1, merged.size)
        assertEquals(3, merged[0].startPeriod)
        assertEquals(6, merged[0].endPeriod)
    }

    @Test
    fun `连续三段合并成一段`() {
        val parts = listOf(
            entry(id = "1", start = 1, end = 2),
            entry(id = "2", start = 3, end = 4),
            entry(id = "3", start = 5, end = 6),
        )
        val merged = parts.mergeAdjacent()
        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startPeriod)
        assertEquals(6, merged[0].endPeriod)
    }

    // ---------- 学期周次计算 ----------

    @Test
    fun `学期首日当天是第 1 周`() {
        val termStart = LocalDate.of(2026, 9, 7)   // 周一
        assertEquals(1, weekOfTerm(termStart, termStart))
    }

    @Test
    fun `第 7 天仍是第 1 周，第 8 天进入第 2 周`() {
        val termStart = LocalDate.of(2026, 9, 7)
        assertEquals(1, weekOfTerm(termStart.plusDays(6), termStart))
        assertEquals(2, weekOfTerm(termStart.plusDays(7), termStart))
    }

    @Test
    fun `开学前被钳制到第 1 周`() {
        val termStart = LocalDate.of(2026, 9, 7)
        assertEquals(1, weekOfTerm(termStart.minusDays(1), termStart))
        assertEquals(1, weekOfTerm(termStart.minusDays(30), termStart))
    }

    @Test
    fun `超过总周数被钳制到最后一击`() {
        val termStart = LocalDate.of(2026, 9, 7)
        assertEquals(TOTAL_WEEKS, weekOfTerm(termStart.plusWeeks(20), termStart))
        assertEquals(TOTAL_WEEKS, weekOfTerm(termStart.plusWeeks(100), termStart))
    }

    @Test
    fun `周次计算在中途也正确`() {
        val termStart = LocalDate.of(2026, 9, 7)
        assertEquals(5, weekOfTerm(termStart.plusWeeks(4), termStart))
        assertEquals(16, weekOfTerm(termStart.plusWeeks(15), termStart))
    }

    @Test
    fun `mondayOfWeek 返回本周一`() {
        // 2026-09-10 是周四
        val thursday = LocalDate.of(2026, 9, 10)
        assertEquals(LocalDate.of(2026, 9, 7), mondayOfWeek(thursday))
        // 周一自身返回自己
        assertEquals(LocalDate.of(2026, 9, 7), mondayOfWeek(LocalDate.of(2026, 9, 7)))
        // 周日归到同一周的周一
        assertEquals(LocalDate.of(2026, 9, 7), mondayOfWeek(LocalDate.of(2026, 9, 13)))
    }

    // ---------- mock 数据自检 ----------

    @Test
    fun `mock 数据覆盖四种周次形态`() {
        // 全周
        assertTrue("缺少全周课", MOCK_COURSES.any { it.odd && it.even })
        // 单周
        assertTrue("缺少单周课", MOCK_COURSES.any { it.odd && !it.even })
        // 双周
        assertTrue("缺少双周课", MOCK_COURSES.any { !it.odd && it.even })
        // 短课
        assertTrue("缺少短课", MOCK_COURSES.any { it.weeks.end < TOTAL_WEEKS })
        // weeksList 不规则周次
        assertTrue("缺少 weeksList 用例", MOCK_COURSES.any { it.weeksList != null })
        // 四节大课
        assertTrue("缺少跨度 >= 4 的连堂课", MOCK_COURSES.any { it.spanRows >= 4 })
    }

    @Test
    fun `mock 数据本身合法且不重叠`() {
        MOCK_COURSES.groupedByDay()
        MOCK_COURSES.forEach {
            assertTrue(it.startPeriod in 1..PERIOD_COUNT)
            assertTrue(it.endPeriod in it.startPeriod..PERIOD_COUNT)
            assertTrue(it.day in 0..6)
        }
        // id 唯一（详情卡与日程靠它关联）
        assertEquals(MOCK_COURSES.size, MOCK_COURSES.map { it.id }.toSet().size)
    }

    @Test
    fun `mock 日程的空 weeksList 视为全周`() {
        val s = MOCK_SCHEDULES[0]
        assertTrue(s.weeksList.isEmpty())
        (1..16).forEach { assertTrue("空 weeksList 应在第 $it 周都出现", s.occursIn(it)) }
    }

    @Test
    fun `schedulesInWeek 按周过滤日程`() {
        val onlyWeek3 = MOCK_SCHEDULES + ScheduleEntry(
            id = "s-x", studentId = MOCK_STUDENT_ID, title = "只第三周", content = "",
            location = "", day = 0, startPeriod = 1, endPeriod = 1,
            weeksList = listOf(3),
            createdAt = "", updatedAt = "",
        )
        assertTrue(onlyWeek3.schedulesInWeek(3).any { it.id == "s-x" })
        assertFalse(onlyWeek3.schedulesInWeek(4).any { it.id == "s-x" })
        // 全周日程任何周都在
        assertTrue(onlyWeek3.schedulesInWeek(9).any { it.id == "s-1" })
    }
}
