package com.nongyu.appplus.webpage.ui

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.design.uiScaleFor
import com.nongyu.appplus.webpage.logic.WebPageAllowlist
import com.nongyu.appplus.webpage.logic.UrlNormalizer
import com.nongyu.appplus.webpage.model.WebPageConfig
import com.nongyu.appplus.webpage.model.WebPageCookieBridge

/**
 * 通用 WebView 容器（票 #119）：在 App 内嵌浏览一个白名单内的网页。
 *
 * ## 与 net 的解耦
 * 只依赖 [WebPageConfig] 与 [WebPageCookieBridge] 接口；Cookie 由装配点注入，
 * 本包不 import net 内部实现，`webpage/logic` 保持纯 JVM。
 *
 * ## 安全边界
 * 每次导航（含重定向）先过 [WebPageAllowlist]：不在白名单的 URL 直接拦截，
 * 不留 WebView、不外跳。JS 开（校方站点需要），DOM Storage 关（避免脏数据）。
 *
 * ## 生命周期与返回
 * 返回键优先让 WebView 回退历史，到首屏则由 [onBack] 退出本页；
 * onPause/onResume 随 Composable 的 DisposableEffect 挂接。
 */
@Composable
fun WebPageScreen(
    config: WebPageConfig,
    cookieBridge: WebPageCookieBridge?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = NongyuTheme.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val uiScale = uiScaleFor(context.resources.displayMetrics.widthPixels / density.density)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy() }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.surface.colorBackground)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                cookieBridge?.syncCookies(config.url)
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = config.javaScriptEnabled
                    settings.domStorageEnabled = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val allowed = WebPageAllowlist.isAllowed(request.url.toString(), config.allowedHosts)
                            if (!allowed) blocked = true
                            return !allowed
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            loading = false
                            canGoBack = view.canGoBack()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request.isForMainFrame) {
                                loading = false
                                failed = true
                            }
                        }
                    }
                    loadUrl(UrlNormalizer.normalize(config.url) ?: config.url)
                }
            },
        )
        when {
            blocked -> NoticeBox("已拦截离开校方站点的导航", uiScale, colors.surface.colorText)
            failed -> NoticeBox("页面加载失败，请检查网络后重试", uiScale, colors.surface.colorText)
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand.colorBrand)
            }
        }
    }
}

@Composable
private fun NoticeBox(text: String, uiScale: Float, color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.fillMaxSize().padding((24f * uiScale).dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = color, fontSize = (14f * uiScale).sp)
    }
}
