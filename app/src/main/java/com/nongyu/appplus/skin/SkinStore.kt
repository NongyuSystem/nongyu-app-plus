package com.nongyu.appplus.skin

import java.io.File
import java.io.IOException

/**
 * 皮肤包的**本地存储层**（票 #40 / 规格 #33，ADR-0003 Q4b）。
 *
 * 负责三件事，都是 ADR-0003 明确定下的：
 * 1. **`filesDir` 而非 `cacheDir`** —— 皮肤包是用户资产，不能被系统在低存储时清掉。
 * 2. **原子 rename** —— 新包先写到临时目录，完整校验后再 rename 就位；半成品永远不会被当成可用包。
 * 3. **当前指针** —— 一个独立小文件指向「现在生效的是哪一套」；回滚 = 把指针改回去，不删数据。
 *
 * 磁盘上**最多并存两套**（ADR-0003）：当前 + 上一套（供回滚）。
 *
 * ## 为什么把「选择逻辑」与「文件操作」分开
 *
 * 文件操作依赖真实磁盘、不好做边界测试（磁盘满、rename 失败、指针损坏）。
 * 所以「该保留哪几套、指针指向谁」被抽成纯函数 [selectSlotToEvict] / [resolvePointer]，
 * 可以在纯 JVM 单测里覆盖所有分支；[SkinStore] 只负责照做。
 */
object SkinStoreLayout {

    /** 指针文件名。独立小文件，内容就是当前生效的槽位名。 */
    const val POINTER_FILE = "current-skin"

    /** 两套槽位名（ADR-0003：磁盘最多并存两套）。 */
    val SLOTS = listOf("slot-a", "slot-b")

    /**
     * 解析指针内容 → 有效槽位。
     *
     * **指针损坏必须安全**：内容为空、乱码、指向不存在的槽位时返回 `null`，
     * 调用方据此回落内置默认。绝不能因为一个坏指针让整个 App 打不开（ADR-0006）。
     *
     * @param pointerContent 指针文件的原始文本；`null` = 文件不存在。
     * @param existingSlots 磁盘上真实存在且**已通过校验**的槽位集合。
     */
    fun resolvePointer(pointerContent: String?, existingSlots: Set<String>): String? {
        val name = pointerContent?.trim().orEmpty()
        if (name.isEmpty()) return null
        // 只接受已知槽位名；不信任任意字符串（防御指针被外部写入）。
        if (name !in SLOTS) return null
        if (name !in existingSlots) return null
        return name
    }

    /**
     * 选出「新包该写进哪个槽位」。
     *
     * 规则：优先选**不是当前生效**的那个槽位——这样即使写新包的过程中崩溃，
     * 当前生效的那套仍然完好（这是原子性的关键：永远不原地覆盖正在用的包）。
     */
    fun slotForNewInstall(currentSlot: String?): String {
        val free = SLOTS.firstOrNull { it != currentSlot }
        // currentSlot 为 null 或非法时，free 必为 SLOTS 第一个。
        return free ?: SLOTS.first()
    }

    /**
     * 选出「装上新包后该淘汰哪一套」以维持最多两套。
     *
     * 装好新包后，磁盘上应只剩：新包（成为 current）+ 上一套 current（供回滚）。
     * 其余槽位一律清掉。
     *
     * @return 要删除的槽位名列表（不含 newSlot 与 previousCurrent）。
     */
    fun selectSlotToEvict(
        newSlot: String,
        previousCurrent: String?,
        existingSlots: Set<String>,
    ): List<String> = existingSlots
        .filter { it != newSlot && it != previousCurrent }
        .sorted()
}

/**
 * 一次安装尝试的结果。
 *
 * 明确区分「成功装了新包」与「原样保持」：安装失败**不得**影响当前生效的那套
 * ——这正是「新包写空闲槽位」的意义。
 */
sealed interface InstallOutcome {
    /** 新包已就位并成为当前指针目标。 */
    data class Installed(val slot: String, val evicted: List<String>) : InstallOutcome

    /** 没有做任何改动（校验失败 / IO 失败 / 包为空）。当前生效的仍是原来那套。 */
    data class Unchanged(val reason: String) : InstallOutcome
}

/**
 * 基于真实文件系统的皮肤包存储。
 *
 * @param baseDir 应传入 `context.filesDir`（**不是** `cacheDir`）。
 * @param log 日志输出，测试里可注入收集器断言「失败被记下」。
 */
