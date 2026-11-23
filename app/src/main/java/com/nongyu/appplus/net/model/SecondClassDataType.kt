package com.nongyu.appplus.net.model

/**
 * 二课（第二课堂）的 `dataType` **受控词表**（后端 `internal/school/secondclass/datatype.go`）。
 *
 * 与后端 36 个常量逐字对应。后端 `ParseDataType` 对未知名一律拒绝，
 * 客户端同样只认这些名字。
 *
 * ## 为什么用枚举
 * 和 [CacheDataType] 同理：调用方拿不到裸 String 入口，编译期就被限制在合法值里，
 * 杜绝「拼错一个字母 → 后端 400」与路径注入。
 *
 * ## ⚠️ 名字是后端契约
 * 全部是小驼峰（如 `creditRecordDetail`），**不要**改成 snake 或全小写。
 */
enum class SecondClassDataType(val wireValue: String) {
    // ---- 学分 / 成绩类 ----
    SECOND_CLASS_CREDIT("secondclassCredit"),
    APPLY_CREDIT("applyCredit"),
    CREDIT_RULE("creditRule"),
    GOOD_FAITH_VALUE("goodFaithValue"),
    CREDIT_RECORD_DETAIL("creditRecordDetail"),
    IMPORT_CREDIT_DETAIL("importCreditDetail"),
    PER_INTEGRATE_DETAIL("perIntegrateDetail"),
    RANKING("ranking"),
    REPORT_CARD("reportCard"),
    REPORT_CARD_SETTING("reportCardSetting"),
    CREDIT_DETAIL_OF_TYPE("creditDetailOfType"),
    REPORT_CARD_PREVIEW("reportCardPreview"),

    // ---- 活动类 ----
    ACT_DETAIL("actDetail"),
    ACT_LIST("actList"),
    ACT_MEMBER_LIST("actMemberList"),
    ACT_EVAL_LIST("actEvalList"),
    ACT_TRIVIA_LIST("actTriviaList"),
    ACT_TYPE_DICT("actTypeDict"),
    ACT_LEVEL_DICT("actLevelDict"),
    ACT_TAG_DICT("actTagDict"),
    ACT_GROUP_DICT("actGroupDict"),
    MY_COLLECT_LIST("myCollectList"),
    MY_ACT_LIST("myActList"),
    COLLEGE_CLASS_INFO("collegeClassInfo"),
    ACT_STATUS("actStatus"),
    ACT_EDIT_DETAIL("actEditDetail"),
    FIRST_REVIEW_PERSON("firstReviewPerson"),
    GPS_RANGE("gpsRange"),
    GROUP_SEARCH_LIST("groupSearchList"),
    IMPORT_MEMBER_RESULT("importMemberResult"),
    SCHOOL_GROUP_DICT("schoolGroupDict"),
    SCHOOL_LOGO("schoolLogo"),
    UPLOAD_FILE_DETAIL("uploadFileDetail"),
    UPLOAD_RESULT("uploadResult"),
    USER_SEARCH_LIST("userSearchList"),
    ACT_EVALUATE_TAG_DICT("actEvaluateTagDict"),
    ;

    companion object {
        val ALL: List<SecondClassDataType> = entries.toList()

        /**
         * 从后端裸字符串反查。
         *
         * **不宽容匹配**：大小写不符、空串、未知名一律返回 `null`，
         * 与后端 `ParseDataType` 的口径一致。
         */
        fun fromWire(raw: String?): SecondClassDataType? =
            if (raw.isNullOrEmpty()) null else ALL.firstOrNull { it.wireValue == raw }

        fun isValid(raw: String?): Boolean = fromWire(raw) != null
    }
}
