package com.nongyu.appplus.timetable.logic

/**
 * 课程卡里「教室」文字的换行策略（**纯逻辑**，无 Android / Compose 依赖，可纯 JVM 单测）。
 *
 * ## 问题（维护者 2026-11-23）
 *
 * 课程卡宽度小时，教室显示被截断成 `10-B2…`。
 * 教务给的教室形如 `10-B201室`、`雅安校区-10-B201室`、`东操场`，
 * 直接单行 Ellipsis 会切在任意位置，可读性很差。
 *
 * ## 策略
 *
 * ```
 * 1. 含「-」→ 在**第一个**横杠处断开，横杠保留在前半段末尾
 * 2. 不含「-」→ 按 [maxChars] 硬切（卡片最多可容纳的字数）
 * 3. 切完不超过两行；两行都放不下时，第二行末尾保留省略号
 * ```
 *
 * ## 为什么在第一个横杠而不是最后一个
 *
 * 教室编号的语义是「楼栋-房间」：`10-B201室` 断成 `10-` / `B201室`，
 * 两段都能独立读懂；若在最后一个横杠断（`10-B201` / `室`），后半段只剩一个「室」字，
 * 反而更难认。多段横杠（`雅安校区-10-B201室`）同理，断在第一个最自然。
 *
 * ## 为什么返回 List&lt;String&gt; 而不是带 \n 的一个字符串
 *
 * 本仓库其它地方用 `\n` 拼多行文本；但这里调用方要**逐行控制 maxLines 与省略**，
 * 返回行列表更直接，也便于单测断言每一行。
 */
object RoomLineBreaker {

    /** 断行符：教务教室里的横杠。全角「－」也算，导出数据里两种都出现过。 */
    private val DASHES = charArrayOf('-', '－')

    /**
     * 把教室文本切成最多两行。
     *
     * @param room 原始教室文本（可能为空）
     * @param maxChars 卡片单行最多容纳的字数；`<= 0` 视为不限（只按横杠断）
     * @return 1~2 行文本；空输入返回空列表，调用方据此不渲染
     */
    fun breakLines(room: String, maxChars: Int): List<String> {
        val text = room.trim()
        if (text.isEmpty()) return emptyList()

        // ① 含横杠：在第一个横杠之后断开（横杠留在前半段）
        val dashIndex = text.indexOfFirst { it in DASHES }
        if (dashIndex >= 0 && dashIndex < text.lastIndex) {
            val first = text.substring(0, dashIndex + 1)
            val second = text.substring(dashIndex + 1)
            return listOf(first, second)
        }

        // ② 不含横杠（或横杠在末尾）：按字数上限切
        if (maxChars <= 0 || text.length <= maxChars) return listOf(text)

        val first = text.take(maxChars)
        val rest = text.drop(maxChars)
        if (rest.isEmpty()) return listOf(first)

        // 两行还放不下：第二行截断并补省略号，避免出现第三行
        val second = if (rest.length <= maxChars) rest else rest.take(maxChars - 1) + "…"
        return listOf(first, second)
    }

    /**
     * 是否需要多行显示。
     *
     * 调用方据此在单行/多行之间切换，避免无谓地放宽 maxLines 影响其它卡片布局。
     */
    fun needsTwoLines(room: String, maxChars: Int): Boolean =
        breakLines(room, maxChars).size > 1

    /**
     * 给 UI 用的行列表：在 [breakLines] 基础上做**展示层**的收尾。
     *
     * ## 与 [breakLines] 的区别
     *
     * [breakLines] 严格按「横杠优先 / 字数兜底」切分，保留原文（含尾部「室」字）；
     * 本方法只做两件渲染相关的事：
     *
     * 1. 最多返回 2 行 —— 卡片高度有限，第三行会被裁掉
     * 2. 去掉每行首尾空白（横杠切断后后半段可能带空格）
     *
     * **不改动文字内容**：教室名里的「室」「楼」等字一律保留。
     */
    fun displayLines(room: String, maxChars: Int): List<String> =
        breakLines(room, maxChars)
            .take(MAX_LINES)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 卡片里最多显示两行教室。 */
    private const val MAX_LINES = 2
}
