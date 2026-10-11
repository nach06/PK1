package com.nacho.kartlog.domain.importing

import org.json.JSONArray
import org.json.JSONObject

/**
 * Volcado de una lectura de OCR en JSON (cajas con su geometría y confianza). En debug se guarda cada lectura en
 * `filesDir/ocr_dumps/` y en Logcat; los volcados reales se copian a `src/test/resources/ocr/` y se usan como fixtures.
 */
object OcrDump {
    fun toJson(scan: OcrScan): String = JSONObject().apply {
        put("width", scan.imageWidth)
        put("height", scan.imageHeight)
        put(
            "boxes",
            JSONArray().also { arr ->
                scan.boxes.forEach { b ->
                    arr.put(
                        JSONObject().put("text", b.text).put("left", b.left.toDouble()).put("top", b.top.toDouble())
                            .put("right", b.right.toDouble()).put("bottom", b.bottom.toDouble())
                            .put("angle", b.baselineAngle.toDouble()).put("confidence", b.confidence.toDouble()),
                    )
                }
            },
        )
    }.toString()

    /** Inversa de [toJson]: cajas, anchura y altura de la imagen leída. */
    fun fromJson(json: String): OcrScan {
        val root = JSONObject(json)
        val arr = root.getJSONArray("boxes")
        val boxes = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OcrBox(
                o.getString("text"), o.getDouble("left").toFloat(), o.getDouble("top").toFloat(),
                o.getDouble("right").toFloat(), o.getDouble("bottom").toFloat(),
                o.optDouble("angle", 0.0).toFloat(), o.optDouble("confidence", 1.0).toFloat(),
            )
        }
        return OcrScan(boxes, OcrLayout.reconstructGrid(boxes), root.optInt("width", 0), root.optInt("height", 0))
    }
}
