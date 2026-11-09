package com.nongyu.appplus.skin

import java.io.File
import java.io.IOException

/**
 * 一套已安装皮肤包的身份：`{packId}/{version}`（ADR-0003 第 22 行）。
 *
 * 落盘路径是 `filesDir/skins/{packId}/{version}/`，当前指针内容就是这个二元组的 JSON。
 * 用值对象而不是裸字符串，是为了让「指针解析 / 淘汰选择 / 回滚目标」这些纯函数
 * 的入参可比较、可放进 Set，且测试能直接构造。
 */
data class PackRef(val packId: String, val version: String)

/**
 * 皮肤包存储层的**纯判定逻辑**（票 #57 ②，ADR-0003）。
 *
 * 与文件操作分开的理由和之前一样：指针损坏、目录被删、最多两套、回滚目标这些边界
 * 必须能在纯 JVM 单测里覆盖，而真实磁盘 IO 不好造边界。本对象**不碰 `org.json`**：
 * 指针是客户端自己写的固定二元组 JSON，用一对对称的手写编解码即可，既不引入新依赖，
 * 也避开「org.json 在 :app:testDebugUnitTest 里是 stub」这个已入库的坑。
 *
 * 落盘结构（ADR-0003）：
 * ```
 * filesDir/
 *   skins/{packId}/{version}/…   ← 包内容，最多并存两套（当前 + 上一套）
 *   current-skin                  ← 指针，内容 {"packId":"…","version":"…"}
 * ```
 */
object SkinStoreLayout {

    /** 指针文件名。独立小文件，内容是 [PackRef] 的 JSON。 */
    const val POINTER_FILE = "current-skin"

    /** 版本目录的根名（相对 `filesDir`）。 */
    const val SKINS_DIR = "skins"

    /** 相对 `filesDir` 的版本目录路径，供日志与测试断言（固定用 `/`，与平台无关）。 */
    fun versionRelativePath(ref: PackRef): String = "$SKINS_DIR/${ref.packId}/${ref.version}"

