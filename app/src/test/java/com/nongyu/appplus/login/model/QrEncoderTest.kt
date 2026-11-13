package com.nongyu.appplus.login.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * [QrEncoder] 单测。
 *
 * 除结构断言外，把点阵导出成 PNG 到 `build/qr-fixtures/`，
 * 由外部脚本用 **OpenCV 真实解码**验证内容可还原（避免"画出来是废图"）。
 */
class QrEncoderTest {

    private fun render(matrix: Array<BooleanArray>, scale: Int = 8, quiet: Int = 4): BufferedImage {
        val size = matrix.size
        val dim = (size + quiet * 2) * scale
        val img = BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until dim) for (x in 0 until dim) {
            img.setRGB(x, y, 0xFFFFFF)
        }
        for (y in 0 until size) for (x in 0 until size) {
            if (!matrix[y][x]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                val px = (x + quiet) * scale + dx
                val py = (y + quiet) * scale + dy
                img.setRGB(px, py, 0x000000)
            }
        }
        return img
    }

    @Test
    fun `编码短文本得到方阵且含三个定位图形`() {
        val m = QrEncoder.encode("HELLO") ?: error("编码失败")
        assertTrue(m.isNotEmpty())
        assertEquals(m.size, m[0].size)
        // 三个角各有一个 7x7 定位图形（外框黑）
        assertTrue(m[0][0] && m[0][6] && m[6][0])
        assertTrue(m[0][m.size - 1] && m[m.size - 1][0])
    }

    @Test
    fun `编码约 300 字符的微信授权URL`() {
        val url = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx7c220ea873c845e0" +
            "&redirect_uri=https%3A%2F%2Fauth.sicau.edu.cn%2Fapi%2Fv1%2Fcas%2FloginByQr%3Fappid%3Dywtb" +
            "%26cascallback%3D&response_type=code&scope=snsapi_userinfo" +
            "&state=loginByQr:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=#wechat_redirect"
        val m = QrEncoder.encode(url)
        assertNotNull("300 字符应在容量内", m)
        // 版本 n 的边长 = 17 + 4n；300 字符需要版本 ≥ 14
        val version = (m!!.size - 17) / 4
        assertTrue("版本应 ≥ 11（实际 $version）", version >= 11)
        assertEquals(17 + version * 4, m.size)
    }

    @Test
    fun `超长内容返回 null`() {
        assertNull(QrEncoder.encode("x".repeat(5000)))
    }

    @Test
    fun `导出PNG供外部解码验证`() {
        val dir = File("build/qr-fixtures").apply { mkdirs() }
        val cases = mapOf(
            "short" to "HELLO-QR",
            "v11" to "A".repeat(200),
            "v12" to "B".repeat(230),
            "v14" to "C".repeat(320),
            "mid100" to ("https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx7c220ea873c845e0" +
                "&response_type=code&scope=snsapi_userinfo&state=loginByQr:CCCCCCCCCC="),
            "url300" to ("https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx7c220ea873c845e0" +
                "&redirect_uri=https%3A%2F%2Fauth.sicau.edu.cn%2Fapi%2Fv1%2Fcas%2FloginByQr%3Fappid%3Dywtb" +
                "%26cascallback%3D&response_type=code&scope=snsapi_userinfo" +
                "&state=loginByQr:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=#wechat_redirect"),
        )
        cases.forEach { (name, text) ->
            val m = QrEncoder.encode(text) ?: error("编码失败: $name")
            ImageIO.write(render(m), "png", File(dir, "$name.png"))
            File(dir, "$name.txt").writeText(text)
        }
        assertTrue(File(dir, "short.png").exists())
        assertTrue(File(dir, "url300.png").exists())
    }
}
