package com.nongyu.appplus.skin

import android.content.Context
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

    private val store = SkinStore(
        baseDir = File(context.filesDir, "skin"),
        log = { Log.i(TAG, it) },
    )

    override val builtIn: BuiltInSkin get() = BuiltInSkinSource.builtIn

    /**
     * 解析当前生效的皮肤。**这是启动路径上的唯一入口。**
     */
    override fun resolve(): SkinResolution {
        val resources = resolveResources()
        val colors = resolveColors()

        val resolution = SkinResolution(
            colors = colors,
            resources = resources,
            builtIn = builtIn,
        )
        Log.i(TAG, "皮肤解析完成：${resolution.describe()}")
        return resolution
    }

    /** 资源段：读指针 → 载包；任何失败都回落内置默认并记下原因。 */
    private fun resolveResources(): ResourcePayload? {
        val packDir = try {
            store.currentPackDir()
        } catch (e: Exception) {
            Log.w(TAG, "读皮肤指针异常，回落内置默认：${e.message}")
            return null
        }

        if (packDir == null) {
            // 无包是正常状态（绝大多数用户），不必告警。
            Log.i(TAG, "无本地皮肤包，使用内置默认")
            return null
        }

        val loaded = try {
            // 清单是包根下的 manifest.json；读不出就按「无包」处理（不阻断启动）。
            val manifestJson = File(packDir, MANIFEST_FILE)
                .takeIf { it.isFile }
                ?.readText()
            val manifest = manifestJson?.let { SkinManifestParser.parseJson(it) }

            if (manifest == null) {
                Log.w(TAG, "皮肤包缺少可用清单（$MANIFEST_FILE），回落内置默认")
                return null
            }

            SkinPackLoader.load(
                packDir = packDir,
                manifest = manifest,
                appVersionCode = appVersionCode,
                builtIn = builtIn.resources,
                verifier = NO_OP_VERIFIER,
            )
        } catch (e: Exception) {
            // 包坏了也必须能启动。
            Log.w(TAG, "皮肤包加载异常，回落内置默认：${e.message}")
            return null
        }

        val payload = loaded.toPayload()
        Log.i(TAG, "资源段：${payload.meta.describe("resources")}")
        return if (payload.meta.origin.isLoaded) payload else null
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
         * 本期签名校验器：放行。
         *
         * ADR-0005 要求「整包签名」，但未定算法与公钥（属部署期决策）。
         * 在它定下来之前，[SkinPackIntegrity] 的**其余所有校验**（逐文件 sha256、
         * 白名单扩展名、目录穿越、禁可执行文件）仍然全部生效——签名只是最后一道。
         * 这个选择是显式的、有注释的，不是遗漏。
         */
        private val NO_OP_VERIFIER = PackSignatureVerifier { _, _ -> true }
    }
}
