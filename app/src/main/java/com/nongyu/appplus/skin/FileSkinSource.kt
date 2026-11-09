package com.nongyu.appplus.skin

import android.content.Context
import android.content.res.Resources
import android.util.Log
import java.io.File

/**
 * **启动路径上真正使用的皮肤数据源**（票 #40 / 规格 #33，ADR-0006）。
 *
 * 它把已就绪的三块接起来：
 * - [SkinStore]：读当前指针 → 本地已校验的包目录
 * - [SkinPackLoader] / [SkinManifestParser]：把包目录解成 [LoadedResources]
 * - [ColorPayloadParser]：色值段（本期无网络层，色值段恒为「无」，走内置默认）
 *
 * ## 硬约束（ADR-0006，逐条对应）
 *
 * 1. **只读本地、绝不碰网络**：本类整个调用链没有任何网络 API。网络检查与下载必须在首帧之后，
 *    由将来的下载器负责，且它只调 [SkinStore.install]，不参与 [resolve]。
 * 2. **不做版本比对、不查新版**：`resolve()` 不比较版本号，指针指向谁就用谁。
 * 3. **不跳色**：本类的结果在**首帧渲染前**就定下（见 `MainActivity`）；
 *    首帧之后不会再被调用替换色板，所以不存在「先默认再跳变」。
 * 4. **失败静默回落 + 记日志**：任何异常都被兜住，返回内置默认，日志写清是哪一环。
 * 5. **同步**：[resolve] 是普通函数而非挂起函数——首帧必须拿到最终色板。
 *
 * ## 为什么失败一律兜住
 *
 * 冷启动掉进异常会让 App 打不开，而换肤只是外观增强。这个优先级不对称是故意的：
 * **宁可显示内置默认，也不能因为一个坏皮肤包启动失败**。
 *
 * @param context 用于取 `filesDir`（绝不用 `cacheDir`：皮肤包是用户资产，不能被系统清理）。
 * @param appVersionCode 当前 App 的 versionCode，供包内 `minAppVersion` 判定。
 */
