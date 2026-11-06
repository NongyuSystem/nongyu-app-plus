# 主题 token 层用自建 CompositionLocal 承载，色板为唯一真源

`MaterialTheme.colorScheme` 只有固定 48 个 Color 槽位、装不下 `brandMuted` / 底栏玻璃 / AI 圆专色，因此农屿用自建的分组色板对象（`NongyuColors`，经 `CompositionLocal` 下发）作为唯一真源，保留 `MaterialTheme` 一层仅作 MD3 组件取色的投影。本期只提供白绿一套、固定浅色；数据源接口现在定好，先用 mock（内置色板），数据库层做好后直接替换实现，Composable 层零改动。

## Status

accepted（2026-11-06，维护者在 wayfinder #4 拍板）

## 决策内容

| 项 | 结论 |
| --- | --- |
| 承载方式 | 自建 `NongyuColors` + `LocalNongyuColors`（`compositionLocalOf`）+ `CompositionLocalProvider`，外层保留 `MaterialTheme`；`ColorScheme` 由色板派生（`lightColorScheme(...)`），是投影不是真源 |
| 术语边界 | **主题** = 颜色 token 集合；**皮肤** = 主题 + 图标/字体等可替换资源包（包含关系）。写入根 `GLOSSARY.md` |
| token 形状 | **分组结构**：按 `brand` / `surface` / `aiOrb` 等语义分组，不用扁平键值 |
| 运行模型 | 顶层 `NongyuAppPlusTheme` 注入；色板由**数据源接口**提供，本期 mock 实现（内置白绿色板），后期数据库层就绪后替换实现，Composable 零改动 |
| 暗色 | **本期移除 `darkTheme` 参数、强制浅色**，并关闭 `dynamicColor`（避免 Android 12+ 动态取色冲掉品牌色）；暗色以后作为另一份色板加入 |
| 命名 | **另起 Compose 风格命名**（`colorPrimary` / `colorSurface` 一类），不沿用 `design-system/web-site/MASTER.md` 的 `--ny-*` 缩写；色值仍以该 MASTER.md 为视觉唯一真源 |
| 皮肤/后端 | 皮肤体系（图标/字体资源包、Go 后端、MinIO、SSO/资源鉴权、数据库下发）**不属本图 destination**，另开 fresh effort（Q5=A）；本图只留「能被皮肤驱动的接口形状」 |

## Considered Options（被否掉的备选）

| 备选 | 否决理由 |
| --- | --- |
| 只定制 `lightColorScheme` 走 `MaterialTheme.colorScheme` | `ColorScheme` 是 `@Immutable class`（非 data class）、主构造器固定 **48 个**槽位、**无法新增字段**，`brandMuted`/`glass`/`aiOrb` 装不进。实证见 `docs/research/compose-design-system-tokens.md` |
| 用 `val ColorScheme.xxx @Composable get()` 扩展属性加字段 | 官方 "Custom design systems in Compose" 明确限定「仅适合简单追加或各主题同值；**多主题应另定义带属性的类 + 自己的 CompositionLocal**」，而本图要的正是可切换 |
| 去掉 `MaterialTheme` 全部手动传色 | M3 组件不包 `MaterialTheme` 时仍渲染但落 `lightColorScheme()` 默认紫，比手动传色更隐蔽；保留一层投影成本最低 |
| 本期引入 DataStore / SharedPreferences 持久化 | 维护者已把「设置页 + 持久化」划出本期；且现在没有数据库，先 mock |
| 留暗色空实现 / 跟随 `isSystemInDarkTheme()` | 与「本期固定白绿浅色」冲突：系统一开暗色会悄悄切到 `darkColorScheme()` 默认暗色 |
| 扁平 `--ny-*` 单文件 | 维护者选分组结构 + Compose 风格命名 |

## Consequences

- **自定义色必须手动传给 M3 组件**（`ButtonDefaults.buttonColors(...)` 等）。M3 组件默认只读 `colorScheme`，自定义 Local 不会自动跟色 —— 这是本决策最主要的日常代价，需写进编码规范。
- **离线首屏天然成立**：色板是编译期内置对象，不依赖网络。
- **数据库层顺序**：先做数据库层，再接皮肤；无数据库期间用 mock。接口形状已定，替换实现时 Composable 不动。
- **皮肤相关结论转交新图**（当前已拍板待带入）：Q6=B 后端仍单起 Go（需补压测证据，且注意仓内 `nongyu-go-track-server` 已 DEPRECATED、现行是 Node）；Q7=B 只做资源防盗链/预签名 URL、不需要 SSO（MinIO 不提供身份认证）；Q8=数据库下发色值。
- **仍属雾**：分组的逐字段清单、`ui/theme/` 单文件还是多文件拆分、M3 组件逐槽位映射 —— 留 `/to-spec` 或 `/implement` 决定。

## References

- `docs/research/compose-design-system-tokens.md`（ColorScheme 48 槽位、CompositionLocal 官方口径、M3 组件取色、系统暗色副作用）
- `design-system/web-site/MASTER.md`（`--ny-*` 色值真源）
- `docs/adr/0001-调试链路走命令行加蓝叠而非IDE集成.md`（编号与格式）
- `GLOSSARY.md`（术语统一）
