package com.nongyu.appplus.net.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 令牌密文格式的纯 JVM 测试。
 *
 * ## 为什么这些测试值得存在
 * 真机上的 `AndroidKeyStore` 在纯 JVM 里不存在，所以 instrumented 测试换不来构建期保障。
 * 但**格式错误**（IV 没拼上、切分位置错、IV 长度漂移）恰恰是最隐蔽的一类 bug：
 * 它不会崩，只会在「用户换设备/重装后令牌再也解不开」时暴露。
 * 把格式抽成 [TokenCipherLayout] 后，就能用普通单测钉死。
 *
 * 另外这里用 JDK 自带的 `AES/GCM/NoPadding` 做一次**真实加解密往返**——
 * 与生产代码用的是同一套 `javax.crypto`，只是密钥来自内存而不是 Keystore，
 * 因此能真实验证「按这个格式打包，GCM 解得开」。
 */
class TokenCipherLayoutTest {

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun encrypt(key: SecretKey, plain: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return TokenCipherLayout.pack(cipher.iv, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    private fun decrypt(key: SecretKey, packed: ByteArray): String {
        val (iv, body) = TokenCipherLayout.unpack(packed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TokenCipherLayout.GCM_TAG_BITS, iv),
        )
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    @Test
    fun `pack 把 IV 放在最前面，长度为 IV 加密文`() {
        val iv = ByteArray(TokenCipherLayout.IV_SIZE) { it.toByte() }
        val body = byteArrayOf(9, 8, 7, 6, 5)
        val packed = TokenCipherLayout.pack(iv, body)

        assertEquals(TokenCipherLayout.IV_SIZE + body.size, packed.size)
        assertArrayEquals("前 12 字节必须是 IV", iv, packed.copyOfRange(0, TokenCipherLayout.IV_SIZE))
        assertArrayEquals("其余必须是密文", body, packed.copyOfRange(TokenCipherLayout.IV_SIZE, packed.size))
    }

    @Test
    fun `unpack 与 pack 互逆`() {
        val iv = ByteArray(TokenCipherLayout.IV_SIZE) { (it * 3).toByte() }
        val body = ByteArray(40) { (it * 7).toByte() }
        val (gotIv, gotBody) = TokenCipherLayout.unpack(TokenCipherLayout.pack(iv, body))
        assertArrayEquals(iv, gotIv)
        assertArrayEquals(body, gotBody)
    }

    @Test
    fun `IV 长度不对时 pack 拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            TokenCipherLayout.pack(ByteArray(11), byteArrayOf(1, 2, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TokenCipherLayout.pack(ByteArray(13), byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun `空密文时 pack 拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            TokenCipherLayout.pack(ByteArray(TokenCipherLayout.IV_SIZE), ByteArray(0))
        }
    }

    @Test
    fun `总长度不足时 unpack 拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            TokenCipherLayout.unpack(ByteArray(TokenCipherLayout.IV_SIZE)) // 只有 IV，没有密文
        }
        assertThrows(IllegalArgumentException::class.java) {
            TokenCipherLayout.unpack(ByteArray(0))
        }
    }

    @Test
    fun `真实 AES-GCM 往返：按该格式打包后能解回原文`() {
        val key = newKey()
        val token = "session-token-测试-${System.nanoTime()}"
        val packed = encrypt(key, token)
        assertEquals(token, decrypt(key, packed))
    }

    @Test
    fun `同一明文两次加密得到不同密文（IV 随机）`() {
        val key = newKey()
        val plain = "same-plaintext"
        val a = encrypt(key, plain)
        val b = encrypt(key, plain)
        assertNotEquals(
            "两次加密的打包结果必须不同（IV 每次都随机），否则等于 ECB 语义",
            a.toList(),
            b.toList(),
        )
        assertEquals(plain, decrypt(key, a))
        assertEquals(plain, decrypt(key, b))
    }

    @Test
    fun `换一把密钥就解不开（模拟换机后 Keystore Key 不在）`() {
        val original = newKey()
        val other = newKey()
        val packed = encrypt(original, "token")
        // GCM 认证失败会抛 AEADBadTagException（GeneralSecurityException 的子类）
        assertThrows(java.security.GeneralSecurityException::class.java) {
            decrypt(other, packed)
        }
    }

    @Test
    fun `密文体被篡改就解不开（GCM 认证生效）`() {
        val key = newKey()
        val packed = encrypt(key, "token-to-tamper")
        val tampered = packed.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()
        assertThrows(java.security.GeneralSecurityException::class.java) {
            decrypt(key, tampered)
        }
    }

    @Test
    fun `打包结果里不含明文令牌`() {
        val key = newKey()
        val token = "PLAINTEXT_CANARY_$"
        val packed = encrypt(key, token)
        val asLatin1 = packed.toString(Charsets.ISO_8859_1)
        assertTrue("密文里不得出现明文", !asLatin1.contains("PLAINTEXT_CANARY"))
    }
}
