package com.nongyu.appplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单测基建的冒烟测试（票 #35）。
 *
 * 存在的目的是**证明测试基建真的通了**，而不是覆盖业务逻辑：
 * 这些用例只依赖 JDK 与 junit4，不碰 Android Framework，
 * 因此能在 `:app:testDebugUnitTest` 下以普通 JVM 进程执行。
 *
 * 真正的业务断言在 T2 起落在「皮肤数据源接口」这个主接缝上（规格 #33
 * 的 Testing Decisions）。本文件不做业务断言是有意的：
 * 基建票不应该为了凑断言而锁死后续设计的实现细节。
 */
class TestInfraSmokeTest {

    /**
     * 真实断言：JUnit 的断言链确实在工作。
     * 若 JUnit runner 没接上，这个用例根本不会被执行，
     * 而失败注入用例（[deliberateFailureIsDetected]）保证它不会静默空跑。
     */
    @Test
    fun assertionsActuallyRun() {
        assertEquals("junit4 断言可用", 4, 2 + 2)
        assertNotEquals(1, 2)
        assertTrue("布尔断言可用", listOf(1, 2, 3).sum() == 6)
    }

    /**
     * 失败注入：故意让一个断言失败，验证 runner 能**检出**失败。
     *
     * 做法是把「必定失败的断言」放进 `runCatching` 里，断言它确实抛了
     * [AssertionError]。这样既证明断言机制会报错（而不是永远返回 true），
     * 又不会让整个测试任务变红。
     */
    @Test
    fun deliberateFailureIsDetected() {
        val thrown = runCatching { assertEquals("这次故意不相等", 1, 2) }.exceptionOrNull()

        assertTrue(
            "故意失败的断言必须抛 AssertionError，实际是 $thrown",
            thrown is AssertionError,
        )
        assertTrue(
            "AssertionError 应带上我们写的失败信息，实际是 ${thrown?.message}",
            thrown?.message?.contains("这次故意不相等") == true,
        )
    }

    /**
     * 纯 JVM 环境自证：跑在 JDK 上，不是 Android 运行时。
     * 这条同时是「本票要求 JDK 17」的运行期证据。
     */
    @Test
    fun runsOnPlainJvmNotAndroid() {
        val javaVersion = System.getProperty("java.version").orEmpty()
        val javaVendor = System.getProperty("java.vm.vendor").orEmpty()

        assertTrue("java.version 不应为空，实际是 '$javaVersion'", javaVersion.isNotEmpty())
        assertTrue(
            "单测应跑在 JVM 上（java.specification.name 含 Java），" +
                "实际 java.version=$javaVersion, vendor=$javaVendor",
            System.getProperty("java.specification.name").orEmpty().contains("Java"),
        )
        assertTrue(
            "主版本号应 >= 17（本工程要求 JDK 17），实际是 $javaVersion",
            majorVersionOf(javaVersion) >= 17,
        )
    }

    /** 从 `17.0.17` 这类版本串里取主版本号；取不到时返回 0 让断言失败暴露问题。 */
    private fun majorVersionOf(version: String): Int =
        version.substringBefore('.').toIntOrNull() ?: 0
}
