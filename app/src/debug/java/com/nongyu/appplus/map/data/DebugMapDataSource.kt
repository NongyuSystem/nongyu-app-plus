package com.nongyu.appplus.map.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File

/**
 * **debug 专用**的 mock 地图数据：画一张示意图底图 + 10-B201 室标注。
 *
 * ## 为什么放 src/debug 而不是 main
 *
 * 后端教室底图与标定都还没有数据，真机上看不到任何内容，无法验证
 * 「上中下三段布局 + 底图 + 标注 + 跳转」这条链路。
 * 但把假地址写进 main 会让发布包显示虚构的楼层位置——那是数据污染。
 * 放 debug 源集后：debug 能看效果，release 里这份代码根本不存在。
 *
 * ## 底图是代码画的，不引入二进制资源
 *
 * 用 Canvas 画网格 + 几栋楼 + 楼名，尺寸固定 [WIDTH]×[HEIGHT]，
 * 标注坐标可以精确对上；也不用往仓库塞几百 KB 的图片。
 *
 * ## 坐标系
 *
 * 标注坐标 = 底图像素坐标。10 号楼画在 (620,380)-(860,560)，
 * "10-B201室" 的标注点取该矩形中心。
 */
class DebugMapDataSource(private val context: Context) : MapDataSource {

    override suspend fun basemap(campus: String): MapDataSource.BasemapData? {
        if (campus != CAMPUS) return null
        val file = ensureImage()
        return MapDataSource.BasemapData(
            width = WIDTH,
            height = HEIGHT,
            imagePath = file.absolutePath,
            calibrated = false,
        )
    }

    override suspend fun annotationsJson(campus: String): String {
        if (campus != CAMPUS) return ""
        // 10 号楼矩形中心，与下面画图坐标一致
        val b201x = 740f
        val b201y = 470f
        val blue = 0xFF2196F3.toInt()
        val red = 0xFFF44336.toInt()
        val black = 0xFF000000.toInt()

        return """
            {
              "version": 1,
              "campus": "$CAMPUS",
              "annotations": [
                {"kind":"LINE","points":[
                  {"x":300,"y":900},{"x":300,"y":620},{"x":620,"y":470},{"x":$b201x,"y":$b201y}
                ],"colorArgb":$blue},
                {"kind":"POINT","points":[{"x":$b201x,"y":$b201y}],"colorArgb":$red},
                {"kind":"TEXT","points":[{"x":$b201x,"y":430}],"text":"10-B201室","colorArgb":$red},
                {"kind":"TEXT","points":[{"x":640,"y":365}],"text":"10号楼","colorArgb":$black},
                {"kind":"TEXT","points":[{"x":250,"y":910}],"text":"校门","colorArgb":$black}
              ]
            }
        """.trimIndent()
    }

    /** 首次调用生成底图 PNG，之后复用（每次重画没必要）。 */
    private fun ensureImage(): File {
        val dir = File(context.filesDir, "debug_map").apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        if (file.exists() && file.length() > 0) return file

        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(241, 239, 233)) // 高德陆地底色

        val grid = Paint().apply {
            color = Color.rgb(222, 222, 209); strokeWidth = 2f; style = Paint.Style.STROKE
        }
        for (x in 0..WIDTH step 100) c.drawLine(x.toFloat(), 0f, x.toFloat(), HEIGHT.toFloat(), grid)
        for (y in 0..HEIGHT step 100) c.drawLine(0f, y.toFloat(), WIDTH.toFloat(), y.toFloat(), grid)

        val building = Paint().apply { color = Color.rgb(208, 214, 222) }
        val edge = Paint().apply {
            color = Color.rgb(150, 158, 168); strokeWidth = 3f; style = Paint.Style.STROKE
        }
        val label = Paint().apply {
            color = Color.rgb(60, 60, 60); textSize = 26f; isAntiAlias = true
        }
        val labelBig = Paint().apply {
            color = Color.rgb(30, 30, 30); textSize = 34f; isAntiAlias = true
            isFakeBoldText = true
        }

        // 10 号楼（与标注坐标对应）
        val b10 = RectF(620f, 380f, 860f, 560f)
        c.drawRect(b10, building)
        c.drawRect(b10, edge)
        c.drawText("10 号楼", 640f, 372f, labelBig)

        listOf(
            RectF(200f, 620f, 400f, 780f) to "1 号楼",
            RectF(460f, 620f, 600f, 780f) to "2 号楼",
            RectF(980f, 300f, 1180f, 480f) to "图书馆",
            RectF(980f, 620f, 1200f, 800f) to "食堂",
            RectF(1200f, 900f, 1450f, 1050f) to "体育馆",
        ).forEach { (r, name) ->
            c.drawRect(r, building)
            c.drawRect(r, edge)
            c.drawText(name, r.left + 12f, r.top - 10f, label)
        }

        c.drawText("校门", 250f, 912f, labelBig)

        file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bmp.recycle()
        return file
    }

    private companion object {
        const val CAMPUS = "yaan"
        const val WIDTH = 1600
        const val HEIGHT = 1200
        const val FILE_NAME = "debug_basemap.png"
    }
}
