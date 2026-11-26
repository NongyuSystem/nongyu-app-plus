package com.nongyu.appplus.profile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.nongyu.appplus.design.showTextToast
import com.nongyu.appplus.profile.logic.ProfileFields
import com.nongyu.appplus.profile.model.ProfileFieldItem

/**
 * 画像字段的单字段复制（票 #113）。
 *
 * 只复制用户点中的那一项（不是整张名片），文案由纯函数 [ProfileFields.copyToastOf]
 * 给出「已复制学号」级别；敏感字段在纯函数里已被判为不可复制，这里二次守住。
 * 与 `login/ui/WechatQrActions` 不同，这里不写死「微信登录链接」这种标签。
 */
internal fun copyProfileField(context: Context, field: ProfileFieldItem, uiScale: Float = 1f) {
    val toast = ProfileFields.copyToastOf(field) ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(field.label, field.value))
    showTextToast(context, toast, uiScale = uiScale)
}
