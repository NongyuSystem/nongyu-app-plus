# Compose「AI 入口大圆按钮」技术路线调研

> 调研对象：`apps/nongyu-app-plus`（AGP 9.2.1 / Kotlin 2.3.0 / Compose BOM 2025.12.00 / compileSdk 36 / minSdk 26 / JDK 21）
> 只做事实调研，不含业务实现。所有断言标注来源；无法从一手资料确认的写进第 5 节「未验证清单」。

---

## 1. 结论摘要

1. **先做两条**：R1「纯 Canvas + Brush 梯度旋转/位移」做兜底基座（minSdk 26 全量可用，零新依赖），R2「AGSL RuntimeShader」做视觉上限（API 33+，同样零新依赖）。
2. **R2 的关键事实**：Compose **没有**自己的 `RuntimeShader` 包装类；`androidx.compose.ui.graphics.Shader` 是 `android.graphics.Shader` 的 typealias，所以直接用平台类 `android.graphics.RuntimeShader`（API 33）再 `ShaderBrush(shader)` 即可，无需任何转换函数。
3. **MeshGradient 在本仓当前不可用**，且前提要纠正：`androidx.graphics.shapes` **没有任何版本**包含 MeshGradient；官方 MeshGradient 是 `androidx.compose.ui:ui` 里的 `MeshGradientPainter`，自 Compose UI **1.12.0** 起提供，而这些制品 `minCompileSdk=37` —— 本机只有 android-36，等于**硬阻断**。
4. **高斯光晕可用但会静默降级**：`Modifier.blur` 在 Android 12（API 31）以下被**直接忽略**（官方文档原文），不抛异常；`RenderEffect` 同样是 no-op。
5. **长按加速不要用 `infiniteRepeatable` 改 duration**：改变 `animationSpec` 会重建动画对象，做不到平滑加速；需要可变速时用 `withFrameNanos` 自己累加相位（R2 骨架即此写法；R1 骨架用固定 spec，长按只改缩放/压暗）。
6. **每帧重组的唯一防线**：把动画值的读取放在 **draw 阶段**（`drawBehind` / `drawWithCache` / `Canvas.onDraw` / `graphicsLayer {}` lambda），不要读在 composition 里。
7. 本方案**不需要新增任何 Maven 依赖**（R1/R2/R4 全部落在现有 `ui` / `ui-graphics` / `foundation` / `animation-core` 内）。

---

## 2. 路线对比表

| 路线 | 最低 API | minSdk 26 降级 | 无限动画实现 | 性能风险 | 依赖成本 |
|---|---|---|---|---|---|
| **R1** 纯 Canvas + Brush（`sweepGradient` / `linearGradient` / `radialGradient` + `rotate`/`translate` 相位） | minSdk 26 即全量可用（无 API 门禁） | 不需要降级，**就是**降级方案本身 | `rememberInfiniteTransition` + `animateFloat` + `infiniteRepeatable(tween, LinearEasing)`；读值必须落在 draw 阶段 | 低。每帧一次 `drawCircle` + 一个 sweep shader；CPU 几乎不参与，GPU 填充率约等于按钮面积 | 0。全在 BOM 2025.12.00 已有的 `ui` / `foundation` / `animation-core` |
| **R2** AGSL `RuntimeShader` + `ShaderBrush` | **33**（`android.graphics.RuntimeShader` 类自 API 33 起存在） | `Build.VERSION.SDK_INT < 33` 走 R1 的 Canvas 实现 | 同 R1 的驱动方式，但相位写进 uniform；用 `withFrameNanos` 累加才能平滑改速 | 中。逐像素 fragment shader，每帧重绘；像素数 = 按钮圆面积，不是全屏，风险可控但低端 GPU 需实测 | 0 新依赖；AGSL 字符串本身无编译期校验（写错到运行时才炸） |
| **R3** `MeshGradientPainter`（Compose UI 1.12+） | 无强制 API 下限（1.12.1 内部只用 `drawVertices`，`drawVertices` 的**硬件加速**自 API 29 起） | 26–28 会走非硬件加速路径；且**当前工程根本无法编译**（见下） | `block` 在 DrawScope 执行，可读动画 state，只触发重绘 | 中高。1.12.1 每帧对每个 patch 走 CPU 细分 + `drawVertices` | **阻断**：需 Compose UI ≥1.12.0，其 AAR `minCompileSdk=37`，本机仅 android-36 |
| **R4** 高斯光晕（`Modifier.blur` / `graphicsLayer { renderEffect = BlurEffect(...) }`） | **31**（`android.graphics.RenderEffect` 自 API 31 起；文档明确「仅 Android 12 及以上支持，低版本忽略」） | 26–30 **自动** no-op（不崩、不报错），表现为「没有外发光」 | 由主体动画连带驱动；半径可用 `animateDpAsState` | 中。`Modifier.blur` 会把内容渲染进独立图层并裁剪，额外一层 offscreen buffer，填充率翻倍 | 0。`androidx.compose.ui:ui` 的 `Modifier.blur` 自 1.10.0 起可用（本仓 1.10.0 就有） |
| **R5**（补充）R1 主体 + R4 光晕 + R2 叠加（按 API 分级） | 26（视觉降级分级） | 26–30：R1+R1 假光晕（径向渐变替代）；31–32：R1+真 blur；33+：R2+blur | 同上 | 同 R2/R4 | 0 |

