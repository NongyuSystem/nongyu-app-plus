package com.nongyu.appplus.profile.model

/**
 * 画像字段的模型（`profile/model`，纯 JVM，禁止 import android.* / androidx.compose.*）。
 *
 * 票 #113：画像卡字段的取舍、复制文案、详情标题、退出目标全部由
 * `profile/logic/ProfileFields` 纯函数推导，本文件只声明这些判定用到的数据结构。
 */

/**
 * 画像字段的域键。
 *
 * [ID_CARD_SUFFIX] 特意声明在此处而不是省略：它是**必须被排除**的敏感字段，
 * 让「排除它」这件事在测试里可表达、可锁定（见 ProfileFieldsTest）。
 */
enum class ProfileFieldKey {
    NAME,
    STUDENT_NO,
    CLASS_NAME,
    MAJOR,
    GRADE,
    COLLEGE,
    /** 身份证后四位：永不展示、永不复制，仅作为「禁止项」存在。 */
    ID_CARD_SUFFIX,
}

/** 一条可上卡的画像字段：域键 + 展示名 + 值。 */
data class ProfileFieldItem(
    val key: ProfileFieldKey,
    val label: String,
    val value: String,
)

/** 画像字段详情的退出目标（纯判定结果，UI 据此决定收拢动作）。 */
enum class ProfileDetailExit {
    /** 详情开着：退出回到画像卡（复用既有底栏详情形态的收拢语义）。 */
    BACK_TO_CARD,
    /** 详情没开：无退出动作。 */
    NONE,
}
