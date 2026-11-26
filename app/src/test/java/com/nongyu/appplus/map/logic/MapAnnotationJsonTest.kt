package com.nongyu.appplus.map.logic

import com.nongyu.appplus.map.model.AnnotationKind
import com.nongyu.appplus.map.model.MapAnnotation
import com.nongyu.appplus.map.model.MapDoc
import com.nongyu.appplus.map.model.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MapAnnotationJson] 的文档契约单测。
 *
 * 2026-11-26 把裸数组改为带版本号的 [MapDoc] 包装文档，这里钉住新契约：
 * 版本位、campus 归属、坏条目跳过、以及手拼 JSON 时代会出错的反斜杠转义。
 */
class MapAnnotationJsonTest {

    private val campus = "yaan"

    private fun doc(vararg annos: String) = """
        {"version":1,"campus":"$campus","annotations":[${annos.joinToString(",")}]}
    """.trimIndent()

    // ---- 新契约：包装文档 ----

    @Test
    fun `解析包装文档里的点`() {
        val raw = doc("""{"kind":"POINT","points":[{"x":120,"y":340}],"colorArgb":-15626753}""")
        val list = MapAnnotationJson.parse(raw, campus)
        assertEquals(1, list.size)
        assertEquals(AnnotationKind.POINT, list[0].kind)
        assertEquals(120f, list[0].points[0].x, 0.01f)
        assertEquals(340f, list[0].points[0].y, 0.01f)
    }

    @Test
    fun `解析折线多点`() {
        val raw = doc("""{"kind":"LINE","points":[{"x":10,"y":20},{"x":80,"y":90}]}""")
        val list = MapAnnotationJson.parse(raw, campus)
        assertEquals(1, list.size)
        assertEquals(2, list[0].points.size)
    }

    @Test
    fun `解析文字标注`() {
        val raw = doc("""{"kind":"TEXT","points":[{"x":50,"y":60}],"text":"第一教学楼"}""")
        val list = MapAnnotationJson.parse(raw, campus)
        assertEquals("第一教学楼", list[0].text)
    }

    // ---- campus 归属：文档优先，缺省才用参数 ----

    @Test
    fun `文档里的 campus 优先于参数`() {
        val raw = """{"version":1,"campus":"chengdu","annotations":[{"kind":"POINT","points":[{"x":1,"y":2}]}]}"""
        val list = MapAnnotationJson.parse(raw, campus = "yaan")
        assertEquals("文档应自报归属", "chengdu", list[0].campus)
    }

    @Test
    fun `文档无 campus 时用参数补齐`() {
        val raw = """{"version":1,"annotations":[{"kind":"POINT","points":[{"x":1,"y":2}]}]}"""
        val list = MapAnnotationJson.parse(raw, campus = "yaan")
        assertEquals("yaan", list[0].campus)
    }

    // ---- 版本位 ----

    @Test
    fun `未知更高版本返回空而不硬解`() {
        val raw = """{"version":99,"campus":"yaan","annotations":[{"kind":"POINT","points":[{"x":1,"y":2}]}]}"""
        assertTrue("不认识的版本应保守返回空", MapAnnotationJson.parse(raw, campus).isEmpty())
    }

    @Test
    fun `当前版本常量与编码一致`() {
        val encoded = MapAnnotationJson.encode(campus, emptyList())
        assertTrue(encoded.contains("\"version\":${MapDoc.CURRENT_VERSION}"))
    }

    // ---- 容错：坏条目跳过，不影响好条目 ----

    @Test
    fun `非法 kind 被跳过`() {
        val raw = doc(
            """{"kind":"BOGUS","points":[{"x":1,"y":2}]}""",
            """{"kind":"POINT","points":[{"x":3,"y":4}]}""",
        )
        val list = MapAnnotationJson.parse(raw, campus)
        assertEquals("只保留合法那条", 1, list.size)
        assertEquals(3f, list[0].points[0].x, 0.01f)
    }

    @Test
    fun `点数为零的条目被跳过`() {
        val raw = doc("""{"kind":"POINT","points":[]}""")
        assertTrue(MapAnnotationJson.parse(raw, campus).isEmpty())
    }

    @Test
    fun `折线只有一个点被跳过`() {
        val raw = doc("""{"kind":"LINE","points":[{"x":1,"y":2}]}""")
        assertTrue(MapAnnotationJson.parse(raw, campus).isEmpty())
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(MapAnnotationJson.parse(null, campus).isEmpty())
        assertTrue(MapAnnotationJson.parse("", campus).isEmpty())
        assertTrue(MapAnnotationJson.parse("   ", campus).isEmpty())
    }

    @Test
    fun `非法 JSON 返回空列表不抛`() {
        assertTrue(MapAnnotationJson.parse("{not json", campus).isEmpty())
    }

    // ---- 编码：kotlinx 处理转义，不再手拼 ----

    @Test
    fun `编码后能解析回来且字段不丢`() {
        val original = listOf(
            MapAnnotation(
                id = "a1",
                kind = AnnotationKind.TEXT,
                points = listOf(MapPoint(10f, 20f)),
                text = "一教\\A 楼",
                colorArgb = -15626753,
                campus = campus,
            ),
        )
        val round = MapAnnotationJson.parse(MapAnnotationJson.encode(campus, original), campus)
        assertEquals(1, round.size)
        assertEquals("反斜杠与中文都要保住", "一教\\A 楼", round[0].text)
        assertEquals(-15626753, round[0].colorArgb)
        assertEquals(10f, round[0].points[0].x, 0.01f)
    }

    @Test
    fun `文字含引号与换行也能往返`() {
        val text = "第一行\n\"第二行\""
        val original = listOf(
            MapAnnotation(
                id = "a2",
                kind = AnnotationKind.TEXT,
                points = listOf(MapPoint(0f, 0f)),
                text = text,
                campus = campus,
            ),
        )
        val round = MapAnnotationJson.parse(MapAnnotationJson.encode(campus, original), campus)
        assertEquals("手拼 JSON 时代这里会产出非法字符串", text, round[0].text)
    }

    @Test
    fun `编码空列表产出空数组`() {
        val encoded = MapAnnotationJson.encode(campus, emptyList())
        assertTrue(MapAnnotationJson.parse(encoded, campus).isEmpty())
    }
}
