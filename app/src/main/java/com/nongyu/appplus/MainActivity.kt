package com.nongyu.appplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nongyu.appplus.skin.FileSkinSource
import com.nongyu.appplus.ui.NongyuApp
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme

/**
 * 应用唯一入口 Activity。
 *
 * 骨架阶段只负责挂载 Compose 根节点，不承载业务逻辑；
 * 后续路由、依赖注入等能力均在 [NongyuApp] 内部扩展。
 *
 * ## 皮肤在哪儿解析（ADR-0006，重要）
 *
 * 皮肤在 [setContent] **之前**同步解析完，然后把结果作为 `colors` 传进主题。
 * 这样做的原因只有一条：**不允许首帧后跳色**。
 *
 * 如果改成「先渲染默认色板、异步加载后再换」，用户会看到一次颜色跳变
 * （`staticCompositionLocalOf` 换值还会触发整块 content 重组）。而先解析再渲染，
 * 首帧就是最终色板，观感稳定。
 *
 * 代价是启动路径上多了一次**本地磁盘读**（读指针小文件 + 可能的包清单）。
 * 这个代价是 ADR-0006 明确接受的；它**绝不包含网络**：
 * 换肤数据的网络刷新必须放在首帧之后，由下载器负责写盘，下次启动才生效。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 同步解析皮肤：只读本地，不碰网络（ADR-0006）。
        // 任何异常都在 FileSkinSource 内部被兜住并回落内置默认，这里不需要 try/catch。
        val skin = FileSkinSource(
            context = applicationContext,
            appVersionCode = BuildConfig.VERSION_CODE,
        )
        val resolution = skin.resolve()

        setContent {
            NongyuAppPlusTheme(
                colors = resolution.palette(),
                source = skin,
            ) {
                NongyuApp()
            }
        }
    }
}
