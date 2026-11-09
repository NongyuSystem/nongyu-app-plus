# 资源通道：先把手绘图标数据化，资源走 ResourcesLoader，字体允许加依赖

皮肤包现在只剩资源载荷（色值已由 ADR 0004 移到数据库目录接口）。但本工程的导航图标是 `DrawScope` 手绘代码、`res/` 里没有任何业务图形 —— **包下下来没有东西能换**。所以先决条件是把手绘图标变成数据。维护者进一步拍板：资源走 `ResourcesLoader`（而非纯文件解码）、字体本期做且允许引入字体依赖、launcher icon 明确不做。

## Status

accepted（2026-11-08，维护者在 wayfinder #25 拍板）

## 决策内容

| 项 | 结论 |
| --- | --- |
| 图标形态 | **先改造为数据驱动**（Q1=A）：每个 `NavIcon` 对应一组可序列化的绘制参数（或构建成 `ImageVector`），使「换包 = 换参数」成立。这是资源通道对图标生效的**唯一前提** |
| 字体 | **本期做，且允许引入字体相关依赖**（维护者 Q2 自定义原话：「添加字体相关依赖可以直接用」）。加载走 `Typeface.createFromFile`；具体依赖选型留给 `/to-spec` |
| 加载路径 | **`ResourcesProvider` → `ResourcesLoader` → `Resources.addLoaders`**（Q3=B，API 30+）；API 26–29 无公开等价，需回落 `BitmapFactory.decodeFile` / `Typeface.createFromFile` |
| 安全校验 | **沿用 ADR 0003 的整包校验**（逐文件 `sha256` + 整包签名），不额外加机制；**补一条硬约束**：禁止包内出现 DEX / so / 可执行文件与未知扩展名，校验阶段直接拒绝 |
| 降级粒度 | **逐资源回落**（Q5=A）：单个图标缺失或损坏只该项用内置默认，其余照用 |
| launcher icon | **本期明确不做**（Q5=A）。`android:icon` 是 manifest drawable、alias 须编译期声明，下载文件本就换不了；预置 alias 切换是「在预置集合里切」而非「下载即换」，不实现以免误解 |
| 禁止项 | Play Asset Delivery / Dynamic Feature（依 Play 且随 app update 更新，大陆不可用）、`AssetManager.addAssetPath` 等隐藏 API（`hiddenapi-flags.csv` 实测 unsupported / blocked）——均由上游 #21 判死，本包不采用 |

## Considered Options（被否掉的备选）

| 备选 | 否决理由 |
| --- | --- |
| 图标仍手绘、本期只做字体 | destination 的「皮肤 = 主题 + 图标 + 字体」里图标那一半永远落空 |
| 图标改为随包下发 VectorDrawable | 完全资源化但需 API 30 双路径 + 解析开销；而数据化后参数表已足够表达 |
| 纯文件解码 + 自建缓存（我起草时的建议 A） | 维护者选了 `ResourcesLoader`：能复用系统资源解析、按名 `getIdentifier` 取用，不必自己维护解码与 LRU |
| 引入 `coil3` 加载图片 | 3.6.3 的 AAR `minCompileSdk=37` 构建不过，被钉在 3.5.0；为几十个图标引入图片框架不划算 |
| 只靠 HTTPS + 预签名 URL 做校验 | 与「完整性是应用层责任」相悖，CDN 被污染时无从发现 |
| 整包回落 | 一个小图标缺失就让整套皮肤失效，体验断崖 |

## Consequences

- **Q3=B 的代价（与我起草建议不同，必须记）**：`ResourcesLoader` 是 **API 30** 公开 API，本工程 `minSdk 26`，**26–29 必须回落另一条路径**（文件解码）。这意味着加载层有两套实现。
  - 但**这不构成 KB 里那条「双渲染通道永远调不齐」的坑**：那条针对的是「同一个视觉用 AGSL 与 Canvas 两条渲染路径产出不同像素」；这里两条路径只是**取资源的方式不同，最终产物同是 `ImageVector` / `Bitmap` / `Typeface`**。因此可以接受，但**必须在规格里写死「两条路径的产物类型与默认值完全一致」，并加一条契约测试**，否则迟早会分叉。
- **Q2 自定义的代价**：「允许加字体依赖」打开了一个口子，但**中文正文字库普遍数 MB**，且商用字体有授权风险。规格里需要一条约束：字体包体积上限、授权来源记录、以及「字体加载失败必须静默回落系统字体」。
- **图标数据化是本票最大的隐藏工作量**：`MainShell.kt` 的 `drawNavIcon`（`:457`）与四类图标绘制要改成参数驱动，同时不能破坏既有视觉。它与 `#5`（导航壳已落地）的视觉结论直接相关，改动需以现有渲染结果为基准做回归。
- **`addLoaders` 的线程约束**：上游事实（#21）明确改 provider / loader 列表应在 UI 线程，否则与 UI 线程锁竞争。
- **校验只写一遍**：不再叠第二层哈希，避免两条校验路径分叉。

## References

- `docs/adr/0003-皮肤包一份契约两条载荷通道.md`（含 amend：载荷范围已由 0004 收窄）
- `docs/adr/0004-色值通道走数据库目录接口.md`（色值移出包）
- `docs/research/skin-decoupling-routes.md`（PAD/DFM 与 `addAssetPath` 判死、`ResourcesLoader` API 30、完整性是应用层责任、coil 版本阶梯）
- KB `android/APK-外资源运行时加载：addAssetPath-是死路，公开路径是-ResourcesLoader-或文件解码.md`
- 本工程 `app/src/main/java/com/nongyu/appplus/ui/MainShell.kt:440,457,508`（`drawNavIcon` 手绘实现）、`app/src/main/res/`（除 launcher 无业务图形）
- wayfinder #25《资源包通道：APK 外资源的运行时加载、包形态与安全校验》决议评论