class FileSkinSource(
    context: Context,
    private val appVersionCode: Int,
) : SkinSource {

    /**
     * 宿主 [Resources]（票 #57 ③ / ADR-0005 Q3=B）。
     *
     * API 30+ 的 [ResourcesLoaderBinding.attach] 需要它才能挂 loader；
     * 过去不传（默认 `null`）导致该分支在生产**永不执行**，此处补上。
     * 只在 [MainActivity.onCreate]（UI 线程）被调用，因此 [SkinPackLoader.load]
     * 的 `onUiThread` 保持 `true` 正确。
     */
    private val resources: Resources = context.resources

    /**
     * 皮肤包本地存储（票 #57 ②：路径已对齐 ADR-0003 的 `filesDir/skins/{packId}/{version}/`）。
     * `skins/` 子目录由 [SkinStore] 内部管理，这里直接传 `filesDir`。
     */
    private val store = SkinStore(
        baseDir = context.filesDir,
        log = { Log.i(TAG, it) },
    )

    /**
     * 资源段的最近一次解析结论（含失败原因），供日志与诊断用。
     *
     * 单独存一份是因为：`SkinResolution.resources` 为 `null` 时，它的
     * `describe()` 只会写「无资源段」，把「校验失败 / schema 过高 / 版本过低」
     * 这些真正有用的成因吞掉。ADR-0006 明确要求日志能区分这四种。
     */
    var lastResourceMeta: PayloadMeta? = null
        private set

    override val builtIn: BuiltInSkin get() = BuiltInSkinSource.builtIn

    /**
     * 解析当前生效的皮肤。**这是启动路径上的唯一入口。**
     */
    override fun resolve(): SkinResolution {
        val (resources, resourceMeta) = resolveResources()
        lastResourceMeta = resourceMeta
        val colors = resolveColors()

        val resolution = SkinResolution(
            colors = colors,
            resources = resources,
            builtIn = builtIn,
        )
        // 用带真实成因的 meta 打日志，而不是让 describe() 猜。
        Log.i(TAG, "皮肤解析完成：${resolution.describe()} | ${resourceMeta.describe("resources")}")
        return resolution
    }

    /**
     * 资源段：读指针 → 载包。
     *
     * 返回值是「载荷 + 结论元信息」两个：失败时载荷为 `null`，但 [PayloadMeta]
     * 一定带上真实原因，上层据此写日志（ADR-0006）。
     */
    private fun resolveResources(): Pair<ResourcePayload?, PayloadMeta> {
        val packDir = try {
            store.currentPackDir()
        } catch (e: Exception) {
            Log.w(TAG, "读皮肤指针异常，回落内置默认：${e.message}")
            return null to PayloadMeta.fallback(PayloadOrigin.Absent, "读指针异常：${e.message}")
        }

        if (packDir == null) {
            // 无包是正常状态（绝大多数用户），不必告警。
            Log.i(TAG, "无本地皮肤包，使用内置默认")
            return null to PayloadMeta.fallback(PayloadOrigin.Absent, "无本地皮肤包")
        }

        val loaded = try {
            // 清单是包根下的 manifest.json；读不出就按「无可用清单」处理（不阻断启动）。
            val manifestJson = File(packDir, MANIFEST_FILE)
                .takeIf { it.isFile }
                ?.readText()
            val manifest = manifestJson?.let { SkinManifestParser.parseJson(it) }

            if (manifest == null) {
                Log.w(TAG, "皮肤包缺少可用清单（$MANIFEST_FILE），回落内置默认")
                return null to PayloadMeta.fallback(PayloadOrigin.Invalid, "包内无可用清单")
            }

            SkinPackLoader.load(
                packDir = packDir,
                manifest = manifest,
                appVersionCode = appVersionCode,
                builtIn = builtIn.resources,
                verifier = NO_OP_VERIFIER,
                // 票 #57 ③：把宿主 Resources 传进去，API 30+ 的 ResourcesLoader
                // 分支在生产才可达。启动路径在 MainActivity.onCreate（UI 线程），
                // 因此 onUiThread = true 是正确语义（addLoaders 只能在 UI 线程）。
                resources = resources,
                onUiThread = true,
            )
        } catch (e: Exception) {
            // 包坏了也必须能启动。
            Log.w(TAG, "皮肤包加载异常，回落内置默认：${e.message}")
            return null to PayloadMeta.fallback(PayloadOrigin.Invalid, "加载异常：${e.message}")
        }

        val payload = loaded.toPayload()
        // 记录完整元信息：无论最终是否采用，都要能说清「这一环到底怎么了」。
        // ADR-0006 要求日志写明是无包 / 校验失败 / schema 过高 / minAppVersion 过高，
        // 所以这里打的是已经带上原因的 meta，而不是笼统写「无资源段」。
        Log.i(TAG, "资源段：${payload.meta.describe("resources")}")
        loaded.warnings.forEach { Log.i(TAG, "资源段警告：$it") }

        // 未采用时把真实原因一并带回，而不是丢弃后让上层误报成「无资源段」。
        return if (payload.meta.origin.isLoaded) payload to payload.meta else null to payload.meta
    }

    /**
     * 色值段：本期返回 `null`（无网络层、无目录接口）。
     *
     * **为什么不在启动路径上取色值**：ADR-0006 要求冷启动只读本地已校验数据；
     * 色值来自服务端数据库目录接口（ADR-0004），属网络。因此本期的正确行为是
     * 「启动时用内置默认色板，网络刷新放到首帧之后」——而不是在这里发请求。
     *
     * 将来接上目录接口后，正确做法是：下载器在首帧之后把色值段**落盘**，
     * 下次启动时由这里读那份本地缓存（与资源段完全同构），仍然不碰网络。
     */
    private fun resolveColors(): ColorPayload? = null

    companion object {
        private const val TAG = "NongyuSkin"

        /** 包根下的清单文件名（ADR-0003：一份清单 + 一条下载链路）。 */
        const val MANIFEST_FILE = "manifest.json"

        /**
         * 本期签名校验器：**显式放行，但把事实记进日志**。
         *
         * ADR-0005 要求「整包签名」，但未定算法与公钥（属部署期决策）。
         * 在它定下来之前有两条路：
         * - 恒返回 `false`（拒绝一切包）：安全，但等于本期资源通道完全不可用，
         *   与「本期做资源通道」的票面要求冲突；
         * - 恒返回 `true`（本选择）：让其余校验承担安全职责。
         *
         * 选了后者，但**必须明确记录**：这是当前唯一的已知安全缺口。
         * [SkinPackIntegrity] 的其余所有校验（逐文件 sha256、白名单扩展名、
         * 目录穿越、禁可执行文件）仍然全部生效 —— 签名缺位不等于无校验。
         *
         * ⇒ 待办：公钥与算法一旦确定，把这里换成真实实现即可，调用方无需改动。
         */
        private val NO_OP_VERIFIER = PackSignatureVerifier { _, _ ->
            Log.w(TAG, "签名校验未实现（ADR-0005 未定算法/公钥），本次按放行处理")
            true
        }
    }
}
