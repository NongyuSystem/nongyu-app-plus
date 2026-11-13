package com.nongyu.appplus.login.model

/**
 * 极简 QR 码编码器（纯 Kotlin，零依赖）。
 *
 * ## 为什么自己写
 * 本工程不引第三方 QR 库（避免多一个依赖、也要为 R8 补 keep）。微信授权 URL
 * 约 300 字符，属于小数据量，用 **Byte 模式 + 纠错等级 M + 自动选版本** 足够。
 *
 * ## 覆盖范围（够用即止，不做全量 QR 规范）
 * - 模式：只有 Byte（8bit/字符，UTF-8 编码）——URL 全是 ASCII，够用
 * - 纠错：只有 M（15%），容错与容量的平衡
 * - 版本：1~10 自动选（URL 300 字符在版本 10 的 M 级容量 271 内，取版本 10）
 * - 掩模：固定用 0 号掩模（不追求最优掩模，扫码器对任意掩模都能读）
 *
 * ## 输出
 * 返回 `BooleanArray` 的二维点阵（`true` = 黑），由 UI 用 Canvas 绘制。
 * 这样编码与绘制解耦：编码可纯 JVM 测，UI 只负责画。
 */
object QrEncoder {

    /** 版本参数：总 data 码字数、短块 data 数/块数、长块 data 数/块数、每块纠错码字数。 */
    private data class VersionInfo(
        val version: Int,
        val totalData: Int,
        val shortData: Int,
        val shortCount: Int,
        val longData: Int,
        val longCount: Int,
        val ecPerBlock: Int,
    ) {
        val blocks: Int get() = shortCount + longCount
    }

    /** 版本 1..20、纠错 M 的块结构（QR 规范表）。 */
    private val VERSIONS_M = listOf(
        VersionInfo(1, 16, 16, 1, 0, 0, 10),
        VersionInfo(2, 28, 28, 1, 0, 0, 16),
        VersionInfo(3, 44, 44, 1, 0, 0, 26),
        VersionInfo(4, 64, 32, 2, 0, 0, 18),
        VersionInfo(5, 86, 43, 2, 0, 0, 24),
        VersionInfo(6, 108, 27, 4, 0, 0, 16),
        VersionInfo(7, 124, 31, 4, 0, 0, 18),
        VersionInfo(8, 154, 38, 2, 39, 2, 22),
        VersionInfo(9, 182, 36, 3, 37, 2, 22),
        VersionInfo(10, 216, 43, 4, 44, 1, 26),
        VersionInfo(11, 254, 50, 1, 51, 4, 30),
        VersionInfo(12, 290, 36, 6, 37, 2, 22),
        VersionInfo(13, 334, 37, 8, 38, 1, 22),
        VersionInfo(14, 365, 40, 4, 41, 5, 24),
        VersionInfo(15, 415, 41, 5, 42, 5, 24),
        VersionInfo(16, 453, 45, 7, 46, 3, 28),
        VersionInfo(17, 507, 46, 10, 47, 1, 28),
        VersionInfo(18, 563, 43, 9, 44, 4, 26),
        VersionInfo(19, 627, 44, 3, 45, 11, 26),
        VersionInfo(20, 669, 41, 3, 42, 13, 26),
    )

