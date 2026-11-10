# 旧农屿课表参照清单（票 #17）

> 地图 #11「制作课程表」。需求 5/6/7/9 反复说「和旧农屿相同 / 看旧农屿相关内容」，
> 本文件是把那些含糊指代落成**带路径与行号的事实**，作为第 3 步（#18）的唯一视觉真源。
> **只读核对，不迁移代码。**
>
> 核对对象：`apps/nongyu-rn-app`（旧 RN 版）。
> 核对日期：2026-11-10。

---

## 一、「卡片上面有一个横杠」——已查清

维护者原话写「卡片上面有一个横杠（很难理解先看下面一条）」，但下一条讲的是时间表，**并未解释**。
现在有答案了：

**它是一个 BottomSheet 的拖动抓手条（drag handle），不是装饰分隔线。**

| 项 | 值 | 证据 |
| --- | --- | --- |
| 组件 | `@gorhom/bottom-sheet` 的 `BottomSheetModal` | `src/modules/course/components/CourseDetailSheet.tsx:197` |
| 设置方式 | `handleIndicatorStyle={styles.handle}` | 同文件 `:206` |
| 宽 | `40` | `:530` |
| 高 | `4` | `:531` |
| 圆角 | `2` | `:532` |
| 颜色 | `lightTokens.color.border` | `:529` |

## 二、详情卡怎么弹出（需求 5）

**重要事实：旧版不是「导航栏原地变形」，而是一个从底部升起的高 BottomSheet + 半透明遮罩。**

| 项 | 值 | 证据 |
| --- | --- | --- |
| 组件 | `BottomSheetModal` | `CourseDetailSheet.tsx:197` |
| 高度档位 | `snapPoints = ["42%"]`（只读） / `["68%"]`（可编辑） | `:94` |
| **宽度** | **全屏宽**，与底部导航栏**不等宽** | 库容器 `left/right: 0`；底栏是带水平内边距的胶囊 |
| 动态尺寸 | `enableDynamicSizing={false}` | `:200` |
| 下滑关闭 | `enablePanDownToClose` | `:201` |
| 圆角 | `borderTopLeftRadius: 20`、`borderTopRightRadius: 20` | `:525-526` |
| 背景 | `lightTokens.color.surface` | `:524` |
| 遮罩 | `BottomSheetBackdrop`，`opacity={0.5}` | `:112-117` |
| 内容内边距 | `paddingHorizontal: 18`、`paddingBottom: 48` | `:535-536` |
| 课程名字号 | `21`，`fontWeight: "700"`，`letterSpacing: -0.2`，`marginBottom: 12` | `:538-544` |
| **动画** | **业务未自定义**，走库默认：Android `timing 250ms + Easing.out(Easing.exp)`；iOS `spring(damping 500, stiffness 1000, mass 3)` | `@gorhom/bottom-sheet` 常量 `ANIMATION_DURATION=250` |

> ⚠️ **重大出入：需求 5 说「这个卡片和导航栏宽度一样」，但旧版详情卡是「全屏宽」。**
> 底栏是带水平内边距的胶囊（`horizontalInset` 12~20），两者并不等宽。
> 这条要在 #18 明确：跟需求（与栏同宽）还是跟旧版（全屏）。

> ⚠️ **与地图现有认知冲突**：地图「环境事实」写「需求 5 的详情卡应复用导航栏变形（AnimatedContent），不要另开 Dialog」。
> 但旧版**用的就是 BottomSheet**，且全仓**没有任何**「底栏变形为详情卡」的实现
> （`app/(tabs)/_layout.tsx:23` 是 `tabBar={() => null}`，`FloatingTabBar` 只管 Tab 切换 + AI 球）。
> 维护者需求原话「下面的导航栏会丝滑变成一个课程详情卡片……导航栏变空白后（中间的农小屿会消失）拉高之后出现内容」
> **确实更像导航栏变形，而不是 BottomSheet**。所以旧版与需求本身有出入。
> 这条已记入本票评论与地图 Fog。

## 三、详情卡里显示什么（字段排布）

渲染顺序（`CourseDetailSheet.tsx:214-431`），字号层级 `21 > 15 > 14.5 > 13~12.5 > 11.5`：

**第一层：标题** —— 课程名
`fontSize 21 / weight 700 / letterSpacing -0.2 / marginBottom 12`（`:538-544`）

