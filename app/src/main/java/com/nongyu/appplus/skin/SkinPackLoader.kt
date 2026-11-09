package com.nongyu.appplus.skin

import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

/**
 * 皮肤包的**两条加载路径**（票 #38 / ADR-0005 Q3=B）。
 *
 * ## 设计要点：把"取"和"判"分开
 *
 * ADR-0005 要求「两条路径的产物类型与默认值必须完全一致」。最容易失败的做法是
 * 「两条路径各写一遍判定」—— 迟早分叉。所以本文件里的两条路径**只负责取字节、
 * 探一次字体可读性**，其余全部判定交给 [SkinPackPlanner]（纯逻辑、可单测）。
 * 于是「同一输入 → 同一个 [LoadedResources]」是**结构上**成立的。
 *
 * | | API 30+ | API 26–29 |
 * | --- | --- | --- |
 * | 取字节 | `ResourcesProvider.loadFromDirectory` → `ResourcesLoader` → `Resources.addLoaders`，再从挂载后的 `Assets` 读 | `File` 直接读（`BitmapFactory.decodeFile` / `Typeface.createFromFile` 同源） |
 * | 字体可读性 | `Resources.getFont` 探一次 | `Typeface.createFromFile` 探一次 |
 * | 产物 | [LoadedResources] | [LoadedResources] |
 *
 * ## 线程约束（ADR-0005 明确要求）
 *
 * `Resources.addLoaders` 会与 UI 线程争锁，官方要求**只在 UI 线程**调用。
 * [ResourcesLoaderBinding.attach] 因此在调用方保证的线程上执行，本文件不自己开线程。
 */

private const val TAG = "SkinPack"

/** 从一个"包根"里取文件字节的能力。两条路径各自实现，判定逻辑不在这里。 */
fun interface PackByteSource {
    /** @return 文件字节；不存在/不可读返回 `null`（不抛异常）。 */
    fun read(relativePath: String): ByteArray?
}

/**
 * API 26–29 路径：把包目录当普通目录读。
 *
 * 与 `BitmapFactory.decodeFile` / `Typeface.createFromFile` 是同一类机制
 * （都是"给一个文件路径，平台自己解码"），只是这里先取成字节好做 sha256。
 */
class FilePackByteSource(private val packDir: File) : PackByteSource {
    override fun read(relativePath: String): ByteArray? = try {
        val file = File(packDir, relativePath)
        // 防目录穿越：规范化后必须仍在包内。清单里已挡过一次，这里再挡一次（解包字节也走这条路）。
        val root = packDir.canonicalFile
        val target = file.canonicalFile
        if (target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            if (target.isFile) target.readBytes() else null
        } else {
            Log.w(TAG, "拒绝读取包外路径: $relativePath")
            null
        }
    } catch (t: Throwable) {
        Log.w(TAG, "读取包内文件失败: $relativePath", t)
        null
    }
}

/**
 * API 30+ 路径：`ResourcesProvider` → `ResourcesLoader` → `Resources.addLoaders`。
 *
 * 挂载成功后，包内文件通过**宿主 `Resources` 的 `assets`** 读取 ——
 * 这就是官方「supply additional resources and assets」在本场景（纯文件型载荷）的用法。
 *
 * 生命周期：loader 一旦 `addLoaders` 就挂在 `Resources` 上，皮肤包被替换时需要先
 * [ResourcesLoaderBinding.detach] 再重挂，否则会同时看到新旧两份资源。
 */
@RequiresApi(Build.VERSION_CODES.R)
object ResourcesLoaderBinding {

    /**
     * 把 [packDir] 挂到 [resources] 上。
     *
     * **必须在 UI 线程调用**（`addLoaders` 的官方要求）。
     *
     * @return 挂载结果；失败返回 `null`（静默回落内置默认，不抛异常）。
     */
    fun attach(resources: Resources, packDir: File): Attached? = try {
        val provider = ResourcesProvider.loadFromDirectory(
            packDir.absolutePath,
            null, // 不需要 AssetsProvider：包内文件就是普通文件
        )
        val loader = ResourcesLoader().apply { addProvider(provider) }
        resources.addLoaders(loader)
        Attached(loader)
    } catch (t: Throwable) {
        Log.w(TAG, "ResourcesLoader 挂载失败，回落文件解码", t)
        null
    }

