# Compose 设计系统 token 落点：技术事实核查

> 范围：只核查「可切换主题应落在 Compose 哪一层」所需的技术事实，不含取舍建议。
> 版本基线：Compose BOM 2025.12.00（核心 Compose 1.10 + Material3 1.4.0）、compileSdk 36、JDK 21。
> 事实来源均为 primary source（developer.android.com / androidx API 参考 / androidx 源码 / 官方 release notes），链接见 §8。
> 标注 **未验证** 的条目见 §7，均未在本轮直接实证。

## 1. 结论摘要

1. `androidx.compose.material3.ColorScheme` 是 `@Immutable public class`（**非 data class**），字段固定写在主构造器里，**无法新增字段**；当前完整构造器共 **48 个 `Color` 槽位**（旧 29/36 槽位构造器已弃用）。
2. 「`val ColorScheme.xxx @Composable get()`」是**官方文档明确给出**的扩展写法，但官方同时限定：只适合「简单追加」或「各主题取值相同」；**多主题时应另定义带属性的类 + 自己的 CompositionLocal**。
3. 官方明确允许「保留 `MaterialTheme` 一层，另加自己的 `CompositionLocal`」：`ExtendedTheme`/`CustomTheme` 骨架即 `CompositionLocalProvider(...) { MaterialTheme(...) }` + `object Theme { val colors @Composable get() = Local…current }`。
4. `compositionLocalOf`（只失效读它的节点）与 `staticCompositionLocalOf`（不跟踪读、换值整块 content 重组）是官方两种 API；官方有「不要滥用、要有好默认值」的告诫，但明确说 CompositionLocal 适合 foundational architecture，且 MaterialTheme 自身就用它。
5. Material3 组件默认从 `MaterialTheme.colorScheme` 取色，**不会**自动读你的自定义 `CompositionLocal`；自定义色要生效必须手动塞进 `ButtonDefaults.buttonColors(...)` 等参数。
6. 不提供 `colorScheme` / 不包 `MaterialTheme` 时组件仍渲染，但落到 `lightColorScheme()` 的 MD3 基线紫，并非「全部都要手动传色」。
7. 官方支持用 `lightColorScheme(primary=…)` / Material Theme Builder 从品牌色生成 `ColorScheme`；「自定义色板为唯一真源、ColorScheme 为投影」是工程写法，官方无专门映射 API。
8. 现有 `darkTheme = isSystemInDarkTheme()` 在「固定浅色」目标下，系统暗色时会切到 `darkColorScheme()` 默认暗色；`@Preview` 不 Provide 自定义 Local 时取默认工厂值。

## 2. Q1 — MaterialTheme.colorScheme 能否承载非 MD3 语义色

### 2.1 类型与槽位（已实证）

androidx 源码（`ColorScheme.kt`，androidx-main）声明：

```kotlin
@Immutable
public class ColorScheme( /* 48 个 public val ...: Color */ ) { … }
```

- **不是 `data class`**；是普通 `class`，带 `@Immutable`。官方 API 参考页头部写 `class ColorScheme`（非 `data class`），其 Public functions 仅列 `copy(...)` 与 `open toString()`（未列 `equals`/`hashCode`/`componentN`）。源码主构造器 48 个 `val` 均为固定字段。
- 主构造器共 **48 个** `val ...: Color` 槽位，完整枚举（按源码顺序，逗号分隔）：
  `primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, secondary, onSecondary, secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, onTertiaryContainer, background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer, outline, outlineVariant, scrim, surfaceBright, surfaceDim, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest, primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant, secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant, tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant`。
- API 参考页列出 3 个构造器：**29 槽位**（到 `scrim`，「deprecated. Use constructor with additional 'surfaceContainer' roles」）、**36 槽位**（加 `surfaceContainer*`，「deprecated. Use constructor with additional 'fixed' container roles」）、**48 槽位**（当前，加 `*Fixed*`）。Material3 1.4.0 release notes 亦记：「ColorScheme constructor without Fixed color roles is now deprecated」。
- **版本对应**：48 是 androidx-main tip 与现行 API 参考的精确值；BOM 2025.12.00 → Material3 1.4.0（官方博客），1.4.0-alpha15 的 release note 确认 fixed roles 已在该线落地/弃用旧构造器。「工程已解析 AAR 内精确槽位数」见 §7（未验证）。

### 2.2 能否新增字段（brandMuted / glass / aiOrb）

