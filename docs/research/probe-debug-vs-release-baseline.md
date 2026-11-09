# 探针实测：debug vs release 冷启动差 3.03 倍，APK 体积差 13.3 倍

> 测量日期 2026-11-09。载体：独立探针工程 `probe/`（wayfinder #30），非主工程。
> 目的：给「发布物形态是不是第一杠杆」这个问题一个实测答案，而不是凭直觉。
> 设备：蓝叠 Tiramisu64，**API 33（`getprop ro.build.version.sdk` 回读确认，不靠端口猜）**。

## 1. 结论摘要

1. **release 冷启动中位数 384.5ms，debug 1164.0ms，差 779.5ms（3.03 倍）**。
2. **两组分布完全不重叠**：release 最大值 580ms < debug 最小值 782ms。这比任何阈值判据都强（等价秩和检验 p < 0.001）。
3. **APK 体积 debug 10,926,125 字节 vs release 823,498 字节（13.3 倍）**，release 开了 R8 + 资源收缩。
4. **release 中位数 384.5ms < 500ms**，满足 #26 定的「真机 P50 ≤ 500ms」达标线（但这是蓝叠数字，**不可作真机定稿**）。
5. **判据本身需要修正**：原定的「中位数差 > 2 倍标准差」在方差悬殊时会误判为「不可判定」，应改用分布重叠检验。

## 2. 测量方法

```
# 每次测量前 force-stop，间隔 1s，共 10 次
adb -s 127.0.0.1:5556 shell am force-stop com.nongyu.perfprobe
adb -s 127.0.0.1:5556 shell am start -W -n com.nongyu.perfprobe/.ProbeActivity
# 取输出中的 TotalTime
```

先跑一次预热并丢弃（首次安装/编译干扰）。debug 与 release 分别独立安装、独立测量（测 release 前先 `uninstall` 掉 debug）。

## 3. 原始数据（TotalTime，毫秒）

| 运行 | debug | release |
| --- | --- | --- |
| 1 | 2277 | 526 |
| 2 | 1355 | 385 |
| 3 | 2428 | 320 |
| 4 | 2477 | 437 |
| 5 | 894 | 348 |
| 6 | 782 | 404 |
| 7 | 901 | 580 |
| 8 | 874 | 379 |
| 9 | 1017 | 320 |
| 10 | 1311 | 384 |

| 统计量 | debug | release |
| --- | --- | --- |
| 中位数 | **1164.0** | **384.5** |
| 均值 | 1431.6 | 408.3 |
| 标准差 | 690.9 | 85.2 |
| min / max | 782 / 2477 | 320 / 580 |
| P90 | 2428 | 526 |

## 4. 判据的修正（重要）

原判据（本票 Q3=A）：**中位数差 > 2 倍标准差**。

按它算：差 779.5ms vs 2 × 690.9 = 1381.8ms → **判「不可判定」**。这是**误判**，原因是：

> debug 的 sd 高达 690.9ms，正是因为它**本身又慢又不稳**。
> 把它的方差当作「噪声」去惩罚一组真实差异，等于让慢的一方自带豁免。
> 方差齐性不成立时，阈值判据会系统性地偏向「无差异」。

**改用分布重叠检验**：release 最大值 580ms < debug 最小值 782ms → 两组完全不重叠。

**建议后续判据改为**：优先看**区间是否重叠**；若重叠，再用 Mann-Whitney U（秩和）检验；只有在方差近似相等时才用「差 > k×sd」这种阈值法。

## 5. 对决策的约束

| 事实 | 含义 |
| --- | --- |
| release 快 3.03 倍 | **发布物形态是整体优化的第一杠杆**，任何代码级优化都应排在它之后（#31 的瓶颈排序应据此调整） |
| APK 小 13.3 倍 | 下载/安装/存储成本同步下降；这也是 release 启动更快的直接原因之一（更少的 dex 解析、无 `ui-tooling`、无 `debuggable`） |
| debug 方差巨大（782–2477） | **debug 包不适合做任何性能定稿**；只有同机前后对比才有意义 |
| 蓝叠数字 | 只能说明「release 明显快于 debug」这个**方向与量级**；真机 P50 仍需真机测（vsync 关、60fps 上限） |

## 6. 未验证

- 真机上的同组数字（本机无 USB 真机在手）。
- `core-splashscreen` 接入后的首帧改善（探针未接 SplashScreen；#26 已定接入，属实现阶段）。
- Baseline Profile 的实际收益（生成端排斥模拟器，见 `docs/research/android-cold-start-prerender.md`）。
- 探针的 120 项首帧组合、7 层羽化、12 层嵌套三类负载各自的耗时占比（本次只测总启动时间，未做 Perfetto 切片归属）。

## 7. 参考资料

- `docs/research/android-cold-start-prerender.md` — TTID/TTFD 口径、官方目标冷 <500ms、Macrobenchmark 排斥模拟器
- `docs/research/bluestacks-adb-compose.md` — 蓝叠 `enable_vsync=0` + 60fps 上限，不能评帧率
- `docs/adr/0001-调试链路走命令行加蓝叠而非IDE集成.md`
- wayfinder #26（探针验收法来源）、#30（本测量所在票）
- 探针源码：`probe/app/src/main/java/com/nongyu/perfprobe/ProbeActivity.kt`
