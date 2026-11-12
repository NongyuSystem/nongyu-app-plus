package com.nongyu.appplus.design


/**
 * 屏宽自适应基准与换算（全工程唯一出处）。
 *
 * 固定 dp 在不同 dp 宽度的设备上观感差异极大：
 * 蓝叠 1920x1080@240dpi = 1280dp 宽，手机 1080x2400@420dpi ≈ 411dp 宽，
 * 同一组固定 dp 在大画布上显得很小、在小画布上显得很大。
 * 因此以 411dp（典型手机）为基准按屏宽缩放。
 *
 * 上限曾经放到 1.55，实测在大画布上整套 UI 被等比放大得过于笨重，
 * 现压到 1.25（手机上就是 1.0，720dp 左右的平板上约 1.25）。
 *
 * 改动基准值只需改这里 —— 之前 MainShell 与 TimetableScreen 各写了一份，
 * 改一处忘另一处就会两屏比例不一致。
 */
const val UI_SCALE_BASE_WIDTH_DP: Float = 411f

/** 下限：再窄的屏也不把 UI 缩到看不清。 */
const val UI_SCALE_MIN: Float = 0.85f

/** 上限：见类注释，1.55 的时代已经过去。 */
const val UI_SCALE_MAX: Float = 1.25f

/** 屏宽（dp）→ 自适应系数，夹在 `[UI_SCALE_MIN, UI_SCALE_MAX]`。 */
fun uiScaleFor(widthDp: Float): Float =
    (widthDp / UI_SCALE_BASE_WIDTH_DP).coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