**不能**。`ColorScheme` 是固定字段的 `class`，所有颜色都是构造器里的 `val`，没有可插拔的成员扩展点；Kotlin 扩展属性是**编译期静态解析**的顶层声明，不会给类增加真实字段、不持有状态、也不能被 `CompositionLocal` per-theme 提供。因此 `brandMuted`/`glass`/`aiOrb` 这类非 MD3 token **无法成为** `MaterialTheme.colorScheme` 的成员。

### 2.3 扩展属性模式的官方口径

「Custom design systems in Compose」把扩展属性列为**第一种方案**（原文）：

> "There are several approaches you might take: **Extend MaterialTheme with additional theming values.** …"
> "However, it's possible to extend the color, typography, and shape sets with additional values. **The simplest approach is to add extension properties**:"
> ```kotlin
> // Use with MaterialTheme.colorScheme.snackbarAction
> val ColorScheme.snackbarAction: Color
>     @Composable
>     get() = if (isSystemInDarkTheme()) Red300 else Red700
> ```

即：**它是官方 API/官方文档写法，不只是社区约定**；官方示例用 `@Composable get()`（等价于 `@get:Composable`）。对照源码，`MaterialTheme.colorScheme` 自身的 getter 是 `@Composable @ReadOnlyComposable get()`。

官方紧接着给出的**限定**（关键，直接关系「可切换」）：

> "**Note:** This approach is only recommended for straightforward theming value additions, or for values that are the same in different themes. **If you have multiple themes, it's better to define a class with new properties instead.**"

坑（按可实证依据）：
- **多主题**：扩展属性示例内部直接读 `isSystemInDarkTheme()`，取的是「系统暗色」而非自定义主题状态；官方因此建议多主题改用「类 + 属性」。主题切换不会经由扩展属性传播到自定义 token。
- **重组**：`@Composable get()` 里读的 `isSystemInDarkTheme()` 是 `@Composable`，会随系统配置变化重组；但它不订阅你自己的 `CompositionLocal`。
- **@Preview / 静态分析**：官方文档未对扩展属性模式给出专门的 lint/静态分析告诫；这几点在本轮 **未验证**（§7）。

### 2.4 官方是否允许「保留 MaterialTheme + 另加 CompositionLocal」

**明确允许**，且给了完整骨架（同页 "Extend Material Theming"）：

> "Another approach is to define an extended theme that **'wraps' MaterialTheme and its values**."
> ```kotlin
> @Immutable data class ExtendedColors(val caution: Color, val onCaution: Color)
> val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors(Color.Unspecified, Color.Unspecified) }
> @Composable fun ExtendedTheme(...) {
>     val extendedColors = ExtendedColors(...)
>     CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
>         MaterialTheme(/* colors=..., ... */ content = content)   // ← 保留 MaterialTheme 一层
>     }
> }
> object ExtendedTheme {
>     val colors: ExtendedColors @Composable get() = LocalExtendedColors.current
> }
> ```
> "This is similar to MaterialTheme usage APIs. It also **supports multiple themes** as you can nest ExtendedThemes in the same way as MaterialTheme."

## 3. Q2 — CompositionLocal 承载自定义色的官方口径

「Locally scoped data with CompositionLocal」原文：

- 两种 API 的区别（官方逐字）：
  > "`compositionLocalOf`: **Changing the value provided during recomposition invalidates only the content that reads its `current` value.**"
  > "`staticCompositionLocalOf`: **Unlike `compositionLocalOf`, reads of a `staticCompositionLocalOf` are not tracked by Compose. Changing the value causes the entirety of the content lambda where the CompositionLocal is provided to be recomposed**, instead of just the places where the `current` value is read in the Composition."
  > "**If the value provided to the CompositionLocal is highly unlikely to change or will never change, use `staticCompositionLocalOf` to get performance benefits.**"

- 官方是否推荐传「主题级」值 / 告诫：
  > "`CompositionLocal` is **what the Material theme uses under the hood**. `MaterialTheme` is an object that provides three `CompositionLocal` instances: colorScheme, typography and shapes … these are the `LocalColorScheme`, `LocalShapes`, and `LocalTypography` …"
  > "Another key signal for using `CompositionLocal` is when the parameter is **cross-cutting** and intermediate layers of implementation shouldn't be aware it exists …"
  > "**We discourage overusing `CompositionLocal`** as it comes with some downsides: **CompositionLocal makes a composable's behavior harder to reason about** … there might be no clear source of truth … debugging … can be more challenging."
  > "**Note: `CompositionLocal` works well for foundational architecture** and Jetpack Compose makes heavy use of it."
  > "**A `CompositionLocal` should have a good default value.** … Not providing a default value can cause problems and frustration when creating tests or **previewing a composable that uses that `CompositionLocal` will always require it to be explicitly provided.**"
  > "**Avoid `CompositionLocal` for concepts that aren't thought as tree-scoped or sub-hierarchy scoped.**"

  即：官方**认可**主题级/横切值用它（MaterialTheme 本身就是），但要求好默认值并告诫别滥用。