    /**
     * 编码为点阵。
     *
     * @param content 要编码的文本（UTF-8）
     * @return `size x size` 的布尔点阵，`true` 为黑；数据超出版本 10 时返回 null
     */
    fun encode(content: String): Array<BooleanArray>? {
        val bytes = content.toByteArray(Charsets.UTF_8)
        // 选版本：需要的数据码字数 = 模式指示(4bit) + 长度(8/16bit) + 数据 + 终止
        val info = VERSIONS_M.firstOrNull { v ->
            val lenBits = if (v.version <= 9) 8 else 16
            // 总位数 = 4 + lenBits + bytes*8，换算成字节并留终止余量
            (4 + lenBits + bytes.size * 8 + 7) / 8 <= v.totalData
        } ?: return null

        val bits = BitBuffer()
        bits.append(0b0100, 4)                       // Byte 模式
        val lenBits = if (info.version <= 9) 8 else 16
        bits.append(bytes.size, lenBits)             // 字符数
        bytes.forEach { bits.append(it.toInt() and 0xFF, 8) }

        // 终止符 + 补齐到字节 + 填充字节 0xEC/0x11
        val capacityBits = info.totalData * 8
        val term = minOf(4, capacityBits - bits.size)
        if (term > 0) bits.append(0, term)
        while (bits.size % 8 != 0) bits.append(0, 1)
        var pad = 0
        while (bits.size < capacityBits) {
            bits.append(if (pad % 2 == 0) 0xEC else 0x11, 8)
            pad++
        }

        val data = ByteArray(info.totalData)
        for (i in data.indices) data[i] = bits.byteAt(i).toByte()

        // Reed-Solomon 分块：前 shortCount 个是短块，其余是长块
        val blocks = ArrayList<Block>()
        var offset = 0
        repeat(info.shortCount) {
            val d = data.copyOfRange(offset, offset + info.shortData)
            offset += info.shortData
            blocks.add(Block(d, ReedSolomon.encode(d, info.ecPerBlock)))
        }
        repeat(info.longCount) {
            val d = data.copyOfRange(offset, offset + info.longData)
            offset += info.longData
            blocks.add(Block(d, ReedSolomon.encode(d, info.ecPerBlock)))
        }

        // 交织：数据按块轮流取，短块取完后跳过
        val totalCodewords = info.totalData + info.blocks * info.ecPerBlock
        val codewords = ByteArray(totalCodewords)
        var p = 0
        val maxData = maxOf(info.shortData, info.longData)
        for (i in 0 until maxData) {
            blocks.forEach { b -> if (i < b.data.size) codewords[p++] = b.data[i] }
        }
        for (i in 0 until info.ecPerBlock) {
            blocks.forEach { b -> codewords[p++] = b.ec[i] }
        }

        return MatrixBuilder(info.version, codewords).build()
    }

    private class Block(val data: ByteArray, val ec: ByteArray)

    /** 简易位缓冲。 */
    private class BitBuffer {
        private val bits = ArrayList<Boolean>(1024)
        val size: Int get() = bits.size
        fun append(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits.add((value ushr i) and 1 == 1)
        }
        fun byteAt(index: Int): Int {
            var v = 0
            for (i in 0 until 8) {
                val bit = bits[index * 8 + i]
                v = (v shl 1) or (if (bit) 1 else 0)
            }
            return v
        }
    }
}

/** Reed-Solomon（GF(256)，本原多项式 0x11D）。 */
internal object ReedSolomon {

    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    private fun mul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

    /** 生成多项式系数（标准做法：从常数多项式开始逐次乘 (x - α^i)）。 */
    private fun generator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val res = IntArray(poly.size + 1)
            for (j in poly.indices) {
                res[j] = res[j] xor poly[j]
                res[j + 1] = res[j + 1] xor mul(poly[j], exp[i])
            }
            poly = res
        }
        return poly
    }

    /** 计算 [degree] 个纠错字节。 */
    fun encode(data: ByteArray, degree: Int): ByteArray {
        val gen = generator(degree)
        val res = IntArray(degree)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor res[0]
            System.arraycopy(res, 1, res, 0, degree - 1)
            res[degree - 1] = 0
            for (i in 0 until degree) res[i] = res[i] xor mul(gen[i + 1], factor)
        }
        return ByteArray(degree) { res[it].toByte() }
    }
}

/** 把码字排布到点阵并画功能图形。 */
private class MatrixBuilder(private val version: Int, private val codewords: ByteArray) {

    private val size = 17 + version * 4
    private val modules = Array(size) { BooleanArray(size) }
    private val reserved = Array(size) { BooleanArray(size) }

    fun build(): Array<BooleanArray> {
        drawFunctionPatterns()
        drawCodewords()
        // 固定掩模 0
        applyMask0()
        drawFormatBits()
        return modules
    }

