package com.nongyu.appplus.net.auth

/**
 * 令牌密文的**打包格式**——纯逻辑，不依赖 Android，可在 JVM 单测里覆盖。
 *
 * ## 为什么单独抽出来
 * [MinimalSessionStore] 的加解密主体依赖 `AndroidKeyStore`（纯 JVM 里不存在），
 * 但「IV 与密文怎么拼、怎么切」这一段是**纯字节操作**，恰恰是最容易写错、
 * 且错了会导致「换了设备后永远解不开」的地方。抽出来才能在构建期用单测钉住。
 *
 * ## 格式
 * ```
 * base64( iv[12] || ciphertext || tag[16] )
 * ```
 * IV 固定 12 字节（GCM 推荐长度），拼在密文前面。切分时**先**按 [IV_SIZE] 切出 IV，
 * 剩下全部是密文体（GCM 的认证标签已在密文尾部，不需要单独切）。
 *
 * ⚠️ 千万不要把 IV 也放进 Keystore 或另外存一份：IV 不是秘密，
 * 它必须与密文一起落盘，否则换机后即使 Key 还在也解不开。
 */
internal object TokenCipherLayout {

    /** GCM 推荐的 IV 长度（字节）。 */
    const val IV_SIZE: Int = 12

    /** GCM 认证标签长度（位）。 */
    const val GCM_TAG_BITS: Int = 128

    /**
     * 把 IV 与密文拼成一个字节数组。
     *
     * @throws IllegalArgumentException IV 长度不等于 [IV_SIZE] 时抛（防止格式漂移）。
     */
    fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size == IV_SIZE) {
            "IV 长度必须是 $IV_SIZE 字节，实际 ${iv.size}"
        }
        require(ciphertext.isNotEmpty()) { "密文不能为空" }
        return iv + ciphertext
    }

    /**
     * 从打包后的字节里切出 (iv, ciphertext)。
     *
     * @throws IllegalArgumentException 总长度不足以容纳 IV 时抛。
     *   注意：**内容损坏（tag 不匹配）不在这里判断**，那要由 GCM 解密本身抛
     *   `AEADBadTagException`，调用方必须兜住并当作「没有 token」。
     */
    fun unpack(packed: ByteArray): Pair<ByteArray, ByteArray> {
        require(packed.size > IV_SIZE) {
            "密文总长度必须大于 IV 长度($IV_SIZE)，实际 ${packed.size}"
        }
        val iv = packed.copyOfRange(0, IV_SIZE)
        val body = packed.copyOfRange(IV_SIZE, packed.size)
        return iv to body
    }
}
