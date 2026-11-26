package com.nongyu.appplus.timetable.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ClassroomNavState] 的单测。
 *
 * 重点守住「空数据」与「认不出」两种失败**不能混成一句话**：
 * 前者是运维没录数据（不是用户的错），后者是教室名写法问题。
 */
class ClassroomNavStateTest {

    private fun ready(lat: Double?, lng: Double?) = ClassroomNavState.State.Ready(
        displayName = "一教",
        addressText = "XX 路 1 号",
        latitude = lat,
        longitude = lng,
        routesCount = 2,
    )

    @Test
    fun `有经纬度才算可定位`() {
        assertTrue(ready(30.1, 104.2).hasCoordinate)
    }

    @Test
    fun `缺任一坐标都不能定位`() {
        assertFalse(ready(null, 104.2).hasCoordinate)
        assertFalse(ready(30.1, null).hasCoordinate)
        assertFalse(ready(null, null).hasCoordinate)
    }

    @Test
    fun `没有楼栋编号时说明是整体未录入`() {
        val msg = ClassroomNavState.noDataDetail(null)
        assertTrue("应说明尚未录入教室坐标", msg.contains("还没有录入"))
        // 不能出现具体楼栋名，否则会误导用户以为是某栋的问题
        assertFalse(msg.contains("「"))
    }

    @Test
    fun `有楼栋编号时点名是哪一栋`() {
        val msg = ClassroomNavState.noDataDetail("10")
        assertTrue(msg.contains("「10」"))
        assertTrue(msg.contains("还没有录入坐标"))
    }

    @Test
    fun `空字符串按无楼栋编号处理`() {
        val msg = ClassroomNavState.noDataDetail("  ")
        assertTrue(msg.contains("还没有录入教室坐标"))
    }

    @Test
    fun `认不出时提示核对地点写法`() {
        val msg = ClassroomNavState.unrecognizedDetail("某某楼302")
        assertTrue(msg.contains("某某楼302"))
        assertTrue("应给用户可执行的指引", msg.contains("核对") || msg.contains("改成"))
    }
}