**第二层：标签行**（横向 wrap，gap 7，`paddingH 9 / paddingV 5 / borderRadius 8`）
| 顺序 | 内容 | 图标 |
| --- | --- | --- |
| 1 | 地点 | `location-outline` 13px |
| 2 | 教师 | `person-outline` |
| 3 | 节次 `周{day} {start}-{end}节 · 08:00-09:35` | `time-outline` |
| 4 | 周次 `第 1-16 周（单）` | `calendar-outline` |
| 5 | 星期几 | 无 |

标签底色 `brandMuted`、文字 `fontSize 12.5 / weight 500`。

**只读模式到此为止**（`:257` `readOnly ? null : ...`）——他人分享的课表只渲染标题 + 标签。

**第三层：考勤组**（`CourseAttendanceSection`）
分隔线 `marginTop 20 / paddingTop 16 / borderTopWidth 1`；标题「考勤」15/700；
副标题「本节 · 第{week}周 · {周X} · 已记/未标记」12；
五态 chips：签到 / 迟到 / 缺勤 / 请假 / 未考勤（`fontSize 13 / paddingH 12 / paddingV 8 / radius 10`）。

**第四层：可选按钮**「在此时间添加日程」
`marginTop 16 / paddingV 10 / radius 10`，底色 `brandMuted+55`。

**第五层：备注组**
组头「备注」+ 计数 chip；输入框 `radius 14 / minHeight 48`，提交钮 34×34 圆形品牌色；
备注卡 `radius 12 / padding 12 / 左侧 3dp 品牌色竖条`，正文 `14.5 / lineHeight 21`。

**第六层：待办组**（`marginTop 28`）
组头「待办」+ `N 待完成`；待办卡 `radius 12`，圆形勾选 22×22（`borderWidth 2`），完成态 `line-through + opacity 0.6`。

**列表限高** `LIST_MAX_HEIGHT = 200`（`:26`），备注/待办各自局部滚动。

**加载态**：编辑模式下动画未结束前挂骨架 `CourseDetailExtensionsSkeleton`，
注释写明「上滑动画结束后再挂载考勤/备注/待办，避免与动画抢帧」（`:99`、`:119-122`）。

> 这条「动画结束后再挂载重内容」的做法值得直接借鉴：它解决的是
> 「弹层动画期间挂载重列表会掉帧」的问题，成本极低。

## 四、时间列 / 星期行 / 周次显示（需求 6/7/9）

### 时间列（需求 6）

**旧版一行装 2 个节次**，5 行 = 10 节，这是它实现「大课区间」的方式。

| 项 | 值 | 证据 |
| --- | --- | --- |
| 列宽 | `TIME_COL_WIDTH = 36` | `WeekGrid.tsx:21` |
| 行间距 | `ROW_GAP = 2` | `:23` |
| 大课区间数 | `MAJOR_SLOT_COUNT = 5` | `model/courseTimes.ts` |
| 单元格纵向内边距 | `paddingVertical: 12`、`paddingHorizontal: 0` | `:388-394` |
| 每行内容 | 并排两个 `timeSlot`：`rIdx*2` 与 `rIdx*2+1` | `:233-244` |
| 每个 timeSlot | `flex:1`、居中 | `:395-399` |
| 节次号 | `fontSize: 13`、`fontWeight: "700"`、`color: textSecondary`、`lineHeight: 16`、`marginVertical: 2` | `:400-406` |
| 起止时间 | `fontSize: 9`、`opacity: 0.6`、`transform: scale(0.85)`、`lineHeight: 10` | `:407-413` |

### 星期行（需求 7）

| 项 | 值 | 证据 |
| --- | --- | --- |
| 表头单元格 | `paddingVertical: 10`、`paddingHorizontal: 2`、居中 | `:374-379` |
| 星期文字 | `fontSize: 12`、`fontWeight: "600"`、`textSecondary` | `:414-419` |
| 日期文字 | `fontSize: 11`、`opacity: 0.8`、`marginTop: 2` | `:431-436` |
| 今天高亮 | 整格背景 `todayWash = brandMuted + "40"`（即约 25% 透明度）+ `borderRadius: 8` | `:123`、`:194` |
| 今天文字 | `color: brand`、`fontWeight: "700"` | `:437-440` |

### 周次显示（需求 9）

