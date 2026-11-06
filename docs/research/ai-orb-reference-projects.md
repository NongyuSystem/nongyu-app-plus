# AI 助手启动球 / 星云球：同类项目调研

> 2026-11-06。起因：维护者要求「搜一下有没有类似项目，就是 AI 助手的启动键这种」。
> 只做资料核查，未引入任何依赖、未改动构建配置。所有链接均为 primary source（项目 README / 源码）。

## 1. 结论摘要

同类项目确实存在，且分两类：**「产品形态」（AI 悬浮球 / 活动指示器）**与**「渲染工艺」（AGSL shader 库）**。
最值得关注的是 [Mirage](https://github.com/AndroidPoet/mirage)（29 个零依赖 AGSL shader，Apache-2.0）与
[OrbMotion](https://github.com/valentineRutto/OrbMotionKMPLibrary)（AI agent orb 状态指示器库）。
[Operit 的 SiriBall](https://github.com/AAswordman/Operit/blob/main/app/src/main/java/com/ai/assistance/operit/ui/floating/ui/ball/SiriBall.kt)
直接印证了我们的长按手势写法与「中心亮核 + 柔和外边界」渲染范式。

**本轮未引入任何依赖**：本仓硬约束是零新依赖 + BOM 2025.12.00 锁定，是否改用 Mirage 属于待拍板决策。

## 2. 同类项目清单

| 项目 | 形态 | 许可 / 版本 | 关键能力 | 与本工程关系 |
| --- | --- | --- | --- | --- |
| [Mirage](https://github.com/AndroidPoet/mirage) (AndroidPoet) | AGSL shader 库 | Apache-2.0 / 0.1.0 / API 33+（minSdk 21 可依赖，调用处 gate） | 29 个 shader：`MeshGradient`（流动色斑）、`Swirl`（同心带绕心扭曲）、`GrainGradient(softness, shape=BLOB/Sphere)`、`SmokeRing`（湍流环）、`NeuroNoise`（神经丝缕）、`StaticRadialGradient(falloff)`；统一 `speed` / `sizing` 参数；`RuntimeShader` + `ShaderBrush` | **渲染工艺最贴切**。算法译自 [Paper Shaders](https://github.com/paper-design/shaders) (Apache-2.0) |
| [OrbMotion](https://github.com/valentineRutto/OrbMotionKMPLibrary) | KMP AI/agent 活动指示器库 | Maven Central `io.github.valentinerutto:orbmotion:1.0.1` | 可定制动画 orb，支持不同 **states / sizes / speeds / colors**；Android + iOS | **产品形态最像**：就是「AI 助手启动球」，状态命名可借鉴 |
| [Operit / SiriBall.kt](https://github.com/AAswordman/Operit/blob/main/app/src/main/java/com/ai/assistance/operit/ui/floating/ui/ball/SiriBall.kt) | 国产开源 AI 助手悬浮球 | 源码可读 | 4 色光斑慢速旋转 + 三层 ripple 音波 + 中心亮核 + 柔和外边界；状态 `Idle/Pressing/Loading/Replying` | 手势与边缘处理可直接参照 |
| [drinkthestars/shady](https://github.com/drinkthestars/shady) | AGSL shader 画廊 | 源码可读 | `RuntimeShader(AGSL)` + `ShaderBrush` 用法；API <33 用 `BitmapShader`/`LinearGradient` 降级 | 印证 #3 调研的 R2 路线 |
| [JARVIS orb.js](https://gist.github.com/KaushikShresth07/09b2a40d6fe51bd95ef22025bd44a89b) | WebGL GLSL 发光球 | gist | 3D simplex noise + hue rotation + `hover/rot` 平滑 ramp | 参考「状态平滑过渡」思路 |

## 3. 三个可直接借鉴的点（已落地到本仓 v0.0.3test）

### 3.1 边缘羽化 / 中心打光 / 无阴影

维护者反馈：「周围边缘模糊一点，逐渐淡化，不要像 3D 球；光从中间打；不要阴影」。

- SiriBall 的做法：`中心明亮核心`（`Brush.radialGradient` 以 center 为圆心）+ `整体柔和外边界`（透明→淡白→透明），
  以及它**确实画了左上高光**（`highlightCenter = center - 0.25r`）—— 那正是我们不想要的 3D 球感来源。
- Mirage 的做法：把边缘软化抽象成参数 `softness` / `falloff` / `blur` / `edges`。
- 本仓落地：`NongyuOrb.kt` 的 `drawSphereFinish` 改为**以 center 为唯一圆心**的亮核 +
  0.72→1.0 环带由极淡绿渐隐到 `Color.Transparent`；删除偏移中心的柔光层与 `colorRimShade` 暗角；
  `MainShell.kt` 去掉 `clip(CircleShape)`（硬裁剪会把羽化软边切成硬圆）。

### 3.2 长按手势状态机

SiriBall 的注释实现（`onPress { pressStartTime; tryAwaitRelease(); 按时长判定长按/短按 }` + `onDragCancel → Idle`）
与本仓 v0.0.2test 的修复一致：**长按压住时进激活态、抬手必定复位**。可参考它把「松手后是否发送语音」按 300ms 阈值判定。

### 3.3 状态命名

OrbMotion / SiriBall 都把球的状态显式建模（`Idle / Pressing / Loading / Replying` 或 `idle / listening / thinking`）。
本仓当前是 `OrbMode { Idle, Listening, Input }`，后续接语音/agent 时可扩 `Thinking / Replying`。

## 4. 渲染工艺对照：自研 vs 引入 Mirage

| 维度 | 当前自研（AGSL fbm+域扭曲） | 引 / 移植 Mirage |
| --- | --- | --- |
| 依赖 | **零新依赖**（全在 BOM 内） | 新增 `io.github.androidpoet:mirage:0.1.0`（或把单个 shader 源码拷进来） |
| 参数化 | 手写常量，改参数要改 AGSL 字符串 | `MeshGradient(colors, distortion, swirl, speed, sizing)` 等现成 uniform |
| 边缘羽化 | 手写 radialGradient alpha | `softness` / `falloff` 现成参数 |
| 低版本 | 自研 Canvas 降级 | 库自身要求调用处 `SDK_INT >= 33` gate，降级同样要自己写 |
| 维护 | 自己维护 shader 字符串（无编译期校验） | 上游维护，Apache-2.0 |
| 与本仓约束 | 符合 | **与「零新依赖」冲突，需维护者拍板** |

**待决策（不代拍板）**：是否把 Mirage 的 `MeshGradient` / `Swirl` / `GrainGradient` shader 源码
（Apache-2.0，保留 NOTICE）移植进 `ui/ai/shaders/`，替代当前手写 AGSL？
好处是参数化与上游维护；代价是要处理 API 33 gate、保留许可声明，且可能偏离「零依赖」原则。

## 5. 参考资料

- https://github.com/AndroidPoet/mirage
- https://github.com/paper-design/shaders
- https://github.com/valentineRutto/OrbMotionKMPLibrary
- https://github.com/AAswordman/Operit/blob/main/app/src/main/java/com/ai/assistance/operit/ui/floating/ui/ball/SiriBall.kt
- https://github.com/drinkthestars/shady
- https://gist.github.com/KaushikShresth07/09b2a40d6fe51bd95ef22025bd44a89b
- 本仓既有：`docs/research/compose-ai-orb-button.md`（R1/R2/低版本降级）
