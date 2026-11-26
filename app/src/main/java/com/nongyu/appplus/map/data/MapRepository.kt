package com.nongyu.appplus.map.data

import android.content.Context
import com.nongyu.appplus.net.NetHolder
import com.nongyu.appplus.net.model.ClassroomMapMetaDto
import java.io.File

/**
 * 地图取数仓库：本地缓存优先，缺失时走后端。
 *
 * ## 流程
 * 1. `GET /classrooms/maps/:campus/meta` 拿 objectKey（作为缓存版本）；
 * 2. 本地有该 objectKey 的文件 → 直接返回（**零网络下载**）；
 * 3. 否则 `GET /classrooms/maps/:campus?v=<objectKey>` 下载字节并写缓存。
 *
 * ## 为什么先取 meta
 * 后端说「底图是静态资源，`object_key` 含内容指纹；客户端带 `?v=<objectKey>`
 * 的 URL 自然失效」。所以 objectKey 既是版本也是缓存键，必须先拿到它。
 *
 * ## 未上传/未标定
 * `meta.uploaded=false` 时没有图可取，返回 [MapLoadResult.NoImage]；
 * `calibrated=false` 时图片仍可显示，但不能做像素↔经纬度换算（UI 层按 meta 判断）。
 */
class MapRepository(private val context: Context) {

    private val cache = MapImageCache(context)

    /**
     * 加载地图（本地优先）。
     *
     * @return [MapLoadResult] 四种结果；不抛网络异常，失败封装在 [MapLoadResult.Error]。
     */
    suspend fun load(campus: String): MapLoadResult {
        // 0) 注入点优先：debug 包可塞 mock 底图（release 恒为 Default，走下面真实逻辑）
        MapDataSourceHolder.source.basemap(campus)?.let { mock ->
            return MapLoadResult.Success(
                meta = ClassroomMapMetaDto(
                    campus = campus,
                    width = mock.width,
                    height = mock.height,
                    uploaded = true,
                    calibrated = mock.calibrated,
                    hint = null,
                ),
                imageFile = File(mock.imagePath),
                fromCache = true,
            )
        }

        val api = try {
            NetHolder.classroom(context)
        } catch (e: Throwable) {
            return MapLoadResult.Error(e)
        }

        val metaResp = try {
            api.mapMeta(campus)
        } catch (e: Throwable) {
            return MapLoadResult.Error(e)
        }
        if (!metaResp.isSuccessful) {
            return MapLoadResult.Error(
                IllegalStateException("取地图元数据失败：HTTP ${metaResp.code()}"),
            )
        }
        val meta = metaResp.body()?.data
            ?: return MapLoadResult.Error(IllegalStateException("地图元数据为空"))

        if (meta.uploaded != true) {
            return MapLoadResult.NoImage(meta.hint.orEmpty())
        }
        val objectKey = meta.objectKey
            ?: return MapLoadResult.Error(IllegalStateException("已上传的地图缺少 objectKey"))

        // 本地缓存命中 → 零下载
        cache.get(campus, objectKey)?.let { file ->
            return MapLoadResult.Success(meta, file, fromCache = true)
        }

        val imageResp = try {
            api.mapImage(campus, version = objectKey)
        } catch (e: Throwable) {
            return MapLoadResult.Error(e)
        }
        if (!imageResp.isSuccessful) {
            return MapLoadResult.Error(
                IllegalStateException("下载地图图片失败：HTTP ${imageResp.code()}"),
            )
        }
        val bytes = imageResp.body()?.bytes()
            ?: return MapLoadResult.Error(IllegalStateException("地图图片为空"))

        val file = try {
            cache.put(campus, objectKey, bytes)
        } catch (e: Throwable) {
            return MapLoadResult.Error(e)
        }
        return MapLoadResult.Success(meta, file, fromCache = false)
    }

    /** 清缓存（设置页可调用）。 */
    suspend fun clearCache(campus: String) = cache.clearCampus(campus)
}

/** 地图加载结果。 */
sealed class MapLoadResult {
    /** 成功，带元数据与本地图片文件。 */
    data class Success(
        val meta: ClassroomMapMetaDto,
        val imageFile: File,
        /** 是否命中本地缓存（供 UI 显示「来自缓存」）。 */
        val fromCache: Boolean,
    ) : MapLoadResult()

    /** 后端说文图尚未上传，无可显示内容。 */
    data class NoImage(val hint: String) : MapLoadResult()

    /** 失败（网络 / 解析 / 缓存写入）。 */
    data class Error(val cause: Throwable) : MapLoadResult()
}
