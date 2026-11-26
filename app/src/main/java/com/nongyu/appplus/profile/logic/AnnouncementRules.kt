package com.nongyu.appplus.profile.logic

import com.nongyu.appplus.profile.model.SchoolAnnouncementView

/**
 * 校内信息公告的生效判定（票 #114，纯 JVM 单测覆盖）。
 *
 * 全部函数确定性、不读系统时钟、不碰 Android/Compose：
 * 需要「现在」的地方由调用方显式传入 epoch 毫秒。时间窗为左闭右开区间
 * `[effectiveFrom, expiresAt)`，缺失一端表示该端无边界。
 */
object AnnouncementRules {

    /**
     * 单条公告在 [nowMillis] 是否生效。
     *
     * - 两端都空 → 恒生效；
     * - 只有起点 → `now >= from`；
     * - 只有终点 → `now < to`；
     * - 两端都有 → `from <= now < to`；窗口颠倒（`from >= to`）时恒不生效。
     */
    fun isEffectiveAt(
        effectiveFromMillis: Long?,
        expiresAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (effectiveFromMillis != null && expiresAtMillis != null &&
            effectiveFromMillis >= expiresAtMillis
        ) {
            return false
        }
        if (effectiveFromMillis != null && nowMillis < effectiveFromMillis) return false
        if (expiresAtMillis != null && nowMillis >= expiresAtMillis) return false
        return true
    }

    /** 从候选列表里筛出当前生效的公告，保持输入顺序。空列表/全失效都返回空列表。 */
    fun effectiveOnly(
        items: List<SchoolAnnouncementView>,
        nowMillis: Long,
    ): List<SchoolAnnouncementView> = items.filter {
        isEffectiveAt(it.effectiveFromMillis, it.expiresAtMillis, nowMillis)
    }

    /**
     * latest/detail 信封的处理：`data == null` 是 HTTP 200 的正常空态（无生效公告），
     * 归一成空列表而非错误；非 null 则是单条候选，仍要走 [effectiveOnly] 的时间窗过滤。
     */
    fun singleOrEmpty(data: SchoolAnnouncementView?): List<SchoolAnnouncementView> =
        if (data == null) emptyList() else listOf(data)
}