> 说明：R3 官方文档页写「Added in 1.13.0-rc01」，但制品 `ui-android:1.12.0` / `1.12.1` 已包含 `MeshGradientPainter`，官方博客亦称「Compose 1.12 introduces `MeshGradientPainter`」。文档与制品版本口径不一致，见第 5 节。

---

## 3. 逐路线细节

### 3.0 前置核查：四条被点名的事实

| 待核实项 | 结论 | 依据 |
|---|---|---|
| AGSL 最低 API level | **33**（Android 13）。`RuntimeShader` 类 `since=33` | [AGSL 文档](https://developer.android.com/develop/ui/views/graphics/agsl)「used by Android 13 and above」；[RuntimeShader 参考页](https://developer.android.com/reference/android/graphics/RuntimeShader)页首「Added in API level 33」；本机 `platforms/android-36/data/api-versions.xml` 第 20907 行 `<class name="android/graphics/RuntimeShader" since="33">` |
| 低于 33 的失败形态 | 官方**只声明**「Android 13 及以上」，未像 `Modifier.blur` 那样承诺静默忽略；Android 官方兼容指南要求对高于 minSdk 的 API 做 `Build.VERSION.SDK_INT` 运行时判断，且 lint 的 `NewApi` 会把未保护的调用判为 **Error**。至于具体抛出 `NoClassDefFoundError` 还是类校验期失败，**未验证** | [支持不同平台版本](https://developer.android.com/training/basics/supporting-devices/platforms)「在运行时检查系统版本」；[NewApi lint](https://googlesamples.github.io/android-custom-lint-rules/checks/NewApi.md.html) |
| MeshGradient 的 artifact 与 API level | **不在** `androidx.graphics.shapes`（1.0.0-alpha05 → 1.1.0 全版本制品内均无 `MeshGradient*` 类；`graphics-core` 1.0.4 / 1.1.0-beta01 也没有）。官方实现是 `androidx.compose.ui.graphics.MeshGradientPainter`，位于 **`androidx.compose.ui:ui`**，Compose UI **1.12.0** 引入。无硬性 API 下限（1.12.1 只用 `Canvas.drawVertices`，其硬件加速自 API 29） | 逐版本解包 AAR 的 `classes.jar` 清单；[MeshGradientPainter 参考页](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/MeshGradientPainter)（Artifact 标为 `androidx.compose.ui:ui-graphics`，但实测类在 `ui` 里）；[网格渐变指南](https://developer.android.com/develop/ui/compose/graphics/draw/mesh-gradient)；[Compose UI 1.12.0 release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.0) |
| `Modifier.blur` 的 API level | **31**（Android 12）。「Note this effect is only supported on Android 12 and above. Attempts to use this Modifier on older Android versions will be ignored.」 | [`Modifier.blur` 参考页](https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/blur.modifier)；`android.graphics.RenderEffect since=31`（本机 api-versions.xml 第 20800 行）；[RenderEffect.kt 源码](https://cs.android.com/search?q=file:androidx/compose/ui/graphics/RenderEffect.kt)「Attempts to use RenderEffect on older Android versions will be ignored」 |
| Compose 里的 `RuntimeShader` 入口类名 | **不存在** `androidx.compose.ui.graphics.RuntimeShader`（该类在 ui-graphics 1.7.6 / 1.9.3 / 1.10.6 / 1.12.1 / 1.13.0-rc01 制品中均不存在，参考页 404）。入口是：平台类 `android.graphics.RuntimeShader` + 顶层工厂函数 `androidx.compose.ui.graphics.ShaderBrush(shader: Shader)` | 制品 `classes.jar` 清单 + 源码 `androidMain/.../AndroidShader.android.kt:30` → `public actual typealias Shader = android.graphics.Shader`；`commonMain/.../Brush.kt:817` → `public fun ShaderBrush(shader: Shader): ShaderBrush` |

---

### R1　纯 Compose Canvas + Brush

**最低 API**：无门禁，minSdk 26 全量可用。
**降级**：不需要——这就是 R2/R5 的降级实现。
**无限动画**：`rememberInfiniteTransition(label)` + `InfiniteTransition.animateFloat(initialValue, targetValue, animationSpec = infiniteRepeatable(...))`；`rememberInfiniteTransition` 内部用 `withInfiniteAnimationFrameNanos` 逐帧推进（源码 `InfiniteTransition.kt:172`），因此它在「动画被系统策略暂停」时可被正确挂钩。R1 若要「长按加速」又不想重建动画，可把 `angle` 换成 `withFrameNanos` 累加（同 R2 写法）。

**性能风险**
- 每帧一次 `drawCircle` + 一个 `SweepGradient`，GPU 侧只是填充一个圆；`drawVertices`、`blur`、shader 都不涉及。
- ⚠️ 唯一真风险是**重组**：动画值若作为 Composable 参数或在 composition 里读取，会每 16ms 重组一次。正确写法是只在 draw lambda 里读。依据：`Canvas(modifier, onDraw)` 的实现就是 `Spacer(modifier.drawBehind(onDraw))`（`foundation/Canvas.kt:41`），`onDraw` 文档明确「will be called during draw stage」，而 Compose 按阶段跟踪 state 读取，draw 阶段读到的 state 变化只重跑 draw。

**实现要点**：`DrawScope.rotate(degrees, pivot)`（`DrawScope.kt:141`）、`Brush.sweepGradient(colorStops, center)`、`Brush.radialGradient`、`Modifier.graphicsLayer { scaleX/scaleY }`（long-press 缩放不触发重排）。

```kotlin
@Composable
fun CanvasOrb(
    longPressed: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    // 相位 0..360：spec 固定，长按只改缩放/压暗，避免重建动画对象
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbAngle",
    )
    // 长按缩放放在 graphicsLayer，不触发重排/重绘内容
    val scale by animateFloatAsState(if (longPressed) 1.06f else 1f, label = "orbScale")

    Box(
        Modifier
            .size(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 绿少白多：白占绝大区间，绿色只挤在 0.62..0.78 一小段
            val orbBrush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to Color.White,
                    0.45f to Color.White,
                    0.62f to Color(0xFFD8F5E4),
                    0.71f to Color(0xFF34C77B),
                    0.78f to Color(0xFFEFFBF4),
                    1.00f to Color.White,   // 首尾同色，接缝才无痕
                ),
                center = center,
            )
            rotate(angle, pivot = center) {   // 读 angle 在 draw 阶段
                drawCircle(orbBrush)
            }
        }
    }
}
```

**验证方式**
1. `./gradlew :app:assembleDebug` 通过（`detectTapGestures` / `rotate` / `sweepGradient` 名字写错会编译失败）。
2. 动态效果静态化：把 `angle` 换成常量 45f，用 `@Preview` 看「绿少白多」是否达标；`sweepGradient` 首尾不同色会出现明显接缝，这是肉眼可判的验收点。
3. 重组验证：在 Composable body 里打日志/计数，跑 10 秒，日志应只出现 1 次（长按切换时 +1 次）。若每帧刷则说明 state 读错了阶段。
4. 帧耗时：在真机上开「GPU 渲染模式分析 / Profile HWUI rendering」看是否掉帧（官方也要求「在启用硬件加速的实际硬件设备上测试」）。

---

### R2　AGSL `RuntimeShader` + `ShaderBrush`

**最低 API**：33。
**minSdk 26 降级**：`if (Build.VERSION.SDK_INT < 33) { CanvasOrb(...); return }`——**必须**用这种方式门禁，否则 lint `NewApi` 报 Error，且 26–32 上会加载不到 `android.graphics.RuntimeShader` 类。
**无限动画**：相位作为 uniform 每帧写入。这里**推荐 `withFrameNanos` 自己累加**，因为长按要变速度，而 `infiniteRepeatable` 的 `durationMillis` 是 `animationSpec` 的一部分，改 spec = 重建动画对象（从 0 重新开始或跳变），做不到「平滑加速」。
**性能风险**：逐像素求值，每帧重绘。若只在按钮圆内绘制（`clip(CircleShape)` 或 `drawCircle`），像素量是按钮面积而非全屏；但低端 GPU、高分屏（3x）下仍需实测。另外 AGSL 是**运行期**编译，字符串写错会在构造 `RuntimeShader` 或首次绘制时才失败（具体异常类型见第 5 节）。
**实现要点**：`android.graphics.RuntimeShader(String)`（API 33）、`setFloatUniform(String, float...)`、`setColorUniform(String, int)`、`layout(color) uniform` 限定符做颜色空间转换、`ShaderBrush(shader)` 复用同一 shader 实例（源码 `Brush.kt:817-822`：`createShader(size) = shader`，所以每帧改 uniform 是可行的——官方也明确「uniforms can then be initialized and updated per frame」）。

```kotlin
@Language("AGSL")
private const val ORB_AGSL = """
    uniform float2 uSize;    // 绘制区尺寸（px）
    uniform float  uPhase;   // 0..1 相位（外部按长按速度累加）
    uniform float  uHeat;    // 0..1 长按强度
    layout(color) uniform vec4 uBase;   // 白底
    layout(color) uniform vec4 uGlow;   // 绿色光带

    half4 main(in float2 p) {
        float2 uv = p / uSize;
        // 两条不同方向/频率的正弦叠加 → 缓慢流动的光带
        float w = sin((uv.x * 2.0 + uv.y * 1.3) * 6.2831 + uPhase * 6.2831) * 0.5 + 0.5;
        w = sin((uv.x * -1.1 + uv.y * 2.4) * 6.2831 + uPhase * 12.5662) * 0.25 + w * 0.75;
        w = pow(clamp(w, 0.0, 1.0), 3.0);              // 提高对比度 → 绿少白多
        float band = smoothstep(0.30, 0.95, w);
        vec3 col = mix(uBase.rgb, uGlow.rgb, band * (0.45 + 0.20 * uHeat));
        col = mix(col, uGlow.rgb, uHeat * 0.10);        // 长按整体泛绿
        return half4(col, 1.0);                         // 不透明：无需处理预乘
    }
""".trimIndent()

@Composable
fun AgslOrb(longPressed: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        CanvasOrb(longPressed, onClick, onLongPress)       // minSdk 26 降级
        return
    }
    val shader = remember { android.graphics.RuntimeShader(ORB_AGSL) }
    val phase = remember { mutableStateOf(0f) }
    // 用 withFrameNanos 累加相位：速度可随长按变化且不打断
    LaunchedEffect(longPressed) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val speed = if (longPressed) 1.6f else 0.30f   // Hz
                phase.value = (phase.value + dt * speed) % 1f
            }
        }
    }
    val scale by animateFloatAsState(if (longPressed) 1.06f else 1f, label = "scale")
    Canvas(
        Modifier
            .size(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            },
    ) {
        val ph = phase.value                // 读在 draw 阶段 → 只重绘不重组
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uPhase", ph)
        shader.setFloatUniform("uHeat", if (longPressed) 1f else 0f)
        shader.setColorUniform("uBase", Color.White.toArgb())
        shader.setColorUniform("uGlow", Color(0xFF2ECC71).toArgb())
        drawCircle(ShaderBrush(shader))
    }
}
```

**验证方式**
1. `./gradlew :app:assembleDebug`：AGSL 是字符串，编译期查不出错，所以必须补一次运行期验证。
2. **API 33/34 真机或模拟器**跑起来看动效；同时用 **API 26 与 API 30 模拟器**跑同一入口，确认走的是 Canvas 降级分支且不崩。
3. `./gradlew :app:lintDebug`，确认没有 `NewApi` 报错（有的话说明 `SDK_INT` 门禁漏了或门禁没被 lint 识别）。
4. 颜色验收：截图后取色，白色像素占比应显著大于绿色（`vision_colors` 之类的取样即可），并确认无缝接缝。
5. 帧耗时：真机 `dumpsys gfxinfo <pkg> framestats` 看 99 分位是否超 16.6ms。

---

### R3　`MeshGradientPainter`（Compose UI 1.12+）——当前工程阻断

**为什么值得单独列**：它是官方「多点、有机」渐变的唯一正规入口，视觉最接近小爱同学的流体球；但它在**本仓当前技术栈下不可用**。

**硬约束（全部实测/查证）**
1. 类在 `androidx.compose.ui:ui`，不在 `ui-graphics`，更**不在** `androidx.graphics:graphics-shapes`。
   - `graphics-shapes` 制品逐版本解包：`1.0.0-alpha05`、`1.0.0-beta01`、`1.0.0`、`1.0.1`、`1.1.0-beta01`、`1.1.0` 的 `classes.jar` 里 **零个** `MeshGradient*`；`graphics-core` 同。
   - `ui-android:1.12.0` / `1.12.1` 的 AAR 内含 `androidx/compose/ui/graphics/MeshGradientPainter.class`；`ui-graphics-android:1.12.1` 不含。
2. 引入版本：Compose UI **1.12.0**（BOM **2026.08.00**）起；BOM 2025.12.00 对应 ui **1.10.0**，没有该类（`ui-android:1.10.0` 制品内 0 个 Mesh 类）。
3. **阻断点**：`ui-android` 的 `META-INF/com/android/build/gradle/aar-metadata.properties` 里 `minCompileSdk`：1.10.0 = **35**，1.11.x = **35**，1.12.0 / 1.12.1 / 1.13.0-rc01 = **37**。本机 SDK 只有 `android-36`（`$ANDROID_HOME/platforms` 仅 `android-36`），升级 BOM 会直接构建失败——这与仓库 `gradle/libs.versions.toml` 里已记录的锁定原因一致。
4. 渲染实现：**1.12.1 只有 `drawVertices` 一条路**（`MeshGradientRenderer.android.kt` 无条件 `return MeshGradientRendererImpl()`），`@RequiresApi(UPSIDE_DOWN_CAKE)` 的 `android.graphics.Mesh`（API 34）路径出现在 **1.13.0-alpha01+** 才分叉（`if (SDK_INT >= UPSIDE_DOWN_CAKE) MeshGradientRendererV34Impl() else MeshGradientRendererImpl()`）。
5. `drawVertices` 的**硬件加速**自 API 29 起；26–28 会走非硬件加速路径（`MeshGradientRendererImpl` 注释与官方硬件加速表一致）。

**动画特性（若将来解锁）**：`MeshGradientPainter(rows, columns, hasBicubicColor) { setVertex(...) }` 的 `block` 在 `DrawScope` 里执行，**可以读 mutableState 且只触发重绘**，不用重造 shader。

```kotlin
// ⚠️ 当前 compileSdk 36 + BOM 2025.12.00 下无法编译，仅作将来参考
val painter = remember {
    MeshGradientPainter(rows = 2, columns = 2, hasBicubicColor = true) {
        // 归一化坐标 (0,0) 左上 → (1,1) 右下
        setVertex(0, 0, Offset(0f, 0f), Color.White)
        setVertex(0, 1, Offset(0.7f, 0.1f) + Offset(phase, 0f), Color(0xFF34C77B))
        setVertex(0, 2, Offset(1f, 0f), Color.White)
        setVertex(1, 0, Offset(0.1f, 0.8f), Color.White)
        setVertex(1, 1, Offset(0.5f, 0.5f), Color(0xFFEFFBF4))
        setVertex(1, 2, Offset(1f, 0.9f), Color.White)
        setVertex(2, 0, Offset(0f, 1f), Color.White)
        setVertex(2, 1, Offset(0.6f, 0.95f), Color.White)
        setVertex(2, 2, Offset(1f, 1f), Color.White)
    }
}
Box(Modifier.size(160.dp).clip(CircleShape).paint(painter))
```

**验证方式**：只能在解锁 compileSdk 37 之后验证；届时用 `./gradlew :app:assembleDebug` + API 26 模拟器（确认走非加速路径不崩）+ API 34 真机（走 `Mesh` 路径）对比。

---

### R4　高斯模糊光晕（`Modifier.blur` / `RenderEffect`）

**最低 API**：31（`android.graphics.RenderEffect`）。
**minSdk 26 降级**：**自动降级，无需写代码也不会崩**——官方原文「Attempts to use this Modifier on older Android versions will be ignored」；Compose 源码注释同样承诺「If RenderEffect is used on an unsupported API level, it should act as a no-op and not crash」。视觉上等于「没有外发光」，所以为了视觉一致，建议 26–30 用「径向渐变模拟光晕」保底。
**无限动画**：半径用 `animateDpAsState`，或由主体相位连带驱动。
**性能风险**：文档明确「Usage of this API renders the corresponding composable into a separate graphics layer. Because the blurred content renders a larger area by the blur radius, this layer is explicitly clipped to the content bounds.」——即多一层 offscreen buffer，填充率大致翻倍；给光晕扩大半径时要注意内容会被裁剪（给足 padding 或 `BlurredEdgeTreatment.Unbounded`）。
**实现要点**：`Modifier.blur(radius, edgeTreatment)`（Compose UI 1.10.0 起可用，本仓 1.10.0 就有）；更细的控制用 `Modifier.graphicsLayer { renderEffect = BlurEffect(radiusX, radiusY, TileMode.Clamp) }`；进度式模糊 `BlurRadiusSpec` 属 **1.13.0-rc01**，本仓不可用。

```kotlin
@Composable
fun OrbWithHalo(longPressed: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val halo by animateDpAsState(if (longPressed) 22.dp else 12.dp, label = "halo")
    Box(
        Modifier
            .size(200.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        // 光晕层：<31 时 Modifier.blur 被忽略，退化成硬边渐变，故整体交由 R1 的径向渐变兜底
        Box(
            Modifier
                .size(160.dp)
                .blur(halo, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF34C77B).copy(alpha = 0.55f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        CanvasOrb(longPressed, onClick, onLongPress)   // 主体复用 R1
    }
}
```

**验证方式**
1. 三档模拟器横评：API 30（应为无光晕但**不崩**）、API 31、API 33。
2. 重点看 API 30 是否出现「渐进式变暗/裁剪」的异常（该版本 `Modifier.blur` 直接返回原 modifier，理论无副作用）。
3. 用 Layout Inspector / 帧耗时工具确认多一层 offscreen 后没有超预算。

---

## 4. 推荐组合与原型建议

### 推荐组合（分阶段）

| 阶段 | 实现 | 理由 |
|---|---|---|
| 立即可做 | **R1** 作为唯一实现落地 | minSdk 26 全量可用、零依赖、无 API 门禁，先确定「绿少白多 + 缓慢流动 + 长按缩放/压暗」的视觉与手感参数（颜色断点、周期、缩放比、scrim 透明度） |
| 视觉升级 | **R2** 加在 33+ 分支，26–32 回落 R1 | 光带形态可做到 R1 做不到的有机感，且零新依赖 |
| 叠加增强 | **R4** 光晕（31+ 真 blur，26–30 用径向渐变假光晕） | 是「被唤起」感的主要来源之一，成本低、可独立开关 |
| 暂缓 | **R3** MeshGradient | 需 compileSdk 37，本机不可构建；等 SDK/工具链升级再评估 |

### 值得先做出来对比的两条

**只做 R1 与 R2 两条，用同一套参数做 A/B**：
- 共用外部契约：`longPressed`、`onClick`、`onLongPress`、同一个 `size`/颜色常量/周期常量；
- R2 内部 33 以下直接委托 R1，保证「低端机看 R1，高端机看 R2」；
- 对比维度只用三个可量化项：①白色像素占比（是否够「绿少白多」）②长按 0.5s 内相位速度是否明显加快且不跳变 ③真机 `framestats` 99 分位是否 < 16.6ms。

原型文件建议放同一目录（例如 `ui/components/orb/`），R1/R2 各一个文件，外加一个 `OrbButton.kt` 做 API 分派与交互状态机（长按 → `longPressed=true` → 缩放 + scrim 压暗 + 加速；松手 → `animateFloatAsState` 回弹；点击 → 导航到 AI 页）。

**背景压暗**用一个和页面同尺寸的 scrim（`Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dimAlpha)))`，`dimAlpha` 由 `animateFloatAsState` 驱动 0f→0.35f），放在按钮下层、内容上层，避免用 `Modifier.alpha` 造成额外 offscreen 层（官方文档对 `Modifier.alpha` 的填充率开销有专门提示）。

---

## 5. 未验证清单

1. **`android.graphics.RuntimeShader` 在 API < 33 上的确切失败形态**：是构造时 `NoClassDefFoundError`、类校验期 `VerifyError`，还是 `linkage` 失败——未在 API 26 真机/模拟器上实测。官方文档仅声明「Android 13 及以上」，**没有**像 `Modifier.blur` 那样给出「低版本静默忽略」的承诺，因此按「必须显式 `SDK_INT` 门禁」处理。
2. **AGSL 程序编译失败时的异常类型**：文档只说明 `setColorUniform` 在 uniform 不存在/类型不符时抛 `IllegalArgumentException`；shader 源码语法错误时的具体异常（是否同为 `IllegalArgumentException` 且文案含编译日志）**未验证**。
3. **`InfiniteTransition.animateFloat` 更换 `animationSpec` 后的行为**：源码中未找到「从当前值续接」还是「重置」的明确文档；因此本文建议改用 `withFrameNanos` 自累加相位。此建议基于 API 语义，**未做行为对比实验**。
4. **`MeshGradientPainter` 的版本口径不一致**：官方参考页标「Added in 1.13.0-rc01」，但 `ui-android:1.12.0/1.12.1` 制品与官方博客均表明 1.12 已提供；同时参考页把 Artifact 写成 `androidx.compose.ui:ui-graphics`，实测类在 `ui`——**两处口径与制品不符，原因未查证**（可能是 1.13 的文档伴随改动回写到旧页）。
5. **`MeshGradient` 在 API 26–28 的实际渲染耗时**：只知道 `drawVertices` 自 API 29 起才硬件加速，未实测 26–28 的降级表现（且当前工程无法构建该路线，无法测）。
6. **三条路线的真机每帧耗时**：本次只做资料调研，未在设备上跑帧率/`gfxinfo`，所有性能判断均为「机制层面推断」+ 官方文档的定性描述。
7. **AGSL 无内置噪声函数**：本文骨架只用正弦叠加（不依赖 `noise`）。AGSL 是否提供噪声类内建函数，未在官方 AGSL/GLSL 差异页找到条目——**未验证**，故不采用。

---

## 6. 参考资料

**AGSL / RuntimeShader**
- Android Graphics Shading Language (AGSL)：https://developer.android.com/develop/ui/views/graphics/agsl
- AGSL 与 GLSL 的差异（类型、坐标、uniform、颜色空间）：https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl
- `android.graphics.RuntimeShader` 参考页（Added in API level 33、uniform 逐帧更新说明）：https://developer.android.com/reference/android/graphics/RuntimeShader
- Compose Brush 指南（含 AGSL `RuntimeShader` brush 章节）：https://developer.android.com/develop/ui/compose/graphics/draw/brush

**MeshGradient**
- 网格渐变指南（含动画示例）：https://developer.android.com/develop/ui/compose/graphics/draw/mesh-gradient
- `MeshGradientPainter` 参考页：https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/MeshGradientPainter
- Compose UI release notes（1.12.0 / 1.13.0）：https://developer.android.com/jetpack/androidx/releases/compose-ui
- Compose BOM ↔ 库版本映射：https://developer.android.com/develop/ui/compose/bom/bom-mapping
- BOM 2025.12.00 POM（ui = 1.10.0）：https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.12.00/compose-bom-2025.12.00.pom
- androidx.graphics 版本索引（graphics-shapes 最新 1.1.0）：https://dl.google.com/dl/android/maven2/androidx/graphics/group-index.xml
- Compose 1.12 发布博客（Mesh Gradients 段落）：https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release

**Blur / RenderEffect**
- `Modifier.blur` 参考页（API 31 限制、低版本忽略、独立图层与裁剪）：https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/blur.modifier
- Compose 模糊指南：https://developer.android.com/develop/ui/compose/graphics/blur/overview
- `androidx.compose.ui.draw` 包索引：https://developer.android.com/reference/kotlin/androidx/compose/ui/draw/package-summary

**动画 / 阶段 / 图形基础**
- 基于值的动画（`rememberInfiniteTransition`、`infiniteRepeatable`）：https://developer.android.com/develop/ui/compose/animation/value-based
- Compose 的三个阶段（state 读取按阶段失效）：https://developer.android.com/develop/ui/compose/phases
- 硬件加速（`drawVertices()` 首个支持 API 29；`Modifier.graphicsLayer` 与 `CompositingStrategy.Offscreen` 建议）：https://developer.android.com/topic/performance/hardware-accel
- 手势：`detectTapGestures` / `PressGestureScope.awaitRelease`：https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press

**兼容性 / 校验**
- 支持不同平台版本（`Build.VERSION.SDK_INT` 运行时判断）：https://developer.android.com/training/basics/supporting-devices/platforms
- lint `NewApi` 检查说明：https://googlesamples.github.io/android-custom-lint-rules/checks/NewApi.md.html

**源码（AndroidX / AOSP）**
- `compose/ui/ui-graphics/src/androidMain/.../AndroidShader.android.kt`（`Shader` = `android.graphics.Shader` 的 typealias）：https://cs.android.com/search?q=file:androidx/compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidShader.android.kt
- `compose/ui/ui-graphics/.../Brush.kt`（`ShaderBrush(shader)` 复用实例、按尺寸缓存）：https://cs.android.com/search?q=file:androidx/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Brush.kt
- `compose/ui/ui/.../MeshGradient.kt`（`MeshGradientPainter`，block 在 DrawScope 中执行）：https://cs.android.com/search?q=file:androidx/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/graphics/MeshGradient.kt
- `compose/ui/ui/.../MeshGradientRenderer.android.kt`（1.12 只走 drawVertices；1.13 起分叉 API 34 Mesh 路径）：https://cs.android.com/search?q=file:androidx/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/graphics/MeshGradientRenderer.android.kt
- `compose/ui/ui/.../RenderEffect.kt`（RenderEffect 低版本 no-op 承诺）：https://cs.android.com/search?q=file:androidx/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/RenderEffect.kt
- `compose/foundation/.../Canvas.kt`（`Canvas` = `Spacer(modifier.drawBehind(...))`）：https://cs.android.com/search?q=file:androidx/compose/foundation/src/commonMain/kotlin/androidx/compose/foundation/Canvas.kt
- `compose/animation/.../InfiniteTransition.kt`（`withInfiniteAnimationFrameNanos` 逐帧驱动）：https://cs.android.com/search?q=file:androidx/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/InfiniteTransition.kt
- AOSP `android/graphics/RuntimeShader.java`：https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RuntimeShader.java
- 本机 SDK 元数据 `platforms/android-36/data/api-versions.xml`（`RuntimeShader since=33` / `RenderEffect since=31` / `Mesh since=34`）
