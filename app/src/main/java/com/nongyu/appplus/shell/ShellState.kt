package com.nongyu.appplus.shell

/**
 * 星云球三态（维护者 #10 定义）。
 *
 * - [Idle]：常态。底栏 2+2 导航可见，球静止。
 * - [Listening]：长按激活。界面 dim、球体微放大、动画加速，最高层。
 * - [Input]：点按激活。导航项淡出、球缩没、原地变形为输入栏。
 *
 * 三态**互斥**，且都由长按/点按/返回键驱动 —— 状态机在这里，动画参数留在 UI。
 */
enum class OrbMode { Idle, Listening, Input }

/**
 * 底部条的五形态。五者**共用同一个容器**，靠高度/圆角/内容切换实现「原地变形」：
 *
 * - [Normal]：常规导航栏（2+2 + 中间星云球）
 * - [Input]：输入态（导航项淡出，原地变输入栏）
 * - [Detail]：课程详情卡（导航项与星云球都溶解，拉高展示课程信息）
 * - [AddSchedule]：添加日程表单（从工具条或空白格进入）
 * - [ScheduleDetail]：已存在日程的明细卡
 */
enum class BarMode { Normal, Input, Detail, AddSchedule, ScheduleDetail }

/**
 * 课程详情卡的四档（维护者 2026-11-10 拍板）：
 * - 无卡片 = [BarMode.Normal]（不在此枚举里）
 * - [Brief]：简介态（三胶囊）
 * - [Full]：详情态（K-V + 考勤/评论/备注）
 * - [FullDelete]：详情态再上划一档，最底部出现「删除这天的这节课」
 *
 * 拖拽横杠在四档之间逐档吸附；拖到最底退出回到 Normal。
 */
enum class DetailStage { Brief, Full, FullDelete }

/**
 * 由「谁被打开了」推导底部条该处于哪个形态。
 *
 * **优先级是本函数存在的全部理由**：添加日程优先于详情卡（从工具条/空白格进入
 * 表单时不该同时开详情卡），日程展开态次之，然后才是课程详情卡、输入态。
 *
 * 原先这段 `when` 内联在 MainShell 的 `BoxWithConstraints` 作用域里，
 * 一是测不了，二是——因为它写在内容区之前、底栏之后才能用——维护者改顺序时
 * 非常容易把命中层级一起改错。抽成纯函数后，顺序有单测守着。
 *
 * @param addingSchedule 添加日程表单的入参；(day, startPeriod, week)，非 null = 表单开着
 * @param scheduleDetailId 日程明细卡正在展示的日程 id，非 null = 明细开着
 * @param hasDetailCourse 课程详情卡是否开着
 * @param orbMode 星云球状态
 */
fun deriveBarMode(
    addingSchedule: Any?,
    scheduleDetailId: String?,
    hasDetailCourse: Boolean,
    orbMode: OrbMode,
): BarMode = when {
    addingSchedule != null -> BarMode.AddSchedule
    scheduleDetailId != null -> BarMode.ScheduleDetail
    hasDetailCourse -> BarMode.Detail
    orbMode == OrbMode.Input -> BarMode.Input
    else -> BarMode.Normal
}

/**
 * 卡片外点击的退出动作归属。
 *
 * 三种形态规则统一（维护者 2026-11-11 拍板）：点「卡片之外」退出、点「卡片之内」不退出。
 * 返回 `null` 表示当前形态不需要「卡片外退出层」（Normal / Input 各自另有处理）。
 */
enum class DismissTarget { Detail, ScheduleDetail, AddSchedule, None }

/** [BarMode] → 卡片外点击应退出谁。纯映射，独立成函数便于单测锁定。 */
fun dismissTargetOf(barMode: BarMode): DismissTarget = when (barMode) {
    BarMode.Detail -> DismissTarget.Detail
    BarMode.ScheduleDetail -> DismissTarget.ScheduleDetail
    BarMode.AddSchedule -> DismissTarget.AddSchedule
    BarMode.Normal, BarMode.Input -> DismissTarget.None
}
