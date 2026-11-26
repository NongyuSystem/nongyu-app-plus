package com.nongyu.appplus.map.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nongyu.appplus.map.data.MapLoadResult
import com.nongyu.appplus.map.data.MapRepository
import com.nongyu.appplus.map.logic.MapAnnotationJson
import com.nongyu.appplus.map.logic.MapGeoUri
import com.nongyu.appplus.map.logic.MapGeometry
import com.nongyu.appplus.map.model.MapViewport
import androidx.compose.foundation.layout.BoxWithConstraints

/**
 * 地图组件（**只读**）。
 *
 * ## 谁提供标注
 * 点 / 线 / 文字与颜色由**调用方通过一个 JSON 字段**传入（[annotationsJson]），
 * 用户在本组件里**不能编辑**标注，只能看与拖动地图。JSON 契约见 [MapAnnotationJson]。
 *
 * ## 功能
 * 1. 地图图片本地缓存优先，缺失时请求后端（`MapRepository`）
 * 2. 显示底图 + 拖动 + 渲染调用方标注
 * 3. 外部地图跳转用独立方法 [openMapNavigation]，不在组件里放按钮
 *
 * @param campus 校区标识（对应后端 `:campus`）
 * @param annotationsJson 调用方提供的标注 JSON（见 [MapAnnotationJson] 契约）
 */
@Composable
fun MapEditor(
    campus: String,
    annotationsJson: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(campus) { MapRepository(context) }

    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var viewport by remember { mutableStateOf(MapViewport()) }

    // 标注完全由调用方 JSON 决定；不持有可变状态、不提供编辑入口
    val annotations = remember(campus, annotationsJson) {
        MapAnnotationJson.parse(annotationsJson, campus)
    }

    // 加载地图（缓存优先）
    val loadResult by produceState<MapLoadResult?>(initialValue = null, campus) {
        value = repository.load(campus)
    }
    LaunchedEffect(loadResult) {
        when (val r = loadResult) {
            is MapLoadResult.Success -> {
                image = decodeMapImage(r.imageFile.absolutePath)
                hint = null
            }

            is MapLoadResult.NoImage -> hint = r.hint.ifBlank { "该校区暂无地图" }
            is MapLoadResult.Error -> hint = "地图加载失败：${r.cause.message ?: "未知错误"}"
            null -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // 用 BoxWithConstraints 拿真实可见区域，而不是写死屏幕尺寸。
        //
        // 2026-11-26 修 bug：之前 clampPan 传的是写死的 1080×1920。
        // 但地图组件经常只占屏幕一小块（例如对话框里 1080×400），于是：
        //   1) clampPan 以为「图比屏幕小」→ 竖直居中到 (1920-图高)/2，整图被推到下方；
        //   2) 一拖动又按 1920 重新夹取 → 画面突然跳位。
        // 现在约束来自实际布局，两个问题一起消失。
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewW = constraints.maxWidth
            val viewH = constraints.maxHeight

            // 首次加载后把地图居中。
            //
            // 初始 viewport 是 (0,0, scale=1)，即图像左上角贴屏幕左上角。
            // 若不处理，大图会「只有右下角露出来」，标注点自然不在中心。
            // 这里用 clampPan(0,0) 求一次居中/夹取结果——图比视口大就贴左上（可拖动看全），
            // 比视口小就居中，正是 clampPan 已有的语义。
            LaunchedEffect(image, viewW, viewH) {
                val img = image ?: return@LaunchedEffect
                if (viewW > 0 && viewH > 0) {
                    viewport = MapGeometry.clampPan(
                        viewport, 0f, 0f, img.width, img.height,
                        screenWidth = viewW,
                        screenHeight = viewH,
                    )
                }
            }

            MapCanvasView(
                image = image,
                viewport = viewport,
                annotations = annotations,
                selectedId = null,
                modifier = Modifier.fillMaxSize(),
                onPan = { dx, dy ->
                    val w = image?.width ?: 1
                    val h = image?.height ?: 1
                    viewport = MapGeometry.clampPan(
                        viewport, dx, dy, w, h,
                        screenWidth = viewW,
                        screenHeight = viewH,
                    )
                },
                onTap = { /* 只读：用户点击标注不触发任何编辑 */ },
            )

            hint?.let {
                Text(
                    it,
                    color = Color(0xFF666666),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
        }
    }
}

/**
 * 便捷入口：直接传标注文档对象。
 *
 * 调用方若已从 [com.nongyu.appplus.map.data.MapAnnotationStore.documentJson] 拿到原始 JSON，
 * 用那个重载即可免去编码往返；本重载供只有对象、没有 JSON 的场景。
 */
@Composable
fun MapEditor(
    campus: String,
    doc: com.nongyu.appplus.map.model.MapDoc,
    modifier: Modifier = Modifier,
) {
    val json = remember(doc) {
        com.nongyu.appplus.map.logic.MapAnnotationJson.encodeDoc(doc)
    }
    MapEditor(campus = campus, annotationsJson = json, modifier = modifier)
}

/**
 * 跳转外部地图软件（独立方法，供调用方在任意按钮里使用）。
 *
 * 用标准 `geo:` URI + `ACTION_VIEW`，不依赖高德 SDK / key，系统让用户选已装地图 App。
 *
 * @return 是否成功调起；未安装地图 App 时返回 false（不崩）
 */
fun openMapNavigation(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String? = null,
): Boolean {
    val uri = Uri.parse(MapGeoUri.build(latitude, longitude, label))
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