| 项 | 值 | 证据 |
| --- | --- | --- |
| 第一行 | `第{weekNumber}周`，`fontSize: 10`、`fontWeight: "700"`、`textSecondary` | `:181`、`:420-424` |
| 第二行 | `共{maxWeek}周`，`fontSize: 10`、`opacity: 0.6`、`marginTop: 2` | `:182`、`:425-430` |

> 注：本仓当前实现是「第N周 / 共N周」（中文数字），与旧版格式一致，仅字号略大（9.5/6.5sp vs 10/10px）。

## 四、课程卡片（需求 3/4）

| 项 | 值 | 证据 |
| --- | --- | --- |
| 圆角 | `borderRadius: 8` | `CourseCard.tsx:119` |
| 内边距 | `paddingVertical: 4`、`paddingHorizontal: 4` | `:120-121` |
| 最小高度 | `minHeight: 56` | `:122` |
| 内容对齐 | `justifyContent: "center"` + 文字 `textAlign: "center"` | `:123`、`:132` |
| 阴影 | `shadowOpacity: 0.1`、`shadowRadius: 2`、`elevation: 2` | `:124-128` |
| 课名 | `fontWeight: "700"`、`marginBottom: 2`、最多 4 行 | `:130-134` |
| 地点/教师 | `opacity: 0.85`、`marginTop: 1`、最多 2 行 / 1 行 | `:135-139` |
| 按下反馈 | `opacity: 0.9` + `scale: 0.95` | `:106-107` |
| 字段顺序 | **课名 → 地点 → 教师**（→ 考勤） | `:33-90` |

### 尺寸档位（旧版有 sm/md/lg 三档）

| 档 | 行高 | 课名字号 | 次级字号 | 证据 |
| --- | --- | --- | --- | --- |
| sm | 100 | 10 | 9 | `model/coursePrefs.ts:13-31` |
| md | 110 | 10 | 9 | 同上 |
| lg | 130 | 12 | 10 | 同上 |

## 五、颜色方案

**旧版也是「按课程名哈希分配颜色」**，与当前实现思路一致，但参数不同：

| 项 | 旧版 | 本仓当前 |
| --- | --- | --- |
| 哈希 | `hash = charCodeAt(i) + ((hash << 5) - hash)`，即 `h*31 + c` | FNV-1a |
| 色数 | **8** 个固定色板 | **24** 个色槽（HSL 生成） |
| 取模 | `Math.abs(hash) % 8` | `(hash >> 7) % 24` + 撞槽顺延 |
| 证据 | `model/colors.ts:30-37`、色板 `:7-16` | `CoursePalette.kt` |

旧版 8 色板（bg / text）：
`#E8F1FF/#2B5797`、`#FDF2F4/#B8325E`、`#E6F7F3/#00856F`、`#FFF7E6/#B36B00`、
`#F2F0FA/#5B4FA2`、`#EBF9F9/#007C89`、`#FFF9E6/#997B00`、`#F0F4F8/#3E5463`。

## 六、大课连堂合并（需求 14）

旧版合并条件（`model/mapJiaowuCourseItems.ts` 的 `canMergeCourseEntries`）：

```
a.name === b.name
&& a.day === b.day
&& a.teacher === b.teacher          ← 比教师，不比地点
&& a.odd === b.odd
&& a.even === b.even
&& weeksEqual(a, b)
&& b.startPeriod === a.endPeriod + 1   ← 只按节次相连，不限制跨午休
```

> **两处与本仓当前实现不一致，需要在 #18 处理：**
> 1. 旧版比 **teacher**，本仓比 **room**；
> 2. 旧版**确实不限制跨午休**（只判 `+1` 相连）——维护者在票 #12 选的「只按节次相连」与旧版行为一致，
>    反而是我当时的推荐（不跨午休）偏离了旧版。

跨节卡片高度公式（`WeekGrid.tsx:148`）：
```
spanCardHeight = effectiveRowHeight * span + ROW_GAP * (span - 1) - 4
```
即**行高 × 跨度 + 行间距 × (跨度−1) − 4**。前两项正是我上一轮踩坑的地方（间距必须计入）。

## 七、切周与回到本周（需求 8）

### 左右滑动切周

| 项 | 值 | 证据 |
| --- | --- | --- |
| 实现 | `FlatList` + `pagingEnabled`（原生分页滚动） | `WeekPager.tsx:187` |
| 切页回写 | `onMomentumScrollEnd` | `:191` |
| 程序化跳转 | `scrollToIndex` / `scrollToOffset`，`animated: false` | `:94`、`:96` |