    /** 卸载由 [attach] 挂上的 loader。**必须在 UI 线程调用**。 */
    fun detach(resources: Resources, attached: Attached) {
        try {
            resources.removeLoaders(attached.loader)
        } catch (t: Throwable) {
            Log.w(TAG, "ResourcesLoader 卸载失败", t)
        }
    }

    /** 已挂载的 loader 句柄。 */
    class Attached internal constructor(internal val loader: ResourcesLoader)
}

/** API 30+ 路径的取字节实现：走**挂载后**的 `Resources.assets`。 */
@RequiresApi(Build.VERSION_CODES.R)
class ResourcesLoaderPackByteSource(private val resources: Resources) : PackByteSource {
    override fun read(relativePath: String): ByteArray? = try {
        // assets 里读的是"包根相对路径"，与清单里的 path 同一套写法。
        resources.assets.open(relativePath).use { it.readBytes() }
    } catch (t: Throwable) {
        Log.w(TAG, "从 ResourcesLoader 读取失败: $relativePath", t)
        null
    }
}

/**
 * 字体可读性探针：两条路径各自的"能不能真的读成字体"。
 *
 * 返回值只用于 [SkinPackPlanner.plan] 的 `fontReadable` 参数 ——
 * 因此两条路径产出的 [LoadedResources] 结构完全相同，差别只在这一次探测。
 */
object PackFontProbe {

    /** API 26–29：`Typeface.createFromFile`，读不出来就是 `null`。 */
    fun fromFile(fontFile: File): Boolean = try {
        fontFile.isFile && android.graphics.Typeface.createFromFile(fontFile) != null
    } catch (t: Throwable) {
        Log.w(TAG, "字体文件无法加载: ${fontFile.path}", t)
        false
    }

    /** API 30+：从挂载后的 `Resources` 里探一次（`getFont` 拿不到就是 `null`）。 */
    @RequiresApi(Build.VERSION_CODES.R)
    fun fromResources(resources: Resources, fontPath: String): Boolean = try {
        // 本工程不依赖编译期 res/font，故按"能不能把字节解成字体"来探。
        resources.assets.open(fontPath).use { stream ->
            val tmp = File.createTempFile("skinfont", ".ttf")
            try {
                tmp.outputStream().use { out -> stream.copyTo(out) }
                android.graphics.Typeface.createFromFile(tmp) != null
            } finally {
                tmp.delete()
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "从 ResourcesLoader 加载字体失败: $fontPath", t)
        false
    }
}

/**
 * 皮肤包加载器：按 SDK 选路径，产出**同一个** [LoadedResources]。
 *
 * 唯一入口 [load]，永远返回非空结果：拿不到就用 [LoadedResources.fallback]。
 */
object SkinPackLoader {

