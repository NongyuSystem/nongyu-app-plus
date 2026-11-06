# 第三方 shader 署名 / NOTICE

本目录的 `MirageOrb.kt` 中 AGSL 算法衍生自以下项目，按 Apache-2.0 保留署名：

## Mirage
- 项目：https://github.com/AndroidPoet/mirage
- 版本：0.1.0（2026-07-05）
- 作者：androidpoet (Ranbir Singh)
- 许可：Apache License 2.0 — https://github.com/AndroidPoet/mirage/blob/master/LICENSE
- 用法：移植 `MeshGradient.kt` 的算法主体（流动色斑 `getPosition` + 域扭曲 +
  `hash21` / `rotate` / `valueNoise` + 加权主循环）。
- 改动：移除 `SIZING_AGSL` 全套 sizing uniform（固定为居中正方形）、
  把 `u_colors` 运行期 uniform 改为编译期常量（农屿白/绿/黄）、
  叠加「中心打光 + 边缘羽化到透明」、去掉 3D 高光与暗角。
- 未引入 Mirage 依赖（本仓零新依赖约束），仅移植源码算法。

## Paper Shaders
- 项目：https://github.com/paper-design/shaders
- 许可：Apache License 2.0
- 说明：Mirage 的 shader 算法译自本项目的 GLSL ES 3.00 实现。
- 见 Mirage 仓库 `NOTICE`：https://github.com/AndroidPoet/mirage/blob/master/NOTICE

原始许可全文：http://www.apache.org/licenses/LICENSE-2.0
