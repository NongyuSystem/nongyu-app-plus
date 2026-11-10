# 课表数据模型与 mock 口径（票 #12）落地记录

> 地图 #11「制作课程表」的数据模型决策票。
> 第 2 步的视觉实现见 `docs/research/timetable-step2-cards.md`。
> 落地日期：2026-11-10 · versionCode 46 / `0.0.38test`

## 一、口径来源

三轮 grilling，维护者逐条拍板，8 项决策全部确定。
字段对齐旧农屿 `apps/nongyu-rn-app/src/modules/course/model/types.ts`。

## 二、最终模型

```kotlin
data class WeekRange(val start: Int, val end: Int)          // 含端点

data class CourseEntry(
    val id: String, val name: String, val teacher: String, val room: String,
    val day: Int,                 // 0=周一 … 6=周日（本仓口径）
    val startPeriod: Int, val endPeriod: Int,
    val weeks: WeekRange,
    val weeksList: List<Int>? = null,   // 存在则优先于 weeks
    val odd: Boolean = true, val even: Boolean = true,
)

data class ScheduleEntry(          // 与课程完全分开、不复用
    val id: String, val studentId: String,
    val title: String, val content: String, val location: String,
    val day: Int, val startPeriod: Int, val endPeriod: Int,
    val weeksList: List<Int> = emptyList(),   // 空 = 全周
    val colorIndex: Int? = null,
    val createdAt: String, val updatedAt: String,
)
```

### 为什么课程用「节次区间」而不是「每格一条」
需求 14 要求大课连成一整块。区间模型让渲染层直接知道边界；
每格一条的话要反向推断「相邻格是否同一门课」，遇同名课或调课必错。

### 为什么星期索引是 0..6 而不是旧版的 1..7
本仓 `WEEKDAY_LABELS` 的下标就是 0..6，模型内部跟它一致可省掉所有 ±1 换算。
旧口径的转换只发生在**解析教务数据的 mapping 层**，不进模型。

## 三、三个容易静默出错的地方

这三处都是「界面看起来正常、其实错了」，所以单测专门钉住：

### 1. 单双周写反
```kotlin
fun occursIn(week: Int): Boolean {
    val inWeeks = weeksList?.let { week in it } ?: weeks.covers(week)
    if (!inWeeks) return false
    if (odd && even) return true
    return when {
        week % 2 == 1 -> odd      // 奇数周看 odd
        else -> even              // 偶数周看 even
    }
}
```
`odd`/`even` 都可为 true 表示全周。写反的话课照上，只是上错了周，肉眼看不出来。

### 2. 连堂合并口径
票 #12 第 6 条：维护者明确选择**只按节次相连，不限制跨午休**。
即第 4 节与第 5 节同课也会合并，卡片会跨过左侧时间列那道午饭大间距。

单测里有一条 `跨午休（4 到 5）仍然合并` 专门**钉住这个口径**，
防止后人「顺手优化」成不跨午休。

合并条件（全部满足才合并）：同天、同课名、同地点、同周次（weeks + weeksList + odd + even）、
且 `entry.startPeriod == current.endPeriod + 1`。

### 3. 学期周次算错
```kotlin
fun weekOfTerm(today: LocalDate, termStart: LocalDate = MOCK_TERM_START): Int {
    val days = ChronoUnit.DAYS.between(termStart, today)
    val week = Math.floorDiv(days, 7L).toInt() + 1
    return week.coerceIn(1, TOTAL_WEEKS)
}
```
**必须用 `Math.floorDiv` 而不是整数除法**：开学前 `days` 为负，
整数除法向零取整会得到 0（甚至 0周），floorDiv 才对。
结果钳到 `1..TOTAL_WEEKS`，切周逻辑就不用到处判空。

## 四、mock 数据设计

刻意覆盖四种周次形态，好让「切周」的差异**真的看得出来**：

| 形态 | 例子 | 验证点 |
| --- | --- | --- |
| 全周 | 高等数学 1-16 周 | 每周都在 |
| 单周 | 体育（单周）odd=true even=false | 偶数周消失 |
| 双周 | 形势与政策（双周）odd=false even=true | 奇数周消失 |
| 短课 | 数据结构 1-8 周 | 第 9 周起消失 |
| 不规则 | 概率论 weeksList=[1,2,3,5,8,13] | 不在列表的周消失 |
| 四节大课 | 设计基础 周三 1-4 节 | 跨节连成一块 |

## 五、实测验收（API 33，1080×2400@420dpi）

装 v46 后设备显示的是**真实算出的周次**，不是写死的第 1 周：

- 表头：**「第十周」/「共16周」**
- 出现的课：高等数学、大学英语、线性代数、程序设计基础、**形势与政策（双周）**、
  设计基础、计算机网络、心理健康、操作系统、创新创业
- **未出现**：
  - 体育（单周）—— 第 10 周是偶数周 ✅
  - 数据结构 —— 只上 1-8 周 ✅
  - 概率论 —— `weeksList` 不含第 10 周 ✅

三种过滤规则（单双周、周次区间、weeksList）在同一次渲染里同时得到验证。

单测 **269 用例全绿**（新增 `CourseEntryTest` 19 例，覆盖上面三处静默错误与 mock 自检）。

## 六、落地时踩到的一个编译坑

```kotlin
fun List<CourseEntry>.forWeek(week: Int): List<CourseEntry>
fun List<ScheduleEntry>.forWeek(week: Int): List<ScheduleEntry>
```

Kotlin 扩展函数经 JVM 擦除后都变成 `forWeek(List, int)`，**平台声明冲突**，编译直接失败。
改成 `coursesInWeek` / `schedulesInWeek` 两个明确名字即可。

## 七、本票没做的事

- **同格堆叠**（同一天同一节有两门课）：模型上留了注释位指向旧版 `GridStack`，
  渲染层的重叠断言仍然保留。属 #18 范围。
- **真实数据源**：本期仍是 mock，等接教务时只需替换 `MOCK_COURSES` 的来源。
- **日程渲染**：`ScheduleEntry` 与 `MOCK_SCHEDULES` 已就位，但网格还不画日程，属 #18。
