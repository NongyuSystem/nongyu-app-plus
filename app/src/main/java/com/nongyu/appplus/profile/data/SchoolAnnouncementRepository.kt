package com.nongyu.appplus.profile.data

import com.nongyu.appplus.net.api.AnnouncementApi
import com.nongyu.appplus.net.model.AnnouncementDto
import com.nongyu.appplus.profile.logic.AnnouncementRules
import com.nongyu.appplus.profile.logic.AnnouncementText
import com.nongyu.appplus.profile.model.SchoolAnnouncementView

/**
 * 校内信息公告的数据适配（票 #114）。
 *
 * 职责只有三件：调 [AnnouncementApi]、把 DTO 映射成展示模型（含 RFC3339 解析）、
 * 用 `profile/logic` 的纯函数过滤生效窗口。**不读系统时钟**：`nowMillis` 由调用方传入，
 * 便于纯 JVM 单测锁死时间边界。异常与错误码在这里归一成 [Result.Failure]，
 * 由 UI 层决定如何呈现，不把 Retrofit 异常抛到 Compose 里。
 */
class SchoolAnnouncementRepository(private val api: AnnouncementApi) {

    sealed interface Result {
        data class Success(val items: List<SchoolAnnouncementView>) : Result
        data class Failure(val message: String) : Result
    }

    /** 列表：取全部候选后按生效窗口过滤，保持后端顺序（置顶的仍排在最前）。 */
    suspend fun load(nowMillis: Long): Result = try {
        val response = api.list()
        if (!response.isSuccessful) {
            Result.Failure("校内信息加载失败（${response.code()}）")
        } else {
            val items = response.body()?.items.orEmpty().map { it.toView() }
            Result.Success(AnnouncementRules.effectiveOnly(items, nowMillis))
        }
    } catch (t: Exception) {
        // 协程取消是正常控制流，不吞掉：交回给 LaunchedEffect 处理。
        if (t is kotlinx.coroutines.CancellationException) throw t
        Result.Failure("校内信息加载失败：${t.message ?: "未知错误"}")
    }

    /**
     * 最新一条：latest/detail 信封的 `data:null` 是「当前没有生效公告」的正常空态，
     * 由 [AnnouncementRules.singleOrEmpty] 归一成空列表，不视为错误。
     */
    suspend fun loadLatest(nowMillis: Long): Result = try {
        val response = api.latest()
        if (!response.isSuccessful) {
            Result.Failure("校内信息加载失败（${response.code()}）")
        } else {
            val candidate = response.body()?.data?.toView()
            val items = AnnouncementRules.effectiveOnly(
                AnnouncementRules.singleOrEmpty(candidate),
                nowMillis,
            )
            Result.Success(items)
        }
    } catch (t: Exception) {
        // 协程取消是正常控制流，不吞掉：交回给 LaunchedEffect 处理。
        if (t is kotlinx.coroutines.CancellationException) throw t
        Result.Failure("校内信息加载失败：${t.message ?: "未知错误"}")
    }
}

private fun AnnouncementDto.toView(): SchoolAnnouncementView = SchoolAnnouncementView(
    id = id?.toString() ?: "latest",
    title = title?.takeIf { it.isNotBlank() } ?: "未命名公告",
    body = body ?: "",
    publishedAt = publishedAt,
    effectiveFromRaw = effectiveFrom,
    // 解析失败给 null（无边界），交给纯函数判定，不在这里编造时间。
    effectiveFromMillis = AnnouncementText.parseRfc3339Millis(effectiveFrom),
    expiresAtRaw = expiresAt,
    expiresAtMillis = AnnouncementText.parseRfc3339Millis(expiresAt),
    pinned = pinned ?: false,
)