class SkinStore(
    private val baseDir: File,
    private val log: (String) -> Unit = { },
) {

    private val pointerFile: File get() = File(baseDir, SkinStoreLayout.POINTER_FILE)

    private fun slotDir(slot: String): File = File(baseDir, slot)

    /**
     * 读当前生效的槽位目录。
     *
     * **这是启动路径上唯一会碰磁盘的地方**（ADR-0006：只读本地、不碰网络）。
     * 它刻意只做「读一个小文本 + 确认目录存在」，不做校验、不做版本比对
     * ——校验在建包时就做过了（[install]）。
     *
     * @return 当前生效的包目录；`null` = 没有可用包，调用方回落内置默认。
     */
    fun currentPackDir(): File? {
        val content = runCatching {
            if (pointerFile.isFile) pointerFile.readText() else null
        }.getOrElse {
            log("皮肤指针读取失败：${it.message}")
            null
        }

        val existing = SkinStoreLayout.SLOTS
            .filter { slotDir(it).isDirectory }
            .toSet()

        val slot = SkinStoreLayout.resolvePointer(content, existing)
        if (slot == null) {
            if (content != null) {
                log("皮肤指针无效（'${content.trim()}'），回落内置默认")
            }
            return null
        }
        return slotDir(slot)
    }

    /**
     * 原子写指针：先写 `current-skin.tmp`，再 rename 覆盖正式文件。
     *
     * 直接 `writeText` 在崩溃/断电时会留下**半截内容**，下次启动读到垃圾指针
     * —— 而 ADR-0003 明确要求「原子改写当前指针」。rename 在同一文件系统内是原子的，
     * 所以读者要么看到旧的完整内容，要么看到新的完整内容，不会看到半截。
     */
    private fun writePointerAtomically(slot: String) {
        val tmp = File(baseDir, "${SkinStoreLayout.POINTER_FILE}.tmp")
        tmp.writeText(slot)
        if (!tmp.renameTo(pointerFile)) {
            // rename 失败时退一步直接写（至少保证指针可用），并记录。
            log("指针 rename 失败，改用直接写入")
            pointerFile.writeText(slot)
            tmp.delete()
        }
    }

    /**
     * 把一套**已通过校验**的包内容装进空闲槽位并切换指针。
     *
     * 调用方负责：解包到 [unpackedDir]、跑完 `SkinPackIntegrity.verify` 与 `SkinPackPlanner.plan`，
     * 只在结论 OK 时才调这里。本方法只做搬运与切换，**不做校验**。
     *
     * 原子性保证：
     * 1. 先复制到 `slot-x.tmp`；
     * 2. 完整复制成功后再 `renameTo(slot-x)`；
     * 3. 最后才写指针。
     * 任何一步失败都不动当前指针 → 线上那套仍然可用。
     */
    fun install(unpackedDir: File): InstallOutcome {
        if (!unpackedDir.isDirectory) {
            return InstallOutcome.Unchanged("源目录不存在：${unpackedDir.path}")
        }

        val previousSlot = currentPackDir()?.name
        val newSlot = SkinStoreLayout.slotForNewInstall(previousSlot)
        val target = slotDir(newSlot)
        val staging = File(baseDir, "$newSlot.tmp")

        try {
            // 1) 清掉可能残留的半成品。
            staging.deleteRecursively()
            if (!staging.mkdirs() && !staging.isDirectory) {
                return InstallOutcome.Unchanged("无法创建暂存目录")
            }

            // 2) 复制到暂存目录。
            unpackedDir.copyRecursively(staging, overwrite = true)

            // 3) 就位。注意顺序：**先把旧槽位改名挪开，再 rename 新的进来**。
            //    绝不能用「先 deleteRecursively 再 rename」——那中间崩溃会让新旧俱失，
            //    而旧的那套可能正是当前生效的（虽然 slotForNewInstall 已避开它，
            //    但槽位目录仍可能残留上一轮的同名半成品）。
            val displaced = File(baseDir, "$newSlot.old")
            if (target.exists()) {
                displaced.deleteRecursively()
                if (!target.renameTo(displaced)) {
                    log("皮肤包旧槽位挪开失败：$target -> $displaced")
                    return InstallOutcome.Unchanged("旧槽位无法挪开")
                }
            }
            if (!staging.renameTo(target)) {
                // 新包没能就位 → 把旧的挪回去，保持现状不变。
                displaced.renameTo(target)
                log("皮肤包 rename 失败：$staging -> $target（已回滚旧槽位）")
                return InstallOutcome.Unchanged("rename 失败")
            }
            // 新包已就位，旧槽位才算可以丢。
            displaced.deleteRecursively()

            // 4) 指针最后写，且**自身也要原子**：先写临时文件再 rename 覆盖。
            //    ADR-0003 要求「原子改写当前指针」；直接 writeText 会在崩溃时留下半截内容。
            writePointerAtomically(newSlot)

            val evicted = SkinStoreLayout.selectSlotToEvict(
                newSlot = newSlot,
                previousCurrent = previousSlot,
                existingSlots = SkinStoreLayout.SLOTS.filter { slotDir(it).isDirectory }.toSet(),
            )
            evicted.forEach { slot ->
                slotDir(slot).deleteRecursively()
                log("皮肤槽位已淘汰：$slot")
            }

            log("皮肤包已安装：$newSlot（上一套：${previousSlot ?: "无"}）")
            return InstallOutcome.Installed(newSlot, evicted)
        } catch (e: IOException) {
            log("皮肤包安装失败（IO）：${e.message}")
            runCatching { staging.deleteRecursively() }
            return InstallOutcome.Unchanged("IO 失败：${e.message}")
        }
    }
}