- 典型官方骨架（`LocalNongyuColors` + `object NongyuTheme { val colors @Composable get() }`），出自「Anatomy of a theme in Compose」+ 上文 `ExtendedTheme`：
  ```kotlin
  @Immutable data class NongyuColors(val brand: Color, val brandMuted: Color, val glass: Color, val aiOrb: Color)
  val LocalNongyuColors = staticCompositionLocalOf { NongyuColors(/* 默认/Unspecified */) }
  @Composable fun NongyuTheme(...) {
      CompositionLocalProvider(LocalNongyuColors provides nongyuColors) { MaterialTheme(... content = content) }
  }
  object NongyuTheme {
      val colors: NongyuColors @Composable get() = LocalNongyuColors.current
  }
  ```
- **源码层细节（tip-of-tree）**：现行 `MaterialTheme.kt` 源码已把三系统合并为 `private val _localMaterialTheme = staticCompositionLocalOf { MaterialTheme.Values() }`（`Values` 含 colorScheme/typography/shapes/motionScheme），对外暴露 `MaterialTheme.LocalMaterialTheme`。与文档「三个 LocalColorScheme/…」的表述差异属版本演进；**该合并是否已落在 1.4.0 未验证**（§7）。

## 4. Q3 — Material3 组件与自定义色并存的代价

- 组件自动读 `colorScheme` 的依据：
  - `MaterialTheme.kt` KDoc：「**Material components such as `Button` and `Checkbox` use values provided here when retrieving default values.**」；`Values` KDoc：「**Components use properties provided here when retrieving default values.**」
  - M3 文档：「When you customize these values, **your changes are automatically reflected in the M3 components** you use to build your app.」、「**All components have default colors applied to them** but expose flexible APIs to customize their colors if required.」
- 「`glass`/`aiOrb` 不会自动跟色」**结论成立**。依据（Custom design systems 原文）：
  > "When extending Material Theming, existing `MaterialTheme` values are maintained and Material components still have reasonable defaults."
  > "**If you want to use extended values in components, wrap them in your own composable functions, directly setting the values you want to alter** …"
  ```kotlin
  fun ExtendedButton(...) = Button(
      colors = ButtonDefaults.buttonColors(
          containerColor = ExtendedTheme.colors.caution,       // ← 手动把自定义 Local 的值塞进 M3 参数
          contentColor  = ExtendedTheme.colors.onCaution)) { ... }
  ```
  即 M3 组件只认 `colorScheme` 槽位，自定义 `CompositionLocal` 的颜色必须经包装组件显式传参才会生效。

- 具体组件读哪些槽位（可实证的例子）：
  | 证据 | 槽位 |
  |---|---|
  | `ColorScheme.contentColorFor` 源码注释 | 「a `Button` typically uses **primary** for its background, and **onPrimary** for the color of its content」 |
  | M3 文档示例 | `CardDefaults.cardColors(containerColor = primaryContainer / surfaceVariant, contentColor = onPrimaryContainer / onSurface)` |
  | Material3 1.4.0 release note | 「`NavigationBarItem`/`NavigationRailItem`'s active label color change from **onSurface** to **secondary**」 |
  | M3 文档 | Scaffold/表面类通过 `contentColorFor(colorScheme.surface)` 派生，`surfaceTint` 用于 tonal elevation |
  「逐组件精确槽位清单」未穷举，见 §7（未验证）。

- 完全不提供 `colorScheme` / 不包 `MaterialTheme` 会怎样：
  - `MaterialTheme` 参数默认 `colorScheme: ColorScheme = MaterialTheme.colorScheme`，`Values` 默认 `colorScheme = lightColorScheme()`；KDoc：「Any values that are not set will inherit the current value from the theme, **falling back to the defaults if there is no parent MaterialTheme**」。
  - Custom design systems 原文：「**When no `MaterialTheme` is present, using Material components as-is will result in unwanted Material color, type, and shape values and indication behavior.**」
  - 结论：**不是「所有组件都要手动传色」**——组件仍用 `lightColorScheme()` 的 MD3 基线默认（紫色系）渲染；只是拿不到品牌色，且自定义 token 完全不生效。要不要手动传，取决于你是否要覆盖默认值。

