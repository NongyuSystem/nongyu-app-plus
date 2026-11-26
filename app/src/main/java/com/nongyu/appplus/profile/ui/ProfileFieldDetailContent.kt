package com.nongyu.appplus.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import com.nongyu.appplus.profile.logic.ProfileFields
import com.nongyu.appplus.profile.model.ProfileFieldItem

/**
 * 画像字段详情卡的内容（`BarMode.Detail` 分支里二选一填入，不新增底栏形态值）。
 *
 * 复用课程详情卡的两条硬约束（见 `shell/bar/DetailCardContent.kt`）：
 * 1. 卡片内部消费点击，点外部才由 Shell 的卡片外退出层收拢；
 * 2. 顶部横杠用 `pointerInput` 单独接管拖拽，**不在整卡根部挂可点击层**，
 *    否则会吃掉横杠手势（仓里踩过第二次上划收不到回调的坑）。
 *
 * 拖拽的退出判定由纯函数 `ProfileFields.dismissByDrag` 在 Shell 里做；本组件只把
 * 累计位移与松手事件上报，和课程详情共用同一套「拖拽跟手 / 松手吸附」状态机。
 */
@Composable
fun ProfileFieldDetailContent(
    field: ProfileFieldItem,
    uiScale: Float,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = NongyuTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape((26f * uiScale).dp))
            .background(colors.surface.colorCard)
            // 卡片内部消费点击：与课程详情同语义，点卡内不退出。
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { /* 只消费，不动作 */ }
            .padding(horizontal = (18f * uiScale).dp, vertical = (4f * uiScale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((12f * uiScale).dp),
    ) {
        // 可拖拽横杠：命中区 56×18，视觉 40×4，与课程详情完全一致。
        Box(
            modifier = Modifier
                .width((56f * uiScale).dp)
                .height((18f * uiScale).dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dy -> onDragDelta(-dy / density.density) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width((40f * uiScale).dp)
                    .height((4f * uiScale).dp)
                    .clip(RoundedCornerShape((2f * uiScale).dp))
                    .background(colors.surface.colorOutline),
            )
        }
        Text(text = ProfileFields.detailTitleOf(field), color = colors.surface.colorText, fontSize = (18f * uiScale).sp)
        Text(text = field.value, color = colors.surface.colorText, fontSize = (20f * uiScale).sp)
        Text(
            text = "上滑或点外部退出；点下方复制当前字段",
            color = colors.surface.colorTextSecondary,
            fontSize = (12f * uiScale).sp,
        )
        Text(
            text = "复制此项",
            color = colors.brand.colorBrand,
            fontSize = (15f * uiScale).sp,
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { copyProfileField(context, field, uiScale) },
                )
                .padding(vertical = (8f * uiScale).dp),
        )
    }
}