    /**
     * 「取字节 → 校验 → 装配」的公共实现（票 #57 ③）。
     *
     * 两条加载路径、以及**真实的两路径一致性契约测试**都走这里：只要给同一个
     * [PackByteSource] 语义喂同一份字节，就必然产出同一个 [LoadedResources]。
     *
     * 刻意做成纯 JVM 可测：只依赖 [PackByteSource] 接口与纯数据类，
     * **不调用 Android `Log`、不碰 `Resources`/`Typeface`**（那些留在 [load] 的生产外壳里）。
     * 于是「同一份包、同一份期望产物」可以用两个不同的 `PackByteSource` 实现
     * 在 `:app:testDebugUnitTest` 里直接断言相等，而不必起 instrumented 测试。
     *
     * @param source 取字节的实现：生产里是 [FilePackByteSource] 或 [ResourcesLoaderPackByteSource]，
     *   测试里可注入任意等价实现。
     * @param fontReadable 平台侧字体可读性探针；两条路径各自传入，这里只消费结论。
     */
    fun assemble(
        source: PackByteSource,
        manifest: SkinPackManifest,
        appVersionCode: Int,
        builtIn: ResourcePayload,
        verifier: PackSignatureVerifier,
        fontReadable: (String) -> Boolean = { false },
    ): LoadedResources {
        // 先按清单声明的路径取全字节（缺失的留空，交给校验判 IntegrityFailed）。
        val bytes = HashMap<String, ByteArray>(manifest.entries.size)
        for (entry in manifest.entries) {
            source.read(entry.path)?.let { bytes[entry.path] = it }
        }

        val verification = SkinPackIntegrity.verify(
            SkinPackVerifyInput(
                manifest = manifest,
                actualBytes = bytes,
                appVersionCode = appVersionCode,
            ),
            verifier,
        )

        // 字体可读性只有在清单真的声明了字体时才需要探。
        val fontOk = manifest.fontFileName?.takeIf { it.isNotBlank() }
            ?.let { path ->
                // 校验没过时不浪费一次解码 —— 整体回落时这个值无关紧要。
                verification.ok && fontReadable(path)
            } ?: false

        return SkinPackPlanner.plan(
            manifest = manifest,
            verification = verification,
            fontReadable = fontOk,
            builtIn = builtIn,
        )
    }

    /**
     * 加载一个已解包到 [packDir] 的皮肤包。
     *
     * @param manifest 已解析的清单；`null` = 没有包（直接回落）。
     * @param appVersionCode 当前 App versionCode。
     * @param builtIn 内置默认资源段（逐资源回落的第二级）。
     * @param verifier 整包签名校验器。
     * @param resources 宿主 `Resources`；API 30+ 用它挂 loader。为 `null` 时强制走文件路径。
     * @param onUiThread 调用方是否已保证在 UI 线程（`addLoaders` 的要求）。
     */
    fun load(
        packDir: File?,
        manifest: SkinPackManifest?,
        appVersionCode: Int,
        builtIn: ResourcePayload,
        verifier: PackSignatureVerifier,
        resources: Resources? = null,
        onUiThread: Boolean = true,
    ): LoadedResources {
        if (packDir == null || manifest == null) {
            return LoadedResources.fallback(
                PayloadMeta.fallback(PayloadOrigin.Absent, "无本地皮肤包"),
            )
        }

        // 选路径：API 30+ 且有 Resources 且确实在 UI 线程 → 走 ResourcesLoader；否则文件解码。
        // 用一个局部非空值承载，避免后面到处写 `!!`（那会让「可能为 null」的语义被掩盖）。
        val canUseLoader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && onUiThread
        val hostResources: Resources? = resources?.takeIf { canUseLoader }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resources != null && !onUiThread) {
            Log.w(TAG, "非 UI 线程调用 addLoaders 会与 UI 线程争锁，本次改走文件解码")
        }

        val attached = hostResources?.let { ResourcesLoaderBinding.attach(it, packDir) }

        val source: PackByteSource
        val fontReadable: (String) -> Boolean
        if (hostResources != null && attached != null) {
            source = ResourcesLoaderPackByteSource(hostResources)
            fontReadable = { path -> PackFontProbe.fromResources(hostResources, path) }
        } else {
            source = FilePackByteSource(packDir)
            fontReadable = { path -> PackFontProbe.fromFile(File(packDir, path)) }
        }

        return try {
            val planned = assemble(
                source = source,
                manifest = manifest,
                appVersionCode = appVersionCode,
                builtIn = builtIn,
                verifier = verifier,
                fontReadable = fontReadable,
            )
            Log.i(TAG, "皮肤包加载完成: ${planned.meta.describe("resources")}")
            planned
        } finally {
            if (hostResources != null && attached != null) {
                ResourcesLoaderBinding.detach(hostResources, attached)
            }
        }
    }
}