- 官方支持的「从自定义色板派生 ColorScheme」：
  - M3 文档：「**While you can create a custom `ColorScheme` manually**, it's often easier to generate one using source colors from your brand. The **Material Theme Builder** tool allows you to do this, and optionally **export Compose theming code**.」导出物为 `Color.kt`（色值）+ `Theme.kt`（`lightColorScheme(primary = md_theme_light_primary, …)`）。
  - 即：官方提供 `lightColorScheme(...)`/`darkColorScheme(...)`/完整构造器与 Theme Builder，**支持「自定义色板 → ColorScheme」的人工/工具投影**；但「自定义色板为唯一真源、ColorScheme 只是投影」是工程架构写法，官方**未提供**把任意语义 token 表自动映射为 ColorScheme 的 API（是否存在该 API 见 §7 未验证）。

## 5. Q4 — 动态取色 / 系统暗色 / @Preview / 版本

- 现有 `Theme.kt` 事实（读本地文件）：`NongyuAppPlusTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false)`；`LightColors = lightColorScheme()`、`DarkColors = darkColorScheme()`；`when` 分支为 `dynamicColor && S → dynamic*ColorScheme` / `darkTheme → DarkColors` / `else → LightColors`。
- **副作用（事实推导）**：`dynamicColor` 默认 `false`，动态取色分支默认不进；但 `darkTheme = isSystemInDarkTheme()` 仍生效——系统暗色设备上会走 `DarkColors = darkColorScheme()`，即 **MD3 默认暗色（基线紫）而非白绿**。在「本期只上白绿一套、固定浅色」下，这与目标不一致。`isSystemInDarkTheme()` 来自 `androidx.compose.foundation`；`dynamicLightColorScheme`/`dynamicDarkColorScheme` 是 Material3 的 Android 12+ API；`dynamicColor` 是本工程自定义参数、非官方 API。
- **@Preview**：官方 CompositionLocal 文档明确「should have a good default value … previewing a composable that uses that CompositionLocal **will always require it to be explicitly provided**」（当没有好默认值时）。据此：自定义 Local 不 Provide 时取默认工厂值——默认工厂给具体色板则渲染默认色板；若默认工厂是 `error("…")` 则该读取抛异常。要指定主题须在 preview 里包 `CompositionLocalProvider`/`NongyuTheme`。「`error()` 工厂在 @Preview 中抛异常」属默认工厂语义推导，本轮未实跑验证（§7）。
- **版本差异 / 弃用（BOM 2025.12.00 = Compose 1.10 + Material3 1.4.0，官方博客）**：
  - ColorScheme 旧构造器（无 `surfaceContainer*`、无 `*Fixed*`）在 1.4.0 线已弃用；`ColorScheme` 现已 `@Immutable`（源码实证）。
  - `lightColorScheme()` / `darkColorScheme()` / `isSystemInDarkTheme()` / `dynamic*ColorScheme` / `CompositionLocal*` 在本轮所查 release notes 中**未见被弃用**；`NavigationBarItem` 激活标签色由 `onSurface` 改 `secondary`（1.4.0 行为变更）。
  - 「上述每个 API 在 1.10/1.4.0 精确弃用状态」未逐一穷举，见 §7（未验证）。

## 6. 事实 → 对决策的影响（约束，不是建议）

