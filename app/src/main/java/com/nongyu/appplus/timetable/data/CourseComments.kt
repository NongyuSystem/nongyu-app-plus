package com.nongyu.appplus.timetable.data

/**
 * 课程评论的「mock 后端」。
 *
 * 本期不接真实后端（地图 #11 Out of scope：网络层/教务对接），
 * 但评论与备注的存储位置不同：评论走后端、备注走本地持久层。
 * 所以这里留一个**接口形状**，将来换成真实 Repository 时只改这一处，
 * UI 不用动。数据暂存在内存态（进程内可增删，重启回到初始几条）。
 */
interface CourseCommentRepository {
    fun list(courseId: String): List<String>
    fun add(courseId: String, text: String)
    fun remove(courseId: String, index: Int)
}

/** 进程内 mock 实现，带几条初始数据便于演示可增删。 */
object MockCourseCommentRepository : CourseCommentRepository {
    private val data = mutableMapOf<String, MutableList<String>>()

    private fun bucket(courseId: String): MutableList<String> =
        data.getOrPut(courseId) {
            mutableListOf("老师讲得很清楚", "记得带教材")
        }

    override fun list(courseId: String): List<String> = bucket(courseId).toList()
    override fun add(courseId: String, text: String) {
        if (text.isNotBlank()) bucket(courseId).add(text.trim())
    }
    override fun remove(courseId: String, index: Int) {
        val b = bucket(courseId)
        if (index in b.indices) b.removeAt(index)
    }
}
