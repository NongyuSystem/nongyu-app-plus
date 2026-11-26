package com.nongyu.appplus.profile.data

import com.nongyu.appplus.profile.model.ProfileSnapshot

/**
 * **debug 专用**演示画像（票 #113 / 规格 #111）。
 *
 * 与 `DebugMapDataSource` 同理由放 `src/debug`：release 包里这些常量根本不存在，
 * 解包/source set 检查都看不到任何 demo 数据。由 [DemoProfileHolder] 持有，
 * 且 [DemoProfileHolder.enabled] 可一键关闭，关掉后页面回到真实数据链路的空白态。
 *
 * 注意：这里**没有**身份证后四位字段——敏感字段永不进入画像领域模型的可复制路径。
 */
object DebugDemoProfile {
    val snapshot: ProfileSnapshot = ProfileSnapshot(
        studentNo = "20260001",
        name = "演示同学",
        college = "信息学院",
        major = "计算机科学与技术",
        className = "计科 2026-1 班",
        grade = "2026 级",
        qq = "10000",
        hasMeResponse = true,
        // expiresAt 留空：demo 不伪造登录态剩余期，真实时间由登录响应提供。
    )

    /** 由 debug Application 在最早时机调用。 */
    fun install(enabled: Boolean = true) {
        DemoProfileHolder.demo = snapshot
        DemoProfileHolder.enabled = enabled
    }
}