    private fun drawFunctionPatterns() {
        // 定位图形
        drawFinder(0, 0)
        drawFinder(size - 7, 0)
        drawFinder(0, size - 7)
        // 定时图形
        for (i in 8 until size - 8) {
            setFunction(i, 6, i % 2 == 0)
            setFunction(6, i, i % 2 == 0)
        }
        // 校正图形（版本 ≥2）
        if (version >= 2) {
            val pos = alignmentPositions()
            val first = pos.first()
            val last = pos.last()
            for (r in pos) for (c in pos) {
                // 只跳过三个定位图形所在的角（(first,first)/(first,last)/(last,first)）
                val onFinder = (r == first && c == first) ||
                    (r == first && c == last) ||
                    (r == last && c == first)
                if (!onFinder) drawAlignment(c, r)
            }
        }
        // 暗模块
        setFunction(8, size - 8, true)
        // 预留格式信息区
        for (i in 0 until 9) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in 0 until 8) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }
        // 版本信息（版本 ≥7）：6x3 两块，先占位，稍后写入
        if (version >= 7) {
            for (i in 0 until 6) for (j in 0 until 3) {
                reserved[size - 11 + j][i] = true
                reserved[i][size - 11 + j] = true
            }
            drawVersionBits()
        }
    }

    private fun alignmentPositions(): List<Int> = when (version) {
        1 -> emptyList()
        2 -> listOf(6, 18)
        3 -> listOf(6, 22)
        4 -> listOf(6, 26)
        5 -> listOf(6, 30)
        6 -> listOf(6, 34)
        7 -> listOf(6, 22, 38)
        8 -> listOf(6, 24, 42)
        9 -> listOf(6, 26, 46)
        10 -> listOf(6, 28, 50)
        11 -> listOf(6, 30, 54)
        12 -> listOf(6, 32, 58)
        13 -> listOf(6, 34, 62)
        14 -> listOf(6, 26, 46, 66)
        15 -> listOf(6, 26, 48, 70)
        16 -> listOf(6, 26, 50, 74)
        17 -> listOf(6, 30, 54, 78)
        18 -> listOf(6, 30, 56, 82)
        19 -> listOf(6, 30, 58, 86)
        20 -> listOf(6, 34, 62, 90)
        else -> listOf(6, size - 7)
    }

    private fun drawFinder(x: Int, y: Int) {
        for (dy in -1..7) for (dx in -1..7) {
            val xx = x + dx
            val yy = y + dy
            if (xx < 0 || yy < 0 || xx >= size || yy >= size) continue
            val on = (dx in 0..6 && (dy == 0 || dy == 6)) ||
                (dy in 0..6 && (dx == 0 || dx == 6)) ||
                (dx in 2..4 && dy in 2..4)
            setFunction(xx, yy, on)
        }
    }

    private fun drawAlignment(cx: Int, cy: Int) {
        for (dy in -2..2) for (dx in -2..2) {
            val on = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
            setFunction(cx + dx, cy + dy, on)
        }
    }

    private fun setFunction(x: Int, y: Int, dark: Boolean) {
        if (x < 0 || y < 0 || x >= size || y >= size) return
        modules[y][x] = dark
        reserved[y][x] = true
    }

    private fun drawCodewords() {
        var bitIndex = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!reserved[y][x] && bitIndex < codewords.size * 8) {
                        val b = codewords[bitIndex ushr 3].toInt()
                        modules[y][x] = ((b ushr (7 - (bitIndex and 7))) and 1) != 0
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask0() {
        for (y in 0 until size) for (x in 0 until size) {
            if (!reserved[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x]
        }
    }

    /** 写入版本信息（版本 ≥7 才有）。 */
    private fun drawVersionBits() {
        var rem = version
        for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val bit = ((bits ushr i) and 1) != 0
            val x = size - 11 + i % 3   // 列
            val y = i / 3               // 行
            modules[y][x] = bit         // 右上块
            modules[x][y] = bit         // 左下块
        }
    }

    /** 写入格式信息（纠错 M + 掩模 0）。 */
    private fun drawFormatBits() {
        val data = 0b00 // M + mask0
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0b101010000010010
        for (i in 0 until 15) {
            val bit = ((bits ushr i) and 1) != 0
            // 左上
            when (i) {
                in 0..5 -> modules[i][8] = bit
                6 -> modules[7][8] = bit
                7 -> modules[8][8] = bit
                8 -> modules[8][7] = bit
                else -> modules[8][14 - i] = bit
            }
            // 右上/左下
            when (i) {
                in 0..7 -> modules[8][size - 1 - i] = bit
                else -> modules[size - 15 + i][8] = bit
            }
        }
    }
}