    /**
     * 路径段是否安全。packId / version 会成为磁盘目录名，必须挡住空、`..`、分隔符。
     * 允许字母数字与 `-_.`（版本号常带点，如 `1.0.0`）。
     */
    fun isSafeSegment(s: String): Boolean {
        if (s.isEmpty() || s == "." || s == "..") return false
        if (s.contains("..")) return false
        return s.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    /**
     * 把 [PackRef] 编成指针内容：`{"packId":"…","version":"…"}`。
     * 与 [parsePointer] 严格对称（含转义），保证 round-trip。
     */
    fun encodePointer(ref: PackRef): String =
        """{"packId":"${escape(ref.packId)}","version":"${escape(ref.version)}"}"""

    /**
     * 解析指针内容 → [PackRef]。**损坏必须安全返回 `null`，绝不抛异常**（ADR-0006）。
     *
     * 用固定键名的手写扫描取值，容忍字段顺序与空白；缺失 / 空值 / 非法路径段一律 `null`。
     * 手写而非 org.json（stub），也刻意不引正则四引号原始字符串那套易错的引号定界。
     */
    fun parsePointer(content: String?): PackRef? {
        if (content == null) return null
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val packId = extractField(trimmed, "packId")
        val version = extractField(trimmed, "version")
        if (packId.isNullOrEmpty() || version.isNullOrEmpty()) return null
        if (!isSafeSegment(packId) || !isSafeSegment(version)) return null
        return PackRef(packId, version)
    }

    /**
     * 从固定形状的 JSON 里取 `"key":"value"` 的 value（处理转义）。
     * 找不到 / 没有闭合引号 → `null`。只认我们 [encodePointer] 写出的那一种形状。
     */
    private fun extractField(json: String, key: String): String? {
        val marker = "\"$key\""
        val keyStart = json.indexOf(marker)
        if (keyStart < 0) return null
        var i = keyStart + marker.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++ // 跳过开引号
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '"') return sb.toString() // 未转义的闭引号
            if (c == '\\' && i + 1 < json.length) {
                when (json[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(json[i + 1]) // 未知转义宽容；isSafeSegment 后面会挡
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return null // 没有闭引号 = 半截内容
    }

    /**
     * 解析指针 → 有效且**目录确实存在**的版本。
     *
     * 内容非法 / 指向的目录不在 [existingVersions] 里 → `null`，回落内置默认。
     * 这是启动路径上「指针损坏不崩」的关键。
     */
    fun resolvePointer(content: String?, existingVersions: Set<PackRef>): PackRef? {
        val ref = parsePointer(content) ?: return null
        if (ref !in existingVersions) return null
        return ref
    }

    /**
     * 装上新包后该淘汰哪几套，以维持「最多并存两套」。
     *
     * 保留：新包（[newRef]）+ 上一套当前（[previousCurrent]，供回滚）；其余一律清掉。
     * 这正是槽位版 `selectSlotToEvict` 的语义，只是 key 从槽位名换成 [PackRef]。
     */
    fun selectVersionsToEvict(
        newRef: PackRef,
        previousCurrent: PackRef?,
        existingVersions: Set<PackRef>,
    ): List<PackRef> = existingVersions
        .filter { it != newRef && it != previousCurrent }
        .sortedWith(REF_ORDER)

    /**
     * 回滚目标：磁盘上「不是当前」的那一套（最多两套时恰好一个）。
     * 找不到（只有一套 / 全损坏）→ `null`，回滚失败但不抛异常。
     */
    fun rollbackTarget(current: PackRef, existingVersions: Set<PackRef>): PackRef? =
        existingVersions.filter { it != current }.sortedWith(REF_ORDER).firstOrNull()

    private val REF_ORDER = compareBy<PackRef> { it.packId }.thenBy { it.version }

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}

/** 一次安装尝试的结果。安装失败不得影响当前生效的那套。 */
sealed interface InstallOutcome {
    /** 新包已就位并成为当前指针目标。 */
    data class Installed(val ref: PackRef, val evicted: List<PackRef>) : InstallOutcome

    /** 没有做任何改动（校验失败 / IO 失败 / 参数非法）。当前生效的仍是原来那套。 */
    data class Unchanged(val reason: String) : InstallOutcome
}

/**
 * 基于真实文件系统的皮肤包存储（票 #57 ②，ADR-0003）。
 *
 * @param baseDir 应传入 `context.filesDir`（**不是** `cacheDir`）。版本目录在其下的 `skins/`。
 * @param log 日志输出，测试里可注入收集器断言「失败被记下」。
 */
class SkinStore(
    private val baseDir: File,
    private val log: (String) -> Unit = { },
) {

    private val skinsRoot: File get() = File(baseDir, SkinStoreLayout.SKINS_DIR)
    private val pointerFile: File get() = File(baseDir, SkinStoreLayout.POINTER_FILE)

    private fun packDir(packId: String): File = File(skinsRoot, packId)
    private fun versionDir(ref: PackRef): File = File(packDir(ref.packId), ref.version)

    /** 枚举磁盘上真实存在的版本目录（两层级），排除安装过程的 `.tmp` / `.old` 半成品。 */
    private fun installedVersions(): Set<PackRef> {
        val result = LinkedHashSet<PackRef>()
        val packDirs = skinsRoot.listFiles() ?: return result
        for (p in packDirs) {
            if (!p.isDirectory || !SkinStoreLayout.isSafeSegment(p.name)) continue
            val versionDirs = p.listFiles() ?: continue
            for (v in versionDirs) {
                if (!v.isDirectory) continue
                if (v.name.endsWith(".tmp") || v.name.endsWith(".old")) continue
                if (!SkinStoreLayout.isSafeSegment(v.name)) continue
                result += PackRef(p.name, v.name)
            }
        }
        return result
    }

    private fun readPointerContent(): String? = runCatching {
        if (pointerFile.isFile) pointerFile.readText() else null
    }.getOrElse {
        log("皮肤指针读取失败：${it.message}")
        null
    }

    private fun currentRef(): PackRef? =
        SkinStoreLayout.resolvePointer(readPointerContent(), installedVersions())

    /**
     * 读当前生效的包目录。
     *
     * **这是启动路径上唯一会碰磁盘的地方**（ADR-0006：只读本地、不碰网络）。
     * 指针内容非法 / 指向的目录被删 → `null`，回落内置默认（既有行为，不退化）。
     */
    fun currentPackDir(): File? {
        val content = readPointerContent()
        val ref = SkinStoreLayout.resolvePointer(content, installedVersions())
        if (ref == null) {
            if (content != null) {
                log("皮肤指针无效（'${content.trim()}'），回落内置默认")
            }
            return null
        }
        return versionDir(ref)
    }

    /**
     * 原子写指针：先写 `current-skin.tmp`，再 rename 覆盖正式文件（保留既有实现）。
     * 崩溃时读者只会看到旧或新的完整 JSON，不会看到半截内容。
     */
    private fun writePointerAtomically(ref: PackRef) {
        val encoded = SkinStoreLayout.encodePointer(ref)
        val tmp = File(baseDir, "${SkinStoreLayout.POINTER_FILE}.tmp")
        tmp.writeText(encoded)
        if (!tmp.renameTo(pointerFile)) {
            log("指针 rename 失败，改用直接写入")
            pointerFile.writeText(encoded)
            tmp.delete()
        }
    }

    /**
     * 把一套**已通过校验**的包内容装进 `skins/{packId}/{version}/` 并切换指针。
     *
     * 调用方负责解包与校验，本方法只做搬运与切换。原子性保证：
     * 1. 复制到 `{version}.tmp`；
     * 2. 完整复制成功后 rename 就位（先把同版本的旧目录挪到 `.old`）；
     * 3. 最后才原子写指针。
     * 任何一步失败都不动当前指针 → 线上那套仍然可用。
     *
     * @param packId 来自清单；会做路径安全校验，非法则 [InstallOutcome.Unchanged]。
     * @param version 版本目录名；同样校验。
     */
    fun install(unpackedDir: File, packId: String, version: String): InstallOutcome {
        if (!unpackedDir.isDirectory) {
            return InstallOutcome.Unchanged("源目录不存在：${unpackedDir.path}")
        }
        if (!SkinStoreLayout.isSafeSegment(packId) || !SkinStoreLayout.isSafeSegment(version)) {
            return InstallOutcome.Unchanged("packId / version 非法：'$packId' / '$version'")
        }

        val newRef = PackRef(packId, version)
        val previousCurrent = currentRef()
        val target = versionDir(newRef)
        val parent = target.parentFile
            ?: return InstallOutcome.Unchanged("无法解析版本根目录：$target")
        val staging = File(parent, "$version.tmp")

        try {
            // 1) 确保版本根目录存在，并清掉可能残留的半成品。
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
                return InstallOutcome.Unchanged("无法创建版本根目录：$parent")
            }
            staging.deleteRecursively()
            if (!staging.mkdirs() && !staging.isDirectory) {
                return InstallOutcome.Unchanged("无法创建暂存目录")
            }

            // 2) 复制到暂存目录。
            unpackedDir.copyRecursively(staging, overwrite = true)

            // 3) 就位。先把同版本旧目录挪开，再 rename 新的进来；失败则挪回，保持现状。
            val displaced = File(parent, "$version.old")
            if (target.exists()) {
                displaced.deleteRecursively()
                if (!target.renameTo(displaced)) {
                    log("皮肤包旧目录挪开失败：$target -> $displaced")
                    return InstallOutcome.Unchanged("旧版本目录无法挪开")
                }
            }
            if (!staging.renameTo(target)) {
                displaced.renameTo(target)
                log("皮肤包 rename 失败：$staging -> $target（已回滚旧目录）")
                return InstallOutcome.Unchanged("rename 失败")
            }
            displaced.deleteRecursively()

            // 4) 指针最后写，且自身原子。
            writePointerAtomically(newRef)

            // 5) 最多两套：淘汰既非新版本、也非上一版本的目录。
            val evicted = SkinStoreLayout.selectVersionsToEvict(
                newRef = newRef,
                previousCurrent = previousCurrent,
                existingVersions = installedVersions(),
            )
            evicted.forEach { ref ->
                versionDir(ref).deleteRecursively()
                log("皮肤版本已淘汰：${SkinStoreLayout.versionRelativePath(ref)}")
            }

            val prevLabel = previousCurrent?.let { SkinStoreLayout.versionRelativePath(it) } ?: "无"
            log("皮肤包已安装：${SkinStoreLayout.versionRelativePath(newRef)}（上一套：$prevLabel）")
            return InstallOutcome.Installed(newRef, evicted)
        } catch (e: IOException) {
            log("皮肤包安装失败（IO）：${e.message}")
            runCatching { staging.deleteRecursively() }
            return InstallOutcome.Unchanged("IO 失败：${e.message}")
        }
    }

    /**
     * 回滚：把指针改回磁盘上「不是当前」的那一套（user story 17 的明确 API）。
     *
     * 调用方不需要自己拼路径；回滚不删数据、不重新下载，两套目录都还在，
     * 因此可再次回滚切回去。指针损坏 / 只有一套 → 返回 `false`，不抛异常。
     */
    fun rollback(): Boolean = try {
        val current = currentRef() ?: return false
        val target = SkinStoreLayout.rollbackTarget(current, installedVersions()) ?: return false
        writePointerAtomically(target)
        log("皮肤包已回滚：${SkinStoreLayout.versionRelativePath(current)} -> ${SkinStoreLayout.versionRelativePath(target)}")
        true
    } catch (e: Exception) {
        log("皮肤包回滚失败：${e.message}")
        false
    }
}
