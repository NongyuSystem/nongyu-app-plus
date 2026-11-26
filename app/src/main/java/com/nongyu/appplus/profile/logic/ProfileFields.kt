package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.ProfileDetailExit
import com.nongyu.appplus.profile.model.ProfileFieldItem
import com.nongyu.appplus.profile.model.ProfileFieldKey
import com.nongyu.appplus.profile.model.ProfileSnapshot
import com.nongyu.appplus.profile.model.SessionRemaining

/**
 * 画像卡 / 画像字段详情卡的纯判定接缝（票 #113）。
 *
 * 全部函数都是确定性的：不读系统时钟、不碰 Android、不碰网络，需要「现在」的地方
 * 由调用方显式传入。字段取舍、复制文案、详情标题、退出目标这些最容易写错的
 * 「when 顺序」和「文案映射」全部在这里，由纯 JVM 单测锁定。
 */
object ProfileFields {

    /**
     * 画像卡字段顺序（规格 #111：姓名、学号、班级、专业、年级；学院为补充字段）。
     * [ProfileFieldKey.ID_CARD_SUFFIX] 永远不在这个列表里。
     */
    private val CARD_ORDER: List<Pair<ProfileFieldKey, (ProfileSnapshot) -> String?>> = listOf(
        ProfileFieldKey.NAME to { it.name },
        ProfileFieldKey.STUDENT_NO to { it.studentNo },
        ProfileFieldKey.CLASS_NAME to { it.className },
        ProfileFieldKey.MAJOR to { it.major },
        ProfileFieldKey.GRADE to { it.grade },
        ProfileFieldKey.COLLEGE to { it.college },
    )

    /** 从上卡字段中按域键找一条；找不到（含敏感字段）返回 null。 */
    fun fieldByKey(fields: List<ProfileFieldItem>, key: ProfileFieldKey): ProfileFieldItem? =
        fields.firstOrNull { it.key == key }

    /** 从画像快照抽出可上卡的字段，跳过 null / 空 / 纯空白值。 */
    fun of(profile: ProfileSnapshot?): List<ProfileFieldItem> {
        if (profile == null) return emptyList()
        return CARD_ORDER.mapNotNull { (key, extract) ->
            val value = extract(profile)
            if (value.isNullOrBlank()) null else ProfileFieldItem(key, labelOf(key), value)
        }
    }

    /** 敏感字段不可复制；空值不可复制；其余画像字段可复制。 */
    fun isCopyable(field: ProfileFieldItem): Boolean =
        field.key != ProfileFieldKey.ID_CARD_SUFFIX && field.value.isNotBlank()

    /** 复制成功的 Toast 文案，明确到具体字段（「已复制学号」级别），绝不提整张名片。 */
    fun copyToastOf(field: ProfileFieldItem): String? =
        if (isCopyable(field)) "已复制${field.label}" else null

    /** 画像字段详情卡的标题。 */
    fun detailTitleOf(field: ProfileFieldItem): String = "${field.label}详情"

    /** 详情卡退出目标：只有详情开着时才回画像卡，否则无动作。 */
    fun exitTargetOf(detailOpen: Boolean): ProfileDetailExit =
        if (detailOpen) ProfileDetailExit.BACK_TO_CARD else ProfileDetailExit.NONE

    /**
     * 详情卡拖拽退出判定：绝对值越过阈值即退出。
     *
     * 既有底栏详情形态的退出语义是「上滑一档越过阈值 → 收拢回常态」。画像字段详情
     * 复用这条语义，两个方向都算退出，阈值由 UI 侧按 uiScale 传入。
     */
    fun dismissByDrag(dragDp: Float, thresholdDp: Float): Boolean =
        kotlin.math.abs(dragDp) > thresholdDp && thresholdDp > 0f

    /** 登录态剩余期文案；`null`（无 expiresAt）不上卡。 */
    fun remainingLabelOf(remaining: SessionRemaining?): String? {
        if (remaining == null) return null
        if (remaining.isExpired) return "登录已过期"
        if (remaining.isUnderDay) {
            return when {
                remaining.totalMinutes >= 1L -> "登录剩余 ${remaining.totalMinutes} 分钟"
                else -> "登录剩余 ${remaining.totalSeconds} 秒"
            }
        }
        val days = remaining.totalSeconds / 86_400L
        val hours = (remaining.totalSeconds % 86_400L) / 3_600L
        return if (hours > 0L) "登录剩余 $days 天 $hours 小时" else "登录剩余 $days 天"
    }

    /**
     * demo 兜底：只有真实缓存为空且开关打开时才用 demo。
     * 开关关闭时一律返回空快照（release 包根本没有 demo 实例可传）。
     */
    fun demoFallback(
        real: ProfileSnapshot?,
        demo: ProfileSnapshot?,
        enabled: Boolean,
    ): ProfileSnapshot = if (enabled && (real == null || !ProfileLogic.isCacheNonEmpty(real))) {
        demo ?: ProfileSnapshot()
    } else {
        real ?: ProfileSnapshot()
    }

    /**
     * demo 兜底生效时，页面按「已登录」展示（但绝不写凭证、不发请求）。
     * 没有 demo 或真实凭证存在时，维持真实凭证判定。
     */
    fun hasEffectiveCredential(hasRealCredential: Boolean, demoActive: Boolean): Boolean =
        hasRealCredential || demoActive

    private fun labelOf(key: ProfileFieldKey): String = when (key) {
        ProfileFieldKey.NAME -> "姓名"
        ProfileFieldKey.STUDENT_NO -> "学号"
        ProfileFieldKey.CLASS_NAME -> "班级"
        ProfileFieldKey.MAJOR -> "专业"
        ProfileFieldKey.GRADE -> "年级"
        ProfileFieldKey.COLLEGE -> "学院"
        // 敏感字段没有展示名；真走到这里也只返回空串，测试断言它不可复制。
        ProfileFieldKey.ID_CARD_SUFFIX -> ""
    }
}