### 「回到今天/本周」按钮

**旧版是右侧贴边的半圆 FAB，不在「周日那个位置」。**

| 项 | 值 | 证据 |
| --- | --- | --- |
| 位置 | `position: absolute`、`right: 0` | `BackToCurrentWeekFab.tsx:52-53` |
| **实际垂直位置** | **`insets.top + 56`**（安全区 + 56），不是组件默认的 64 | `CourseScreen.tsx:772`（调用点覆盖默认值 `:23`） |
| 尺寸 | `width: 48`、`height: 40` | `:55-56` |
| 圆角 | 只有左侧圆：`borderTopLeftRadius/BottomLeftRadius: 20`，右侧 0 | `:60-63` |
| 背景 | `t.color.brand`（品牌绿） | `:59` |
| 图标 | **Ionicons 字体图标** `return-up-back`，`size: 22`，`color: onBrand`。**不是 SVG** | `:45` |
| 出现动画 | `opacity` 淡入，`duration: 160` | `:30` |
| 可见性 | `pointerEvents: visible ? "auto" : "none"` | `:40` |

> **关于「SVG 样式」**：需求 8 原话要求「svg 样式的非文字按钮」，但旧版用的是 `@expo/vector-icons` 的
> **Ionicons 字体字形**（`createIconSet(glyphMap, 'ionicons', Ionicons.ttf)`），不是 SVG。
> `react-native-svg` 虽在 `package.json` 里，但 `src` 内**零使用**（grep 0 命中）。
> 若本仓要求真 SVG，需自行绘制，不能照抄旧版。

> ⚠️ 需求 8 原话是「切换到非本周/本日后**将周日那个位置**出现一个 svg 样式的非文字按钮」。
> 旧版是**右侧贴边的半圆胶囊**（right:0，宽 48），横向确实压在周日列右段并向右溢出约 4px，
> 但**不是**「周日表头单元格内部的一个控件」。两者不一致，#18 需拍板。

## 八、旧版没有的能力（本仓新增）

| 需求 | 旧版状况 | 证据 |
| --- | --- | --- |
| 9. 点击左上角弹**日历** | **左上角那两行是纯静态 Text，完全不可点**。真正的日期入口在**页面顶栏右上角**的日历图标，且它选的是「开学日」不是任意日期。**没有日历组件** | `WeekGrid.tsx:180-183`（无 Pressable）；入口 `CourseScreen.tsx:698-709`（`calendar-outline` size 20）；`SemesterStartPicker.tsx:43-45`（Android 直接调系统对话框） |
| 10. **按天**模式（一页一天） | **没有**。课表 Tab 内只有「周视图 / 考试安排」两种 pane，没有第三种分支 | `CourseScreen.tsx:109`（状态）、`:739-767`（只有两个分支） |
| 11. 右上角 4 个按钮 | 旧版顶栏有日历图标等，但未在本次核对范围内逐个数清 | — |

### 关于「日历」的补充事实

- `package.json` 里**没有任何日历/月历库**（无 `react-native-calendars` 等）。
- 只有 `@react-native-community/datetimepicker`（`package.json:21`），且只用于选开学日。
- `dayjs` 仅做日期数学，不出 UI。
- 全仓 `Calendar|MonthPicker|DatePicker` 的命中只有 `isSameCalendarDay` 函数与 `Ionicons name="calendar-outline"` 图标名，**零个日历组件**。

所以需求 9 的「点击弹日历 + 选日期直接跳转」是**本仓新增能力**，旧版无可抄。

## 九、其他可复用事实

- **同格堆叠**：多卡时有 3D 翻牌动画（`rotateY`，`react-native-reanimated`），单卡长按为「添加日程」菜单。
  证据：`StackedCellCard.tsx:52`（`rotateY`）、`:34-36` 注释。
- 旧版对「今天」的处理：**整列背景 wash（brandMuted + 40 透明度 ≈ 25%）+ 圆角 8**，
  而本仓当前是「只给日期盒子淡绿」。需求 7 原话说「今天的这一列变成淡淡的颜色（主题色的5%透明度）」——
  旧版实际是 25%，比需求写的 5% 重。这个差异也要在 #18 确认。
