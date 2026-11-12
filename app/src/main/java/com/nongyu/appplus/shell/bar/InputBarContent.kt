package com.nongyu.appplus.shell.bar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.design.NongyuTheme
import kotlinx.coroutines.delay

/**
 * 底部条的**输入态内容**（`BarMode.Input` 分支）。
 *
 * 从 `MainShell` 原样搬出，行为一行未改。搬出的理由只有一条：
 * 这里面的**自动聚焦时序**是全工程最脆的一段（一次 delay 不够、必须
 * 先等一帧让 AnimatedContent 挂上输入框、再等切换动画走完），
 * 单独成文件后改动它时不容易误伤上下文的别的逻辑。
 *
 * 关键约束（改之前必读）：
 * - **占位文字不能带 clickable**：clickable 会把整段手势消费掉，
 *   下层 BasicTextField 永远收不到触摸，表现就是「点了打不了字」。
 * - **输入框自己必须有可点的命中区**（`heightIn` + clickable + focusRequester）：
 *   AnimatedContent 切换时旧内容可能仍在树上抢点击。
 * - 只做 scaleX 展开动画由外层容器负责，这里只管内容。
 *
 * @param inputText 输入框当前文本（状态由 Shell 持有，`rememberSaveable`）
 * @param onInputChange 文本变化回调
 * @param uiScale 屏宽自适应系数
 * @param s dp 缩放函数（由 Shell 注入，保证与本文件外一致）
 */
@Composable
fun InputBarContent(
    inputText: String,
    onInputChange: (String) -> Unit,
    uiScale: Float,
    s: (Dp) -> Dp,
) {
    val colors = NongyuTheme.colors

    // 自动聚焦：等切换动画走完再请求焦点。
    // 直接 rememberFocusRequester + requestFocus() 在重组时调用会失败
    // （此时输入框还没 attach 到窗口），必须放到 LaunchedEffect 里，
    // 并延迟一点等 AnimatedContent 把内容挂上去，否则键盘不弹。
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 先等一帧让 AnimatedContent 真正把输入框挂上，
    // 再等切换动画走完，最后才请求焦点并弹键盘。
    // 只 delay 一次会在内容 attach 前就 requestFocus（静默失败，键盘不弹）。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(340)
        runCatching {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 内边距左右 20dp + 上下 8dp：输入框不再贴住容器边缘，
            // 白色圆角与文字不会被容器的 clip/featheredEdge 裁掉一截。
            .padding(horizontal = s(20.dp), vertical = s(8.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 占位提示必须画在 EditText**之上或同层但不可交互**，
        // 否则空输入时它会被 BasicTextField 自己的绘制区域盖住（实测看不到）。
        // 这里用 Box 叠放：输入框在下，占位在上且不接收触摸。
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    // 输入框自己必须有可点的命中区：AnimatedContent 切换时
                    // 旧内容可能仍在树上抢点击，给它在垂直方向留出整块可点区域，
                    // 避免点边缘时落到外层容器、表现为「点了没反应」。
                    .heightIn(min = s(24.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { focusRequester.requestFocus() }
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = colors.surface.colorText,
                    fontSize = (12f * uiScale).sp,
                ),
                cursorBrush = SolidColor(colors.brand.colorBrand),
                singleLine = true,
            )
            if (inputText.isEmpty()) {
                // 占位文字**不能带 clickable**：clickable 会把手势整段消费掉，
                // 下层 BasicTextField 永远收不到触摸，表现就是「点了打不了字」。
                // 纯 Text 不参与手势，事件会自然穿透到下面的输入框。
                Text(
                    text = "输入想问农小屿的问题…",
                    color = colors.surface.colorTextSecondary.copy(alpha = 0.55f),
                    fontSize = (12f * uiScale).sp,
                )
            }
        }
    }
}
