package com.nongyu.appplus.login.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.nongyu.appplus.login.model.QrEncoder

/**
 * 微信扫码的两个按钮动作（票 #96）。
 *
 * ## 两个按钮各自的语义
 * - **下载二维码并跳转微信**：把二维码存进系统相册 → 拉起微信。
 *   用户在微信里「扫一扫 → 从相册选取」即可扫到刚存的码。
 * - **复制链接并跳转微信**：把微信授权 URL 复制到剪贴板 → 拉起微信。
 *   用户在微信任意聊天窗口粘贴发送后点开即可（官方支持的路径）。
 *
 * ## 为什么不能"直接把 URL 交给微信打开"
 * 真机实测（2026-11-13，微信已登录）：
 * - `https://open.weixin.qq.com/...` 的 VIEW intent 只有 Chrome 认领，微信不注册 https scheme；
 * - `weixin://dl/business/?url=<任意参数>` 只会停在 `WXCustomSchemeEntryActivity`，
 *   微信没有「打开任意网页」的公开契约（只认小程序 ticket）。
 * 所以这里只能拉起微信本身，把"打开那个 URL"交给用户在微信内完成。
 */
object WechatQrActions {

    /** 把二维码存进相册。成功返回 true。 */
    fun saveQrToGallery(context: Context, url: String): Boolean {
        val matrix = QrEncoder.encode(url) ?: return false
        val scale = 12
        val quiet = 4
        val size = matrix.size
        val dim = (size + quiet * 2) * scale
        val bitmap = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(dim * dim) { 0xFFFFFFFF.toInt() }
        for (y in 0 until size) for (x in 0 until size) {
            if (!matrix[y][x]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                val px = (x + quiet) * scale + dx
                val py = (y + quiet) * scale + dy
                pixels[py * dim + px] = 0xFF000000.toInt()
            }
        }
        bitmap.setPixels(pixels, 0, dim, 0, 0, dim, dim)

        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "nongyu_wechat_qr.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 复制链接到剪贴板。 */
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("微信登录链接", text))
    }

    /**
     * 拉起微信，让用户用「扫一扫」扫屏幕上的二维码。
     *
     * ## 跳转顺序（实测确定）
     * 1. `getLaunchIntentForPackage(com.tencent.mm)` → 微信主界面（确定性最高）
     * 2. 兜底 `weixin://dl/scan`
     *
     * 注意 `weixin://dl/scan` **不能**当作首选：真机实测它会被微信解析到
     * `com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI`（内置浏览器），
     * 并不是扫一扫界面。用它当首选会让用户落在一个莫名其妙的网页上。
     *
     * ## 为什么以前"点了没反应"（两个坑，都已修）
     * 1. **Android 11+ 包可见性**：targetSdk 36 下不声明 `<queries>` 时，
     *    `getLaunchIntentForPackage` **直接返回 null 且不抛异常**，
     *    旧代码 `if (launch != null)` 于是静默跳过。已在 AndroidManifest 补
     *    `<queries><package com.tencent.mm/><intent VIEW weixin/></queries>`。
     * 2. **异常被 `runCatching` 吞掉**：即使 startActivity 失败也无任何反馈。
     *    现在返回 [Boolean] 让调用方给出可见提示。
     *
     * @return true 表示已经发出跳转（微信会被切到前台）
     */
    fun openWechat(context: Context): Boolean {
        // ① 首选：微信主界面
        val launch = try {
            context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
        } catch (e: Exception) {
            null
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startQuietly(context, launch)) return true
        }

        // ② 兜底：weixin scheme（落点可能是内置浏览器，仅保证能切到微信）
        val schemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startQuietly(context, schemeIntent)
    }

    /** 尝试启动一个 Intent；成功返回 true，任何失败返回 false（不抛出）。 */
    private fun startQuietly(context: Context, intent: Intent): Boolean = try {
        // 先确认有 Activity 能接，避免直接抛 ActivityNotFoundException
        val resolved = context.packageManager.resolveActivity(
            intent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
        if (resolved == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (e: Exception) {
        false
    }

    /** 微信包名。 */
    const val WECHAT_PACKAGE = "com.tencent.mm"

    /** 便于测试/判断：微信是否已安装。 */
    fun isWechatInstalled(context: Context): Boolean = try {
        context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE) != null
    } catch (e: Exception) {
        false
    }

    /** 供调用方拿二维码 Uri（若将来需要分享 Intent）。 */
    @Suppress("unused")
    fun qrUriFor(context: Context, url: String): Uri? = try {
        Uri.parse(url)
    } catch (e: Exception) {
        null
    }
}
