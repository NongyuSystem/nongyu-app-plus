package com.nongyu.appplus.login.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LoginTransition] 纯 JVM 单测（票 #94 验收项「UI 状态测试覆盖三阶段时序与仅主动成功路径条件」）。
 *
 * 重点钉死三件容易做错的事：
 * 1. 三段**连续**：进度任一时刻三段函数都有定义，不存在空洞；
 * 2. 交叉溶解**真的重叠**：按钮还没完全消失时主界面就已经开始出现；
 * 3. 冷启动**绝对不播**：只有 LOGIN_CENTER → MAIN 才播。
 */
class LoginTransitionTest {

    // ---------- 仅主动成功路径 ----------

    @Test
    fun `登录中心到主界面才播放`() {
        assertTrue(
            LoginTransition.shouldPlay(
                LoginDestination.LOGIN_CENTER,
                LoginDestination.MAIN,
            ),
        )
    }

    @Test
    fun `冷启动直接进主界面不播放`() {
        // 冷启动首帧就是 MAIN，没有从登录中心切过来的过程：
        // 用 MAIN -> MAIN 表示「本来就在主界面」，不应播转场
        assertFalse(
            LoginTransition.shouldPlay(
                LoginDestination.MAIN,
                LoginDestination.MAIN,
            ),
        )
    }

    @Test
    fun `会话失效切回登录中心不播放`() {
        assertFalse(
            LoginTransition.shouldPlay(
                LoginDestination.MAIN,
                LoginDestination.LOGIN_CENTER,
            ),
        )
    }

    @Test
    fun `留在登录中心不播放`() {
        // 失败 / 取消 / 超时都不改 destination，等价于 LOGIN_CENTER -> LOGIN_CENTER
        assertFalse(
            LoginTransition.shouldPlay(
                LoginDestination.LOGIN_CENTER,
                LoginDestination.LOGIN_CENTER,
            ),
        )
    }

    // ---------- 阶段 1：按钮淡出放大 ----------

    @Test
    fun `起点按钮完全不透明且未放大`() {
        assertEquals(1f, LoginTransition.buttonAlpha(0f), 0.001f)
        assertEquals(1f, LoginTransition.buttonScale(0f), 0.001f)
    }

    @Test
    fun `按钮在阶段1末尾归零并放大到顶`() {
        assertEquals(0f, LoginTransition.buttonAlpha(LoginTransition.BUTTON_END), 0.001f)
        assertEquals(
            LoginTransition.BUTTON_SCALE_END,
            LoginTransition.buttonScale(LoginTransition.BUTTON_END),
            0.001f,
        )
    }

    @Test
    fun `按钮淡出后始终保持透明`() {
        val after = LoginTransition.BUTTON_END + 0.1f
        assertEquals(0f, LoginTransition.buttonAlpha(after), 0.001f)
        assertEquals(0f, LoginTransition.buttonAlpha(1f), 0.001f)
    }

    // ---------- 阶段 2：交叉溶解 ----------

    @Test
    fun `交叉溶解是真重叠_按钮未消失时主界面已开始出现`() {
        // 取一个仍在阶段 1 内部、但已过主界面起点的进度
        val p = LoginTransition.BUTTON_END * 0.8f
        val buttonStillVisible = LoginTransition.buttonAlpha(p) > 0f
        val mainStarted = LoginTransition.mainAlpha(p) > 0f
        assertTrue("按钮此时应仍可见", buttonStillVisible)
        assertTrue("主界面此时应已开始出现（与按钮重叠）", mainStarted)
    }

    @Test
    fun `主界面在阶段1前半段为全透明`() {
        assertEquals(0f, LoginTransition.mainAlpha(0f), 0.001f)
        assertEquals(0f, LoginTransition.mainAlpha(0.1f), 0.001f)
    }

    @Test
    fun `主界面在交叉溶解末尾完全不透明`() {
        assertEquals(1f, LoginTransition.mainAlpha(LoginTransition.MAIN_END), 0.001f)
        assertEquals(1f, LoginTransition.mainAlpha(1f), 0.001f)
    }

    @Test
    fun `主界面alpha单调不减`() {
        var prev = -1f
        var p = 0f
        while (p <= 1.0001f) {
            val a = LoginTransition.mainAlpha(p)
            assertTrue("主界面 alpha 不应回退（p=$p）", a >= prev - 0.0001f)
            prev = a
            p += 0.02f
        }
    }

    // ---------- 阶段 3：内容入场 ----------

    @Test
    fun `内容在交叉溶解结束时才开始入场`() {
        assertEquals(0f, LoginTransition.contentAlpha(0f), 0.001f)
        assertEquals(0f, LoginTransition.contentAlpha(LoginTransition.MAIN_END), 0.001f)
    }

    @Test
    fun `内容在终点完全显影`() {
        assertEquals(1f, LoginTransition.contentAlpha(1f), 0.001f)
    }

    @Test
    fun `内容alpha单调不减`() {
        var prev = -1f
        var p = 0f
        while (p <= 1.0001f) {
            val a = LoginTransition.contentAlpha(p)
            assertTrue("内容 alpha 不应回退（p=$p）", a >= prev - 0.0001f)
            prev = a
            p += 0.02f
        }
    }

    // ---------- 三段连续性 ----------

    @Test
    fun `三段按进度依次切换`() {
        assertEquals(LoginTransition.Stage.BUTTON_OUT, LoginTransition.stageOf(0f))
        assertEquals(LoginTransition.Stage.BUTTON_OUT, LoginTransition.stageOf(0.2f))
        assertEquals(
            LoginTransition.Stage.CROSSFADE,
            LoginTransition.stageOf(LoginTransition.BUTTON_END + 0.01f),
        )
        assertEquals(LoginTransition.Stage.CROSSFADE, LoginTransition.stageOf(0.6f))
        assertEquals(
            LoginTransition.Stage.CONTENT_IN,
            LoginTransition.stageOf(LoginTransition.MAIN_END + 0.01f),
        )
        assertEquals(LoginTransition.Stage.CONTENT_IN, LoginTransition.stageOf(1f))
    }

    @Test
    fun `整条转场在每一时刻都有可见内容`() {
        // 不允许出现「按钮已透明 + 主界面仍全透明」的全黑/全灰空档
        var p = 0f
        while (p <= 1.0001f) {
            val button = LoginTransition.buttonAlpha(p)
            val main = LoginTransition.mainAlpha(p)
            assertTrue("p=$p 时按钮与主界面不能同时全透明", button > 0f || main > 0f)
            p += 0.02f
        }
    }

    @Test
    fun `总时长是正数`() {
        assertTrue(LoginTransition.DURATION_MS > 0)
    }

    @Test
    fun `进度越界被夹紧`() {
        assertEquals(1f, LoginTransition.buttonAlpha(-1f), 0.001f)
        assertEquals(0f, LoginTransition.buttonAlpha(2f), 0.001f)
        assertEquals(1f, LoginTransition.mainAlpha(2f), 0.001f)
    }
}