| # | 事实 | 如何约束「可切换落在哪一层」的选项 |
|---|---|---|
| F1 | `ColorScheme` 是固定 48 槽位的非 data class，不能加字段 | 「只扩展 ColorScheme」装不下 `brandMuted/glass/aiOrb`；任何方案都必须有 ColorScheme 之外的承载物 |
| F2 | 官方扩展属性写法仅荐「简单追加 / 各主题同值」，多主题建议另建类 | 若「可切换」= 多套取值，扩展属性方案与官方限定冲突；单套固定值则不冲突 |
| F3 | 官方允许 `CompositionLocalProvider(…) { MaterialTheme(…) }`，且称「supports multiple themes as you can nest ExtendedThemes」 | 「MaterialTheme + 自有 Local」是官方列出的合法落点，不要求替换掉 MaterialTheme |
| F4 | `staticCompositionLocalOf` 换值整块 content 重组；`compositionLocalOf` 只失效读者 | 主题切换频率/范围决定用哪个 API 才不付全树重组代价 |
| F5 | 官方要求 CompositionLocal 有「好默认值」，否则测试/@Preview 必须显式 Provide | 自定义 Local 的默认工厂设计直接影响 preview/测试是否需包裹 |
| F6 | M3 组件默认只读 `colorScheme`，自定义 Local 值须手动塞进组件参数 | 自定义 token 不会「自动跟色」；选此层需为每个用到的 M3 组件写包装/显式传色 |
| F7 | 不包 MaterialTheme 时组件仍渲染，但落 `lightColorScheme()` 默认紫 | 「去掉 MaterialTheme」不会让组件报错，但会失去品牌色控制；不是「全要手传」 |
| F8 | 官方支持 `lightColorScheme(primary=…)` / Theme Builder 从品牌色生成 | 「色板唯一真源 → ColorScheme 投影」可落地；但映射是手写/工具导出，无自动 API |
| F9 | 现有 `darkTheme=isSystemInDarkTheme()` 在系统暗色切 `darkColorScheme()` 默认暗色 | 「固定浅色一套」目标下，该默认分支会产生非白绿结果，属需处理的事实而非可选项 |
| F10 | 1.4.0 弃用旧 ColorScheme 构造器、ColorScheme 已 @Immutable；未见上述其余 API 弃用 | 落点所用 API 在本基线可用；用旧槽位构造器会触发弃用警告 |

## 7. 未验证清单

1. **工程已解析 AAR 内 `ColorScheme` 的精确槽位数**：48 取自 androidx-main tip 与现行 API 参考；未解包本机 Gradle 缓存中 BOM 2025.12.00 实际解析的 material3 AAR 复核。
2. **`LocalMaterialTheme`/`Values` 合并是否已落在 1.4.0**：源码合并为 tip-of-tree 观察；未比对 1.4.0 tag。
3. **逐组件精确槽位清单**：只实证了 Button/Card/NavigationBar/表面类的个别例子，未穷举 `Scaffold`/全部组件的默认取色表。
4. **是否存在「语义 token 表 → ColorScheme」的官方自动映射 API**：所查文档未见，但未做全量 API 索引检索，按「未见」处理。
5. **`@Composable` 扩展属性模式的 lint / 静态分析告诫**：官方文档未给专门告诫；未查 lint 检查项与重组/预览的实测行为。
6. **`error()` 默认工厂在 @Preview 中抛异常**：属 CompositionLocal 默认工厂语义推导，未实跑 preview 验证。
7. **上述各 API 在 1.10 / 1.4.0 的逐一弃用状态**：只查了 ColorScheme 构造器与 1.4.0 行为变更，未逐条核对 `isSystemInDarkTheme`/`dynamic*ColorScheme`/`CompositionLocal*` 的 deprecation 表。
8. **`@Composable get()` 与 `@ReadOnlyComposable`、`@get:Composable` 在 1.10 下的精确语义差异**：未做编译期/工具链验证。

## 8. 参考资料（primary source）

官方文档（本轮因 developer.android.com 主站取不到，经 `developer.android.google.cn` 镜像读取全文，链接仍给官方 .com 地址）：
- [Custom design systems in Compose](https://developer.android.com/develop/ui/compose/designsystems/custom) — 扩展属性、ExtendedTheme/CompositionLocalProvider 骨架、「no MaterialTheme」行为；[Anatomy of a theme](https://developer.android.com/develop/ui/compose/designsystems/anatomy) — `LocalColorSystem` + `object Theme` 骨架。
- [ColorScheme API 参考](https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme) — 类声明、48 属性、3 个构造器及弃用说明；[Locally scoped data with CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal) — 两种 API 原文、不要滥用/好默认值告诫；[Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) — 「automatically reflected in the M3 components」、Theme Builder、Card/Button 取色示例。
- androidx 源码：[ColorScheme.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ColorScheme.kt)（[android.googlesource 镜像](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ColorScheme.kt)）、[MaterialTheme.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialTheme.kt) — `@Immutable class`、48 `val`、组件取默认值的 KDoc、`Values` 默认 `lightColorScheme()`。
- Release notes / 版本：[Compose Material 3 1.4.0 notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)、[Compose December '25 发布博客](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)（BOM 2025.12.00 = Compose 1.10 + Material3 1.4.0）、[BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)。
- 本工程 `app/src/main/java/com/nongyu/appplus/ui/theme/Theme.kt`（只读，用于 Q4 现状）。
