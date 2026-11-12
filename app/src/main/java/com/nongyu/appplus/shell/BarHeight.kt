package com.nongyu.appplus.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部条的**目标高度计算**（纯逻辑，零 Compose 运行时依赖，可纯 JVM 测）。
 *
 * 原先这段 `when` 内联在 `MainShell` 的 `BoxWithConstraints` 里，直接读
 * `barMode / isCollapsing / detailStage / isDragging / addingSchedule / scheduleDetailId`
 * 六个状态，然后喂给 `Animatable` 做弹簧动画。
 *
 * 抽出来的两个动机：
 *
 * 1. **六个 key 里有四个是冗余的**：`addingSchedule`、`scheduleDetailId` 的信息
 *    已经由 `barMode` 承载（见 [deriveBarMode]）。旧代码把它们也塞进
 *    `LaunchedEffect` 的 key 里，等于给同一个变化建了多条重跑路径 ——
 *    那段注释记的「上划/下降没有动画」就是 key 设计失误的一次真实代价。
 * 2. **高数值与「是否正在拖拽」的交互要能测**：拖拽期必须 `snapTo`、松手必须 `animateTo`，
 *    这条规则错了只表现为「动画一闪而过」，肉眼难复现。
 *
 * @param barMode 当前底部条形态
 * @param isCollapsing 是否处于「退出收拢」过程（先降高度、再切内容）
 * @param detailStage 详情卡档位（只在 [BarMode.Detail] 下生效）
 * @param dragDp 拖拽中的实时位移（dp），正值 = 往上拉高
 * @param metrics 形态 → 基础高度的映射（由 UI 侧按 uiScale 算好后注入，便于测试）
 * @param navHeight 常态导航栏高度
 */
fun barTargetHeight(
    barMode: BarMode,
    isCollapsing: Boolean,
    detailStage: DetailStage,
    dragDp: Float,
    metrics: BarMetrics,
    navHeight: Dp,
): Dp {
    if (isCollapsing) return navHeight
    return when (barMode) {
        BarMode.AddSchedule -> metrics.addSchedule
        BarMode.ScheduleDetail -> metrics.scheduleDetail
        BarMode.Detail -> {
            val base = when (detailStage) {
                DetailStage.Brief -> metrics.brief
                DetailStage.Full -> metrics.full
                DetailStage.FullDelete -> metrics.fullDelete
            }
            (base.value + dragDp).coerceAtLeast(navHeight.value).dp
        }
        BarMode.Normal, BarMode.Input -> navHeight
    }
}

/**
 * 底部条各形态的基础高度（dp 值，已乘过 uiScale）。
 *
 * 做成数据类而不是几个散落的 `val`，是为了让 [barTargetHeight] 可测，
 * 同时把「详情卡三档是 128 / 340 / 384」这类魔法数字集中在一处。
 */
data class BarMetrics(
    /** 简介态：三胶囊 */
    val brief: Dp,
    /** 详情态：K-V + 考勤/评论/备注 */
    val full: Dp,
    /** 详情态再上划一档：底部出现「删除这天的这节课」 */
    val fullDelete: Dp,
    /** 添加日程表单卡 */
    val addSchedule: Dp,
    /** 日程明细卡 */
    val scheduleDetail: Dp,
)

/** 松手后三档吸附的去向。 */
sealed interface DetailSnap {
    /** 吸附到某一档 */
    data class To(val stage: DetailStage) : DetailSnap

    /** 拖到底：进入退出收拢 */
    data object Collapse : DetailSnap
}

/**
 * 详情卡拖拽松手后的**三档吸附判定**（纯逻辑，可测）。
 *
 * 规则（维护者 2026-11-10 拍板）：
 * - 阈值取相邻两档的**中点**；
 * - 低于简介档 55% 视为「拖到底」→ 退出（[DetailSnap.Collapse]）；
 * - 其余按落在哪个区间吸附到对应档。
 *
 * 「拖到底」**不能**直接清 `detailCourse`：那样 AnimatedContent 当帧就把内容换成导航栏、
 * 收拢动画根本来不及跑。所以只返回 [DetailSnap.Collapse]，由调用方先置 isCollapsing。
 *
 * @param currentStage 松手时所在的档位
 * @param dragDp 本次手势累计偏移（dp，正值 = 往上拉高）
 */
fun snapDetailStage(
    currentStage: DetailStage,
    dragDp: Float,
    metrics: BarMetrics,
): DetailSnap {
    val briefDp = metrics.brief.value
    val fullDp = metrics.full.value
    val delDp = metrics.fullDelete.value
    val base = when (currentStage) {
        DetailStage.Brief -> briefDp
        DetailStage.Full -> fullDp
        DetailStage.FullDelete -> delDp
    }
    val current = base + dragDp
    val midBriefFull = (briefDp + fullDp) / 2f
    val midFullDel = (fullDp + delDp) / 2f
    return when {
        current < briefDp * 0.55f -> DetailSnap.Collapse
        current > midFullDel -> DetailSnap.To(DetailStage.FullDelete)
        current > midBriefFull -> DetailSnap.To(DetailStage.Full)
        else -> DetailSnap.To(DetailStage.Brief)
    }
}
