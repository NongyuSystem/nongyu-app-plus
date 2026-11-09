package com.nongyu.appplus.net.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 设备上的加密存储往返测试（需要真实 Keystore，纯 JVM 测不了）。
 *
 * 覆盖三条：
 * 1. **往返**：写入后能原样读出（证明 AES/GCM 加解密链路通）。
 * 2. **落盘是密文**：DataStore 文件里**不得出现明文令牌**（证明真的加密了，不是明文落盘）。
 * 3. **容错**：把落盘文件内容破坏后，`readToken()` 返回 null 且不抛
 *    （证明「解密失败 = 无 token」这条硬约束成立，换机恢复备份不会崩）。
 */
@RunWith(AndroidJUnit4::class)
class MinimalSessionStoreInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun store() = MinimalSessionStore(context)

    private val dataStoreFile: File
        get() = File(context.filesDir, "datastore/${SecurePrefs.DATASTORE_NAME}.preferences_pb")

    @Before
    fun clean() {
        store().clear()
        dataStoreFile.delete()
    }

    @Test
    fun writeThenRead_roundTrips() {
        val token = "test-token-${System.nanoTime()}"
        store().writeToken(token)
        assertEquals("写入后应能原样读出", token, store().readToken())
    }

    @Test
    fun storedBytes_doNotContainPlaintext() {
        val token = "PLAINTEXT_CANARY_${System.nanoTime()}"
        store().writeToken(token)

        assertTrue("DataStore 文件应已生成：${dataStoreFile.absolutePath}", dataStoreFile.exists())
        val raw = dataStoreFile.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("落盘内容不得包含明文令牌（说明没加密）", !raw.contains(token))
        // 反向断言：文件确实有内容（避免「什么都没写所以当然不含明文」的假通过）
        assertNotEquals("落盘文件不该为空", 0, dataStoreFile.length())
    }

    @Test
    fun corruptedCiphertext_returnsNullAndDoesNotThrow() {
        store().writeToken("will-be-corrupted-${System.nanoTime()}")
        assertTrue("前置条件：文件应存在", dataStoreFile.exists())

        // 把密文段整体替换成等长垃圾字节
        val bytes = dataStoreFile.readBytes()
        for (i in bytes.indices) {
            val b = bytes[i]
            if (b in 'A'.code.toByte()..'z'.code.toByte()) bytes[i] = 'Q'.code.toByte()
        }
        dataStoreFile.writeBytes(bytes)

        // 关键断言：不抛异常，且视为无 token
        val result = store().readToken()
        assertNull("密文损坏时必须当作没有 token，而不是抛异常", result)
    }

    @Test
    fun clear_isIdempotent() {
        store().writeToken("to-be-cleared")
        store().clear()
        assertNull(store().readToken())
        store().clear() // 再清一次不应抛
        assertNull(store().readToken())
    }
}
