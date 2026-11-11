package com.nongyu.appplus.net.model

/**
 * 回传缓存的 `dataType` **受控词表**（规格 §3.6）。
 *
 * 与后端 `internal/school/jiaowu/datatype.go` 的 15 个常量一一对应。
 * 后端 `ParseDataType` 未知名一律 400 `target_denied`，且**绝不把用户输入
 * 直接当缓存键的一段拼进去** —— 客户端同样只认这 15 个名字。
 *
 * ## 为什么用枚举而不是裸字符串
 * 调用方拿不到 `String` 入口，编译期就被限制在这 15 个值里，
 * 从根上杜绝「拼错一个字母 → 后端 400」和「把外部输入塞进 URL 路径」。
 *
 * ## ⚠️ 名字是后端契约，不是客户端命名
 * `teachingNotice` / `classroomInfo` 这类驼峰是后端原样写定的，
 * **不要**改成 snake 或小写；`@Path` 会直接用 [wireValue]。
 *
 * ## 三种顶层形状（供文档与将来 typed DTO 参考，本类不做解析）
 * - 数组（8）：course / score / exam / progress / notice / teachingNotice / competition / classroomInfo
 * - 单对象（3，可为 null）：rank / personal / teacherInfo
 * - 包装对象（4）：plan / teacherCourse / classroomCourse / kaike
 *
 * 依据：`apps/nongyu-backend-plus/docs/tech/14类回传JSON字段形状清单.md` 顶层形状分类表。
 */
enum class CacheDataType(val wireValue: String) {
    COURSE("course"),
    SCORE("score"),
    EXAM("exam"),
    PROGRESS("progress"),
    RANK("rank"),
    PLAN("plan"),
    PERSONAL("personal"),
    NOTICE("notice"),
    TEACHING_NOTICE("teachingNotice"),
    COMPETITION("competition"),
    CLASSROOM_INFO("classroomInfo"),
    TEACHER_INFO("teacherInfo"),
    TEACHER_COURSE("teacherCourse"),
    CLASSROOM_COURSE("classroomCourse"),
    KAIKE("kaike"),
    ;

    companion object {
        /** 全部合法值，按后端 `KnownDataTypes()` 的声明顺序。 */
        val ALL: List<CacheDataType> = entries.toList()

        /**
         * 从后端回传的裸字符串反查。
         *
         * **不宽容匹配**：大小写不符、空串、未知名一律返回 `null`。
         * 与后端 `ParseDataType` 的口径一致，不要在这里加 `lowercase()` 之类的兜底。
         */
        fun fromWire(raw: String?): CacheDataType? =
            if (raw.isNullOrEmpty()) null else ALL.firstOrNull { it.wireValue == raw }

        /** 该名字是否合法（不抛异常）。 */
        fun isValid(raw: String?): Boolean = fromWire(raw) != null
    }
}
